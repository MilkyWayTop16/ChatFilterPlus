package org.gw.chatfilterplus;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.gw.chatfilterplus.commands.CommandHandler;
import org.gw.chatfilterplus.commands.CommandsTabCompleter;
import org.gw.chatfilterplus.listener.CommandSendListener;
import org.gw.chatfilterplus.manager.ChatManager;
import org.gw.chatfilterplus.manager.ConfigManager;
import org.gw.chatfilterplus.manager.LinksManager;
import org.gw.chatfilterplus.manager.LogCleanupManager;
import org.gw.chatfilterplus.manager.NotificationManager;
import org.gw.chatfilterplus.manager.PunishmentManager;
import org.gw.chatfilterplus.manager.WordsManager;
import org.gw.chatfilterplus.utils.HexColors;

import java.io.File;
import java.io.IOException;

public class ChatFilterPlus extends JavaPlugin {
    private ConfigManager configManager;
    private WordsManager wordsManager;
    private LinksManager linksManager;
    private ChatManager chatManager;
    private NotificationManager notificationManager;
    private LogCleanupManager logCleanupManager;
    private PunishmentManager punishmentManager;

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();
        initializePlugin();
        logCleanupManager.startLogCleanupTask();
        long loadTime = System.currentTimeMillis() - startTime;
        logStartupInfo(loadTime);
    }

    @Override
    public void onDisable() {
        long startTime = System.currentTimeMillis();
        logCleanupManager.stopLogCleanupTask();
        long unloadTime = System.currentTimeMillis() - startTime;
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
        getLogger().info(HexColors.colorize("&e█▀▀ █░█ ▄▀█ ▀█▀ █▀▀ █ █░░ ▀█▀ █▀▀ █▀█ █▀█ █░░ █░█ █▀", false, getLogger()));
        getLogger().info(HexColors.colorize("&e█▄▄ █▀█ █▀█ ░█░ █▀░ █ █▄▄ ░█░ ██▄ █▀▄ █▀▀ █▄▄ █▄█ ▄█", false, getLogger()));
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
        getLogger().info(HexColors.colorize("&c▶ Плагин успешно выгружен!", false, getLogger()));
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
        getLogger().info(HexColors.colorize("&e◆ &fВерсия плагина: &e" + getDescription().getVersion(), false, getLogger()));
        getLogger().info(HexColors.colorize("&e◆ &fВерсия сервера: &e" + Bukkit.getMinecraftVersion(), false, getLogger()));
        getLogger().info(HexColors.colorize("&e◆ &fСохранено плохих слов: &e" + wordsManager.getWordsMap().size(), false, getLogger()));
        getLogger().info(HexColors.colorize("&e◆ &fВремя выгрузки: &e" + unloadTime + " мс", false, getLogger()));
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
    }

    private void initializePlugin() {
        if (!new File(getDataFolder(), "config.yml").exists()) {
            saveResource("config.yml", false);
            getLogger().info("Создан новый config.yml");
        }

        File badWordsLogFile = new File(getDataFolder(), "badwords-logs.txt");
        File linksLogFile = new File(getDataFolder(), "links-logs.txt");
        File badWordsPunishmentLogFile = new File(getDataFolder(), "badwords-punishments-logs.txt");
        File linksPunishmentLogFile = new File(getDataFolder(), "links-punishments-logs.txt");
        try {
            for (File logFile : new File[]{badWordsLogFile, linksLogFile, badWordsPunishmentLogFile, linksPunishmentLogFile}) {
                if (!logFile.exists()) {
                    logFile.getParentFile().mkdirs();
                    logFile.createNewFile();
                    getLogger().info("Создан файл логов " + logFile.getName() + ".");
                }
            }
        } catch (IOException e) {
            getLogger().warning("Не удалось создать файл логов: " + e.getMessage());
        }

        configManager = new ConfigManager(this);
        configManager.loadWordsConfig();
        configManager.loadConfig();
        wordsManager = new WordsManager(this, configManager);
        wordsManager.loadWords();
        linksManager = new LinksManager(this, configManager);
        notificationManager = new NotificationManager(this, configManager);
        logCleanupManager = new LogCleanupManager(this, configManager);
        punishmentManager = new PunishmentManager(this, configManager);
        chatManager = new ChatManager(this, configManager, wordsManager, linksManager, notificationManager, logCleanupManager, punishmentManager);

        getServer().getPluginManager().registerEvents(chatManager, this);
        getServer().getPluginManager().registerEvents(new CommandSendListener(), this);
        getCommand("chatfilterplus").setExecutor(new CommandHandler(this, wordsManager, configManager, chatManager));
        getCommand("chatfilterplus").setTabCompleter(new CommandsTabCompleter(wordsManager));
    }

    private void logStartupInfo(long loadTime) {
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
        getLogger().info(HexColors.colorize("&e█▀▀ █░█ ▄▀█ ▀█▀ █▀▀ █ █░░ ▀█▀ █▀▀ █▀█ █▀█ █░░ █░█ █▀", false, getLogger()));
        getLogger().info(HexColors.colorize("&e█▄▄ █▀█ █▀█ ░█░ █▀░ █ █▄▄ ░█░ ██▄ █▀▄ █▀▀ █▄▄ █▄█ ▄█", false, getLogger()));
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
        getLogger().info(HexColors.colorize("&a▶ Плагин успешно загружен!", false, getLogger()));
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
        getLogger().info(HexColors.colorize("&e◆ &fВерсия плагина: &e" + getDescription().getVersion(), false, getLogger()));
        getLogger().info(HexColors.colorize("&e◆ &fВерсия сервера: &e" + Bukkit.getMinecraftVersion(), false, getLogger()));
        getLogger().info(HexColors.colorize("&e◆ &fЗагружено плохих слов: &e" + wordsManager.getWordsMap().size(), false, getLogger()));
        getLogger().info(HexColors.colorize("&e◆ &fВремя загрузки: &e" + loadTime + " мс", false, getLogger()));
        getLogger().info(HexColors.colorize("&e ", false, getLogger()));
    }

    public void reloadPluginConfig() {
        reloadConfig();
        configManager.loadConfig();
        configManager.loadWordsConfig();
        wordsManager.loadWords();
        linksManager.loadLinkPattern();
        chatManager.updateWordsMap();
        chatManager.updateLinkPattern();
        chatManager.clearCache();
        notificationManager = new NotificationManager(this, configManager);
        logCleanupManager.stopLogCleanupTask();
        logCleanupManager = new LogCleanupManager(this, configManager);
        punishmentManager = new PunishmentManager(this, configManager);
        chatManager = new ChatManager(this, configManager, wordsManager, linksManager, notificationManager, logCleanupManager, punishmentManager);
        getCommand("chatfilterplus").setExecutor(new CommandHandler(this, wordsManager, configManager, chatManager));
        getCommand("chatfilterplus").setTabCompleter(new CommandsTabCompleter(wordsManager));
        logCleanupManager.startLogCleanupTask();
        if (configManager.isConsoleLogsEnabled()) {
            getLogger().info("Менеджеры плагина перезагружены");
        }
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public PunishmentManager getPunishmentManager() {
        return punishmentManager;
    }
}