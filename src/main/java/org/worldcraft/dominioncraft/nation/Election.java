package org.worldcraft.dominioncraft.nation;

import java.util.*;

/**
 * Выборы президента (для республик).
 */
public class Election {

    public enum Phase { REGISTRATION, VOTING, FINISHED }

    /* ─────────────────────────── поля ─────────────────────────── */

    private final long regEndTick;   // конец регистрации
    private final long voteEndTick;  // конец голосования

    private final Set<UUID> candidates = new HashSet<>();
    public final Map<UUID, UUID> votes = new HashMap<>(); // voter → candidate

    private Phase phase = Phase.REGISTRATION;

    /* ───────────────────────── ctor ───────────────────────────── */

    /** Создание новой кампании (now — текущий tick). */
    public Election(long now) {
        this.regEndTick  = now + 20L * 60 * 60 * 24;           // +24 ч
        this.voteEndTick = regEndTick + 20L * 60 * 60 * 24;    // +24 ч
    }

    /* ─────────────────────── getters ──────────────────────────── */

    public Phase getPhase()        { return phase;        }
    public long  getRegEndTick()   { return regEndTick;   }
    public long  getVoteEndTick()  { return voteEndTick;  }
    public Set<UUID> getCandidates() { return candidates; }

    /* ───────────────────── public API ─────────────────────────── */

    /** Регистрация кандидата (только REGISTRATION). */
    public boolean addCandidate(UUID player) {
        return phase == Phase.REGISTRATION && candidates.add(player);
    }

    /** Голос игрока (только VOTING). */
    public boolean vote(UUID voter, UUID candidate) {
        if (phase != Phase.VOTING || !candidates.contains(candidate)) return false;
        votes.put(voter, candidate);
        return true;
    }

    /** Победитель (null — если ничья/нет голосов). */
    public UUID getWinner() {
        return votes.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(k -> k,
                        java.util.stream.Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    /* ───────────────────────── tick ───────────────────────────── */

    /** Вызывается из NationTimers раз в tick. */
    public void tick(long now) {
        if (phase == Phase.REGISTRATION && now >= regEndTick) phase = Phase.VOTING;
        if (phase == Phase.VOTING       && now >= voteEndTick) phase = Phase.FINISHED;
    }
}
