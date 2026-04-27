package org.gw.chatfilterplus.commands;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.managers.*;

public class CommandsHandler implements CommandExecutor {

    private final ChatFilterPlus plugin;
    private final WordsCommand wordsCommand;
    private final LinksCommand linksCommand;
    private final CapsCommand capsCommand;
    private final NotifyCommand notifyCommand;
    private final ReloadCommand reloadCommand;
    private final HelpCommand helpCommand;

    public CommandsHandler(ChatFilterPlus plugin, WordsManager wordsManager,
                           BlockedWordsManager blockedWordsManager,
                           LinksManager linksManager,
                           CapsManager capsManager,
                           ConfigManager configManager,
                           ChatManager chatManager) {
        this.plugin = plugin;
        this.wordsCommand = new WordsCommand(plugin, wordsManager, blockedWordsManager);
        this.linksCommand = new LinksCommand(plugin, linksManager);
        this.capsCommand = new CapsCommand(plugin, capsManager);
        this.notifyCommand = new NotifyCommand(plugin);
        this.reloadCommand = new ReloadCommand(plugin, configManager, chatManager);
        this.helpCommand = new HelpCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (hasAnyCommandPermission(sender)) {
                helpCommand.execute(sender, args);
            } else {
                plugin.getConfigManager().executeActions(sender, "errors.no-permission");
            }
            return true;
        }

        return switch (args[0].toLowerCase()) {
            case "words" -> handleWords(sender, args);
            case "links" -> handleLinks(sender, args);
            case "caps" -> handleCaps(sender, args);
            case "notify" -> handleNotify(sender, args);
            case "reload" -> handleReload(sender, args);
            case "help" -> handleHelp(sender, args);
            default -> {
                if (hasAnyCommandPermission(sender)) {
                    helpCommand.execute(sender, args);
                } else {
                    plugin.getConfigManager().executeActions(sender, "errors.no-permission");
                }
                yield true;
            }
        };
    }

    private boolean hasAnyCommandPermission(CommandSender sender) {
        return sender.hasPermission("chatfilterplus.words") ||
                sender.hasPermission("chatfilterplus.links") ||
                sender.hasPermission("chatfilterplus.caps") ||
                sender.hasPermission("chatfilterplus.notify") ||
                sender.hasPermission("chatfilterplus.reload");
    }

    private boolean handleWords(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chatfilterplus.words")) {
            plugin.getConfigManager().executeActions(sender, "errors.no-permission");
            return true;
        }
        return wordsCommand.execute(sender, args);
    }

    private boolean handleLinks(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chatfilterplus.links")) {
            plugin.getConfigManager().executeActions(sender, "errors.no-permission");
            return true;
        }
        return linksCommand.execute(sender, args);
    }

    private boolean handleCaps(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chatfilterplus.caps")) {
            plugin.getConfigManager().executeActions(sender, "errors.no-permission");
            return true;
        }
        return capsCommand.execute(sender, args);
    }

    private boolean handleNotify(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chatfilterplus.notify")) {
            plugin.getConfigManager().executeActions(sender, "errors.no-permission");
            return true;
        }
        return notifyCommand.execute(sender, args);
    }

    private boolean handleReload(CommandSender sender, String[] args) {
        if (!sender.hasPermission("chatfilterplus.reload")) {
            plugin.getConfigManager().executeActions(sender, "errors.no-permission");
            return true;
        }
        return reloadCommand.execute(sender, args);
    }

    private boolean handleHelp(CommandSender sender, String[] args) {
        if (!hasAnyCommandPermission(sender)) {
            plugin.getConfigManager().executeActions(sender, "errors.no-permission");
            return true;
        }
        helpCommand.execute(sender, args);
        return true;
    }
}