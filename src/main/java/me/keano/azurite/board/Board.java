package me.keano.azurite.board;

import me.keano.azurite.StressTest;
import me.keano.azurite.board.fastboard.FastBoard;
import me.keano.azurite.board.utils.CC;
import org.apache.commons.lang.time.DurationFormatUtils;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Copyright (c) 2024. Keano
 * Use or redistribution of source or file is
 * only permitted if given explicit permission.
 */
public class Board {

    private static final long MINUTE = TimeUnit.MINUTES.toMillis(1L);
    private static final long HOUR = TimeUnit.HOURS.toMillis(1L);
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("dd MMMM yyyy");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("hh:mm:ss aa");
    private static final ThreadLocal<DecimalFormat> REMAINING_SECONDS_TRAILING = ThreadLocal.withInitial(() -> new DecimalFormat("0.0"));

    private final FastBoard fastBoard;

    public Board(Player player) {
        this.fastBoard = new FastBoard(player);
    }

    public void update() {
        int averagePing = 0;
        int total = 0;

        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            // Ignore the other players online as they could have ping spikes
            if (onlinePlayer.isOp() || StressTest.PING_JOIN_COOLDOWN.hasCooldown(onlinePlayer)) {
                continue;
            }

            averagePing += ((CraftPlayer) onlinePlayer).getHandle().ping;
            total++;
        }

        Date date = new Date();
        fastBoard.setTitle(CC.t("&5&lAzurite Stress Test"));
        fastBoard.setLines(CC.t(Arrays.asList(
                "&7&m-------------------------",
                "&fTime elapsed: &d" + getTimeElapsed(),
                "&fDate: &d" + DATE_FORMAT.format(date),
                "&fTime: &d" + TIME_FORMAT.format(date),
                "&fPlayers: &d" + (Bukkit.getOnlinePlayers().size() + "/" + Bukkit.getMaxPlayers()),
                "&fAverage bot ping: &d" + (total > 0 ? (averagePing / total) : 0) + "ms",
                "",
                "&7Purchase at azuritedev.net",
                "&7&m-------------------------"
        )));
    }

    private String getTimeElapsed() {
        if (StressTest.startTime == 0L) {
            return getRemaining(0L, false);
        }
        return getRemaining(System.currentTimeMillis() - StressTest.startTime, false);
    }

    public static String getRemaining(long duration, boolean milliseconds) {
        if (milliseconds && duration < MINUTE) {
            return REMAINING_SECONDS_TRAILING.get().format(duration * 0.001) + 's';
        } else {
            return (duration <= 0 ? "00:00" : DurationFormatUtils.formatDuration(duration, (duration >= HOUR ? "HH:" : "") + "mm:ss"));
        }
    }
}