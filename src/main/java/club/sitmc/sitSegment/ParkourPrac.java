package club.sitmc.sitSegment;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public final class ParkourPrac extends JavaPlugin implements Listener {

    private static final String PREFIX = "&3&lSIT-Parkour &8| &f";
    private static final String ITEM_TAG_SLIME = "slime_ball";
    private static final String ITEM_TAG_EMERALD = "emerald";

    private static final String PERMISSION_PRAC = "parkourprac.prac";
    private static final String PERMISSION_UNPRAC = "parkourprac.unprac";
    private static final String PERMISSION_WORLD = "parkourprac.world";
    private static final String PERMISSION_SPEC = "parkourprac.spec";
    private static final String PERMISSION_UNSPEC = "parkourprac.unspec";
    private static final String PERMISSION_SPEC_WORLD = "parkourprac.specworld";
    private static final String CFG_DISABLED_WORLDS = "disabled-worlds";
    private static final String CFG_SPEC_DISABLED_WORLDS = "spec-disabled-worlds";

    private final Map<UUID, Location> practiceLocations = new HashMap<>();
    private final Map<UUID, SpecState> spectatorStates = new HashMap<>();

    private NamespacedKey practiceItemKey;
    private NamespacedKey practiceLocationKey;
    private NamespacedKey spectatorLocationKey;
    private NamespacedKey spectatorGameModeKey;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        practiceItemKey = new NamespacedKey(this, "practice_item");
        practiceLocationKey = new NamespacedKey(this, "practice_location");
        spectatorLocationKey = new NamespacedKey(this, "spec_location");
        spectatorGameModeKey = new NamespacedKey(this, "spec_gamemode");

        getServer().getPluginManager().registerEvents(this, this);

        Objects.requireNonNull(getCommand("prac")).setExecutor(this);
        Objects.requireNonNull(getCommand("unprac")).setExecutor(this);
        Objects.requireNonNull(getCommand("pracworld")).setExecutor(this);
        Objects.requireNonNull(getCommand("spec")).setExecutor(this);
        Objects.requireNonNull(getCommand("unspec")).setExecutor(this);
        Objects.requireNonNull(getCommand("specworld")).setExecutor(this);
    }

    @Override
    public void onDisable() {
        practiceLocations.clear();
        spectatorStates.clear();
    }

    // =======================
    // 事件监听
    // =======================

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        practiceLocations.remove(e.getPlayer().getUniqueId());
        spectatorStates.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        if (getPracticeLocation(player) != null) {
            givePracticeItems(player);
        }
        getSpectatorState(player);
    }

    @EventHandler
    public void onCommandBlock(PlayerCommandPreprocessEvent e) {
        Player player = e.getPlayer();

        if (!isPracticing(player)) return;

        String msg = e.getMessage().toLowerCase();

        if (msg.startsWith("/unprac")) return;

        e.setCancelled(true);
        sendMessage(player, "练习模式下不可使用其他命令，使用/unprac退出练习模式。");
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {

        if (e.getHand() != EquipmentSlot.HAND) return;

        ItemStack item = e.getItem();
        if (item == null) return;

        String tag = getPracticeItemTag(item);
        if (tag == null) return;

        if (e.getAction() == Action.RIGHT_CLICK_AIR) {
            handlePracticeItemRightClickAir(e, tag);
            return;
        }

        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            handlePracticeItemRightClickBlock(e, tag);
        }
    }

    private void handlePracticeItemRightClickAir(PlayerInteractEvent e, String tag) {
        executePracticeItemAction(e, tag);
    }

    private void handlePracticeItemRightClickBlock(PlayerInteractEvent e, String tag) {
        executePracticeItemAction(e, tag);
    }

    private void executePracticeItemAction(PlayerInteractEvent e, String tag) {
        e.setCancelled(true);

        Player player = e.getPlayer();
        Location saved = getPracticeLocation(player);

        if (saved == null) {
            sendMessage(player, "你还没有记录练习位置。");
            return;
        }

        // 返回记录点
        if (ITEM_TAG_SLIME.equals(tag)) {
            player.teleport(saved);
            sendMessage(player, "已返回记录位置。");
            return;
        }

        // 退出练习
        if (ITEM_TAG_EMERALD.equals(tag)) {
            player.teleport(saved);
            clearPracticeLocation(player);

            removePracticeItems(player, ITEM_TAG_SLIME);
            removePracticeItems(player, ITEM_TAG_EMERALD);

            consumeItemInHand(player, e.getHand());

            sendMessage(player, "已退出练习模式。");
        }
    }

    // =======================
    // 命令处理
    // =======================

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        switch (command.getName().toLowerCase()) {

            case "prac":
                handlePrac(sender);
                return true;

            case "unprac":
                handleUnprac(sender);
                return true;

            case "pracworld":
                handleWorldToggle(sender);
                return true;

            case "spec":
                handleSpec(sender);
                return true;

            case "unspec":
                handleUnspec(sender);
                return true;

            case "specworld":
                handleSpecWorldToggle(sender);
                return true;
        }

        return false;
    }

    private void handlePrac(CommandSender sender) {

        if (!(sender instanceof Player player)) {
            sendMessage(sender, "仅玩家可用。");
            return;
        }

        if (!player.hasPermission(PERMISSION_PRAC)) {
            sendMessage(sender, "你没有权限。");
            return;
        }

        if (!isWorldEnabled(player.getWorld().getName())) {
            sendMessage(sender, "该世界已禁用练习模式。");
            return;
        }

        if (!player.isOnGround()) {
            sendMessage(player, "必须站在地面上才能进入练习模式。");
            return;
        }

        if (isSpectating(player)) {
            sendMessage(player, "你正在旁观者模式，请先使用/unspec退出。");
            return;
        }

        savePracticeLocation(player, player.getLocation().clone());
        givePracticeItems(player);

        sendMessage(player, "已进入练习模式。");
    }

    private void handleUnprac(CommandSender sender) {

        if (!(sender instanceof Player player)) {
            sendMessage(sender, "仅玩家可用。");
            return;
        }

        if (!player.hasPermission(PERMISSION_UNPRAC)) {
            sendMessage(sender, "你没有权限。");
            return;
        }

        Location saved = getPracticeLocation(player);

        if (saved == null) {
            sendMessage(player, "你未进入练习模式。");
            return;
        }

        player.teleport(saved);
        clearPracticeLocation(player);

        removePracticeItems(player, ITEM_TAG_SLIME);
        removePracticeItems(player, ITEM_TAG_EMERALD);

        sendMessage(player, "已退出练习模式。");
    }

    private void handleWorldToggle(CommandSender sender) {

        if (!sender.hasPermission(PERMISSION_WORLD)) {
            sendMessage(sender, "你没有权限。");
            return;
        }

        if (!(sender instanceof Player player)) {
            sendMessage(sender, "该命令仅玩家可用。");
            return;
        }

        String worldName = player.getWorld().getName();
        List<String> list = getNormalizedDisabledWorlds();

        if (containsIgnoreCase(list, worldName)) {
            list.removeIf(s -> s.equalsIgnoreCase(worldName));
            sendMessage(sender, "已启用世界: " + worldName);
        } else {
            list.add(worldName);
            sendMessage(sender, "已禁用世界: " + worldName);
        }

        getConfig().set(CFG_DISABLED_WORLDS, list);
        saveConfig();
    }

    // =======================
    // 旁观者模式
    // =======================

    private void handleSpec(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "仅玩家可用。");
            return;
        }

        if (!player.hasPermission(PERMISSION_SPEC)) {
            sendMessage(sender, "你没有权限。");
            return;
        }

        if (!isSpecWorldEnabled(player.getWorld().getName())) {
            sendMessage(sender, "该世界已禁用旁观者模式。");
            return;
        }

        if (isPracticing(player)) {
            sendMessage(sender, "你正在练习模式，请先使用/unprac退出。");
            return;
        }

        if (isSpectating(player)) {
            sendMessage(sender, "你已在旁观者模式。");
            return;
        }

        saveSpectatorState(player, player.getLocation().clone(), player.getGameMode());
        player.setGameMode(GameMode.SPECTATOR);
        sendMessage(sender, "已进入旁观者模式。");
    }

    private void handleUnspec(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sendMessage(sender, "仅玩家可用。");
            return;
        }

        if (!player.hasPermission(PERMISSION_UNSPEC)) {
            sendMessage(sender, "你没有权限。");
            return;
        }

        SpecState state = getSpectatorState(player);
        if (state == null) {
            sendMessage(sender, "你未进入旁观者模式。");
            return;
        }

        if (state.location != null) {
            player.teleport(state.location);
        }
        if (state.previousGameMode != null) {
            player.setGameMode(state.previousGameMode);
        }
        clearSpectatorState(player);

        sendMessage(sender, "已退出旁观者模式。");
    }

    private void handleSpecWorldToggle(CommandSender sender) {
        if (!sender.hasPermission(PERMISSION_SPEC_WORLD)) {
            sendMessage(sender, "你没有权限。");
            return;
        }

        if (!(sender instanceof Player player)) {
            sendMessage(sender, "该命令仅玩家可用。");
            return;
        }

        String worldName = player.getWorld().getName();
        List<String> list = getNormalizedSpecDisabledWorlds();

        if (containsIgnoreCase(list, worldName)) {
            list.removeIf(s -> s.equalsIgnoreCase(worldName));
            sendMessage(sender, "已启用旁观世界: " + worldName);
        } else {
            list.add(worldName);
            sendMessage(sender, "已禁用旁观世界: " + worldName);
        }

        getConfig().set(CFG_SPEC_DISABLED_WORLDS, list);
        saveConfig();
    }

    // =======================
    // 工具方法
    // =======================

    private boolean isWorldEnabled(String name) {
        return !containsIgnoreCase(
                getNormalizedDisabledWorlds(),
                name
        );
    }

    private List<String> getNormalizedDisabledWorlds() {
        List<String> source = getConfig().getStringList(CFG_DISABLED_WORLDS);
        List<String> normalized = new ArrayList<>();

        for (String worldName : source) {
            if (worldName == null || worldName.isBlank()) continue;
            if (!containsIgnoreCase(normalized, worldName)) {
                normalized.add(worldName);
            }
        }

        return normalized;
    }

    private boolean isSpecWorldEnabled(String name) {
        return !containsIgnoreCase(
                getNormalizedSpecDisabledWorlds(),
                name
        );
    }

    private List<String> getNormalizedSpecDisabledWorlds() {
        List<String> source = getConfig().getStringList(CFG_SPEC_DISABLED_WORLDS);
        List<String> normalized = new ArrayList<>();

        for (String worldName : source) {
            if (worldName == null || worldName.isBlank()) continue;
            if (!containsIgnoreCase(normalized, worldName)) {
                normalized.add(worldName);
            }
        }

        return normalized;
    }

    private boolean containsIgnoreCase(List<String> list, String s) {
        for (String str : list) {
            if (str.equalsIgnoreCase(s)) return true;
        }
        return false;
    }

    private boolean isPracticing(Player p) {
        return getPracticeLocation(p) != null;
    }

    private boolean isSpectating(Player p) {
        return getSpectatorState(p) != null;
    }

    private void savePracticeLocation(Player p, Location loc) {
        practiceLocations.put(p.getUniqueId(), loc);
        p.getPersistentDataContainer().set(
                practiceLocationKey,
                PersistentDataType.STRING,
                serializeLocation(loc)
        );
    }

    private Location getPracticeLocation(Player p) {
        UUID uuid = p.getUniqueId();

        if (practiceLocations.containsKey(uuid))
            return practiceLocations.get(uuid);

        String data = p.getPersistentDataContainer()
                .get(practiceLocationKey, PersistentDataType.STRING);

        if (data == null) return null;

        Location loc = deserializeLocation(data);
        if (loc != null)
            practiceLocations.put(uuid, loc);

        return loc;
    }

    private void clearPracticeLocation(Player p) {
        practiceLocations.remove(p.getUniqueId());
        p.getPersistentDataContainer().remove(practiceLocationKey);
    }

    private void saveSpectatorState(Player p, Location loc, GameMode previousMode) {
        spectatorStates.put(p.getUniqueId(), new SpecState(loc, previousMode));
        PersistentDataContainer container = p.getPersistentDataContainer();
        container.set(
                spectatorLocationKey,
                PersistentDataType.STRING,
                serializeLocation(loc)
        );
        container.set(
                spectatorGameModeKey,
                PersistentDataType.STRING,
                previousMode == null ? "" : previousMode.name()
        );
    }

    private SpecState getSpectatorState(Player p) {
        UUID uuid = p.getUniqueId();
        if (spectatorStates.containsKey(uuid)) {
            return spectatorStates.get(uuid);
        }

        PersistentDataContainer container = p.getPersistentDataContainer();
        String locData = container.get(spectatorLocationKey, PersistentDataType.STRING);
        String modeData = container.get(spectatorGameModeKey, PersistentDataType.STRING);

        if (locData == null && modeData == null) return null;

        Location loc = locData == null ? null : deserializeLocation(locData);
        GameMode mode = parseGameMode(modeData);
        SpecState state = new SpecState(loc, mode);
        spectatorStates.put(uuid, state);
        return state;
    }

    private GameMode parseGameMode(String name) {
        if (name == null || name.isBlank()) return null;
        try {
            return GameMode.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void clearSpectatorState(Player p) {
        spectatorStates.remove(p.getUniqueId());
        PersistentDataContainer container = p.getPersistentDataContainer();
        container.remove(spectatorLocationKey);
        container.remove(spectatorGameModeKey);
    }

    private String serializeLocation(Location l) {
        return l.getWorld().getName() + ";" +
                l.getX() + ";" +
                l.getY() + ";" +
                l.getZ() + ";" +
                l.getYaw() + ";" +
                l.getPitch();
    }

    private Location deserializeLocation(String s) {
        try {
            String[] p = s.split(";");
            return new Location(
                    Bukkit.getWorld(p[0]),
                    Double.parseDouble(p[1]),
                    Double.parseDouble(p[2]),
                    Double.parseDouble(p[3]),
                    Float.parseFloat(p[4]),
                    Float.parseFloat(p[5])
            );
        } catch (Exception e) {
            return null;
        }
    }

    private void givePracticeItems(Player p) {

        if (!hasPracticeItem(p, ITEM_TAG_SLIME))
            p.getInventory().addItem(
                    createItem(Material.SLIME_BALL,
                            "&a返回记录点",
                            ITEM_TAG_SLIME));

        if (!hasPracticeItem(p, ITEM_TAG_EMERALD))
            p.getInventory().addItem(
                    createItem(Material.EMERALD,
                            "&a退出练习",
                            ITEM_TAG_EMERALD));
    }

    private boolean hasPracticeItem(Player p, String tag) {
        for (ItemStack item : p.getInventory().getContents())
            if (isPracticeItem(item, tag))
                return true;
        return false;
    }

    private ItemStack createItem(Material m, String name, String tag) {
        ItemStack item = new ItemStack(m);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(color(name));
        meta.getPersistentDataContainer()
                .set(practiceItemKey,
                        PersistentDataType.STRING,
                        tag);

        item.setItemMeta(meta);
        return item;
    }

    private boolean isPracticeItem(ItemStack item, String tag) {
        if (item == null || !item.hasItemMeta()) return false;

        return tag.equals(
                item.getItemMeta()
                        .getPersistentDataContainer()
                        .get(practiceItemKey,
                                PersistentDataType.STRING)
        );
    }

    private String getPracticeItemTag(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta()
                .getPersistentDataContainer()
                .get(practiceItemKey,
                        PersistentDataType.STRING);
    }

    private void removePracticeItems(Player p, String tag) {
        PlayerInventory inv = p.getInventory();

        for (int i = 0; i < inv.getSize(); i++) {
            if (isPracticeItem(inv.getItem(i), tag))
                inv.setItem(i, null);
        }
    }

    private void consumeItemInHand(Player p, EquipmentSlot hand) {
        if (hand == null) return;

        ItemStack item = hand == EquipmentSlot.HAND ?
                p.getInventory().getItemInMainHand() :
                p.getInventory().getItemInOffHand();

        if (item == null) return;

        if (item.getAmount() <= 1)
            item = null;
        else
            item.setAmount(item.getAmount() - 1);

        if (hand == EquipmentSlot.HAND)
            p.getInventory().setItemInMainHand(item);
        else
            p.getInventory().setItemInOffHand(item);
    }

    private void sendMessage(CommandSender s, String msg) {
        s.sendMessage(color(PREFIX + msg));
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
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
