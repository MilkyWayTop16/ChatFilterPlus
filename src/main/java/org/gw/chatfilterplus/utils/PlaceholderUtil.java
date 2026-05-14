package org.gw.chatfilterplus.utils;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class PlaceholderUtil {

    private static final boolean placeholderAPIAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

    public static String parse(Player player, String text) {
        if (text == null || text.isEmpty()) return text;

        if (placeholderAPIAvailable && player != null) {
            return PlaceholderAPI.setPlaceholders(player, text);
        }
        return text;
    }
}