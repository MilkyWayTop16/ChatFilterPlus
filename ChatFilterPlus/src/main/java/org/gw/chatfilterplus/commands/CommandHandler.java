package org.gw.chatfilterplus.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.manager.ConfigManager;
import org.gw.chatfilterplus.manager.WordsManager;
import org.gw.chatfilterplus.manager.ChatManager;
import org.gw.chatfilterplus.utils.HexColors;

import java.util.HashMap;
import java.util.Map;

public class CommandHandler implements CommandExecutor {

    private final ChatFilterPlus plugin;
    private final Map<String, ChatFilterCommand> commands;

    public CommandHandler(ChatFilterPlus plugin, WordsManager wordsManager, ConfigManager configManager, ChatManager chatManager) {
        this.plugin = plugin;
        this.commands = new HashMap<>();
        commands.put("addword", new AddWordCommand(plugin, wordsManager));
        commands.put("removeword", new RemoveWordCommand(plugin, wordsManager));
        commands.put("reload", new ReloadCommand(plugin, configManager, wordsManager, chatManager));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender.hasPermission("chatfilterplus.addword") ||
                    sender.hasPermission("chatfilterplus.removeword") ||
                    sender.hasPermission("chatfilterplus.reload")) {
                HexColors.sendMessage(sender, plugin.getConfig().getStringList("messages.help"));
            } else {
                String noPermissionMsg = plugin.getConfig().getString("messages.no-permission");
                HexColors.sendMessage(sender, noPermissionMsg);
            }
            return true;
        }

        String subCommand = args[0].toLowerCase();
        ChatFilterCommand cmd = commands.get(subCommand);
        if (cmd == null) {
            String noPermissionMsg = plugin.getConfig().getString("messages.no-permission");
            HexColors.sendMessage(sender, noPermissionMsg);
            return true;
        }
        cmd.execute(sender, args);
        return true;
    }
}