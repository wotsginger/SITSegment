package club.sitmc.sitSegment;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class ParkourListener implements Listener {
    private final ParkourManager manager;

    public ParkourListener(ParkourManager manager) {
        this.manager = manager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || isSameBlock(from, to)) {
            return;
        }
        World world = to.getWorld();
        if (manager.getWorldMode(world) == WorldMode.NONE) {
            return;
        }
        WorldData data = manager.getWorldData(world);
        if (data == null) {
            return;
        }
        Player player = event.getPlayer();
        if (manager.getPracticeSpecManager().isPracticing(player)) {
            return;
        }
        Location start = data.getStart();
        if (start != null && WorldData.isSameBlock(to, start)) {
            manager.startRun(player, data);
            return;
        }
        RunSession session = manager.getSession(player.getUniqueId());
        if (session == null || !session.isStarted()) {
            return;
        }
        Integer expected = session.getNextCheckpointIndex();
        if (expected != null) {
            Location checkpoint = data.getCheckpoint(expected);
            if (checkpoint != null && WorldData.isSameBlock(to, checkpoint)) {
                manager.reachCheckpoint(player, session, data, expected);
                return;
            }
        }
        Location end = data.getEnd();
        if (end != null && WorldData.isSameBlock(to, end)) {
            manager.tryFinish(player, session);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action == Action.RIGHT_CLICK_AIR) {
            handleRightClick(event);
            return;
        }
        if (action == Action.RIGHT_CLICK_BLOCK) {
            handleRightClick(event);
        }
    }

    @EventHandler
    public void onCommandPreprocess(PlayerCommandPreprocessEvent event) {
        manager.getPracticeSpecManager().onCommandPreprocess(event);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.saveAndRemoveSession(event.getPlayer());
        manager.getPracticeSpecManager().onQuit(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        manager.saveAndRemoveSession(event.getPlayer());
        manager.tryRestoreSession(event.getPlayer());
        manager.giveDefaultItems(event.getPlayer());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        manager.tryRestoreSession(event.getPlayer());
        manager.giveDefaultItems(event.getPlayer());
        manager.getPracticeSpecManager().onJoin(event.getPlayer());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        manager.bindWorldData(event.getWorld());
    }

    private boolean isSameBlock(Location from, Location to) {
        return from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }

    private void handleRightClick(PlayerInteractEvent event) {
        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR) {
            return;
        }

        if (manager.getPracticeSpecManager().handlePracticeItemInteract(player, item)) {
            event.setCancelled(true);
            return;
        }

        boolean isReturn = manager.getItemUtil().isReturnItem(item);
        boolean isRestart = manager.getItemUtil().isRestartItem(item);
        boolean isExit = manager.getItemUtil().isExitItem(item);
        if (!isReturn && !isRestart && !isExit) {
            return;
        }

        if (isReturn) {
            event.setCancelled(true);
            manager.handleReturnItem(player);
            return;
        }

        if (isRestart) {
            event.setCancelled(true);
            manager.handleRestartItem(player);
            return;
        }

        if (isExit) {
            event.setCancelled(true);
            manager.handleExitParkour(player);
        }
    }
}
