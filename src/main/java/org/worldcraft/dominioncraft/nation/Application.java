package org.worldcraft.dominioncraft.nation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Заявка города на вступление в нацию.
 */
public final class Application {

    /** Сколько миллисекунд заявка живёт (10 мин). */
    public static final long TIMEOUT_MS = 10 * 60 * 1000L;

    public final UUID townId;      // кандидат
    public final UUID requester;   // кто подал
    public final long created;     // время создания (ms)

    /** Голоса правителей онлайн <playerUUID, approve>. */
    public final Map<UUID, Boolean> votes = new HashMap<>();

    /* ──────────────────────────── ctors ──────────────────────────── */

    /** Новый запрос (текущее время ставится автоматически). */
    public Application(UUID townId, UUID requester) {
        this(townId, requester, System.currentTimeMillis());
    }

    /** Восстановление из NBT или тестов. */
    public Application(UUID townId, UUID requester, long created) {
        this.townId    = townId;
        this.requester = requester;
        this.created   = created;
    }

    /* ───────────────────────── helpers ───────────────────────────── */

    /** Сколько осталось до тайм‑аута (ms). */
    public long millisLeft() {
        return Math.max(0, TIMEOUT_MS - (System.currentTimeMillis() - created));
    }

    /** true, если > 50 % проголосовавших «за». */
    public boolean approved() {
        long yes   = votes.values().stream().filter(b -> b).count();
        long total = votes.size();
        return total > 0 && yes * 2 > total;
    }

    /** true, если все голоса — «против». */
    public boolean rejected() {
        return votes.size() > 0 && votes.values().stream().noneMatch(b -> b);
    }
}
