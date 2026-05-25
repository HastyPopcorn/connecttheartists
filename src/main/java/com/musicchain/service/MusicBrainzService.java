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
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

/**
 * Service for interacting with the MusicBrainz API.
 *
 * MusicBrainz is a free, open music encyclopedia.
 * API docs: https://musicbrainz.org/doc/MusicBrainz_API
 *
 * Rate limit: 1 request/second. We handle this with Thread.sleep().
 * No API key required for basic usage.
 */
@Service
public class MusicBrainzService {

    private static final Logger log = LoggerFactory.getLogger(MusicBrainzService.class);

    private static final String BASE_URL = "https://musicbrainz.org/ws/2";
    private static final String USER_AGENT = "MusicChainGame/1.0 (contact@musicchain.example)";
    private static final long RATE_LIMIT_MS = 1100; // MusicBrainz allows 1 req/sec

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private long lastRequestTime = 0;

    public MusicBrainzService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Search for artists/people by name.
     * Returns up to 10 results.
     */
    @Cacheable("artistSearch")
    public List<Person> searchArtists(String query) {
        try {
            String url = BASE_URL + "/artist/?query=" + encode(query) + "&limit=10&fmt=json";
            JsonNode root = fetchJson(url);
            List<Person> results = new ArrayList<>();

            if (root == null || !root.has("artists")) return results;

            for (JsonNode node : root.get("artists")) {
                Person p = new Person();
                p.setId(node.path("id").asText());
                p.setName(node.path("name").asText());
                p.setType(node.path("type").asText("Unknown"));
                p.setDisambiguation(node.path("disambiguation").asText(""));
                results.add(p);
            }
            return results;
        } catch (Exception e) {
            log.error("Error searching artists for query: {}", query, e);
            return Collections.emptyList();
        }
    }

    /**
     * Get all recordings (songs) that an artist participated in.
     * Includes credited roles via relationships.
     *
     * @param artistId MusicBrainz artist MBID
     */
    @Cacheable("artistRecordings")
    public List<Song> getRecordingsForArtist(String artistId) {
        List<Song> songs = new ArrayList<>();
        int offset = 0;
        int limit = 100;
        int maxSongs = 300; // Cap to avoid excessive API calls

        try {
            while (songs.size() < maxSongs) {
                String url = BASE_URL + "/recording?artist=" + artistId
                        + "&inc=artist-credits+artist-rels"
                        + "&limit=" + limit + "&offset=" + offset + "&fmt=json";

                JsonNode root = fetchJson(url);
                if (root == null || !root.has("recordings")) break;

                JsonNode recordings = root.get("recordings");
                if (recordings.isEmpty()) break;

                for (JsonNode rec : recordings) {
                    Song song = parseRecording(rec);
                    if (song != null) songs.add(song);
                }

                int total = root.path("recording-count").asInt(0);
                offset += limit;
                if (offset >= total || offset >= maxSongs) break;
            }
        } catch (Exception e) {
            log.error("Error fetching recordings for artist: {}", artistId, e);
        }
        return songs;
    }

    /**
     * Get a recording by ID with full artist relationships.
     */
    @Cacheable("recordingDetail")
    public Song getRecordingDetail(String recordingId) {
        try {
            String url = BASE_URL + "/recording/" + recordingId
                    + "?inc=artist-credits+artist-rels+work-rels&fmt=json";
            JsonNode root = fetchJson(url);
            return root != null ? parseRecording(root) : null;
        } catch (Exception e) {
            log.error("Error fetching recording detail: {}", recordingId, e);
            return null;
        }
    }

      /**
   * Search for recordings (songs) by title.
   * Returns up to 10 results.
   */
  @Cacheable("songSearch")
  public List<Song> searchSongs(String query) {
      try {
          String url = BASE_URL + "/recording/?query=" + encode(query)
                  + "&limit=10&fmt=json";
          JsonNode root = fetchJson(url);
          List<Song> results = new ArrayList<>();

          if (root == null || !root.has("recordings")) return results;

          for (JsonNode rec : root.get("recordings")) {
              Song song = parseRecording(rec);
              if (song != null) results.add(song);
          }
          return results;
      } catch (Exception e) {
          log.error("Error searching songs for query: {}", query, e);
          return Collections.emptyList();
      }
  }

  @Cacheable("artistDetail")
public Person getArtistDetail(String artistId) {
    try {
        String url = BASE_URL + "/artist/" + artistId + "?fmt=json";
        JsonNode root = fetchJson(url);
        if (root == null) return null;

        Person p = new Person();
        p.setId(root.path("id").asText());
        p.setName(root.path("name").asText());
        p.setType(root.path("type").asText("Unknown"));
        p.setDisambiguation(root.path("disambiguation").asText(""));
        return p;
    } catch (Exception e) {
        log.error("Error fetching artist detail: {}", artistId, e);
        return null;
    }
}


  @Cacheable("songSearchForArtist")
  public List<Song> searchSongsForArtist(String query, String artistId) {
      try {
          String url = BASE_URL + "/recording/?query=" + encode(query)
                  + "%20AND%20arid:" + artistId
                  + "&inc=releases&limit=25&fmt=json";
          JsonNode root = fetchJson(url);
          List<Song> results = new ArrayList<>();
          if (root == null || !root.has("recordings")) return results;

          for (JsonNode rec : root.get("recordings")) {
              Song song = parseRecording(rec);
              if (song != null) results.add(song);
          }

          results.sort(Comparator.comparingInt(s -> releaseScore((Song) s, root))
                  .reversed());

          return results.stream().limit(15).toList();
      } catch (Exception e) {
          log.error("Error searching songs for artist: {} query: {}", artistId, query, e);
          return Collections.emptyList();
      }
  }

