package org.gw.chatfilterplus.commands;

import org.bukkit.command.CommandSender;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.managers.BlockedWordsManager;
import org.gw.chatfilterplus.managers.WordsManager;

import java.util.List;

public class WordsCommand {

    private final ChatFilterPlus plugin;
    private final WordsManager wordsManager;
    private final BlockedWordsManager blockedWordsManager;

    public WordsCommand(ChatFilterPlus plugin, WordsManager wordsManager, BlockedWordsManager blockedWordsManager) {
        this.plugin = plugin;
        this.wordsManager = wordsManager;
        this.blockedWordsManager = blockedWordsManager;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.getConfigManager().executeActions(sender, "help");
            return true;
        }

        return switch (args[1].toLowerCase()) {
            case "add" -> handleAdd(sender, args);
            case "remove" -> handleRemove(sender, args);
            case "list" -> handleList(sender, args);
            default -> {
                plugin.getConfigManager().executeActions(sender, "help");
                yield true;
            }
        };
    }

    private boolean handleAdd(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.getConfigManager().executeActions(sender, "errors.invalid-args-words-add");
            return true;
        }

        String type = args[2].toLowerCase();
        String word = args[3];
        String replacement = args.length >= 5 ? args[4] : "*";

        boolean success = switch (type) {
            case "bad" -> wordsManager.addBadWord(sender, word, replacement);
            case "safe" -> {
                wordsManager.addSafeWord(sender, word);
                yield true;
            }
            case "blocked" -> blockedWordsManager.addBlockedWord(word);
            default -> false;
        };

        if (success) {
            plugin.getConfigManager().executeActions(sender, "words.add.success", java.util.Map.of("word", word, "type", type));
        } else {
            plugin.getConfigManager().executeActions(sender, "words.add.already-exists", java.util.Map.of("word", word));
        }
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 4) {
            plugin.getConfigManager().executeActions(sender, "help");
            return true;
        }

        String type = args[2].toLowerCase();
        String word = args[3];

        boolean success = switch (type) {
            case "bad" -> wordsManager.removeBadWord(sender, word);
            case "safe" -> wordsManager.removeSafeWord(sender, word);
            case "blocked" -> blockedWordsManager.removeBlockedWord(word);
            default -> false;
        };

        if (success) {
            plugin.getConfigManager().executeActions(sender, "words.remove.success", java.util.Map.of("word", word, "type", type));
        } else {
            plugin.getConfigManager().executeActions(sender, "words.remove.not-found", java.util.Map.of("word", word, "type", type));
        }
        return true;
    }

    private boolean handleList(CommandSender sender, String[] args) {
        String type = args.length >= 3 ? args[2].toLowerCase() : "all";

        switch (type) {
            case "bad" -> sendList(sender, "bad-words", wordsManager.getBadWordsList());
            case "safe" -> sendList(sender, "safe-words", wordsManager.getSafeWords());
            case "blocked" -> sendList(sender, "blocked-words", blockedWordsManager.getBlockedWordsList());
            case "all" -> {
                sendList(sender, "bad-words", wordsManager.getBadWordsList());
                sendList(sender, "safe-words", wordsManager.getSafeWords());
                sendList(sender, "blocked-words", blockedWordsManager.getBlockedWordsList());
            }
            default -> plugin.getConfigManager().executeActions(sender, "help");
        }
        return true;
    }

    private void sendList(CommandSender sender, String type, List<String> list) {
        if (list.isEmpty()) {
            plugin.getConfigManager().executeActions(sender, "words.list.empty", java.util.Map.of("type", type));
            return;
        }
        plugin.getConfigManager().executeActions(sender, "words.list.header", java.util.Map.of("type", type));
        for (String w : list) {
            plugin.getConfigManager().executeActions(sender, "words.list.item", java.util.Map.of("word", w));
        }
    }
}