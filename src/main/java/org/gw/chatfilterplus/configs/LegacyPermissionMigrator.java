package org.gw.chatfilterplus.configs;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.PermissionHolder;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.matcher.NodeMatcher;
import net.luckperms.api.node.types.PermissionNode;
import org.bukkit.Bukkit;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.PermissionCompat;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

public final class LegacyPermissionMigrator {

    private static final String FLAG_FILE = "migrations" + File.separator + "perms-antispam-to-spam.done";

    private static final String[][] PERMISSION_PAIRS = {
            {PermissionCompat.CHAT_ANTISPAM, PermissionCompat.CHAT_SPAM},
            {PermissionCompat.PUNISH_ANTISPAM, PermissionCompat.PUNISH_SPAM}
    };

    private final ChatFilterPlus plugin;

    public LegacyPermissionMigrator(ChatFilterPlus plugin) {
        this.plugin = plugin;
    }

    public void migrateAsyncIfNeeded() {
        File flag = new File(plugin.getDataFolder(), FLAG_FILE);
        if (flag.exists()) return;

        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            writeFlag(flag, "skipped-no-luckperms " + LocalDateTime.now());
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                int changed = migrateAll();
                writeFlag(flag, "done " + LocalDateTime.now() + " nodes-added=" + changed);
            } catch (Exception e) {
                plugin.error("Ошибка миграции прав antispam→spam: " + e.getMessage());
            }
        });
    }

    private int migrateAll() {
        LuckPerms lp = LuckPermsProvider.get();
        AtomicInteger added = new AtomicInteger();

        for (String[] pair : PERMISSION_PAIRS) {
            String oldPerm = pair[0];
            String newPerm = pair[1];
            NodeMatcher<? extends Node> matcher = NodeMatcher.key(oldPerm);

            Map<String, ? extends Collection<? extends Node>> groups =
                    lp.getGroupManager().searchAll(matcher).join();
            for (Map.Entry<String, ? extends Collection<? extends Node>> entry : groups.entrySet()) {
                Group group = lp.getGroupManager().loadGroup(entry.getKey()).join().orElse(null);
                if (group == null) continue;
                if (applyNewNodes(group, entry.getValue(), newPerm, added)) {
                    lp.getGroupManager().saveGroup(group).join();
                }
            }

            Map<UUID, ? extends Collection<? extends Node>> users =
                    lp.getUserManager().searchAll(matcher).join();
            for (Map.Entry<UUID, ? extends Collection<? extends Node>> entry : users.entrySet()) {
                User user = lp.getUserManager().loadUser(entry.getKey()).join();
                if (user == null) continue;
                if (applyNewNodes(user, entry.getValue(), newPerm, added)) {
                    lp.getUserManager().saveUser(user).join();
                }
            }
        }

        return added.get();
    }

    private boolean applyNewNodes(PermissionHolder holder,
                                  Collection<? extends Node> oldNodes,
                                  String newPerm,
                                  AtomicInteger added) {
        boolean modified = false;
        List<Node> toAdd = new ArrayList<>();

        for (Node raw : oldNodes) {
            if (!(raw instanceof PermissionNode oldNode)) continue;
            if (hasEquivalentNode(holder, newPerm, oldNode)) continue;

            PermissionNode.Builder builder = PermissionNode.builder(newPerm)
                    .value(oldNode.getValue())
                    .context(oldNode.getContexts());

            if (oldNode.hasExpiry() && oldNode.getExpiry() != null) {
                builder.expiry(oldNode.getExpiry());
            }

            toAdd.add(builder.build());
        }

        for (Node node : toAdd) {
            holder.data().add(node);
            added.incrementAndGet();
            modified = true;
        }
        return modified;
    }

    private boolean hasEquivalentNode(PermissionHolder holder, String newPerm, PermissionNode oldNode) {
        for (Node node : holder.getNodes()) {
            if (!(node instanceof PermissionNode permissionNode)) continue;
            if (!permissionNode.getKey().equalsIgnoreCase(newPerm)) continue;
            if (permissionNode.getValue() != oldNode.getValue()) continue;
            if (!permissionNode.getContexts().equals(oldNode.getContexts())) continue;
            return true;
        }
        return false;
    }

    private void writeFlag(File flag, String content) {
        try {
            File parent = flag.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Files.writeString(flag.toPath(), content, StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.error("Не удалось записать флаг миграции прав: " + e.getMessage());
        }
    }
}
