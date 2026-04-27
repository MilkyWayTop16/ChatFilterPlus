package org.gw.chatfilterplus.managers;

import org.bukkit.scheduler.BukkitRunnable;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class LogCleanupManager {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;

    private final File badWordsLogFile;
    private final File linksLogFile;
    private final File capsLogFile;
    private final File blockedWordsLogFile;
    private final File antiSpamLogFile;

    private final ThreadLocal<SimpleDateFormat> dateFormat = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        sdf.setLenient(false);
        return sdf;
    });

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private BukkitRunnable cleanupTask;

    public LogCleanupManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        File logsDir = new File(plugin.getDataFolder(), "logs");
        this.badWordsLogFile = new File(logsDir, "badwords-logs.txt");
        this.linksLogFile = new File(logsDir, "links-logs.txt");
        this.capsLogFile = new File(logsDir, "caps-logs.txt");
        this.blockedWordsLogFile = new File(logsDir, "blockedwords-logs.txt");
        this.antiSpamLogFile = new File(logsDir, "antispam-logs.txt");
    }

    public synchronized void startLogCleanupTask() {
        if (cleanupTask != null) return;

        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                executor.submit(() -> {
                    cleanup(badWordsLogFile, configManager.getBadWordsConfig(), "bad-words");
                    cleanup(linksLogFile, configManager.getLinksConfig(), "links");
                    cleanup(capsLogFile, configManager.getCapsConfig(), "caps");
                    cleanup(blockedWordsLogFile, configManager.getBlockedWordsConfig(), "blocked-words");
                    cleanup(antiSpamLogFile, configManager.getAntiSpamConfig(), "anti-spam");
                });
            }
        };

        cleanupTask.runTaskTimerAsynchronously(plugin, 0L, 6000L);
    }

    public synchronized void stopLogCleanupTask() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
    }

    public void appendAndCleanBadWordsLog(String entry) {
        appendAndClean(badWordsLogFile, entry, configManager.getBadWordsConfig(), "bad-words");
    }

    public void appendAndCleanLinksLog(String entry) {
        appendAndClean(linksLogFile, entry, configManager.getLinksConfig(), "links");
    }

    public void appendAndCleanCapsLog(String entry) {
        appendAndClean(capsLogFile, entry, configManager.getCapsConfig(), "caps");
    }

    public void appendAndCleanBlockedWordsLog(String entry) {
        appendAndClean(blockedWordsLogFile, entry, configManager.getBlockedWordsConfig(), "blocked-words");
    }

    public void appendAndCleanAntiSpamLog(String entry) {
        appendAndClean(antiSpamLogFile, entry, configManager.getAntiSpamConfig(), "anti-spam");
    }

    private void appendAndClean(File file, String entry, org.bukkit.configuration.ConfigurationSection cfg, String section) {
        executor.submit(() -> {
            try {
                ensureParentDirectory(file);
                appendLog(file, entry);
                cleanup(file, cfg, section);
            } catch (IOException e) {
                plugin.console("Ошибка записи в " + file.getName() + ": " + e.getMessage());
            }
        });
    }

    private void cleanup(File file, org.bukkit.configuration.ConfigurationSection cfg, String section) {
        if (!cfg.getBoolean("logs.file." + section + ".cleanup.enabled", true)) return;

        String mode = cfg.getString("logs.file." + section + ".cleanup.mode", "remove-oldest");

        try {
            if ("truncate".equals(mode)) {
                long max = cfg.getLong("logs.file." + section + ".cleanup.max-lines", 10000);
                if (countLines(file) >= max) truncateFile(file);
            } else if ("keep-latest".equals(mode)) {
                long max = cfg.getLong("logs.file." + section + ".cleanup.max-lines", 10000);
                keepLatestLines(file, max);
            } else if ("remove-oldest".equals(mode)) {
                long retention = cfg.getLong("logs.file." + section + ".cleanup.retention-period", 7 * 24 * 60 * 60 * 1000L);
                removeOldestLines(file, retention);
            }
        } catch (IOException e) {
            plugin.console("Не удалось очистить " + file.getName() + ": " + e.getMessage());
        }
    }

    private void appendLog(File file, String entry) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            writer.write(entry);
            writer.newLine();
        }
    }

    private void truncateFile(File file) throws IOException {
        Files.writeString(file.toPath(), "");
    }

    private void keepLatestLines(File file, long maxLines) throws IOException {
        if (!file.exists()) return;

        Deque<String> lines = new ArrayDeque<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
                if (lines.size() > maxLines) lines.pollFirst();
            }
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String line : lines) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private void removeOldestLines(File file, long retentionMillis) throws IOException {
        if (!file.exists()) return;

        long now = System.currentTimeMillis();
        List<String> kept = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String ts = extractTimestamp(line);
                if (ts != null) {
                    try {
                        long time = dateFormat.get().parse(ts).getTime();
                        if (now - time <= retentionMillis) kept.add(line);
                    } catch (Exception e) {
                        kept.add(line);
                    }
                } else {
                    kept.add(line);
                }
            }
        }

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
            for (String line : kept) {
                writer.write(line);
                writer.newLine();
            }
        }
    }

    private long countLines(File file) throws IOException {
        if (!file.exists()) return 0;
        long count = 0;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.readLine() != null) count++;
        }
        return count;
    }

    private String extractTimestamp(String line) {
        if (line == null || line.length() < 21 || line.charAt(0) != '[' || line.charAt(20) != ']') return null;
        return line.substring(1, 21);
    }

    private void ensureParentDirectory(File file) {
        File parent = file.getParentFile();
        if (!parent.exists()) parent.mkdirs();
    }

    public void shutdown() {
        stopLogCleanupTask();
        executor.shutdown();
    }

    public synchronized void reload() {
        stopLogCleanupTask();
        startLogCleanupTask();
    }
}