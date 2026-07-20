package org.gw.chatfilterplus.managers.profanity;

final class ProfanityTrie {

    static final int ALPHABET = 36;

    static final class Node {
        final Node[] children = new Node[ALPHABET];
        String word;
    }

    final Node root = new Node();

    static int childIndex(char c) {
        if (c >= 'a' && c <= 'z') return c - 'a';
        if (c >= '0' && c <= '9') return 26 + (c - '0');
        return -1;
    }

    static Node getChild(Node node, char c) {
        int idx = childIndex(c);
        if (idx < 0) return null;
        return node.children[idx];
    }

    static Node getOrCreateChild(Node node, char c) {
        int idx = childIndex(c);
        if (idx < 0) return null;
        Node child = node.children[idx];
        if (child == null) {
            child = new Node();
            node.children[idx] = child;
        }
        return child;
    }

    void insertWord(String norm) {
        if (norm == null || norm.isEmpty()) return;
        Node node = root;
        for (int i = 0; i < norm.length(); i++) {
            node = getOrCreateChild(node, norm.charAt(i));
            if (node == null) return;
        }
        node.word = norm;
    }

    boolean insertReduced(String reduced, String dictWord) {
        if (reduced == null || reduced.isEmpty() || dictWord == null) return false;
        Node node = root;
        for (int i = 0; i < reduced.length(); i++) {
            node = getOrCreateChild(node, reduced.charAt(i));
            if (node == null) return false;
        }
        if (node.word == null) {
            node.word = dictWord;
        }
        return true;
    }
}
