package org.gw.chatfilterplus.commands;

import org.bukkit.command.CommandSender;

public interface ChatFilterCommand {
    void execute(CommandSender sender, String[] args);
}