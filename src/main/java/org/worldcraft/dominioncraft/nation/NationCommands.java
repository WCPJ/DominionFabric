/* ===================================================================== *
 *  file: org/worldcraft/dominioncraft/nation/NationCommands.java        *
 *  одномодульная, самодостаточная версия (без недостающих ссылок)       *
 * ===================================================================== */
package org.worldcraft.dominioncraft.nation;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.worldcraft.dominioncraft.NewsUtil;
import org.worldcraft.dominioncraft.town.Town;
import org.worldcraft.dominioncraft.town.TownData;

import java.util.*;
import java.util.stream.Collectors;

import static org.worldcraft.dominioncraft.NewsUtil.broadcastNation;

/* ---------------------------------------------- */
/*  ТИК‑константы здесь, чтобы не ссылаться       */
/*  на TICKS_XX из других классов                 */
/* ---------------------------------------------- */
@SuppressWarnings("unused")
public final class NationCommands {

    public static final long TICKS_24_HOURS  = 24L * 60 * 60 * 20; // 172 800 t
    public static final long TICKS_14_DAYS   = 14L * 24 * 60 * 60 * 20; // 2 419 200 t
    private static final Map<UUID, Long> nationDeleteConfirm = new HashMap<>();
    private static final long CONFIRM_TIMEOUT_TICKS = 30 * 20;

