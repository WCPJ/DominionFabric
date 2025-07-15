package org.worldcraft.dominioncraft.town;

import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

/**
 * Хранилище всех городов конкретного мира (level).
 * <p>Сохраняется и загружается через Vanilla {@link SavedData}.</p>
 */
public class TownData extends SavedData {

    /* ------------------------------------------------------------------ */
    /*                            поля и константы                        */
    /* ------------------------------------------------------------------ */

    /** Имя файла на диске (без расширения). */
    private static final String FILE_ID = "dominioncraft_towns";

    /** id города → объект {@link Town}. */
    private final Map<UUID, Town> towns = new HashMap<>();

    /** позиция чанка → id города-владельца. */
    private final Map<ChunkPos, UUID> chunkMap = new HashMap<>();

    /** Максимальное число чанков, которое может иметь один город. */
    private static final int MAX_CLAIMS = 64;

    public Town getTownByName(String name) {
        for (Town t : getTownMap().values()) {
            if (t.getName().equalsIgnoreCase(name)) {
                return t;
            }
        }
        return null;
    }

    /* ------------------------------------------------------------------ */
    /*                             публичный API                          */
    /* ------------------------------------------------------------------ */

    public Town createTown(String name, UUID mayor, ChunkPos spawn) {
        UUID id = UUID.randomUUID();
        Town t  = new Town(id, name, mayor);
        towns.put(id, t);
        claimChunk(t, spawn);
        setDirty();
        return t;
    }

    public void deleteTown(Town t) {
        t.allChunks().forEach(c -> chunkMap.remove(c.getPos()));
        towns.remove(t.getId());
        setDirty();
    }

    public Town getTown(UUID id)                 { return towns.get(id); }

    public Town getTownByChunk(ChunkPos pos)     {
        UUID id = chunkMap.get(pos);
        return id == null ? null : towns.get(id);
    }

    public Map<UUID, Town> getTownMap()          { return towns; }

    public Town getTownOfPlayer(UUID p) {
        for (Town t : towns.values())
            if (t.getMembers().contains(p)) return t;
        return null;
    }

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

    /* ------------------------------------------------------------------ */
    /*                         сериализация в NBT                         */
    /* ------------------------------------------------------------------ */

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag townList = new ListTag();

        for (Town t : towns.values()) {
            CompoundTag tc = new CompoundTag();
            tc.putUUID("Id",   t.getId());
            tc.putString("Name", t.getName());
            tc.putUUID("Mayor",  t.getMayor());
            tc.putBoolean("TownPvP", t.getTownPvp());
            tc.putBoolean("TownExplosion", t.getTownExplosion());
            tc.putBoolean("Open", t.isOpen());

            // Участники + ранги
            ListTag mem = new ListTag();
            for (UUID p : t.getMembers()) {
                CompoundTag m = new CompoundTag();
                m.putUUID("U", p);
                m.putString("R", t.getRank(p).name());
                mem.add(m);
            }
            tc.put("Members", mem);

            // Приглашения
            ListTag inv = new ListTag();
            for (UUID p : t.getInvites()) inv.add(NbtUtils.createUUID(p));
            tc.put("Invites", inv);

            // Клеймы
            ListTag claims = new ListTag();
            for (TownChunk ch : t.allChunks()) claims.add(ch.toNbt());
            tc.put("Claims", claims);

            townList.add(tc);
        }
        tag.put("Towns", townList);
        return tag;
    }

    /* ------------------------------------------------------------------ */
    /*                         десериализация из NBT                      */
    /* ------------------------------------------------------------------ */

    public static TownData load(CompoundTag tag) {
        TownData d = new TownData();

        ListTag townList = tag.getList("Towns", Tag.TAG_COMPOUND);
        for (Tag tt : townList) {
            CompoundTag tc = (CompoundTag) tt;

            UUID   id  = tc.getUUID("Id");
            String nm  = tc.getString("Name");
            UUID   may = tc.getUUID("Mayor");

            Town t = new Town(id, nm, may);
            if (tc.contains("TownPvP")) t.setTownPvp(tc.getBoolean("TownPvP"));
            if (tc.contains("TownExplosion")) t.setTownExplosion(tc.getBoolean("TownExplosion"));
            if (tc.contains("Open")) t.setOpen(tc.getBoolean("Open"));

            // Участники
            for (Tag mm : tc.getList("Members", Tag.TAG_COMPOUND)) {
                CompoundTag m = (CompoundTag) mm;
                t.addMember(m.getUUID("U"), TownRank.valueOf(m.getString("R")));
            }

            // Приглашения
            for (Tag inv : tc.getList("Invites", Tag.TAG_INT_ARRAY)) {
                UUID p = NbtUtils.loadUUID(inv);
                t.addInvite(p);
            }

            // Клеймы
            for (Tag cc : tc.getList("Claims", Tag.TAG_COMPOUND)) {
                TownChunk ch = TownChunk.fromNbt((CompoundTag) cc);
                t.putChunk(ch);
                d.chunkMap.put(ch.getPos(), id);
            }

            d.towns.put(id, t);
        }
        return d;
    }

    /* ------------------------------------------------------------------ */
    /*                               helpers                              */
    /* ------------------------------------------------------------------ */

    /** Получить (или создать) {@link TownData} для данного мира. */
    public static TownData get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(TownData::load, TownData::new, FILE_ID);
    }
}
