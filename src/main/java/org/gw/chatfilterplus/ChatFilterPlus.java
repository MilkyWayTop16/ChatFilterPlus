package org.gw.chatfilterplus;

import lombok.Getter;
import org.bukkit.Bukkit;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;
import org.gw.chatfilterplus.commands.CommandsHandler;
import org.gw.chatfilterplus.commands.CommandsTabCompleter;
import org.gw.chatfilterplus.configs.LegacyPermissionMigrator;
import org.gw.chatfilterplus.listeners.CommandFilterListener;
import org.gw.chatfilterplus.listeners.CommandSendListener;
import org.gw.chatfilterplus.managers.*;
import org.gw.chatfilterplus.utils.*;

@Getter
public class ChatFilterPlus extends JavaPlugin {

    private ConfigManager configManager;
    private WordsManager wordsManager;
    private LinksManager linksManager;
    private ChatManager chatManager;
    private NotificationManager notificationManager;
    private LogCleanupManager logCleanupManager;
    private PunishmentManager punishmentManager;
    private MessageCacheManager messageCacheManager;
    private UpdateChecker updateChecker;
    private CapsManager capsManager;
    private BlockedWordsManager blockedWordsManager;
    private AntiSpamManager antiSpamManager;
    private WordNormalizer wordNormalizer;
    private CommandFilterListener commandFilterListener;
    private PlayerNameRegistry playerNameRegistry;

    public java.util.function.Supplier<java.util.Set<String>> protectedNameSupplier() {
        return () -> {
            if (playerNameRegistry == null) {
                return java.util.Set.of();
            }
            return playerNameRegistry.names();
        };
    }

    @Override
    public void onEnable() {
        long startTime = System.currentTimeMillis();

        if (!initializePlugin()) {
            setEnabled(false);
            return;
        }

        if (configManager.isBStatsEnabled()) {
            new BStats(this);
        }

        long loadTime = System.currentTimeMillis() - startTime;
        logStartupInfo(loadTime);
    }

    private boolean initializePlugin() {
        console("&#ffff00 ");
        console("&#00FF5A◆ ChatFilterPlus &f| Чтение &#00FF5Aконфигурационных &fфайлов...");
        configManager = new ConfigManager(this);
        configManager.loadAllConfigs();

        console("&#00FF5A◆ ChatFilterPlus &f| Инициализация &#00FF5Aменеджеров &fи &#00FF5Aсистемы кэширования&f...");

        playerNameRegistry = new PlayerNameRegistry();

        wordsManager = new WordsManager(this, configManager);
        wordsManager.loadWords();

        capsManager = new CapsManager(this, configManager);

        linksManager = new LinksManager(this, configManager);
        blockedWordsManager = new BlockedWordsManager(this, configManager);
        blockedWordsManager.loadBlockedWords();

        notificationManager = new NotificationManager(this, configManager);
        logCleanupManager = new LogCleanupManager(this, configManager);
        punishmentManager = new PunishmentManager(this, configManager);

        wordNormalizer = new WordNormalizer(this);

        messageCacheManager = new MessageCacheManager(this, configManager, wordsManager, linksManager, capsManager,
                blockedWordsManager, wordNormalizer, configManager.getCacheMaxSize());

        antiSpamManager = new AntiSpamManager(this, configManager);

        chatManager = new ChatManager(this, configManager, wordsManager, linksManager, capsManager,
                blockedWordsManager, notificationManager, logCleanupManager, punishmentManager,
                messageCacheManager, antiSpamManager);

        console("&#00FF5A◆ ChatFilterPlus &f| Регистрация &#00FF5Aсобытий &fи &#00FF5Aкоманд...");

        commandFilterListener = new CommandFilterListener(this, chatManager);
        Bukkit.getPluginManager().registerEvents(commandFilterListener, this);
        Bukkit.getPluginManager().registerEvents(new CommandSendListener(), this);
        Bukkit.getPluginManager().registerEvents(chatManager, this);
        Bukkit.getPluginManager().registerEvents(playerNameRegistry, this);
        playerNameRegistry.refresh();
        registerChatListener();

        getCommand("chatfilterplus").setExecutor(new CommandsHandler(
                this,
                wordsManager,
                blockedWordsManager,
                linksManager,
                capsManager,
                configManager,
                chatManager
        ));
        getCommand("chatfilterplus").setTabCompleter(new CommandsTabCompleter(wordsManager, linksManager));

        logCleanupManager.startLogCleanupTask();

        new LegacyPermissionMigrator(this).migrateAsyncIfNeeded();

        console("&#00FF5A◆ ChatFilterPlus &f| Инициализация &#00FF5Aсистемы проверки &fобновлений...");
        updateChecker = new UpdateChecker(this);
        Bukkit.getPluginManager().registerEvents(updateChecker, this);

        return true;
    }

