/* ===================================================================== *
 *  file: org/worldcraft/dominioncraft/nation/Nation.java                *
 *  desc: сущность «Нация» (полная и рабочая)                             *
 * ===================================================================== */
package org.worldcraft.dominioncraft.nation;

import net.minecraft.nbt.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import org.worldcraft.dominioncraft.town.*;

import java.util.*;

/**
 * Данные одной нации — состав городов, ранги игроков, конституция,
 * заявки, выборы, референдумы.
 */
public class Nation {

    /* ───────────────────────── базовые поля ───────────────────────── */

    private final UUID id;
    private String name;
    private GovernmentType government;
    private NationStatus status = NationStatus.CLOSED;

    private UUID capitalTown;
    private UUID leader;

    /* ───────────────────────── коллекции ──────────────────────────── */

    private final Set<UUID> towns = new HashSet<>();
    public final Map<UUID, NationRank> playerRanks = new HashMap<>();

    public final Map<NationRank, EnumSet<NationPermission>> rankPerms =
            new EnumMap<>(NationRank.class);

    private String constitution = "";
    private EnumMap<ConstitutionKey, Boolean> rules = new EnumMap<>(ConstitutionKey.class);

    public final Map<UUID, Application>      applications = new HashMap<>();
    public final Map<UUID, SecessionRequest> secessions   = new HashMap<>();

    public Election   election   = null;
    public Referendum referendum = null;

    private long nextElectionTick = 0;




    /* ───────────────────────── ctor ──────────────────────────────── */
    public Nation(UUID id, String name, GovernmentType gov,
                  UUID capitalTown, UUID leader) {

        this.id          = id;
        this.name        = name;
        this.government  = gov;
        this.capitalTown = capitalTown;
        this.leader      = leader;

        towns.add(capitalTown);
        playerRanks.put(leader, NationRank.LEADER);

        /* ---------- базовые права рангов ---------- */
        rankPerms.put(NationRank.LEADER, EnumSet.allOf(NationPermission.class));
        rankPerms.put(NationRank.MINISTER, EnumSet.of(
                NationPermission.INVITE_TOWN,
                NationPermission.APPROVE_APPLICATION,
                NationPermission.APPROVE_SECESSION,
                NationPermission.MANAGE_STATUS,
                NationPermission.MANAGE_CONSTITUTION,
                NationPermission.VOTE_COUNCIL,
                NationPermission.START_REFERENDUM
        ));
        rankPerms.put(NationRank.REPRESENTATIVE, EnumSet.of(
                NationPermission.VOTE_COUNCIL,
                NationPermission.START_REFERENDUM
        ));
        rankPerms.put(NationRank.CITIZEN, EnumSet.of(
                NationPermission.VOTE_REFERENDUM
        ));
    }

    /* ───────────────────── getters / setters ─────────────────────── */

    public UUID getId()            { return id; }
    public String getName()        { return name; }
    public void   setName(String n){ this.name = n; }

    public GovernmentType getGovernment() { return government; }
    public NationStatus   getStatus()     { return status; }
    public void           setStatus(NationStatus s){ status = s; }

    public UUID getCapitalTown() { return capitalTown; }
    public UUID getLeader()      { return leader; }

    /** Передать лидерство новому игроку. */
    public void setLeader(UUID newLeader) {
        leader = newLeader;
        playerRanks.replaceAll((u, r) ->
                u.equals(newLeader)
                        ? NationRank.LEADER
                        : (r == NationRank.LEADER ? NationRank.CITIZEN : r));
    }
    public class GlobalNews {
        public static void broadcast(MinecraftServer server, String message) {
            server.getPlayerList().getPlayers().forEach(p ->
                    p.sendSystemMessage(Component.literal("§6[Мировые новости]§r " + message)));
        }
    }


    /* ──────────────────── города ──────────────────── */

    public Set<UUID> getTowns()            { return Collections.unmodifiableSet(towns); }

    /** Добавить город; всем его жителям выдать базовый ранг. */
    public void addTown(UUID townId) {
        towns.add(townId);
        Town t = org.worldcraft.dominioncraft.town.TownData.getServerTown(townId);
        if (t != null) t.getMembers().forEach(this::addCitizenIfAbsent);
    }

    /** Удалить город и сбросить ранги его жителей. */
    public void removeTown(UUID townId) {
        towns.remove(townId);
        applications.remove(townId);
        secessions.remove(townId);

        Town t = org.worldcraft.dominioncraft.town.TownData.getServerTown(townId);
        if (t != null) t.getMembers().forEach(playerRanks::remove);
    }

    /* ──────────────────── ранги ───────────────────── */

    public NationRank getRank(UUID player) { return playerRanks.get(player); }

    /** Задать/заменить ранг (null → CITIZEN). */
    public void setRank(UUID player, NationRank rank) {
        playerRanks.put(player, rank == null ? NationRank.CITIZEN : rank);
    }

    /** Авто‑выдать CITIZEN, если игрок ещё не состоит. */
    public void addCitizenIfAbsent(UUID player) {
        playerRanks.putIfAbsent(player, NationRank.CITIZEN);
    }

