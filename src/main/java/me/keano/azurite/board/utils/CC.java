package me.keano.azurite.board.utils;

import org.bukkit.ChatColor;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Copyright (c) 2024. Keano
 * Use or redistribution of source or file is
 * only permitted if given explicit permission.
 */
public class CC {

    private static final Function<String, String> REPLACER;

    static {
        REPLACER = s -> ChatColor.translateAlternateColorCodes('&', s);
    }

    public static String t(String t) {
        return REPLACER.apply(t);
    }

    public static List<String> t(List<String> t) {
        return t.stream().map(REPLACER).collect(Collectors.toList());
    }
}