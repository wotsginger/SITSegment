package club.sitmc.sitSegment;

import java.util.UUID;

public class RecordEntry {
    private final UUID playerId;
    private String name;
    private long timeMs;

    public RecordEntry(UUID playerId, String name, long timeMs) {
        this.playerId = playerId;
        this.name = name;
        this.timeMs = timeMs;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getName() {
        return name;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public void setName(String name) {
        this.name = name;
    }
}
