package org.gw.chatfilterplus.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.Set;

public class CommandSendListener implements Listener {

    private static final Set<String> COMMAND_LABELS = Set.of(
            "chatfilterplus",
            "cfp",
            "chatfilterplus:chatfilterplus",
            "chatfilterplus:cfp"
    );

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommandSend(PlayerCommandSendEvent event) {
        boolean hasAnyPermission = event.getPlayer().hasPermission("chatfilterplus.words") ||
                event.getPlayer().hasPermission("chatfilterplus.links") ||
                event.getPlayer().hasPermission("chatfilterplus.caps") ||
                event.getPlayer().hasPermission("chatfilterplus.notify") ||
                event.getPlayer().hasPermission("chatfilterplus.reload") ||
                event.getPlayer().hasPermission("chatfilterplus.admin");

        if (!hasAnyPermission) {
            event.getCommands().removeAll(COMMAND_LABELS);
        }
    }
}