/*
 * This file is part of FastBoard, licensed under the MIT License.
 *
 * Copyright (c) 2019-2021 MrMicky
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package me.keano.azurite.board.fastboard;

import lombok.Getter;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Lightweight packet-based scoreboard API for Bukkit plugins.
 * It can be safely used asynchronously as everything is at packet level.
 * <p>
 * The project is on <a href="https://github.com/MrMicky-FR/FastBoard">GitHub</a>.
 *
 * @author MrMicky
 * @version 1.2.1
 */
@Getter
public class FastBoard {

    private static final Map<Class<?>, Field[]> PACKETS = new HashMap<>(8);
    private static final String[] COLOR_CODES = Arrays.stream(ChatColor.values())
            .map(Object::toString)
            .toArray(String[]::new);
    private static final VersionType VERSION_TYPE;
    // Packets and components
    private static final Class<?> CHAT_COMPONENT_CLASS;
    private static final Class<?> CHAT_FORMAT_ENUM;
    private static final Object EMPTY_MESSAGE;
    private static final Object RESET_FORMATTING;
    private static final MethodHandle MESSAGE_FROM_STRING;
    private static final MethodHandle PLAYER_CONNECTION;
    private static final MethodHandle SEND_PACKET;
    private static final MethodHandle PLAYER_GET_HANDLE;
    // Scoreboard packets
    private static final FastReflection.PacketConstructor PACKET_SB_OBJ;
    private static final FastReflection.PacketConstructor PACKET_SB_DISPLAY_OBJ;
    private static final FastReflection.PacketConstructor PACKET_SB_SCORE;
    private static final FastReflection.PacketConstructor PACKET_SB_TEAM;
    private static final FastReflection.PacketConstructor PACKET_SB_SERIALIZABLE_TEAM;
    // Scoreboard enums
    private static final Class<?> ENUM_SB_HEALTH_DISPLAY;
    private static final Class<?> ENUM_SB_ACTION;
    private static final Object ENUM_SB_HEALTH_DISPLAY_INTEGER;
    private static final Object ENUM_SB_ACTION_CHANGE;
    private static final Object ENUM_SB_ACTION_REMOVE;

