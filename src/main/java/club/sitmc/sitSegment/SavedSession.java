package club.sitmc.sitSegment;

import java.util.UUID;
import org.bukkit.Location;

public class SavedSession {
    private final UUID playerId;
    private final String worldName;
    private final long elapsedMs;
    private final int lastCheckpointIndex;
    private final Location lastCheckpointLocation;

    public SavedSession(UUID playerId, String worldName, long elapsedMs, int lastCheckpointIndex, Location lastCheckpointLocation) {
        this.playerId = playerId;
        this.worldName = worldName;
        this.elapsedMs = elapsedMs;
        this.lastCheckpointIndex = lastCheckpointIndex;
        this.lastCheckpointLocation = lastCheckpointLocation == null ? null : lastCheckpointLocation.clone();
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getWorldName() {
        return worldName;
    }

    public long getElapsedMs() {
        return elapsedMs;
    }

    public int getLastCheckpointIndex() {
        return lastCheckpointIndex;
    }

    public Location getLastCheckpointLocation() {
        return lastCheckpointLocation == null ? null : lastCheckpointLocation.clone();
    }

    public void bindWorld(org.bukkit.World world) {
        if (world == null || lastCheckpointLocation == null) {
            return;
        }
        if (lastCheckpointLocation.getWorld() == null) {
            lastCheckpointLocation.setWorld(world);
        }
    }
}
