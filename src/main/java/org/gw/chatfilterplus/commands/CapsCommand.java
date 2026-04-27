package org.gw.chatfilterplus.commands;

import org.bukkit.command.CommandSender;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.managers.CapsManager;

import java.util.List;

public class CapsCommand {

    private final ChatFilterPlus plugin;
    private final CapsManager capsManager;

    public CapsCommand(ChatFilterPlus plugin, CapsManager capsManager) {
        this.plugin = plugin;
        this.capsManager = capsManager;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getConfigManager().executeActions(sender, "help");
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "whitelist" -> handleWhitelist(sender, args);
            case "list" -> handleList(sender);
            default -> {
                plugin.getConfigManager().executeActions(sender, "help");
                yield true;
            }
        };
    }

    private boolean handleWhitelist(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.getConfigManager().executeActions(sender, "help");
            return true;
        }

        String action = args[2].toLowerCase();
        String word = args[3];

        boolean success = switch (action) {
            case "add" -> capsManager.addWhitelistWord(word);
            case "remove" -> capsManager.removeWhitelistWord(word);
            default -> false;
        };

        if (success) {
            String path = action.equals("add") ? "caps.whitelist.add.success" : "caps.whitelist.remove.success";
            plugin.getConfigManager().executeActions(sender, path, java.util.Map.of("word", word));
        } else {
            String path = action.equals("add") ? "caps.whitelist.add.already-exists" : "caps.whitelist.remove.not-found";
            plugin.getConfigManager().executeActions(sender, path, java.util.Map.of("word", word));
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<String> list = plugin.getConfigManager().getCapsWhitelist();
        if (list.isEmpty()) {
            plugin.getConfigManager().executeActions(sender, "caps.list.empty");
        } else {
            plugin.getConfigManager().executeActions(sender, "caps.list.header");
            for (String w : list) {
                plugin.getConfigManager().executeActions(sender, "caps.list.item", java.util.Map.of("word", w));
            }
        }
        return true;
    }
}