package org.gw.chatfilterplus.listeners;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.gw.chatfilterplus.managers.ChatManager;
import org.gw.chatfilterplus.utils.HexColors;

import java.util.ConcurrentModificationException;
import java.util.Iterator;
import java.util.Set;

public final class PaperChatSupport {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();

    private PaperChatSupport() {
    }

    public static void register(Plugin plugin,
                                ChatManager chatManager,
                                EventPriority eventPriority,
                                boolean ignoreCancelled,
                                boolean readOnly) {
        EventExecutor primary = (listener, event) -> {
            if (event instanceof AsyncChatEvent chatEvent) {
                handlePrimary(chatManager, chatEvent, readOnly);
            }
        };

        Bukkit.getPluginManager().registerEvent(
                AsyncChatEvent.class,
                chatManager,
                eventPriority,
                primary,
                plugin,
                ignoreCancelled
        );

        if (!readOnly) {
            EventExecutor enforce = (listener, event) -> {
                if (event instanceof AsyncChatEvent chatEvent) {
                    handleEnforce(chatManager, chatEvent);
                }
            };

            Bukkit.getPluginManager().registerEvent(
                    AsyncChatEvent.class,
                    chatManager,
                    EventPriority.HIGHEST,
                    enforce,
                    plugin,
                    false
            );

            EventExecutor verify = (listener, event) -> {
                if (event instanceof AsyncChatEvent chatEvent) {
                    chatManager.verifyChat(
                            chatEvent.getPlayer(),
                            toPlain(chatEvent.message()),
                            chatEvent.isCancelled(),
                            AsyncChatEvent.getHandlerList().getRegisteredListeners(),
                            eventPriority);
                }
            };

            Bukkit.getPluginManager().registerEvent(
                    AsyncChatEvent.class,
                    chatManager,
                    EventPriority.MONITOR,
                    verify,
                    plugin,
                    false
            );
        }
    }

    private static void handlePrimary(ChatManager chatManager, AsyncChatEvent event, boolean readOnly) {
        String toFilter = toPlain(event.message());
        if (toFilter.isEmpty()) {
            toFilter = toPlain(event.originalMessage());
        }
        chatManager.onChat(event.getPlayer(), toFilter, readOnly, paperAccess(event));
    }

    private static void handleEnforce(ChatManager chatManager, AsyncChatEvent event) {
        String current = toPlain(event.message());
        String original = toPlain(event.originalMessage());
        chatManager.enforceChat(event.getPlayer(), current, original, paperAccess(event));
    }

    private static ChatManager.ChatEventAccess paperAccess(AsyncChatEvent event) {
        return new ChatManager.ChatEventAccess() {
            @Override
            public boolean isCancelled() {
                return event.isCancelled();
            }

            @Override
            public void setMessage(String plainMessage) {
                event.message(toComponent(plainMessage));
            }

            @Override
            public void block() {
                event.setCancelled(true);
                try {
                    event.viewers().clear();
                } catch (UnsupportedOperationException | ConcurrentModificationException ignored) {
                    try {
                        Set<Audience> viewers = event.viewers();
                        Iterator<Audience> iterator = viewers.iterator();
                        while (iterator.hasNext()) {
                            iterator.next();
                            iterator.remove();
                        }
                    } catch (Exception ignored2) {
                    }
                }
            }

            @Override
            public void uncancel() {
                event.setCancelled(false);
            }
        };
    }

    static String toPlain(Component component) {
        if (component == null) return "";
        try {
            return PLAIN.serialize(component);
        } catch (Throwable t) {
            try {
                return stripSection(LEGACY_SECTION.serialize(component));
            } catch (Throwable t2) {
                return component.toString();
            }
        }
    }

    private static String stripSection(String legacy) {
        if (legacy == null || legacy.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(legacy.length());
        for (int i = 0; i < legacy.length(); i++) {
            char c = legacy.charAt(i);
            if (c == '§' && i + 1 < legacy.length()) {
                i++;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    static Component toComponent(String plainMessage) {
        if (plainMessage == null || plainMessage.isEmpty()) {
            return Component.empty();
        }
        try {
            return HexColors.translateToComponent(HexColors.stripMiniMessageTags(plainMessage));
        } catch (Throwable t) {
            return Component.text(plainMessage);
        }
    }
}
