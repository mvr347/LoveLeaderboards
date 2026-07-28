package dev.lovelace.loveleaderboards.models;

import java.util.UUID;

public record PlayerStats(
    UUID uuid,
    String playerName,
    int rank,
    double score
) {
}
