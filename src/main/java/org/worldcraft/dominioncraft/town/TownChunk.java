package org.worldcraft.dominioncraft.town;

import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

/**
 * Данные о клейм-чанке.
 *
 * <p>Содержит:</p>
 * <ul>
 *   <li>персональные override-права игроков ({@link #playerPerms});</li>
 *   <li>локальный PvP-флаг (может переопределять городской);</li>
 *   <li>локальный Explosion-флаг (может переопределять городской);</li>
 *   <li>координаты чанка ({@link #pos}).</li>
 * </ul>
 */
public class TownChunk {

    /* ------------------------------------------------------------------ */
    /*                              поля                                   */
    /* ------------------------------------------------------------------ */

    /** Координаты чанка. */
    private final ChunkPos pos;

    /** Override флаг для взрывов (null — наследует город, true/false — override). */
    private Boolean explosionOverride = null;

    /** PvP-override: {@code true} / {@code false} / {@code null} (наследует город). */
    private Boolean pvpOverride = null;

    /** Персональные права игроков на этом чанке. */
    private final Map<UUID, EnumSet<TownPermission>> playerPerms = new HashMap<>();

    /* ------------------------------------------------------------------ */
    /*                            конструктор                              */
    /* ------------------------------------------------------------------ */

    public TownChunk(ChunkPos pos) {
        this.pos = pos;
    }

    /* ------------------------------------------------------------------ */
    /*                           EXPLOSION-флаг                            */
    /* ------------------------------------------------------------------ */

    /**
     * Установить локальный флаг для взрывов (null — сброс, наследовать город).
     */
    public void setExplosion(Boolean flag) { this.explosionOverride = flag; }

    /** @return локальный флаг для взрывов; null — наследует город. */
    public Boolean getExplosion() { return explosionOverride; }

    /* ------------------------------------------------------------------ */
    /*                           PvP-флаг                                  */
    /* ------------------------------------------------------------------ */

    /**
     * Установить локальный PvP-флаг.
     *
     * @param flag {@code true}/{@code false} — задать явно, {@code null} — сброс (наследовать город)
     */
    public void setPvp(Boolean flag) {
        this.pvpOverride = flag;
    }

    /** @return локальный PvP-флаг; {@code null}, если наследует город. */
    public Boolean getPvp() {
        return pvpOverride;
    }

    /* ------------------------------------------------------------------ */
    /*                     персональные override-права                     */
    /* ------------------------------------------------------------------ */

    /**
     * Выдать или забрать конкретное право игроку на этом чанке.
     *
     * @param player UUID игрока
     * @param perm   право
     * @param value  {@code true} — выдать, {@code false} — забрать
     */
    public void setPlayerPerm(UUID player, TownPermission perm, boolean value) {
        playerPerms.computeIfAbsent(player, k -> EnumSet.noneOf(TownPermission.class));
        if (value) playerPerms.get(player).add(perm);
        else       playerPerms.get(player).remove(perm);
    }

    /**
     * Проверка, есть ли у игрока override-право на этом чанке.
     *
     * @param player UUID игрока
     * @param perm   право
     * @return {@code true}, если право выдано индивидуально
     */
    public boolean playerHas(UUID player, TownPermission perm) {
        return playerPerms
                .getOrDefault(player, EnumSet.noneOf(TownPermission.class))
                .contains(perm);
    }

    /* ------------------------------------------------------------------ */
    /*                      сериализация / десериализация                 */
    /* ------------------------------------------------------------------ */

    /** Сохранить данные чанка в NBT. */
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", pos.x);
        tag.putInt("Z", pos.z);
        if (pvpOverride != null) tag.putBoolean("PvP", pvpOverride);
        if (explosionOverride != null) tag.putBoolean("Explosion", explosionOverride);

        ListTag list = new ListTag();
        for (var entry : playerPerms.entrySet()) {
            CompoundTag p = new CompoundTag();
            p.putUUID("Player", entry.getKey());

            ListTag perms = new ListTag();
            for (TownPermission tp : entry.getValue())
                perms.add(StringTag.valueOf(tp.name()));
            p.put("Perms", perms);

            list.add(p);
        }
        tag.put("PlayerPerms", list);
        return tag;
    }

    /** Загрузить данные чанка из NBT. */
    public static TownChunk fromNbt(CompoundTag tag) {
        TownChunk tc = new TownChunk(new ChunkPos(tag.getInt("X"), tag.getInt("Z")));
        if (tag.contains("PvP")) tc.pvpOverride = tag.getBoolean("PvP");
        if (tag.contains("Explosion")) tc.explosionOverride = tag.getBoolean("Explosion");

        ListTag list = tag.getList("PlayerPerms", Tag.TAG_COMPOUND);
        for (Tag t : list) {
            CompoundTag p = (CompoundTag) t;
            UUID id = p.getUUID("Player");

            EnumSet<TownPermission> set = EnumSet.noneOf(TownPermission.class);
            for (Tag s : p.getList("Perms", Tag.TAG_STRING))
                set.add(TownPermission.valueOf(s.getAsString()));

            tc.playerPerms.put(id, set);
        }
        return tc;
    }

    /* ------------------------------------------------------------------ */
    /*                               getter                                */
    /* ------------------------------------------------------------------ */

    /** @return координаты чанка. */
    public ChunkPos getPos() {
        return pos;
    }
}
