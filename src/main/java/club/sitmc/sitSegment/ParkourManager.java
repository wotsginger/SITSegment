package club.sitmc.sitSegment;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

public class ParkourManager {
    private final SITSegment plugin;
    private final MessageUtil messages;
    private final ItemUtil itemUtil;
    private final HologramManager hologramManager;
    private final Map<String, WorldData> worldDataMap = new HashMap<>();
    private final Map<String, Map<UUID, RecordEntry>> records = new HashMap<>();
    private final Map<String, Map<UUID, SavedSession>> savedSessions = new HashMap<>();
    private final Set<String> segmentWorlds = new HashSet<>();
    private final Set<String> onlySprintWorlds = new HashSet<>();
    private final Map<UUID, RunSession> sessions = new HashMap<>();
    private BukkitTask tickTask;

    public ParkourManager(SITSegment plugin) {
        this.plugin = plugin;
        String prefix = plugin.getConfig().getString("prefix", "&3&lSIT-Parkour &8| &f");
        this.messages = new MessageUtil(prefix);
        this.itemUtil = new ItemUtil(plugin);
        this.hologramManager = new HologramManager();
    }

    public void load() {
        worldDataMap.clear();
        records.clear();
        savedSessions.clear();
        segmentWorlds.clear();
        onlySprintWorlds.clear();
        hologramManager.clearStoredHolograms();

        FileConfiguration config = plugin.getConfig();
        segmentWorlds.addAll(config.getStringList("worlds.segment"));
        onlySprintWorlds.addAll(config.getStringList("worlds.onlysprint"));

        ConfigurationSection pointsSection = config.getConfigurationSection("points");
        if (pointsSection != null) {
            for (String worldName : pointsSection.getKeys(false)) {
                WorldData data = new WorldData();
                Location start = readLocation(config, "points." + worldName + ".start");
                if (start != null) {
                    data.setStart(start);
                }
                Location end = readLocation(config, "points." + worldName + ".end");
                if (end != null) {
                    data.setEnd(end);
                }
                ConfigurationSection checkpoints = config.getConfigurationSection("points." + worldName + ".checkpoints");
                if (checkpoints != null) {
                    for (String key : checkpoints.getKeys(false)) {
                        int index;
                        try {
                            index = Integer.parseInt(key);
                        } catch (NumberFormatException ex) {
                            continue;
                        }
                        Location checkpoint = readLocation(config, "points." + worldName + ".checkpoints." + key);
                        if (checkpoint != null) {
                            data.setCheckpoint(index, checkpoint);
                        }
                    }
                }
                if (!data.isEmpty()) {
                    worldDataMap.put(worldName, data);
                }
            }
        }

        ConfigurationSection recordsSection = config.getConfigurationSection("records");
        if (recordsSection != null) {
            for (String worldName : recordsSection.getKeys(false)) {
                ConfigurationSection worldSection = recordsSection.getConfigurationSection(worldName);
                if (worldSection == null) {
                    continue;
                }
                Map<UUID, RecordEntry> worldRecords = new HashMap<>();
                for (String key : worldSection.getKeys(false)) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(key);
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    long time = worldSection.getLong(key + ".time", -1L);
                    if (time <= 0L) {
                        continue;
                    }
                    String name = worldSection.getString(key + ".name", "未知");
                    worldRecords.put(uuid, new RecordEntry(uuid, name, time));
                }
                if (!worldRecords.isEmpty()) {
                    records.put(worldName, worldRecords);
                }
            }
        }

        ConfigurationSection sessionsSection = config.getConfigurationSection("sessions");
        if (sessionsSection != null) {
            for (String worldName : sessionsSection.getKeys(false)) {
                ConfigurationSection worldSection = sessionsSection.getConfigurationSection(worldName);
                if (worldSection == null) {
                    continue;
                }
                Map<UUID, SavedSession> worldSessions = new HashMap<>();
                for (String key : worldSection.getKeys(false)) {
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(key);
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    long elapsed = worldSection.getLong(key + ".elapsed", -1L);
                    if (elapsed < 0L) {
                        continue;
                    }
                    int lastIndex = worldSection.getInt(key + ".lastIndex", 0);
                    Location lastLocation = readLocation(config, "sessions." + worldName + "." + key + ".lastLocation");
                    if (lastLocation == null) {
                        continue;
                    }
                    worldSessions.put(uuid, new SavedSession(uuid, worldName, elapsed, lastIndex, lastLocation));
                }
                if (!worldSessions.isEmpty()) {
                    savedSessions.put(worldName, worldSessions);
                }
            }
        }

