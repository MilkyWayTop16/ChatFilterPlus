package org.gw.chatfilterplus.manager;

import org.bukkit.scheduler.BukkitRunnable;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

public class LogCleanupManager {
    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final File badWordsLogFile;
    private final File linksLogFile;
    private final SimpleDateFormat dateFormat;
    private static final String TIMESTAMP_PATTERN = "\\[\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\]";
    private BukkitRunnable badWordsLogCleanupTask;
    private BukkitRunnable linksLogCleanupTask;

    public LogCleanupManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.badWordsLogFile = new File(plugin.getDataFolder(), "badwords-logs.txt");
        this.linksLogFile = new File(plugin.getDataFolder(), "links-logs.txt");
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        this.dateFormat.setLenient(false);
    }

    public void startLogCleanupTask() {
        if (configManager.isBadWordsCleanupEnabled()) {
            badWordsLogCleanupTask = new BukkitRunnable() {
                @Override
                public void run() {
                    checkAndCleanBadWordsLogFile();
                }
            };
            long retentionMillis = configManager.getBadWordsCleanupRetentionMillis();
            long ticks = retentionMillis <= 60 * 1000L ? 100L : retentionMillis / (50L * 6);
            ticks = Math.max(ticks, 20L);
            badWordsLogCleanupTask.runTaskTimer(plugin, 0L, ticks);
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Запущен планировщик очистки логов матов с интервалом " + (ticks * 50 / 1000.0) + " секунд.");
            }
        }
        if (configManager.isLinksCleanupEnabled()) {
            linksLogCleanupTask = new BukkitRunnable() {
                @Override
                public void run() {
                    checkAndCleanLinksLogFile();
                }
            };
            long retentionMillis = configManager.getLinksCleanupRetentionMillis();
            long ticks = retentionMillis <= 60 * 1000L ? 100L : retentionMillis / (50L * 6);
            ticks = Math.max(ticks, 20L);
            linksLogCleanupTask.runTaskTimer(plugin, 0L, ticks);
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Запущен планировщик очистки логов ссылок с интервалом " + (ticks * 50 / 1000.0) + " секунд.");
            }
        }
    }

    public void stopLogCleanupTask() {
        if (badWordsLogCleanupTask != null) {
            badWordsLogCleanupTask.cancel();
            badWordsLogCleanupTask = null;
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Планировщик очистки логов матов остановлен.");
            }
        }
        if (linksLogCleanupTask != null) {
            linksLogCleanupTask.cancel();
            linksLogCleanupTask = null;
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Планировщик очистки логов ссылок остановлен.");
            }
        }
    }

    public void checkAndCleanBadWordsLogFile() {
        String mode = configManager.getBadWordsCleanupMode();
        if ("truncate".equals(mode)) {
            long lineCount = countLines(badWordsLogFile);
            if (lineCount >= configManager.getBadWordsCleanupMaxLines()) {
                truncateLogFile(badWordsLogFile, "badwords-logs.txt");
            }
        } else if ("keep-latest".equals(mode)) {
            long lineCount = countLines(badWordsLogFile);
            if (lineCount >= configManager.getBadWordsCleanupMaxLines()) {
                keepLatestLogLines(badWordsLogFile, null, configManager.getBadWordsCleanupMaxLines(), "badwords-logs.txt");
            }
        } else if ("remove-oldest".equals(mode)) {
            removeOldestLogLines(badWordsLogFile, configManager.getBadWordsCleanupRetentionMillis(), "badwords-logs.txt");
        }
    }

    public void checkAndCleanLinksLogFile() {
        String mode = configManager.getLinksCleanupMode();
        if ("truncate".equals(mode)) {
            long lineCount = countLines(linksLogFile);
            if (lineCount >= configManager.getLinksCleanupMaxLines()) {
                truncateLogFile(linksLogFile, "links-logs.txt");
            }
        } else if ("keep-latest".equals(mode)) {
            long lineCount = countLines(linksLogFile);
            if (lineCount >= configManager.getLinksCleanupMaxLines()) {
                keepLatestLogLines(linksLogFile, null, configManager.getLinksCleanupMaxLines(), "links-logs.txt");
            }
        } else if ("remove-oldest".equals(mode)) {
            removeOldestLogLines(linksLogFile, configManager.getLinksCleanupRetentionMillis(), "links-logs.txt");
        }
    }

    public void appendAndCleanBadWordsLog(String newLogEntry) {
        String mode = configManager.getBadWordsCleanupMode();
        if ("truncate".equals(mode)) {
            long lineCount = countLines(badWordsLogFile);
            if (lineCount >= configManager.getBadWordsCleanupMaxLines()) {
                truncateLogFile(badWordsLogFile, "badwords-logs.txt");
            }
            appendLog(badWordsLogFile, newLogEntry, "badwords-logs.txt");
        } else if ("keep-latest".equals(mode)) {
            keepLatestLogLines(badWordsLogFile, newLogEntry, configManager.getBadWordsCleanupMaxLines(), "badwords-logs.txt");
        } else if ("remove-oldest".equals(mode)) {
            appendLog(badWordsLogFile, newLogEntry, "badwords-logs.txt");
            removeOldestLogLines(badWordsLogFile, configManager.getBadWordsCleanupRetentionMillis(), "badwords-logs.txt");
        } else {
            appendLog(badWordsLogFile, newLogEntry, "badwords-logs.txt");
        }
    }

    public void appendAndCleanLinksLog(String newLogEntry) {
        String mode = configManager.getLinksCleanupMode();
        if ("truncate".equals(mode)) {
            long lineCount = countLines(linksLogFile);
            if (lineCount >= configManager.getLinksCleanupMaxLines()) {
                truncateLogFile(linksLogFile, "links-logs.txt");
            }
            appendLog(linksLogFile, newLogEntry, "links-logs.txt");
        } else if ("keep-latest".equals(mode)) {
            keepLatestLogLines(linksLogFile, newLogEntry, configManager.getLinksCleanupMaxLines(), "links-logs.txt");
        } else if ("remove-oldest".equals(mode)) {
            appendLog(linksLogFile, newLogEntry, "links-logs.txt");
            removeOldestLogLines(linksLogFile, configManager.getLinksCleanupRetentionMillis(), "links-logs.txt");
        } else {
            appendLog(linksLogFile, newLogEntry, "links-logs.txt");
        }
    }

    private void truncateLogFile(File logFile, String fileName) {
        try {
            new FileWriter(logFile, false).close();
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Файл " + fileName + " очищен в режиме truncate.");
            }
        } catch (IOException e) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().log(Level.WARNING, "Не удалось очистить файл " + fileName + " в режиме truncate: " + e.getMessage(), e);
            }
        }
    }

    private void removeOldestLogLines(File logFile, long retentionMillis, String fileName) {
        try {
            long currentTime = System.currentTimeMillis();
            List<String> lines = new ArrayList<>();
            List<String> removedLines = new ArrayList<>();
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Начало проверки логов " + fileName + " на удаление. Текущее время: " + dateFormat.format(new Date(currentTime)) + " (" + currentTime + " мс), период хранения: " + (retentionMillis / 1000.0) + " секунд.");
            }
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String timestamp = extractTimestamp(line);
                    if (timestamp != null) {
                        try {
                            long lineTime = dateFormat.parse(timestamp).getTime();
                            long ageMillis = currentTime - lineTime;
                            if (ageMillis <= retentionMillis) {
                                lines.add(line);
                                if (configManager.isConsoleLogsEnabled()) {
                                    plugin.getLogger().info("Сохранена строка в " + fileName + ": " + line + " (время: " + lineTime + " мс, возраст: " + (ageMillis / 1000.0) + " секунд, timestamp: " + timestamp + ").");
                                }
                            } else {
                                removedLines.add(line);
                                if (configManager.isConsoleLogsEnabled()) {
                                    plugin.getLogger().info("Помечена для удаления строка из " + fileName + ": " + line + " (время: " + lineTime + " мс, возраст: " + (ageMillis / 1000.0) + " секунд, timestamp: " + timestamp + ").");
                                }
                            }
                        } catch (ParseException e) {
                            lines.add(line);
                            if (configManager.isConsoleLogsEnabled()) {
                                plugin.getLogger().warning("Не удалось разобрать временную метку в строке из " + fileName + ": " + line + " (" + e.getMessage() + ")");
                            }
                        }
                    } else {
                        lines.add(line);
                        if (configManager.isConsoleLogsEnabled()) {
                            plugin.getLogger().warning("Строка без временной метки в " + fileName + ": " + line);
                        }
                    }
                }
            }
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile))) {
                for (String line : lines) {
                    writer.write(line);
                    writer.newLine();
                }
            }
            if (configManager.isConsoleLogsEnabled() && !removedLines.isEmpty()) {
                plugin.getLogger().info("Удалено " + removedLines.size() + " строк старше " + (retentionMillis / 1000.0) + " секунд из " + fileName + ".");
                for (String removedLine : removedLines) {
                    plugin.getLogger().info("Удалена строка из " + fileName + ": " + removedLine);
                }
            }
        } catch (IOException e) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().log(Level.WARNING, "Не удалось удалить старые строки из " + fileName + ": " + e.getMessage(), e);
            }
        }
    }

    private void keepLatestLogLines(File logFile, String newLogEntry, long maxLines, String fileName) {
        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    lines.add(line);
                }
            }
            if (lines.size() >= maxLines || newLogEntry != null) {
                int keepCount = newLogEntry != null ? (int) maxLines - 1 : (int) maxLines;
                if (lines.size() > keepCount) {
                    lines = lines.subList(lines.size() - keepCount, lines.size());
                }
                if (newLogEntry != null) {
                    lines.add(newLogEntry);
                }
                try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile))) {
                    for (String line : lines) {
                        writer.write(line);
                        writer.newLine();
                    }
                }
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Сохранены последние " + lines.size() + " строк в " + fileName + ".");
                }
            }
        } catch (IOException e) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().log(Level.WARNING, "Не удалось сохранить последние строки в " + fileName + ": " + e.getMessage(), e);
            }
        }
    }

    private void appendLog(File logFile, String newLogEntry, String fileName) {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(logFile, true))) {
            writer.write(newLogEntry);
            writer.newLine();
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Записана строка в " + fileName + ": " + newLogEntry);
            }
        } catch (IOException e) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().log(Level.WARNING, "Не удалось записать строку в " + fileName + ": " + e.getMessage(), e);
            }
        }
    }

    private long countLines(File logFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            return reader.lines().count();
        } catch (IOException e) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().log(Level.WARNING, "Не удалось подсчитать строки в " + logFile.getName() + ": " + e.getMessage(), e);
            }
            return 0;
        }
    }

    private String extractTimestamp(String line) {
        if (line.matches(TIMESTAMP_PATTERN + ".*")) {
            String timestamp = line.substring(1, 19);
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Извлечена временная метка: " + timestamp + " из строки: " + line);
            }
            return timestamp;
        }
        if (configManager.isConsoleLogsEnabled()) {
            plugin.getLogger().warning("Не удалось извлечь временную метку из строки: " + line);
        }
        return null;
    }
}