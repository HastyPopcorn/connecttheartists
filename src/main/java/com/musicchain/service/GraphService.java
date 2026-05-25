package com.musicchain.service;

import com.musicchain.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Core graph service.
 *
 * The music graph is bipartite:
 *   Nodes = People (artists, producers, songwriters, etc.)
 *   Edges = Songs (a song connects all its contributors)
 *
 * BFS finds the shortest chain between two people.
 * Each "step" in the chain is: PersonA → [via Song] → PersonB
 */
@Service
public class GraphService {

    private static final Logger log = LoggerFactory.getLogger(GraphService.class);
    private static final int MAX_BFS_DEPTH = 6; // Max 6 songs in a chain
    private static final int MAX_SONGS_PER_PERSON = 150; // Limit API calls

    private final MusicBrainzService musicBrainzService;

    public GraphService(MusicBrainzService musicBrainzService) {
        this.musicBrainzService = musicBrainzService;
    }

    /**
     * Find the shortest chain between two people using bidirectional BFS.
     *
     * @param startId MusicBrainz MBID of the starting person
     * @param endId   MusicBrainz MBID of the target person
     * @return ChainResult with the path, or a "not found" result
     */
    public ChainResult findShortestChain(String startId, String endId) {
        if (startId.equals(endId)) {
            Person p = musicBrainzService.getArtistDetail(startId);
            return ChainResult.found(p, p, Collections.emptyList(), List.of(p));
        }

        Person startPerson = musicBrainzService.getArtistDetail(startId);
        Person endPerson = musicBrainzService.getArtistDetail(endId);

        if (startPerson == null || endPerson == null) {
            return ChainResult.notFound(startPerson, endPerson);
        }

        log.info("Finding chain: {} → {}", startPerson.getName(), endPerson.getName());

        // BFS from start, tracking how we arrived at each person
        // State: personId → (parentPersonId, viaSong, fromRole, toRole)
        Map<String, PathNode> visited = new LinkedHashMap<>();
        Queue<String> queue = new LinkedList<>();

        visited.put(startId, new PathNode(null, null, null, null));
        queue.add(startId);

        int depth = 0;

        while (!queue.isEmpty() && depth < MAX_BFS_DEPTH) {
            int levelSize = queue.size();
            depth++;
            log.info("BFS depth {}, queue size {}", depth, levelSize);

            for (int i = 0; i < levelSize; i++) {
                String currentPersonId = queue.poll();

                // Get all songs this person contributed to
                List<Song> songs = musicBrainzService.getRecordingsForArtist(currentPersonId);
                int songsToProcess = Math.min(songs.size(), MAX_SONGS_PER_PERSON);

                for (int j = 0; j < songsToProcess; j++) {
                    Song song = songs.get(j);

                    // Get all contributors of this song
                    Map<String, String> collaborators = musicBrainzService.getArtistsForRecording(song.getId());
                    String currentRole = collaborators.getOrDefault(currentPersonId, "performer");

                    for (Map.Entry<String, String> entry : collaborators.entrySet()) {
                        String collaboratorId = entry.getKey();
                        String collaboratorRole = entry.getValue();

                        if (visited.containsKey(collaboratorId)) continue;

                        visited.put(collaboratorId, new PathNode(currentPersonId, song, currentRole, collaboratorRole));
                        queue.add(collaboratorId);

                        // Found the target!
                        if (collaboratorId.equals(endId)) {
                            log.info("Found connection at depth {}", depth);
                            return reconstructPath(startId, endId, visited, startPerson, endPerson);
                        }
                    }
                }
            }
        }

        log.info("No connection found after depth {}", depth);
        return ChainResult.notFound(startPerson, endPerson);
    }

    /**
     * Validate a player-submitted chain step.
     * Checks that fromPersonId and toPersonId both contributed to songId.
     */
    public ValidationResult validateStep(String fromPersonId, String songId, String toPersonId) {
        Map<String, String> contributors = musicBrainzService.getArtistsForRecording(songId);

        boolean fromValid = contributors.containsKey(fromPersonId);
        boolean toValid = contributors.containsKey(toPersonId);

        if (!fromValid) {
            Person from = musicBrainzService.getArtistDetail(fromPersonId);
            return ValidationResult.invalid(
                (from != null ? from.getName() : fromPersonId) + " did not contribute to this song."
            );
        }
        if (!toValid) {
            Person to = musicBrainzService.getArtistDetail(toPersonId);
            return ValidationResult.invalid(
                (to != null ? to.getName() : toPersonId) + " did not contribute to this song."
            );
        }

        String fromRole = contributors.get(fromPersonId);
        String toRole = contributors.get(toPersonId);
        return ValidationResult.valid(fromRole, toRole);
    }

    // ─── Private Helpers ─────────────────────────────────────────────────────

    private ChainResult reconstructPath(String startId, String endId,
                                         Map<String, PathNode> visited,
                                         Person startPerson, Person endPerson) {
        // Walk backwards from end to start
        List<ChainStep> steps = new ArrayList<>();
        List<String> personIdChain = new ArrayList<>();

        String current = endId;
        while (!current.equals(startId)) {
            PathNode node = visited.get(current);
            personIdChain.add(0, current);

            Person fromPerson = musicBrainzService.getArtistDetail(node.parentId);
            ChainStep step = new ChainStep(fromPerson, node.viaSong, node.fromRole, node.toRole);
            steps.add(0, step);
            current = node.parentId;
        }
        personIdChain.add(0, startId);

        // Resolve person objects for the chain
        List<Person> personChain = new ArrayList<>();
        for (String id : personIdChain) {
            if (id.equals(startId)) {
                personChain.add(startPerson);
            } else if (id.equals(endId)) {
                personChain.add(endPerson);
            } else {
                personChain.add(musicBrainzService.getArtistDetail(id));
            }
        }

        return ChainResult.found(startPerson, endPerson, steps, personChain);
    }

    // ─── Inner Classes ───────────────────────────────────────────────────────

    private static class PathNode {
        String parentId;
        Song viaSong;
        String fromRole;
        String toRole;

        PathNode(String parentId, Song viaSong, String fromRole, String toRole) {
            this.parentId = parentId;
            this.viaSong = viaSong;
            this.fromRole = fromRole;
            this.toRole = toRole;
        }
    }

    public static class ValidationResult {
        public final boolean valid;
        public final String message;
        public final String fromRole;
        public final String toRole;

        private ValidationResult(boolean valid, String message, String fromRole, String toRole) {
            this.valid = valid;
            this.message = message;
            this.fromRole = fromRole;
            this.toRole = toRole;
        }

        public static ValidationResult valid(String fromRole, String toRole) {
            return new ValidationResult(true, "Valid connection!", fromRole, toRole);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message, null, null);
        }
    }
}