    static {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();

            if (FastReflection.isRepackaged()) {
                VERSION_TYPE = VersionType.V1_17;
            } else if (FastReflection.nmsOptionalClass(null, "ScoreboardServer$Action").isPresent()) {
                VERSION_TYPE = VersionType.V1_13;
            } else if (FastReflection.nmsOptionalClass(null, "IScoreboardCriteria$EnumScoreboardHealthDisplay").isPresent()) {
                VERSION_TYPE = VersionType.V1_8;
            } else {
                VERSION_TYPE = VersionType.V1_7;
            }

            String gameProtocolPackage = "network.protocol.game";
            Class<?> craftPlayerClass = FastReflection.obcClass("entity.CraftPlayer");
            Class<?> craftChatMessageClass = FastReflection.obcClass("util.CraftChatMessage");
            Class<?> entityPlayerClass = FastReflection.nmsClass("server.level", "EntityPlayer");
            Class<?> playerConnectionClass = FastReflection.nmsClass("server.network", "PlayerConnection");
            Class<?> packetClass = FastReflection.nmsClass("network.protocol", "Packet");
            Class<?> packetSbObjClass = FastReflection.nmsClass(gameProtocolPackage, "PacketPlayOutScoreboardObjective");
            Class<?> packetSbDisplayObjClass = FastReflection.nmsClass(gameProtocolPackage, "PacketPlayOutScoreboardDisplayObjective");
            Class<?> packetSbScoreClass = FastReflection.nmsClass(gameProtocolPackage, "PacketPlayOutScoreboardScore");
            Class<?> packetSbTeamClass = FastReflection.nmsClass(gameProtocolPackage, "PacketPlayOutScoreboardTeam");
            Class<?> sbTeamClass = VersionType.V1_17.isHigherOrEqual()
                    ? FastReflection.innerClass(packetSbTeamClass, innerClass -> !innerClass.isEnum()) : null;
            Field playerConnectionField = Arrays.stream(entityPlayerClass.getFields())
                    .filter(field -> field.getType().isAssignableFrom(playerConnectionClass))
                    .findFirst().orElseThrow(NoSuchFieldException::new);
            Method sendPacketMethod = Arrays.stream(playerConnectionClass.getMethods())
                    .filter(m -> m.getParameterCount() == 1 && m.getParameterTypes()[0] == packetClass)
                    .findFirst().orElseThrow(NoSuchMethodException::new);

            MESSAGE_FROM_STRING = lookup.unreflect(craftChatMessageClass.getMethod("fromString", String.class));
            CHAT_COMPONENT_CLASS = FastReflection.nmsClass("network.chat", "IChatBaseComponent");
            CHAT_FORMAT_ENUM = FastReflection.nmsClass(null, "EnumChatFormat");
            EMPTY_MESSAGE = Array.get(MESSAGE_FROM_STRING.invoke(""), 0);
            RESET_FORMATTING = FastReflection.enumValueOf(CHAT_FORMAT_ENUM, "RESET", 21);
            PLAYER_GET_HANDLE = lookup.findVirtual(craftPlayerClass, "getHandle", MethodType.methodType(entityPlayerClass));
            PLAYER_CONNECTION = lookup.unreflectGetter(playerConnectionField);
            SEND_PACKET = lookup.unreflect(sendPacketMethod);
            PACKET_SB_OBJ = FastReflection.findPacketConstructor(packetSbObjClass, lookup);
            PACKET_SB_DISPLAY_OBJ = FastReflection.findPacketConstructor(packetSbDisplayObjClass, lookup);
            PACKET_SB_SCORE = FastReflection.findPacketConstructor(packetSbScoreClass, lookup);
            PACKET_SB_TEAM = FastReflection.findPacketConstructor(packetSbTeamClass, lookup);
            PACKET_SB_SERIALIZABLE_TEAM = sbTeamClass == null ? null : FastReflection.findPacketConstructor(sbTeamClass, lookup);

            for (Class<?> clazz : Arrays.asList(packetSbObjClass, packetSbDisplayObjClass, packetSbScoreClass, packetSbTeamClass, sbTeamClass)) {
                if (clazz == null) {
                    continue;
                }
                Field[] fields = Arrays.stream(clazz.getDeclaredFields())
                        .filter(field -> !Modifier.isStatic(field.getModifiers()))
                        .toArray(Field[]::new);
                for (Field field : fields) {
                    field.setAccessible(true);
                }
                PACKETS.put(clazz, fields);
            }

            if (VersionType.V1_8.isHigherOrEqual()) {
                String enumSbActionClass = VersionType.V1_13.isHigherOrEqual()
                        ? "ScoreboardServer$Action"
                        : "PacketPlayOutScoreboardScore$EnumScoreboardAction";
                ENUM_SB_HEALTH_DISPLAY = FastReflection.nmsClass("world.scores.criteria", "IScoreboardCriteria$EnumScoreboardHealthDisplay");
                ENUM_SB_ACTION = FastReflection.nmsClass("server", enumSbActionClass);
                ENUM_SB_HEALTH_DISPLAY_INTEGER = FastReflection.enumValueOf(ENUM_SB_HEALTH_DISPLAY, "INTEGER", 0);
                ENUM_SB_ACTION_CHANGE = FastReflection.enumValueOf(ENUM_SB_ACTION, "CHANGE", 0);
                ENUM_SB_ACTION_REMOVE = FastReflection.enumValueOf(ENUM_SB_ACTION, "REMOVE", 1);
            } else {
                ENUM_SB_HEALTH_DISPLAY = null;
                ENUM_SB_ACTION = null;
                ENUM_SB_HEALTH_DISPLAY_INTEGER = null;
                ENUM_SB_ACTION_CHANGE = null;
                ENUM_SB_ACTION_REMOVE = null;
            }
        } catch (Throwable t) {
            throw new ExceptionInInitializerError(t);
        }
    }

    protected Player player;
    protected String id;

    protected final List<String> lines = new ArrayList<>();
    protected final Map<Integer, String> scores = new HashMap<>();

    protected String title = ChatColor.RESET.toString();
    protected boolean deleted = false;

    /**
     * Creates a new FastBoard.
     *
     * @param player the owner of the scoreboard
     */
    public FastBoard(Player player) {
        if (player == null) return;

        this.player = Objects.requireNonNull(player, "player");
        this.id = "fb-" + Integer.toHexString(ThreadLocalRandom.current().nextInt());

        try {
            sendObjectivePacket(ObjectiveMode.CREATE);
            sendDisplayObjectivePacket();
        } catch (Throwable t) {
            throw new RuntimeException("Unable to create scoreboard", t);
        }
    }

    /**
     * Update the scoreboard title.
     *
     * @param title the new scoreboard title
     * @throws IllegalArgumentException if the title is longer than 32 chars on 1.12 or lower
     * @throws IllegalStateException    if {@link #delete()} was call before
     */
    public void setTitle(String title) {
        if (this.title.equals(Objects.requireNonNull(title, "title"))) {
            return;
        }

        if (!VersionType.V1_13.isHigherOrEqual() && title.length() > 32) {
            throw new IllegalArgumentException("Title is longer than 32 chars");
        }

        this.title = title;

        try {
            sendObjectivePacket(ObjectiveMode.UPDATE);
        } catch (Throwable t) {
            throw new RuntimeException("Unable to update scoreboard title", t);
        }
    }

    /**
     * Get the scoreboard lines.
     *
     * @return the scoreboard lines
     */
    public List<String> getLines() {
        return new ArrayList<>(this.lines);
    }

    /**
     * Update the lines of the scoreboard
     *
     * @param lines the new scoreboard lines
     * @throws IllegalArgumentException if one line is longer than 48 chars on 1.12 or lower
     * @throws IllegalStateException    if {@link #delete()} was call before
     */
    public synchronized void setLines(List<String> lines) {
        if (lines.size() >= 21) {
            lines = lines.subList(0, 21);
        }

        if (!VersionType.V1_13.isHigherOrEqual()) {
            int lineCount = 0;
            for (String s : lines) {
                if (s != null && s.length() > 48) {
                    throw new IllegalArgumentException("Line " + lineCount + " is longer than 48 chars");
                }
                lineCount++;
            }
        }

        List<String> oldLines = new ArrayList<>(this.lines);
        this.lines.clear();
        this.lines.addAll(lines);

        int linesSize = this.lines.size();

        try {
            if (oldLines.size() != linesSize) {
                List<String> oldLinesCopy = new ArrayList<>(oldLines);

                if (oldLines.size() > linesSize) {
                    for (int i = oldLinesCopy.size(); i > linesSize; i--) {
                        String score = this.scores.remove(i - 1);

                        if (score == null) {
                            score = COLOR_CODES[i - 1];
                        }

                        sendTeamPacket(i - 1);
                        sendScorePacket(ScoreboardAction.REMOVE, score, 0);

                        oldLines.remove(0);
                    }
                } else {
                    for (int i = oldLinesCopy.size(); i < linesSize; i++) {
                        sendScoreChange(i, null, null);
                        sendTeamPacket(i, TeamMode.CREATE, "", "");
                    }
                }
            }

            for (int i = 0; i < linesSize; i++) {
                if (!Objects.equals(getLineByScore(oldLines, i), getLineByScore(i))) {
                    sendLineChange(i);
                }
            }
        } catch (Throwable t) {
            throw new RuntimeException("Unable to update scoreboard lines", t);
        }
    }

    /**
     * Get the scoreboard size (the number of lines).
     *
     * @return the size
     */
    public int size() {
        return this.lines.size();
    }

    /**
     * Delete this FastBoard, and will remove the scoreboard for the associated player if he is online.
     * After this, all uses of {@link #setLines(List)} and {@link #setTitle(String)} will throw an {@link IllegalStateException}
     *
     * @throws IllegalStateException if this was already call before
     */
    public void delete() {
        try {
            for (int i = 0; i < this.lines.size(); i++) {
                sendTeamPacket(i);
            }

            sendObjectivePacket(ObjectiveMode.REMOVE);
        } catch (Throwable t) {
            throw new RuntimeException("Unable to delete scoreboard", t);
        }

        this.deleted = true;
    }

    /**
     * Return if the player has a prefix/suffix characters limit.
     * By default, it returns true only in 1.12 or lower.
     * This method can be overridden to fix compatibility with some versions support plugin.
     *
     * @return max length
     */
    public boolean hasLinesMaxLength() {
        return !FastBoard.VersionType.V1_13.isHigherOrEqual();
    }

    protected String getLineByScore(int score) {
        return getLineByScore(this.lines, score);
    }

    protected String getLineByScore(List<String> lines, int score) {
        return score < lines.size() ? lines.get(lines.size() - score - 1) : null;
    }

    private void sendObjectivePacket(ObjectiveMode mode) throws Throwable {
        Object packet = PACKET_SB_OBJ.invoke();

        setField(packet, String.class, this.id);
        setField(packet, int.class, mode.ordinal());

        if (mode != ObjectiveMode.REMOVE) {
            setComponentField(packet, this.title, 1);

            if (VersionType.V1_8.isHigherOrEqual()) {
                setField(packet, ENUM_SB_HEALTH_DISPLAY, ENUM_SB_HEALTH_DISPLAY_INTEGER);
            }
        } else if (VERSION_TYPE == VersionType.V1_7) {
            setField(packet, String.class, "", 1);
        }

        sendPacket(packet);
    }

    protected void sendDisplayObjectivePacket() throws Throwable {
        Object packet = PACKET_SB_DISPLAY_OBJ.invoke();

        setField(packet, int.class, 1); // Position (1: sidebar)
        setField(packet, String.class, this.id); // Score Name

        sendPacket(packet);
    }

    private void sendScorePacket(ScoreboardAction action, String name, int score)
            throws Throwable {
        Object packet = PACKET_SB_SCORE.invoke();

        Objects.requireNonNull(name, "name");

        setField(packet, String.class, name, 0); // Player Name

        if (VersionType.V1_8.isHigherOrEqual()) {
            Object enumAction = action == ScoreboardAction.REMOVE
                    ? ENUM_SB_ACTION_REMOVE : ENUM_SB_ACTION_CHANGE;
            setField(packet, ENUM_SB_ACTION, enumAction);
        } else {
            setField(packet, int.class, action.ordinal(), 1); // Action
        }

        if (action == ScoreboardAction.CHANGE) {
            setField(packet, String.class, this.id, 1); // Objective Name
            setField(packet, int.class, score); // Score
        }

        sendPacket(packet);
    }

    protected void sendTeamPacket(int score) throws Throwable {
        sendTeamPacket(score, TeamMode.REMOVE, null, null);
    }

    private void sendTeamPacket(int score, TeamMode mode, String prefix, String suffix)
            throws Throwable {
        if (mode == TeamMode.ADD_PLAYERS || mode == TeamMode.REMOVE_PLAYERS) {
            throw new UnsupportedOperationException();
        }

        Object packet = PACKET_SB_TEAM.invoke();

        setField(packet, String.class, this.id + ':' + score); // Team name
        setField(packet, int.class, mode.ordinal(), VERSION_TYPE == VersionType.V1_8 ? 1 : 0); // Update mode

        if (mode == TeamMode.CREATE || mode == TeamMode.UPDATE) {
            Objects.requireNonNull(prefix, "prefix");
            Objects.requireNonNull(suffix, "suffix");

            if (VersionType.V1_17.isHigherOrEqual()) {
                Object team = PACKET_SB_SERIALIZABLE_TEAM.invoke();
                // Since the packet is initialized with null values, we need to change more things.
                setComponentField(team, "", 0); // Display name
                setField(team, CHAT_FORMAT_ENUM, RESET_FORMATTING); // Color
                setComponentField(team, prefix, 1); // Prefix
                setComponentField(team, suffix, 2); // Suffix
                setField(team, String.class, "always", 0); // Visibility
                setField(team, String.class, "always", 1); // Collisions
                setField(packet, Optional.class, Optional.of(team));
            } else {
                setComponentField(packet, prefix, 2); // Prefix
                setComponentField(packet, suffix, 3); // Suffix
                setField(packet, String.class, "always", 4); // Visibility for 1.8+
                setField(packet, String.class, "always", 5); // Collisions for 1.9+
            }

            if (mode == TeamMode.CREATE) {
                String player = this.scores.get(score);

                if (player == null) {
                    player = COLOR_CODES[score];
                }

                setField(packet, Collection.class, Collections.singletonList(player)); // Players in the team
            }
        }

        sendPacket(packet);
    }

    private void sendLineChange(int score) throws Throwable {
        int maxLength = hasLinesMaxLength() ? 16 : 1024;
        String line = getLineByScore(score);
        String prefix;
        String name = null;
        String suffix = null;

        if (line == null || line.isEmpty()) {
            prefix = COLOR_CODES[score] + ChatColor.RESET;
        } else if (line.length() <= maxLength) {
            prefix = line;
        } else {
            int i = line.charAt(maxLength - 1) == ChatColor.COLOR_CHAR
                    ? (maxLength - 1) : maxLength;
            if (line.length() <= 28) {
                // Prevent splitting color codes
                prefix = line.substring(0, i);
                String suffixTmp = line.substring(i);
                ChatColor chatColor = null;

                if (suffixTmp.length() >= 2 && suffixTmp.charAt(0) == ChatColor.COLOR_CHAR) {
                    chatColor = ChatColor.getByChar(suffixTmp.charAt(1));
                }

                String color = ChatColor.getLastColors(prefix);
                boolean addColor = chatColor == null || chatColor.isFormat();

                suffix = (addColor ? (color.isEmpty() ? ChatColor.RESET.toString() : color) : "") + suffixTmp;
            } else {
                int suffixLength = Math.min(maxLength, line.length() - i);
                int index2 = line.charAt(line.length() - suffixLength - 1) == ChatColor.COLOR_CHAR
                        ? (line.length() - suffixLength - 1) : (line.length() - suffixLength);

                prefix = line.substring(0, i);
                name = line.substring(i, index2);
                suffix = line.substring(index2);
            }
        }

        if (prefix.length() > maxLength || (suffix != null && suffix.length() > maxLength)) {
            // Something went wrong, just cut to prevent client crash/kick
            prefix = prefix.substring(0, maxLength);
            suffix = (suffix != null) ? suffix.substring(0, maxLength) : null;
        }

        if (sendScoreChange(score, name, prefix)) {
            sendTeamPacket(score);
            sendTeamPacket(score, TeamMode.CREATE, prefix, suffix != null ? suffix : "");
        } else {
            sendTeamPacket(score, TeamMode.UPDATE, prefix, suffix != null ? suffix : "");
        }
    }

    private boolean sendScoreChange(int score, String value, String prefix) throws Throwable {
        String oldValue = this.scores.get(score);
        String newValue = value != null ? value : COLOR_CODES[score];

        if (Objects.equals(oldValue, newValue)) {
            return false;
        }

        if (this.scores.containsValue(newValue)) {
            String colors = COLOR_CODES[score] + ChatColor.getLastColors(prefix + value);
            newValue = newValue + colors;
        }

        if (Objects.equals(oldValue, newValue)) {
            return false;
        }

        this.scores.put(score, newValue);

        if (oldValue != null) {
            sendScorePacket(ScoreboardAction.REMOVE, oldValue, score);
        }

        sendScorePacket(ScoreboardAction.CHANGE, newValue, score);
        return true;
    }

    protected void sendPacket(Object packet) throws Throwable {
        if (this.deleted) {
            throw new IllegalStateException("This FastBoard is deleted");
        }

        if (this.player.isOnline()) {
            Object entityPlayer = PLAYER_GET_HANDLE.invoke(this.player);
            Object playerConnection = PLAYER_CONNECTION.invoke(entityPlayer);
            SEND_PACKET.invoke(playerConnection, packet);
        }
    }

    protected void setField(Object object, Class<?> fieldType, Object value) throws ReflectiveOperationException {
        setField(object, fieldType, value, 0);
    }

    protected void setField(Object packet, Class<?> fieldType, Object value, int count) throws ReflectiveOperationException {
        int i = 0;
        for (Field field : PACKETS.get(packet.getClass())) {
            if (field.getType() == fieldType && count == i++) {
                field.set(packet, value);
            }
        }
    }

    private void setComponentField(Object packet, String value, int count) throws Throwable {
        if (!VersionType.V1_13.isHigherOrEqual()) {
            setField(packet, String.class, value, count);
            return;
        }

        int i = 0;
        for (Field field : PACKETS.get(packet.getClass())) {
            if ((field.getType() == String.class || field.getType() == CHAT_COMPONENT_CLASS) && count == i++) {
                field.set(packet, value.isEmpty() ? EMPTY_MESSAGE : Array.get(MESSAGE_FROM_STRING.invoke(value), 0));
            }
        }
    }

    public enum ObjectiveMode {
        CREATE, REMOVE, UPDATE
    }

    public enum TeamMode {
        CREATE, REMOVE, UPDATE, ADD_PLAYERS, REMOVE_PLAYERS
    }

    public enum ScoreboardAction {
        CHANGE, REMOVE
    }

    public enum VersionType {
        V1_7, V1_8, V1_13, V1_17;

        public boolean isHigherOrEqual() {
            return VERSION_TYPE.ordinal() >= ordinal();
        }
    }
}