package dev.lovelace.loveleaderboards.managers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Pure decision of which stored clan rows belong to clans that no longer exist. A row keyed by a
 * clan UUID is judged by that UUID alone: matching by name or tag would let a new clan with the same
 * name keep the dead clan's score in the top. Rows with any other key are legacy and left to the
 * name check in {@link DatabaseManager#isClanActive}.
 */
public final class ClanLiveness {
    private ClanLiveness() {}

    public static boolean isOrphan(String entityId, Set<String> liveIds) {
        if (entityId == null) return false;
        String id = entityId.trim().toLowerCase(Locale.ROOT);
        try {
            UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            return false;
        }
        return !liveIds.contains(id);
    }

    /** Stored ids to delete. Live ids must be lower-case UUID strings. */
    public static List<String> orphans(Collection<String> storedIds, Set<String> liveIds) {
        List<String> result = new ArrayList<>();
        for (String id : storedIds) if (isOrphan(id, liveIds)) result.add(id);
        return result;
    }
}
