package org.gw.chatfilterplus.commands;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.util.HashMap;
import java.util.Map;

public class NotifyCommand {

    private final ChatFilterPlus plugin;

    public NotifyCommand(ChatFilterPlus plugin) {
        this.plugin = plugin;
    }

    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 1) {
            if (!(sender instanceof Player player)) {
                plugin.getConfigManager().executeActions(sender, "errors.notify-console-cannot-toggle");
                return true;
            }

            boolean newState = !plugin.getNotificationManager().isNotificationsEnabled(player);
            plugin.getNotificationManager().setNotificationsEnabled(player, newState);
            sendNotificationMessage(sender, player, newState, true);
            return true;
        }

        if (args.length == 2) {
            if (!(sender instanceof Player player)) {
                plugin.getConfigManager().executeActions(sender, "errors.notify-console-cannot-toggle");
                return true;
            }

            String state = args[1].toLowerCase();
            if (!state.equals("on") && !state.equals("off")) {
                plugin.getConfigManager().executeActions(sender, "help");
                return true;
            }

            boolean newState = state.equals("on");
            plugin.getNotificationManager().setNotificationsEnabled(player, newState);
            sendNotificationMessage(sender, player, newState, true);
            return true;
        }

        if (args.length == 3) {
            String state = args[1].toLowerCase();
            if (!state.equals("on") && !state.equals("off")) {
                plugin.getConfigManager().executeActions(sender, "help");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                Map<String, String> ph = new HashMap<>();
                ph.put("player", args[2]);
                plugin.getConfigManager().executeActions(sender, "errors.notify-player-not-found", ph);
                return true;
            }

            boolean newState = state.equals("on");
            boolean isSelf = sender.equals(target);

            plugin.getNotificationManager().setNotificationsEnabled(target, newState);
            sendNotificationMessage(sender, target, newState, isSelf);
            return true;
        }

        plugin.getConfigManager().executeActions(sender, "help");
        return true;
    }

    private void sendNotificationMessage(CommandSender sender, Player target, boolean enabled, boolean isSelf) {
        Map<String, String> ph = new HashMap<>();
        ph.put("player", target.getName());

        String keyForSender = isSelf
                ? (enabled ? "notify.enabled-self" : "notify.disabled-self")
                : (enabled ? "notify.enabled" : "notify.disabled");

        plugin.getConfigManager().executeActions(sender, keyForSender, ph);

        if (!isSelf) {
            String keyForTarget = enabled ? "notify.enabled-self" : "notify.disabled-self";
            plugin.getConfigManager().executeActions(target, keyForTarget, ph);
        }
    }
}