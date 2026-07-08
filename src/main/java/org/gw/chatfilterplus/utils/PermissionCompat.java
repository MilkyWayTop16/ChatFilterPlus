package org.gw.chatfilterplus.utils;

import org.bukkit.permissions.Permissible;
import org.gw.chatfilterplus.managers.FilterType;

public final class PermissionCompat {

    public static final String CHAT_SPAM = "chatfilterplus.bypass.chatfilter.spam";
    public static final String CHAT_ANTISPAM = "chatfilterplus.bypass.chatfilter.antispam";
    public static final String PUNISH_SPAM = "chatfilterplus.bypass.punishment.spam";
    public static final String PUNISH_ANTISPAM = "chatfilterplus.bypass.punishment.antispam";

    private PermissionCompat() {
    }

    public static boolean hasChatFilterBypass(Permissible permissible, FilterType type) {
        if (permissible == null || type == null) return false;
        if (permissible.hasPermission(type.bypassPermission())) return true;
        return type == FilterType.ANTI_SPAM && permissible.hasPermission(CHAT_ANTISPAM);
    }

    public static boolean hasPermission(Permissible permissible, String permission) {
        if (permissible == null || permission == null || permission.isEmpty()) return false;
        if (permissible.hasPermission(permission)) return true;

        if (CHAT_SPAM.equalsIgnoreCase(permission)) {
            return permissible.hasPermission(CHAT_ANTISPAM);
        }
        if (PUNISH_SPAM.equalsIgnoreCase(permission)) {
            return permissible.hasPermission(PUNISH_ANTISPAM);
        }
        return false;
    }
}
