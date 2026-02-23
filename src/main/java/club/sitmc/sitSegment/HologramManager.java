package club.sitmc.sitSegment;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.TextDisplay;

public class HologramManager {
    private final Map<String, Map<String, UUID>> holograms = new HashMap<>();

    public void storeHologram(String worldName, String key, UUID uuid) {
        if (worldName == null || key == null || uuid == null) {
            return;
        }
        holograms.computeIfAbsent(worldName, name -> new HashMap<>()).put(key, uuid);
    }

    public Map<String, Map<String, UUID>> getStoredHolograms() {
        Map<String, Map<String, UUID>> snapshot = new HashMap<>();
        for (Map.Entry<String, Map<String, UUID>> entry : holograms.entrySet()) {
            snapshot.put(entry.getKey(), new HashMap<>(entry.getValue()));
        }
        return snapshot;
    }

    public void clearStoredHolograms() {
        holograms.clear();
    }

    public void setStart(World world, Location location) {
        setHologram(world, "start", location, "\u8d77\u70b9");
    }

    public void setEnd(World world, Location location) {
        setHologram(world, "end", location, "\u7ec8\u70b9");
    }

    public void setCheckpoint(World world, int index, Location location) {
        String label = "\u8bb0\u5f55\u70b9 " + index;
        setHologram(world, "checkpoint:" + index, location, label);
    }

    public void removeStart(World world) {
        removeHologram(world, "start");
    }

    public void removeEnd(World world) {
        removeHologram(world, "end");
    }

    public void removeCheckpoint(World world, int index) {
        removeHologram(world, "checkpoint:" + index);
    }

    public void removeWorld(String worldName) {
        Map<String, UUID> worldHolograms = holograms.remove(worldName);
        if (worldHolograms == null) {
            return;
        }
        for (UUID uuid : worldHolograms.values()) {
            Entity entity = Bukkit.getEntity(uuid);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    private void setHologram(World world, String key, Location location, String text) {
        if (world == null || location == null || key == null) {
            return;
        }
        removeHologram(world, key);
        UUID uuid = spawnText(world, location, text);
        storeHologram(world.getName(), key, uuid);
    }

    private void removeHologram(World world, String key) {
        if (world == null || key == null) {
            return;
        }
        Map<String, UUID> worldHolograms = holograms.get(world.getName());
        if (worldHolograms == null) {
            return;
        }
        UUID uuid = worldHolograms.remove(key);
        if (uuid == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(uuid);
        if (entity != null) {
            entity.remove();
        }
        if (worldHolograms.isEmpty()) {
            holograms.remove(world.getName());
        }
    }

    private UUID spawnText(World world, Location baseLocation, String text) {
        Location spawnLocation = baseLocation.clone().add(0.0, 1.5, 0.0);
        TextDisplay display = world.spawn(spawnLocation, TextDisplay.class, entity -> {
            entity.text(Component.text(text, NamedTextColor.GOLD, TextDecoration.BOLD));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setAlignment(TextDisplay.TextAlignment.CENTER);
            entity.setDefaultBackground(false);
            entity.setShadowed(false);
            entity.setSeeThrough(true);
            entity.setGravity(false);
            entity.setPersistent(true);
        });
        return display.getUniqueId();
    }
}
