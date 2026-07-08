package org.gw.chatfilterplus.managers;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.configs.ConfigUtils;

import java.io.*;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LogCleanupManager {

    private static final long DEFAULT_RETENTION_MILLIS = 7 * 24 * 60 * 60 * 1000L;
    private static final long DEFAULT_MAX_LINES = 10000L;

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;

    private final Map<FilterType, File> logFiles = new EnumMap<>(FilterType.class);

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
        for (FilterType type : FilterType.values()) {
            logFiles.put(type, new File(logsDir, type.logFileName()));
        }
    }

    public synchronized void startLogCleanupTask() {
        if (cleanupTask != null) return;

        cleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                executor.submit(() -> {
                    for (FilterType type : FilterType.values()) {
                        cleanup(type);
                    }
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

    public void appendLog(FilterType type, String entry) {
        File file = logFiles.get(type);
        executor.submit(() -> {
            try {
                ensureParentDirectory(file);
                appendLog(file, entry);
            } catch (IOException e) {
                plugin.console("Ошибка записи в " + file.getName() + ": " + e.getMessage());
            }
        });
    }

    private void cleanup(FilterType type) {
        FileConfiguration cfg = type.config(configManager);
        if (!cfg.getBoolean(type.logPath("cleanup.enabled"), true)) return;

        File file = logFiles.get(type);
        String mode = cfg.getString(type.logPath("cleanup.mode"), "remove-oldest");

        try {
            switch (mode) {
                case "truncate" -> {
                    long max = cfg.getLong(type.logPath("cleanup.max-lines"), DEFAULT_MAX_LINES);
                    if (countLines(file) >= max) truncateFile(file);
                }
                case "keep-latest" -> {
                    long max = cfg.getLong(type.logPath("cleanup.max-lines"), DEFAULT_MAX_LINES);
                    keepLatestLines(file, max);
                }
                case "remove-oldest" -> {
                    long retention = ConfigUtils.parseRetentionPeriod(
                            cfg.getString(type.logPath("cleanup.retention-period")), DEFAULT_RETENTION_MILLIS);
                    removeOldestLines(file, retention);
                }
                default -> plugin.console("&#FF5D00Неизвестный режим очистки логов: " + mode);
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
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    public synchronized void reload() {
        stopLogCleanupTask();
        startLogCleanupTask();
    }
}
