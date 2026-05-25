package com.musicchain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.Objects;

/**
 * Represents a song/recording, linking multiple contributors.
 * A song is the "edge" in the graph between people.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Song {

    private String id;              // MusicBrainz recording MBID
    private String title;           // Song title
    private String releaseYear;     // Year of release
    private String albumName;       // Album it appeared on
    private List<Contributor> contributors; // All people linked to this song

    public Song() {}

    public Song(String id, String title, String releaseYear, String albumName) {
        this.id = id;
        this.title = title;
        this.releaseYear = releaseYear;
        this.albumName = albumName;
    }

    /**
     * A contributor to the song with their specific role.
     */
    public static class Contributor {
        private Person person;
        private String role; // "vocalist", "producer", "lyricist", "guitarist", etc.

        public Contributor() {}

        public Contributor(Person person, String role) {
            this.person = person;
            this.role = role;
        }

        public Person getPerson() { return person; }
        public void setPerson(Person person) { this.person = person; }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getReleaseYear() { return releaseYear; }
    public void setReleaseYear(String releaseYear) { this.releaseYear = releaseYear; }

    public String getAlbumName() { return albumName; }
    public void setAlbumName(String albumName) { this.albumName = albumName; }

    public List<Contributor> getContributors() { return contributors; }
    public void setContributors(List<Contributor> contributors) { this.contributors = contributors; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Song)) return false;
        Song s = (Song) o;
        return Objects.equals(id, s.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() { return title + " (" + id + ")"; }
}
