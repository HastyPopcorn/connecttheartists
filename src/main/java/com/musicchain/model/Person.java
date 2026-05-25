package com.musicchain.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.Objects;

/**
 * Represents any person in the music industry:
 * singers, songwriters, producers, instrumentalists, etc.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Person {

    private String id;          // MusicBrainz artist MBID
    private String name;        // Display name
    private String type;        // Person, Group, etc.
    private String disambiguation; // e.g., "American rock guitarist"
    private String imageUrl;    // Optional image URL (from Cover Art Archive / Fanart.tv)

    public Person() {}

    public Person(String id, String name, String type, String disambiguation) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.disambiguation = disambiguation;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getDisambiguation() { return disambiguation; }
    public void setDisambiguation(String disambiguation) { this.disambiguation = disambiguation; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Person)) return false;
        Person p = (Person) o;
        return Objects.equals(id, p.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() { return name + " (" + id + ")"; }
}