    /** Проверка прав с учётом авто‑CITIZEN. */
    public boolean hasPermission(UUID player, NationPermission perm) {
        NationRank r = playerRanks.getOrDefault(player, NationRank.CITIZEN);
        if (r == NationRank.LEADER) return true;
        return rankPerms.getOrDefault(r, EnumSet.noneOf(NationPermission.class)).contains(perm);
    }

    /* ────────────────── конституция ───────────────── */

    public String getConstitution() { return constitution; }

    /** вернуть текущие правила (можно изменять прямо в Map) */
    public Map<ConstitutionKey, Boolean> getRules() {
        return rules;          // больше НЕ unmodifiable
    }
    /** установить/обновить флаг */
    public void setRule(ConstitutionKey key, boolean value) {
        rules.put(key, value);
    }

    /** удалить флаг (если нужно) */
    public void removeRule(ConstitutionKey key) {
        rules.remove(key);
    }

    public void setConstitution(String text) {
        this.constitution = text;
        this.rules = ConstitutionUtil.extractRules(text);
    }

    public boolean rule(ConstitutionKey k) { return rules.getOrDefault(k, false); }

    /* ───────────────────── выборы ─────────────────── */

    public Election getElection()            { return election; }
    public void     setElection(Election e)  { this.election = e; }

    public long  getNextElectionTick()       { return nextElectionTick; }
    public void  setNextElectionTick(long t) { this.nextElectionTick = t; }

    /* ───────────────────── утилиты ───────────────── */

    public String getPlayerName(MinecraftServer srv, UUID id) {
        return srv.getProfileCache().get(id)
                .map(com.mojang.authlib.GameProfile::getName)
                .orElse(id.toString());
    }

    /* ================================================================= *
     *                             NBT ‑ save                            *
     * ================================================================= */
    public CompoundTag toNbt() {
        CompoundTag tag = new CompoundTag();

        tag.putUUID("Id",       id);
        tag.putString("Name",   name);
        tag.putString("Gov",    government.name());
        tag.putString("Status", status.name());
        tag.putUUID("Capital",  capitalTown);
        tag.putUUID("Leader",   leader);

        /* towns */
        ListTag tl = new ListTag();
        towns.forEach(t -> tl.add(NbtUtils.createUUID(t)));
        tag.put("Towns", tl);

        /* player → rank */
        ListTag pr = new ListTag();
        playerRanks.forEach((u,r) -> {
            CompoundTag e = new CompoundTag();
            e.putUUID("U", u);
            e.putString("R", r.name());
            pr.add(e);
        });
        tag.put("PlayerRanks", pr);

        /* constitution */
        tag.putString("ConstText", constitution);
        tag.put("ConstRules", ConstitutionUtil.rulesToNbt(rules));

        /* election meta */
        tag.putLong("NextElect", nextElectionTick);
        if (election   != null) tag.put("Election",   saveElection(election));
        if (referendum != null) tag.put("Referendum", saveReferendum(referendum));

        /* applications / secessions */
        ListTag appl = new ListTag();
        applications.values().forEach(a -> appl.add(saveApplication(a)));
        tag.put("Applications", appl);

        ListTag sec = new ListTag();
        secessions.values().forEach(s -> sec.add(saveSecession(s)));
        tag.put("Secessions", sec);

        return tag;
    }

    /* ================================================================= *
     *                             NBT ‑ load                            *
     * ================================================================= */
    public static Nation fromNbt(CompoundTag tag) {

        UUID id     = tag.getUUID("Id");
        String nm   = tag.getString("Name");
        GovernmentType gov = GovernmentType.valueOf(tag.getString("Gov"));
        UUID cap    = tag.getUUID("Capital");
        UUID lead   = tag.getUUID("Leader");

        Nation n = new Nation(id, nm, gov, cap, lead);
        n.status = NationStatus.valueOf(tag.getString("Status"));

        /* towns */
        tag.getList("Towns", Tag.TAG_INT_ARRAY)
                .forEach(t -> n.towns.add(NbtUtils.loadUUID(t)));

        /* ranks */
        tag.getList("PlayerRanks", Tag.TAG_COMPOUND)
                .forEach(r -> {
                    CompoundTag e = (CompoundTag) r;
                    n.playerRanks.put(e.getUUID("U"),
                            NationRank.valueOf(e.getString("R")));
                });

        /* constitution */
        n.constitution = tag.getString("ConstText");
        n.rules        = ConstitutionUtil.rulesFromNbt(
                tag.getList("ConstRules", Tag.TAG_STRING));

        /* election meta */
        if (tag.contains("NextElect"))
            n.nextElectionTick = tag.getLong("NextElect");

        if (tag.contains("Election"))
            n.election = loadElection(tag.getCompound("Election"));
        if (tag.contains("Referendum"))
            n.referendum = loadReferendum(tag.getCompound("Referendum"));

        /* applications */
        tag.getList("Applications", Tag.TAG_COMPOUND)
                .forEach(x -> {
                    Application a = loadApplication((CompoundTag) x);
                    n.applications.put(a.townId, a);
                });

        /* secessions */
        tag.getList("Secessions", Tag.TAG_COMPOUND)
                .forEach(x -> {
                    SecessionRequest s = loadSecession((CompoundTag) x);
                    n.secessions.put(s.townId, s);
                });

        /* гарантируем задержку первых выборов, если республика без кампании */
        if (gov == GovernmentType.REPUBLIC
                && n.election == null
                && n.nextElectionTick == 0) {
            n.nextElectionTick = NationCommands.TICKS_14_DAYS;
        }

        return n;
    }


