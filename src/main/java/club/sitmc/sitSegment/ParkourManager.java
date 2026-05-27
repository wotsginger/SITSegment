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
    private final PracticeSpecManager practiceSpecManager;
    private final HologramManager hologramManager;
    private final Map<String, WorldData> worldDataMap = new HashMap<>();
    private final Map<String, Map<UUID, RecordEntry>> records = new HashMap<>();
    private final Map<String, Map<UUID, SavedSession>> savedSessions = new HashMap<>();
    private final Set<String> segmentWorlds = new HashSet<>();
    private final Set<String> onlySprintWorlds = new HashSet<>();
    private final Set<String> pkwWorlds = new HashSet<>();
    private final Map<UUID, RunSession> sessions = new HashMap<>();
    private final Map<String, PkwWorldData> pkwWorldDataMap = new HashMap<>();
    private final Map<UUID, PkwSession> pkwSessions = new HashMap<>();
    private BukkitTask tickTask;
    private int tickCounter;
    private static final int ACTIONBAR_PERIOD_TICKS = 5;
    private static final float SOUTH_YAW = 0.0f;

    public ParkourManager(SITSegment plugin) {
        this.plugin = plugin;
        String prefix = plugin.getConfig().getString("prefix", "&3&lSIT-Parkour &8| &f");
        this.messages = new MessageUtil(prefix);
        this.itemUtil = new ItemUtil(plugin);
        this.practiceSpecManager = new PracticeSpecManager(plugin, messages, itemUtil, this);
        this.hologramManager = new HologramManager();
    }

    public void load() {
        worldDataMap.clear();
        records.clear();
        savedSessions.clear();
        segmentWorlds.clear();
        onlySprintWorlds.clear();
        pkwWorlds.clear();
        pkwWorldDataMap.clear();
        pkwSessions.clear();
        hologramManager.clearStoredHolograms();
        practiceSpecManager.loadConfig();

        FileConfiguration config = plugin.getConfig();
        segmentWorlds.addAll(config.getStringList("worlds.segment"));
        onlySprintWorlds.addAll(config.getStringList("worlds.onlysprint"));
        pkwWorlds.addAll(config.getStringList("worlds.pkw"));

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

        ConfigurationSection pkwSection = config.getConfigurationSection("pkw");
        if (pkwSection != null) {
            for (String worldName : pkwSection.getKeys(false)) {
                ConfigurationSection worldSection = pkwSection.getConfigurationSection(worldName);
                if (worldSection == null) {
                    continue;
                }
                PkwWorldData data = new PkwWorldData();
                if (worldSection.isSet("yKill")) {
                    data.setYKill(worldSection.getInt("yKill"));
                }
                if (worldSection.isSet("startZ")) {
                    data.setStartZ(worldSection.getInt("startZ"));
                }
                if (worldSection.isSet("end.mainZ")) {
                    data.setEndMainZ(worldSection.getInt("end.mainZ"));
                } else if (worldSection.isSet("endMainZ")) {
                    // backward compatible key (older builds)
                    data.setEndMainZ(worldSection.getInt("endMainZ"));
                }
                ConfigurationSection lines = worldSection.getConfigurationSection("lines");
                if (lines != null) {
                    for (String key : lines.getKeys(false)) {
                        int index;
                        try {
                            index = Integer.parseInt(key);
                        } catch (NumberFormatException ex) {
                            continue;
                        }
                        if (index <= 0) {
                            continue;
                        }
                        if (lines.isSet(key)) {
                            data.setLineZ(index, lines.getInt(key));
                        }
                    }
                }

                Object branchRaw = config.get("pkw." + worldName + ".end.branch");
                if (branchRaw instanceof List<?> list) {
                    for (Object obj : list) {
                        if (obj instanceof Location loc) {
                            data.addEndBranchCenter(loc);
                        }
                    }
                } else {
                    Location endBranch = readLocation(config, "pkw." + worldName + ".end.branch");
                    if (endBranch != null) {
                        data.addEndBranchCenter(endBranch);
                    }
                }
                Location endEasy = readLocation(config, "pkw." + worldName + ".end.easy");
                if (endEasy != null) {
                    data.setEndEasyCenter(endEasy);
                }
                Location endMedium = readLocation(config, "pkw." + worldName + ".end.medium");
                if (endMedium != null) {
                    data.setEndMediumCenter(endMedium);
                }
                Location endHard = readLocation(config, "pkw." + worldName + ".end.hard");
                if (endHard != null) {
                    data.setEndHardCenter(endHard);
                }
                Location endExtreme = readLocation(config, "pkw." + worldName + ".end.extreme");
                if (endExtreme != null) {
                    data.setEndExtremeCenter(endExtreme);
                }

                boolean hasAny = data.getYKill() != null
                        || data.getStartZ() != null
                        || data.getEndMainZ() != null
                        || !data.getLines().isEmpty()
                        || !data.getEndBranchCenters().isEmpty()
                        || data.getEndEasyCenter() != null
                        || data.getEndMediumCenter() != null
                        || data.getEndHardCenter() != null
                        || data.getEndExtremeCenter() != null;
                if (hasAny) {
                    pkwWorldDataMap.put(worldName, data);
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
        config.set("worlds.pkw", new ArrayList<>(pkwWorlds));
        config.set("points", null);
        config.set("records", null);
        config.set("sessions", null);
        config.set("holograms", null);
        config.set("pkw", null);

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
        for (Map.Entry<String, PkwWorldData> entry : pkwWorldDataMap.entrySet()) {
            String worldName = entry.getKey();
            PkwWorldData data = entry.getValue();
            String basePath = "pkw." + worldName;
            if (data.getYKill() != null) {
                config.set(basePath + ".yKill", data.getYKill());
            }
            if (data.getStartZ() != null) {
                config.set(basePath + ".startZ", data.getStartZ());
            }
            if (data.getEndMainZ() != null) {
                config.set(basePath + ".end.mainZ", data.getEndMainZ());
            }
            if (!data.getLines().isEmpty()) {
                for (Map.Entry<Integer, Integer> line : data.getLines().entrySet()) {
                    config.set(basePath + ".lines." + line.getKey(), line.getValue());
                }
            }
            List<Location> branchCenters = data.getEndBranchCenters();
            if (!branchCenters.isEmpty()) {
                config.set(basePath + ".end.branch", branchCenters);
            }
            if (data.getEndEasyCenter() != null) {
                config.set(basePath + ".end.easy", data.getEndEasyCenter());
            }
            if (data.getEndMediumCenter() != null) {
                config.set(basePath + ".end.medium", data.getEndMediumCenter());
            }
            if (data.getEndHardCenter() != null) {
                config.set(basePath + ".end.hard", data.getEndHardCenter());
            }
            if (data.getEndExtremeCenter() != null) {
                config.set(basePath + ".end.extreme", data.getEndExtremeCenter());
            }
        }
        plugin.saveConfig();
    }

    public void startTasks() {
        if (tickTask != null) {
            tickTask.cancel();
        }
        tickCounter = 0;
        tickTask = new BukkitRunnable() {
            @Override
            public void run() {
                tickCounter++;
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
        for (UUID playerId : pkwSessions.keySet()) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                messages.clearActionBar(player);
            }
        }
        pkwSessions.clear();
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

    public PracticeSpecManager getPracticeSpecManager() {
        return practiceSpecManager;
    }

    public WorldData getWorldData(World world) {
        return world == null ? null : worldDataMap.get(world.getName());
    }

    public PkwWorldData getPkwWorldData(World world) {
        return world == null ? null : pkwWorldDataMap.get(world.getName());
    }

    public void bindWorldData(World world) {
        if (world == null) {
            return;
        }
        WorldData data = worldDataMap.get(world.getName());
        if (data != null) {
            data.bindWorld(world);
        }
        PkwWorldData pkwData = pkwWorldDataMap.get(world.getName());
        if (pkwData != null) {
            pkwData.bindWorld(world);
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
        if (pkwWorlds.contains(name)) {
            return WorldMode.PKW;
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
        pkwWorlds.remove(name);
        if (mode == WorldMode.SEGMENT) {
            segmentWorlds.add(name);
        } else if (mode == WorldMode.ONLY_SPRINT) {
            onlySprintWorlds.add(name);
        } else if (mode == WorldMode.PKW) {
            pkwWorlds.add(name);
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
        WorldMode mode = getWorldMode(player.getWorld());
        if (mode == WorldMode.NONE) {
            return;
        }
        if (mode == WorldMode.PKW) {
            itemUtil.removeSegmentItems(player);
            itemUtil.givePkwGateReturnItem(player);
            itemUtil.givePkwAbandonItem(player);
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
        if (player == null) {
            return;
        }
        if (getWorldMode(player.getWorld()) == WorldMode.PKW) {
            handlePkwReturn(player);
            return;
        }
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
        if (mode == WorldMode.PKW) {
            handlePkwRestart(player);
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
        pkwSessions.remove(player.getUniqueId());
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

    public void setPkwYKill(World world, int yKill) {
        if (world == null) {
            return;
        }
        PkwWorldData data = pkwWorldDataMap.computeIfAbsent(world.getName(), key -> new PkwWorldData());
        data.setYKill(yKill);
        save();
    }

    public void removePkwYKill(World world) {
        if (world == null) {
            return;
        }
        PkwWorldData data = pkwWorldDataMap.get(world.getName());
        if (data == null) {
            return;
        }
        data.setYKill(null);
        cleanupPkwData(world.getName(), data);
        save();
    }

    public void setPkwStartLine(World world, int z) {
        if (world == null) {
            return;
        }
        PkwWorldData data = pkwWorldDataMap.computeIfAbsent(world.getName(), key -> new PkwWorldData());
        data.setStartZ(z);
        save();
    }

    public void removePkwStartLine(World world) {
        if (world == null) {
            return;
        }
        PkwWorldData data = pkwWorldDataMap.get(world.getName());
        if (data == null) {
            return;
        }
        data.setStartZ(null);
        cleanupPkwData(world.getName(), data);
        save();
    }

    public void setPkwEndMainLine(World world, int z) {
        if (world == null) {
            return;
        }
        PkwWorldData data = pkwWorldDataMap.computeIfAbsent(world.getName(), key -> new PkwWorldData());
        data.setEndMainZ(z);
        save();
    }

    public void removePkwEndMainLine(World world) {
        if (world == null) {
            return;
        }
        PkwWorldData data = pkwWorldDataMap.get(world.getName());
        if (data == null) {
            return;
        }
        data.setEndMainZ(null);
        cleanupPkwData(world.getName(), data);
        save();
    }

    public void setPkwRecordLine(World world, int index, int z) {
        if (world == null) {
            return;
        }
        if (index <= 0) {
            return;
        }
        PkwWorldData data = pkwWorldDataMap.computeIfAbsent(world.getName(), key -> new PkwWorldData());
        data.setLineZ(index, z);
        save();
    }

    public void removePkwRecordLine(World world, int index) {
        if (world == null) {
            return;
        }
        if (index <= 0) {
            return;
        }
        PkwWorldData data = pkwWorldDataMap.get(world.getName());
        if (data == null) {
            return;
        }
        data.setLineZ(index, null);
        cleanupPkwData(world.getName(), data);
        save();
    }

    public void setPkwEnd(World world, String type, Location location) {
        if (world == null || type == null || location == null) {
            return;
        }
        PkwWorldData data = pkwWorldDataMap.computeIfAbsent(world.getName(), key -> new PkwWorldData());
        Location center = normalizeLocation(location);
        switch (type.toLowerCase()) {
            case "branch" -> data.addEndBranchCenter(center);
            case "easy" -> data.setEndEasyCenter(center);
            case "medium" -> data.setEndMediumCenter(center);
            case "hard" -> data.setEndHardCenter(center);
            case "extreme" -> data.setEndExtremeCenter(center);
            default -> {
                return;
            }
        }
        save();
    }

    public void removePkwEnd(World world, String type) {
        if (world == null || type == null) {
            return;
        }
        PkwWorldData data = pkwWorldDataMap.get(world.getName());
        if (data == null) {
            return;
        }
        switch (type.toLowerCase()) {
            case "branch" -> data.clearEndBranchCenters();
            case "easy" -> data.setEndEasyCenter(null);
            case "medium" -> data.setEndMediumCenter(null);
            case "hard" -> data.setEndHardCenter(null);
            case "extreme" -> data.setEndExtremeCenter(null);
            default -> {
                return;
            }
        }
        cleanupPkwData(world.getName(), data);
        save();
    }

    private void cleanupPkwData(String worldName, PkwWorldData data) {
        if (worldName == null || data == null) {
            return;
        }
        boolean empty = data.getYKill() == null
                && data.getStartZ() == null
                && data.getEndMainZ() == null
                && data.getLines().isEmpty()
                && data.getEndBranchCenters().isEmpty()
                && data.getEndEasyCenter() == null
                && data.getEndMediumCenter() == null
                && data.getEndHardCenter() == null
                && data.getEndExtremeCenter() == null;
        if (empty) {
            pkwWorldDataMap.remove(worldName);
        }
    }

    public void clearPkwSession(Player player) {
        if (player == null) {
            return;
        }
        pkwSessions.remove(player.getUniqueId());
        messages.clearActionBar(player);
    }

    public void handlePkwMove(Player player, Location from, Location to) {
        if (player == null || to == null || to.getWorld() == null) {
            return;
        }
        if (practiceSpecManager.isPracticing(player)) {
            return;
        }
        World world = to.getWorld();
        PkwWorldData data = pkwWorldDataMap.get(world.getName());
        if (data == null) {
            return;
        }

        Integer yKill = data.getYKill();
        if (yKill != null && to.getY() < yKill) {
            player.setHealth(0.0);
            return;
        }

        PkwSession session = pkwSessions.computeIfAbsent(player.getUniqueId(), id -> new PkwSession(id));
        session.ensureWorld(world.getName());

        Location startRespawn = createPkwStartRespawn(world);
        if (!world.getName().equals(session.getWorldName()) || session.getRespawnLocation() == null) {
            session.reset(world.getName(), startRespawn);
            setPlayerRespawn(player, startRespawn);
        }

        // Branch end: enter any 5x5 region -> teleport back to current gate and give item.
        if (enteredAnyBranchEnd(from, to, data)) {
            teleportToPkwRespawn(player, session);
            itemUtil.givePkwGateReturnItem(player);
            messages.send(player, "&a已将您传送至当前大关关口。");
            return;
        }

        // Main end: stop timer and tp to start when entering any end region.
        if (session.isStarted() && enteredAnyMainEnd(from, to, data)) {
            long elapsed = System.currentTimeMillis() - session.getStartTimeMs();
            messages.send(player, "&a完成！用时 &f" + formatDuration(elapsed));
            pkwSessions.remove(player.getUniqueId());
            messages.clearActionBar(player);
            setPlayerRespawn(player, startRespawn);
            player.teleport(startRespawn.clone());
            return;
        }

        Integer startZ = data.getStartZ();
        if (startZ != null && !session.isStarted() && crossedLine(from, to, startZ)) {
            session.startTiming();
            session.reachRecord(0, startRespawn);
            setPlayerRespawn(player, startRespawn);
            messages.send(player, "&a计时开始。");
        }

        if (!session.isStarted()) {
            return;
        }

        // End-main line: set a dedicated checkpoint at x=0, z=line (not +21).
        if (!session.hasReachedEndMain()
                && data.getEndMainZ() != null
                && data.getNextLineIndex(session.getLastRecordIndex()) == null
                && crossedLine(from, to, data.getEndMainZ())) {
            Location respawn = createPkwEndMainRespawn(world, data.getEndMainZ(), to.getBlockY());
            session.reachEndMain(respawn);
            setPlayerRespawn(player, respawn);
        }

        Integer nextIndex = data.getNextLineIndex(session.getLastRecordIndex());
        if (nextIndex == null) {
            return;
        }
        Integer lineZ = data.getLineZ(nextIndex);
        if (lineZ == null) {
            return;
        }
        if (!crossedLine(from, to, lineZ)) {
            return;
        }
        Location respawn = createPkwRecordRespawn(world, lineZ, to.getBlockY());
        session.reachRecord(nextIndex, respawn);
        setPlayerRespawn(player, respawn);
    }

    public void handlePkwReturn(Player player) {
        if (player == null) {
            return;
        }
        if (getWorldMode(player.getWorld()) != WorldMode.PKW) {
            return;
        }
        PkwSession session = pkwSessions.computeIfAbsent(player.getUniqueId(), id -> new PkwSession(id));
        session.ensureWorld(player.getWorld().getName());
        Location startRespawn = createPkwStartRespawn(player.getWorld());
        if (session.getRespawnLocation() == null) {
            session.reset(player.getWorld().getName(), startRespawn);
        }
        teleportToPkwRespawn(player, session);
    }

    public void handlePkwAbandon(Player player) {
        if (player == null) {
            return;
        }
        if (getWorldMode(player.getWorld()) != WorldMode.PKW) {
            return;
        }
        World world = player.getWorld();
        Location startRespawn = createPkwStartRespawn(world);
        // Stop timer and reset progress.
        pkwSessions.remove(player.getUniqueId());
        messages.clearActionBar(player);
        setPlayerRespawn(player, startRespawn);
        player.teleport(startRespawn.clone());
        messages.send(player, "&a已将您传送至起点。");
    }

    private void handlePkwRestart(Player player) {
        if (player == null) {
            return;
        }
        World world = player.getWorld();
        PkwWorldData data = pkwWorldDataMap.get(world.getName());
        if (data == null) {
            return;
        }
        PkwSession session = pkwSessions.computeIfAbsent(player.getUniqueId(), id -> new PkwSession(id));
        Location startRespawn = createPkwStartRespawn(world);
        session.reset(world.getName(), startRespawn);
        setPlayerRespawn(player, startRespawn);
        player.teleport(startRespawn.clone());
        messages.send(player, "&a已重置，穿过起点线开始计时。");
    }

    private void teleportToPkwRespawn(Player player, PkwSession session) {
        if (player == null || session == null) {
            return;
        }
        Location respawn = session.getRespawnLocation();
        if (respawn == null) {
            respawn = createPkwStartRespawn(player.getWorld());
            session.reachRecord(session.getLastRecordIndex(), respawn);
        }
        Location safe = snapToGround(respawn.getWorld(), respawn.getBlockX(), respawn.getBlockZ());
        player.teleport((safe == null ? respawn : safe).clone());
    }

    private boolean enteredAnyMainEnd(Location from, Location to, PkwWorldData data) {
        return enteredRegion(from, to, data.getEndEasyCenter())
                || enteredRegion(from, to, data.getEndMediumCenter())
                || enteredRegion(from, to, data.getEndHardCenter())
                || enteredRegion(from, to, data.getEndExtremeCenter());
    }

    private boolean enteredAnyBranchEnd(Location from, Location to, PkwWorldData data) {
        if (data == null) {
            return false;
        }
        for (Location center : data.getEndBranchCenters()) {
            if (enteredRegion(from, to, center)) {
                return true;
            }
        }
        return false;
    }

    private boolean enteredRegion(Location from, Location to, Location center) {
        if (center == null || to == null) {
            return false;
        }
        if (!isIn5x5(to, center)) {
            return false;
        }
        return from == null || !isIn5x5(from, center);
    }

    private boolean isIn5x5(Location location, Location center) {
        if (location == null || center == null) {
            return false;
        }
        if (location.getWorld() == null || center.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().equals(center.getWorld())) {
            return false;
        }
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return y == cy && x >= cx - 2 && x <= cx + 2 && z >= cz - 2 && z <= cz + 2;
    }

    private boolean crossedLine(Location from, Location to, int zLine) {
        if (from == null || to == null) {
            return false;
        }
        return from.getZ() <= zLine && to.getZ() > zLine;
    }

    private Location createPkwStartRespawn(World world) {
        if (world == null) {
            return null;
        }
        return snapToGround(world, 0, 0);
    }

    private Location createPkwRecordRespawn(World world, int recordZ, int y) {
        if (world == null) {
            return null;
        }
        return snapToGround(world, 0, recordZ + 21);
    }

    private Location createPkwEndMainRespawn(World world, int zLine, int y) {
        if (world == null) {
            return null;
        }
        return snapToGround(world, 0, zLine);
    }

    private Location snapToGround(World world, int blockX, int blockZ) {
        if (world == null) {
            return null;
        }
        int topY = world.getHighestBlockYAt(blockX, blockZ);
        int y = Math.max(world.getMinHeight(), topY + 1);
        Location loc = new Location(world, blockX + 0.5, y, blockZ + 0.5);
        loc.setYaw(SOUTH_YAW);
        loc.setPitch(0.0f);
        return loc;
    }

    private void setPlayerRespawn(Player player, Location location) {
        if (player == null || location == null) {
            return;
        }
        // Paper API: force = true to apply even if player hasn't slept.
        player.setRespawnLocation(location, true);
    }

    private void tick() {
        final boolean updateActionBar = tickCounter % ACTIONBAR_PERIOD_TICKS == 0;
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

            if (updateActionBar) {
                long elapsed = System.currentTimeMillis() - session.getStartTimeMs();
                long totalSeconds = Math.max(0L, elapsed / 1000L);
                long minutes = totalSeconds / 60L;
                long seconds = totalSeconds % 60L;
                messages.actionBar(player, String.format("Time: %02d:%02d", minutes, seconds));
            }

            if (mode == WorldMode.ONLY_SPRINT) {
                // Practice mode should not be punished by ONLY_SPRINT tp-back logic.
                if (practiceSpecManager.isPracticing(player)) {
                    continue;
                }
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

        Iterator<Map.Entry<UUID, PkwSession>> pkwIterator = pkwSessions.entrySet().iterator();
        while (pkwIterator.hasNext()) {
            Map.Entry<UUID, PkwSession> entry = pkwIterator.next();
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                pkwIterator.remove();
                continue;
            }
            PkwSession session = entry.getValue();
            if (session.getWorldName() == null || !player.getWorld().getName().equals(session.getWorldName())) {
                messages.clearActionBar(player);
                pkwIterator.remove();
                continue;
            }
            if (getWorldMode(player.getWorld()) != WorldMode.PKW) {
                messages.clearActionBar(player);
                pkwIterator.remove();
                continue;
            }
            if (!session.isStarted()) {
                continue;
            }
            if (updateActionBar) {
                long elapsed = System.currentTimeMillis() - session.getStartTimeMs();
                long totalSeconds = Math.max(0L, elapsed / 1000L);
                long minutes = totalSeconds / 60L;
                long seconds = totalSeconds % 60L;
                messages.actionBar(player, String.format("Time: %02d:%02d", minutes, seconds));
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
        // Bukkit's Location deserialization throws IllegalArgumentException("unknown world")
        // if the world isn't loaded (e.g. multiverse worlds loaded later) or was renamed/removed.
        // We must not fail plugin enable because of a single bad location entry.
        Location direct = null;
        try {
            direct = config.getLocation(path);
        } catch (Exception ex) {
            plugin.getLogger().warning("Failed to deserialize Location at '" + path + "': " + ex.getMessage());
        }
        if (direct != null && direct.getWorld() != null) {
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
        if (world == null) {
            if (worldName != null && !worldName.isBlank()) {
                plugin.getLogger().warning("World '" + worldName + "' is not loaded/unknown for location '" + path + "'. Skipping.");
            }
            return null;
        }
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
