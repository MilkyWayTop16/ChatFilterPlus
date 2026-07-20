import org.gw.chatfilterplus.managers.profanity.ProfanityEngine;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Verifies item 4: nickname shielding, including the abuse guard. */
public class NameShield {

    public static void main(String[] args) throws Exception {
        Path p = Paths.get("./src/main/resources/bad-words.yml");
        List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
        List<String> bad = Bench.clean(Bench.section(lines, "bad-words"));
        List<String> safe = Bench.clean(Bench.section(lines, "safe-words"));

        // A harmless nick that merely collides with a dictionary fragment.
        Set<String> harmless = Set.of("трость");
        // An abusive nick that embeds real profanity.
        Set<String> abusive = Set.of("хуйгеймер");

        ProfanityEngine noNames = new ProfanityEngine(bad, safe, "high", true,
                ProfanityEngine.PrecisionOptions.forLevel("high"), Set::of);
        ProfanityEngine withHarmless = new ProfanityEngine(bad, safe, "high", true,
                ProfanityEngine.PrecisionOptions.forLevel("high"), () -> harmless);
        ProfanityEngine withAbusive = new ProfanityEngine(bad, safe, "high", true,
                ProfanityEngine.PrecisionOptions.forLevel("high"), () -> abusive);

        System.out.println("=== 1. Nick shielding works (harmless nick) ===");
        show("трость подай", noNames, "без защиты имён");
        show("трость подай", withHarmless, "ник 'трость' онлайн");

        System.out.println();
        System.out.println("=== 2. Abuse guard: nick containing real profanity is NOT shielded ===");
        show("хуйгеймер привет", withAbusive, "ник 'хуйгеймер' онлайн");
        show("хуйгеймер иди сюда", withAbusive, "ник 'хуйгеймер' онлайн");

        System.out.println();
        System.out.println("=== 3. Shielding a nick must not shield the same word elsewhere ===");
        show("ты хуй", withAbusive, "ник 'хуйгеймер' онлайн");
        show("ты хуй", withHarmless, "ник 'трость' онлайн");
    }

    static void show(String message, ProfanityEngine engine, String ctx) {
        List<ProfanityEngine.Match> m = engine.findMatches(message);
        System.out.printf("  %-24s [%-22s] -> %s%n", "\"" + message + "\"", ctx,
                m.isEmpty() ? "clean" : "FLAGGED dict=" + m.get(0).dictionaryWord()
                        + " hit=\"" + m.get(0).matchedText() + "\"");
    }
}