  private int releaseScore(Song song, JsonNode root) {
      // Find this song's recording node to inspect its releases
      for (JsonNode rec : root.get("recordings")) {
          if (!song.getId().equals(rec.path("id").asText())) continue;
          if (!rec.has("releases")) return 0;

          int best = 0;
          for (JsonNode release : rec.get("releases")) {
              String status = release.path("status").asText("").toLowerCase();
              String primaryType = release.path("release-group")
                      .path("primary-type").asText("").toLowerCase();

              if (!"official".equals(status)) continue; // skip bootlegs, promos

              int score = switch (primaryType) {
                  case "album" -> 4;
                  case "ep"    -> 3;
                  case "single"-> 2;
                  default      -> 1; // compilation, live, other
              };
              if (score > best) best = score;
          }
          return best;
      }
      return 0;
  }

    /**
     * Get all artists who collaborated on a given recording.
     * Returns map of artistId → role.
     */
    @Cacheable("recordingArtists")
    public Map<String, String> getArtistsForRecording(String recordingId) {
        Map<String, String> artistRoles = new LinkedHashMap<>();
        try {
            String url = BASE_URL + "/recording/" + recordingId
                    + "?inc=artist-credits+artist-rels&fmt=json";
            JsonNode root = fetchJson(url);
            if (root == null) return artistRoles;

            // Standard artist credits (performer, featured artist, etc.)
            if (root.has("artist-credit")) {
                for (JsonNode credit : root.get("artist-credit")) {
                    if (credit.has("artist")) {
                        String id = credit.path("artist").path("id").asText();
                        artistRoles.put(id, "performer");
                    }
                }
            }

            // Relationship-based credits (producer, lyricist, etc.)
            if (root.has("relations")) {
                for (JsonNode rel : root.get("relations")) {
                    String targetType = rel.path("target-type").asText();
                    if ("artist".equals(targetType) && rel.has("artist")) {
                        String id = rel.path("artist").path("id").asText();
                        String type = rel.path("type").asText("contributor");
                        artistRoles.put(id, normalizeRole(type));
                    }
                }
            }
        } catch (Exception e) {
            log.error("Error fetching artists for recording: {}", recordingId, e);
        }
        return artistRoles;
    }

    // ─── Private Helpers ───────────────────────────────────────────────────────

    private Song parseRecording(JsonNode rec) {
        try {
            String id = rec.path("id").asText();
            String title = rec.path("title").asText();
            if (id.isBlank() || title.isBlank()) return null;

            Song song = new Song();
            song.setId(id);
            song.setTitle(title);

            // Release year from first-release-date or releases array
            String date = rec.path("first-release-date").asText("");
            if (date.length() >= 4) {
                song.setReleaseYear(date.substring(0, 4));
            }

            // Album name from releases
            if (rec.has("releases") && !rec.get("releases").isEmpty()) {
                song.setAlbumName(rec.get("releases").get(0).path("title").asText(""));
            }

            // Parse contributors
            List<Song.Contributor> contributors = new ArrayList<>();
            if (rec.has("artist-credit")) {
                for (JsonNode credit : rec.get("artist-credit")) {
                    if (credit.has("artist")) {
                        Person p = new Person();
                        p.setId(credit.path("artist").path("id").asText());
                        p.setName(credit.path("artist").path("name").asText());
                        contributors.add(new Song.Contributor(p, "performer"));
                    }
                }
            }
            song.setContributors(contributors);
            return song;
        } catch (Exception e) {
            return null;
        }
    }

    private synchronized JsonNode fetchJson(String url) {
        // Respect rate limit
        long now = System.currentTimeMillis();
        long elapsed = now - lastRequestTime;
        if (elapsed < RATE_LIMIT_MS) {
            try { Thread.sleep(RATE_LIMIT_MS - elapsed); } catch (InterruptedException ignored) {}
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            lastRequestTime = System.currentTimeMillis();

            if (response.statusCode() == 200) {
                return objectMapper.readTree(response.body());
            } else if (response.statusCode() == 503) {
                // Rate limited - wait and retry once
                Thread.sleep(2000);
                HttpResponse<String> retry = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofString());
                lastRequestTime = System.currentTimeMillis();
                if (retry.statusCode() == 200) {
                    return objectMapper.readTree(retry.body());
                }
            }
            log.warn("MusicBrainz returned status {} for URL: {}", response.statusCode(), url);
        } catch (Exception e) {
            log.error("HTTP request failed for URL: {}", url, e);
        }
        return null;
    }

    private String encode(String s) {
        return s.replace(" ", "%20").replace("\"", "%22").replace("&", "%26");
    }

    private String normalizeRole(String mbRole) {
        return switch (mbRole.toLowerCase()) {
            case "producer" -> "producer";
            case "lyricist" -> "lyricist";
            case "composer" -> "composer";
            case "performer" -> "performer";
            case "vocal" -> "vocalist";
            case "instrument" -> "instrumentalist";
            case "mix" -> "mixer";
            case "engineer" -> "engineer";
            case "arranger" -> "arranger";
            default -> mbRole;
        };
    }
}
