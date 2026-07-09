package org.gw.chatfilterplus.configs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

public final class SpamConfigMigrator {

    private static final String LEGACY_FILE = "anti-spam.yml";
    private static final String CURRENT_FILE = "spam.yml";

    private static final String[][] SECTION_MOVES = {
            {"filter.anti-spam", "filter.spam"},
            {"logs.file.anti-spam", "logs.file.spam"},
            {"notifications.anti-spam", "notifications.spam"},
            {"punishments.anti-spam", "punishments.spam"}
    };

    private static final String[][] LOG_FILE_MOVES = {
            {"antispam-logs.txt", "spam-logs.txt"},
            {"antispam-punishments-logs.txt", "spam-punishments-logs.txt"}
    };

    private final ChatFilterPlus plugin;

    public SpamConfigMigrator(ChatFilterPlus plugin) {
        this.plugin = plugin;
    }

    public void migrate() {
        File dataFolder = plugin.getDataFolder();
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            return;
        }

        File legacyFile = new File(dataFolder, LEGACY_FILE);
        File currentFile = new File(dataFolder, CURRENT_FILE);

        try {
            if (legacyFile.exists()) {
                if (!currentFile.exists()) {
                    moveFile(legacyFile, currentFile);
                    plugin.log("Миграция из версии 2.1 в 2.2: &#ffff00anti-spam.yml &fв &#ffff00spam.yml &f(настройки все сохранены)");
                } else {
                    mergeLegacyIntoCurrent(legacyFile, currentFile);
                    archiveFile(legacyFile, "anti-spam.yml");
                    plugin.log("Миграция из версии 2.1 в 2.2: настройки из &#ffff00anti-spam.yml &fперенесены в &#ffff00spam.yml");
                }
            }

            if (currentFile.exists()) {
                boolean changed = transformKeysInPlace(currentFile);
                if (changed) {
                    plugin.log("Миграция из версии 2.1 в 2.2: ключи &#ffff00anti-spam &fв &#ffff00spam &fв spam.yml");
                }
            }

            migrateLogFiles(dataFolder);
        } catch (Exception e) {
            plugin.error("Ошибка миграции anti-spam.yml в spam.yml: " + e.getMessage());
        }
    }

    private void mergeLegacyIntoCurrent(File legacyFile, File currentFile) throws IOException {
        FileConfiguration legacy = YamlConfiguration.loadConfiguration(legacyFile);
        FileConfiguration current = YamlConfiguration.loadConfiguration(currentFile);
        boolean changed = false;

        for (String[] move : SECTION_MOVES) {
            if (!legacy.contains(move[0])) continue;
            copySectionPreferSource(legacy, move[0], current, move[1]);
            changed = true;
        }

        if (legacy.contains("config-version") && !current.contains("config-version")) {
            current.set("config-version", legacy.get("config-version"));
            changed = true;
        }

        changed |= rewritePermissionStrings(current);

        if (changed) {
            createBackup(currentFile);
            current.save(currentFile);
        }
    }

    private boolean transformKeysInPlace(File file) throws IOException {
        FileConfiguration config = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;

        for (String[] move : SECTION_MOVES) {
            String from = move[0];
            String to = move[1];
            if (!config.contains(from)) continue;

            copySectionPreferSource(config, from, config, to);
            config.set(from, null);
            changed = true;
        }

        changed |= rewritePermissionStrings(config);

        if (changed) {
            createBackup(file);
            config.save(file);
        }
        return changed;
    }

    private void copySectionPreferSource(FileConfiguration source,
                                         String sourcePath,
                                         FileConfiguration target,
                                         String targetPath) {
        if (!source.contains(sourcePath)) return;

        if (!source.isConfigurationSection(sourcePath)) {
            target.set(targetPath, source.get(sourcePath));
            return;
        }

        ConfigurationSection section = source.getConfigurationSection(sourcePath);
        if (section == null) return;

        for (String key : section.getKeys(true)) {
            if (section.isConfigurationSection(key)) continue;
            target.set(targetPath + "." + key, section.get(key));
        }
    }

    private boolean rewritePermissionStrings(FileConfiguration config) {
        boolean changed = false;
        for (String path : config.getKeys(true)) {
            if (config.isConfigurationSection(path)) continue;
            Object value = config.get(path);

            if (value instanceof String str) {
                String updated = rewritePermission(str);
                if (!updated.equals(str)) {
                    config.set(path, updated);
                    changed = true;
                }
            } else if (value instanceof List<?> list) {
                boolean listChanged = false;
                List<Object> newList = new ArrayList<>(list.size());
                for (Object item : list) {
                    if (item instanceof String s) {
                        String updated = rewritePermission(s);
                        newList.add(updated);
                        if (!updated.equals(s)) listChanged = true;
                    } else {
                        newList.add(item);
                    }
                }
                if (listChanged) {
                    config.set(path, newList);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private String rewritePermission(String value) {
        return value
                .replace("chatfilterplus.bypass.chatfilter.antispam", "chatfilterplus.bypass.chatfilter.spam")
                .replace("chatfilterplus.bypass.punishment.antispam", "chatfilterplus.bypass.punishment.spam");
    }

    private void migrateLogFiles(File dataFolder) {
        File logsDir = new File(dataFolder, "logs");
        if (!logsDir.isDirectory()) return;

        for (String[] move : LOG_FILE_MOVES) {
            File from = new File(logsDir, move[0]);
            File to = new File(logsDir, move[1]);
            if (!from.exists()) continue;
            try {
                if (!to.exists()) {
                    moveFile(from, to);
                    plugin.log("Миграция логов: &#ffff00" + move[0] + " &fв &#ffff00" + move[1]);
                } else {
                    archiveFile(from, move[0]);
                }
            } catch (Exception e) {
                plugin.error("Не удалось перенести лог " + move[0] + ": " + e.getMessage());
            }
        }
    }

    private void moveFile(File from, File to) throws IOException {
        File parent = to.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try {
            Files.move(from.toPath(), to.toPath());
        } catch (IOException ex) {
            Files.copy(from.toPath(), to.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(from.toPath());
        }
    }

    private void archiveFile(File file, String baseName) {
        if (!file.exists()) return;
        try {
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists()) backupDir.mkdirs();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File target = new File(backupDir, baseName + ".migrated-" + timestamp);
            Files.move(file.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            plugin.error("Не удалось архивировать " + file.getName() + ": " + e.getMessage());
        }
    }

    private void createBackup(File file) {
        if (!file.exists()) return;
        try {
            File backupDir = new File(file.getParentFile(), "backups");
            if (!backupDir.exists()) backupDir.mkdirs();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File backup = new File(backupDir, file.getName() + ".bak-" + timestamp);
            Files.copy(file.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            plugin.error("Не удалось создать бэкап " + file.getName() + ": " + e.getMessage());
        }
    }
}
