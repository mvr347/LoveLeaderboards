package dev.lovelace.loveleaderboards.managers;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ClanLivenessTest {
    private static final String ALIVE = UUID.randomUUID().toString();
    private static final String DEAD = UUID.randomUUID().toString();

    @Test
    void deadClanIdIsOrphanLiveOneIsNot() {
        assertTrue(ClanLiveness.isOrphan(DEAD, Set.of(ALIVE)));
        assertFalse(ClanLiveness.isOrphan(ALIVE, Set.of(ALIVE)));
    }

    @Test
    void upperCaseStoredIdMatchesLowerCaseLiveId() {
        assertFalse(ClanLiveness.isOrphan(ALIVE.toUpperCase(), Set.of(ALIVE)));
    }

    @Test
    void legacyKeysThatAreNotUuidsAreNeverTouched() {
        assertFalse(ClanLiveness.isOrphan("Lovelace", Set.of(ALIVE)));
        assertFalse(ClanLiveness.isOrphan(null, Set.of(ALIVE)));
    }

    @Test
    void orphansKeepsOnlyDeadUuids() {
        assertEquals(List.of(DEAD), ClanLiveness.orphans(List.of(ALIVE, DEAD, "tag"), Set.of(ALIVE)));
    }
}
