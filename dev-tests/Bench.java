import org.gw.chatfilterplus.managers.profanity.ProfanityEngine;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Measures precision/recall of ProfanityEngine against the shared corpora. */
public class Bench {

    static List<String> section(List<String> lines, String header) {
        List<String> out = new ArrayList<>();
        boolean in = false;
        for (String raw : lines) {
            String line = raw.replace("﻿", "");
            if (line.startsWith(header + ":")) { in = true; continue; }
            if (!in) continue;
            String t = line.trim();
            if (t.startsWith("#") || t.isEmpty()) continue;
            if (t.startsWith("- ")) {
                String w = t.substring(2).trim();
                if (w.startsWith("\"") && w.endsWith("\"") && w.length() > 1) w = w.substring(1, w.length() - 1);
                if (!w.isEmpty()) out.add(w);
                continue;
            }
            if (!line.startsWith(" ")) break;
            if (!t.startsWith("-")) break;
        }
        return out;
    }

    static List<String> clean(List<String> in) {
        List<String> r = new ArrayList<>();
        for (String s : in) {
            if (s == null) continue;
            String t = s.trim().toLowerCase();
            if (t.length() >= 2 && !r.contains(t)) r.add(t);
        }
        return r;
    }

    public static void main(String[] args) throws Exception {
        Path p = Paths.get("./src/main/resources/bad-words.yml");
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        List<String> bad = clean(section(lines, "bad-words"));
        List<String> safe = clean(section(lines, "safe-words"));

        String label = args.length > 0 ? args[0] : "engine";
        ProfanityEngine engine = new ProfanityEngine(bad, safe, "high", true);

        System.out.println("=========== " + label + " (level=high, dict=" + bad.size() + ") ===========");
        System.out.println();

        int fp = 0;
        System.out.println("--- FALSE POSITIVES (clean text flagged) ---");
        for (String s : Corpus.CLEAN) {
            List<ProfanityEngine.Match> m = engine.findMatches(s);
            if (!m.isEmpty()) {
                fp++;
                ProfanityEngine.Match x = m.get(0);
                System.out.printf("   FP: %-38s -> dict=%-12s hit=\"%s\" conf=%d %s%n",
                        "\"" + s + "\"", x.dictionaryWord(), x.matchedText(), x.confidence(), x.reason());
            }
        }
        if (fp == 0) System.out.println("   (none)");

        int missed = 0;
        System.out.println();
        System.out.println("--- FALSE NEGATIVES (profanity NOT caught) ---");
        for (String s : Corpus.PROFANE) {
            if (engine.findMatches(s).isEmpty()) {
                missed++;
                System.out.printf("   MISS: %s%n", "\"" + s + "\"");
            }
        }
        if (missed == 0) System.out.println("   (none)");

        System.out.println();
        System.out.println("--- BORDERLINE evasions ---");
        int bCaught = 0;
        for (String s : Corpus.BORDERLINE) {
            boolean caught = !engine.findMatches(s).isEmpty();
            if (caught) bCaught++;
            System.out.printf("   %-8s %s%n", caught ? "caught" : "missed", "\"" + s + "\"");
        }

        int cleanN = Corpus.CLEAN.length;
        int profN = Corpus.PROFANE.length;
        System.out.println();
        System.out.println("=========== SUMMARY ===========");
        System.out.printf("  clean corpus : %d, false positives = %d  (FP rate %.1f%%)%n",
                cleanN, fp, 100.0 * fp / cleanN);
        System.out.printf("  profane corpus: %d, missed = %d  (recall %.1f%%)%n",
                profN, missed, 100.0 * (profN - missed) / profN);
        System.out.printf("  borderline    : %d/%d caught%n", bCaught, Corpus.BORDERLINE.length);

        // Item 6 regression: a safe word must shield only its own span, not the whole token.
        System.out.println();
        System.out.println("=========== safe-word region shielding (item 6) ===========");
        String[] safeCases = {"хуже", "похуже", "получше хуже стало", "хужехуй", "художник рисует", "члены партии"};
        for (String s : safeCases) {
            List<ProfanityEngine.Match> m = engine.findMatches(s);
            System.out.printf("  %-24s -> %s%n", "\"" + s + "\"",
                    m.isEmpty() ? "clean" : "FLAGGED dict=" + m.get(0).dictionaryWord() + " hit=\"" + m.get(0).matchedText() + "\"");
        }
    }
}
