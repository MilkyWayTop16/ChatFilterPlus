package org.gw.chatfilterplus.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.managers.LinksManager;
import org.gw.chatfilterplus.managers.WordsManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public class CommandsTabCompleter implements TabCompleter {

    private final WordsManager wordsManager;
    private final LinksManager linksManager;

    public CommandsTabCompleter(WordsManager wordsManager, LinksManager linksManager) {
        this.wordsManager = wordsManager;
        this.linksManager = linksManager;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasAnyCommandPermission(sender)) return List.of();

        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            addPermittedSubcommands(sender, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("words")) {
            if (sender.hasPermission("chatfilterplus.words")) {
                completions.addAll(Arrays.asList("add", "remove", "list"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("words") && (args[1].equalsIgnoreCase("add") || args[1].equalsIgnoreCase("remove"))) {
            if (sender.hasPermission("chatfilterplus.words")) {
                completions.addAll(Arrays.asList("bad", "safe", "blocked"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("words") && args[1].equalsIgnoreCase("list")) {
            if (sender.hasPermission("chatfilterplus.words")) {
                completions.addAll(Arrays.asList("bad", "safe", "blocked", "all"));
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("words") && args[1].equalsIgnoreCase("remove")) {
            String type = args[2].toLowerCase();
            if ("bad".equals(type)) {
                completions.addAll(wordsManager.getBadWordsList());
            } else if ("safe".equals(type)) {
                completions.addAll(wordsManager.getSafeWords());
            } else if ("blocked".equals(type)) {
                completions.addAll(wordsManager.getBlockedWordsList());
            }
        } else if (args.length == 4 && args[0].equalsIgnoreCase("words") && args[1].equalsIgnoreCase("list")) {
            String type = args[2].toLowerCase();
            if ("bad".equals(type)) {
                completions.addAll(wordsManager.getBadWordsList());
            } else if ("safe".equals(type)) {
                completions.addAll(wordsManager.getSafeWords());
            } else if ("blocked".equals(type)) {
                completions.addAll(wordsManager.getBlockedWordsList());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("links")) {
            if (sender.hasPermission("chatfilterplus.links")) {
                completions.addAll(Arrays.asList("whitelist", "keywords", "list"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("links") && args[1].equalsIgnoreCase("whitelist")) {
            if (sender.hasPermission("chatfilterplus.links")) {
                completions.addAll(Arrays.asList("add", "remove"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("links") && args[1].equalsIgnoreCase("keywords")) {
            if (sender.hasPermission("chatfilterplus.links")) {
                completions.addAll(Arrays.asList("add", "remove", "list"));
            }
        } else if (args.length >= 4 && args[0].equalsIgnoreCase("links")
                && args[1].equalsIgnoreCase("keywords") && args[2].equalsIgnoreCase("remove")) {
            if (sender.hasPermission("chatfilterplus.links")) {
                String prefix = String.join(" ", Arrays.copyOfRange(args, 3, args.length)).toLowerCase(Locale.ROOT);
                completions.addAll(linksManager.getPromoKeywords().stream()
                        .filter(k -> k.toLowerCase(Locale.ROOT).startsWith(prefix) || k.toLowerCase(Locale.ROOT).contains(prefix))
                        .collect(Collectors.toList()));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("caps")) {
            if (sender.hasPermission("chatfilterplus.caps")) {
                completions.addAll(Arrays.asList("whitelist", "list"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("caps") && args[1].equalsIgnoreCase("whitelist")) {
            if (sender.hasPermission("chatfilterplus.caps")) {
                completions.addAll(Arrays.asList("add", "remove"));
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("notify")) {
            if (sender.hasPermission("chatfilterplus.notify")) {
                completions.addAll(Arrays.asList("on", "off"));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("notify")) {
            if (sender.hasPermission("chatfilterplus.notify")) {
                completions.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reload")) {
            if (sender.hasPermission("chatfilterplus.reload")) {
                completions.addAll(Arrays.asList("configs", "cache", "all"));
            }
        }

        return completions;
    }

    private boolean hasAnyCommandPermission(CommandSender sender) {
        return sender.hasPermission("chatfilterplus.words") ||
                sender.hasPermission("chatfilterplus.links") ||
                sender.hasPermission("chatfilterplus.caps") ||
                sender.hasPermission("chatfilterplus.notify") ||
                sender.hasPermission("chatfilterplus.reload");
    }

    private void addPermittedSubcommands(CommandSender sender, List<String> completions) {
        if (sender.hasPermission("chatfilterplus.words")) completions.add("words");
        if (sender.hasPermission("chatfilterplus.links")) completions.add("links");
        if (sender.hasPermission("chatfilterplus.caps")) completions.add("caps");
        if (sender.hasPermission("chatfilterplus.notify")) completions.add("notify");
        if (sender.hasPermission("chatfilterplus.reload")) completions.add("reload");
        completions.add("help");
    }
}