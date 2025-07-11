package org.worldcraft.dominioncraft.town;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

/**
 * Данные одного города.
 *
 * <p>Содержит:</p>
 * <ul>
 *   <li>участников, их {@link TownRank ранги};</li>
 *   <li>клейм-чанки и локальные override-права;</li>
 *   <li>глобальный и локальные PvP-флаги;</li>
 *   <li>активные приглашения.</li>
 * </ul>
 */
public class Town {

    /* ------------------------------------------------------------------ */
    /*                            базовые поля                             */
    /* ------------------------------------------------------------------ */

    private final UUID id;
    private String     name;
    private final UUID mayor;

    /** Включён ли PvP во всём городе по умолчанию. */
    private boolean townPvp = false;

    /* ------------------------------------------------------------------ */
    /*                             коллекции                               */
    /* ------------------------------------------------------------------ */

    /** Все участники города. */
    private final Set<UUID> members = new HashSet<>();

    /** Ранг каждого участника. */
    private final Map<UUID, TownRank> ranks = new HashMap<>();

    /** Права, привязанные к каждому рангу. */
    private final Map<TownRank, EnumSet<TownPermission>> rankPerms =
            new EnumMap<>(TownRank.class);

    /** Все заклеймленные чанки. */
    private final Map<ChunkPos, TownChunk> claims = new HashMap<>();

    /** Активные приглашения (UUID игроков). */
    private final Set<UUID> invites = new HashSet<>();

    /* ------------------------------------------------------------------ */
    /*                              конструктор                            */
    /* ------------------------------------------------------------------ */

    public Town(UUID id, String name, UUID mayor) {
        this.id   = id;
        this.name = name;
        this.mayor= mayor;

        /* мэра сразу добавляем участником с рангом MAYOR */
        addMember(mayor, TownRank.MAYOR);

        /* ---------- базовые права рангов ---------- */
        rankPerms.put(TownRank.MAYOR, EnumSet.allOf(TownPermission.class));

        rankPerms.put(TownRank.ASSISTANT, EnumSet.of(
                TownPermission.BUILD, TownPermission.BREAK,
                TownPermission.CONTAINER, TownPermission.INTERACT,
                TownPermission.INVITE, TownPermission.KICK,
                TownPermission.MANAGE_CLAIMS, TownPermission.SET_RANK,
                TownPermission.CHUNK_PERM, TownPermission.MANAGE_PVP,
                TownPermission.CHUNK_PVP, TownPermission.DELETE));

        rankPerms.put(TownRank.MEMBER, EnumSet.of(
                TownPermission.BUILD, TownPermission.BREAK,
                TownPermission.CONTAINER, TownPermission.INTERACT));

        rankPerms.put(TownRank.RECRUIT, EnumSet.of(
                TownPermission.INTERACT));
    }

    /* ------------------------------------------------------------------ */
    /*                     участники / ранги / приглашения                */
    /* ------------------------------------------------------------------ */

    /** Добавить игрока с указанным рангом (или обновить ранг). */
    public void addMember(UUID player, TownRank rank) {
        members.add(player);
        ranks.put(player, rank);
        invites.remove(player);
    }

    /** Удалить игрока из города. */
    public void removeMember(UUID player) {
        members.remove(player);
        ranks.remove(player);
    }

    /** Получить ранг игрока ({@code RECRUIT}, если не найден). */
    public TownRank getRank(UUID player) {
        return ranks.getOrDefault(player, TownRank.RECRUIT);
    }

    /** Изменить ранг уже состоящего участника. */
    public void setRank(UUID player, TownRank rank) {
        if (members.contains(player)) ranks.put(player, rank);
    }

    /** Проверка глобального (рангового) права. */
    public boolean hasPermission(UUID player, TownPermission perm) {
        return rankPerms
                .getOrDefault(getRank(player), EnumSet.noneOf(TownPermission.class))
                .contains(perm);
    }

    /* ---------- приглашения ---------- */

    public void addInvite(UUID player)        { invites.add(player); }
    public void removeInvite(UUID player)     { invites.remove(player); }
    public boolean hasInvite(UUID player)     { return invites.contains(player); }
    public Set<UUID> getInvites()             { return invites; }

    /* ------------------------------------------------------------------ */
    /*                             PvP-флаги                               */
    /* ------------------------------------------------------------------ */

    /** Включить или выключить PvP во всём городе. */
    public void setTownPvp(boolean flag) { this.townPvp = flag; }

    /** @return глобальный PvP-флаг города. */
    public boolean getTownPvp()          { return townPvp; }

    /**
     * Итоговый PvP-флаг указанного чанка
     * (учитывает {@link TownChunk#getPvp() локальный override},
     * если тот не {@code null}).
     */
    public boolean isChunkPvp(ChunkPos pos) {
        TownChunk tc = claims.get(pos);
        return tc != null && tc.getPvp() != null ? tc.getPvp()
                : townPvp;
    }

    /* ------------------------------------------------------------------ */
    /*                              клеймы                                 */
    /* ------------------------------------------------------------------ */

    /** Заклеймить чанк (вызов только из {@link TownData}). */
    void claim(ChunkPos pos) {
        claims.putIfAbsent(pos, new TownChunk(pos));
    }

    /** Используется при загрузке из NBT — сразу кладёт готовый объект. */
    void putChunk(TownChunk ch) {
        claims.put(ch.getPos(), ch);
    }

    /** Расклеймить чанк (вызов только из {@link TownData}). */
    void unclaim(ChunkPos pos) {
        claims.remove(pos);
    }

    /** @return владеет ли город указанным чанком. */
    public boolean owns(ChunkPos pos) {
        return claims.containsKey(pos);
    }

    /** Получить объект {@link TownChunk} по координатам (или {@code null}). */
    public TownChunk chunk(ChunkPos pos) {
        return claims.get(pos);
    }

    /** @return общее число клеймов города. */
    public int getClaimCount() {
        return claims.size();
    }

    /** @return неизменяемая коллекция всех чанков. */
    public Collection<TownChunk> allChunks() {
        return Collections.unmodifiableCollection(claims.values());
    }

    /* ------------------------------------------------------------------ */
    /*                         утилитные методы                            */
    /* ------------------------------------------------------------------ */

    /**
     * Получить имя мэра из кеша профилей; если профиль ещё не загружен —
     * вернуть строковое представление UUID.
     */
    public String getMayorName(MinecraftServer srv) {
        return srv.getProfileCache().get(mayor)
                .map(com.mojang.authlib.GameProfile::getName)
                .orElse(mayor.toString());
    }

    /* ------------------------------------------------------------------ */
    /*                               getters                               */
    /* ------------------------------------------------------------------ */

    public UUID getId()           { return id; }
    public String getName()       { return name; }
    public UUID getMayor()        { return mayor; }
    public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }
}
