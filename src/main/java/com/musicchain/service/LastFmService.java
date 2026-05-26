package com.musicchain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.musicchain.model.Person;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
public class LastFmService {

    private static final Logger log = LoggerFactory.getLogger(LastFmService.class);
    private static final String BASE_URL = "https://ws.audioscrobbler.com/2.0/";
    private static final String USER_AGENT = "MusicChainGame/1.0";

    @Value("${lastfm.api.key:}")
    private String apiKey;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public LastFmService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10)).build();
        this.objectMapper = new ObjectMapper();
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Search artists — Last.fm returns results ordered by listener count already.
     * Only called when configured; falls back to MusicBrainz otherwise.
     */
    @Cacheable("lastfmArtistSearch")
    public List<Person> searchArtists(String query) {
        if (!isConfigured()) return Collections.emptyList();
        try {
            String url = BASE_URL + "?method=artist.search&artist="
                    + URLEncoder.encode(query, StandardCharsets.UTF_8)
                    + "&api_key=" + apiKey + "&format=json&limit=10";
            JsonNode root = fetch(url);
            if (root == null) return Collections.emptyList();
            List<Person> results = new ArrayList<>();
            for (JsonNode a : root.path("results").path("artistmatches").path("artist")) {
                String mbid = a.path("mbid").asText("").trim();
                if (mbid.isBlank()) continue; // skip entries with no MBID
                Person p = new Person();
                p.setId(mbid);
                p.setName(a.path("name").asText());
                p.setType("Person");
                p.setDisambiguation(formatListeners(a.path("listeners").asText("0")));
                results.add(p);
            }
            return results;
        } catch (Exception e) {
            log.error("Last.fm artist search failed for: {}", query, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get listener count for an artist — used to sort MusicBrainz results.
     */
    @Cacheable("lastfmListeners")
    public long getArtistListeners(String artistName) {
        if (!isConfigured()) return 0;
        try {
            String url = BASE_URL + "?method=artist.getinfo&artist="
                    + URLEncoder.encode(artistName, StandardCharsets.UTF_8)
                    + "&api_key=" + apiKey + "&format=json";
            JsonNode root = fetch(url);
            if (root == null) return 0;
            return parseLong(root.path("artist").path("stats").path("listeners").asText("0"));
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get playcount for a track — used to sort song search results.
     */
    @Cacheable("lastfmPlaycount")
    public long getTrackPlaycount(String artist, String track) {
        if (!isConfigured()) return 0;
        try {
            String url = BASE_URL + "?method=track.getinfo&artist="
                    + URLEncoder.encode(artist, StandardCharsets.UTF_8)
                    + "&track=" + URLEncoder.encode(track, StandardCharsets.UTF_8)
                    + "&api_key=" + apiKey + "&format=json";
            JsonNode root = fetch(url);
            if (root == null) return 0;
            return parseLong(root.path("track").path("playcount").asText("0"));
        } catch (Exception e) {
            return 0;
        }
    }

    // ─── Private ─────────────────────────────────────────────────────────────

    private JsonNode fetch(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url))
                    .header("User-Agent", USER_AGENT).GET().build();
            HttpResponse<String> res = httpClient.send(req,
                    HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 200) return objectMapper.readTree(res.body());
        } catch (Exception e) {
            log.error("Last.fm request failed: {}", url, e);
        }
        return null;
    }

    private long parseLong(String s) {
        try { return Long.parseLong(s.replaceAll("[^0-9]", "")); }
        catch (NumberFormatException e) { return 0; }
    }

    private String formatListeners(String raw) {
        try {
            long n = parseLong(raw);
            if (n >= 1_000_000) return String.format("%.1fM listeners", n / 1_000_000.0);
            if (n >= 1_000)     return String.format("%.0fK listeners", n / 1_000.0);
            return n + " listeners";
        } catch (Exception e) { return ""; }
    }
}