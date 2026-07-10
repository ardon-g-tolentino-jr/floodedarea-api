# Spec — feat/report-photo (baha-api)

Optional photo on a flood report, stored **in the DB**, plus a public endpoint that streams it.
Pairs with the already-shipped baha-ui v1.35.0 change (frontend sends `photoDataUrl`, reads
`photoUrl`, shares the report to Facebook).

## Contract (what baha-ui already expects)

- `POST /reports` body MAY include `photoDataUrl`: a compressed JPEG **data-URL**
  (`data:image/jpeg;base64,…`, client-capped ~300 KB). Absent ⇒ report posts as today.
- Every report in `GET /reports`, `GET /reports/history`, `GET /reports/{id}` gains
  **`photoUrl`**: `"/reports/{id}/photo"` when a photo exists, else `null`. **The raw bytes are
  never returned in these list/detail payloads.**
- New **`GET /reports/{id}/photo`** streams the image (`Content-Type` = stored MIME); `404` when
  the report has no photo. **Public** — already covered by `GET /reports/**` permitAll in
  `SecurityConfig` (line 45), so no security change.

## Design decision — keep the bytes off the entity's hot path

The controller returns the `FloodReport` **entity** directly (no DTO), and Hibernate runs with
`ddl-auto=none`. A mapped `byte[] photo` would be SELECTed and base64-serialized into every
list response. Two guards, both important:

1. **Do not map `photo` (BYTEA) on the entity at all.** Read/write it via native queries.
2. Map only **`photo_mime`** (tiny TEXT) as **read-only** (`insertable=false, updatable=false`),
   `@JsonIgnore` it, and expose a **`@Transient getPhotoUrl()`** derived from it.

This guarantees the map/feed queries never load image bytes, and `photoUrl` costs nothing.

---

## Code changes

### 1. `entity/FloodReport.java` — add read-only mime flag + derived URL

```java
import com.fasterxml.jackson.annotation.JsonIgnore;
// ...

    /** Presence + type of the report's photo. Read-only here: the bytes and this mime are written
     *  together by a native UPDATE (see FloodReportRepository.setPhoto). Never serialized directly. */
    @JsonIgnore
    @Column(name = "photo_mime", insertable = false, updatable = false)
    private String photoMime;

    /** URL the UI uses to load the photo, or null when none. Derived — not a column. */
    @Transient
    public String getPhotoUrl() {
        return photoMime != null ? "/reports/" + id + "/photo" : null;
    }
```

> Lombok `@Data` generates `getPhotoMime()`/`setPhotoMime()`; the hand-written `getPhotoUrl()`
> coexists and Jackson serializes it as `photoUrl`. `insertable=false, updatable=false` means the
> JPA INSERT/UPDATE never touch `photo_mime`, so it can only ever be set by our native write —
> which keeps the DB pairing constraint satisfied.

### 2. `dto/CreateReportRequest.java` — accept the data-URL, bounded

```java
import jakarta.validation.constraints.Size;
// ...add a trailing field to the record:
        String captchaToken,
        /** Optional compressed JPEG data-URL ("data:image/…;base64,…"). Bounded to keep the JSON body
         *  small; the client already compresses to ~300 KB. */
        @Size(max = 1_400_000, message = "Photo is too large.") String photoDataUrl
```

### 3. `repository/FloodReportRepository.java` — native read/write for the bytes

```java
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

    /** Write the photo bytes + mime together (satisfies the DB pairing constraint). */
    @Modifying
    @Query(value = "UPDATE flood_report SET photo = :bytes, photo_mime = :mime WHERE id = :id",
           nativeQuery = true)
    void setPhoto(@Param("id") Integer id, @Param("bytes") byte[] bytes, @Param("mime") String mime);

    /** Read the photo bytes + mime for one report; empty when it has no photo. */
    @Query(value = "SELECT photo AS photo, photo_mime AS photoMime FROM flood_report " +
                   "WHERE id = :id AND photo IS NOT NULL", nativeQuery = true)
    Optional<PhotoView> findPhotoById(@Param("id") Integer id);

    /** Projection over the native photo read (Spring maps column aliases → getters). */
    interface PhotoView {
        byte[] getPhoto();
        String getPhotoMime();
    }
```

### 4. `service/FloodReportService.java` — decode/validate on create, read on demand

```java
import org.springframework.transaction.annotation.Transactional;
import java.util.Base64;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

    private static final Set<String> ALLOWED_MIME = Set.of("image/jpeg", "image/png", "image/webp");
    private static final int MAX_PHOTO_BYTES = 900_000; // ~0.9 MB decoded; client sends ~300 KB
    private static final Pattern DATA_URL =
            Pattern.compile("^data:(image/[a-zA-Z0-9.+-]+);base64,(.+)$", Pattern.DOTALL);

    /** Small carrier for the photo endpoint. */
    public record ReportPhoto(byte[] bytes, String mime) {}
```

