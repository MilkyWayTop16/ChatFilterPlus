package org.gw.chatfilterplus.manager;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.HexColors;
import org.jetbrains.annotations.NotNull;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.List;

public class NotificationManager {
    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final boolean useLegacyTitles;

    public NotificationManager(ChatFilterPlus plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        String version = Bukkit.getBukkitVersion().split("-")[0];
        this.useLegacyTitles = isLegacyVersion(version);
        if (configManager.isConsoleLogsEnabled()) {
            plugin.getLogger().info("Используется " + (useLegacyTitles ? "устаревший" : "современный") + " метод отправки тайтлов для версии " + version);
        }
    }

    private boolean isLegacyVersion(String version) {
        try {
            String[] parts = version.split("\\.");
            int major = Integer.parseInt(parts[1]);
            return major < 18;
        } catch (Exception e) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().warning("Ошибка определения версии сервера: " + e.getMessage() + ". Используется устаревший метод тайтлов.");
            }
            return true;
        }
    }

    private Title.Times createTitleTimes(long fadeInMillis, long stayMillis, long fadeOutMillis) {
        if (useLegacyTitles) {
            try {
                Method ofMethod = Title.Times.class.getMethod("of", long.class, long.class, long.class);
                return (Title.Times) ofMethod.invoke(null, fadeInMillis, stayMillis, fadeOutMillis);
            } catch (Exception e) {
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().warning("Ошибка создания Title.Times для старых версий: " + e.getMessage());
                }
                return Title.Times.of(Duration.ofMillis(fadeInMillis), Duration.ofMillis(stayMillis), Duration.ofMillis(fadeOutMillis));
            }
        } else {
            return Title.Times.of(
                    Duration.ofMillis(fadeInMillis),
                    Duration.ofMillis(stayMillis),
                    Duration.ofMillis(fadeOutMillis)
            );
        }
    }

    public void notifyPlayerBadWords(Player player, String badWord) {
        if (!configManager.isBadWordsNotificationsEnabled()) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Уведомления для матов отключены, пропуск уведомления для игрока " + player.getName());
            }
            return;
        }

        if (configManager.isBadWordsChatEnabled() && !plugin.getPunishmentManager().isBadWordsNotificationDisabled(player, "message")) {
            if (!configManager.getBadWordsFilterWarningMessage().isEmpty()) {
                HexColors.sendMessage(player, configManager.getBadWordsFilterWarningMessage());
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Отправлено сообщение в чат игроку " + player.getName() + ": " + configManager.getBadWordsFilterWarningMessage());
                }
            }
        }

        if (configManager.isBadWordsTitleEnabled() && !plugin.getPunishmentManager().isBadWordsNotificationDisabled(player, "title")) {
            if (!configManager.getBadWordsTitleText().isEmpty()) {
                String[] titleParts = configManager.getBadWordsTitleText().split("\n", 2);
                String mainTitleText = titleParts[0];
                String subTitleText = titleParts.length > 1 ? titleParts[1] : "";
                Component mainTitle = HexColors.translateToComponent(mainTitleText);
                Component subTitle = HexColors.translateToComponent(subTitleText);

                Title.Times times = createTitleTimes(
                        configManager.getBadWordsTitleFadeInTicks() * 50L,
                        configManager.getBadWordsTitleStayTicks() * 50L,
                        configManager.getBadWordsTitleFadeOutTicks() * 50L
                );

                player.showTitle(Title.title(mainTitle, subTitle, times));
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Отправлен тайтл игроку " + player.getName() + ": mainTitle=" + mainTitleText + ", subTitle=" + subTitleText);
                }
            }
        }

        if (configManager.isBadWordsActionBarEnabled() && !plugin.getPunishmentManager().isBadWordsNotificationDisabled(player, "actionbar")) {
            if (!configManager.getBadWordsActionBarText().isEmpty()) {
                Component actionBarComponent = HexColors.translateToComponent(configManager.getBadWordsActionBarText());
                player.sendActionBar(actionBarComponent);
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Отправлен экшенбар игроку " + player.getName() + ": " + configManager.getBadWordsActionBarText());
                }
            }
        }

        if (configManager.isBadWordsPlayerSoundEnabled() && !plugin.getPunishmentManager().isBadWordsNotificationDisabled(player, "sound")) {
            try {
                Sound sound = Sound.valueOf(configManager.getBadWordsPlayerSoundName());
                player.playSound(player.getLocation(), sound, configManager.getBadWordsPlayerSoundVolume(), configManager.getBadWordsPlayerSoundPitch());
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Проигран звук игроку " + player.getName() + ": " + configManager.getBadWordsPlayerSoundName());
                }
            } catch (IllegalArgumentException e) {
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().warning("Неверное имя звука для игрока (маты): " + configManager.getBadWordsPlayerSoundName());
                }
            }
        }
    }

    public void notifyConsoleBadWords(Player player, List<String> badWords) {
        if (!configManager.isBadWordsConsoleNotificationsEnabled()) {
            return;
        }

        String notifyMessage;
        if (badWords.size() == 1) {
            notifyMessage = configManager.getBadWordsConsoleNotifyMessage()
                    .replace("{player}", player.getName())
                    .replace("{words}", configManager.getBadWordsConsoleSingleWordTemplate().replace("{word}", badWords.get(0)));
        } else {
            StringBuilder wordsFormatted = new StringBuilder();
            for (int i = 0; i < badWords.size(); i++) {
                wordsFormatted.append(configManager.getBadWordsConsoleWordTemplate().replace("{word}", badWords.get(i)));
                if (i < badWords.size() - 1) {
                    wordsFormatted.append(configManager.getBadWordsConsoleWordsSeparator());
                }
            }
            notifyMessage = configManager.getBadWordsConsoleNotifyMessage()
                    .replace("{player}", player.getName())
                    .replace("{words}", wordsFormatted.toString());
        }

        String coloredMessage = HexColors.colorize(notifyMessage, configManager.isConsoleLogsEnabled(), plugin.getLogger());
        plugin.getLogger().info(coloredMessage);
    }

    public void notifyAdminsBadWords(Player player, List<String> badWords) {
        if (!configManager.isBadWordsNotificationsEnabled()) {
            return;
        }

        String notifyMessage;
        if (badWords.size() == 1) {
            notifyMessage = configManager.getBadWordsAdminNotifyMessage()
                    .replace("{player}", player.getName())
                    .replace("{words}", configManager.getBadWordsAdminSingleWordTemplate().replace("{word}", badWords.get(0)));
        } else {
            StringBuilder wordsFormatted = new StringBuilder();
            for (int i = 0; i < badWords.size(); i++) {
                wordsFormatted.append(configManager.getBadWordsAdminWordTemplate().replace("{word}", badWords.get(i)));
                if (i < badWords.size() - 1) {
                    wordsFormatted.append(configManager.getBadWordsAdminWordsSeparator());
                }
            }
            notifyMessage = configManager.getBadWordsAdminNotifyMessage()
                    .replace("{player}", player.getName())
                    .replace("{words}", wordsFormatted.toString());
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player admin : Bukkit.getOnlinePlayers()) {
                    if (admin.hasPermission("chatfilterplus.admin.notify")) {
                        HexColors.sendMessage(admin, notifyMessage);
                        if (configManager.isBadWordsAdminSoundEnabled()) {
                            try {
                                Sound sound = Sound.valueOf(configManager.getBadWordsAdminSoundName());
                                admin.playSound(admin.getLocation(), sound, configManager.getBadWordsAdminSoundVolume(), configManager.getBadWordsAdminSoundPitch());
                            } catch (IllegalArgumentException e) {
                                if (configManager.isConsoleLogsEnabled()) {
                                    plugin.getLogger().warning("Неверное имя звука для админа (маты): " + configManager.getBadWordsAdminSoundName());
                                }
                            }
                        }
                    }
                }
            }
        }.runTask(plugin);
    }

    public void notifyPlayerLinks(Player player, String link) {
        if (!configManager.isLinksNotificationsEnabled()) {
            if (configManager.isConsoleLogsEnabled()) {
                plugin.getLogger().info("Уведомления для ссылок отключены, пропуск уведомления для игрока " + player.getName());
            }
            return;
        }

        if (configManager.isLinksChatEnabled() && !plugin.getPunishmentManager().isLinksNotificationDisabled(player, "message")) {
            if (!configManager.getLinksFilterWarningMessage().isEmpty()) {
                HexColors.sendMessage(player, configManager.getLinksFilterWarningMessage());
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Отправлено сообщение в чат игроку " + player.getName() + ": " + configManager.getLinksFilterWarningMessage());
                }
            }
        }

        if (configManager.isLinksTitleEnabled() && !plugin.getPunishmentManager().isLinksNotificationDisabled(player, "title")) {
            if (!configManager.getLinksTitleText().isEmpty()) {
                String[] titleParts = configManager.getLinksTitleText().split("\n", 2);
                String mainTitleText = titleParts[0];
                String subTitleText = titleParts.length > 1 ? titleParts[1] : "";
                Component mainTitle = HexColors.translateToComponent(mainTitleText);
                Component subTitle = HexColors.translateToComponent(subTitleText);

                Title.Times times = createTitleTimes(
                        configManager.getLinksTitleFadeInTicks() * 50L,
                        configManager.getLinksTitleStayTicks() * 50L,
                        configManager.getLinksTitleFadeOutTicks() * 50L
                );

                player.showTitle(Title.title(mainTitle, subTitle, times));
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Отправлен тайтл игроку " + player.getName() + ": mainTitle=" + mainTitleText + ", subTitle=" + subTitleText);
                }
            }
        }

        if (configManager.isLinksActionBarEnabled() && !plugin.getPunishmentManager().isLinksNotificationDisabled(player, "actionbar")) {
            if (!configManager.getLinksActionBarText().isEmpty()) {
                Component actionBarComponent = HexColors.translateToComponent(configManager.getLinksActionBarText());
                player.sendActionBar(actionBarComponent);
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Отправлен экшенбар игроку " + player.getName() + ": " + configManager.getLinksActionBarText());
                }
            }
        }

        if (configManager.isLinksPlayerSoundEnabled() && !plugin.getPunishmentManager().isLinksNotificationDisabled(player, "sound")) {
            try {
                Sound sound = Sound.valueOf(configManager.getLinksPlayerSoundName());
                player.playSound(player.getLocation(), sound, configManager.getLinksPlayerSoundVolume(), configManager.getLinksPlayerSoundPitch());
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().info("Проигран звук игроку " + player.getName() + ": " + configManager.getLinksPlayerSoundName());
                }
            } catch (IllegalArgumentException e) {
                if (configManager.isConsoleLogsEnabled()) {
                    plugin.getLogger().warning("Неверное имя звука для игрока (ссылки): " + configManager.getLinksPlayerSoundName());
                }
            }
        }
    }

    public void notifyConsoleLinks(Player player, List<String> links) {
        if (!configManager.isLinksConsoleNotificationsEnabled()) {
            return;
        }

        String notifyMessage;
        if (links.size() == 1) {
            notifyMessage = configManager.getLinksConsoleNotifyMessage()
                    .replace("{player}", player.getName())
                    .replace("{links}", configManager.getLinksConsoleSingleLinkTemplate().replace("{link}", links.get(0)));
        } else {
            StringBuilder linksFormatted = new StringBuilder();
            for (int i = 0; i < links.size(); i++) {
                linksFormatted.append(configManager.getLinksConsoleLinkTemplate().replace("{link}", links.get(i)));
                if (i < links.size() - 1) {
                    linksFormatted.append(configManager.getLinksConsoleLinksSeparator());
                }
            }
            notifyMessage = configManager.getLinksConsoleNotifyMessage()
                    .replace("{player}", player.getName())
                    .replace("{links}", linksFormatted.toString());
        }

        String coloredMessage = HexColors.colorize(notifyMessage, configManager.isConsoleLogsEnabled(), plugin.getLogger());
        plugin.getLogger().info(coloredMessage);
    }

    public void notifyAdminsLinks(Player player, List<String> links) {
        if (!configManager.isLinksNotificationsEnabled()) {
            return;
        }

        String notifyMessage;
        if (links.size() == 1) {
            notifyMessage = configManager.getLinksAdminNotifyMessage()
                    .replace("{player}", player.getName())
                    .replace("{links}", configManager.getLinksAdminSingleLinkTemplate().replace("{link}", links.get(0)));
        } else {
            StringBuilder linksFormatted = new StringBuilder();
            for (int i = 0; i < links.size(); i++) {
                linksFormatted.append(configManager.getLinksAdminLinkTemplate().replace("{link}", links.get(i)));
                if (i < links.size() - 1) {
                    linksFormatted.append(configManager.getLinksAdminLinksSeparator());
                }
            }
            notifyMessage = configManager.getLinksAdminNotifyMessage()
                    .replace("{player}", player.getName())
                    .replace("{links}", linksFormatted.toString());
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                for (Player admin : Bukkit.getOnlinePlayers()) {
                    if (admin.hasPermission("chatfilterplus.admin.notify")) {
                        HexColors.sendMessage(admin, notifyMessage);
                        if (configManager.isLinksAdminSoundEnabled()) {
                            try {
                                Sound sound = Sound.valueOf(configManager.getLinksAdminSoundName());
                                admin.playSound(admin.getLocation(), sound, configManager.getLinksAdminSoundVolume(), configManager.getLinksAdminSoundPitch());
                            } catch (IllegalArgumentException e) {
                                if (configManager.isConsoleLogsEnabled()) {
                                    plugin.getLogger().warning("Неверное имя звука для админа (ссылки): " + configManager.getLinksAdminSoundName());
                                }
                            }
                        }
                    }
                }
            }
        }.runTask(plugin);
    }
}