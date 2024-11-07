package me.keano.azurite;

import me.keano.azurite.board.listener.BoardListener;
import me.keano.azurite.board.task.BoardTask;
import me.keano.azurite.board.utils.Cooldown;
import net.minecraft.server.v1_8_R3.ChatComponentText;
import net.minecraft.server.v1_8_R3.MinecraftServer;
import net.minecraft.server.v1_8_R3.PacketPlayOutChat;
import net.minecraft.server.v1_8_R3.PlayerConnection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Copyright (c) 2024. Keano
 * Use or redistribution of source or file is
 * only permitted if given explicit permission.
 */
public class StressTest extends JavaPlugin implements Listener {

    public static final Cooldown PING_JOIN_COOLDOWN = new Cooldown();

    private int previousOnline;
    private int i;
    private boolean profiling;
    public static long startTime = 0L;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getPluginManager().registerEvents(new BoardListener(), this);
        Executors.newScheduledThreadPool(1).scheduleAtFixedRate(new BoardTask(), 0L, 200L, TimeUnit.MILLISECONDS);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            String tpsColored = getTPSColored();
            int online = Bukkit.getOnlinePlayers().size();
            int maxOnline = Bukkit.getMaxPlayers();
            int chunks = 0;
            int entities = 0;

            for (World world : Bukkit.getWorlds()) {
                chunks += world.getLoadedChunks().length;
                entities += world.getEntities().size();
            }

            ChatComponentText text = new ChatComponentText(t(
                    "&5TPS: " + tpsColored + " &7┃ &5Players: &f" + online + "/" + maxOnline + " &7┃ &5Chunks: &f" + chunks + " &7┃ &5Entities: &f" + entities
            ));

            // This will ensure the profiler taken is accurate with same amount of ticks from first bot join
            if (profiling) {
                if (i % 8 == 0) {
                    // Every 2 second compare old online players to new online players
                    if (previousOnline == online) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spark profiler --stop");
                        profiling = false;
                    }

                    previousOnline = online;
                }
                i++;
            }

            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (!onlinePlayer.isOp()) continue;

                PlayerConnection connection = ((CraftPlayer) onlinePlayer).getHandle().playerConnection;
                if (connection != null) {
                    connection.sendPacket(new PacketPlayOutChat(text, (byte) 2));
                }
            }
        }, 0L, 5L);
    }

    private String t(String t) {
        return ChatColor.translateAlternateColorCodes('&', t);
    }

    private String getTPSColored() {
        double tps = MinecraftServer.getServer().recentTps[0];
        String color = (tps > 18 ? "§a" : tps > 16 ? "§e" : "§c");
        String asterisk = (tps > 20 ? "*" : "");
        return color + asterisk + Math.min(Math.round(tps * 100.0) / 100.0, 20.0);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!e.hasItem()) return;

        Player player = e.getPlayer();

        if (player.isOp()) {
            if (e.getItem().getType() == Material.STICK) {
                player.chat("/spigot:tps");
            }

            if (e.getItem().getType() == Material.BLAZE_ROD) {
                player.chat("/tps");
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.setJoinMessage(null);

        if (e.getPlayer().getName().equals("rowin_0")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "spark profiler --thread *");
            this.profiling = true;
            startTime = System.currentTimeMillis();
        }

        if (!e.getPlayer().hasPlayedBefore()) {
            e.getPlayer().teleport(e.getPlayer().getWorld().getSpawnLocation().clone().add(0.5, 0, 0.5));
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        e.setQuitMessage(null);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity().getName().contains("rowin")) {
            e.setCancelled(true);
        }
    }
}