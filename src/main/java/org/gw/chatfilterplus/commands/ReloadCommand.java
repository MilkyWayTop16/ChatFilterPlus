package org.gw.chatfilterplus.commands;

import org.bukkit.command.CommandSender;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.managers.ChatManager;
import org.gw.chatfilterplus.managers.ConfigManager;

import java.util.HashMap;
import java.util.Map;

public class ReloadCommand {

    private final ChatFilterPlus plugin;
    private final ConfigManager configManager;
    private final ChatManager chatManager;

    public ReloadCommand(ChatFilterPlus plugin, ConfigManager configManager, ChatManager chatManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.chatManager = chatManager;
    }

    public boolean execute(CommandSender sender, String[] args) {
        long startTime = System.currentTimeMillis();

        String subCommand = (args.length > 1) ? args[1].toLowerCase() : "configs";

        boolean success = true;

        switch (subCommand) {
            case "configs" -> success = plugin.reloadConfigs();
            case "cache" -> chatManager.clearCache();
            case "all" -> success = plugin.reloadPlugin();
            default -> {
                plugin.getConfigManager().executeActions(sender, "help");
                return true;
            }
        }

        long reloadTime = System.currentTimeMillis() - startTime;
        Map<String, String> placeholders = new HashMap<>();
        placeholders.put("time", String.valueOf(reloadTime));

        if (!success) {
            configManager.executeActions(sender, "reload.failure");
        } else if ("cache".equals(subCommand)) {
            configManager.executeActions(sender, "reload.cache.success", placeholders);
        } else {
            String actionPath = "all".equals(subCommand) ? "reload.all.success" : "reload.configs.success";
            configManager.executeActions(sender, actionPath, placeholders);
        }

        return true;
    }
}