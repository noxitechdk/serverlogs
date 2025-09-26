package dk.noxitech.serverlogs;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.bukkit.entity.Player;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class EventListener implements Listener {

    private final Main plugin;
    private final JDA jda;
    private final long channelId;
    private final LanguageManager lang;
    private final Map<String, Long> recentActions = new ConcurrentHashMap<>();
    private final long ACTION_COOLDOWN_MS = 2000;
    private final int BLOCK_REGION_SIZE = 25;
    private final long SPAM_WINDOW_MS = 5000;
    private final int SPAM_THRESHOLD = 4;
    private final long SPAM_MUTE_MS = 30000;
    private final Map<String, Integer> actionCounts = new ConcurrentHashMap<>();
    private final Map<String, Long> mutedUntil = new ConcurrentHashMap<>();

    private boolean shouldProcess(String key) {
        long now = System.currentTimeMillis();
        Long muted = mutedUntil.get(key);
        if (muted != null && now < muted) return false;

        Long last = recentActions.get(key);
        if (last == null || now - last > SPAM_WINDOW_MS) {
            actionCounts.put(key, 1);
        } else {
            int c = actionCounts.getOrDefault(key, 0) + 1;
            actionCounts.put(key, c);
            if (c > SPAM_THRESHOLD) {
                mutedUntil.put(key, now + SPAM_MUTE_MS);
                actionCounts.put(key, 0);
                recentActions.put(key, now);
                plugin.getLogger().info("Spam detected for key '" + key + "' - muting for " + SPAM_MUTE_MS + "ms");
                return false;
            }
        }
        Long lastShort = recentActions.get(key);
        if (lastShort != null && now - lastShort < ACTION_COOLDOWN_MS) return false;
        recentActions.put(key, now);
        return true;
    }

    public EventListener(Main plugin, JDA jda, long channelId, LanguageManager lang) {
        this.plugin = plugin;
        this.jda = jda;
        this.channelId = channelId;
        this.lang = lang;
    }

    private void post(String message) {
        try {
            TextChannel channel = jda.getChannelById(net.dv8tion.jda.api.entities.channel.concrete.TextChannel.class, channelId);
            if (channel != null) {
                channel.sendMessage(message).queue(success -> {
                }, throwable -> plugin.getLogger().warning("Failed to send message to Discord: " + throwable.getMessage()));
            } else {
                plugin.getLogger().warning("Discord channel not found: " + channelId);
            }
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            plugin.getLogger().warning("Discord requester stopped, message not sent: " + ex.getMessage());
        } catch (Exception ex) {
            plugin.getLogger().warning("Unexpected error sending Discord message: " + ex.getMessage());
        }
    }

    private String coords(Location loc) {
        return "(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")";
    }

    private String formatItemName(String enumName) {
        String[] parts = enumName.split("_");
        for (int i = 0; i < parts.length; i++) {
            parts[i] = parts[i].charAt(0) + parts[i].substring(1).toLowerCase();
        }
        return String.join(" ", parts);
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent e) {
        String player = e.getPlayer().getName();
        String cmd = e.getMessage();
        String key = "command:" + player + ":" + cmd;
        if (!shouldProcess(key)) return;
        post(lang.get("event.command", java.util.Map.of("player", player, "cmd", cmd)));
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        String victim = e.getEntity().getName();
        if (e.getEntity().getKiller() != null) {
            String killer = e.getEntity().getKiller().getName();
            post(lang.get("event.death_killer", java.util.Map.of("victim", victim, "killer", killer)));
        } else {
            post(lang.get("event.death", java.util.Map.of("victim", victim)));
        }
        try {
            org.bukkit.entity.Player p = e.getEntity();
            java.util.Map<String, Integer> counts = new java.util.HashMap<>();
            if (p.getInventory() != null) {
                ItemStack[] contents = p.getInventory().getContents();
                if (contents != null) {
                    for (ItemStack it : contents) {
                        if (it == null) continue;
                        if (it.getType() == org.bukkit.Material.AIR) continue;
                        counts.merge(it.getType().name(), it.getAmount(), Integer::sum);
                    }
                }
                ItemStack[] armor = p.getInventory().getArmorContents();
                if (armor != null) {
                    for (ItemStack it : armor) {
                        if (it == null) continue;
                        if (it.getType() == org.bukkit.Material.AIR) continue;
                        counts.merge(it.getType().name(), it.getAmount(), Integer::sum);
                    }
                }
            }
            if (!counts.isEmpty()) {
                java.util.List<java.util.Map<String, Object>> arr = new java.util.ArrayList<>();
                for (java.util.Map.Entry<String, Integer> en : counts.entrySet()) {
                    java.util.Map<String, Object> o = new java.util.HashMap<>();
                    o.put("id", en.getKey());
                    o.put("name", formatItemName(en.getKey()));
                    o.put("amount", en.getValue());
                    arr.add(o);
                }
                String json = new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(arr);
                post(lang.get("event.death_items_header", java.util.Map.of("player", victim)));
                post("```json\n" + json + "\n```");
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to aggregate death inventory: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent e) {
        String player = e.getPlayer().getName();
        ItemStack item = e.getItemDrop().getItemStack();
        String key = "drop:" + player + ":" + item.getType().name();
        if (!shouldProcess(key)) return;
        String itemDesc = item.getType().name();
        if (item.getAmount() > 1) itemDesc = item.getAmount() + " " + itemDesc;
        post(lang.get("event.drop", java.util.Map.of("player", player, "item", itemDesc)));
    }

    @EventHandler
    public void onPickup(PlayerPickupItemEvent e) {
        String player = e.getPlayer().getName();
        ItemStack item = e.getItem().getItemStack();
        String key = "pickup:" + player + ":" + item.getType().name();
        if (!shouldProcess(key)) return;
        String itemDesc = item.getType().name();
        if (item.getAmount() > 1) itemDesc = item.getAmount() + " " + itemDesc;
        post(lang.get("event.pickup", java.util.Map.of("player", player, "item", itemDesc)));
    }

    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        String player = e.getPlayer().getName();
        String msg = e.getMessage();
        String key = "chat:" + player + ":" + msg;
        if (!shouldProcess(key)) return;
        post(lang.get("event.chat", java.util.Map.of("player", player, "msg", msg)));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String player = e.getPlayer().getName();
        String key = "join:" + player;
        if (!shouldProcess(key)) return;
        post(lang.get("event.join", java.util.Map.of("player", player)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        String player = e.getPlayer().getName();
        String key = "quit:" + player;
        if (!shouldProcess(key)) return;
        post(lang.get("event.quit", java.util.Map.of("player", player)));
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        String player = e.getPlayer().getName();
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        String block = e.getBlock().getType().name();
    int rx = Math.floorDiv(e.getBlock().getLocation().getBlockX(), BLOCK_REGION_SIZE);
    int ry = Math.floorDiv(e.getBlock().getLocation().getBlockY(), BLOCK_REGION_SIZE);
    int rz = Math.floorDiv(e.getBlock().getLocation().getBlockZ(), BLOCK_REGION_SIZE);
    String region = rx + ":" + ry + ":" + rz;
    String key = "break:" + player + ":" + block + ":" + region;
    if (!shouldProcess(key)) return;
    String coords = coords(e.getBlock().getLocation());
    post(lang.get("event.block_break", java.util.Map.of("player", player, "block", block, "coords", coords)));
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        String player = e.getPlayer().getName();
        if (e.getPlayer().getGameMode() == GameMode.CREATIVE) return;
        String block = e.getBlock().getType().name();
    int rx = Math.floorDiv(e.getBlock().getLocation().getBlockX(), BLOCK_REGION_SIZE);
    int ry = Math.floorDiv(e.getBlock().getLocation().getBlockY(), BLOCK_REGION_SIZE);
    int rz = Math.floorDiv(e.getBlock().getLocation().getBlockZ(), BLOCK_REGION_SIZE);
    String region = rx + ":" + ry + ":" + rz;
    String key = "place:" + player + ":" + block + ":" + region;
    if (!shouldProcess(key)) return;
    String coords = coords(e.getBlock().getLocation());
    post(lang.get("event.block_place", java.util.Map.of("player", player, "block", block, "coords", coords)));
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof org.bukkit.entity.Player && e.getDamager() instanceof org.bukkit.entity.Player) {
            org.bukkit.entity.Player victim = (org.bukkit.entity.Player) e.getEntity();
            org.bukkit.entity.Player attacker = (org.bukkit.entity.Player) e.getDamager();
            String weapon = attacker.getInventory().getItemInMainHand().getType().name();
            String c = coords(victim.getLocation());
            String key = "damage:" + attacker.getName() + ":" + victim.getName() + ":" + weapon + ":" + c;
            if (!shouldProcess(key)) return;
            post(lang.get("event.damage", java.util.Map.of("attacker", attacker.getName(), "victim", victim.getName(), "weapon", weapon, "damage", String.valueOf(e.getFinalDamage()), "coords", c)));
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        String player = e.getPlayer().getName();
        String c = coords(e.getTo());
        String key = "teleport:" + player + ":" + c;
        if (!shouldProcess(key)) return;
        post(lang.get("event.teleport", java.util.Map.of("player", player, "coords", c)));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        String player = e.getPlayer().getName();
        String world = e.getPlayer().getWorld().getName();
        String key = "worldchange:" + player + ":" + world;
        if (!shouldProcess(key)) return;
        post(lang.get("event.world_change", java.util.Map.of("player", player, "world", world)));
    }

    @EventHandler
    public void onEnchant(EnchantItemEvent e) {
        if (e.getEnchanter() instanceof Player) {
            Player p = (Player) e.getEnchanter();
            ItemStack item = e.getItem();
            String key = "enchant:" + p.getName() + ":" + item.getType().name();
            if (!shouldProcess(key)) return;
            post(lang.get("event.enchant", java.util.Map.of("player", p.getName(), "item", item.getType().name())));
        }
    }

    @EventHandler
    public void onPortal(PlayerPortalEvent e) {
        String player = e.getPlayer().getName();
        String key = "portal:" + player;
        if (!shouldProcess(key)) return;
        post(lang.get("event.portal", java.util.Map.of("player", player, "coords", coords(e.getFrom()))));
    }

    @EventHandler
    public void onCraft(CraftItemEvent e) {
        if (e.getWhoClicked() instanceof Player) {
            Player p = (Player) e.getWhoClicked();
            ItemStack result = e.getRecipe() != null ? e.getRecipe().getResult() : null;
            String itemName = result != null ? result.getType().name() : "unknown";
            String key = "craft:" + p.getName() + ":" + itemName;
            if (!shouldProcess(key)) return;
            post(lang.get("event.craft", java.util.Map.of("player", p.getName(), "item", itemName)));
        }
    }
}