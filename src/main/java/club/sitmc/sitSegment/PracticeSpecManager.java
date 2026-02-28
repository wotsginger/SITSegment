package club.sitmc.sitSegment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class PracticeSpecManager {
    private static final String CFG_DISABLED_WORLDS = "disabled-worlds";
    private static final String CFG_SPEC_DISABLED_WORLDS = "spec-disabled-worlds";

    private final SITSegment plugin;
    private final MessageUtil messages;
    private final ItemUtil itemUtil;
    private final ParkourManager parkourManager;

    private final Map<UUID, Location> practiceLocations = new HashMap<>();
    private final Map<UUID, SpecState> spectatorStates = new HashMap<>();
    private final Set<String> disabledPracticeWorlds = new HashSet<>();
    private final Set<String> disabledSpecWorlds = new HashSet<>();

    private final NamespacedKey practiceLocationKey;
    private final NamespacedKey practiceFlightEnabledKey;
    private final NamespacedKey practicePrevAllowFlightKey;
    private final NamespacedKey practicePrevFlyingKey;
    private final NamespacedKey spectatorLocationKey;
    private final NamespacedKey spectatorGameModeKey;

    public PracticeSpecManager(SITSegment plugin, MessageUtil messages, ItemUtil itemUtil, ParkourManager parkourManager) {
        this.plugin = plugin;
        this.messages = messages;
        this.itemUtil = itemUtil;
        this.parkourManager = parkourManager;
        this.practiceLocationKey = new NamespacedKey(plugin, "practice_location");
        this.practiceFlightEnabledKey = new NamespacedKey(plugin, "practice_flight_enabled");
        this.practicePrevAllowFlightKey = new NamespacedKey(plugin, "practice_prev_allow_flight");
        this.practicePrevFlyingKey = new NamespacedKey(plugin, "practice_prev_flying");
        this.spectatorLocationKey = new NamespacedKey(plugin, "spec_location");
        this.spectatorGameModeKey = new NamespacedKey(plugin, "spec_gamemode");
    }

    public void loadConfig() {
        disabledPracticeWorlds.clear();
        disabledSpecWorlds.clear();
        normalizeToSet(plugin.getConfig().getStringList(CFG_DISABLED_WORLDS), disabledPracticeWorlds);
        normalizeToSet(plugin.getConfig().getStringList(CFG_SPEC_DISABLED_WORLDS), disabledSpecWorlds);
    }

    public boolean onNamedCommand(String commandName, CommandSender sender) {
        switch (commandName.toLowerCase()) {
            case "prac":
                handlePrac(sender);
                return true;
            case "unprac":
                handleUnprac(sender);
                return true;
            case "pracworld":
                handlePracWorld(sender);
                return true;
            case "spec":
                handleSpec(sender);
                return true;
            case "unspec":
                handleUnspec(sender);
                return true;
            case "specworld":
                handleSpecWorld(sender);
                return true;
            default:
                return false;
        }
    }

    public void handlePrac(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "&c该命令仅玩家可用。");
            return;
        }
        if (!player.hasPermission("sitsegment.command.prac")) {
            messages.send(sender, "&c你没有权限。");
            return;
        }
        if (!isPracticeWorldEnabled(player.getWorld().getName())) {
            messages.send(sender, "&c当前世界已禁用练习模式。");
            return;
        }
        if (!player.isOnGround()) {
            messages.send(sender, "&c必须站在地面上才能进入练习模式。");
            return;
        }
        if (isSpectating(player)) {
            messages.send(sender, "&c你正在旁观模式，请先使用 /unspec。");
            return;
        }
        if (isPracticing(player)) {
            itemUtil.givePracticeItems(player);
            applyPracticeFlightState(player);
            messages.send(sender, "&e你已经在练习模式。");
            return;
        }

        savePracticeLocation(player, player.getLocation().clone());
        saveOriginalFlightState(player);
        setPracticeFlightEnabled(player, false);
        applyPracticeFlightState(player);

        itemUtil.givePracticeItems(player);
        messages.send(sender, "&a已进入练习模式。");
    }

    public void handleUnprac(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "&c该命令仅玩家可用。");
            return;
        }
        if (!player.hasPermission("sitsegment.command.unprac")) {
            messages.send(sender, "&c你没有权限。");
            return;
        }

        Location saved = getPracticeLocation(player);
        if (saved == null) {
            messages.send(sender, "&c你当前不在练习模式。");
            return;
        }

        exitPractice(player, saved, true, "&a已退出练习模式。");
    }

    public void handlePracWorld(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "&c该命令仅玩家可用。");
            return;
        }
        if (!player.hasPermission("sitsegment.command.pracworld")) {
            messages.send(sender, "&c你没有权限。");
            return;
        }

        String worldName = player.getWorld().getName();
        if (containsIgnoreCase(disabledPracticeWorlds, worldName)) {
            removeIgnoreCase(disabledPracticeWorlds, worldName);
            messages.send(sender, "&a已启用当前世界练习模式: &f" + worldName);
        } else {
            disabledPracticeWorlds.add(worldName);
            messages.send(sender, "&e已禁用当前世界练习模式: &f" + worldName);
        }

        saveWorldToggles();
    }

    public void handleSpec(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "&c该命令仅玩家可用。");
            return;
        }
        if (!player.hasPermission("sitsegment.command.spec")) {
            messages.send(sender, "&c你没有权限。");
            return;
        }
        if (!isSpecWorldEnabled(player.getWorld().getName())) {
            messages.send(sender, "&c当前世界已禁用旁观模式。");
            return;
        }
        if (isPracticing(player)) {
            messages.send(sender, "&c你正在练习模式，请先使用 /unprac。");
            return;
        }
        if (parkourManager.getSession(player.getUniqueId()) != null) {
            messages.send(sender, "&c你正在进行跑酷，请先退出当前跑酷。");
            return;
        }
        if (isSpectating(player)) {
            messages.send(sender, "&e你已经在旁观模式。");
            return;
        }

        saveSpectatorState(player, player.getLocation().clone(), player.getGameMode());
        player.setGameMode(GameMode.SPECTATOR);
        messages.send(sender, "&a已进入旁观模式。");
    }

    public void handleUnspec(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "&c该命令仅玩家可用。");
            return;
        }
        if (!player.hasPermission("sitsegment.command.unspec")) {
            messages.send(sender, "&c你没有权限。");
            return;
        }

        SpecState state = getSpectatorState(player);
        if (state == null) {
            messages.send(sender, "&c你当前不在旁观模式。");
            return;
        }

        if (state.location != null) {
            player.teleport(state.location.clone());
        }
        if (state.previousGameMode != null) {
            player.setGameMode(state.previousGameMode);
        }
        clearSpectatorState(player);
        messages.send(sender, "&a已退出旁观模式。");
    }

    public void handleSpecWorld(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "&c该命令仅玩家可用。");
            return;
        }
        if (!player.hasPermission("sitsegment.command.specworld")) {
            messages.send(sender, "&c你没有权限。");
            return;
        }

        String worldName = player.getWorld().getName();
        if (containsIgnoreCase(disabledSpecWorlds, worldName)) {
            removeIgnoreCase(disabledSpecWorlds, worldName);
            messages.send(sender, "&a已启用当前世界旁观模式: &f" + worldName);
        } else {
            disabledSpecWorlds.add(worldName);
            messages.send(sender, "&e已禁用当前世界旁观模式: &f" + worldName);
        }

        saveWorldToggles();
    }

    public void onJoin(Player player) {
        if (player == null) {
            return;
        }
        if (getPracticeLocation(player) != null) {
            itemUtil.givePracticeItems(player);
            applyPracticeFlightState(player);
        }
        getSpectatorState(player);
    }

    public void onQuit(Player player) {
        if (player == null) {
            return;
        }
        practiceLocations.remove(player.getUniqueId());
        spectatorStates.remove(player.getUniqueId());
    }

    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String raw = event.getMessage();
        if (raw == null) {
            return;
        }

        String cmd = raw.trim().toLowerCase();
        if (isPracticing(player)) {
            if (cmd.startsWith("/unprac") || cmd.startsWith("/sitpk unprac")) {
                return;
            }
            event.setCancelled(true);
            messages.send(player, "&c练习模式下仅可使用 /unprac 退出。");
            return;
        }

        if (isSpectating(player)) {
            if (cmd.startsWith("/unspec") || cmd.startsWith("/sitpk unspec")) {
                return;
            }
            event.setCancelled(true);
            messages.send(player, "&c旁观模式下仅可使用 /unspec 退出。");
        }
    }

    public boolean handlePracticeItemInteract(Player player, ItemStack item) {
        if (player == null || item == null) {
            return false;
        }
        Location saved = getPracticeLocation(player);
        if (saved == null) {
            return false;
        }

        if (itemUtil.isPracticeReturnItem(item)) {
            player.teleport(saved.clone());
            messages.send(player, "&a已返回练习点。");
            return true;
        }

        if (itemUtil.isPracticeExitItem(item)) {
            exitPractice(player, saved, true, "&a已退出练习模式。");
            return true;
        }

        if (itemUtil.isPracticeFlightItem(item)) {
            boolean enabled = !isPracticeFlightEnabled(player);
            setPracticeFlightEnabled(player, enabled);
            applyPracticeFlightState(player);
            messages.send(player, enabled ? "&a练习飞行已开启。" : "&e练习飞行已关闭。");
            return true;
        }

        return false;
    }

    public boolean isPracticing(Player player) {
        return getPracticeLocation(player) != null;
    }

    public boolean isSpectating(Player player) {
        return getSpectatorState(player) != null;
    }

    private boolean isPracticeWorldEnabled(String worldName) {
        return !containsIgnoreCase(disabledPracticeWorlds, worldName);
    }

    private boolean isSpecWorldEnabled(String worldName) {
        return !containsIgnoreCase(disabledSpecWorlds, worldName);
    }

    private void saveWorldToggles() {
        plugin.getConfig().set(CFG_DISABLED_WORLDS, new ArrayList<>(disabledPracticeWorlds));
        plugin.getConfig().set(CFG_SPEC_DISABLED_WORLDS, new ArrayList<>(disabledSpecWorlds));
        plugin.saveConfig();
    }

    private void exitPractice(Player player, Location saved, boolean teleport, String message) {
        if (teleport && saved != null) {
            player.teleport(saved.clone());
        }
        restoreOriginalFlightState(player);
        clearPracticeLocation(player);
        clearPracticeFlightState(player);
        itemUtil.removePracticeItems(player);
        messages.send(player, message);
    }

    private void savePracticeLocation(Player player, Location location) {
        UUID uuid = player.getUniqueId();
        practiceLocations.put(uuid, location);
        player.getPersistentDataContainer().set(practiceLocationKey, PersistentDataType.STRING, serializeLocation(location));
    }

    private Location getPracticeLocation(Player player) {
        UUID uuid = player.getUniqueId();
        Location cached = practiceLocations.get(uuid);
        if (cached != null) {
            return cached;
        }

        String raw = player.getPersistentDataContainer().get(practiceLocationKey, PersistentDataType.STRING);
        if (raw == null) {
            return null;
        }

        Location loaded = deserializeLocation(raw);
        if (loaded != null) {
            practiceLocations.put(uuid, loaded);
        }
        return loaded;
    }

    private void clearPracticeLocation(Player player) {
        practiceLocations.remove(player.getUniqueId());
        player.getPersistentDataContainer().remove(practiceLocationKey);
    }

    private void saveOriginalFlightState(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        container.set(practicePrevAllowFlightKey, PersistentDataType.BYTE, toByte(player.getAllowFlight()));
        container.set(practicePrevFlyingKey, PersistentDataType.BYTE, toByte(player.isFlying()));
    }

    private void restoreOriginalFlightState(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        boolean previousAllow = fromByte(container.get(practicePrevAllowFlightKey, PersistentDataType.BYTE));
        boolean previousFlying = fromByte(container.get(practicePrevFlyingKey, PersistentDataType.BYTE));

        if (!isNaturalFlightMode(player)) {
            player.setAllowFlight(previousAllow);
            player.setFlying(previousAllow && previousFlying);
        }
    }

    private void setPracticeFlightEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(practiceFlightEnabledKey, PersistentDataType.BYTE, toByte(enabled));
    }

    private boolean isPracticeFlightEnabled(Player player) {
        Byte value = player.getPersistentDataContainer().get(practiceFlightEnabledKey, PersistentDataType.BYTE);
        return fromByte(value);
    }

    private void applyPracticeFlightState(Player player) {
        if (isNaturalFlightMode(player)) {
            return;
        }

        boolean enabled = isPracticeFlightEnabled(player);
        player.setAllowFlight(enabled);
        if (enabled) {
            player.setFlying(true);
        } else if (player.isFlying()) {
            player.setFlying(false);
        }
    }

    private void clearPracticeFlightState(Player player) {
        PersistentDataContainer container = player.getPersistentDataContainer();
        container.remove(practiceFlightEnabledKey);
        container.remove(practicePrevAllowFlightKey);
        container.remove(practicePrevFlyingKey);
    }

    private boolean isNaturalFlightMode(Player player) {
        GameMode mode = player.getGameMode();
        return mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR;
    }

    private byte toByte(boolean value) {
        return (byte) (value ? 1 : 0);
    }

    private boolean fromByte(Byte value) {
        return value != null && value == (byte) 1;
    }

    private void saveSpectatorState(Player player, Location location, GameMode previousMode) {
        UUID uuid = player.getUniqueId();
        SpecState state = new SpecState(location, previousMode);
        spectatorStates.put(uuid, state);

        PersistentDataContainer container = player.getPersistentDataContainer();
        container.set(spectatorLocationKey, PersistentDataType.STRING, serializeLocation(location));
        container.set(spectatorGameModeKey, PersistentDataType.STRING, previousMode == null ? "" : previousMode.name());
    }

    private SpecState getSpectatorState(Player player) {
        UUID uuid = player.getUniqueId();
        SpecState cached = spectatorStates.get(uuid);
        if (cached != null) {
            return cached;
        }

        PersistentDataContainer container = player.getPersistentDataContainer();
        String rawLocation = container.get(spectatorLocationKey, PersistentDataType.STRING);
        String rawGameMode = container.get(spectatorGameModeKey, PersistentDataType.STRING);
        if (rawLocation == null && rawGameMode == null) {
            return null;
        }

        Location location = rawLocation == null ? null : deserializeLocation(rawLocation);
        GameMode previousMode = parseGameMode(rawGameMode);
        SpecState loaded = new SpecState(location, previousMode);
        spectatorStates.put(uuid, loaded);
        return loaded;
    }

    private void clearSpectatorState(Player player) {
        spectatorStates.remove(player.getUniqueId());
        PersistentDataContainer container = player.getPersistentDataContainer();
        container.remove(spectatorLocationKey);
        container.remove(spectatorGameModeKey);
    }

    private String serializeLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return "";
        }
        return location.getWorld().getName() + ";" +
                location.getX() + ";" +
                location.getY() + ";" +
                location.getZ() + ";" +
                location.getYaw() + ";" +
                location.getPitch();
    }

    private Location deserializeLocation(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            String[] parts = raw.split(";");
            if (parts.length != 6) {
                return null;
            }
            return new Location(
                    Bukkit.getWorld(parts[0]),
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    Float.parseFloat(parts[4]),
                    Float.parseFloat(parts[5])
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private GameMode parseGameMode(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return GameMode.valueOf(raw);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void normalizeToSet(List<String> source, Set<String> target) {
        for (String worldName : source) {
            if (worldName == null || worldName.isBlank()) {
                continue;
            }
            if (!containsIgnoreCase(target, worldName)) {
                target.add(worldName);
            }
        }
    }

    private boolean containsIgnoreCase(Set<String> data, String value) {
        for (String item : data) {
            if (item.equalsIgnoreCase(value)) {
                return true;
            }
        }
        return false;
    }

    private void removeIgnoreCase(Set<String> data, String value) {
        data.removeIf(item -> item.equalsIgnoreCase(value));
    }

    private static final class SpecState {
        private final Location location;
        private final GameMode previousGameMode;

        private SpecState(Location location, GameMode previousGameMode) {
            this.location = location;
            this.previousGameMode = previousGameMode;
        }
    }
}
