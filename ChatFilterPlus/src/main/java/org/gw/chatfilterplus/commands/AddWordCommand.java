package org.gw.chatfilterplus.commands;

import org.bukkit.command.CommandSender;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.manager.WordsManager;
import org.gw.chatfilterplus.utils.HexColors;

public class AddWordCommand implements ChatFilterCommand {

    private final ChatFilterPlus plugin;
    private final WordsManager wordsManager;

    public AddWordCommand(ChatFilterPlus plugin, WordsManager wordsManager) {
        this.plugin = plugin;
        this.wordsManager = wordsManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chatfilterplus.addword")) {
            String noPermissionMsg = plugin.getConfig().getString("messages.no-permission");
            HexColors.sendMessage(sender, noPermissionMsg);
            return;
        }

        if (args.length < 3) {
            HexColors.sendMessage(sender, plugin.getConfig().getStringList("messages.help"));
            return;
        }

        String word = args[1];
        String replacement = args[2];
        wordsManager.addWord(sender, word, replacement);
    }
}