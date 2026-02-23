package club.sitmc.sitSegment;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Location;

public class WorldData {
    private Location start;
    private Location end;
    private final TreeMap<Integer, Location> checkpoints = new TreeMap<>();

    public Location getStart() {
        return cloneLocation(start);
    }

    public void setStart(Location start) {
        this.start = cloneLocation(start);
    }

    public Location getEnd() {
        return cloneLocation(end);
    }

    public void setEnd(Location end) {
        this.end = cloneLocation(end);
    }

    public Map<Integer, Location> getCheckpoints() {
        return Collections.unmodifiableMap(checkpoints);
    }

    public Location getCheckpoint(int index) {
        return cloneLocation(checkpoints.get(index));
    }

    public void setCheckpoint(int index, Location location) {
        checkpoints.put(index, cloneLocation(location));
    }

    public void removeCheckpoint(int index) {
        checkpoints.remove(index);
    }

    public Integer getNextCheckpointIndex(int lastIndex) {
        return checkpoints.higherKey(lastIndex);
    }

    public boolean isCheckpointBlock(Location location) {
        if (location == null) {
            return false;
        }
        if (start != null && isSameBlock(location, start)) {
            return true;
        }
        for (Location checkpoint : checkpoints.values()) {
            if (isSameBlock(location, checkpoint)) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return start == null && end == null && checkpoints.isEmpty();
    }

    public void bindWorld(org.bukkit.World world) {
        if (world == null) {
            return;
        }
        if (start != null && start.getWorld() == null) {
            start.setWorld(world);
        }
        if (end != null && end.getWorld() == null) {
            end.setWorld(world);
        }
        for (Location checkpoint : checkpoints.values()) {
            if (checkpoint != null && checkpoint.getWorld() == null) {
                checkpoint.setWorld(world);
            }
        }
    }

    public static boolean isSameBlock(Location first, Location second) {
        if (first == null || second == null) {
            return false;
        }
        if (first.getWorld() == null || second.getWorld() == null) {
            return false;
        }
        if (!first.getWorld().equals(second.getWorld())) {
            return false;
        }
        return first.getBlockX() == second.getBlockX()
                && first.getBlockY() == second.getBlockY()
                && first.getBlockZ() == second.getBlockZ();
    }

    private static Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }
}
