package dev.lovelace.loveleaderboards.models;

public record LeaderboardEntry(
    String entityType,
    String entityId,
    String entityName,
    int rank,
    double score,
    long updatedAt
) {
}
