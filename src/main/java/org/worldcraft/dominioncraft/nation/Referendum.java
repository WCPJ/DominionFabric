package org.worldcraft.dominioncraft.nation;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.NbtUtils;

/**
 * Общенациональный референдум.
 * Пройдён, если «за» ≥ 60% от всех проголосовавших.
 */
public final class Referendum {

    /** unix‑tick начала */
    public final long startTick;

    /** unix‑tick завершения */
    public final long endTick;

    /** Текст вопроса. */
    public final String question;

    /** true — это референдум о роспуске нации */
    private final boolean dissolutionReferendum;

    /** Подписи для запуска (петиция). */
    public final Set<UUID> signatures = new HashSet<>();

    /** Голосование playerUUID → YES/NO. */
    public final Map<UUID, Boolean> votes = new HashMap<>();

    /** Сколько подписей нужно, чтобы начать голосование. */
    public static final int PETITION_THRESHOLD = 10;

    // Обычный референдум
    public Referendum(String question, long startTick, long endTick) {
        this(question, startTick, endTick, false);
    }

    // Референдум о роспуске нации
    public Referendum(String question, long startTick, long endTick, boolean dissolution) {
        this.question = question;
        this.startTick = startTick;
        this.endTick = endTick;
        this.dissolutionReferendum = dissolution;
    }

    /* ------- петиция ------- */
    public boolean sign(UUID player) { return signatures.add(player); }
    public boolean readyToStart()    { return signatures.size() >= PETITION_THRESHOLD; }

    /* ------- голосование ------- */
    public void vote(UUID voter, boolean yes) { votes.put(voter, yes); }

    /** Прошёл ли референдум? */
    public boolean isPassed() {
        long yes = votes.values().stream().filter(b -> b).count();
        long total = votes.size();
        return total > 0 && yes * 100 / total >= 60;
    }

    /** Это ли роспуск нации? */
    public boolean isDissolutionReferendum() {
        return dissolutionReferendum;
    }

    // ------------------- NBT Serialization --------------------

    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Question", question);
        tag.putLong("StartTick", startTick);
        tag.putLong("EndTick", endTick);
        tag.putBoolean("Dissolution", dissolutionReferendum);

        ListTag sigList = new ListTag();
        for (UUID uuid : signatures) {
            sigList.add(NbtUtils.createUUID(uuid));
        }
        tag.put("Signatures", sigList);

        ListTag vList = new ListTag();
        for (Map.Entry<UUID, Boolean> e : votes.entrySet()) {
            CompoundTag t2 = new CompoundTag();
            t2.putUUID("Voter", e.getKey());
            t2.putBoolean("Yes", e.getValue());
            vList.add(t2);
        }
        tag.put("Votes", vList);

        return tag;
    }

    public static Referendum fromNbt(CompoundTag tag) {
        String question = tag.getString("Question");
        long startTick = tag.getLong("StartTick");
        long endTick = tag.getLong("EndTick");
        boolean dissolution = tag.getBoolean("Dissolution");
        Referendum r = new Referendum(question, startTick, endTick, dissolution);

        ListTag sigList = tag.getList("Signatures", Tag.TAG_INT_ARRAY);
        for (Tag t : sigList) r.signatures.add(NbtUtils.loadUUID(t));

        ListTag vList = tag.getList("Votes", Tag.TAG_COMPOUND);
        for (Tag t : vList) {
            CompoundTag v = (CompoundTag) t;
            r.votes.put(v.getUUID("Voter"), v.getBoolean("Yes"));
        }
        return r;
    }
}
