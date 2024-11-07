package me.keano.azurite.board.utils;

import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Cooldown {

    private final Map<UUID, Long> cooldowns;

    public Cooldown() {
        this.cooldowns = new ConcurrentHashMap<>();
    }

    public void applyCooldown(Player player, int seconds) {
        cooldowns.put(player.getUniqueId(), System.currentTimeMillis() + (seconds * 1000L));
    }

    public boolean hasCooldown(Player player) {
        return cooldowns.containsKey(player.getUniqueId()) && (cooldowns.get(player.getUniqueId()) >= System.currentTimeMillis());
    }
}