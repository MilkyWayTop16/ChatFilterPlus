package org.gw.chatfilterplus.configs;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.gw.chatfilterplus.ChatFilterPlus;
import org.gw.chatfilterplus.utils.HexColors;
import org.gw.chatfilterplus.utils.PlaceholderUtil;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ActionManager {

    public record ParsedAction(String type, String content) {
    }

    private final ChatFilterPlus plugin;
    private final Map<String, List<ParsedAction>> parsedActionCache = new ConcurrentHashMap<>();

    public ActionManager(ChatFilterPlus plugin) {
        this.plugin = plugin;
    }

    public void reload(MainConfig mainConfig,
                       BadWordsConfig badWordsConfig,
                       LinksConfig linksConfig,
                       CapsConfig capsConfig,
                       BlockedWordsConfig blockedWordsConfig,
                       AntiSpamConfig antiSpamConfig) {
        parsedActionCache.clear();
        preParseSection("main", mainConfig.getConfig().getConfigurationSection("actions"));
        preParseSection("badwords", badWordsConfig.getConfig().getConfigurationSection("notifications.bad-words"));
        preParseSection("links", linksConfig.getConfig().getConfigurationSection("notifications.links"));
        preParseSection("caps", capsConfig.getConfig().getConfigurationSection("notifications.caps"));
        preParseSection("blockedwords", blockedWordsConfig.getConfig().getConfigurationSection("notifications.blocked-words"));
        preParseSection("spam", antiSpamConfig.getConfig().getConfigurationSection("notifications.spam"));
    }

    private void preParseSection(String prefix, ConfigurationSection section) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            if (section.isList(key)) {
                String cacheKey = prefix + "." + key;
                List<String> raw = section.getStringList(key);
                List<ParsedAction> parsed = new ArrayList<>(raw.size());
                for (String line : raw) {
                    ParsedAction pa = parseActionLine(line);
                    if (pa != null) parsed.add(pa);
                }
                parsedActionCache.put(cacheKey, parsed);
            } else if (section.isConfigurationSection(key)) {
                preParseSection(prefix + "." + key, section.getConfigurationSection(key));
            }
        }
    }

    private ParsedAction parseActionLine(String actionLine) {
        if (actionLine == null || !actionLine.startsWith("[")) return null;
        int end = actionLine.indexOf("]");
        if (end == -1) return null;
        String type = actionLine.substring(1, end).trim().toLowerCase(Locale.ROOT);
        String content = actionLine.substring(end + 1).trim();
        if (type.isEmpty()) return null;
        return new ParsedAction(type, content);
    }

    public List<ParsedAction> getParsedActions(String path) {
        if (path == null) return List.of();
        String key = path.startsWith("main.") ? path : "main." + path;
        return parsedActionCache.getOrDefault(key, List.of());
    }

    public List<ParsedAction> getBadWordsParsedActions(String subPath) {
        return parsedActionCache.getOrDefault("badwords." + subPath, List.of());
    }

    public List<ParsedAction> getLinksParsedActions(String subPath) {
        return parsedActionCache.getOrDefault("links." + subPath, List.of());
    }

    public List<ParsedAction> getCapsParsedActions(String subPath) {
        return parsedActionCache.getOrDefault("caps." + subPath, List.of());
    }

    public List<ParsedAction> getBlockedWordsParsedActions(String subPath) {
        return parsedActionCache.getOrDefault("blockedwords." + subPath, List.of());
    }

    public List<ParsedAction> getAntiSpamParsedActions(String subPath) {
        if (subPath == null) return List.of();
        List<ParsedAction> direct = parsedActionCache.get("spam." + subPath);
        if (direct != null && !direct.isEmpty()) return direct;
        return parsedActionCache.getOrDefault("spam.player." + subPath, List.of());
    }

    public void executeActions(CommandSender sender, String path) {
        Player player = sender instanceof Player p ? p : null;
        executeActions(player, path, null);
    }

    public void executeActions(CommandSender sender, String path, Map<String, String> placeholders) {
        Player player = sender instanceof Player p ? p : null;
        executeActions(player, path, placeholders);
    }

    public void executeActions(Player player, String path, Map<String, String> placeholders) {
        executeParsedActionList(player, getParsedActions(path), placeholders);
    }

    public void executeActionsFromBadWords(Player player, String subPath, Map<String, String> placeholders) {
        executeParsedActionList(player, getBadWordsParsedActions(subPath), placeholders);
    }

    public void executeActionsFromLinks(Player player, String subPath, Map<String, String> placeholders) {
        executeParsedActionList(player, getLinksParsedActions(subPath), placeholders);
    }

    public void executeActionsFromCaps(Player player, String subPath, Map<String, String> placeholders) {
        executeParsedActionList(player, getCapsParsedActions(subPath), placeholders);
    }

    public void executeActionsFromBlockedWords(Player player, String subPath, Map<String, String> placeholders) {
        executeParsedActionList(player, getBlockedWordsParsedActions(subPath), placeholders);
    }

    public void executeActionsFromAntiSpam(Player player, String subPath, Map<String, String> placeholders) {
        executeParsedActionList(player, getAntiSpamParsedActions(subPath), placeholders);
    }

    private void executeParsedActionList(Player player, List<ParsedAction> actions, Map<String, String> placeholders) {
        if (actions.isEmpty()) return;

        Map<String, String> ph = new HashMap<>(placeholders != null ? placeholders : Collections.emptyMap());
        if (player != null && !ph.containsKey("player")) ph.put("player", player.getName());

        for (ParsedAction action : actions) {
            String content = action.content();
            boolean commandAction = "console-command".equals(action.type()) || "player-command".equals(action.type());

            if (commandAction) {
                content = PlaceholderUtil.parse(player, content);
                for (Map.Entry<String, String> e : ph.entrySet()) {
                    String value = e.getValue() != null ? e.getValue() : "";
                    if (PlaceholderUtil.isPlayerControlledPlaceholder(e.getKey())) {
                        value = PlaceholderUtil.sanitizeCommandValue(value);
                    }
                    content = content.replace("{" + e.getKey() + "}", value);
                }
            } else {
                for (Map.Entry<String, String> e : ph.entrySet()) {
                    String value = e.getValue() != null ? e.getValue() : "";
                    if (PlaceholderUtil.isPlayerControlledPlaceholder(e.getKey())) {
                        value = value.replace("%", "");
                        value = HexColors.stripMiniMessageTags(value);
                    }
                    content = content.replace("{" + e.getKey() + "}", value);
                }
                content = PlaceholderUtil.parse(player, content);
            }
            executeParsedAction(player, action.type(), content);
        }
    }

    private void executeParsedAction(Player player, String type, String content) {
        try {
            switch (type) {
                case "message" -> {
                    if (player != null) HexColors.sendMessage(player, content);
                }
                case "message-console" -> plugin.console(content);
                case "broadcast" -> {
                    net.kyori.adventure.text.Component broadcast = HexColors.translateToComponent(content);
                    try {
                        Bukkit.getServer().broadcast(broadcast);
                    } catch (Throwable t) {
                        Bukkit.broadcastMessage(HexColors.translate(content));
                    }
                }
                case "sound" -> executeSound(player, content);
                case "title" -> executeTitle(player, content, false);
                case "subtitle" -> executeTitle(player, content, true);
                case "actionbar" -> {
                    if (player != null) {
                        try {
                            player.sendActionBar(HexColors.translateToComponent(content));
                        } catch (Throwable t) {
                            player.sendMessage(HexColors.translate(content));
                        }
                    }
                }
                case "console-command" -> plugin.getServer().dispatchCommand(plugin.getServer().getConsoleSender(), content);
                case "player-command" -> {
                    if (player != null) plugin.getServer().dispatchCommand(player, content);
                }
                case "effect" -> executePotionEffect(player, content);
                case "teleport" -> executeTeleport(player, content);
                case "give-item" -> executeGiveItem(player, content);
                default -> plugin.console("&#FF5D00Неизвестный тип действия: " + type);
            }
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка выполнения действия [" + type + "]: " + e.getMessage());
        }
    }

    private void executeSound(Player player, String content) {
        if (player == null || content == null) return;
        content = content.trim();
        if (content.isEmpty()) return;

        String[] parts = content.split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) return;

        float volume = 1.0f;
        float pitch = 1.0f;
        try {
            if (parts.length > 1) volume = Float.parseFloat(parts[1]);
            if (parts.length > 2) pitch = Float.parseFloat(parts[2]);
        } catch (NumberFormatException ignored) {
        }

        String rawName = parts[0].trim();
        if (playSoundByEnum(player, rawName, volume, pitch)) return;
        if (playSoundByKey(player, rawName, volume, pitch)) return;

        plugin.console("&#FF5D00Ошибка воспроизведения звука: " + content);
    }

    private boolean playSoundByEnum(Player player, String rawName, float volume, float pitch) {
        try {
            String enumName = rawName.toUpperCase(Locale.ROOT)
                    .replace('-', '_')
                    .replace('.', '_');
            int colon = enumName.indexOf(':');
            if (colon >= 0 && colon + 1 < enumName.length()) {
                enumName = enumName.substring(colon + 1);
            }
            Sound sound = Sound.valueOf(enumName);
            player.playSound(player.getLocation(), sound, SoundCategory.MASTER, volume, pitch);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean playSoundByKey(Player player, String rawName, float volume, float pitch) {
        try {
            String key = rawName.toLowerCase(Locale.ROOT).replace('_', '.');
            if (!key.contains(":")) {
                key = "minecraft:" + key;
            }
            player.playSound(player.getLocation(), key, SoundCategory.MASTER, volume, pitch);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void executeTitle(Player player, String content, boolean isSubtitle) {
        if (player == null || content == null) return;

        String[] parts = content.split(";", 4);
        String rawText = parts[0].trim();
        int fadeIn = parts.length > 1 ? parseIntSafe(parts[1], 10) : 10;
        int stay = parts.length > 2 ? parseIntSafe(parts[2], 70) : 70;
        int fadeOut = parts.length > 3 ? parseIntSafe(parts[3], 20) : 20;

        try {
            if (sendAdventureTitle(player, rawText, isSubtitle, fadeIn, stay, fadeOut)) {
                return;
            }
        } catch (Throwable ignored) {
        }

        try {
            sendLegacyTitle(player, rawText, isSubtitle, fadeIn, stay, fadeOut);
        } catch (Throwable t) {
            plugin.console("&#FF5D00Ошибка выполнения тайтла: " + content);
        }
    }

    private boolean sendAdventureTitle(Player player, String rawText, boolean isSubtitle,
                                       int fadeIn, int stay, int fadeOut) {
        Object times = createTitleTimes(fadeIn, stay, fadeOut);
        if (times == null) return false;

        if (rawText.contains("\n")) {
            String[] lines = rawText.split("\n", 2);
            player.sendTitlePart(net.kyori.adventure.title.TitlePart.TITLE,
                    HexColors.translateToComponent(lines[0]));
            if (lines.length > 1) {
                player.sendTitlePart(net.kyori.adventure.title.TitlePart.SUBTITLE,
                        HexColors.translateToComponent(lines[1]));
            }
        } else {
            net.kyori.adventure.text.Component comp = HexColors.translateToComponent(rawText);
            if (isSubtitle) {
                player.sendTitlePart(net.kyori.adventure.title.TitlePart.SUBTITLE, comp);
            } else {
                player.sendTitlePart(net.kyori.adventure.title.TitlePart.TITLE, comp);
            }
        }
        player.sendTitlePart(net.kyori.adventure.title.TitlePart.TIMES,
                (net.kyori.adventure.title.Title.Times) times);
        return true;
    }

    private static Object createTitleTimes(int fadeInTicks, int stayTicks, int fadeOutTicks) {
        java.time.Duration in = java.time.Duration.ofMillis(Math.max(0, fadeInTicks) * 50L);
        java.time.Duration stay = java.time.Duration.ofMillis(Math.max(0, stayTicks) * 50L);
        java.time.Duration out = java.time.Duration.ofMillis(Math.max(0, fadeOutTicks) * 50L);
        Class<?> timesClass = net.kyori.adventure.title.Title.Times.class;

        try {
            return timesClass.getMethod("times",
                            java.time.Duration.class, java.time.Duration.class, java.time.Duration.class)
                    .invoke(null, in, stay, out);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }

        try {
            return timesClass.getMethod("of",
                            java.time.Duration.class, java.time.Duration.class, java.time.Duration.class)
                    .invoke(null, in, stay, out);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
        }

        return null;
    }

    private void sendLegacyTitle(Player player, String rawText, boolean isSubtitle,
                                 int fadeIn, int stay, int fadeOut) {
        String title = "";
        String subtitle = "";
        if (rawText.contains("\n")) {
            String[] lines = rawText.split("\n", 2);
            title = HexColors.translate(lines[0]);
            if (lines.length > 1) {
                subtitle = HexColors.translate(lines[1]);
            }
        } else if (isSubtitle) {
            subtitle = HexColors.translate(rawText);
        } else {
            title = HexColors.translate(rawText);
        }
        player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
    }

    private void executePotionEffect(Player player, String content) {
        if (player == null) return;
        String[] parts = content.split("\\s+");
        if (parts.length == 0) return;
        try {
            org.bukkit.potion.PotionEffectType type = resolvePotionEffectType(parts[0]);
            if (type == null) {
                plugin.console("&#FF5D00Ошибка применения эффекта: " + content);
                return;
            }
            int duration = parts.length > 1 ? Integer.parseInt(parts[1]) * 20 : 600;
            int amplifier = parts.length > 2 ? Integer.parseInt(parts[2]) - 1 : 0;
            player.addPotionEffect(new org.bukkit.potion.PotionEffect(type, duration, amplifier));
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка применения эффекта: " + content);
        }
    }

    private org.bukkit.potion.PotionEffectType resolvePotionEffectType(String rawName) {
        if (rawName == null || rawName.isEmpty()) return null;
        String name = rawName.trim();
        org.bukkit.potion.PotionEffectType type =
                org.bukkit.potion.PotionEffectType.getByName(name.toUpperCase(Locale.ROOT));
        if (type != null) return type;

        String key = name.toLowerCase(Locale.ROOT).replace(' ', '_');
        if (key.contains(":")) {
            key = key.substring(key.indexOf(':') + 1);
        }
        type = org.bukkit.potion.PotionEffectType.getByName(key.toUpperCase(Locale.ROOT));
        if (type != null) return type;

        try {
            Class<?> namespacedKeyClass = Class.forName("org.bukkit.NamespacedKey");
            Object namespacedKey = namespacedKeyClass
                    .getMethod("minecraft", String.class)
                    .invoke(null, key);
            java.lang.reflect.Method getByKey = org.bukkit.potion.PotionEffectType.class
                    .getMethod("getByKey", namespacedKeyClass);
            Object resolved = getByKey.invoke(null, namespacedKey);
            if (resolved instanceof org.bukkit.potion.PotionEffectType pet) return pet;
        } catch (Throwable ignored) {
        }
        return null;
    }

    private void executeTeleport(Player player, String content) {
        if (player == null) return;
        String[] parts = content.split("\\s+");
        if (parts.length < 4) return;
        try {
            double x = Double.parseDouble(parts[0]);
            double y = Double.parseDouble(parts[1]);
            double z = Double.parseDouble(parts[2]);
            org.bukkit.World world = plugin.getServer().getWorld(parts[3]);
            if (world != null) player.teleport(new org.bukkit.Location(world, x, y, z));
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка телепортации: " + content);
        }
    }

    private void executeGiveItem(Player player, String content) {
        if (player == null) return;
        String[] parts = content.split("\\s+");
        if (parts.length < 2) return;
        try {
            org.bukkit.Material material = org.bukkit.Material.valueOf(parts[0].toUpperCase(Locale.ROOT));
            int amount = Integer.parseInt(parts[1]);
            player.getInventory().addItem(new org.bukkit.inventory.ItemStack(material, amount));
        } catch (Exception e) {
            plugin.console("&#FF5D00Ошибка выдачи предмета: " + content);
        }
    }

    private int parseIntSafe(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
