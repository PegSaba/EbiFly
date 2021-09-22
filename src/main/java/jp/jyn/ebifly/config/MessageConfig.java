package jp.jyn.ebifly.config;

import jp.jyn.ebifly.EbiFlyPlugin;
import jp.jyn.jbukkitlib.config.YamlLoader;
import jp.jyn.jbukkitlib.config.parser.component.ComponentParser;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;

import java.util.Objects;

public class MessageConfig {
    public final static String PREFIX = "[EbiFly] ";
    public final static String HEADER = "========== EbiFly ==========";
    public final static String PLAYER_ONLY = PREFIX + ChatColor.RED + "This command can only be run by players.";

    public final String locale;

    public final ComponentParser invalidNumber;
    public final ComponentParser playerNotFound;
    public final ComponentParser permissionError;

    public final HelpMessage help;

    public MessageConfig(EbiFlyPlugin plugin, String locale) {
        this.locale = locale;
        var config = loadConfig(plugin, locale);

        invalidNumber = p(config.getString("invalidNumber"));
        playerNotFound = p(config.getString("playerNotFound"));
        permissionError = p(config.getString("permissionError"));

        help = new HelpMessage(Objects.requireNonNull(config.getConfigurationSection("help")));
    }

    private FileConfiguration loadConfig(Plugin plugin, String locale) {
        var f = "locale/" + locale + ".yml";
        var l = plugin.getLogger();
        var c = YamlLoader.load(plugin, f);

        if (c.getInt("version", -1) != Objects.requireNonNull(c.getDefaults()).getInt("version")) {
            l.warning("Detected plugin downgrade!!");
            l.warning("Incompatible changes in " + f);
            l.warning("Please check " + f + ". If you need downgrade, use backup.");
        }
        return c;
    }

    private static ComponentParser p(String value) {
        return ComponentParser.parse(PREFIX + value);
    }

    public final static class HelpMessage {
        public final ComponentParser fly;
        public final ComponentParser version;
        public final ComponentParser reload;
        public final ComponentParser help;

        private HelpMessage(ConfigurationSection config) {
            fly = p("/fly [%s] [%s] - %s".formatted(
                config.getString("time", "time"),
                config.getString("player", "player"),
                config.getString("fly"))
            );
            version = p("/fly version - " + config.getString("version"));
            reload = p("/fly reload - " + config.getString("reload"));
            help = p("/fly help - " + config.getString("help"));
        }
    }
}
