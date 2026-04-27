package org.gw.chatfilterplus.utils;

import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

public class BStats {

    public BStats(JavaPlugin plugin) {
        if (plugin == null) return;

        try {
            new Metrics(plugin, 30379);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}