        ConfigurationSection hologramsSection = config.getConfigurationSection("holograms");
        if (hologramsSection != null) {
            for (String worldName : hologramsSection.getKeys(false)) {
                ConfigurationSection worldSection = hologramsSection.getConfigurationSection(worldName);
                if (worldSection == null) {
                    continue;
                }
                for (String key : worldSection.getKeys(false)) {
                    String raw = worldSection.getString(key);
                    if (raw == null || raw.isEmpty()) {
                        continue;
                    }
                    UUID uuid;
                    try {
                        uuid = UUID.fromString(raw);
                    } catch (IllegalArgumentException ex) {
                        continue;
                    }
                    hologramManager.storeHologram(worldName, key, uuid);
                }
            }
        }

        for (World world : Bukkit.getWorlds()) {
            bindWorldData(world);
        }
    }

    public void save() {
        FileConfiguration config = plugin.getConfig();
        config.set("worlds.segment", new ArrayList<>(segmentWorlds));
        config.set("worlds.onlysprint", new ArrayList<>(onlySprintWorlds));
        config.set("points", null);
        config.set("records", null);
        config.set("sessions", null);
        config.set("holograms", null);

        for (Map.Entry<String, WorldData> entry : worldDataMap.entrySet()) {
            String worldName = entry.getKey();
            WorldData data = entry.getValue();
            String basePath = "points." + worldName;
            if (data.getStart() != null) {
                config.set(basePath + ".start", data.getStart());
            }
            if (data.getEnd() != null) {
                config.set(basePath + ".end", data.getEnd());
            }
            if (!data.getCheckpoints().isEmpty()) {
                for (Map.Entry<Integer, Location> checkpoint : data.getCheckpoints().entrySet()) {
                    config.set(basePath + ".checkpoints." + checkpoint.getKey(), checkpoint.getValue());
                }
            }
        }
        for (Map.Entry<String, Map<UUID, RecordEntry>> entry : records.entrySet()) {
            String worldName = entry.getKey();
            String basePath = "records." + worldName;
            for (RecordEntry recordEntry : entry.getValue().values()) {
                String recordPath = basePath + "." + recordEntry.getPlayerId();
                config.set(recordPath + ".name", recordEntry.getName());
                config.set(recordPath + ".time", recordEntry.getTimeMs());
            }
        }
        for (Map.Entry<String, Map<UUID, SavedSession>> entry : savedSessions.entrySet()) {
            String worldName = entry.getKey();
            String basePath = "sessions." + worldName;
            for (SavedSession savedSession : entry.getValue().values()) {
                String sessionPath = basePath + "." + savedSession.getPlayerId();
                config.set(sessionPath + ".elapsed", savedSession.getElapsedMs());
                config.set(sessionPath + ".lastIndex", savedSession.getLastCheckpointIndex());
                config.set(sessionPath + ".lastLocation", savedSession.getLastCheckpointLocation());
            }
        }
        for (Map.Entry<String, Map<String, UUID>> entry : hologramManager.getStoredHolograms().entrySet()) {
            String worldName = entry.getKey();
            String basePath = "holograms." + worldName;
            for (Map.Entry<String, UUID> hologramEntry : entry.getValue().entrySet()) {
                config.set(basePath + "." + hologramEntry.getKey(), hologramEntry.getValue().toString());
            }
        }
        plugin.saveConfig();
    }

    public void startTasks() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tick();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    public void shutdown() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        for (Map.Entry<UUID, RunSession> entry : sessions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) {
                saveSession(entry.getKey(), entry.getValue());
                messages.clearActionBar(player);
            }
        }
        sessions.clear();
    }

    public MessageUtil getMessages() {
        return messages;
    }

    public SITSegment getPlugin() {
        return plugin;
    }

    public ItemUtil getItemUtil() {
        return itemUtil;
    }

    public WorldData getWorldData(World world) {
        return world == null ? null : worldDataMap.get(world.getName());
    }

    public void bindWorldData(World world) {
        if (world == null) {
            return;
        }
        WorldData data = worldDataMap.get(world.getName());
        if (data != null) {
            data.bindWorld(world);
        }
        Map<UUID, SavedSession> worldSessions = savedSessions.get(world.getName());
        if (worldSessions != null) {
            for (SavedSession savedSession : worldSessions.values()) {
                savedSession.bindWorld(world);
            }
        }
    }

    public WorldMode getWorldMode(World world) {
        if (world == null) {
            return WorldMode.NONE;
        }
        String name = world.getName();
        if (onlySprintWorlds.contains(name)) {
            return WorldMode.ONLY_SPRINT;
        }
        if (segmentWorlds.contains(name)) {
            return WorldMode.SEGMENT;
        }
        return WorldMode.NONE;
    }

    public void setWorldMode(World world, WorldMode mode) {
        if (world == null) {
            return;
        }
        String name = world.getName();
        segmentWorlds.remove(name);
        onlySprintWorlds.remove(name);
        if (mode == WorldMode.SEGMENT) {
            segmentWorlds.add(name);
        } else if (mode == WorldMode.ONLY_SPRINT) {
            onlySprintWorlds.add(name);
        }
        save();
    }

    public RunSession getSession(UUID playerId) {
        return sessions.get(playerId);
    }

    public List<RecordEntry> getTopRecords(World world, int limit) {
        if (world == null || limit <= 0) {
            return List.of();
        }
        Map<UUID, RecordEntry> worldRecords = records.get(world.getName());
        if (worldRecords == null || worldRecords.isEmpty()) {
            return List.of();
        }
        List<RecordEntry> sorted = new ArrayList<>(worldRecords.values());
        sorted.sort(Comparator.comparingLong(RecordEntry::getTimeMs));
        if (sorted.size() <= limit) {
            return sorted;
        }
        return new ArrayList<>(sorted.subList(0, limit));
    }

    public void giveDefaultItems(Player player) {
        if (player == null) {
            return;
        }
        if (getWorldMode(player.getWorld()) == WorldMode.NONE) {
            return;
        }
        itemUtil.giveReturnItem(player);
        itemUtil.giveExitItem(player);
        itemUtil.giveRestartItem(player);
    }

    public void saveAndRemoveSession(Player player) {
        if (player == null) {
            return;
        }
        RunSession session = sessions.remove(player.getUniqueId());
        if (session != null && session.isStarted()) {
            saveSession(player.getUniqueId(), session);
        }
        messages.clearActionBar(player);
    }

    public void clearSession(Player player) {
        if (player == null) {
            return;
        }
        sessions.remove(player.getUniqueId());
        messages.clearActionBar(player);
    }

    public void tryRestoreSession(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        if (sessions.containsKey(playerId)) {
            return;
        }
        World world = player.getWorld();
        WorldMode mode = getWorldMode(world);
        if (mode == WorldMode.NONE) {
            return;
        }
        Map<UUID, SavedSession> worldSessions = savedSessions.get(world.getName());
        if (worldSessions == null) {
            return;
        }
        SavedSession savedSession = worldSessions.get(playerId);
        if (savedSession == null) {
            return;
        }
        WorldData data = worldDataMap.get(world.getName());
        if (data == null) {
            return;
        }
        Location lastLocation = savedSession.getLastCheckpointLocation();
        if (lastLocation == null) {
            return;
        }
        RunSession session = new RunSession(playerId);
        long startTimeMs = System.currentTimeMillis() - savedSession.getElapsedMs();
        session.restore(world.getName(), startTimeMs, savedSession.getLastCheckpointIndex(), lastLocation, data);
        sessions.put(playerId, session);
        messages.send(player, "&a已恢复你的跑酷进度。");
        giveDefaultItems(player);
    }

    public void restoreOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            tryRestoreSession(player);
            giveDefaultItems(player);
        }
    }

    public void startRun(Player player, WorldData data) {
        if (player == null || data == null || data.getStart() == null) {
            return;
        }
        clearSavedSession(player.getUniqueId(), player.getWorld().getName());
        RunSession session = new RunSession(player.getUniqueId());
        session.start(player.getWorld().getName(), data.getStart(), data);
        sessions.put(player.getUniqueId(), session);
        messages.send(player, "&a计时开始。");
        giveDefaultItems(player);
    }

    public void reachCheckpoint(Player player, RunSession session, WorldData data, int index) {
        if (session == null || data == null) {
            return;
        }
        Location checkpoint = data.getCheckpoint(index);
        session.reachCheckpoint(index, checkpoint, data);
        messages.send(player, "&a已到达记录点 &f" + index);
        giveDefaultItems(player);
    }

    public void tryFinish(Player player, RunSession session) {
        if (session.getNextCheckpointIndex() != null) {
            messages.send(player, "&c你还没有经过所有记录点。");
            return;
        }
        long elapsed = System.currentTimeMillis() - session.getStartTimeMs();
        boolean newRecord = updateRecord(player, elapsed);
        String suffix = newRecord ? " &e(新纪录)" : "";
        messages.send(player, "&a完成！用时: &f" + formatDuration(elapsed) + suffix);
        messages.clearActionBar(player);
        sessions.remove(player.getUniqueId());
        clearSavedSession(player.getUniqueId(), player.getWorld().getName());
    }

    public void handleReturnItem(Player player) {
        RunSession session = sessions.get(player.getUniqueId());
        if (session == null || !session.isStarted()) {
            messages.send(player, "&c当前没有进行中的跑酷。");
            return;
        }
        teleportToCheckpoint(player, session, "&a已返回上一个记录点。");
    }

    public void handleRestartItem(Player player) {
        if (player == null) {
            return;
        }
        World world = player.getWorld();
        WorldMode mode = getWorldMode(world);
        if (mode == WorldMode.NONE) {
            messages.send(player, "&c当前世界未启用跑酷模式。");
            return;
        }
        WorldData data = worldDataMap.get(world.getName());
        if (data == null || data.getStart() == null) {
            messages.send(player, "&c当前世界未设置起点。");
            return;
        }
        sessions.remove(player.getUniqueId());
        clearSavedSession(player.getUniqueId(), world.getName());
        Location start = data.getStart();
        player.teleport(start.clone());
        RunSession session = new RunSession(player.getUniqueId());
        session.start(world.getName(), start, data);
        sessions.put(player.getUniqueId(), session);
        messages.send(player, "&a已重新开始跑酷。");
        giveDefaultItems(player);
    }
    public void handleExitParkour(Player player) {
        if (player == null) {
            return;
        }
        World world = player.getWorld();
        sessions.remove(player.getUniqueId());
        clearSavedSession(player.getUniqueId(), world.getName());
        messages.clearActionBar(player);
        player.teleport(world.getSpawnLocation());
        messages.send(player, "&a已退出当前跑酷。");
        giveDefaultItems(player);
    }

    public void setStart(World world, Location location) {
        if (world == null || location == null) {
            return;
        }
        WorldData data = worldDataMap.computeIfAbsent(world.getName(), key -> new WorldData());
        data.setStart(normalizeLocation(location));
        hologramManager.setStart(world, data.getStart());
        save();
    }

    public void setEnd(World world, Location location) {
        if (world == null || location == null) {
            return;
        }
        WorldData data = worldDataMap.computeIfAbsent(world.getName(), key -> new WorldData());
        data.setEnd(normalizeLocation(location));
        hologramManager.setEnd(world, data.getEnd());
        save();
    }

    public void setCheckpoint(World world, int index, Location location) {
        if (world == null || location == null) {
            return;
        }
        WorldData data = worldDataMap.computeIfAbsent(world.getName(), key -> new WorldData());
        data.setCheckpoint(index, normalizeLocation(location));
        hologramManager.setCheckpoint(world, index, data.getCheckpoint(index));
        save();
    }

    public void removeStart(World world) {
        if (world == null) {
            return;
        }
        WorldData data = worldDataMap.get(world.getName());
        if (data == null) {
            return;
        }
        data.setStart(null);
        if (data.isEmpty()) {
            worldDataMap.remove(world.getName());
        }
        hologramManager.removeStart(world);
        save();
    }

    public void removeEnd(World world) {
        if (world == null) {
            return;
        }
        WorldData data = worldDataMap.get(world.getName());
        if (data == null) {
            return;
        }
        data.setEnd(null);
        if (data.isEmpty()) {
            worldDataMap.remove(world.getName());
        }
        hologramManager.removeEnd(world);
        save();
    }

    public void removeCheckpoint(World world, int index) {
        if (world == null) {
            return;
        }
        WorldData data = worldDataMap.get(world.getName());
        if (data == null) {
            return;
        }
        data.removeCheckpoint(index);
        if (data.isEmpty()) {
            worldDataMap.remove(world.getName());
        }
        hologramManager.removeCheckpoint(world, index);
        save();
    }

    private void tick() {
        Iterator<Map.Entry<UUID, RunSession>> iterator = sessions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, RunSession> entry = iterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                saveSession(entry.getKey(), entry.getValue());
                iterator.remove();
                continue;
            }
            RunSession session = entry.getValue();
            if (!player.getWorld().getName().equals(session.getWorldName())) {
                saveSession(entry.getKey(), session);
                messages.clearActionBar(player);
                iterator.remove();
                continue;
            }
            WorldMode mode = getWorldMode(player.getWorld());
            if (mode == WorldMode.NONE) {
                messages.clearActionBar(player);
                iterator.remove();
                continue;
            }
            if (!session.isStarted()) {
                continue;
            }

            long elapsed = System.currentTimeMillis() - session.getStartTimeMs();
            messages.actionBar(player, "用时: " + formatDuration(elapsed));

            if (mode == WorldMode.ONLY_SPRINT) {
                WorldData data = worldDataMap.get(player.getWorld().getName());
                if (data != null && !data.isCheckpointBlock(player.getLocation())) {
                    if (!player.isSprinting()) {
                        Location lastCheckpoint = session.getLastCheckpointLocation();
                        if (lastCheckpoint != null && WorldData.isSameBlock(player.getLocation(), lastCheckpoint)) {
                            continue;
                        }
                        teleportToCheckpoint(player, session, "&c疾跑中断，已返回上一个记录点。");
                    }
                }
            }
        }
    }

    private void teleportToCheckpoint(Player player, RunSession session, String message) {
        Location target = session.getLastCheckpointLocation();
        if (target == null) {
            messages.send(player, "&c未找到可返回的记录点。");
            return;
        }
        player.teleport(target.clone());
        messages.send(player, message);
    }

    private void saveSession(UUID playerId, RunSession session) {
        if (session == null || !session.isStarted()) {
            return;
        }
        String worldName = session.getWorldName();
        if (worldName == null) {
            return;
        }
        Location lastLocation = session.getLastCheckpointLocation();
        if (lastLocation == null) {
            return;
        }
        long elapsed = System.currentTimeMillis() - session.getStartTimeMs();
        Map<UUID, SavedSession> worldSessions = savedSessions.computeIfAbsent(worldName, key -> new HashMap<>());
        worldSessions.put(playerId, new SavedSession(playerId, worldName, elapsed, session.getLastCheckpointIndex(), lastLocation));
        save();
    }

    private void clearSavedSession(UUID playerId, String worldName) {
        if (worldName == null) {
            return;
        }
        Map<UUID, SavedSession> worldSessions = savedSessions.get(worldName);
        if (worldSessions == null) {
            return;
        }
        if (worldSessions.remove(playerId) != null) {
            if (worldSessions.isEmpty()) {
                savedSessions.remove(worldName);
            }
            save();
        }
    }

    private boolean updateRecord(Player player, long elapsed) {
        if (player == null) {
            return false;
        }
        String worldName = player.getWorld().getName();
        Map<UUID, RecordEntry> worldRecords = records.computeIfAbsent(worldName, key -> new HashMap<>());
        UUID playerId = player.getUniqueId();
        RecordEntry entry = worldRecords.get(playerId);
        if (entry == null || elapsed < entry.getTimeMs()) {
            worldRecords.put(playerId, new RecordEntry(playerId, player.getName(), elapsed));
            save();
            return true;
        }
        if (!player.getName().equals(entry.getName())) {
            entry.setName(player.getName());
            save();
        }
        return false;
    }

    private Location normalizeLocation(Location location) {
        Location block = location.getBlock().getLocation();
        block.setX(block.getBlockX() + 0.5);
        block.setY(block.getBlockY());
        block.setZ(block.getBlockZ() + 0.5);
        block.setYaw(location.getYaw());
        block.setPitch(location.getPitch());
        return block;
    }

    private Location readLocation(FileConfiguration config, String path) {
        Location direct = config.getLocation(path);
        if (direct != null) {
            return direct;
        }

        ConfigurationSection section = config.getConfigurationSection(path);
        if (section == null) {
            return null;
        }

        if (!section.isSet("x") || !section.isSet("y") || !section.isSet("z")) {
            return null;
        }

        String worldName = section.getString("world");
        World world = worldName == null || worldName.isEmpty() ? null : Bukkit.getWorld(worldName);
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw", 0.0D);
        float pitch = (float) section.getDouble("pitch", 0.0D);
        return new Location(world, x, y, z, yaw, pitch);
    }

    public String formatDuration(long millis) {
        long hours = TimeUnit.MILLISECONDS.toHours(millis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(millis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(millis) % 60;
        long ms = millis % 1000;
        if (hours > 0) {
            return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, ms);
        }
        return String.format("%02d:%02d.%03d", minutes, seconds, ms);
    }
}
