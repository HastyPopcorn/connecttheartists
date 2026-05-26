package com.musicchain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicchain.model.Person;
import com.musicchain.model.Song;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class WikidataService {

    private static final Logger log = LoggerFactory.getLogger(WikidataService.class);
    private static final String SPARQL_URL = "https://query.wikidata.org/sparql";
    private static final String USER_AGENT = "MusicChainGame/1.0 (contact@musicchain.example)";
    private static final long RATE_LIMIT_MS = 300; // much more generous than MusicBrainz

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private long lastRequestTime = 0;

    public WikidataService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Get members of a band using MusicBrainz artist MBID.
     * Wikidata P527 = "has part" (band → members).
     */
    @Cacheable("wikidataBandMembers")
    public List<Person> getBandMembers(String mbid) {
        String query = """
            SELECT ?member ?memberLabel ?memberMbid WHERE {
              ?band wdt:P435 "%s" .
              ?band wdt:P527 ?member .
              OPTIONAL { ?member wdt:P435 ?memberMbid . }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en" . }
            }
            """.formatted(mbid);
        try {
            JsonNode root = executeSparql(query);
            List<Person> members = new ArrayList<>();
            if (root == null) return members;
            for (JsonNode b : root.path("results").path("bindings")) {
                String label  = b.path("memberLabel").path("value").asText("");
                String memberMbid = b.path("memberMbid").path("value").asText("");
                String wdId   = b.path("member").path("value").asText("").replaceAll(".*/", "");
                if (label.isBlank()) continue;
                Person p = new Person();
                p.setId(memberMbid.isBlank() ? wdId : memberMbid);
                p.setName(label);
                p.setType("Person");
                members.add(p);
            }
            return members;
        } catch (Exception e) {
            log.error("Wikidata band members failed for mbid: {}", mbid, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get recordings for an artist via Wikidata (P175 = performer).
     * Faster than MusicBrainz due to no strict rate limit.
     */
    @Cacheable("wikidataRecordings")
    public List<Song> getRecordingsForArtist(String mbid) {
        String query = """
            SELECT ?recording ?recordingLabel ?recordingMbid WHERE {
              ?artist wdt:P435 "%s" .
              ?recording wdt:P175 ?artist .
              OPTIONAL { ?recording wdt:P6839 ?recordingMbid . }
              SERVICE wikibase:label { bd:serviceParam wikibase:language "en" . }
            }
            LIMIT 200
            """.formatted(mbid);
        try {
            JsonNode root = executeSparql(query);
            List<Song> songs = new ArrayList<>();
            if (root == null) return songs;
            for (JsonNode b : root.path("results").path("bindings")) {
                String title = b.path("recordingLabel").path("value").asText("");
                String recMbid = b.path("recordingMbid").path("value").asText("");
                String wdId  = b.path("recording").path("value").asText("").replaceAll(".*/", "");
                if (title.isBlank()) continue;
                Song s = new Song();
                s.setId(recMbid.isBlank() ? wdId : recMbid);
                s.setTitle(title);
                songs.add(s);
            }
            return songs;
        } catch (Exception e) {
            log.error("Wikidata recordings failed for mbid: {}", mbid, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get performers of a recording via Wikidata (P6839 = MusicBrainz recording ID).
     */
    @Cacheable("wikidataArtistsForRecording")
    public Map<String, String> getArtistsForRecording(String mbid) {
        String query = """
            SELECT ?artist ?artistMbid WHERE {
              ?recording wdt:P6839 "%s" .
              ?recording wdt:P175 ?artist .
              OPTIONAL { ?artist wdt:P435 ?artistMbid . }
            }
            """.formatted(mbid);
        try {
            JsonNode root = executeSparql(query);
            Map<String, String> result = new LinkedHashMap<>();
            if (root == null) return result;
            for (JsonNode b : root.path("results").path("bindings")) {
                String artistMbid = b.path("artistMbid").path("value").asText("");
                String wdId = b.path("artist").path("value").asText("").replaceAll(".*/", "");
                String id = artistMbid.isBlank() ? wdId : artistMbid;
                if (!id.isBlank()) result.put(id, "performer");
            }
            return result;
        } catch (Exception e) {
            log.error("Wikidata artists for recording failed for mbid: {}", mbid, e);
            return Collections.emptyMap();
        }
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private synchronized JsonNode executeSparql(String query) {
        long elapsed = System.currentTimeMillis() - lastRequestTime;
        if (elapsed < RATE_LIMIT_MS) {
            try { Thread.sleep(RATE_LIMIT_MS - elapsed); } catch (InterruptedException ignored) {}
        }
        try {
            String url = SPARQL_URL + "?query="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&format=json";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/sparql-results+json")
                    .GET().build();
            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());
            lastRequestTime = System.currentTimeMillis();
            if (response.statusCode() == 200) return objectMapper.readTree(response.body());
            log.warn("Wikidata returned {} for SPARQL query", response.statusCode());
        } catch (Exception e) {
            log.error("Wikidata SPARQL request failed", e);
        }
        return null;
    }
}