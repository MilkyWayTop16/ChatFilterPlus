import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.gw.chatfilterplus.configs.ConfigUpdater;
import sun.misc.Unsafe;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/** Verifies the 2.2 -> 2.3 config upgrade actually delivers the new TLDs to an existing links.yml. */
public class MigrationCheck {

    static final String REPO = ".";
    static final String SCRATCH = "dev-tests";

    public static void main(String[] args) throws Exception {
        Field f = Unsafe.class.getDeclaredField("theUnsafe");
        f.setAccessible(true);
        Unsafe u = (Unsafe) f.get(null);

        File linksYml = new File(REPO + "/src/main/resources/links.yml");
        FileConfiguration defaults = YamlConfiguration.loadConfiguration(linksYml);

        // Симуляция конфига пользователя на 2.2: старый список TLD + свой кастомный домен/зона
        FileConfiguration user = YamlConfiguration.loadConfiguration(linksYml);
        user.set("config-version", "2.2");
        user.set("filter.smart-detection.tlds", new ArrayList<>(List.of("ru", "com", "net", "мойкастом")));
        user.set("filter.smart-detection.quick-triggers", new ArrayList<>(List.of("http", "www.")));
        user.set("filter.links.list-filter.domains", new ArrayList<>(List.of("https://t.me/myown")));

        ConfigUpdater updater = (ConfigUpdater) u.allocateInstance(ConfigUpdater.class);
        Method merge = ConfigUpdater.class.getDeclaredMethod(
                "mergeMissingKeys", FileConfiguration.class, FileConfiguration.class, String.class);
        merge.setAccessible(true);
        boolean changed = (boolean) merge.invoke(updater, user, defaults, "");

        List<String> tlds = user.getStringList("filter.smart-detection.tlds");
        List<String> triggers = user.getStringList("filter.smart-detection.quick-triggers");
        List<String> domains = user.getStringList("filter.links.list-filter.domains");

        StringBuilder rep = new StringBuilder();
        rep.append("MIGRATION CHECK 2.2 -> 2.3\n").append("=".repeat(60)).append("\n");
        rep.append("changed = ").append(changed).append("\n\n");

        String[] mustHave = {"su", "py", "pf", "top", "pro", "space", "website", "host", "icu",
                             "games", "world", "link", "click", "ru", "com", "net"};
        List<String> missing = new ArrayList<>();
        for (String t : mustHave) if (!tlds.contains(t)) missing.add(t);

        rep.append("TLD после миграции (").append(tlds.size()).append("): ").append(tlds).append("\n");
        rep.append(missing.isEmpty() ? "  OK: все новые зоны доехали\n"
                                     : "  FAIL: отсутствуют " + missing + "\n");
        rep.append("  кастомная зона пользователя сохранена: ")
           .append(tlds.contains("мойкастом") ? "OK" : "FAIL").append("\n\n");

        rep.append("quick-triggers (").append(triggers.size()).append("): ").append(triggers).append("\n");
        rep.append("  .su добавлен: ").append(triggers.contains(".su") ? "OK" : "FAIL").append("\n");
        rep.append("  пользовательские сохранены: ")
           .append(triggers.contains("http") && triggers.contains("www.") ? "OK" : "FAIL").append("\n\n");

        rep.append("list-filter.domains (пользовательский, НЕ должен мержиться): ").append(domains).append("\n");
        boolean userDomainsUntouched = domains.size() == 1 && domains.contains("https://t.me/myown");
        rep.append("  не тронут: ").append(userDomainsUntouched ? "OK" : "FAIL").append("\n");

        Files.write(Paths.get(SCRATCH + "/migration_report.txt"), rep.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println("changed=" + changed
                + " | missing_tlds=" + missing.size()
                + " | custom_kept=" + tlds.contains("мойкастом")
                + " | triggers_ok=" + (triggers.contains(".su") && triggers.contains("http"))
                + " | user_domains_untouched=" + userDomainsUntouched);
        System.out.println("report -> migration_report.txt");
    }
}
