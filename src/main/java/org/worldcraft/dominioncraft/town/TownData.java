package org.worldcraft.dominioncraft.town;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.worldcraft.dominioncraft.nation.Nation;
import org.worldcraft.dominioncraft.nation.NationData;

import java.util.*;

/**
 * Хранилище всех городов мира (SavedData).
 * Полностью совместимо с Minecraft 1.20.4 (официальные маппинги, Fabric).
 */
public class TownData extends SavedData {

    /* ────────────────────────── константы ────────────────────────── */

    /** Имя NBT‑файла на диске (без расширения). */
    private static final String FILE_ID   = "dominioncraft_towns";
    /** Лимит клеймов для одного города. */
    private static final int    MAX_CLAIMS = 64;

    /* ────────────────────────── коллекции ────────────────────────── */

    /** id → город. */
    private final Map<UUID, Town>     towns    = new HashMap<>();
    /** Чанк → id города‑владельца. */
    private final Map<ChunkPos, UUID> chunkMap = new HashMap<>();

    private ServerLevel cachedLevel = null; // Чтобы получать уровень в методах удаления

    /* ────────────────────────── конструкторы ─────────────────────── */

    /** Пустой — нужен, когда файл ещё не создан. */
    public TownData() {}

    /* ────────────────────────── API (основное) ───────────────────── */

    public Town getTownByName(String name) {
        for (Town t : towns.values())
            if (t.getName().equalsIgnoreCase(name)) return t;
        return null;
    }
    public Town getTown(UUID id)                  { return towns.get(id); }
    public Map<UUID, Town> getTownMap()           { return towns; }

    public Town getTownOfPlayer(UUID player) {
        for (Town t : towns.values())
            if (t.getMembers().contains(player)) return t;
        return null;
    }
    public Town getTownByChunk(ChunkPos pos) {
        UUID id = chunkMap.get(pos);
        return id == null ? null : towns.get(id);
    }
    public static Town getServerTown(UUID townId) {
        net.minecraft.server.MinecraftServer srv =
                net.fabricmc.loader.api.FabricLoader.getInstance()
                        .getGameInstance() instanceof net.minecraft.server.MinecraftServer ms ? ms : null;
        if (srv == null) return null;

        for (net.minecraft.server.level.ServerLevel lvl : srv.getAllLevels()) {
            Town t = TownData.get(lvl).getTown(townId);
            if (t != null) return t;
        }
        return null;
    }

    /* ── создание / удаление ── */
    public Town createTown(String name, UUID mayor, ChunkPos spawn) {
        Town t = new Town(UUID.randomUUID(), name, mayor);
        towns.put(t.getId(), t);
        claimChunk(t, spawn);
        setDirty();
        return t;
    }
    public void deleteTown(Town t, ServerLevel level) {
        // Сначала удалить из нации, если есть
        if (t.getNation() != null) {
            NationData nd = NationData.get(level);
            Nation nation = nd.get(t.getNation());

            if (nation != null) {
                nation.removeTown(t.getId());
                nd.setDirty();
            }
            t.setNation(null);
        }
        t.allChunks().forEach(c -> chunkMap.remove(c.getPos()));
        towns.remove(t.getId());
        setDirty();
    }
    // Старый вариант для обратной совместимости, если нет доступа к уровню (НЕ РЕКОМЕНДУЮ использовать)
    public void deleteTown(Town t) {
        t.allChunks().forEach(c -> chunkMap.remove(c.getPos()));
        towns.remove(t.getId());
        setDirty();
    }

    /* ── claim / unclaim ── */
    public boolean claimChunk(Town t, ChunkPos pos) {
        if (t.getClaimCount() >= MAX_CLAIMS) return false;
        if (chunkMap.containsKey(pos))        return false;
        t.claim(pos);
        chunkMap.put(pos, t.getId());
        setDirty();
        return true;
    }
    public boolean unclaimChunk(Town t, ChunkPos pos) {
        if (!t.owns(pos)) return false;
        t.unclaim(pos);
        chunkMap.remove(pos);
        setDirty();
        return true;
    }

    /* ────────────────────────── SAVE → NBT ───────────────────────── */

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag townList = new ListTag();

        for (Town t : towns.values()) {
            CompoundTag tc = new CompoundTag();
            tc.putUUID("Id",   t.getId());
            tc.putString("Name", t.getName());
            tc.putUUID("Mayor",  t.getMayor());
            tc.putBoolean("TownPvP",       t.getTownPvp());
            tc.putBoolean("TownExplosion", t.getTownExplosion());
            tc.putBoolean("Open",          t.isOpen());

            // === Сохраняем нацию (если есть) ===
            if (t.getNation() != null)
                tc.putUUID("Nation", t.getNation());

            /* участники + ранги */
            ListTag members = new ListTag();
            for (UUID p : t.getMembers()) {
                CompoundTag m = new CompoundTag();
                m.putUUID("U", p);
                m.putString("R", t.getRank(p).name());
                members.add(m);
            }
            tc.put("Members", members);

            /* приглашения */
            ListTag invites = new ListTag();
            for (UUID p : t.getInvites()) invites.add(NbtUtils.createUUID(p));
            tc.put("Invites", invites);

            /* клеймы */
            ListTag claims = new ListTag();
            for (TownChunk ch : t.allChunks()) claims.add(ch.toNbt());
            tc.put("Claims", claims);

            townList.add(tc);
        }
        tag.put("Towns", townList);
        return tag;
    }

    /* ────────────────────────── LOAD ← NBT ───────────────────────── */

    public static TownData load(CompoundTag tag) {
        TownData d = new TownData();

        for (Tag tt : tag.getList("Towns", Tag.TAG_COMPOUND)) {
            CompoundTag tc = (CompoundTag) tt;

            UUID   id  = tc.getUUID("Id");
            Town t = new Town(id, tc.getString("Name"), tc.getUUID("Mayor"));

            if (tc.contains("TownPvP"))       t.setTownPvp(tc.getBoolean("TownPvP"));
            if (tc.contains("TownExplosion")) t.setTownExplosion(tc.getBoolean("TownExplosion"));
            if (tc.contains("Open"))          t.setOpen(tc.getBoolean("Open"));

            // === Грузим нацию (если есть) ===
            if (tc.contains("Nation")) t.setNation(tc.getUUID("Nation"));

            /* участники */
            for (Tag mm : tc.getList("Members", Tag.TAG_COMPOUND)) {
                CompoundTag m = (CompoundTag) mm;
                t.addMember(m.getUUID("U"), TownRank.valueOf(m.getString("R")));
            }
            /* инвайты */
            for (Tag inv : tc.getList("Invites", Tag.TAG_INT_ARRAY))
                t.addInvite(NbtUtils.loadUUID(inv));

            /* клеймы */
            for (Tag cc : tc.getList("Claims", Tag.TAG_COMPOUND)) {
                TownChunk ch = TownChunk.fromNbt((CompoundTag) cc);
                t.putChunk(ch);
                d.chunkMap.put(ch.getPos(), id);
            }

            d.towns.put(id, t);
        }
        return d;
    }

    /* ─────────────────── accessor для мира ─────────────────── */

    /**
     * Получить (или создать) экземпляр TownData для данного {@link ServerLevel}.
     */
    public static TownData get(ServerLevel level) {
        // порядок: (loadFn, constructorFn, fileId)
        return level.getDataStorage()
                .computeIfAbsent(TownData::load, TownData::new, FILE_ID);
    }

}
