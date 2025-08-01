package org.gw.chatfilterplus.manager;

import java.util.ArrayList;
import java.util.List;

public class CachedMessage {
    private final String filteredMessage;
    private final boolean containsBadWord;
    private final boolean containsLink;
    private final List<String> badWords;
    private final List<String> links;

    public CachedMessage(String filteredMessage, boolean containsBadWord, boolean containsLink, List<String> badWords, List<String> links) {
        this.filteredMessage = filteredMessage;
        this.containsBadWord = containsBadWord;
        this.containsLink = containsLink;
        this.badWords = badWords != null ? new ArrayList<>(badWords) : new ArrayList<>();
        this.links = links != null ? new ArrayList<>(links) : new ArrayList<>();
    }

    public String getFilteredMessage() {
        return filteredMessage;
    }

    public boolean isContainsBadWord() {
        return containsBadWord;
    }

    public boolean isContainsLink() {
        return containsLink;
    }

    public List<String> getBadWords() {
        return badWords;
    }

    public List<String> getLinks() {
        return links;
    }
}