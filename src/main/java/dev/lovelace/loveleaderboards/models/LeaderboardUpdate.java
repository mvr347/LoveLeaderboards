package dev.lovelace.loveleaderboards.models;

public record LeaderboardUpdate(
    String entityId,
    String entityName,
    String entityType,
    String category,
    double score
) {
}
