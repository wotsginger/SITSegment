package club.sitmc.sitSegment;

import java.util.UUID;
import org.bukkit.Location;

public class RunSession {
    private final UUID playerId;
    private String worldName;
    private boolean started;
    private long startTimeMs;
    private int lastCheckpointIndex;
    private Location lastCheckpointLocation;
    private Integer nextCheckpointIndex;

    public RunSession(UUID playerId) {
        this.playerId = playerId;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getWorldName() {
        return worldName;
    }

    public boolean isStarted() {
        return started;
    }

    public long getStartTimeMs() {
        return startTimeMs;
    }

    public int getLastCheckpointIndex() {
        return lastCheckpointIndex;
    }

    public Location getLastCheckpointLocation() {
        return lastCheckpointLocation == null ? null : lastCheckpointLocation.clone();
    }

    public Integer getNextCheckpointIndex() {
        return nextCheckpointIndex;
    }

    public void start(String worldName, Location startLocation, WorldData data) {
        this.worldName = worldName;
        this.started = true;
        this.startTimeMs = System.currentTimeMillis();
        this.lastCheckpointIndex = 0;
        this.lastCheckpointLocation = startLocation == null ? null : startLocation.clone();
        this.nextCheckpointIndex = data == null ? null : data.getNextCheckpointIndex(0);
    }

    public void reachCheckpoint(int index, Location checkpointLocation, WorldData data) {
        this.lastCheckpointIndex = index;
        this.lastCheckpointLocation = checkpointLocation == null ? null : checkpointLocation.clone();
        this.nextCheckpointIndex = data == null ? null : data.getNextCheckpointIndex(index);
    }

    public void restore(String worldName, long startTimeMs, int lastCheckpointIndex, Location lastCheckpointLocation, WorldData data) {
        this.worldName = worldName;
        this.started = true;
        this.startTimeMs = startTimeMs;
        this.lastCheckpointIndex = lastCheckpointIndex;
        this.lastCheckpointLocation = lastCheckpointLocation == null ? null : lastCheckpointLocation.clone();
        this.nextCheckpointIndex = data == null ? null : data.getNextCheckpointIndex(lastCheckpointIndex);
    }
}
