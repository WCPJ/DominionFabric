package org.worldcraft.dominioncraft.nation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Заявка города на выход из нации.
 */
public final class SecessionRequest {

    public final UUID townId;     // город
    public final UUID requester;  // кто подал
    public final long created;    // время создания (ms)

    /** Голоса (council / referendum). */
    public final Map<UUID, Boolean> votes = new HashMap<>();

    /* ──────────────────────────── ctors ──────────────────────────── */

    /** Новый запрос (текущее время). */
    public SecessionRequest(UUID townId, UUID requester) {
        this(townId, requester, System.currentTimeMillis());
    }

    /** Восстановление из NBT или тестов. */
    public SecessionRequest(UUID townId, UUID requester, long created) {
        this.townId    = townId;
        this.requester = requester;
        this.created   = created;
    }

    /**
     * true, если «за» ≥ threshold (обычно 0.6 == 60 %).
     * @param threshold доля голосов «за»
     */
    public boolean referendumPassed(double threshold) {
        long yes   = votes.values().stream().filter(b -> b).count();
        long total = votes.size();
        return total > 0 && yes >= Math.ceil(total * threshold);
    }
}