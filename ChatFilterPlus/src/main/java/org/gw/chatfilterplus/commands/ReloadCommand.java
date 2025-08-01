package org.gw.chatfilterplus.commands;

import org.bukkit.command.CommandSender;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.manager.ConfigManager;
import org.gw.chatfilterplus.manager.WordsManager;
import org.gw.chatfilterplus.manager.ChatManager;
import org.gw.chatfilterplus.utils.HexColors;

public class ReloadCommand implements ChatFilterCommand {
    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final WordsManager wordsManager;
    private final ChatManager chatManager;

    public ReloadCommand(ChatFilterPlus plugin, ConfigManager configManager, WordsManager wordsManager, ChatManager chatManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.wordsManager = wordsManager;
        this.chatManager = chatManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chatfilterplus.reload")) {
            String noPermissionMsg = plugin.getConfig().getString("messages.no-permission", "&#FB8808▶ &fНедостаточно &#FB8808прав на использование &fданной команды!");
            HexColors.sendMessage(sender, noPermissionMsg);
            return;
        }

        boolean consoleLogsEnabled = configManager.isConsoleLogsEnabled();
        try {
            long startTime = System.currentTimeMillis();
            configManager.loadConfig();
            configManager.loadWordsConfig();
            wordsManager.loadWords();
            plugin.reloadPluginConfig();
            chatManager.updateWordsMap();
            chatManager.clearCache();
            long reloadTime = System.currentTimeMillis() - startTime;
            String successMsg = plugin.getConfig().getString("messages.reload-success", "&#FFFF00◆ &fПлагин успешно перезагружен за &#FFFF00{time}мс.");
            successMsg = successMsg.replace("{time}", String.valueOf(reloadTime));
            HexColors.sendMessage(sender, successMsg);
            String coloredMessage = HexColors.colorize(successMsg, consoleLogsEnabled, plugin.getLogger());
            plugin.getLogger().info(coloredMessage);
        } catch (Exception e) {
            String failureMsg = plugin.getConfig().getString("messages.reload-failure", "&#FB8808▶ Ошибка! &fПочему-то &#FB8808не удалось &fперезагрузить плагин...");
            HexColors.sendMessage(sender, failureMsg);
            String coloredFailureMessage = HexColors.colorize(failureMsg, consoleLogsEnabled, plugin.getLogger());
            plugin.getLogger().warning(coloredFailureMessage);
            if (consoleLogsEnabled) {
                plugin.getLogger().warning("Исключение при перезагрузке: " + e.getMessage());
            }
        }
    }
}