    /* ================================================================= *
     *         helper‑методы для Election / Referendum / заявок          *
     * ================================================================= */

    /* ---------- Election ---------- */

    private static CompoundTag saveElection(Election e) {
        CompoundTag t = new CompoundTag();
        t.putLong("RegEnd",  e.getRegEndTick());
        t.putLong("VoteEnd", e.getVoteEndTick());

        /* кандидаты */
        ListTag c = new ListTag();
        e.getCandidates().forEach(u -> c.add(NbtUtils.createUUID(u)));
        t.put("Cands", c);

        /* голоса */
        ListTag v = new ListTag();
        e.votes.forEach((voter, cand) -> {
            CompoundTag e2 = new CompoundTag();
            e2.putUUID("V", voter);
            e2.putUUID("C", cand);
            v.add(e2);
        });
        t.put("Votes", v);
        return t;
    }

    private static Election loadElection(CompoundTag t) {
        long regEnd  = t.getLong("RegEnd");
        long voteEnd = t.getLong("VoteEnd");
        long period  = voteEnd - regEnd;              // 24 ч по текущей логике
        long start   = regEnd - period;               // восстановить «start»

        Election e = new Election(start);

        /* кандидаты */
        t.getList("Cands", Tag.TAG_INT_ARRAY)
                .forEach(x -> e.addCandidate(NbtUtils.loadUUID(x)));

        /* голоса */
        t.getList("Votes", Tag.TAG_COMPOUND)
                .forEach(x -> {
                    CompoundTag cv = (CompoundTag) x;
                    e.vote(cv.getUUID("V"), cv.getUUID("C"));
                });
        return e;
    }

    /* ---------- Referendum ---------- */

    private static CompoundTag saveReferendum(Referendum r) {
        CompoundTag t = new CompoundTag();
        t.putString("Q", r.question);
        t.putLong("End", r.endTick);

        ListTag sig = new ListTag();
        r.signatures.forEach(u -> sig.add(NbtUtils.createUUID(u)));
        t.put("Sig", sig);

        ListTag v = new ListTag();
        r.votes.forEach((voter, yes) -> {
            CompoundTag e2 = new CompoundTag();
            e2.putUUID("V", voter);
            e2.putBoolean("Y", yes);
            v.add(e2);
        });
        t.put("Votes", v);
        return t;
    }

    private static Referendum loadReferendum(CompoundTag t) {
        return Referendum.fromNbt(t);
    }



    /* ---------- Application (заявка города) ---------- */

    private static CompoundTag saveApplication(Application a) {
        CompoundTag t = new CompoundTag();
        t.putUUID("Town", a.townId);
        t.putUUID("Req",  a.requester);
        t.putLong("Time", a.created);

        ListTag v = new ListTag();
        a.votes.forEach((u, yes) -> {
            CompoundTag e2 = new CompoundTag();
            e2.putUUID("U", u);
            e2.putBoolean("Y", yes);
            v.add(e2);
        });
        t.put("Votes", v);
        return t;
    }

    private static Application loadApplication(CompoundTag t) {
        Application a = new Application(
                t.getUUID("Town"),
                t.getUUID("Req"),
                t.getLong("Time")
        );
        t.getList("Votes", Tag.TAG_COMPOUND)
                .forEach(x -> {
                    CompoundTag cv = (CompoundTag) x;
                    a.votes.put(cv.getUUID("U"), cv.getBoolean("Y"));
                });
        return a;
    }

    /* ---------- SecessionRequest (заявка на выход) ---------- */

    private static CompoundTag saveSecession(SecessionRequest s) {
        CompoundTag t = new CompoundTag();
        t.putUUID("Town", s.townId);
        t.putUUID("Req",  s.requester);
        t.putLong("Time", s.created);

        ListTag v = new ListTag();
        s.votes.forEach((u, yes) -> {
            CompoundTag e2 = new CompoundTag();
            e2.putUUID("U", u);
            e2.putBoolean("Y", yes);
            v.add(e2);
        });
        t.put("Votes", v);
        return t;
    }

    private static SecessionRequest loadSecession(CompoundTag t) {
        SecessionRequest s = new SecessionRequest(
                t.getUUID("Town"),
                t.getUUID("Req"),
                t.getLong("Time")
        );
        t.getList("Votes", Tag.TAG_COMPOUND)
                .forEach(x -> {
                    CompoundTag cv = (CompoundTag) x;
                    s.votes.put(cv.getUUID("U"), cv.getBoolean("Y"));
                });
        return s;
    }

}
