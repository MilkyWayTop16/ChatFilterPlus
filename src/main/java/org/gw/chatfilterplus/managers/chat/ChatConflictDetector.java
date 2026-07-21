package org.gw.chatfilterplus.managers.chat;

import org.bukkit.event.EventPriority;
import org.bukkit.plugin.RegisteredListener;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ChatConflictDetector {

    private final ChatFilterPlus plugin;
    private final AtomicBoolean reported = new AtomicBoolean();

    public ChatConflictDetector(ChatFilterPlus plugin) {
        this.plugin = plugin;
    }

    public void reset() {
        reported.set(false);
    }

    public void verify(boolean expectedBlocked,
                       String expectedMessage,
                       boolean actualCancelled,
                       String actualMessage,
                       RegisteredListener[] listeners,
                       EventPriority ourPriority) {

        if (reported.get()) return;

        if (expectedBlocked) {
            if (actualCancelled) return;
            report("Сообщение &#FF5D00должно было &fбыть заблокировано, но другой плагин &#FF5D00не дал отменить &fсобытие чата...",
                    listeners, ourPriority);
            return;
        }

        if (expectedMessage == null || actualCancelled) return;
        if (expectedMessage.equals(actualMessage)) return;

        if (actualMessage != null && !expectedMessage.isEmpty() && actualMessage.contains(expectedMessage)) {
            return;
        }

        report("Отфильтрованный текст &#FF5D00не дошёл &fдо отправки, его &#FF5D00перезаписал другой &fплагин...",
                listeners, ourPriority);
    }

    private void report(String symptom, RegisteredListener[] listeners, EventPriority ourPriority) {
        if (!reported.compareAndSet(false, true)) return;

        Set<String> suspects = collectSuspects(listeners, ourPriority);
        String detail = suspects.isEmpty()
                ? symptom
                : symptom + " &f(Плагин(ы): &#FF5D00" + String.join("&f, &#FF5D00", suspects) + "&f)";
        plugin.error("Обнаружен &#FF5D00конфликт &fс другим плагином на чат: &#FF5D00" + detail
                + "&f; из-за которого &#FF5D00часть сообщений &fили &#FF5D00все сообщения &fмогут вообще &#FF5D00не фильтроваться&f...");
    }

    private Set<String> collectSuspects(RegisteredListener[] listeners, EventPriority ourPriority) {
        Set<String> suspects = new LinkedHashSet<>();
        if (listeners == null) return suspects;

        for (RegisteredListener listener : listeners) {
            if (listener == null || listener.getPlugin() == null) continue;
            if (listener.getPlugin().equals(plugin)) continue;
            if (listener.getPriority().ordinal() >= ourPriority.ordinal()) {
                suspects.add(listener.getPlugin().getName());
            }
        }
        return suspects;
    }
}
