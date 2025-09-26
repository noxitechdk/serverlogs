package dk.noxitech.serverlogs;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import java.util.stream.Collectors;

public class LanguageManager {

    private final JavaPlugin plugin;
    private final String defaultLocale;
    private final Map<String, Map<String, String>> locales = new HashMap<>();
    private String currentLocale;

    public LanguageManager(JavaPlugin plugin, String defaultLocale) {
        this.plugin = plugin;
        this.defaultLocale = defaultLocale;
        this.currentLocale = defaultLocale;
        loadLocale(defaultLocale);
    }

    public void loadLocale(String locale) {
        File dataLocale = new File(plugin.getDataFolder(), "locale" + File.separator + locale + ".json");
        Gson g = new Gson();
        Type type = new TypeToken<Map<String, String>>() {}.getType();
        try {
            if (dataLocale.exists()) {
                try (Reader r = new InputStreamReader(new FileInputStream(dataLocale), StandardCharsets.UTF_8)) {
                    Map<String, String> map = g.fromJson(r, type);
                    if (map != null) {
                        locales.put(locale, map);
                        this.currentLocale = locale;
                        return;
                    }
                }
            }

            try (InputStream in = plugin.getResource("locale/" + locale + ".json")) {
                if (in != null) {
                    try (Reader r = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                        Map<String, String> map = g.fromJson(r, type);
                        if (map != null) {
                            locales.put(locale, map);
                            this.currentLocale = locale;
                        }
                    }
                } else {
                    plugin.getLogger().warning("Locale resource not found: " + locale + ".json");
                }
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load locale '" + locale + "': " + e.getMessage());
        }
    }

    public void exportDefaults(String... localeIds) {
        File localeDir = new File(plugin.getDataFolder(), "locale");
        if (!localeDir.exists()) localeDir.mkdirs();

        for (String locale : localeIds) {
            File out = new File(localeDir, locale + ".json");
            if (out.exists()) continue;
            try (InputStream in = plugin.getResource("locale/" + locale + ".json")) {
                if (in == null) continue;
                try (OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[8192];
                    int len;
                    while ((len = in.read(buf)) != -1) {
                        os.write(buf, 0, len);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to export locale " + locale + ": " + e.getMessage());
            }
        }
    }

    public String get(String locale, String key, Map<String, String> placeholders) {
        String val = null;
        Map<String, String> map = locales.get(locale);
        if (map != null) val = map.get(key);
        if (val == null) {
            Map<String, String> def = locales.get(defaultLocale);
            if (def != null) val = def.get(key);
        }
        if (val == null) return key;
        if (placeholders != null) {
            for (Map.Entry<String, String> e : placeholders.entrySet()) {
                val = val.replace("{" + e.getKey() + "}", e.getValue());
            }
        }
        return val;
    }

    public String get(String locale, String key) {
        return get(locale, key, null);
    }

    public String get(String key) {
        return get(this.currentLocale, key, null);
    }

    public String get(String key, Map<String, String> placeholders) {
        return get(this.currentLocale, key, placeholders);
    }
}
