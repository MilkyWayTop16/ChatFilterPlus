package org.gw.chatfilterplus.configs;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import org.gw.chatfilterplus.ChatFilterPlus;

import java.io.File;
import java.util.List;
import java.util.Set;

@Getter
public class LinksConfig {

    private static final String DEFAULT_REGEX =
            "(?i)(?:h\\s*t\\s*t\\s*p\\s*s?://\\S+|\\S*\\b(?:https?://)?[\\w\\p{L}]+(?:[\\.\\,\\s\\u200B\\u200C\\u200D\\u2060\\uFEFF]+[\\w\\p{L}]+)+[\\.\\,\\s]*(?:ru|com|net|org|io|me|info|biz|co|edu|gov|pro|fun|club|xyz|online|shop|site|tech|store|live|app|blog|world|space|work|game|dev|tv|cc|us|uk|ca|au|de|fr|jp|cn|link|digital|agency|news|media|cloud|page|wiki|art|team|systems|solutions|community|academy|center|group|tools|today|best|win|vip|bet|stream|chat|email|life|company|co\\.uk|co\\.jp|org\\.uk|gov\\.uk|ac\\.uk|edu\\.au|gov\\.au|bit\\.ly|t\\.co|tinyurl\\.com|goo\\.gl)[/\\S]*)";

    private final ChatFilterPlus plugin;
    private final File configFile;
    private FileConfiguration config;

    private volatile boolean filterEnabled;
    private volatile String filterMode;
    private volatile String filterReplacement;
    private volatile String linksRegex;
    private volatile boolean listFilterEnabled;
    private volatile String listFilterMode;
    private volatile List<String> listFilterDomains;
    private volatile Set<String> exceptionPlayers;
    private volatile Set<String> exceptionGroups;

    public LinksConfig(ChatFilterPlus plugin) {
        this.plugin = plugin;
        this.configFile = new File(plugin.getDataFolder(), "links.yml");
    }

    public void load() {
        config = ConfigUtils.loadWithUpdate(plugin, configFile, "links.yml");

        filterEnabled = config.getBoolean("filter.links.enabled", true);
        filterMode = config.getString("filter.links.mode", "block-and-notify").toLowerCase();
        filterReplacement = config.getString("filter.links.replacement", "&#FB8808[Ссылка удалена]&r");
        linksRegex = config.getString("filter.links.regex", DEFAULT_REGEX);
        listFilterEnabled = config.getBoolean("filter.links.list-filter.enabled", false);
        listFilterMode = config.getString("filter.links.list-filter.mode", "whitelist").toLowerCase();
        listFilterDomains = ConfigUtils.cleanStringList(config.getStringList("filter.links.list-filter.domains"));
        exceptionPlayers = ConfigUtils.cleanStringSet(config.getStringList("filter.links.exceptions.players"));
        exceptionGroups = ConfigUtils.cleanStringSet(config.getStringList("filter.links.exceptions.groups"));
    }
}
