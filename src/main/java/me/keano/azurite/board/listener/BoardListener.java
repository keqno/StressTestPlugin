package me.keano.azurite.board.listener;

import me.keano.azurite.StressTest;
import me.keano.azurite.board.Board;
import me.keano.azurite.board.task.BoardTask;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Copyright (c) 2023. Keano
 * Use or redistribution of source or file is
 * only permitted if given explicit permission.
 */
public class BoardListener implements Listener {

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();

        if (player.isOp()) {
            BoardTask.PLAYER_BOARDS.put(player.getUniqueId(), new Board(player));
        }

        // Delay ping updates as on join ping isn't accurate
        StressTest.PING_JOIN_COOLDOWN.applyCooldown(player, 3);
    }

    @EventHandler
    public void onExplode(BlockExplodeEvent e) {
        e.blockList().clear();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player player = e.getPlayer();
        BoardTask.PLAYER_BOARDS.remove(player.getUniqueId());
    }
}