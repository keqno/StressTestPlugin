package me.keano.azurite.board.task;

import me.keano.azurite.board.Board;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Copyright (c) 2023. Keano
 * Use or redistribution of source or file is
 * only permitted if given explicit permission.
 */
public class BoardTask implements Runnable {

    public static final Map<UUID, Board> PLAYER_BOARDS = new ConcurrentHashMap<>();

    @Override
    public void run() {
        try {

            for (Player player : Bukkit.getOnlinePlayers()) {
                Board board = PLAYER_BOARDS.get(player.getUniqueId());

                if (player.isOp() && board == null) {
                    board = new Board(player);
                    PLAYER_BOARDS.put(player.getUniqueId(), board);
                }

                // not sure if this would happen but just in case.
                if (board != null) {
                    board.update();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}