In `create(...)`, after `FloodReport saved = reportRepository.save(r);` and before the SSE
broadcast, attach the photo if one was sent:

```java
        // Optional photo → store the decoded bytes in the DB (bytes + mime written together).
        if (req.photoDataUrl() != null && !req.photoDataUrl().isBlank()) {
            ReportPhoto photo = decodePhoto(req.photoDataUrl());
            reportRepository.setPhoto(saved.getId(), photo.bytes(), photo.mime());
            saved.setPhotoMime(photo.mime()); // so the create response carries photoUrl (no refetch)
        }
```

Add the decoder + the read method:

```java
    private ReportPhoto decodePhoto(String dataUrl) {
        Matcher m = DATA_URL.matcher(dataUrl.trim());
        if (!m.matches()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo must be an image data URL.");
        }
        String mime = m.group(1).toLowerCase();
        if (!ALLOWED_MIME.contains(mime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image type.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(m.group(2));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo is not valid base64.");
        }
        if (bytes.length == 0 || bytes.length > MAX_PHOTO_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Photo is too large.");
        }
        return new ReportPhoto(bytes, mime);
    }

    @Transactional(readOnly = true)
    public Optional<ReportPhoto> photo(Integer id) {
        return reportRepository.findPhotoById(id)
                .map(v -> new ReportPhoto(v.getPhoto(), v.getPhotoMime()));
    }
```

### 5. `controller/FloodReportController.java` — the public streaming endpoint

```java
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import java.time.Duration;

    /** Public: stream a report's photo. 404 when the report has no photo. */
    @GetMapping("/{id}/photo")
    public ResponseEntity<byte[]> photo(@PathVariable Integer id) {
        return service.photo(id)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.mime()))
                        .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                        .body(p.bytes()))
                .orElse(ResponseEntity.notFound().build());
    }
```

### 6. Security / config
- **No security change.** `GET /reports/**` is already `permitAll()` (`SecurityConfig.java:45`),
  which covers `/reports/{id}/photo`. `POST /reports` stays `authenticated()` (GUEST allowed) —
  the photo rides in the existing authenticated body.
- **No body-size config needed.** The base64 photo (~400 KB) sits well under Spring MVC's default
  JSON handling; the real bound is the `@Size` on the DTO + the decoded-length check in the
  service. (Only revisit `server.tomcat.*` limits if you later raise the client compression cap.)

---

## Testing

1. Apply the migration locally:
   ```
   psql "$DB_URL" -f db/migrations/feat/report-photo/20260710_report_photo.up.sql
   ```
2. `POST /reports` (authenticated) with a `photoDataUrl` → 201; response includes
   `photoUrl:"/reports/{id}/photo"`.
3. `GET /reports` → the new report has `photoUrl` set and **no** `photo`/base64 field; older
   reports have `photoUrl:null`.
4. `GET /reports/{id}/photo` → 200, correct `Content-Type`, the image bytes; a report with no
   photo → 404.
5. `POST /reports` with a non-image or oversize data-URL → 400 / 413 (not stored).
6. Rollback check: `psql "$DB_URL" -f db/migrations/feat/report-photo/20260710_report_photo.down.sql`.

---

## CHANGELOG entry (paste at the top of `documentations/CHANGELOG.md`)

```markdown
## [feat/report-photo] — 2026-07-10

**Summary:** Let citizens attach an optional photo when posting a flood report, stored in the DB,
and let them share the report to Facebook. This entry covers the backend; the baha-ui side shipped
in v1.35.0.

### Frontend
- Shipped in baha-ui v1.35.0: optional photo capture (compressed client-side to a JPEG data-URL),
  photo thumbnail in the report detail card, and a hybrid "Share to Facebook" action.

### Backend
- `FloodReport` entity: read-only `photo_mime` mapping + derived `photoUrl` (bytes are unmapped,
  kept off the list/detail JSON and hot-path SELECTs).
- `CreateReportRequest`: optional, size-bounded `photoDataUrl`.
- `FloodReportRepository`: native `setPhoto` (write) + `findPhotoById` projection (read).
- `FloodReportService`: decode/validate the data-URL (allowed MIME, ≤ ~0.9 MB) on create; new
  `photo(id)` read.
- `FloodReportController`: new public `GET /reports/{id}/photo` streaming the image; 404 when none.
- No SecurityConfig change — `GET /reports/**` is already public.

### Database
- setup.sql appended: yes · Migration: db/migrations/feat/report-photo/20260710_report_photo.up.sql
  · Rollback: db/migrations/feat/report-photo/20260710_report_photo.down.sql
- `flood_report`: `+photo BYTEA`, `+photo_mime TEXT`; CHECK constraints for allowed MIME and
  photo/mime pairing. No index (fetched by PK only).

### Config / Env
- None.

### Testing
- Migration applied locally; POST with/without photo, GET list (no bytes, photoUrl present),
  GET /reports/{id}/photo (stream + 404), oversize/non-image rejection, rollback verified.
```