    /* ================================================================= */
    /*                      REGISTRATION (Fabric)                        */
    /* ================================================================= */
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(root());
    }

    /* ================================================================= */
    /*                           COMMAND TREE                            */
    /* ================================================================= */
    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("nation")
                .then(cmdCreate())
                .then(cmdDelete())
                .then(cmdOpen())
                .then(cmdInfo())
                .then(cmdJoin())
                .then(cmdInvite())
                .then(cmdInviteAccept())
                .then(cmdRequests())
                .then(cmdKick())
                .then(cmdLeave())
                .then(cmdConstitution())
                .then(cmdRank())
                .then(cmdElection())
                .then(cmdApprove())
                .then(cmdDeny())
                .then(cmdReferendum())
                .then(cmdList())
                .then(cmdInfoOther());


        // (выборы / референдумы можно вернуть позже, сейчас убрано
        //  из‑за отсутствия Election API)
    }

    /* -------------------- basic builders -------------------- */
    private static LiteralArgumentBuilder<CommandSourceStack> cmdCreate() {
        return Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                        .then(Commands.literal("monarchy")
                                .executes(c -> create(c, GovernmentType.MONARCHY)))
                        .then(Commands.literal("republic")
                                .executes(c -> create(c, GovernmentType.REPUBLIC))));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> cmdElection() {
        return Commands.literal("election")
                .then(Commands.literal("candidacy")
                        .executes(NationCommands::electionCandidacy))
                .then(Commands.literal("vote")
                        .then(Commands.argument("candidate", StringArgumentType.word())
                                .suggests(CANDIDATE_SUGGEST)
                                .executes(NationCommands::electionVote)))
                .then(Commands.literal("info")
                        .executes(NationCommands::electionInfo));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> cmdReferendum() {
        return Commands.literal("referendum")
                .then(Commands.literal("start")
                        .then(Commands.argument("question", StringArgumentType.greedyString())
                                .executes(NationCommands::referendumStart)))
                .then(Commands.literal("vote")
                        .then(Commands.argument("yes", BoolArgumentType.bool())
                                .executes(NationCommands::referendumVote)))
                .then(Commands.literal("info")
                        .executes(NationCommands::referendumInfo));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> cmdList() {
        return Commands.literal("list")
                .executes(NationCommands::listNations);
    }
    private static LiteralArgumentBuilder<CommandSourceStack> cmdInfoOther() {
        return Commands.literal("info")
                .then(Commands.argument("nation", StringArgumentType.word())
                        .suggests(NATION_SUGGEST)
                        .executes(NationCommands::infoOther));
    }



    private static LiteralArgumentBuilder<CommandSourceStack> cmdDelete() {
        return Commands.literal("delete").executes(NationCommands::deleteNation);
    }
    private static LiteralArgumentBuilder<CommandSourceStack> cmdOpen() {
        return Commands.literal("open")
                .then(Commands.argument("flag", BoolArgumentType.bool())
                        .executes(c -> setStatus(c,
                                BoolArgumentType.getBool(c,"flag")?
                                        NationStatus.OPEN : NationStatus.CLOSED)));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> cmdInfo() {
        return Commands.literal("info").executes(NationCommands::info);
    }

    /* -------------------- join / invite --------------------- */
    private static LiteralArgumentBuilder<CommandSourceStack> cmdJoin() {
        return Commands.literal("join")
                .then(Commands.argument("nation", StringArgumentType.word())
                        .suggests(NATION_SUGGEST)
                        .executes(NationCommands::join));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> cmdInvite() {
        return Commands.literal("invite")
                .then(Commands.argument("town", StringArgumentType.word())
                        .suggests(TOWN_NO_NATION_SUGGEST)
                        .executes(NationCommands::inviteTown));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> cmdInviteAccept() {
        return Commands.literal("inviteaccept")
                .then(Commands.argument("town", StringArgumentType.word())
                        .executes(NationCommands::inviteAccept));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> cmdRequests() {
        return Commands.literal("requests").executes(NationCommands::showRequests);
    }

    /* -------------------- kick / leave ---------------------- */
    private static LiteralArgumentBuilder<CommandSourceStack> cmdKick() {
        return Commands.literal("kick")
                .then(Commands.argument("town", StringArgumentType.word())
                        .suggests(TOWN_IN_MY_NATION_SUGGEST)
                        .executes(NationCommands::kickTown));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> cmdLeave() {
        return Commands.literal("leave").executes(NationCommands::leave);
    }

    /* -------------------- constitution ---------------------- */
    private static LiteralArgumentBuilder<CommandSourceStack> cmdConstitution() {
        return Commands.literal("constitution")
                .then(Commands.literal("view").executes(NationCommands::constitutionView))
                .then(Commands.literal("rule")
                        .then(Commands.literal("add")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.word())
                                                .executes(c -> ruleEdit(c,true)))))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("key", StringArgumentType.word())
                                        .executes(c -> ruleEdit(c,false))))
                );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cmdApprove() {
        return Commands.literal("approve")
                .then(Commands.argument("town", StringArgumentType.word())
                        .suggests(TOWN_REQUEST_SUGGEST)
                        .executes(NationCommands::approveRequest));
    }
    private static LiteralArgumentBuilder<CommandSourceStack> cmdDeny() {
        return Commands.literal("deny")
                .then(Commands.argument("town", StringArgumentType.word())
                        .suggests(TOWN_REQUEST_SUGGEST)
                        .executes(NationCommands::denyRequest));
    }

    /* -------------------- rank set -------------------------- */
    private static LiteralArgumentBuilder<CommandSourceStack> cmdRank() {
        return Commands.literal("rank")
                .then(Commands.literal("set")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests(RANK_SUGGEST)
                                        .executes(NationCommands::rankSet))));
    }

    /* ================================================================= */
    /*                              LOGIC                                */
    /* ================================================================= */

    /* ---------- create ---------- */
    private static int create(CommandContext<CommandSourceStack> ctx, GovernmentType gov)
            throws CommandSyntaxException {

        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        Town city = requireMayorCity(pl, ctx); if (city == null) return 0;

        NationData nd = NationData.get(pl.serverLevel());
        if (city.getNation() != null) return fail(ctx, "§cГород уже в нации.");
        if (nd.byName(name) != null)  return fail(ctx, "§cИмя занято.");

        Nation n = nd.createNation(name, gov, city.getId(), pl.getUUID());
        /* ---------- ВСТАВЬ ЭТИ 4 СТРОКИ ---------- */
        if (gov == GovernmentType.REPUBLIC) {
            long now = pl.serverLevel().getGameTime();
            n.setNextElectionTick(now + TICKS_14_DAYS);   // отсрочить первые выборы
        }
        /* ----------------------------------------- */
        city.setNation(n.getId());
        TownData.get(pl.serverLevel()).setDirty();

        // Глобальная новость
        String form = gov == GovernmentType.REPUBLIC ? "республика" : "монархия";
        Nation.GlobalNews.broadcast(pl.getServer(),
                "§6[Мировые новости] §fБыла образована новая нация §e\"" + name + "\"§f с формой правления: §b" + form);

        success(ctx, "§aСоздана нация «" + name + "» (" + gov + ").");
        return Command.SINGLE_SUCCESS;
    }

    private static int approveRequest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = requireNationWithPerm(pl, NationPermission.APPROVE_APPLICATION, ctx); if (n==null) return 0;
        String townName = StringArgumentType.getString(ctx,"town");
        TownData td = TownData.get(pl.serverLevel());
        Town t = td.getTownByName(townName);
        if (t==null) return fail(ctx,"§cГород не найден.");
        if (!n.applications.containsKey(t.getId())) return fail(ctx,"§cЗаявки нет.");
        n.addTown(t.getId()); t.setNation(n.getId());
        n.applications.remove(t.getId());
        td.setDirty(); NationData.get(pl.serverLevel()).setDirty();
        success(ctx,"§aГород принят в нацию.");
        return Command.SINGLE_SUCCESS;
    }
    private static int denyRequest(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = requireNationWithPerm(pl, NationPermission.APPROVE_APPLICATION, ctx); if (n==null) return 0;
        String townName = StringArgumentType.getString(ctx,"town");
        TownData td = TownData.get(pl.serverLevel());
        Town t = td.getTownByName(townName);
        if (t==null) return fail(ctx,"§cГород не найден.");
        if (!n.applications.containsKey(t.getId())) return fail(ctx,"§cЗаявки нет.");
        n.applications.remove(t.getId());
        NationData.get(pl.serverLevel()).setDirty();
        success(ctx,"§eЗаявка отклонена.");
        return Command.SINGLE_SUCCESS;
    }

    private static int electionCandidacy(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = playerNation(pl);
        if (n == null) return fail(ctx, "§cВы вне нации.");
        if (n.getGovernment() != GovernmentType.REPUBLIC)
            return fail(ctx, "§cТолько для республик.");
        if (!NationPermissionUtil.has(n, pl.getUUID(), NationPermission.START_REFERENDUM))
            return fail(ctx, "§cНет права регистрироваться.");

        Election e = n.getElection();
        if (e == null || e.getPhase() != Election.Phase.REGISTRATION)
            return fail(ctx, "§cРегистрация не проводится.");
        if (!e.addCandidate(pl.getUUID()))
            return fail(ctx, "§eВы уже кандидат.");

        NationData.get(pl.serverLevel()).setDirty();
        success(ctx, "§aВы зарегистрированы кандидатом.");
        return Command.SINGLE_SUCCESS;
    }
    private static int listNations(CommandContext<CommandSourceStack> ctx) {
        var nations = NationData.get(ctx.getSource().getLevel()).all();
        if (nations.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§7Наций нет."), false);
            return Command.SINGLE_SUCCESS;
        }
        StringBuilder sb = new StringBuilder("§6Список наций: ");
        boolean first = true;
        for (Nation n : nations) {
            if (!first) sb.append("§7, ");
            sb.append("§e").append(n.getName());
            first = false;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return Command.SINGLE_SUCCESS;
    }
    private static int infoOther(CommandContext<CommandSourceStack> ctx) {
        String nName = StringArgumentType.getString(ctx, "nation");
        Nation n = NationData.get(ctx.getSource().getLevel()).byName(nName);
        if (n == null) {
            ctx.getSource().sendFailure(Component.literal("§cНация не найдена."));
            return 0;
        }
        TownData td = TownData.get(ctx.getSource().getLevel());
        StringBuilder sb = new StringBuilder();
        sb.append("§6Нация: §e").append(n.getName())
                .append("\n§7Форма: §a").append(n.getGovernment())
                .append("\n§7Статус: ").append(n.getStatus())
                .append("\n§7Столица: §a");
        Town capital = td.getTown(n.getCapitalTown());
        sb.append(capital != null ? capital.getName() : "—");
        sb.append("\n§7Лидер: §b").append(n.getLeader());
        sb.append("\n§7Городов: §a").append(n.getTowns().size());

        // Можно вывести города
        sb.append("\n§7Состав: §f");
        boolean first = true;
        for (UUID townId : n.getTowns()) {
            Town t = td.getTown(townId);
            if (t != null) {
                if (!first) sb.append("§7, ");
                sb.append("§e").append(t.getName());
                first = false;
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal(sb.toString()), false);
        return Command.SINGLE_SUCCESS;
    }
    private static int referendumStart(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = playerNation(pl);
        if (n == null) return fail(ctx, "§cВы вне нации.");
        if (!n.hasPermission(pl.getUUID(), NationPermission.START_REFERENDUM))
            return fail(ctx, "§cНет права запускать референдум.");
        if (n.referendum != null)
            return fail(ctx, "§eРеферендум уже идёт!");

        String question = StringArgumentType.getString(ctx, "question");
        long now = pl.serverLevel().getGameTime();
        long duration = 24 * 60 * 60 * 20; // 24 часа
        n.referendum = new Referendum(question, now, now + duration, false);

        NationData.get(pl.serverLevel()).setDirty();

        // Новость только для граждан нации
        NewsUtil.broadcastNation(pl.getServer(), n,
                "§6[Нация] §eВ нации §b" + n.getName() + " §eначался референдум:\n§f" + question);
        // Ответ только тому, кто запустил
        success(ctx, "§aРеферендум запущен!");

        return Command.SINGLE_SUCCESS;
    }


    // Голосование
    private static int referendumVote(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = playerNation(pl);
        if (n == null) return fail(ctx, "§cВы вне нации.");
        if (n.referendum == null) return fail(ctx, "§eНет активного референдума.");
        if (!n.hasPermission(pl.getUUID(), NationPermission.VOTE_REFERENDUM))
            return fail(ctx, "§cНет права голосовать.");

        boolean yes = BoolArgumentType.getBool(ctx, "yes");
        n.referendum.vote(pl.getUUID(), yes);
        NationData.get(pl.serverLevel()).setDirty();
        success(ctx, "§aВаш голос учтён: " + (yes ? "§aЗА" : "§cПРОТИВ"));
        return Command.SINGLE_SUCCESS;
    }

    // Просмотр информации
    private static int referendumInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = playerNation(pl);
        if (n == null) return fail(ctx, "§cВы вне нации.");
        if (n.referendum == null) return fail(ctx, "§eНет активного референдума.");
        Referendum r = n.referendum;
        long now = pl.serverLevel().getGameTime();
        long left = (r.endTick - now) / 20;
        long yes = r.votes.values().stream().filter(b -> b).count();
        long total = r.votes.size();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6Вопрос: §e" + r.question
                        + "\n§7До окончания: §a" + left + " сек."
                        + "\n§aЗА: " + yes + "§7, §cПРОТИВ: " + (total - yes)
        ), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- delete ---------- */
    // Для хранения подтверждения на удаление (30 секунд на подтверждение)


    private static int deleteNation(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = playerNation(pl);
        if (n == null) return fail(ctx, "§cВы вне нации.");
        if (!NationPermissionUtil.isLeader(n, pl.getUUID()))
            return fail(ctx, "§cТолько лидер.");

        ServerLevel lvl = pl.serverLevel();
        TownData td = TownData.get(lvl);

        if (n.getGovernment() == GovernmentType.REPUBLIC) {
            long now = lvl.getServer().overworld().getGameTime();
            Long lastTry = nationDeleteConfirm.get(pl.getUUID());

            // Уже идёт референдум — не даём запустить ещё один
            if (n.referendum != null) {
                nationDeleteConfirm.remove(pl.getUUID()); // сбросить флаг подтверждения если референдум уже идёт
                return fail(ctx, "§cВ нации уже идёт референдум!");
            }

            // Первый вызов — подтверждение
            if (lastTry == null || now - lastTry > CONFIRM_TIMEOUT_TICKS) {
                nationDeleteConfirm.put(pl.getUUID(), now);
                ctx.getSource().sendFailure(Component.literal("§cПодтвердите удаление нации повторным вводом /nation delete в течение 30 секунд!"));
                return 0;
            }

            // Повторный вызов в течение 30 секунд — запуск референдума
            nationDeleteConfirm.remove(pl.getUUID());

            long duration = 24 * 60 * 60 * 20; // 24 часа в тиках
            n.referendum = new Referendum(
                    "[Роспуск нации] Вы согласны распустить нацию \"" + n.getName() + "\"?",
                    now,
                    now + duration,
                    true
            );
            success(ctx, "§eРеферендум о роспуске начат. Через 24 часа будет принято решение.");
            Nation.GlobalNews.broadcast(lvl.getServer(), "В нации \"" + n.getName() + "\" запущен референдум о роспуске!");
            NationData.get(lvl).setDirty();
            return Command.SINGLE_SUCCESS;
        }

        // Для монархий — обычное удаление
        for (UUID townId : n.getTowns()) {
            Town t = td.getTown(townId);
            if (t != null) t.setNation(null);
        }
        NationData.get(lvl).deleteNation(n);
        Nation.GlobalNews.broadcast(lvl.getServer(), "Нация \"" + n.getName() + "\" распущена!");
        success(ctx, "§eНация удалена.");
        return Command.SINGLE_SUCCESS;
    }




    private static int electionVote(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = playerNation(pl);
        if (n == null) return fail(ctx, "§cВы вне нации.");
        if (n.getGovernment() != GovernmentType.REPUBLIC)
            return fail(ctx, "§cТолько для республик.");
        if (!NationPermissionUtil.has(n, pl.getUUID(), NationPermission.VOTE_REFERENDUM))
            return fail(ctx, "§cНет права голосовать.");

        Election e = n.getElection();
        if (e == null || e.getPhase() != Election.Phase.VOTING)
            return fail(ctx, "§cГолосование не проводится.");

        String candName = StringArgumentType.getString(ctx, "candidate");
        ServerPlayer candPl = pl.getServer().getPlayerList().getPlayerByName(candName);
        UUID candUUID = candPl != null ? candPl.getUUID() : null;
        if (candUUID == null || !e.getCandidates().contains(candUUID))
            return fail(ctx, "§cКандидат не найден.");

        e.vote(pl.getUUID(), candUUID);
        NationData.get(pl.serverLevel()).setDirty();
        success(ctx, "§aГолос учтён.");
        return Command.SINGLE_SUCCESS;
    }
    private static int electionInfo(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = playerNation(pl);
        if (n == null) return fail(ctx, "§cВы вне нации.");
        if (n.getGovernment() != GovernmentType.REPUBLIC)
            return fail(ctx, "§cТолько для республик.");

        Election e = n.getElection();
        if (e == null) return fail(ctx, "§cКампания не запущена.");

        long now  = pl.serverLevel().getGameTime();
        long left = switch (e.getPhase()) {
            case REGISTRATION -> e.getRegEndTick()  - now;
            case VOTING       -> e.getVoteEndTick() - now;
            default           -> 0;
        };
        String timeLeft = (left / 20 / 3600) + "ч " + (left / 20 / 60 % 60) + "м";

        String cands = e.getCandidates().stream()
                .map(id -> n.getPlayerName(pl.getServer(), id))
                .collect(Collectors.joining(", "));

        ctx.getSource().sendSuccess(() -> Component.literal(
                "§6Фаза: §e" + e.getPhase()
                        + "\n§7До конца этапа: §a" + timeLeft
                        + "\n§7Кандидаты: §b" + (cands.isEmpty() ? "—" : cands)), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- open/close ---------- */
    private static int setStatus(CommandContext<CommandSourceStack> ctx, NationStatus st)
            throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = requireNationWithPerm(pl, NationPermission.MANAGE_STATUS, ctx); if (n==null) return 0;
        n.setStatus(st); NationData.get(pl.serverLevel()).setDirty();
        success(ctx,"§aСтатус установлен.");
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- info ---------- */
    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = playerNation(pl);
        if (n == null) return fail(ctx, "§cВы вне нации.");
        var server = pl.getServer();
        var td = TownData.get(pl.serverLevel());

        // Лидер
        String leaderName = n.getLeader() != null ? n.getPlayerName(server, n.getLeader()) : "—";

        // Столица
        String capitalName = n.getCapitalTown() != null && td.getTown(n.getCapitalTown()) != null
                ? td.getTown(n.getCapitalTown()).getName() : "—";

        // Министры (или совет, зависит от рангов)
        List<String> ministers = n.playerRanks.entrySet().stream()
                .filter(e -> e.getValue() == NationRank.MINISTER)
                .map(e -> n.getPlayerName(server, e.getKey()))
                .toList();

        // Список городов
        List<String> townNames = n.getTowns().stream()
                .map(uuid -> td.getTown(uuid))
                .filter(Objects::nonNull)
                .map(Town::getName)
                .toList();

        // Число граждан
        long citizens = n.playerRanks.values().stream()
                .filter(r -> r == NationRank.CITIZEN)
                .count();

        // Краткая конституция (можно вывести "есть" или короткое описание)
        String constitution = (n.getConstitution() == null || n.getConstitution().isEmpty()) ?
                "—" : (n.getConstitution().length() > 40 ? n.getConstitution().substring(0, 40) + "..." : n.getConstitution());

        // Соберем сообщение
        String msg = "§6Нация: §e" + n.getName() +
                "\n§7Форма: §a" + n.getGovernment() +
                "\n§7Статус: §f" + n.getStatus() +
                "\n§7Столица: §b" + capitalName +
                "\n§7Лидер: §b" + leaderName +
                "\n§7Министры: §e" + (ministers.isEmpty() ? "—" : String.join(", ", ministers)) +
                "\n§7Города: §a" + (townNames.isEmpty() ? "—" : String.join(", ", townNames)) +
                "\n§7Число граждан: §b" + citizens +
                "\n§7Конституция: §f" + constitution;

        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return Command.SINGLE_SUCCESS;
    }


    /* ---------- join ---------- */
    private static int join(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        String nName = StringArgumentType.getString(ctx, "nation");
        Town city = requireMayorCity(pl, ctx); if (city == null) return 0;

        NationData nd = NationData.get(pl.serverLevel());
        Nation n = nd.byName(nName);
        if (n == null) return fail(ctx, "§cНация не найдена.");
        if (city.getNation() != null) return fail(ctx, "§cГород уже в нации.");

        if (n.getStatus() == NationStatus.OPEN) {
            n.addTown(city.getId());
            city.setNation(n.getId());
            TownData.get(pl.serverLevel()).setDirty();
            nd.setDirty();

            // Глобальная новость:
            Nation.GlobalNews.broadcast(pl.getServer(),
                    "§6[Мировые новости] §fГород §e\"" + city.getName() + "\" §fвступил в нацию §e\"" + n.getName() + "\"");

            success(ctx, "§aГород вступил.");
        } else {
            if (n.applications.containsKey(city.getId()))
                return fail(ctx, "§eЗаявка уже есть.");
            n.applications.put(city.getId(), new Application(city.getId(), pl.getUUID()));
            nd.setDirty();
            success(ctx, "§aЗаявка отправлена.");
        }
        return Command.SINGLE_SUCCESS;
    }


    /* ---------- invite / inviteaccept ---------- */
    private static int inviteTown(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = requireNationWithPerm(pl, NationPermission.INVITE_TOWN, ctx); if (n==null) return 0;

        String townName = StringArgumentType.getString(ctx,"town");
        Town t = TownData.get(pl.serverLevel()).getTownByName(townName);
        if (t==null) return fail(ctx,"§cГород не найден.");
        if (t.getNation()!=null) return fail(ctx,"§cГород уже в нации.");

        n.applications.put(t.getId(), new Application(t.getId(), pl.getUUID()));
        NationData.get(pl.serverLevel()).setDirty();
        success(ctx,"§aПриглашение отправлено.");
        return Command.SINGLE_SUCCESS;
    }

    private static int inviteAccept(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        String townName = StringArgumentType.getString(ctx, "town");
        Town t = TownData.get(pl.serverLevel()).getTownByName(townName);
        if (t == null || !pl.getUUID().equals(t.getMayor()))
            return fail(ctx, "§cЭто не ваш город.");

        NationData nd = NationData.get(pl.serverLevel());
        Nation n = nd.all().stream()
                .filter(N -> N.applications.containsKey(t.getId()))
                .findFirst().orElse(null);
        if (n == null) return fail(ctx, "§cПриглашения нет.");

        n.addTown(t.getId());
        t.setNation(n.getId());
        n.applications.remove(t.getId());
        TownData.get(pl.serverLevel()).setDirty();
        nd.setDirty();

        // Глобальная новость:
        Nation.GlobalNews.broadcast(pl.getServer(),
                "§6[Мировые новости] §fГород §e\"" + t.getName() + "\" §fвступил в нацию §e\"" + n.getName() + "\"");

        success(ctx, "§aГород вступил.");
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- requests ---------- */
    private static int showRequests(CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {

        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = playerNation(pl);
        if (n==null) return fail(ctx,"§cВы вне нации.");

        TownData td = TownData.get(pl.serverLevel());
        String list = n.applications.keySet().stream()
                .map(td::getTown)
                .filter(java.util.Objects::nonNull)
                .map(Town::getName)
                .collect(Collectors.joining(", "));

        ctx.getSource().sendSuccess(() -> Component.literal(
                list.isEmpty() ? "§7Нет заявок." : "§6Заявки: §e"+list), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- kick (monarchy) ---------- */
    private static int kickTown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = requireNationLeader(pl, ctx); if (n == null) return 0;
        if (n.getGovernment() != GovernmentType.MONARCHY)
            return fail(ctx, "§cКик доступен только монарху.");

        String townName = StringArgumentType.getString(ctx, "town");
        TownData td = TownData.get(pl.serverLevel());
        Town t = td.getTownByName(townName);
        if (t == null || !n.getTowns().contains(t.getId()))
            return fail(ctx, "§cГород не найден в нации.");

        n.removeTown(t.getId());
        t.setNation(null);
        td.setDirty();
        NationData.get(pl.serverLevel()).setDirty();

        // Глобальная новость:
        Nation.GlobalNews.broadcast(pl.getServer(),
                "§6[Мировые новости] §fГород §e\"" + t.getName() + "\" §fбыл исключён из нации §e\"" + n.getName() + "\"");

        success(ctx, "§eГород исключён.");
        return Command.SINGLE_SUCCESS;
    }


    /* ---------- leave (если разрешено) ---------- */
    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Town city = requireMayorCity(pl, ctx); if (city == null) return 0;
        Nation n = playerNation(pl);
        if (n == null) return fail(ctx, "§cГород не в нации.");

        if (!n.rule(ConstitutionKey.NATION_LEAVE_RULE))
            return fail(ctx, "§cОдносторонний выход запрещён.");

        n.removeTown(city.getId());
        city.setNation(null);
        TownData.get(pl.serverLevel()).setDirty();
        NationData.get(pl.serverLevel()).setDirty();

        // Глобальная новость
        Nation.GlobalNews.broadcast(pl.getServer(),
                "§6[Мировые новости] §fГород §e\"" + city.getName() + "\" §fпокинул нацию §e\"" + n.getName() + "\"");

        success(ctx, "§eГород вышел.");
        return Command.SINGLE_SUCCESS;
    }


    /* ---------- constitution view/rule ---------- */
    private static int constitutionView(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Nation n = playerNation(p);
        if (n==null) return fail(ctx,"§cВы вне нации.");
        ctx.getSource().sendSuccess(() -> Component.literal(n.getConstitution()), false);
        return Command.SINGLE_SUCCESS;
    }
    /* ---------- constitution rule add/remove ---------- */
    private static int ruleEdit(CommandContext<CommandSourceStack> ctx, boolean add)
            throws CommandSyntaxException {

        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = requireNationLeader(pl, ctx);                   // только лидер
        if (n == null) return 0;

        String keyRaw = StringArgumentType.getString(ctx, "key").toUpperCase(Locale.ROOT);
        ConstitutionKey ck;
        try { ck = ConstitutionKey.valueOf(keyRaw); }
        catch (IllegalArgumentException e) { return fail(ctx, "§cUnknown key."); }

        if (add) {
            boolean val = Boolean.parseBoolean(StringArgumentType.getString(ctx, "value"));
            n.setRule(ck, val);            // ← новый мутатор
        } else {
            n.removeRule(ck);              // ← новый мутатор
        }
        NationData.get(pl.serverLevel()).setDirty();
        success(ctx, "§aИзменено.");
        return Command.SINGLE_SUCCESS;
    }


    /* ---------- rank set ---------- */
    private static int rankSet(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {

        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        Nation n = requireNationLeader(pl, ctx); if (n==null) return 0;

        ServerPlayer target = pl.getServer().getPlayerList()
                .getPlayerByName(StringArgumentType.getString(ctx,"player"));
        if (target==null) return fail(ctx,"§cИгрок оффлайн.");

        NationRank rank;
        try { rank = NationRank.valueOf(StringArgumentType.getString(ctx,"rank").toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException e){ return fail(ctx,"§cНеизвестный ранг."); }

        n.setRank(target.getUUID(), rank);
        NationData.get(pl.serverLevel()).setDirty();
        success(ctx,"§aРанг установлен.");
        return Command.SINGLE_SUCCESS;
    }

    /* ================================================================= */
    /*                           HELPERS                                 */
    /* ================================================================= */
    private static Town requireMayorCity(ServerPlayer p, CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        Town t = TownData.get(p.serverLevel()).getTownOfPlayer(p.getUUID());
        if (t==null || !p.getUUID().equals(t.getMayor())) {
            fail(ctx,"§cВы должны быть мэром."); return null; }
        return t;
    }
    private static Nation requireNationLeader(ServerPlayer p, CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        Nation n = playerNation(p);
        if (n==null) { fail(ctx,"§cВы вне нации."); return null; }
        if (!NationPermissionUtil.isLeader(n, p.getUUID()))
        { fail(ctx,"§cТолько лидер."); return null; }
        return n;
    }
    private static Nation requireNationWithPerm(ServerPlayer p, NationPermission perm,
                                                CommandContext<CommandSourceStack> ctx)
            throws CommandSyntaxException {
        Nation n = playerNation(p);
        if (n==null) { fail(ctx,"§cВы вне нации."); return null; }
        if (!NationPermissionUtil.has(n, p.getUUID(), perm))
        { fail(ctx,"§cНет права."); return null; }
        return n;
    }

    private static Town playerCity(ServerPlayer p) {
        return TownData.get(p.serverLevel()).getTownOfPlayer(p.getUUID());
    }
    private static Nation playerNation(ServerPlayer p) {
        Town t = playerCity(p);
        return (t==null || t.getNation()==null)? null :
                NationData.get(p.serverLevel()).get(t.getNation());
    }
    private static int fail(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendFailure(Component.literal(msg)); return 0;
    }
    private static void success(CommandContext<CommandSourceStack> ctx, String msg) {
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
    }

    /* ================================================================= */
    /*                    SUGGESTION PROVIDERS (простые)                  */
    /* ================================================================= */
    private static final SuggestionProvider<CommandSourceStack> NATION_SUGGEST =
            (c,b)-> SharedSuggestionProvider.suggest(
                    NationData.get(c.getSource().getLevel()).all()
                            .stream().map(Nation::getName).collect(Collectors.toList()), b);

    private static final SuggestionProvider<CommandSourceStack> TOWN_NO_NATION_SUGGEST =
            (c,b)-> SharedSuggestionProvider.suggest(
                    TownData.get(c.getSource().getLevel()).getTownMap().values().stream()
                            .filter(t->t.getNation()==null)
                            .map(Town::getName).collect(Collectors.toList()), b);

    private static final SuggestionProvider<CommandSourceStack> TOWN_IN_MY_NATION_SUGGEST =
            (c,b)-> {
                Nation n = playerNationSafe(c);
                if (n==null) return b.buildFuture();
                return SharedSuggestionProvider.suggest(
                        n.getTowns().stream()
                                .map(id-> TownData.get(c.getSource().getLevel()).getTown(id))
                                .filter(java.util.Objects::nonNull)
                                .map(Town::getName).collect(Collectors.toList()), b);
            };
    private static final SuggestionProvider<CommandSourceStack> CANDIDATE_SUGGEST =
            (c, b) -> {
                Nation n = playerNationSafe(c);
                if (n == null || n.getElection() == null) return b.buildFuture();
                return SharedSuggestionProvider.suggest(
                        n.getElection().getCandidates().stream()
                                .map(id -> n.getPlayerName(c.getSource().getServer(), id))
                                .collect(Collectors.toList()), b);
            };
    private static final SuggestionProvider<CommandSourceStack> TOWN_REQUEST_SUGGEST =
            (c,b)-> {
                Nation n = playerNationSafe(c);
                if (n==null) return b.buildFuture();
                return SharedSuggestionProvider.suggest(
                        n.applications.keySet().stream()
                                .map(id-> TownData.get(c.getSource().getLevel()).getTown(id))
                                .filter(java.util.Objects::nonNull)
                                .map(Town::getName).collect(Collectors.toList()), b);
            };

    private static final SuggestionProvider<CommandSourceStack> RANK_SUGGEST =
            (c,b)-> SharedSuggestionProvider.suggest(
                    List.of("LEADER","MINISTER","CITIZEN"), b);

    private static Nation playerNationSafe(CommandContext<CommandSourceStack> c) {
        try { return playerNation(c.getSource().getPlayerOrException()); }
        catch (CommandSyntaxException e) { return null; }
    }
}
