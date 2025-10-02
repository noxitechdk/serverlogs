package dk.noxitech.serverlogs;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.command.CommandExecutor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.HandlerList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Main extends JavaPlugin {

    private JDA jda;
    private long channelId;
    private EventListener listener;
    private LanguageManager lang;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        String token = getConfig().getString("discord.token", "");
        channelId = getConfig().getLong("discord.channel-id", 0L);
        String locale = getConfig().getString("locale", "en");
    lang = new LanguageManager(this, "en");
    lang.exportDefaults("en", "da-dk");
    lang.loadLocale(locale);

        if (token.isEmpty() || channelId == 0L) {
            getLogger().severe("Discord token or channel id not set in config.yml. Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!startJda()) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        this.getCommand("serverlogs").setExecutor(new CommandExecutor() {
            @Override
            public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
                if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                    if (!sender.hasPermission("serverlogs.reload")) {
                        sender.sendMessage(lang.get("command.no_permission"));
                        return true;
                    }
                    reload(sender);
                    return true;
                }
                sender.sendMessage(lang.get("command.usage"));
                return true;
            }
        });
        this.getCommand("serverlogs").setTabCompleter((sender, command, alias, args) -> {
            if (args.length == 1) return Collections.singletonList("reload");
            return Collections.emptyList();
        });
    }

    private boolean startJda() {
        String cfgLocale = getConfig().getString("locale", "en");
        String locale = cfgLocale.equalsIgnoreCase("da") ? "da-dk" : cfgLocale;
        if (lang == null) lang = new LanguageManager(this, "en");
        lang.loadLocale(locale);

        String token = getConfig().getString("discord.token", "");
        long id = getConfig().getLong("discord.channel-id", 0L);
        try {
            jda = JDABuilder.createLight(token)
                    .disableCache(CacheFlag.VOICE_STATE, CacheFlag.EMOJI, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
                    .setActivity(Activity.watching(Objects.requireNonNull(getConfig().getString("discord.activity"))))
                    .setStatus(OnlineStatus.IDLE)
                    .build()
                    .awaitReady();
            getLogger().info(lang.get("console.discord_connected", java.util.Map.of("tag", jda.getSelfUser().getAsTag())));

            listener = new EventListener(this, jda, id, lang);
            getServer().getPluginManager().registerEvents(listener, this);
            return true;
        } catch (Exception e) {
            getLogger().severe(lang.get("console.discord_failed", java.util.Map.of("error", e.getMessage())));
            return false;
        }
    }

    private void stopJda() {
        if (jda != null) {
            if (listener != null) {
                try {
                    HandlerList.unregisterAll(listener);
                } catch (Exception ignored) {}
                listener = null;
            }
            try {
                jda.shutdown();
                if (!jda.awaitShutdown(java.time.Duration.ofSeconds(2))) {
                    jda.shutdownNow();
                }
            } catch (Exception e) {
                getLogger().warning("JDA shutdown error (this is normal during server shutdown): " + e.getMessage());
            } finally {
                jda = null;
            }
        }
    }

    private void reload(CommandSender sender) {
        sender.sendMessage(lang.get("command.reloading"));
        reloadConfig();
        stopJda();
        if (startJda()) {
            sender.sendMessage(lang.get("command.reload_success"));
        } else {
            sender.sendMessage(lang.get("command.reload_fail"));
        }
    }

    @Override
    public void onDisable() {
        try {
            stopJda();
        } catch (Exception e) {
            getLogger().info("Plugin disabled (shutdown errors are normal)");
        }
    }
}