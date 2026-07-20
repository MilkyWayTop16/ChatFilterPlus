import org.gw.chatfilterplus.managers.profanity.ProfanityEngine;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/**
 * Large-scale false-positive hunt for ProfanityEngine.
 * Writes a UTF-8 report (console can't render Cyrillic on this Windows shell).
 *
 * Part 1: curated realistic teen chat (clean_corpus.txt) -> any match is an FP.
 * Part 2: generated sequences of genuinely-innocent whole words (innocent_words.txt)
 *         -> any match is an FP (spaced-fragment / cross-token join collisions).
 * Recall: Corpus.PROFANE must stay caught.
 */
public class FpHunt {

    static final java.nio.charset.Charset UTF8 = StandardCharsets.UTF_8;
    static final String REPO = ".";
    static final String SCRATCH = "dev-tests";

    // ---- yml parsing (same rules as Bench) ----
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

    static List<String> readLines(String path) throws Exception {
        List<String> out = new ArrayList<>();
        for (String s : Files.readAllLines(Paths.get(path), UTF8)) {
            String t = s.replace("﻿", "").trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    // group key -> sample messages + count
    static final Map<String, int[]> groupCount = new LinkedHashMap<>();
    static final Map<String, LinkedHashSet<String>> groupSamples = new LinkedHashMap<>();

    static void record(String message, ProfanityEngine.Match m) {
        String key = m.reason() + " | dict=" + m.dictionaryWord();
        groupCount.computeIfAbsent(key, k -> new int[1])[0]++;
        LinkedHashSet<String> s = groupSamples.computeIfAbsent(key, k -> new LinkedHashSet<>());
        if (s.size() < 15) s.add(message + "   [hit=\"" + m.matchedText() + "\" conf=" + m.confidence() + "]");
    }

    static List<String> obfuscate(String w) {
        List<String> out = new ArrayList<>();
        int n = w.length();
        if (n < 4) return out;
        for (int i = 1; i < n - 1; i++) {                 // one internal char -> mask (single deletion)
            out.add(w.substring(0, i) + "*" + w.substring(i + 1));
            out.add(w.substring(0, i) + "." + w.substring(i + 1));
            char sub = leet(w.charAt(i));
            if (sub != 0) out.add(w.substring(0, i) + sub + w.substring(i + 1));
        }
        StringBuilder dot = new StringBuilder(), dash = new StringBuilder(), sp = new StringBuilder();
        for (int i = 0; i < n; i++) {                     // fully separated variants
            if (i > 0) { dot.append('.'); dash.append('-'); sp.append(' '); }
            dot.append(w.charAt(i)); dash.append(w.charAt(i)); sp.append(w.charAt(i));
        }
        out.add(dot.toString());
        out.add(dash.toString());
        out.add(sp.toString());
        return out;
    }

    static char leet(char c) {
        return switch (Character.toLowerCase(c)) {
            case 'о' -> '0';
            case 'и' -> '1';
            case 'е' -> '3';
            case 'а' -> '@';
            case 'з' -> '3';
            case 'с' -> '$';
            default -> 0;
        };
    }

    public static void main(String[] args) throws Exception {
        List<String> yml = Files.readAllLines(Paths.get(REPO + "/src/main/resources/bad-words.yml"), UTF8);
        List<String> bad = clean(section(yml, "bad-words"));
        List<String> safe = clean(section(yml, "safe-words"));
        ProfanityEngine engine = new ProfanityEngine(bad, safe, "high", true);

        StringBuilder rep = new StringBuilder();
        rep.append("FP HUNT REPORT   dict=").append(bad.size()).append("  safe=").append(safe.size()).append("\n");
        rep.append("=".repeat(70)).append("\n\n");

        // ---------- Part 1: curated corpus ----------
        List<String> corpus = new ArrayList<>(new LinkedHashSet<>(readLines(SCRATCH + "/clean_corpus.txt")));
        List<String> curatedFp = new ArrayList<>();
        for (String msg : corpus) {
            List<ProfanityEngine.Match> ms = engine.findMatches(msg);
            if (!ms.isEmpty()) {
                ProfanityEngine.Match m = ms.get(0);
                curatedFp.add(String.format("  \"%s\"  -> dict=%s hit=\"%s\" conf=%d %s",
                        msg, m.dictionaryWord(), m.matchedText(), m.confidence(), m.reason()));
                for (ProfanityEngine.Match mm : ms) record(msg, mm);
            }
        }
        rep.append("PART 1 — CURATED REALISTIC CHAT (").append(corpus.size()).append(" messages)\n");
        rep.append("  false positives: ").append(curatedFp.size()).append("\n");
        for (String f : curatedFp) rep.append(f).append("\n");
        rep.append("\n");

        // ---------- Part 2: generated innocent-word sequences ----------
        List<String> pool = readLines(SCRATCH + "/innocent_words.txt");
        int poolN = pool.size();
        long tested = 0, flagged = 0;

        // pairs (exhaustive)
        for (int i = 0; i < poolN; i++) {
            for (int j = 0; j < poolN; j++) {
                if (i == j) continue;
                String msg = pool.get(i) + " " + pool.get(j);
                tested++;
                List<ProfanityEngine.Match> ms = engine.findMatches(msg);
                if (!ms.isEmpty()) { flagged++; for (ProfanityEngine.Match m : ms) record(msg, m); }
            }
        }
        // triples + quads (random sample, deterministic)
        Random rnd = new Random(42);
        for (int n = 0; n < 300000; n++) {
            String msg = pool.get(rnd.nextInt(poolN)) + " " + pool.get(rnd.nextInt(poolN)) + " " + pool.get(rnd.nextInt(poolN));
            tested++;
            List<ProfanityEngine.Match> ms = engine.findMatches(msg);
            if (!ms.isEmpty()) { flagged++; for (ProfanityEngine.Match m : ms) record(msg, m); }
        }
        for (int n = 0; n < 200000; n++) {
            String msg = pool.get(rnd.nextInt(poolN)) + " " + pool.get(rnd.nextInt(poolN)) + " "
                    + pool.get(rnd.nextInt(poolN)) + " " + pool.get(rnd.nextInt(poolN));
            tested++;
            List<ProfanityEngine.Match> ms = engine.findMatches(msg);
            if (!ms.isEmpty()) { flagged++; for (ProfanityEngine.Match m : ms) record(msg, m); }
        }

        rep.append("PART 2 — GENERATED INNOCENT-WORD SEQUENCES (pool=").append(poolN)
           .append(", tested=").append(tested).append(", flagged=").append(flagged).append(")\n\n");

        // ---------- Part 2b: longer vocabulary — stress stream / fuzzy / exact-token ----------
        List<String> pool2 = readLines(SCRATCH + "/innocent_words2.txt");
        int pool2N = pool2.size();
        long tested2 = 0, flagged2 = 0;
        for (String w : pool2) {                       // standalone words
            tested2++;
            List<ProfanityEngine.Match> ms = engine.findMatches(w);
            if (!ms.isEmpty()) { flagged2++; for (ProfanityEngine.Match m : ms) record(w, m); }
        }
        for (int i = 0; i < pool2N; i++) {             // exhaustive pairs
            for (int j = 0; j < pool2N; j++) {
                if (i == j) continue;
                String msg = pool2.get(i) + " " + pool2.get(j);
                tested2++;
                List<ProfanityEngine.Match> ms = engine.findMatches(msg);
                if (!ms.isEmpty()) { flagged2++; for (ProfanityEngine.Match m : ms) record(msg, m); }
            }
        }
        List<String> union = new ArrayList<>(pool); union.addAll(pool2);
        int uN = union.size();
        for (int n = 0; n < 300000; n++) {             // random triples across both pools
            String msg = union.get(rnd.nextInt(uN)) + " " + union.get(rnd.nextInt(uN)) + " " + union.get(rnd.nextInt(uN));
            tested2++;
            List<ProfanityEngine.Match> ms = engine.findMatches(msg);
            if (!ms.isEmpty()) { flagged2++; for (ProfanityEngine.Match m : ms) record(msg, m); }
        }
        rep.append("PART 2b — LONGER VOCAB stream/fuzzy/exact (pool2=").append(pool2N)
           .append(", tested=").append(tested2).append(", flagged=").append(flagged2).append(")\n\n");

        // ---------- grouped summary ----------
        rep.append("GROUPED FALSE POSITIVES (reason | dict -> count, samples)\n");
        rep.append("-".repeat(70)).append("\n");
        List<String> keys = new ArrayList<>(groupCount.keySet());
        keys.sort((x, y) -> Integer.compare(groupCount.get(y)[0], groupCount.get(x)[0]));
        for (String k : keys) {
            rep.append(k).append("   count=").append(groupCount.get(k)[0]).append("\n");
            for (String s : groupSamples.get(k)) rep.append("      ").append(s).append("\n");
            rep.append("\n");
        }

        // ---------- recall guard ----------
        String[] mustCatch = {
            "хуй", "иди нахуй", "бля", "блять что происходит", "пизда", "пиздец полный",
            "ебать как круто", "ты сука", "сучка", "хуйня какая то", "долбоеб", "мудак",
            "гандон", "пидор", "х у й", "х-у-й", "х.у.й", "п и з д а", "б л я т ь",
            // ш/щ-based profanity that must stay caught after any normalization change
            "сиська", "сиськи", "шлюха", "шлюхи", "шваль", "шалава", "шмара", "шлюшка",
            "потаскуха", "залупа", "хуесос", "ахуеть", "нахуй"
        };
        int caught = 0, missed = 0;
        StringBuilder miss = new StringBuilder();
        for (String s : mustCatch) {
            if (engine.findMatches(s).isEmpty()) { missed++; miss.append("   MISS: \"").append(s).append("\"\n"); }
            else caught++;
        }
        rep.append("=".repeat(70)).append("\n");
        rep.append("RECALL GUARD (must-catch=").append(mustCatch.length).append("): caught=")
           .append(caught).append(" missed=").append(missed).append("\n");
        rep.append(miss);

        // ---------- homograph battery: innocent ш/щ words that must stay CLEAN ----------
        String[] mustClean = {
            "шишка на лбу", "собираю шишки", "поймал щуку", "щука большая", "щукой ловят",
            "ищу команду", "вообще класс", "ещё разок", "на площади", "в роще", "роща",
            "помощник админа", "товарищ помоги", "какое счастье", "приятное ощущение",
            "пища богов", "плащ надел", "борщ вкусный", "вещь классная", "мощный пк"
        };
        int cleanOk = 0;
        StringBuilder dirty = new StringBuilder();
        for (String s : mustClean) {
            List<ProfanityEngine.Match> ms = engine.findMatches(s);
            if (ms.isEmpty()) cleanOk++;
            else dirty.append("   STILL-FLAGGED: \"").append(s).append("\" -> dict=")
                      .append(ms.get(0).dictionaryWord()).append(" hit=\"").append(ms.get(0).matchedText()).append("\"\n");
        }
        rep.append("\nHOMOGRAPH BATTERY (must-clean=").append(mustClean.length).append("): clean=")
           .append(cleanOk).append(" flagged=").append(mustClean.length - cleanOk).append("\n");
        rep.append(dirty);

        // ---------- recovery check: previously-missed / masked evasions ----------
        String[] recovery = {
            "с у к а", "с у к и", "хyй", "п*зда", "п*зды", "х*й", "с*ка", "п*дор",
            "м*дак", "бл*ть", "х у й", "п и з д а", "б л я т ь", "н а х у й",
            "х**", "г*ндон", "уёб*к"
        };
        rep.append("\n").append("=".repeat(70)).append("\n");
        rep.append("RECOVERY CHECK (previously-missed / masked evasions)\n");
        for (String s : recovery) {
            List<ProfanityEngine.Match> ms = engine.findMatches(s);
            rep.append("   ").append(ms.isEmpty() ? "MISS   " : "catch  ").append("\"").append(s).append("\"");
            if (!ms.isEmpty()) rep.append(" -> dict=").append(ms.get(0).dictionaryWord())
                                  .append(" (").append(ms.get(0).reason()).append(")");
            rep.append("\n");
        }

        // ---------- obfuscation stress: innocent words leet-ified must stay clean ----------
        String[] obfBase = {
            "привет", "спасибо", "пожалуйста", "помоги", "играть", "ставить", "писать",
            "письмо", "список", "миска", "каска", "маска", "ириска", "редиска", "сосиска",
            "пицца", "физра", "снова", "супер", "класс", "ошибка", "кнопка", "полка",
            "палка", "галка", "балка", "скидка", "посылка", "насос", "пылесос", "колесо",
            "полено", "село", "школа", "шишка", "щука", "мышка", "кошка", "ложка", "вилка",
            "тарелка", "бутылка", "коробка", "лавка", "будка", "утка", "сетка", "ветка",
            "нитка", "плитка", "калитка", "улитка", "пилот", "пират", "салат", "халат",
            "гудок", "садик", "радуга", "погода", "дорога", "работа", "забота", "суббота"
        };
        List<String> obfWords = new ArrayList<>(pool2);
        Collections.addAll(obfWords, obfBase);
        long obfTested = 0, obfFlagged = 0;
        StringBuilder obf = new StringBuilder();
        for (String w : obfWords) {
            for (String v : obfuscate(w)) {
                obfTested++;
                List<ProfanityEngine.Match> ms = engine.findMatches(v);
                if (!ms.isEmpty()) {
                    obfFlagged++;
                    if (obf.length() < 6000) obf.append("   FP \"").append(v).append("\" (base \"").append(w)
                            .append("\") -> dict=").append(ms.get(0).dictionaryWord())
                            .append(" hit=\"").append(ms.get(0).matchedText()).append("\" ").append(ms.get(0).reason()).append("\n");
                }
            }
        }
        rep.append("\nOBFUSCATION STRESS (innocent words leet-ified): tested=").append(obfTested)
           .append(" flagged=").append(obfFlagged).append("\n").append(obf);
        System.out.println("obfuscation stress tested = " + obfTested + ", flagged = " + obfFlagged);
        System.out.println("recovery: see report");

        Files.write(Paths.get(SCRATCH + "/fp_report.txt"), rep.toString().getBytes(UTF8));

        // ascii-only console summary
        System.out.println("curated messages = " + corpus.size() + ", curated FPs = " + curatedFp.size());
        System.out.println("generated tested = " + tested + ", generated flags = " + flagged);
        System.out.println("distinct FP groups = " + groupCount.size());
        System.out.println("recall caught = " + caught + ", missed = " + missed + " (of " + mustCatch.length + ")");
        System.out.println("homograph must-clean = " + cleanOk + " clean, " + (mustClean.length - cleanOk) + " flagged (of " + mustClean.length + ")");
        System.out.println("report -> fp_report.txt");
    }
}
