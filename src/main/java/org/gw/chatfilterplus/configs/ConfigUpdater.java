package org.gw.chatfilterplus.configs;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ConfigUpdater {

    private final ChatFilterPlus plugin;

    public ConfigUpdater(ChatFilterPlus plugin) {
        this.plugin = plugin;
    }

    public boolean update(File targetFile, String resourcePath) {
        if (!targetFile.exists()) return false;

        FileConfiguration userConfig = YamlConfiguration.loadConfiguration(targetFile);
        FileConfiguration defaultConfig = loadDefaultConfig(resourcePath);

        if (defaultConfig == null) {
            plugin.error("Не удалось загрузить дефолтный конфиг: " + resourcePath);
            return false;
        }

        int userVersion = getVersion(userConfig);
        int defaultVersion = getVersion(defaultConfig);

        if (userVersion >= defaultVersion) return false;

        boolean changed = mergeMissingKeys(userConfig, defaultConfig, "");

        if (changed) {
            createBackup(targetFile);
        }

        userConfig.set("config-version", defaultConfig.getString("config-version", "1.0"));
        saveConfig(targetFile, userConfig, "Конфиг &#ffff00" + targetFile.getName() + " &fобновлён до версии &#ffff00" + defaultConfig.getString("config-version", "2.1"));
        return changed;
    }

    private int getVersion(FileConfiguration config) {
        String versionStr = config.getString("config-version", "1.0");
        try {
            return (int) Math.round(Double.parseDouble(versionStr) * 10);
        } catch (Exception e) {
            return 10;
        }
    }

    /**
     * Technical reference lists that must gain new default entries on upgrade. Without this an
     * existing config keeps its old list forever (a present key is never touched), so a server
     * upgrading from an older version would detect fewer domains than a fresh install.
     * Only lists that are reference data belong here — user-owned lists (whitelist domains,
     * exception players/groups, bad-words) must never be repopulated behind the admin's back.
     */
    private static final Set<String> UNION_MERGED_LISTS = Set.of(
            "filter.smart-detection.tlds",
            "filter.smart-detection.quick-triggers"
    );

    private boolean mergeMissingKeys(FileConfiguration user, FileConfiguration defaults, String path) {
        boolean changed = false;
        ConfigurationSection defaultSection = path.isEmpty() ? defaults : defaults.getConfigurationSection(path);

        if (defaultSection == null) return false;

        for (String key : defaultSection.getKeys(false)) {
            String fullKey = path.isEmpty() ? key : path + "." + key;

            if (!user.contains(fullKey)) {
                user.set(fullKey, defaultSection.get(key));
                changed = true;
                continue;
            }

            if (UNION_MERGED_LISTS.contains(fullKey)) {
                changed |= mergeListUnion(user, defaultSection, key, fullKey);
                continue;
            }

            if (defaultSection.isConfigurationSection(key)) {
                if (!user.isConfigurationSection(fullKey)) {
                    user.createSection(fullKey);
                }
                changed |= mergeMissingKeys(user, defaults, fullKey);
            }
        }
        return changed;
    }

    /** Appends default entries the user's list is missing, preserving their own order and additions. */
    private boolean mergeListUnion(FileConfiguration user, ConfigurationSection defaults,
                                   String key, String fullKey) {
        List<String> defaultList = defaults.getStringList(key);
        if (defaultList.isEmpty()) return false;

        List<String> userList = user.getStringList(fullKey);
        Set<String> seen = new HashSet<>();
        for (String value : userList) {
            if (value != null) seen.add(value.trim().toLowerCase(Locale.ROOT));
        }

        List<String> merged = new ArrayList<>(userList);
        boolean added = false;
        for (String value : defaultList) {
            if (value == null) continue;
            if (seen.add(value.trim().toLowerCase(Locale.ROOT))) {
                merged.add(value);
                added = true;
            }
        }

        if (added) user.set(fullKey, merged);
        return added;
    }

    private FileConfiguration loadDefaultConfig(String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return null;
            return YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    private void createBackup(File file) {
        try {
            File backupDir = new File(file.getParentFile(), "backups");
            if (!backupDir.exists()) backupDir.mkdirs();

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
            File backup = new File(backupDir, file.getName() + ".bak-" + timestamp);
            Files.copy(file.toPath(), backup.toPath());
        } catch (Exception e) {
            plugin.error("Не удалось создать бэкап файла " + file.getName() + ": " + e.getMessage());
        }
    }

    private void saveConfig(File file, FileConfiguration config, String successMessage) {
        try {
            config.save(file);
            plugin.log(successMessage);
        } catch (Exception e) {
            plugin.error("Ошибка сохранения файла " + file.getName() + ": " + e.getMessage());
        }
    }
}
