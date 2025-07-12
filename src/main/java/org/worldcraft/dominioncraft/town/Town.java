package org.worldcraft.dominioncraft.town;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

/**
 * Данные одного города.
 */
public class Town {

    /* ------------------------------------------------------------------ */
    /*                             базовые поля                            */
    /* ------------------------------------------------------------------ */

    private final UUID id;
    private String     name;
    private final UUID mayor;

    private boolean townPvp = false;
    private boolean townExplosion = false;

    /* ------------------------------------------------------------------ */
    /*                              коллекции                              */
    /* ------------------------------------------------------------------ */

    /** Все участники города. */
    private final Set<UUID> members = new HashSet<>();

    /** Ранг каждого участника. */
    private final Map<UUID, TownRank> ranks = new HashMap<>();

    /** Права, привязанные к каждому рангу. */
    public final Map<TownRank, EnumSet<TownPermission>> rankPerms = new EnumMap<>(TownRank.class);

    /** Все заклеймленные чанки. */
    private final Map<ChunkPos, TownChunk> claims = new HashMap<>();

    /** Активные приглашения (UUID игроков). */
    private final Set<UUID> invites = new HashSet<>();

    public void setTownExplosion(boolean flag) { this.townExplosion = flag; }
    public boolean getTownExplosion() { return townExplosion; }

    public boolean isChunkExplosion(ChunkPos pos) {
        TownChunk tc = claims.get(pos);
        return tc != null && tc.getExplosion() != null ? tc.getExplosion() : townExplosion;
    }

    /* ------------------------------------------------------------------ */
    /*                              конструктор                            */
    /* ------------------------------------------------------------------ */

    public Town(UUID id, String name, UUID mayor) {
        this.id    = id;
        this.name  = name;
        this.mayor = mayor;

        /* мэра сразу добавляем участником с рангом MAYOR */
        addMember(mayor, TownRank.MAYOR);

        /* ---------- базовые права рангов ---------- */
        rankPerms.put(TownRank.MAYOR, EnumSet.allOf(TownPermission.class));
        rankPerms.put(TownRank.ASSISTANT, EnumSet.of(
                TownPermission.BUILD, TownPermission.BREAK,
                TownPermission.CONTAINER, TownPermission.INTERACT,
                TownPermission.FARM, TownPermission.ANIMAL,
                TownPermission.INVITE, TownPermission.KICK,
                TownPermission.MANAGE_CLAIMS, TownPermission.SET_RANK,
                TownPermission.CHUNK_PERM, TownPermission.MANAGE_PVP,
                TownPermission.CHUNK_PVP, TownPermission.DELETE,
                TownPermission.EXPLOSION, TownPermission.CHUNK_EXPLOSION
        ));

        // У MEMBER и RECRUIT по умолчанию нет никаких прав!
        rankPerms.put(TownRank.MEMBER, EnumSet.noneOf(TownPermission.class));
        rankPerms.put(TownRank.RECRUIT, EnumSet.noneOf(TownPermission.class));
    }

    /* ------------------------------------------------------------------ */
    /*                  участники / ранги / приглашения                    */
    /* ------------------------------------------------------------------ */

    /** Добавить игрока с указанным рангом (или обновить ранг). */
    public void addMember(UUID player, TownRank rank) {
        members.add(player);
        ranks.put(player, rank != null ? rank : TownRank.MEMBER);
        invites.remove(player);
    }

    /** Добавить игрока как MEMBER (без явного ранга) */
    public void addMember(UUID player) {
        addMember(player, TownRank.MEMBER);
    }

    /** Удалить игрока из города. */
    public void removeMember(UUID player) {
        members.remove(player);
        ranks.remove(player);
    }

    /** Получить ранг игрока, или null если не состоит в городе. */
    public TownRank getRank(UUID player) {
        return ranks.get(player); // null если игрок не состоит
    }

    /** Изменить ранг уже состоящего участника. */
    public void setRank(UUID player, TownRank rank) {
        if (members.contains(player)) ranks.put(player, rank);
    }

    /** Проверка глобального (рангового) права. */
    public boolean hasPermission(UUID player, TownPermission perm) {
        TownRank rank = getRank(player);
        if (rank == null) return false;
        return rankPerms
                .getOrDefault(rank, EnumSet.noneOf(TownPermission.class))
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

    public void setTownPvp(boolean flag) { this.townPvp = flag; }
    public boolean getTownPvp()          { return townPvp; }

    public boolean isChunkPvp(ChunkPos pos) {
        TownChunk tc = claims.get(pos);
        return tc != null && tc.getPvp() != null ? tc.getPvp() : townPvp;
    }

    /* ------------------------------------------------------------------ */
    /*                              клеймы                                 */
    /* ------------------------------------------------------------------ */

    void claim(ChunkPos pos) { claims.putIfAbsent(pos, new TownChunk(pos)); }
    void putChunk(TownChunk ch) { claims.put(ch.getPos(), ch); }
    void unclaim(ChunkPos pos) { claims.remove(pos); }
    public boolean owns(ChunkPos pos) { return claims.containsKey(pos); }
    public TownChunk chunk(ChunkPos pos) { return claims.get(pos); }
    public int getClaimCount() { return claims.size(); }
    public Collection<TownChunk> allChunks() {
        return Collections.unmodifiableCollection(claims.values());
    }

    /* ------------------------------------------------------------------ */
    /*                         утилитные методы                            */
    /* ------------------------------------------------------------------ */

    public String getMayorName(MinecraftServer srv) {
        return srv.getProfileCache().get(mayor)
                .map(com.mojang.authlib.GameProfile::getName)
                .orElse(mayor.toString());
    }

    /* ------------------------------------------------------------------ */
    /*                                getters                              */
    /* ------------------------------------------------------------------ */

    public UUID getId()           { return id;      }
    public String getName()       { return name;    }
    public UUID getMayor()        { return mayor;   }
    public Set<UUID> getMembers() { return Collections.unmodifiableSet(members); }
}
