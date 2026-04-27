package org.gw.chatfilterplus.commands;

import org.bukkit.command.CommandSender;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.managers.LinksManager;

import java.util.List;

public class LinksCommand {

    private final ChatFilterPlus plugin;
    private final LinksManager linksManager;

    public LinksCommand(ChatFilterPlus plugin, LinksManager linksManager) {
        this.plugin = plugin;
        this.linksManager = linksManager;
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
        String link = args[3];

        boolean success = switch (action) {
            case "add" -> linksManager.addWhitelistDomain(link);
            case "remove" -> linksManager.removeWhitelistDomain(link);
            default -> false;
        };

        if (success) {
            String path = action.equals("add") ? "links.whitelist.add.success" : "links.whitelist.remove.success";
            plugin.getConfigManager().executeActions(sender, path, java.util.Map.of("link", link));
        } else {
            String path = action.equals("add") ? "links.whitelist.add.already-exists" : "links.whitelist.remove.not-found";
            plugin.getConfigManager().executeActions(sender, path, java.util.Map.of("link", link));
        }
        return true;
    }

    private boolean handleList(CommandSender sender) {
        List<String> domains = plugin.getConfigManager().getLinksListFilterDomains();
        if (domains.isEmpty()) {
            plugin.getConfigManager().executeActions(sender, "links.list.empty");
        } else {
            plugin.getConfigManager().executeActions(sender, "links.list.header");
            for (String d : domains) {
                plugin.getConfigManager().executeActions(sender, "links.list.item", java.util.Map.of("link", d));
            }
        }
        return true;
    }
}