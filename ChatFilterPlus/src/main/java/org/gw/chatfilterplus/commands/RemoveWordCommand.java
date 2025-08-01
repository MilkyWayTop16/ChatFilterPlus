package org.gw.chatfilterplus.commands;

import org.bukkit.command.CommandSender;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.manager.WordsManager;
import org.gw.chatfilterplus.utils.HexColors;

public class RemoveWordCommand implements ChatFilterCommand {

    private final ChatFilterPlus plugin;
    private final WordsManager wordsManager;

    public RemoveWordCommand(ChatFilterPlus plugin, WordsManager wordsManager) {
        this.plugin = plugin;
        this.wordsManager = wordsManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chatfilterplus.removeword")) {
            String noPermissionMsg = plugin.getConfig().getString("messages.no-permission");
            HexColors.sendMessage(sender, noPermissionMsg);
            return;
        }

        if (args.length < 2) {
            HexColors.sendMessage(sender, plugin.getConfig().getStringList("messages.help"));
            return;
        }

        String word = args[1];
        wordsManager.removeWord(sender, word);
    }
}