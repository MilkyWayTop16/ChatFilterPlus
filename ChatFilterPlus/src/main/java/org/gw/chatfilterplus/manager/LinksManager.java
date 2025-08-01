package org.gw.chatfilterplus.manager;

import org.gw.chatfilterplus.ChatFilterPlus;

import java.util.List;
import java.util.regex.Pattern;

public class LinksManager {
    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private Pattern linkPattern;
    private boolean listFilterEnabled;
    private String listFilterMode;
    private List<String> listFilterDomains;

    public LinksManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        loadLinkPattern();
    }

    public void loadLinkPattern() {
        String regex = configManager.getLinksRegex();
        try {
            linkPattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            listFilterEnabled = configManager.isLinksListFilterEnabled();
            listFilterMode = configManager.getLinksListFilterMode();
            listFilterDomains = configManager.getLinksListFilterDomains();
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Загружен шаблон для ссылок: " + regex);
                plugin.getLogger().info("Белый/чёрный список ссылок: enabled=" + listFilterEnabled + ", mode=" + listFilterMode + ", domains=" + listFilterDomains);
            }
        } catch (Exception e) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Ошибка при компиляции шаблона для ссылок: " + e.getMessage());
            }
            linkPattern = Pattern.compile("(https?://\\S+|\\S+\\.(com|org|net|ru|io|me|info|biz|co|edu|gov)\\S*)", Pattern.CASE_INSENSITIVE);
        }
    }

    public Pattern getLinkPattern() {
        return linkPattern;
    }

    public boolean isLinkAllowed(String link) {
        String normalizedLink = link.toLowerCase().trim();

        boolean matchesRegex = linkPattern.matcher(normalizedLink).matches();

        if (!configManager.isLinksFilterEnabled()) {
            return true;
        }

        if (!listFilterEnabled) {
            if (matchesRegex && configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Ссылка " + link + " заблокирована по regex, так как list-filter отключён.");
            }
            return !matchesRegex;
        }

        String domain = normalizedLink
                .replaceFirst("^(https?://|h\\s*t\\s*t\\s*p\\s*s?://)", "")
                .replaceAll("/.*$", "");
        if (domain.isEmpty()) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Не удалось извлечь домен из ссылки: " + link);
            }
            return false;
        }

        boolean inList = listFilterDomains.stream().anyMatch(entry -> {
            String normalizedEntry = entry.toLowerCase().trim();
            if (normalizedEntry.matches("^(https?://|h\\s*t\\s*t\\s*p\\s*s?://).+")) {
                return normalizedLink.equals(normalizedEntry) ||
                        normalizedLink.equals(normalizedEntry.replaceFirst("^h\\s*t\\s*t\\s*p\\s*s?://", "https://"));
            } else {
                return domain.equalsIgnoreCase(normalizedEntry) || domain.endsWith("." + normalizedEntry);
            }
        });

        if ("whitelist".equalsIgnoreCase(listFilterMode)) {
            if (inList && configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Ссылка " + link + " разрешена по белому списку.");
            }
            return inList;
        } else if ("blacklist".equalsIgnoreCase(listFilterMode)) {
            if (!inList && matchesRegex && configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Ссылка " + link + " заблокирована по regex, так как не в чёрном списке.");
            }
            return !inList && !matchesRegex;
        }

        if (configManager.isConsoleLogsEnabled()) {
            plugin.getLogger().warning("Неизвестный режим фильтрации списка: " + listFilterMode + ", ссылка разрешена по умолчанию.");
        }
        return true;
    }
}