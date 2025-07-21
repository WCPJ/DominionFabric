package org.worldcraft.dominioncraft.nation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * SavedData‑хранилище всех наций мира.
 *
 * <p>Экономика и дипломатия добавятся позже; сейчас только
 * состав, статус и конституция.</p>
 */
public class NationData extends SavedData {

    private static final String FILE_ID = "dominioncraft_nations";

    private final Map<UUID, Nation> nations = new HashMap<>();

    /* ───────── CRUD ───────── */

    public Nation createNation(String name, GovernmentType gov,
                               UUID capitalTown, UUID leader) {
        Nation n = new Nation(UUID.randomUUID(), name, gov, capitalTown, leader);
        nations.put(n.getId(), n);
        setDirty();
        return n;
    }

    public void deleteNation(Nation n) {
        nations.remove(n.getId());
        setDirty();
    }

    public Nation get(UUID id)               { return nations.get(id); }
    public Nation byName(String name) {
        for (Nation n : nations.values())
            if (n.getName().equalsIgnoreCase(name)) return n;
        return null;
    }
    public Collection<Nation> all()   { return nations.values(); }

    /* ───────── SAVE ───────── */

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (Nation n : nations.values())
            list.add(n.toNbt());
        tag.put("Nations", list);
        return tag;
    }

    /* ───────── LOAD ───────── */

    public static NationData load(CompoundTag tag) {
        NationData d = new NationData();
        for (Tag t : tag.getList("Nations", Tag.TAG_COMPOUND)) {
            Nation n = Nation.fromNbt((CompoundTag) t);
            d.nations.put(n.getId(), n);
        }
        return d;
    }

    /* ───────── accessor ───────── */

    public static NationData get(ServerLevel level) {
        return level.getDataStorage()
                .computeIfAbsent(NationData::load, NationData::new, FILE_ID);
    }

}