    private EventPriority resolveChatEventPriority() {
        String priorityStr = configManager.getCompatibilityEventPriority().toUpperCase();
        try {
            return EventPriority.valueOf(priorityStr);
        } catch (IllegalArgumentException e) {
            return EventPriority.LOWEST;
        }
    }

    private void registerChatListener() {
        EventPriority eventPriority = resolveChatEventPriority();
        boolean ignoreCancelled = !configManager.isCompatibilityAggressiveMode();
        boolean readOnly = eventPriority == EventPriority.MONITOR;

        registerLegacyChatListener(eventPriority, ignoreCancelled, readOnly);

        if (isPaperAsyncChatEventPresent()) {
            registerPaperChatListener(eventPriority, ignoreCancelled, readOnly);
        }
    }

    private static boolean isPaperAsyncChatEventPresent() {
        try {
            Class.forName("io.papermc.paper.event.player.AsyncChatEvent");
            return true;
        } catch (ClassNotFoundException | NoClassDefFoundError e) {
            return false;
        }
    }

    private void registerPaperChatListener(EventPriority eventPriority,
                                           boolean ignoreCancelled,
                                           boolean readOnly) {
        try {
            Class<?> support = Class.forName("org.gw.chatfilterplus.listeners.PaperChatSupport");
            support.getMethod(
                            "register",
                            org.bukkit.plugin.Plugin.class,
                            org.gw.chatfilterplus.managers.ChatManager.class,
                            EventPriority.class,
                            boolean.class,
                            boolean.class)
                    .invoke(null, this, chatManager, eventPriority, ignoreCancelled, readOnly);
        } catch (Throwable ignored) {
        }
    }

    private void registerLegacyChatListener(EventPriority eventPriority,
                                            boolean ignoreCancelled,
                                            boolean readOnly) {
        EventExecutor primary = (listener, event) -> {
            if (event instanceof AsyncPlayerChatEvent chatEvent) {
                chatManager.onPlayerChat(chatEvent, readOnly);
            }
        };

        Bukkit.getPluginManager().registerEvent(
                AsyncPlayerChatEvent.class,
                chatManager,
                eventPriority,
                primary,
                this,
                ignoreCancelled
        );

        if (!readOnly) {
            EventExecutor enforce = (listener, event) -> {
                if (event instanceof AsyncPlayerChatEvent chatEvent) {
                    chatManager.enforcePlayerChat(chatEvent);
                }
            };

            Bukkit.getPluginManager().registerEvent(
                    AsyncPlayerChatEvent.class,
                    chatManager,
                    EventPriority.HIGHEST,
                    enforce,
                    this,
                    false
            );

            EventExecutor verify = (listener, event) -> {
                if (event instanceof AsyncPlayerChatEvent chatEvent) {
                    chatManager.verifyPlayerChat(chatEvent, eventPriority);
                }
            };

            Bukkit.getPluginManager().registerEvent(
                    AsyncPlayerChatEvent.class,
                    chatManager,
                    EventPriority.MONITOR,
                    verify,
                    this,
                    false
            );
        }
    }

