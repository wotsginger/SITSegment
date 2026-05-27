package club.sitmc.sitSegment;

import java.util.UUID;
import org.bukkit.Location;

public class PkwSession {
    private final UUID playerId;
    private String worldName;
    private boolean started;
    private long startTimeMs;
    private int lastRecordIndex; // 0 = start, 1..3 = record lines
    private Location respawnLocation;
    private boolean reachedEndMain;

    public PkwSession(UUID playerId) {
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

    public int getLastRecordIndex() {
        return lastRecordIndex;
    }

    public Location getRespawnLocation() {
        return respawnLocation == null ? null : respawnLocation.clone();
    }

    public boolean hasReachedEndMain() {
        return reachedEndMain;
    }

    public void ensureWorld(String worldName) {
        if (this.worldName == null) {
            this.worldName = worldName;
        }
    }

    public void reset(String worldName, Location startRespawn) {
        this.worldName = worldName;
        this.started = false;
        this.startTimeMs = 0L;
        this.lastRecordIndex = 0;
        this.respawnLocation = startRespawn == null ? null : startRespawn.clone();
        this.reachedEndMain = false;
    }

    public void startTiming() {
        this.started = true;
        this.startTimeMs = System.currentTimeMillis();
    }

    public void reachRecord(int index, Location respawn) {
        this.lastRecordIndex = index;
        this.respawnLocation = respawn == null ? null : respawn.clone();
    }

    public void reachEndMain(Location respawn) {
        this.reachedEndMain = true;
        this.respawnLocation = respawn == null ? null : respawn.clone();
    }
}
