package org.gw.chatfilterplus.managers.chat;

import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.gw.chatfilterplus.managers.ChatManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ChatDecisionStore {

    private static final long DECISION_TTL_MILLIS = 3_000L;
    private static final long REAPPLY_DEDUPE_MILLIS = 250L;

    private final Plugin plugin;
    private final Map<UUID, PendingChatDecision> pendingChat = new ConcurrentHashMap<>();
    private final Map<UUID, PendingCommandDecision> pendingCommand = new ConcurrentHashMap<>();
    private BukkitTask cleanupTask;

    public ChatDecisionStore(Plugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (cleanupTask != null) return;
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::cleanupExpired, 100L, 100L);
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        clear();
    }

    public void clear() {
        pendingChat.clear();
        pendingCommand.clear();
    }

    public void clearPlayer(UUID uuid) {
        if (uuid == null) return;
        pendingChat.remove(uuid);
        pendingCommand.remove(uuid);
    }

    public void putChat(UUID uuid, String originalMessage, String finalMessage,
                        boolean blocked, boolean modified) {
        pendingChat.put(uuid, new PendingChatDecision(originalMessage, finalMessage, blocked, modified));
    }

    public void putCommand(UUID uuid, String commandLabel, String originalArgs,
                           String finalFullMessage, boolean blocked, boolean modified) {
        pendingCommand.put(uuid, new PendingCommandDecision(
                commandLabel, originalArgs, finalFullMessage, blocked, modified));
    }

    public boolean hasActiveMatchingDecision(UUID uuid, String currentMessage) {
        if (uuid == null) return false;

        PendingChatDecision decision = pendingChat.get(uuid);
        if (decision == null || decision.isExpired()) {
            if (decision != null) pendingChat.remove(uuid, decision);
            return false;
        }

        String current = currentMessage != null ? currentMessage : "";
        return decision.matches(current);
    }

    public boolean tryReapplyExisting(UUID uuid,
                                      String currentMessage,
                                      ChatManager.ChatEventAccess access,
                                      boolean aggressiveMode) {
        if (uuid == null) return false;

        PendingChatDecision decision = pendingChat.get(uuid);
        if (decision == null || decision.isExpired()) {
            if (decision != null) pendingChat.remove(uuid, decision);
            return false;
        }

        String current = currentMessage != null ? currentMessage : "";
        if (!decision.matches(current)) {
            return false;
        }
        if (System.currentTimeMillis() - decision.createdAt > REAPPLY_DEDUPE_MILLIS) {
            return false;
        }

        if (access != null) {
            applyDecision(decision, access, aggressiveMode);
        }
        return true;
    }

    public void enforceChat(UUID uuid,
                            String currentMessage,
                            String eventOriginalPlain,
                            ChatManager.ChatEventAccess access,
                            boolean aggressiveMode) {
        if (access == null || uuid == null) return;

        PendingChatDecision decision = pendingChat.get(uuid);
        if (decision == null || decision.isExpired()) {
            if (decision != null) pendingChat.remove(uuid, decision);
            return;
        }

        String current = currentMessage != null ? currentMessage : "";
        if (!shouldReapplyDecision(decision, current, eventOriginalPlain)) {
            return;
        }

        applyDecision(decision, access, aggressiveMode);
    }

    private static void applyDecision(PendingChatDecision decision,
                                      ChatManager.ChatEventAccess access,
                                      boolean aggressiveMode) {
        if (decision.blocked) {
            access.block();
            return;
        }

        if (decision.modified) {
            access.setMessage(decision.finalMessage);
            if (aggressiveMode && access.isCancelled()) {
                access.uncancel();
            }
        }
    }

    public void verifyChat(UUID uuid,
                           String actualMessage,
                           boolean actualCancelled,
                           ChatConflictDetector detector,
                           org.bukkit.plugin.RegisteredListener[] listeners,
                           org.bukkit.event.EventPriority ourPriority) {
        if (uuid == null || detector == null) return;

        PendingChatDecision decision = pendingChat.get(uuid);
        if (decision == null || decision.isExpired()) return;
        if (!decision.blocked && !decision.modified) return;

        detector.verify(
                decision.blocked,
                decision.modified ? decision.finalMessage : null,
                actualCancelled,
                actualMessage,
                listeners,
                ourPriority);
    }

    public void enforceCommand(PlayerCommandPreprocessEvent event) {
        if (event == null || event.getPlayer() == null) return;

        UUID uuid = event.getPlayer().getUniqueId();
        PendingCommandDecision decision = pendingCommand.get(uuid);
        if (decision == null || decision.isExpired()) {
            if (decision != null) pendingCommand.remove(uuid, decision);
            return;
        }

        String current = event.getMessage();
        if (decision.blocked) {
            event.setCancelled(true);
            return;
        }

        if (decision.modified && decision.finalFullMessage != null && !decision.finalFullMessage.isEmpty()) {
            String withoutSlash = current.startsWith("/") ? current.substring(1) : current;
            int space = withoutSlash.indexOf(' ');
            String label = space == -1 ? withoutSlash : withoutSlash.substring(0, space);
            if (decision.commandLabel.isEmpty() || decision.commandLabel.equalsIgnoreCase(label)) {
                event.setMessage(decision.finalFullMessage);
            }
        }
    }

    private void cleanupExpired() {
        if (!plugin.isEnabled()) return;
        pendingChat.entrySet().removeIf(e -> e.getValue().isExpired());
        pendingCommand.entrySet().removeIf(e -> e.getValue().isExpired());
    }

    private static boolean shouldReapplyDecision(PendingChatDecision decision,
                                                 String current,
                                                 String eventOriginalPlain) {
        if (decision.matches(current)) {
            return true;
        }

        if (eventOriginalPlain != null && !eventOriginalPlain.isEmpty()) {
            if (decision.originalMessage.equals(eventOriginalPlain)
                    || decision.matches(eventOriginalPlain)) {
                return true;
            }
        }

        if (isSpecificAnchor(decision.originalMessage) && current.contains(decision.originalMessage)) {
            return true;
        }
        if (decision.modified
                && isSpecificAnchor(decision.finalMessage)
                && !decision.finalMessage.equals(decision.originalMessage)
                && current.contains(decision.finalMessage)) {
            return true;
        }

        return false;
    }

    private static boolean isSpecificAnchor(String text) {
        return text != null && text.length() >= 3;
    }

    private static final class PendingChatDecision {
        final String originalMessage;
        final String finalMessage;
        final boolean blocked;
        final boolean modified;
        final long createdAt;

        PendingChatDecision(String originalMessage, String finalMessage, boolean blocked, boolean modified) {
            this.originalMessage = originalMessage;
            this.finalMessage = finalMessage;
            this.blocked = blocked;
            this.modified = modified;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > DECISION_TTL_MILLIS;
        }

        boolean matches(String current) {
            return originalMessage.equals(current) || finalMessage.equals(current);
        }
    }

    private static final class PendingCommandDecision {
        final String commandLabel;
        final String originalArgs;
        final String finalFullMessage;
        final boolean blocked;
        final boolean modified;
        final long createdAt;

        PendingCommandDecision(String commandLabel, String originalArgs, String finalFullMessage,
                               boolean blocked, boolean modified) {
            this.commandLabel = commandLabel;
            this.originalArgs = originalArgs;
            this.finalFullMessage = finalFullMessage;
            this.blocked = blocked;
            this.modified = modified;
            this.createdAt = System.currentTimeMillis();
        }

        boolean isExpired() {
            return System.currentTimeMillis() - createdAt > DECISION_TTL_MILLIS;
        }
    }
}