    public boolean reloadConfigs() {
        console("&#ffff00◆ ChatFilterPlus &f| Перезагрузка всех &#ffff00.yml-файлов&f...");

        boolean success = configManager.reload();
        if (!success) {
            console("&#FF5D00◆ ChatFilterPlus &f| Ошибка &#FF5D00при перезагрузке &f.yml-файлов...");
            return false;
        }

        console("&#ffff00◆ ChatFilterPlus &f| Перезагрузка &#ffff00менеджеров&f, зависящих от конфигов...");
        wordsManager.reload();
        blockedWordsManager.reload();
        linksManager.reload();
        capsManager.reload();
        antiSpamManager.reload();

        console("&#ffff00◆ ChatFilterPlus &f| Перезагрузка &#ffff00кэша &fи &#ffff00уведомлений&f...");
        chatManager.reload();
        notificationManager.reload();
        punishmentManager.reload();
        logCleanupManager.reload();
        wordNormalizer.reload(configManager.getSafeWords());

        if (commandFilterListener != null) {
            commandFilterListener.reload();
        }

        HandlerList.unregisterAll(chatManager);
        Bukkit.getPluginManager().registerEvents(chatManager, this);
        registerChatListener();

        if (updateChecker != null) {
            updateChecker.reload();
        }

        return true;
    }

    public boolean reloadPlugin() {
        console("&#ffff00◆ ChatFilterPlus &f| Начало &#ffff00полной перезагрузки &fплагина...");

        boolean success = reloadConfigs();
        if (!success) {
            return false;
        }

        return true;
    }

    private void logStartupInfo(long loadTime) {
        console("&#ffff00 ");
        console("&#FFFF00  █&#FFFD00▀&#FFFB00▀ &#FFF800█&#FFF600░&#FFF400█ &#FFF100▄&#FFEF00▀&#FFED00█ &#FFEA00▀&#FFE800█&#FFE600▀ &#FFE300█&#FFE100▀&#FFDF00▀ &#FFDC00█ &#FFD800█&#FFD600░&#FFD500░ &#FFD100▀&#FFCF00█&#FFCE00▀ &#FFCA00█&#FFC800▀&#FFC700▀ &#FFC300█&#FFC100▀&#FFBF00█ &#FFBC00█&#FFBA00▀&#FFB800█ &#FFB500█&#FFB300░&#FFB100░ &#FFAE00█&#FFAC00░&#FFAA00█ &#FFA700█&#FFA500▀");
        console("&#FFFF00  █&#FFFD00▄&#FFFB00▄ &#FFF800█&#FFF600▀&#FFF400█ &#FFF100█&#FFEF00▀&#FFED00█ &#FFEA00░&#FFE800█&#FFE600░ &#FFE300█&#FFE100▀&#FFDF00░ &#FFDC00█ &#FFD800█&#FFD600▄&#FFD500▄ &#FFD100░&#FFCF00█&#FFCE00░ &#FFCA00█&#FFC800█&#FFC700▄ &#FFC300█&#FFC100▀&#FFBF00▄ &#FFBC00█&#FFBA00▀&#FFB800▀ &#FFB500█&#FFB300▄&#FFB100▄ &#FFAE00█&#FFAC00▄&#FFAA00█ &#FFA700▄&#FFA500█");
        console("&#ffff00 ");
        console("&f             (By MilkyWay for everyone)");
        console("&#ffff00 ");
        console("&#00FF5A       ▶ &fПлагин &#00FF5Aуспешно &fзагружен и включен!");
        console("&#ffff00 ");
        console("&#ffff00               ◆ &fВерсия плагина: &#ffff00v" + getDescription().getVersion());
        console("&#ffff00            ◆ &fЗагружено плохих слов: &#ffff00" + wordsManager.getBadWordsList().size());

        console("&#ffff00               ◆ &fВключённые фильтры:");

        if (configManager.isBadWordsFilterEnabled()) {
            console("&#ffff00                  &f— &#ffff00Мат");
        }
        if (configManager.isLinksFilterEnabled()) {
            console("&#ffff00                  &f— &#ffff00Ссылки");
        }
        if (configManager.isCapsFilterEnabled()) {
            console("&#ffff00                  &f— &#ffff00Капс");
        }
        if (configManager.isBlockedWordsFilterEnabled()) {
            console("&#ffff00                  &f— &#ffff00Запрещённые слова");
        }
        if (configManager.isAntiSpamEnabled()) {
            console("&#ffff00                  &f— &#ffff00Антиспам");
        }
        if (!configManager.isBadWordsFilterEnabled() &&
                !configManager.isLinksFilterEnabled() &&
                !configManager.isCapsFilterEnabled() &&
                !configManager.isBlockedWordsFilterEnabled() &&
                !configManager.isAntiSpamEnabled()) {
            console("&#ffff00                  &#FF5D00— Ничего не включено");
        }

        console("&#ffff00              ◆ &fВремя загрузки: &#ffff00" + loadTime + " мс.");
        console("&#ffff00 ");
    }

