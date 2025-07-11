package org.worldcraft.dominioncraft.command;

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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import org.worldcraft.dominioncraft.town.*;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Полная реализация всех /town-команд (создание, клеймы, участники, PvP, override-пермы).
 *
 * <p>Структура:</p>
 * <pre>
 * /town create &lt;name&gt;
 * /town delete
 * /town claim | unclaim | info
 *
 * /town invite &lt;player&gt;
 * /town accept
 * /town leave
 * /town kick &lt;player&gt;
 *
 * /town rank add &lt;player&gt; &lt;rank&gt;
 * /town rank remove &lt;player&gt;
 *
 * /town pvp &lt;true/false&gt;
 *
 * /town chunk pvp &lt;true/false&gt;
 * /town chunk pvp reset
 * /town chunk perm &lt;player&gt; &lt;build|break|interact|container&gt; &lt;true/false&gt;
 * </pre>
 */
public final class TownCommands {

    /* ------------------------------------------------------------------ */
    /*                       suggestion providers                         */
    /* ------------------------------------------------------------------ */

    private static final SuggestionProvider<CommandSourceStack> RANK_SUGGEST =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    Stream.of(TownRank.values())
                            .map(r -> r.name().toLowerCase(Locale.ROOT)), b);

    private static final SuggestionProvider<CommandSourceStack> CHUNK_PERM_SUGGEST =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    Stream.of(TownPermission.BUILD, TownPermission.BREAK,
                                    TownPermission.INTERACT, TownPermission.CONTAINER)
                            .map(Enum::name)
                            .map(String::toLowerCase), b);

    /* ------------------------------------------------------------------ */
    /*                         публичная регистрация                      */
    /* ------------------------------------------------------------------ */

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(root());
    }

    /* ------------------------------------------------------------------ */
    /*                         HANDLERS (COMMAND LOGIC)                   */
    /* ------------------------------------------------------------------ */

    /* ---------- create ---------- */
    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String name    = StringArgumentType.getString(ctx, "name");

        TownData d  = TownData.get(p.serverLevel());
        ChunkPos pos = new ChunkPos(p.blockPosition());

        if (d.getTownByChunk(pos) != null) {
            ctx.getSource().sendFailure(Component.literal("§cЭтот чанк уже занят."));
            return 0;
        }
        d.createTown(name, p.getUUID(), pos);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§aГород «" + name + "» создан!"), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- delete ---------- */
    private static int deleteTown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        TownData d     = TownData.get(p.serverLevel());
        Town t         = d.getTownOfPlayer(p.getUUID());

        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("§cВы не в городе."));
            return 0;
        }
        if (!t.hasPermission(p.getUUID(), TownPermission.DELETE)) {
            ctx.getSource().sendFailure(Component.literal("§cНет права удалять город."));
            return 0;
        }

        d.deleteTown(t);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§eГород удалён."), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- claim ---------- */
    private static int claim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        TownData d     = TownData.get(p.serverLevel());
        ChunkPos pos   = new ChunkPos(p.blockPosition());

        if (d.getTownByChunk(pos) != null) {
            ctx.getSource().sendFailure(Component.literal("§cЧанк уже принадлежит другому городу."));
            return 0;
        }
        Town t = d.getTownOfPlayer(p.getUUID());
        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("§cВы не в городе."));
            return 0;
        }
        if (!t.hasPermission(p.getUUID(), TownPermission.MANAGE_CLAIMS)) {
            ctx.getSource().sendFailure(Component.literal("§cНет права клеймить."));
            return 0;
        }
        if (!d.claimChunk(t, pos)) {
            ctx.getSource().sendFailure(Component.literal("§cЛимит клеймов достигнут."));
            return 0;
        }

        ctx.getSource().sendSuccess(() ->
                Component.literal("§aЧанк успешно клеймнут!"), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- unclaim ---------- */
    private static int unclaim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        TownData d     = TownData.get(p.serverLevel());
        Town t         = d.getTownOfPlayer(p.getUUID());
        ChunkPos pos   = new ChunkPos(p.blockPosition());

        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("§cВы не в городе."));
            return 0;
        }
        if (!t.owns(pos)) {
            ctx.getSource().sendFailure(Component.literal("§cЭтот чанк не вашего города."));
            return 0;
        }
        if (!t.hasPermission(p.getUUID(), TownPermission.MANAGE_CLAIMS)) {
            ctx.getSource().sendFailure(Component.literal("§cНет права расклеймить."));
            return 0;
        }

        d.unclaimChunk(t, pos);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§eЧанк расклеймлен."), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- info ---------- */
    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        TownData d     = TownData.get(p.serverLevel());
        Town t         = d.getTownByChunk(new ChunkPos(p.blockPosition()));

        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("§eДикие земли."));
            return 0;
        }
        String msg = "§6[§eГород §f" + t.getName() + "§6]\n" +
                "§7Мэр: §b" + t.getMayorName(ctx.getSource().getServer()) + "\n" +
                "§7Участников: §a" + t.getMembers().size() +
                "  §7| Чанков: §a" + t.getClaimCount() +
                "  §7| PvP: §a" + t.getTownPvp();
        ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- invite ---------- */
    private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer inviter = ctx.getSource().getPlayerOrException();
        String name          = StringArgumentType.getString(ctx, "player");

        TownData d = TownData.get(inviter.serverLevel());
        Town t     = d.getTownOfPlayer(inviter.getUUID());

        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("§cВы не в городе."));
            return 0;
        }
        if (!t.hasPermission(inviter.getUUID(), TownPermission.INVITE)) {
            ctx.getSource().sendFailure(Component.literal("§cНет права приглашать."));
            return 0;
        }

        ServerPlayer target = inviter.getServer().getPlayerList().getPlayerByName(name);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("§cИгрок оффлайн."));
            return 0;
        }
        if (t.getMembers().contains(target.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("§eИгрок уже в городе."));
            return 0;
        }

        t.addInvite(target.getUUID());
        d.setDirty();
        target.displayClientMessage(Component.literal("§6Приглашение в §e" + t.getName() + "§6. /town accept"), false);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§aПриглашение отправлено."), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- accept ---------- */
    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        TownData d     = TownData.get(p.serverLevel());

        if (d.getTownOfPlayer(p.getUUID()) != null) {
            ctx.getSource().sendFailure(Component.literal("§cВы уже в городе."));
            return 0;
        }
        Town t = d.getTownMap().values().stream()
                .filter(x -> x.hasInvite(p.getUUID()))
                .findFirst().orElse(null);
        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("§cНет активных приглашений."));
            return 0;
        }

        t.addMember(p.getUUID(), TownRank.RECRUIT);
        d.setDirty();
        ctx.getSource().sendSuccess(() ->
                Component.literal("§aВы вступили в город §e" + t.getName()), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- leave ---------- */
    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        TownData d     = TownData.get(p.serverLevel());
        Town t         = d.getTownOfPlayer(p.getUUID());

        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("§cВы не в городе."));
            return 0;
        }
        if (p.getUUID().equals(t.getMayor())) {
            ctx.getSource().sendFailure(Component.literal("§cМэр не может покинуть город."));
            return 0;
        }

        t.removeMember(p.getUUID());
        d.setDirty();
        ctx.getSource().sendSuccess(() ->
                Component.literal("§eВы покинули город " + t.getName()), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- kick ---------- */
    private static int kick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer kicker = ctx.getSource().getPlayerOrException();
        String name         = StringArgumentType.getString(ctx, "player");

        TownData d = TownData.get(kicker.serverLevel());
        Town t     = d.getTownOfPlayer(kicker.getUUID());

        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("§cВы не в городе."));
            return 0;
        }
        if (!t.hasPermission(kicker.getUUID(), TownPermission.KICK)) {
            ctx.getSource().sendFailure(Component.literal("§cНет права кикать."));
            return 0;
        }

        ServerPlayer target = kicker.getServer().getPlayerList().getPlayerByName(name);
        if (target == null || !t.getMembers().contains(target.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("§cИгрок не найден в городе."));
            return 0;
        }

        t.removeMember(target.getUUID());
        d.setDirty();
        kicker.sendSystemMessage(Component.literal("§aИгрок изгнан."));
        target.displayClientMessage(Component.literal("§cВы изгнаны из " + t.getName()), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- rank add ---------- */
    private static int rankAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        String targetName   = StringArgumentType.getString(ctx, "player");
        String rankRaw      = StringArgumentType.getString(ctx, "rank").toUpperCase(Locale.ROOT);

        TownData d = TownData.get(sender.serverLevel());
        Town t     = d.getTownOfPlayer(sender.getUUID());

        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("§cВы не в городе."));
            return 0;
        }
        if (!t.hasPermission(sender.getUUID(), TownPermission.SET_RANK)) {
            ctx.getSource().sendFailure(Component.literal("§cНет права менять ранги."));
            return 0;
        }

        TownRank rank;
        try {
            rank = TownRank.valueOf(rankRaw);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal("§cНеизвестный ранг."));
            return 0;
        }

        ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null || !t.getMembers().contains(target.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("§cИгрок не найден в городе."));
            return 0;
        }

        t.setRank(target.getUUID(), rank);
        d.setDirty();
        ctx.getSource().sendSuccess(() ->
                Component.literal("§aРанг установлен."), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- rank remove ---------- */
    private static int rankRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        String targetName   = StringArgumentType.getString(ctx, "player");

        TownData d = TownData.get(sender.serverLevel());
        Town t     = d.getTownOfPlayer(sender.getUUID());

        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("§cВы не в городе."));
            return 0;
        }
        if (!t.hasPermission(sender.getUUID(), TownPermission.SET_RANK)) {
            ctx.getSource().sendFailure(Component.literal("§cНет права менять ранги."));
            return 0;
        }

        ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null || !t.getMembers().contains(target.getUUID())) {
            ctx.getSource().sendFailure(Component.literal("§cИгрок не найден в городе."));
            return 0;
        }

        t.setRank(target.getUUID(), TownRank.RECRUIT);
        d.setDirty();
        ctx.getSource().sendSuccess(() ->
                Component.literal("§eРанг сброшен."), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- town PvP ---------- */
    private static int townPvp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        boolean flag    = BoolArgumentType.getBool(ctx, "flag");

        TownData d = TownData.get(pl.serverLevel());
        Town t     = d.getTownOfPlayer(pl.getUUID());

        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("§cВы не в городе."));
            return 0;
        }
        if (!t.hasPermission(pl.getUUID(), TownPermission.MANAGE_PVP)) {
            ctx.getSource().sendFailure(Component.literal("§cНет права менять PvP."));
            return 0;
        }

        t.setTownPvp(flag);
        d.setDirty();
        ctx.getSource().sendSuccess(() ->
                Component.literal("§ePvP города: " + flag), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- chunk PvP (flag) ---------- */
    private static int chunkPvpFlag(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        boolean flag    = BoolArgumentType.getBool(ctx, "flag");

        return setChunkPvp(pl, flag, ctx);
    }

    /* ---------- chunk PvP (reset) ---------- */
    private static int chunkPvpReset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        return setChunkPvp(pl, null, ctx);
    }

    private static int setChunkPvp(ServerPlayer pl, Boolean flag, CommandContext<CommandSourceStack> ctx) {
        TownData d = TownData.get(pl.serverLevel());
        Town t     = d.getTownOfPlayer(pl.getUUID());

        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("§cВы не в городе."));
            return 0;
        }
        if (!t.hasPermission(pl.getUUID(), TownPermission.CHUNK_PVP)) {
            ctx.getSource().sendFailure(Component.literal("§cНет права менять PvP чанка."));
            return 0;
        }

        ChunkPos pos = new ChunkPos(pl.blockPosition());
        if (!t.owns(pos)) {
            ctx.getSource().sendFailure(Component.literal("§cЧанк не вашего города."));
            return 0;
        }

        t.chunk(pos).setPvp(flag);
        d.setDirty();
        ctx.getSource().sendSuccess(() ->
                Component.literal("§ePvP чанка: " + (flag == null ? "reset (наследует город)" : flag)), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- chunk perm ---------- */
    private static int chunkPerm(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        String targetName   = StringArgumentType.getString(ctx, "player");
        String permRaw      = StringArgumentType.getString(ctx, "perm").toUpperCase(Locale.ROOT);
        boolean value       = BoolArgumentType.getBool(ctx, "value");

        if (Stream.of(TownPermission.BUILD, TownPermission.BREAK,
                        TownPermission.INTERACT, TownPermission.CONTAINER)
                .noneMatch(tp -> tp.name().equals(permRaw))) {
            ctx.getSource().sendFailure(Component.literal("§cТолько BUILD/BREAK/INTERACT/CONTAINER."));
            return 0;
        }
        TownPermission perm = TownPermission.valueOf(permRaw);

        TownData d = TownData.get(sender.serverLevel());
        Town t     = d.getTownOfPlayer(sender.getUUID());

        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("§cВы не в городе."));
            return 0;
        }
        if (!t.hasPermission(sender.getUUID(), TownPermission.CHUNK_PERM)) {
            ctx.getSource().sendFailure(Component.literal("§cНет права менять пермы."));
            return 0;
        }

        ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(targetName);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("§cИгрок оффлайн."));
            return 0;
        }

        ChunkPos pos = new ChunkPos(sender.blockPosition());
        if (!t.owns(pos)) {
            ctx.getSource().sendFailure(Component.literal("§cЧанк не вашего города."));
            return 0;
        }

        t.chunk(pos).setPlayerPerm(target.getUUID(), perm, value);
        d.setDirty();
        ctx.getSource().sendSuccess(() ->
                Component.literal("§a" + permRaw + " для " + targetName + " = " + value), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ------------------------------------------------------------------ */
    /*                       COMMAND TREE BUILDERS                        */
    /* ------------------------------------------------------------------ */

    private static LiteralArgumentBuilder<CommandSourceStack> root() {
        return Commands.literal("town")

                /* базовые */
                .then(cmdCreate())
                .then(cmdDelete())
                .then(cmdClaim())
                .then(cmdUnclaim())
                .then(cmdInfo())

                /* люди */
                .then(cmdInvite())
                .then(cmdAccept())
                .then(cmdLeave())
                .then(cmdKick())
                .then(cmdRank())

                /* PvP (город) */
                .then(cmdTownPvp())

                /* PvP / perm для чанков */
                .then(cmdChunk());
    }

    /* ---------- leaf builders ---------- */

    private static LiteralArgumentBuilder<CommandSourceStack> cmdCreate() {
        return Commands.literal("create")
                .then(Commands.argument("name", StringArgumentType.word())
                        .executes(TownCommands::create));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cmdDelete() {
        return Commands.literal("delete").executes(TownCommands::deleteTown);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cmdClaim() {
        return Commands.literal("claim").executes(TownCommands::claim);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cmdUnclaim() {
        return Commands.literal("unclaim").executes(TownCommands::unclaim);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cmdInfo() {
        return Commands.literal("info").executes(TownCommands::info);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cmdInvite() {
        return Commands.literal("invite")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(TownCommands::invite));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cmdAccept() {
        return Commands.literal("accept").executes(TownCommands::accept);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cmdLeave() {
        return Commands.literal("leave").executes(TownCommands::leave);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cmdKick() {
        return Commands.literal("kick")
                .then(Commands.argument("player", StringArgumentType.word())
                        .executes(TownCommands::kick));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cmdRank() {
        return Commands.literal("rank")
                .then(Commands.literal("add")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .then(Commands.argument("rank", StringArgumentType.word())
                                        .suggests(RANK_SUGGEST)
                                        .executes(TownCommands::rankAdd))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("player", StringArgumentType.word())
                                .executes(TownCommands::rankRemove)));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cmdTownPvp() {
        return Commands.literal("pvp")
                .then(Commands.argument("flag", BoolArgumentType.bool())
                        .executes(TownCommands::townPvp));
    }

    /* ---------- chunk subtree ---------- */

    private static LiteralArgumentBuilder<CommandSourceStack> cmdChunk() {
        return Commands.literal("chunk")
                .then(cmdChunkPvp())
                .then(cmdChunkPerm());
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cmdChunkPvp() {
        return Commands.literal("pvp")
                .then(Commands.literal("reset")
                        .executes(TownCommands::chunkPvpReset))
                .then(Commands.argument("flag", BoolArgumentType.bool())
                        .executes(TownCommands::chunkPvpFlag));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> cmdChunkPerm() {
        return Commands.literal("perm")
                .then(Commands.argument("player", StringArgumentType.word())
                        .then(Commands.argument("perm", StringArgumentType.word())
                                .suggests(CHUNK_PERM_SUGGEST)
                                .then(Commands.argument("value", BoolArgumentType.bool())
                                        .executes(TownCommands::chunkPerm))));
    }
}
