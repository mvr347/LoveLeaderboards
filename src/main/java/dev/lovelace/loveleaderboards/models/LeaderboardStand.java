package dev.lovelace.loveleaderboards.models;

import org.bukkit.Location;

import java.util.UUID;

public class LeaderboardStand {
    private final String id;
    private final Location location;
    private final String category;
    private final int position;
    private final String type;
    private String currentPeriod;
    private String npcKey;
    private String npcProvider;
    private UUID hologramEntityUuid;

    public LeaderboardStand(String id, Location location, String category, int position, String type, String currentPeriod, String npcKey, String npcProvider, UUID hologramEntityUuid) {
        this.id = id;
        this.location = location;
        this.category = category;
        this.position = position;
        this.type = type == null ? "all" : type.toLowerCase();
        
        if (currentPeriod == null) {
            this.currentPeriod = this.type.equals("temporary") ? "monthly" : "alltime";
        } else {
            this.currentPeriod = currentPeriod;
        }
        
        this.npcKey = npcKey;
        this.npcProvider = npcKey == null ? null : npcProvider;
        this.hologramEntityUuid = hologramEntityUuid;
    }

    public String getType() {
        return type;
    }

    public String getId() {
        return id;
    }

    public Location getLocation() {
        return location;
    }

    public String getCategory() {
        return category;
    }

    public int getPosition() {
        return position;
    }

    public String getCurrentPeriod() {
        return currentPeriod;
    }

    public void setCurrentPeriod(String currentPeriod) {
        this.currentPeriod = currentPeriod;
    }

    /** NPC key in the provider's own format (Citizens: numeric id as text), or null if the stand has no NPC. */
    public String getNpcKey() {
        return npcKey;
    }

    /** Id of the provider that owns {@link #getNpcKey()}; null if the stand has no NPC. */
    public String getNpcProvider() {
        return npcProvider;
    }

    public boolean hasNpc() {
        return npcKey != null;
    }

    public void setNpc(String npcKey, String npcProvider) {
        this.npcKey = npcKey;
        this.npcProvider = npcKey == null ? null : npcProvider;
    }

    public UUID getHologramEntityUuid() {
        return hologramEntityUuid;
    }

    public void setHologramEntityUuid(UUID hologramEntityUuid) {
        this.hologramEntityUuid = hologramEntityUuid;
    }

    public String cycleNextPeriod() {
        if (type.equals("alltime")) {
            return currentPeriod; // Cannot cycle
        }
        
        switch (currentPeriod.toLowerCase()) {
            case "alltime" -> currentPeriod = "monthly";
            case "monthly" -> currentPeriod = "weekly";
            case "weekly" -> currentPeriod = "yearly";
            case "yearly" -> {
                if (type.equals("temporary")) {
                    currentPeriod = "monthly"; // Skip alltime
                } else {
                    currentPeriod = "alltime";
                }
            }
            default -> {
                if (type.equals("temporary")) {
                    currentPeriod = "monthly";
                } else {
                    currentPeriod = "alltime";
                }
            }
        }
        return currentPeriod;
    }
}