    private void logShutdownInfo(long unloadTime) {
        console("&#ffff00 ");
        console("&#FFFF00  █&#FFFD00▀&#FFFB00▀ &#FFF800█&#FFF600░&#FFF400█ &#FFF100▄&#FFEF00▀&#FFED00█ &#FFEA00▀&#FFE800█&#FFE600▀ &#FFE300█&#FFE100▀&#FFDF00▀ &#FFDC00█ &#FFD800█&#FFD600░&#FFD500░ &#FFD100▀&#FFCF00█&#FFCE00▀ &#FFCA00█&#FFC800▀&#FFC700▀ &#FFC300█&#FFC100▀&#FFBF00█ &#FFBC00█&#FFBA00▀&#FFB800█ &#FFB500█&#FFB300░&#FFB100░ &#FFAE00█&#FFAC00░&#FFAA00█ &#FFA700█&#FFA500▀");
        console("&#FFFF00  █&#FFFD00▄&#FFFB00▄ &#FFF800█&#FFF600▀&#FFF400█ &#FFF100█&#FFEF00▀&#FFED00█ &#FFEA00░&#FFE800█&#FFE600░ &#FFE300█&#FFE100▀&#FFDF00░ &#FFDC00█ &#FFD800█&#FFD600▄&#FFD500▄ &#FFD100░&#FFCF00█&#FFCE00░ &#FFCA00█&#FFC800█&#FFC700▄ &#FFC300█&#FFC100▀&#FFBF00▄ &#FFBC00█&#FFBA00▀&#FFB800▀ &#FFB500█&#FFB300▄&#FFB100▄ &#FFAE00█&#FFAC00▄&#FFAA00█ &#FFA700▄&#FFA500█");
        console("&#ffff00 ");
        console("&f             (By MilkyWay for everyone)");
        console("&#ffff00 ");
        console("&#FF5D00      ▶ &fПлагин &#FF5D00успешно &fвыгружен и выключен...");
        console("&#ffff00 ");
        console("&#ffff00               ◆ &fВерсия плагина: &#ffff00v" + getDescription().getVersion());
        console("&#ffff00              ◆ &fВремя выгрузки: &#ffff00" + unloadTime + " мс.");
        console("&#ffff00 ");
    }

    @Override
    public void onDisable() {
        long startTime = System.currentTimeMillis();

        console("&#00FF5A◆ ChatFilterPlus &f| Начало &#00FF5Aвыгрузки &fплагина...");

        if (chatManager != null) {
            chatManager.shutdown();
        }
        if (logCleanupManager != null) {
            logCleanupManager.stopLogCleanupTask();
            logCleanupManager.shutdown();
        }
        if (messageCacheManager != null) {
            messageCacheManager.clearCache();
        }
        if (punishmentManager != null) {
            punishmentManager.reload();
        }
        if (notificationManager != null) {
            notificationManager.reload();
        }
        if (updateChecker != null) {
            updateChecker.shutdown();
        }

        long unloadTime = System.currentTimeMillis() - startTime;
        logShutdownInfo(unloadTime);
    }

    public void console(String message) {
        if (message == null) return;
        Bukkit.getConsoleSender().sendMessage(HexColors.translateForConsole(message));
    }

    public void log(String message) {
        if (configManager != null && configManager.isConsoleLogsEnabled()) {
            console("&#ffff00◆ ChatFilterPlus &f| " + message);
        }
    }

    public void error(String message) {
        Bukkit.getConsoleSender().sendMessage(HexColors.translateForConsole("&#FF5D00◆ ChatFilterPlus &f| " + message));
    }
}