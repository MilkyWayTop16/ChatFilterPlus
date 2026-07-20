import org.gw.chatfilterplus.managers.LinksManager;
import org.gw.chatfilterplus.managers.ConfigManager;
import org.gw.chatfilterplus.managers.AdaptiveAdFilter;
import org.gw.chatfilterplus.configs.LinksConfig;
import org.bukkit.configuration.file.YamlConfiguration;
import sun.misc.Unsafe;

import java.io.File;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Boots the REAL LinksManager/AdaptiveAdFilter detection (no logic duplication) by allocating the
 * objects without their Bukkit-touching constructors and wiring real config from links.yml.
 */
public class AdBench {

    static final String REPO = ".";
    static final String SCRATCH = "dev-tests";

    static Unsafe U;
    static {
        try {
            Field f = Unsafe.class.getDeclaredField("theUnsafe");
            f.setAccessible(true);
            U = (Unsafe) f.get(null);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    static Field field(Class<?> c, String name) {
        for (Class<?> k = c; k != null; k = k.getSuperclass()) {
            try { return k.getDeclaredField(name); } catch (NoSuchFieldException ignored) {}
        }
        throw new RuntimeException("no field " + name + " in " + c);
    }
    static void setObj(Object o, String n, Object v)  { U.putObject(o, U.objectFieldOffset(field(o.getClass(), n)), v); }
    static void setBool(Object o, String n, boolean v){ U.putBoolean(o, U.objectFieldOffset(field(o.getClass(), n)), v); }
    static void setInt(Object o, String n, int v)     { U.putInt(o, U.objectFieldOffset(field(o.getClass(), n)), v); }
    static void setLong(Object o, String n, long v)   { U.putLong(o, U.objectFieldOffset(field(o.getClass(), n)), v); }

    static LinksManager LM;
    static Method FIND;

    static void boot() throws Exception {
        File linksYml = new File(REPO + "/src/main/resources/links.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(linksYml);

        LinksConfig lc = (LinksConfig) U.allocateInstance(LinksConfig.class);
        setObj(lc, "config", yaml);
        setBool(lc, "filterEnabled", true);
        setObj(lc, "filterMode", "block-and-notify");
        setObj(lc, "filterReplacement", yaml.getString("filter.links.replacement", "[x]"));
        setObj(lc, "linksRegex", yaml.getString("filter.links.regex"));
        setBool(lc, "listFilterEnabled", yaml.getBoolean("filter.links.list-filter.enabled", false));
        setObj(lc, "listFilterMode", yaml.getString("filter.links.list-filter.mode", "whitelist").toLowerCase());
        setObj(lc, "listFilterDomains", new ArrayList<>(yaml.getStringList("filter.links.list-filter.domains")));
        setObj(lc, "exceptionPlayers", new HashSet<String>());
        setObj(lc, "exceptionGroups", new HashSet<String>());

        ConfigManager cm = (ConfigManager) U.allocateInstance(ConfigManager.class);
        setObj(cm, "links", lc);

        LinksManager lm = (LinksManager) U.allocateInstance(LinksManager.class);
        setObj(lm, "configManager", cm);

        Method build = LinksManager.class.getDeclaredMethod("buildSettings");
        build.setAccessible(true);
        Object settings = build.invoke(lm);
        setObj(lm, "settingsRef", new AtomicReference<>(settings));

        AdaptiveAdFilter aaf = (AdaptiveAdFilter) U.allocateInstance(AdaptiveAdFilter.class);
        setBool(aaf, "enabled", true);
        setBool(aaf, "highPrecisionMode", true);
        setLong(aaf, "suspicionTtlMillis", 180000L);
        setInt(aaf, "historySize", 10);
        setInt(aaf, "maxLevel", 5);
        setInt(aaf, "elevatedSimilarityPercent", 68);
        setInt(aaf, "highSimilarityPercent", 55);
        setInt(aaf, "minScoreToBlock", 70);
        setBool(aaf, "trackSimilarity", true);
        setBool(aaf, "blockWholeMessageOnAdaptive", true);
        List<String> promo = new ArrayList<>(yaml.getStringList("filter.adaptive-ad-filter.promo-keywords"));
        setObj(aaf, "promoKeywords", promo);
        setObj(aaf, "states", new ConcurrentHashMap<>());
        setObj(aaf, "configManager", cm);
        setObj(lm, "adaptiveAdFilter", aaf);

        LM = lm;
        FIND = LinksManager.class.getMethod("findBlockedLinks", String.class, UUID.class);
    }

    static Method HIT_TEXT;
    static List<?> find(String msg) {
        try { return (List<?>) FIND.invoke(LM, msg, null); }
        catch (Exception e) { throw new RuntimeException("find failed on \"" + msg + "\"", e); }
    }
    static String hitTexts(List<?> hits) throws Exception {
        if (HIT_TEXT == null) HIT_TEXT = hits.get(0).getClass().getMethod("text");
        List<String> t = new ArrayList<>();
        for (Object h : hits) t.add("\"" + HIT_TEXT.invoke(h) + "\"");
        return String.join(", ", t);
    }

    static List<String> readLines(String path) throws Exception {
        List<String> out = new ArrayList<>();
        Path p = Paths.get(path);
        if (!Files.exists(p)) return out;
        for (String s : Files.readAllLines(p, StandardCharsets.UTF_8)) {
            String t = s.replace("﻿", "");
            if (t.isBlank() || t.trim().startsWith("#")) continue;
            out.add(t);
        }
        return out;
    }

    public static void main(String[] args) throws Exception {
        boot();
        StringBuilder rep = new StringBuilder();

        List<String> clean = readLines(SCRATCH + "/ad_clean.txt");
        List<String> ads = readLines(SCRATCH + "/ad_ads.txt");

        rep.append("AD-FILTER BENCH\n").append("=".repeat(70)).append("\n\n");

        int fp = 0;
        rep.append("FALSE POSITIVES (обычная речь ошибочно заблокирована) — из ").append(clean.size()).append("\n");
        for (String s : clean) {
            List<?> hits = find(s);
            if (!hits.isEmpty()) { fp++; rep.append("  FP  \"").append(s).append("\"  -> ").append(hitTexts(hits)).append("\n"); }
        }
        if (fp == 0) rep.append("  (нет)\n");

        int fn = 0;
        rep.append("\nFALSE NEGATIVES (реклама/обход НЕ пойман) — из ").append(ads.size()).append("\n");
        for (String s : ads) {
            List<?> hits = find(s);
            if (hits.isEmpty()) { fn++; rep.append("  MISS \"").append(s).append("\"\n"); }
        }
        if (fn == 0) rep.append("  (нет)\n");

        rep.append("\n").append("=".repeat(70)).append("\n");
        rep.append(String.format("ИТОГ: clean=%d, FP=%d (%.1f%%)   |   ads=%d, FN=%d (recall %.1f%%)%n",
                clean.size(), fp, clean.isEmpty() ? 0.0 : 100.0 * fp / clean.size(),
                ads.size(), fn, ads.isEmpty() ? 0.0 : 100.0 * (ads.size() - fn) / ads.size()));

        Files.write(Paths.get(SCRATCH + "/ad_report.txt"), rep.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("clean=" + clean.size() + " FP=" + fp + " | ads=" + ads.size() + " FN=" + fn);
        System.out.println("report -> ad_report.txt");
    }
}
