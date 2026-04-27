package org.gw.chatfilterplus.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.managers.ChatManager;
import org.gw.chatfilterplus.utils.AntiSpamResult;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class CommandFilterListener implements Listener {

    private final ChatFilterPlus plugin;
    private final ChatManager chatManager;
    private final Set<String> filteredCommands = new HashSet<>();

    public CommandFilterListener(ChatFilterPlus plugin, ChatManager chatManager) {
        this.plugin = plugin;
        this.chatManager = chatManager;

        for (String cmd : plugin.getConfigManager().getCommandFilteringCommands()) {
            filteredCommands.add(cmd.toLowerCase(Locale.ROOT));
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (!plugin.getConfigManager().isCommandFilteringEnabled()) return;

        String fullMessage = event.getMessage();
        if (fullMessage == null || fullMessage.isEmpty() || !fullMessage.startsWith("/")) return;

        String withoutSlash = fullMessage.substring(1);
        int spaceIndex = withoutSlash.indexOf(' ');
        if (spaceIndex == -1) return;

        String command = withoutSlash.substring(0, spaceIndex).toLowerCase(Locale.ROOT);
        if (!filteredCommands.contains(command)) return;

        String commandMessage = withoutSlash.substring(spaceIndex + 1);

        chatManager.handleCommandMessage(event.getPlayer(), commandMessage, filteredMessage -> {
            event.setMessage("/" + command + " " + filteredMessage);
        });
    }
}