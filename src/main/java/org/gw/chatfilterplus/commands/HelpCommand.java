package org.gw.chatfilterplus.commands;

import org.bukkit.command.CommandSender;
import org.gw.chatfilterplus.ChatFilterPlus;

public class HelpCommand {

    private final ChatFilterPlus plugin;

    public HelpCommand(ChatFilterPlus plugin) {
        this.plugin = plugin;
    }

    public void execute(CommandSender sender, String[] args) {
        plugin.getConfigManager().executeActions(sender, "help");
    }
}