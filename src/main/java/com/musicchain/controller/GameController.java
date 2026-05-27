package com.musicchain.controller;

import com.musicchain.model.*;
import com.musicchain.service.GraphService;
import com.musicchain.service.LastFmService;
import com.musicchain.service.MusicBrainzService;
import com.musicchain.service.WikidataService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Map;

/**
 * REST API controller for the Music Chain game.
 *
 * Endpoints:
 *   GET  /api/search?q=...            → Search for people by name
 *   GET  /api/artist/{id}             → Get artist details
 *   POST /api/chain/find              → Find shortest chain (auto-solver)
 *   POST /api/chain/validate-step     → Validate a player's chain step
 *   GET  /api/song/search?q=...       → Search for songs by title
 *   GET  /api/song/{id}/contributors  → Get contributors for a song
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")  // Allow frontend dev server
public class GameController {

    private final MusicBrainzService musicBrainzService;
    private final GraphService graphService;
    private final WikidataService wikidataService;
    private final LastFmService lastFmService;

   @Autowired
    public GameController(MusicBrainzService musicBrainzService, GraphService graphService,
                        WikidataService wikidataService, LastFmService lastFmService) {
      this.musicBrainzService = musicBrainzService;
      this.graphService       = graphService;
      this.wikidataService    = wikidataService;
      this.lastFmService      = lastFmService;
  }

    /**
     * Search for people (artists, producers, songwriters, etc.) by name.
     * Used by the autocomplete search boxes.
     */
    @GetMapping("/search")
    public ResponseEntity<List<Person>> searchPeople(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) return ResponseEntity.badRequest().build();
        String query = q.trim();

        // Last.fm returns results ordered by listener count already
        if (lastFmService.isConfigured()) {
            List<Person> lastFmResults = lastFmService.searchArtists(query);
            if (!lastFmResults.isEmpty()) return ResponseEntity.ok(lastFmResults);
        }
        // Fallback to MusicBrainz
        return ResponseEntity.ok(musicBrainzService.searchArtists(query));
    }


    @GetMapping("/artist/{id}/members")
public ResponseEntity<List<Person>> getBandMembers(@PathVariable String id) {
    // Wikidata first — better band membership coverage
    List<Person> members = wikidataService.getBandMembers(id);
    if (members.isEmpty()) {
        members = musicBrainzService.getBandMembers(id);
    }
    return ResponseEntity.ok(members);
}
    /**
     * Get detailed info about a specific artist.
     */
    @GetMapping("/artist/{id}")
    public ResponseEntity<Person> getArtist(@PathVariable String id) {
        Person person = musicBrainzService.getArtistDetail(id);
        if (person == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(person);
    }

    /**
     * Auto-solve: find the shortest chain between two people.
     * Request body: { "startId": "...", "endId": "..." }
     */
    @PostMapping("/chain/find")
    public ResponseEntity<ChainResult> findChain(@RequestBody Map<String, String> body) {
        String startId = body.get("startId");
        String endId = body.get("endId");

        if (startId == null || endId == null || startId.isBlank() || endId.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ChainResult result = graphService.findShortestChain(startId, endId);
        return ResponseEntity.ok(result);
    }

    /**
     * Validate a player's proposed chain step.
     * Request body: { "fromPersonId": "...", "songId": "...", "toPersonId": "..." }
     *
     * Returns whether fromPerson and toPerson both contributed to the song,
     * and what their respective roles were.
     */
    @PostMapping("/chain/validate-step")
    public ResponseEntity<Map<String, Object>> validateStep(@RequestBody Map<String, String> body) {
        String fromPersonId = body.get("fromPersonId");
        String songId = body.get("songId");
        String toPersonId = body.get("toPersonId");

        if (fromPersonId == null || songId == null || toPersonId == null) {
            return ResponseEntity.badRequest().build();
        }

        GraphService.ValidationResult result = graphService.validateStep(fromPersonId, songId, toPersonId);

        return ResponseEntity.ok(Map.of(
            "valid", result.valid,
            "message", result.message,
            "fromRole", result.fromRole != null ? result.fromRole : "",
            "toRole", result.toRole != null ? result.toRole : ""
        ));
    }

    /**
     * Search for songs by title.
     * Used to help players find the connecting song.
     */
    @GetMapping("/song/search")
    public ResponseEntity<List<Song>> searchSongs(@RequestParam String q) {
        if (q == null || q.trim().length() < 2) {
            return ResponseEntity.badRequest().build();
        }
        List<Song> results = musicBrainzService.searchSongs(q.trim());
        return ResponseEntity.ok(results);
    }

    @GetMapping("/artist/{id}/song-search")
    public ResponseEntity<List<Song>> searchSongsForArtist(
            @PathVariable String id, @RequestParam String q) {
        if (q == null || q.trim().length() < 2) return ResponseEntity.badRequest().build();
        List<Song> results = musicBrainzService.searchSongsForArtist(q.trim(), id);
        return ResponseEntity.ok(results);
    }

    /**
     * Get all songs that a person contributed to.
     * Used to populate the song picker when a player selects a person.
     */
    @GetMapping("/artist/{id}/songs")
    public ResponseEntity<List<Song>> getArtistSongs(@PathVariable String id) {
        List<Song> songs = musicBrainzService.getRecordingsForArtist(id);
        return ResponseEntity.ok(songs);
    }

    /**
     * Get all contributors for a given song.
     * Used to show who the player can jump to after picking a song.
     */
    @GetMapping("/song/{id}/contributors")
    public ResponseEntity<Map<String, Object>> getSongContributors(@PathVariable String id) {
        Song song = musicBrainzService.getRecordingDetail(id);
        if (song == null) return ResponseEntity.notFound().build();

        Map<String, String> artistRoles = musicBrainzService.getArtistsForRecording(id);

        return ResponseEntity.ok(Map.of(
            "song", song,
            "contributors", artistRoles
        ));
    }

}
