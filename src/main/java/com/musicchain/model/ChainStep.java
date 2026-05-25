package com.musicchain.model;

import java.util.List;

/**
 * Represents one step in a chain: Person → Song → Person
 */
public class ChainStep {

    private Person fromPerson;
    private Song viaSong;
    private String fromRole;   // Role of fromPerson in the song
    private String toRole;     // Role of the next person in the song

    public ChainStep() {}

    public ChainStep(Person fromPerson, Song viaSong, String fromRole, String toRole) {
        this.fromPerson = fromPerson;
        this.viaSong = viaSong;
        this.fromRole = fromRole;
        this.toRole = toRole;
    }

    public Person getFromPerson() { return fromPerson; }
    public void setFromPerson(Person fromPerson) { this.fromPerson = fromPerson; }

    public Song getViaSong() { return viaSong; }
    public void setViaSong(Song viaSong) { this.viaSong = viaSong; }

    public String getFromRole() { return fromRole; }
    public void setFromRole(String fromRole) { this.fromRole = fromRole; }

    public String getToRole() { return toRole; }
    public void setToRole(String toRole) { this.toRole = toRole; }
}
