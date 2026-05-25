package com.musicchain.model;

import java.util.List;

/**
 * The result of a path-finding operation between two people.
 */
public class ChainResult {

    private Person startPerson;
    private Person endPerson;
    private List<ChainStep> steps;     // The chain of connections
    private List<Person> personChain;  // Ordered list of people in the chain
    private int chainLength;           // Number of songs used (= steps)
    private boolean found;
    private String message;

    public ChainResult() {}

    public static ChainResult notFound(Person start, Person end) {
        ChainResult r = new ChainResult();
        r.startPerson = start;
        r.endPerson = end;
        r.found = false;
        r.chainLength = -1;
        r.message = "No connection found between these two people.";
        return r;
    }

    public static ChainResult found(Person start, Person end, List<ChainStep> steps, List<Person> personChain) {
        ChainResult r = new ChainResult();
        r.startPerson = start;
        r.endPerson = end;
        r.steps = steps;
        r.personChain = personChain;
        r.chainLength = steps.size();
        r.found = true;
        r.message = "Connection found in " + steps.size() + " step" + (steps.size() == 1 ? "" : "s") + "!";
        return r;
    }

    public Person getStartPerson() { return startPerson; }
    public void setStartPerson(Person startPerson) { this.startPerson = startPerson; }

    public Person getEndPerson() { return endPerson; }
    public void setEndPerson(Person endPerson) { this.endPerson = endPerson; }

    public List<ChainStep> getSteps() { return steps; }
    public void setSteps(List<ChainStep> steps) { this.steps = steps; }

    public List<Person> getPersonChain() { return personChain; }
    public void setPersonChain(List<Person> personChain) { this.personChain = personChain; }

    public int getChainLength() { return chainLength; }
    public void setChainLength(int chainLength) { this.chainLength = chainLength; }

    public boolean isFound() { return found; }
    public void setFound(boolean found) { this.found = found; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
