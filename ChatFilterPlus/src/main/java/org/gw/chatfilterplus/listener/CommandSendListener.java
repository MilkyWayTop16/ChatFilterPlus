package org.gw.chatfilterplus.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

public class CommandSendListener implements Listener {

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        boolean hasAnyPermission = event.getPlayer().hasPermission("chatfilterplus.addword") ||
                event.getPlayer().hasPermission("chatfilterplus.removeword") ||
                event.getPlayer().hasPermission("chatfilterplus.reload");

        if (!hasAnyPermission) {
            event.getCommands().remove("chatfilterplus");
            event.getCommands().remove("cfp");
            event.getCommands().remove("chatfilterplus:chatfilterplus");
            event.getCommands().remove("chatfilterplus:cfp");
        }
    }
}