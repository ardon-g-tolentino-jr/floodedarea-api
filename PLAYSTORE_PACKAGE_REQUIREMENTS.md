# FloodedArea — Google Play Package Requirements (for Claude Code)

> Hand this file to Claude Code from inside the project. It is the complete spec
> for producing a Google Play–ready Android package that wraps the live website.

---

## 1. Context

- **App:** FloodedArea — live crowd-sourced flood map for Metro Manila & Rizal.
- **Already in production as a website:** https://floodedarea.com/ (HTTPS, live).
- **Stack:** React + Vite + TypeScript.
- **Local project root:** `/Users/ardontolentinojr./Documents/CLAUDE DEV/DEVELOPMENT/FREE APP`
- **Distribution goal:** Publish to Google Play for more reach **without forking the
  codebase**. The website stays the single source of truth.

## 2. Chosen approach (do not deviate without flagging)

Build a **Trusted Web Activity (TWA)** using **Bubblewrap** (CLI). The Android app is a
thin shell that loads `https://floodedarea.com/` full-screen with no browser UI.
Content updates ship via the website with no Play Store review in the loop.

Do **not** use Capacitor or rewrite in React Native — those bundle/fork the frontend,
which defeats the "website is source of truth" goal.

## 3. Objective / Deliverables

Produce, in the project output directory:

1. A **signed Android App Bundle** (`.aab`) — the upload artifact for Play Console.
2. A valid **`assetlinks.json`** for Digital Asset Links domain verification.
3. A **Caddy snippet** to serve `assetlinks.json` from `/.well-known/`.
4. The **signing keystore** + a `key.properties` (gitignored), with the **SHA-256
   fingerprint** recorded.
5. A **README** documenting the manual Play Console steps that remain.

## 4. Hard requirements / constraints

| Item | Requirement |
|------|-------------|
| Output format | Android App Bundle (`.aab`). **Not** APK. |
| `targetSdkVersion` | **36** (Android 16) — required for new Play submissions from 31 Aug 2026. |
| `minSdkVersion` | 23 or higher. |
| App ID (package name) | `com.floodedarea.app` — **confirm before building; permanent and unchangeable on Play.** |
| Host | `https://floodedarea.com` (already HTTPS). |
| Icons | 192×192, 512×512, and a 512×512 **maskable** variant. |
| Signing | Generate a release keystore. **Back it up.** Losing it = can never update the app. |

## 5. Tasks for Claude Code (in order)

### Phase 1 — Verify / establish PWA readiness
A TWA requires the site to be an installable PWA.

1. Inspect the project for an existing web app manifest and service worker
   (`vite-plugin-pwa` config, `public/manifest.*`, registered SW).
2. If missing or incomplete, add PWA support:
   - `npm install -D vite-plugin-pwa`
   - Add `VitePWA({ registerType: 'autoUpdate', manifest: {...} })` to `vite.config.ts`.
   - Manifest fields: `name`, `short_name`, `start_url: "/"`, `display: "standalone"`,
     `background_color`, `theme_color`, and the three icons above.
   - Place icon PNGs in `/public`.
3. Build and confirm the manifest resolves at a stable URL
   (e.g. `https://floodedarea.com/manifest.webmanifest`).
   **Note:** the site must be redeployed with the manifest live before Phase 2,
   because Bubblewrap reads it from the production URL.

### Phase 2 — Initialize the TWA
1. Ensure prerequisites: JDK 17 and Android SDK build-tools. Bubblewrap can install and
   manage its own JDK + Android SDK on first run — allow this if not already present.
2. `npm install -g @bubblewrap/cli` (or use `npx @bubblewrap/cli`).
3. `bubblewrap init --manifest https://floodedarea.com/manifest.webmanifest`
4. During init, set:
   - Application ID = `com.floodedarea.app` (confirmed value from §4)
   - Launcher name = `FloodedArea`
   - Display mode = `standalone`
   - Theme/background colors matching the brand
   - Signing key = create a new keystore (record alias + store path; keep passwords
     OUT of the repo)
5. In the generated `twa-manifest.json`, set `targetSdkVersion` to **36** and confirm
   `minSdkVersion` ≥ 23. Re-run `bubblewrap update` if needed so the Gradle project
   picks up the change.

### Phase 3 — Build
1. `bubblewrap build` → produces the signed `.aab` and the keystore.
2. Extract the release key SHA-256 fingerprint:
   `keytool -list -v -keystore <keystore> -alias <alias>`
3. Record the fingerprint in the README.

### Phase 4 — Digital Asset Links
1. Generate `assetlinks.json` containing:
   - `package_name`: `com.floodedarea.app`
   - `sha256_cert_fingerprints`: the fingerprint from Phase 3
   - **Important:** if you later enable Play App Signing (recommended), the *upload* key
     fingerprint differs from the *app-signing* key fingerprint. The final
     `assetlinks.json` must include the **app-signing** key fingerprint shown in
     Play Console → App integrity. Document both and note which to use post-upload.
2. Produce a Caddy snippet that serves the file at
   `https://floodedarea.com/.well-known/assetlinks.json` with
   `Content-Type: application/json`.

### Phase 5 — Output + document
1. Copy the `.aab`, `assetlinks.json`, Caddy snippet, and keystore into the output dir.
2. Write a README covering:
   - The exact fingerprint(s) used.
   - The remaining **manual** steps (see §7).
   - A reminder to back up the keystore offline.

## 6. Output routing

Write all deliverables to:
`/Users/ardontolentinojr./Documents/CLAUDE DEV/OUTPUT/DB/FLOODEDAREA/`
(`mkdir -p` it first, then confirm the exact path.)

## 7. Out of scope — do NOT attempt (manual steps for Ardz)

Claude Code must stop before these and list them in the README instead:

- **Do not upload to Play Console.** The `.aab` upload, store listing, data safety form,
  content rating, and release submission are done manually by the account owner.
- **Do not enter or store any Play Console / Google credentials or keystore passwords**
  in plaintext or in git.
- **Note the closed-testing gate:** this is a personal Play Console account, so production
  requires a closed test with **12 testers opted in for 14 continuous days** before
  production access unlocks. Packaging does not satisfy this — flag it in the README.

## 8. Security boundaries

- Add the keystore, `key.properties`, and any `*.jks`/`*.keystore` to `.gitignore`.
- Never print keystore or key passwords to logs or commit them.
- The keystore is irreplaceable for updates — instruct Ardz to store a backup offline.

## 9. Acceptance criteria

- [ ] `https://floodedarea.com/manifest.webmanifest` resolves and the site is installable.
- [ ] `bubblewrap build` succeeds and outputs a signed `.aab` targeting API 36.
- [ ] `assetlinks.json` is generated with the correct package name + fingerprint and a
      working Caddy serve rule.
- [ ] Keystore + fingerprint recorded; secrets gitignored, not printed.
- [ ] README lists the remaining manual Play Console + 12-tester steps.
