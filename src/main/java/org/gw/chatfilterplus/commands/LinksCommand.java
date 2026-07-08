package org.gw.chatfilterplus.commands;

import org.bukkit.command.CommandSender;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.managers.LinksManager;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
            case "keywords" -> handleKeywords(sender, args);
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
            plugin.getConfigManager().executeActions(sender, path, Map.of("link", link));
        } else {
            String path = action.equals("add") ? "links.whitelist.add.already-exists" : "links.whitelist.remove.not-found";
            plugin.getConfigManager().executeActions(sender, path, Map.of("link", link));
        }
        return true;
    }

    private boolean handleKeywords(CommandSender sender, String[] args) {
        if (args.length < 3) {
            plugin.getConfigManager().executeActions(sender, "help");
            return true;
        }

        String action = args[2].toLowerCase();
        return switch (action) {
            case "add" -> handleKeywordAdd(sender, args);
            case "remove" -> handleKeywordRemove(sender, args);
            case "list" -> handleKeywordList(sender);
            default -> {
                plugin.getConfigManager().executeActions(sender, "help");
                yield true;
            }
        };
    }

    private boolean handleKeywordAdd(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.getConfigManager().executeActions(sender, "help");
            return true;
        }

        String keyword = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
        if (keyword.isEmpty()) {
            plugin.getConfigManager().executeActions(sender, "help");
            return true;
        }

        if (linksManager.addPromoKeyword(keyword)) {
            plugin.getConfigManager().executeActions(sender, "links.keywords.add.success", Map.of("word", keyword));
        } else {
            plugin.getConfigManager().executeActions(sender, "links.keywords.add.already-exists", Map.of("word", keyword));
        }
        return true;
    }

    private boolean handleKeywordRemove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.getConfigManager().executeActions(sender, "help");
            return true;
        }

        String keyword = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).trim();
        if (keyword.isEmpty()) {
            plugin.getConfigManager().executeActions(sender, "help");
            return true;
        }

        if (linksManager.removePromoKeyword(keyword)) {
            plugin.getConfigManager().executeActions(sender, "links.keywords.remove.success", Map.of("word", keyword));
        } else {
            plugin.getConfigManager().executeActions(sender, "links.keywords.remove.not-found", Map.of("word", keyword));
        }
        return true;
    }

    private boolean handleKeywordList(CommandSender sender) {
        List<String> keywords = linksManager.getPromoKeywords();
        if (keywords.isEmpty()) {
            plugin.getConfigManager().executeActions(sender, "links.keywords.list.empty");
        } else {
            plugin.getConfigManager().executeActions(sender, "links.keywords.list.header");
            for (String word : keywords) {
                plugin.getConfigManager().executeActions(sender, "links.keywords.list.item", Map.of("word", word));
            }
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
                plugin.getConfigManager().executeActions(sender, "links.list.item", Map.of("link", d));
            }
        }
        return true;
    }
}
