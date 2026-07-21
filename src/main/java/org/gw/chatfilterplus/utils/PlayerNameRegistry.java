package org.gw.chatfilterplus.utils;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class PlayerNameRegistry implements Listener {

    private volatile Set<String> names = Set.of();

    public Set<String> names() {
        return names;
    }

    public void refresh() {
        Set<String> snapshot = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            addName(snapshot, player.getName());
        }
        names = Set.copyOf(snapshot);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Set<String> updated = new HashSet<>(names);
        if (addName(updated, event.getPlayer().getName())) {
            names = Set.copyOf(updated);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        String name = normalize(event.getPlayer().getName());
        if (name.isEmpty() || !names.contains(name)) return;

        Set<String> updated = new HashSet<>(names);
        updated.remove(name);
        names = Set.copyOf(updated);
    }

    public void clear() {
        names = Set.of();
    }

    private static boolean addName(Set<String> target, String rawName) {
        String name = normalize(rawName);
        if (name.length() < 2) return false;
        return target.add(name);
    }

    /** Same letter-only, lowercase normalization used to build {@link #names()} — callers matching
     * arbitrary text against online player names (e.g. distinguishing an @mention of a real player
     * from an advertised handle) must normalize with this exact method, since Minecraft usernames
     * routinely contain digits/underscores that other normalizers (leet/homoglyph-aware) transform
     * differently. */
    public static String normalize(String rawName) {
        if (rawName == null) return "";
        StringBuilder sb = new StringBuilder(rawName.length());
        for (int i = 0; i < rawName.length(); i++) {
            char c = rawName.charAt(i);
            if (Character.isLetter(c)) sb.append(Character.toLowerCase(c));
        }
        return sb.toString().toLowerCase(Locale.ROOT);
    }
}
