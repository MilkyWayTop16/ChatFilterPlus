package org.gw.chatfilterplus.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.gw.chatfilterplus.manager.WordsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class CommandsTabCompleter implements TabCompleter {

    private final WordsManager wordsManager;

    public CommandsTabCompleter(WordsManager wordsManager) {
        this.wordsManager = wordsManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        boolean hasAnyPermission = sender.hasPermission("chatfilterplus.addword") ||
                sender.hasPermission("chatfilterplus.removeword") ||
                sender.hasPermission("chatfilterplus.reload");

        if (!hasAnyPermission) {
            return new ArrayList<>();
        }

        List<String> completions = new ArrayList<>();

        if (args.length == 0) {
            completions.addAll(Arrays.asList("cfp", "chatfilterplus", "chatfilterplus:chatfilterplus", "chatfilterplus:cfp"));
            return completions;
        }

        if (args.length == 1) {
            if (sender.hasPermission("chatfilterplus.addword")) {
                completions.add("addword");
            }
            if (sender.hasPermission("chatfilterplus.removeword")) {
                completions.add("removeword");
            }
            if (sender.hasPermission("chatfilterplus.reload")) {
                completions.add("reload");
            }
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("addword") && sender.hasPermission("chatfilterplus.addword")) {
            completions.add("<Слово>");
        } else if (args.length == 3 && args[0].equalsIgnoreCase("addword") && sender.hasPermission("chatfilterplus.addword")) {
            completions.add("<Замена>");
        }
        else if (args.length == 2 && args[0].equalsIgnoreCase("removeword") && sender.hasPermission("chatfilterplus.removeword")) {
            Map<String, String> words = wordsManager.getWords();
            completions.addAll(words.keySet());
        }

        return completions;
    }
}