package com.humanworkstream.baha.service;

import com.humanworkstream.baha.config.IncidentIngestProperties;
import com.humanworkstream.baha.entity.DraftIncident;
import com.humanworkstream.baha.enumeration.Road;
import com.humanworkstream.baha.enumeration.Severity;
import com.humanworkstream.baha.enumeration.Trend;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Scheduled drafter: pulls recent Philippine flood news from Google News RSS, extracts a location and
 * severity, and files each as a pending {@link DraftIncident} for an admin to review. Deliberately
 * conservative and self-contained — a place gazetteer + keyword severity rules, no external LLM/geocoder
 * (both can be swapped in later). Disabled by default; fail-soft (a bad feed logs and skips, never
 * throws out of the scheduled tick). The human review queue is the safety gate on every extraction.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncidentIngestionService {

    private final IncidentIngestProperties props;
    private final DraftIncidentService incidents;

    private final RestClient http = RestClient.create();

    /** Metro Manila + Rizal places we can plot. First match wins, so specific entries precede the
     *  region-wide fallbacks at the end. Coordinates are approximate centroids. */
    private record Place(String needle, String name, String district, double lat, double lng) {}

    private static final List<Place> GAZETTEER = List.of(
            new Place("provident village", "Provident Village", "Marikina", 14.6349, 121.1035),
            new Place("tumana", "Tumana", "Marikina", 14.6563, 121.0875),
            new Place("nangka", "Nangka", "Marikina", 14.6712, 121.1041),
            new Place("malanday", "Malanday", "Marikina", 14.6678, 121.0959),
            new Place("marikina", "Marikina", "Marikina", 14.6507, 121.1029),
            new Place("cainta", "Cainta", "Cainta, Rizal", 14.5786, 121.1220),
            new Place("taytay", "Taytay", "Taytay, Rizal", 14.5573, 121.1320),
            new Place("angono", "Angono", "Angono, Rizal", 14.5266, 121.1530),
            new Place("antipolo", "Antipolo", "Antipolo, Rizal", 14.5843, 121.1760),
            new Place("san mateo", "San Mateo", "San Mateo, Rizal", 14.6968, 121.1210),
            new Place("rodriguez", "Rodriguez (Montalban)", "Rodriguez, Rizal", 14.7280, 121.1440),
            new Place("montalban", "Rodriguez (Montalban)", "Rodriguez, Rizal", 14.7280, 121.1440),
            new Place("españa", "España Blvd", "Manila", 14.6098, 120.9906),
            new Place("espana", "España Blvd", "Manila", 14.6098, 120.9906),
            new Place("quiapo", "Quiapo", "Manila", 14.5986, 120.9835),
            new Place("tondo", "Tondo", "Manila", 14.6170, 120.9670),
            new Place("manila", "Manila", "Manila", 14.5995, 120.9842),
            new Place("quezon city", "Quezon City", "Quezon City", 14.6760, 121.0437),
            new Place("cubao", "Cubao", "Quezon City", 14.6190, 121.0530),
            new Place("pasig", "Pasig", "Pasig", 14.5764, 121.0851),
            new Place("mandaluyong", "Mandaluyong", "Mandaluyong", 14.5794, 121.0359),
            new Place("makati", "Makati", "Makati", 14.5547, 121.0244),
            new Place("taguig", "Taguig", "Taguig", 14.5176, 121.0509),
            new Place("valenzuela", "Valenzuela", "Valenzuela", 14.7000, 120.9830),
            new Place("malabon", "Malabon", "Malabon", 14.6620, 120.9570),
            new Place("caloocan", "Caloocan", "Caloocan", 14.6510, 120.9720),
            new Place("parañaque", "Parañaque", "Parañaque", 14.4790, 121.0140),
            new Place("paranaque", "Parañaque", "Parañaque", 14.4790, 121.0140),
            new Place("las piñas", "Las Piñas", "Las Piñas", 14.4500, 120.9830),
            // Region-wide fallbacks (used only when no specific place is named) → low confidence.
            new Place("metro manila", "Metro Manila (unspecified)", "Metro Manila", 14.5995, 120.9842),
            new Place("ncr", "Metro Manila (unspecified)", "Metro Manila", 14.5995, 120.9842),
            new Place("rizal", "Rizal (unspecified)", "Rizal", 14.6000, 121.1500)
    );

    /** Only draft items that are actually about flooding. */
    private static final List<String> FLOOD_TERMS = List.of(
            "flood", "flooded", "flooding", "baha", "inundat", "submerged",
            "knee-deep", "waist-deep", "gutter-deep", "impassable", "overflow");

    @Scheduled(
            fixedDelayString = "${baha.incidents.ingest.interval-ms:900000}",
            initialDelayString = "${baha.incidents.ingest.initial-delay-ms:60000}")
    public void ingestTick() {
        if (!props.isEnabled()) return;
        try {
            String xml = fetch();
            if (xml == null || xml.isBlank()) return;
            List<Element> items = items(xml);
            int drafted = 0;
            for (Element item : items) {
                if (drafted >= props.getMaxPerRun()) break;
                DraftIncident draft = toDraft(item);
                if (draft == null || draft.getConfidence() < props.getMinConfidence()) continue;
                if (incidents.ingest(draft).isPresent()) drafted++;
            }
            log.info("[incidents] ingestion tick: {} item(s) scanned, {} new draft(s)", items.size(), drafted);
        } catch (Exception e) {
            // Fail soft — a flaky/blocked feed must never break the scheduler.
            log.warn("[incidents] ingestion tick failed: {}", e.getMessage());
        }
    }

    private String fetch() {
        String url = props.getRssUrl()
                + "?q=" + URLEncoder.encode(props.getQuery(), StandardCharsets.UTF_8)
                + "&hl=en-PH&gl=PH&ceid=PH:en";
        return http.get().uri(url)
                .header("User-Agent", props.getUserAgent())
                .retrieve()
                .body(String.class);
    }

    /** Parse the RSS XML into its <item> elements, hardened against XXE. */
    private List<Element> items(String xml) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document doc = db.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        NodeList nodes = doc.getElementsByTagName("item");
        java.util.ArrayList<Element> out = new java.util.ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            if (nodes.item(i) instanceof Element el) out.add(el);
        }
        return out;
    }

    /** Turn one RSS item into a draft, or null when it isn't a placeable flood story. */
    private DraftIncident toDraft(Element item) {
        String title = text(item, "title");
        String link = text(item, "link");
        if (title == null || link == null) return null;

        String lower = title.toLowerCase(Locale.ROOT);
        if (FLOOD_TERMS.stream().noneMatch(lower::contains)) return null;

        Place place = GAZETTEER.stream().filter(p -> lower.contains(p.needle())).findFirst().orElse(null);
        if (place == null) return null; // can't place it on the map → skip

        // Publisher: prefer the <source> element, else the "Headline - Publisher" suffix.
        String source = text(item, "source");
        String headline = title;
        if (source == null && title.contains(" - ")) {
            int cut = title.lastIndexOf(" - ");
            source = title.substring(cut + 3).trim();
            headline = title.substring(0, cut).trim();
        }
        if (source == null || source.isBlank()) source = "Google News";

        Severity severity = severityOf(lower);
        boolean regionOnly = place.district().equals("Metro Manila") || place.district().equals("Rizal");
        double confidence = (regionOnly ? 0.40 : 0.70) + (hasSeverityCue(lower) ? 0.12 : 0.0);

        DraftIncident d = new DraftIncident();
        d.setHeadline(headline);
        d.setSourceName(source);
        d.setSourceUrl(link);
        d.setPublishedAt(parseDate(text(item, "pubDate")));
        d.setName(place.name());
        d.setDistrict(place.district());
        d.setLatitude(place.lat());
        d.setLongitude(place.lng());
        d.setSeverity(severity);
        d.setDepthCm(depthFor(severity));
        d.setTrend(severity == Severity.low ? Trend.steady : Trend.rising);
        d.setRoad(roadFor(severity));
        d.setConfidence(Math.min(0.9, confidence));
        return d;
    }

    private static boolean hasSeverityCue(String lower) {
        return lower.matches(".*(waist-deep|chest-deep|neck-deep|impassable|not passable|submerged|knee-deep|gutter-deep).*");
    }

    private static Severity severityOf(String lower) {
        if (lower.matches(".*(waist-deep|chest-deep|neck-deep|impassable|not passable|submerged).*")) return Severity.high;
        if (lower.matches(".*(knee-deep|gutter-deep|half-tire|risky|rising).*")) return Severity.medium;
        return Severity.low;
    }

    private static int depthFor(Severity s) {
        return switch (s) { case high -> 100; case medium -> 45; case low -> 15; };
    }

    private static Road roadFor(Severity s) {
        return switch (s) { case high -> Road.impassable; case medium -> Road.risky; case low -> Road.passable; };
    }

    /** RFC-1123 pubDate (e.g. "Wed, 08 Jul 2026 10:00:00 GMT") → OffsetDateTime; now() on failure. */
    private static OffsetDateTime parseDate(String pubDate) {
        if (pubDate == null || pubDate.isBlank()) return OffsetDateTime.now();
        try {
            return OffsetDateTime.parse(pubDate.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
        } catch (Exception e) {
            return OffsetDateTime.now();
        }
    }

    /** First child element's text by tag name, or null. */
    private static String text(Element parent, String tag) {
        NodeList nl = parent.getElementsByTagName(tag);
        if (nl.getLength() == 0) return null;
        Node n = nl.item(0);
        String v = n.getTextContent();
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
