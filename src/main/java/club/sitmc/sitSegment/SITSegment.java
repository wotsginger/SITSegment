package club.sitmc.sitSegment;

import org.bukkit.command.PluginCommand;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerLoadEvent;
import org.bukkit.entity.Player;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class SITSegment extends JavaPlugin {

    private static final String DEFAULT_PREFIX = "&3&lSIT-Parkour &8| &f";
    private static final String LOBBY_EVENT_CLASS =
            "club.sitmc.sitParkourLobby.event.ManagedWorldsLoadedEvent";

    private ParkourManager parkourManager;
    private volatile boolean worldDataLoaded;

    @Override
    public void onEnable() {
        /*
         * IMPORTANT: Do NOT call saveDefaultConfig() or getConfig() here.
         * Both trigger YamlConfiguration.loadConfiguration() which deserializes
         * ALL Location objects in the config file. At this point Lobby has not
         * yet loaded its worlds, so Location deserialization fails with
         * "unknown world" and the entire config load throws, crashing onEnable().
         *
         * Instead we only ensure config.yml exists on disk, defer ALL config
         * reading to after the ManagedWorldsLoadedEvent (or fallback).
         */
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            saveResource("config.yml", false);
        }

        // Use hardcoded prefix during init. Real prefix from config is applied
        // in reloadAll() after config is safely loaded.
        parkourManager = new ParkourManager(this, DEFAULT_PREFIX);

        // Register event listeners and commands — no world dependency.
        ParkourListener listener = new ParkourListener(parkourManager);
        getServer().getPluginManager().registerEvents(listener, this);

        ParkourCommand command = new ParkourCommand(parkourManager);
        PluginCommand pluginCommand = getCommand("sitpk");
        if (pluginCommand != null) {
            pluginCommand.setExecutor(command);
            pluginCommand.setTabCompleter(command);
        }
        registerStandaloneCommand("prac", command);
        registerStandaloneCommand("unprac", command);
        registerStandaloneCommand("pracworld", command);
        registerStandaloneCommand("spec", command);
        registerStandaloneCommand("unspec", command);
        registerStandaloneCommand("specworld", command);
        PluginCommand topCommand = getCommand("top");
        if (topCommand != null) {
            topCommand.setExecutor(new TopCommand(parkourManager));
        }

        parkourManager.startTasks();

        // ---- Primary: dynamically register for Lobby's ManagedWorldsLoadedEvent ----
        boolean lobbyEventAvailable = tryRegisterLobbyEventListener();

        // ---- Controller via ServerLoadEvent (fallback + safety timeout) ----
        getServer().getPluginManager().registerEvents(new Listener() {
            @EventHandler(priority = EventPriority.MONITOR)
            public void onServerLoad(ServerLoadEvent event) {
                if (worldDataLoaded) {
                    return;
                }

                if (lobbyEventAvailable) {
                    /*
                     * Lobby event IS registered and should fire soon.
                     * Set a safety timeout: if the event hasn't fired within
                     * 100 ticks (5 seconds), assume something went wrong and
                     * fall back to direct loading.
                     */
                    getLogger().info("Server loaded — awaiting ManagedWorldsLoadedEvent from Lobby "
                            + "(safety timeout: 100 ticks)...");
                    getServer().getScheduler().runTaskLater(SITSegment.this, () -> {
                        if (!worldDataLoaded) {
                            getLogger().warning("ManagedWorldsLoadedEvent not received within "
                                    + "100 ticks. Performing fallback config load...");
                            performFallbackLoad();
                        }
                    }, 100L);
                } else {
                    /*
                     * Lobby is NOT installed or its event class is unavailable.
                     * Fall back to a delay after ServerLoadEvent, then load config.
                     * readLocation() will gracefully skip any worlds still missing.
                     */
                    getLogger().info("SITParkourLobby event unavailable — will load config "
                            + "via fallback in 20 ticks.");
                    getServer().getScheduler().runTaskLater(SITSegment.this, () -> {
                        if (!worldDataLoaded) {
                            performFallbackLoad();
                        }
                    }, 20L);
                }
            }
        }, this);
    }

    // -----------------------------------------------------------------------
    //  Dynamic event registration for ManagedWorldsLoadedEvent
    // -----------------------------------------------------------------------

    /**
     * Uses reflection to find and register a listener for Lobby's
     * ManagedWorldsLoadedEvent.  This avoids a compile-time dependency on
     * the Lobby plugin while still receiving its event at runtime.
     *
     * @return true if the event class was found and the listener was registered
     */
    @SuppressWarnings("unchecked")
    private boolean tryRegisterLobbyEventListener() {
        // Step 1 – load the event class via reflection
        Class<? extends Event> eventClass;
        try {
            eventClass = (Class<? extends Event>) Class.forName(LOBBY_EVENT_CLASS);
        } catch (ClassNotFoundException e) {
            getLogger().warning("Lobby event class '" + LOBBY_EVENT_CLASS
                    + "' not found — Lobby may not be installed. Will use fallback loading.");
            return false;
        }

        // Step 2 – get the getWorldNames() method for optional logging
        Method getWorldNamesMethod = null;
        try {
            getWorldNamesMethod = eventClass.getMethod("getWorldNames");
        } catch (NoSuchMethodException ignored) {
            // Method not present — logging will use a generic message
        }

        // Step 3 – build the executor that runs when the event fires
        final Method namesMethod = getWorldNamesMethod;
        EventExecutor executor = (listener, event) -> {
            if (worldDataLoaded) {
                return; // already loaded (safety-net against duplicate events)
            }
            worldDataLoaded = true;

            if (namesMethod != null) {
                try {
                    List<String> worldNames = (List<String>) namesMethod.invoke(event);
                    getLogger().info("Received ManagedWorldsLoadedEvent — "
                            + worldNames.size() + " world(s): " + worldNames);
                } catch (Exception e) {
                    getLogger().info("Received ManagedWorldsLoadedEvent — loading configuration...");
                }
            } else {
                getLogger().info("Received ManagedWorldsLoadedEvent — loading configuration...");
            }

            reloadAll();
            parkourManager.setWorldDataLoaded(true);
            getLogger().info("Configuration loaded successfully via ManagedWorldsLoadedEvent.");
        };

        // Step 4 – register the dynamic listener
        getServer().getPluginManager().registerEvent(
                eventClass,
                new Listener() {},       // dummy Listener instance
                EventPriority.NORMAL,    // Lobby fires at NORMAL; we match it
                executor,
                this                     // plugin
        );

        getLogger().info("Registered dynamic listener for " + LOBBY_EVENT_CLASS + ".");
        return true;
    }

    // -----------------------------------------------------------------------
    //  Fallback & safety
    // -----------------------------------------------------------------------

    /**
     * Called when:
     *  - Lobby is not installed (the event class is missing), OR
     *  - the safety timeout expired without receiving the event.
     *
     * By this point worlds should be loaded, so reloadConfig() will succeed.
     * If any worlds are still missing, readLocation() gracefully skips them
     * with a single WARNING log per entry (no stack traces).
     */
    private void performFallbackLoad() {
        if (worldDataLoaded) {
            return;
        }
        worldDataLoaded = true;
        getLogger().info("Performing fallback configuration load...");
        reloadAll();
        parkourManager.setWorldDataLoaded(true);
        getLogger().info("Configuration loaded successfully via fallback.");
    }

    // -----------------------------------------------------------------------
    //  Public API
    // -----------------------------------------------------------------------

    @Override
    public void onDisable() {
        if (parkourManager != null) {
            parkourManager.shutdown();
        }
    }

    /**
     * Reloads config from disk and re-populates all in-memory data.
     * Safe to call when worlds are already loaded (e.g. /sitpk reload).
     */
    public void reloadAll() {
        reloadConfig();
        // Now that config is safely loaded, read the real prefix
        String prefix = getConfig().getString("prefix", DEFAULT_PREFIX);
        parkourManager.updatePrefix(prefix);
        parkourManager.load();
        parkourManager.restoreOnlinePlayers();
    }

    // -----------------------------------------------------------------------
    //  供 Lobby 钟菜单反射调用的只读查询方法
    // -----------------------------------------------------------------------

    /**
     * 获取所有地图列表（供 Lobby 钟菜单反射调用）。
     * <p>
     * 每条 Map 固定 key: id, name, type, world。
     * 包含 segment 和 onlysprint 两类地图，type 分别为 "segment" / "onlysprint"。
     * author/status 元数据已转移至 SITParkourLobby 统一管理。
     * 数据未就绪时返回空列表。
     *
     * @return 地图信息列表，不含空值
     */
    public List<Map<String, String>> getParkourMaps() {
        try {
            if (parkourManager == null || !parkourManager.isWorldDataLoaded()) {
                return List.of();
            }
            return parkourManager.buildParkourMapsList();
        } catch (Exception e) {
            getLogger().warning("getParkourMaps() 异常: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * 获取指定地图排行榜（按榜名分组，供 Lobby 钟菜单反射调用）。
     * <p>
     * 外层 Map key 固定 "main"（Segment 每图单榜），value 为前 topN 名列表。
     * 每条记录 Map 固定 key: rank, player, score（score 为已格式化的成绩字符串）。
     * 数据未就绪或地图不存在时返回空 Map。
     *
     * @param mapId 地图标识（世界名）
     * @param topN  返回前 N 名
     * @return 按榜名分组的排行榜，外层含 "main" 键
     */
    public Map<String, List<Map<String, String>>> getLeaderboards(String mapId, int topN) {
        try {
            if (parkourManager == null || !parkourManager.isWorldDataLoaded()) {
                return Map.of();
            }
            return parkourManager.buildLeaderboards(mapId, topN);
        } catch (Exception e) {
            getLogger().warning("getLeaderboards() 异常: " + e.getMessage());
            return Map.of();
        }
    }

    /**
     * 获取玩家在指定地图的个人名次（按榜名分组，供 Lobby 钟菜单反射调用）。
     * <p>
     * 外层 Map key 固定 "main"，value 为玩家名次 Map（key: rank, player, score）。
     * 数据未就绪或无记录时返回 null。
     *
     * @param mapId    地图标识（世界名）
     * @param playerId 玩家 UUID
     * @return 按榜名分组的名次，或无记录时返回 null
     */
    public Map<String, Map<String, String>> getPlayerRanks(String mapId, UUID playerId) {
        try {
            if (parkourManager == null || !parkourManager.isWorldDataLoaded()) {
                return null;
            }
            return parkourManager.buildPlayerRanks(mapId, playerId);
        } catch (Exception e) {
            getLogger().warning("getPlayerRanks() 异常: " + e.getMessage());
            return null;
        }
    }

    // -----------------------------------------------------------------------
    //  供其他插件调用的公开查询方法
    // -----------------------------------------------------------------------

    /**
     * 检查玩家是否处于练习模式（供其他插件调用）。
     *
     * @param player 目标玩家
     * @return true 表示玩家正在练习模式中
     */
    public boolean isPracticing(Player player) {
        if (parkourManager == null) {
            return false;
        }
        return parkourManager.getPracticeSpecManager().isPracticing(player);
    }

    /**
     * 检查玩家是否处于旁观模式（供其他插件调用）。
     *
     * @param player 目标玩家
     * @return true 表示玩家正在旁观模式中
     */
    public boolean isSpectating(Player player) {
        if (parkourManager == null) {
            return false;
        }
        return parkourManager.getPracticeSpecManager().isSpectating(player);
    }

    private void registerStandaloneCommand(String name, ParkourCommand executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }
}
