package club.sitmc.sitSegment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.Location;
import org.bukkit.World;

public class PkwWorldData {
    private Integer yKill;
    private Integer startZ;
    private final TreeMap<Integer, Integer> lines = new TreeMap<>();
    private Integer endMainZ;

    private final List<Location> endBranchCenters = new ArrayList<>();
    private Location endEasyCenter;
    private Location endMediumCenter;
    private Location endHardCenter;
    private Location endExtremeCenter;

    public Integer getYKill() {
        return yKill;
    }

    public void setYKill(Integer yKill) {
        this.yKill = yKill;
    }

    public Integer getStartZ() {
        return startZ;
    }

    public void setStartZ(Integer startZ) {
        this.startZ = startZ;
    }

    public Integer getEndMainZ() {
        return endMainZ;
    }

    public void setEndMainZ(Integer endMainZ) {
        this.endMainZ = endMainZ;
    }

    public Map<Integer, Integer> getLines() {
        return Collections.unmodifiableMap(lines);
    }

    public Integer getLineZ(int index) {
        return lines.get(index);
    }

    public Integer getNextLineIndex(int lastIndex) {
        return lines.higherKey(lastIndex);
    }

    public void setLineZ(int index, Integer z) {
        if (index <= 0) {
            return;
        }
        if (z == null) {
            lines.remove(index);
            return;
        }
        lines.put(index, z);
    }

    public List<Location> getEndBranchCenters() {
        List<Location> copy = new ArrayList<>();
        for (Location loc : endBranchCenters) {
            if (loc != null) {
                copy.add(loc.clone());
            }
        }
        return copy;
    }

    public void addEndBranchCenter(Location endBranchCenter) {
        if (endBranchCenter == null) {
            return;
        }
        endBranchCenters.add(endBranchCenter.clone());
    }

    public void clearEndBranchCenters() {
        endBranchCenters.clear();
    }

    public Location getEndEasyCenter() {
        return cloneLocation(endEasyCenter);
    }

    public void setEndEasyCenter(Location endEasyCenter) {
        this.endEasyCenter = cloneLocation(endEasyCenter);
    }

    public Location getEndMediumCenter() {
        return cloneLocation(endMediumCenter);
    }

    public void setEndMediumCenter(Location endMediumCenter) {
        this.endMediumCenter = cloneLocation(endMediumCenter);
    }

    public Location getEndHardCenter() {
        return cloneLocation(endHardCenter);
    }

    public void setEndHardCenter(Location endHardCenter) {
        this.endHardCenter = cloneLocation(endHardCenter);
    }

    public Location getEndExtremeCenter() {
        return cloneLocation(endExtremeCenter);
    }

    public void setEndExtremeCenter(Location endExtremeCenter) {
        this.endExtremeCenter = cloneLocation(endExtremeCenter);
    }

    public void bindWorld(World world) {
        if (world == null) {
            return;
        }
        for (Location endBranchCenter : endBranchCenters) {
            if (endBranchCenter != null && endBranchCenter.getWorld() == null) {
                endBranchCenter.setWorld(world);
            }
        }
        if (endEasyCenter != null && endEasyCenter.getWorld() == null) {
            endEasyCenter.setWorld(world);
        }
        if (endMediumCenter != null && endMediumCenter.getWorld() == null) {
            endMediumCenter.setWorld(world);
        }
        if (endHardCenter != null && endHardCenter.getWorld() == null) {
            endHardCenter.setWorld(world);
        }
        if (endExtremeCenter != null && endExtremeCenter.getWorld() == null) {
            endExtremeCenter.setWorld(world);
        }
    }

    private static Location cloneLocation(Location location) {
        return location == null ? null : location.clone();
    }
}
