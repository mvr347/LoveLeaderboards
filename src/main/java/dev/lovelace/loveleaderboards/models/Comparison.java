package dev.lovelace.loveleaderboards.models;

import java.util.UUID;

public record Comparison(
    UUID uuid1,
    String name1,
    PlayerStats stats1,
    UUID uuid2,
    String name2,
    PlayerStats stats2,
    String category
) {
}
