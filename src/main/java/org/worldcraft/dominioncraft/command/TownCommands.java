/* ===================================================================== *
 *  file: org/worldcraft/dominioncraft/command/TownCommands.java         *
 * ===================================================================== */
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
import java.util.UUID;
import java.util.stream.Stream;

public final class TownCommands {

    /* ------------------------------------------------------------------ */
    /*            ░░  S U G G E S T I O N   P R O V I D E R S  ░░          */
    /* ------------------------------------------------------------------ */

    private static final SuggestionProvider<CommandSourceStack> RANK_SUGGEST =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    Stream.of(TownRank.values())
                            .map(r -> r.name().toLowerCase(Locale.ROOT)), b);

    private static final SuggestionProvider<CommandSourceStack> CHUNK_PERM_SUGGEST =
            (ctx, b) -> SharedSuggestionProvider.suggest(
                    Stream.of(
                                    TownPermission.BUILD,
                                    TownPermission.BREAK,
                                    TownPermission.INTERACT,
                                    TownPermission.CONTAINER,
                                    TownPermission.ANIMAL,
                                    TownPermission.FARM)
                            .map(Enum::name)
                            .map(String::toLowerCase), b);

    /* ------------------------------------------------------------------ */
    /*                       Р Е Г И С Т Р А Ц И Я                         */
    /* ------------------------------------------------------------------ */
    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(root());
    }

    /* ------------------------------------------------------------------ */
    /*                    ░░  О С Н О В Н Ы Е   К М Д  ░░                 */
    /* ------------------------------------------------------------------ */

    /* ---------- /town create <name> ---------- */
    private static int create(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl   = ctx.getSource().getPlayerOrException();
        String name       = StringArgumentType.getString(ctx, "name");
        TownData data     = TownData.get(pl.serverLevel());
        ChunkPos pos      = new ChunkPos(pl.blockPosition());

        if (data.getTownByChunk(pos) != null) {
            ctx.getSource().sendFailure(Component.literal("§cЭтот чанк уже занят."));
            return 0;
        }
        data.createTown(name, pl.getUUID(), pos);
        ctx.getSource().sendSuccess(() ->
                Component.literal("§aГород «" + name + "» создан!"), false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- /town delete ---------- */
    private static int deleteTown(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        TownData d      = TownData.get(pl.serverLevel());
        Town t          = d.getTownOfPlayer(pl.getUUID());

        if (t == null) return fail(ctx, "§cВы не в городе.");
        if (!t.hasPermission(pl.getUUID(), TownPermission.DELETE))
            return fail(ctx, "§cНет права удалять город.");

        d.deleteTown(t);
        success(ctx, "§eГород удалён.");
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- /town claim (смежный) ---------- */
    private static int claim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        TownData d      = TownData.get(pl.serverLevel());
        ChunkPos pos    = new ChunkPos(pl.blockPosition());

        if (d.getTownByChunk(pos) != null) return fail(ctx,"§cЧанк уже занят.");

        Town t = d.getTownOfPlayer(pl.getUUID());
        if (t == null || !t.getMayor().equals(pl.getUUID()))
            return fail(ctx,"§cТолько мэр может клеймить.");
        if (!t.hasPermission(pl.getUUID(), TownPermission.MANAGE_CLAIMS))
            return fail(ctx,"§cНет права клеймить.");

        boolean adjacent = t.allChunks().stream()
                .map(TownChunk::getPos)
                .anyMatch(c -> isAdjacent(c,pos));
        if (!adjacent) return fail(ctx,"§eЧанк должен примыкать к городу.");

        if (!d.claimChunk(t,pos)) return fail(ctx,"§cЛимит клеймов.");

        success(ctx,"§aЧанк клеймнут!");
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- /town unclaim ---------- */
    private static int unclaim(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        TownData d      = TownData.get(pl.serverLevel());
        Town t          = d.getTownOfPlayer(pl.getUUID());
        ChunkPos pos    = new ChunkPos(pl.blockPosition());

        if (t == null) return fail(ctx,"§cВы не в городе.");
        if (!t.owns(pos)) return fail(ctx,"§cЧанк не вашего города.");
        if (!t.hasPermission(pl.getUUID(), TownPermission.MANAGE_CLAIMS))
            return fail(ctx,"§cНет права расклеймить.");
        if (t.getClaimCount()==1) return fail(ctx,"§cПоследний чанк нельзя.");

        d.unclaimChunk(t,pos);
        success(ctx,"§eЧанк расклеймлен.");
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- /town info ---------- */
    private static int info(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl  = ctx.getSource().getPlayerOrException();
        TownData d       = TownData.get(pl.serverLevel());
        ChunkPos pos     = new ChunkPos(pl.blockPosition());
        Town t           = d.getTownByChunk(pos);

        if (t == null) return fail(ctx,"§eДикие земли.");

        Boolean chunkExpl = t.chunk(pos)!=null? t.chunk(pos).getExplosion():null;
        String explosionLine = "§7Взрывы: §a" +
                (chunkExpl!=null? (chunkExpl?"ON§7 (чанк)":"OFF§7 (чанк)") :
                        (t.getTownExplosion()? "ON§7 (город)" : "OFF§7 (город)"));

        String msg = "§6[§eГород §f" + t.getName() + "§6]\n" +
                "§7Мэр: §b" + t.getMayorName(ctx.getSource().getServer()) + "\n" +
                "§7Участников: §a" + t.getMembers().size() +
                "  §7| Чанков: §a" + t.getClaimCount() +
                "  §7| PvP: §a" + t.getTownPvp() + "\n" +
                explosionLine;
        success(ctx,msg);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- /town invite <player> ---------- */
    private static int invite(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer inv  = ctx.getSource().getPlayerOrException();
        String name       = StringArgumentType.getString(ctx,"player");
        TownData d        = TownData.get(inv.serverLevel());
        Town t            = d.getTownOfPlayer(inv.getUUID());

        if (t==null)              return fail(ctx,"§cВы не в городе.");
        if (!t.hasPermission(inv.getUUID(),TownPermission.INVITE))
            return fail(ctx,"§cНет права приглашать.");

        ServerPlayer target = inv.getServer().getPlayerList().getPlayerByName(name);
        if (target==null)         return fail(ctx,"§cИгрок оффлайн.");
        if (t.getMembers().contains(target.getUUID()))
            return fail(ctx,"§eИгрок уже в городе.");

        t.addInvite(target.getUUID());
        d.setDirty();
        target.displayClientMessage(Component.literal("§6Приглашение в §e"+t.getName()+"§6. /town accept"),false);
        success(ctx,"§aПриглашение отправлено.");
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- /town accept ---------- */
    private static int accept(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        String name    = StringArgumentType.getString(ctx, "name");
        TownData d     = TownData.get(p.serverLevel());

        if (d.getTownOfPlayer(p.getUUID()) != null) {
            ctx.getSource().sendFailure(Component.literal("§cВы уже в городе."));
            return 0;
        }
        Town t = d.getTownMap().values().stream()
                .filter(x -> x.getName().equalsIgnoreCase(name) && x.hasInvite(p.getUUID()))
                .findFirst().orElse(null);
        if (t == null) {
            ctx.getSource().sendFailure(Component.literal("§cНет приглашения в этот город."));
            return 0;
        }

        t.addMember(p.getUUID(), TownRank.RECRUIT);
        d.setDirty();
        ctx.getSource().sendSuccess(() ->
                Component.literal("§aВы вступили в город §e" + t.getName()), false);
        return Command.SINGLE_SUCCESS;
    }
    private static final SuggestionProvider<CommandSourceStack> TOWN_INVITE_SUGGEST =
            (ctx, builder) -> {
                try {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    TownData d = TownData.get(player.serverLevel());
                    return SharedSuggestionProvider.suggest(
                            d.getTownMap().values().stream()
                                    .filter(t -> t.hasInvite(player.getUUID()))
                                    .map(Town::getName), builder);
                } catch (Exception e) {
                    return builder.buildFuture();
                }
            };


    /* ---------- /town leave ---------- */
    private static int leave(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        TownData d      = TownData.get(pl.serverLevel());
        Town t          = d.getTownOfPlayer(pl.getUUID());

        if (t==null) return fail(ctx,"§cВы не в городе.");
        if (pl.getUUID().equals(t.getMayor()))
            return fail(ctx,"§cМэр не может покинуть город.");

        t.removeMember(pl.getUUID());
        d.setDirty();
        success(ctx,"§eВы покинули "+t.getName());
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- /town kick <player> ---------- */
    private static int kick(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer kicker = ctx.getSource().getPlayerOrException();
        String name         = StringArgumentType.getString(ctx,"player");
        TownData d          = TownData.get(kicker.serverLevel());
        Town t              = d.getTownOfPlayer(kicker.getUUID());

        if (t==null) return fail(ctx,"§cВы не в городе.");
        if (!t.hasPermission(kicker.getUUID(),TownPermission.KICK))
            return fail(ctx,"§cНет права кикать.");

        ServerPlayer target = kicker.getServer().getPlayerList().getPlayerByName(name);
        if (target==null || !t.getMembers().contains(target.getUUID()))
            return fail(ctx,"§cИгрок не найден в городе.");

        t.removeMember(target.getUUID());
        d.setDirty();
        kicker.sendSystemMessage(Component.literal("§aИгрок изгнан."));
        target.displayClientMessage(Component.literal("§cВы изгнаны из "+t.getName()),false);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- /town rank add|remove ---------- */
    private static int rankAdd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setRank(ctx,true);
    }
    private static int rankRemove(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return setRank(ctx,false);
    }
    private static int setRank(CommandContext<CommandSourceStack> ctx, boolean add) throws CommandSyntaxException {
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        String targetName   = StringArgumentType.getString(ctx,"player");
        TownData d          = TownData.get(sender.serverLevel());
        Town t              = d.getTownOfPlayer(sender.getUUID());

        if (t==null) return fail(ctx,"§cВы не в городе.");
        if (!t.hasPermission(sender.getUUID(),TownPermission.SET_RANK))
            return fail(ctx,"§cНет права менять ранги.");

        ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(targetName);
        if (target==null || !t.getMembers().contains(target.getUUID()))
            return fail(ctx,"§cИгрок не найден в городе.");

        if (add) {
            String rankRaw = StringArgumentType.getString(ctx,"rank").toUpperCase(Locale.ROOT);
            TownRank rank;
            try { rank = TownRank.valueOf(rankRaw); }
            catch (IllegalArgumentException e){ return fail(ctx,"§cНеизвестный ранг."); }
            t.setRank(target.getUUID(),rank);
            success(ctx,"§aРанг установлен.");
        } else {
            t.setRank(target.getUUID(),TownRank.RECRUIT);
            success(ctx,"§eРанг сброшен.");
        }
        d.setDirty();
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- /town pvp <bool> ---------- */
    private static int townPvp(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        boolean flag    = BoolArgumentType.getBool(ctx,"flag");
        TownData d      = TownData.get(pl.serverLevel());
        Town t          = d.getTownOfPlayer(pl.getUUID());

        if (t==null) return fail(ctx,"§cВы не в городе.");
        if (!t.hasPermission(pl.getUUID(),TownPermission.MANAGE_PVP))
            return fail(ctx,"§cНет права.");

        t.setTownPvp(flag);
        d.setDirty();
        success(ctx,"§ePvP города: "+flag);
        return Command.SINGLE_SUCCESS;
    }

    /* ---------- /town chunk pvp ... ---------- */
    private static int chunkPvpFlag(CommandContext<CommandSourceStack> ctx)throws CommandSyntaxException{
        return setChunkPvp(ctx,BoolArgumentType.getBool(ctx,"flag"));
    }
    private static int chunkPvpReset(CommandContext<CommandSourceStack> ctx)throws CommandSyntaxException{
        return setChunkPvp(ctx,null);
    }
    private static int setChunkPvp(CommandContext<CommandSourceStack> ctx, Boolean flag)throws CommandSyntaxException{
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        TownData d      = TownData.get(pl.serverLevel());
        Town t          = d.getTownOfPlayer(pl.getUUID());
        ChunkPos pos    = new ChunkPos(pl.blockPosition());

        if (t==null) return fail(ctx,"§cВы не в городе.");
        if (!t.hasPermission(pl.getUUID(),TownPermission.CHUNK_PVP))
            return fail(ctx,"§cНет права.");
        if (!t.owns(pos)) return fail(ctx,"§cЧанк не вашего города.");

        t.chunk(pos).setPvp(flag);
        d.setDirty();
        success(ctx,"§ePvP чанка: "+(flag==null?"reset":flag));
        return Command.SINGLE_SUCCESS;
    }

    /* ------------------------------------------------------------------ */
    /*                    ░░   В З Р Ы В Ы   ░░                            */
    /* ------------------------------------------------------------------ */

    private static int townExplosion(CommandContext<CommandSourceStack> ctx)throws CommandSyntaxException{
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        boolean flag    = BoolArgumentType.getBool(ctx,"flag");
        TownData d      = TownData.get(pl.serverLevel());
        Town t          = d.getTownOfPlayer(pl.getUUID());

        if (t==null) return fail(ctx,"§cВы не в городе.");
        if (!t.hasPermission(pl.getUUID(),TownPermission.EXPLOSION)
                && !t.hasPermission(pl.getUUID(),TownPermission.MANAGE_PVP))
            return fail(ctx,"§cНет права.");

        t.setTownExplosion(flag);
        d.setDirty();
        success(ctx,"§eВзрывы в городе: "+flag);
        return Command.SINGLE_SUCCESS;
    }

    private static int chunkExplosionFlag(CommandContext<CommandSourceStack> ctx)throws CommandSyntaxException{
        return setChunkExplosion(ctx,BoolArgumentType.getBool(ctx,"flag"));
    }
    private static int chunkExplosionReset(CommandContext<CommandSourceStack> ctx)throws CommandSyntaxException{
        return setChunkExplosion(ctx,null);
    }
    private static int setChunkExplosion(CommandContext<CommandSourceStack> ctx, Boolean flag)throws CommandSyntaxException{
        ServerPlayer pl = ctx.getSource().getPlayerOrException();
        TownData d      = TownData.get(pl.serverLevel());
        Town t          = d.getTownOfPlayer(pl.getUUID());
        ChunkPos pos    = new ChunkPos(pl.blockPosition());

        if (t==null) return fail(ctx,"§cВы не в городе.");
        if (!t.hasPermission(pl.getUUID(),TownPermission.CHUNK_EXPLOSION))
            return fail(ctx,"§cНет права.");
        if (!t.owns(pos)) return fail(ctx,"§cЧанк не вашего города.");

        t.chunk(pos).setExplosion(flag);
        d.setDirty();
        success(ctx,"§eВзрывы в чанке: "+(flag==null?"reset":flag));
        return Command.SINGLE_SUCCESS;
    }

    /* ------------------------------------------------------------------ */
    /*               ░░   Ч А Н К  P E R M S (build / …) ░░               */
    /* ------------------------------------------------------------------ */

    private static int chunkPerm(CommandContext<CommandSourceStack> ctx)throws CommandSyntaxException{
        ServerPlayer sender = ctx.getSource().getPlayerOrException();
        String trgName      = StringArgumentType.getString(ctx,"player");
        String permRaw      = StringArgumentType.getString(ctx,"perm").toUpperCase(Locale.ROOT);
        boolean value       = BoolArgumentType.getBool(ctx,"value");

        var allowed = Stream.of(TownPermission.values())
                .map(Enum::name).anyMatch(permRaw::equals);
        if(!allowed) return fail(ctx,"§cНеизвестное право.");

        TownPermission perm = TownPermission.valueOf(permRaw);
        TownData d   = TownData.get(sender.serverLevel());
        Town t       = d.getTownOfPlayer(sender.getUUID());
        if (t==null) return fail(ctx,"§cВы не в городе.");
        if (!t.hasPermission(sender.getUUID(),TownPermission.CHUNK_PERM))
            return fail(ctx,"§cНет права.");

        ServerPlayer target = sender.getServer().getPlayerList().getPlayerByName(trgName);
        if (target==null) return fail(ctx,"§cИгрок оффлайн.");

        ChunkPos pos = new ChunkPos(sender.blockPosition());
        if (!t.owns(pos)) return fail(ctx,"§cЧанк не вашего города.");

        t.chunk(pos).setPlayerPerm(target.getUUID(),perm,value);
        d.setDirty();
        success(ctx,"§a"+permRaw+" для "+trgName+" = "+value);
        return Command.SINGLE_SUCCESS;
    }

    /* ------------------------------------------------------------------ */
    /*                         К О М А Н Д - Д Е Р Е В О                  */
    /* ------------------------------------------------------------------ */

    private static LiteralArgumentBuilder<CommandSourceStack> root(){
        return Commands.literal("town")
                .then(cmdCreate()).then(cmdDelete()).then(cmdClaim()).then(cmdUnclaim())
                .then(cmdInfo()).then(cmdInvite()).then(cmdAccept()).then(cmdLeave())
                .then(cmdKick()).then(cmdRank())
                .then(cmdTownPvp()).then(cmdTownExplosion())
                .then(cmdChunk());
    }

    /* ----- листья ----- */
    private static LiteralArgumentBuilder<CommandSourceStack> cmdCreate(){ return Commands.literal("create")
            .then(Commands.argument("name",StringArgumentType.word()).executes(TownCommands::create));}
    private static LiteralArgumentBuilder<CommandSourceStack> cmdDelete(){return Commands.literal("delete").executes(TownCommands::deleteTown);}
    private static LiteralArgumentBuilder<CommandSourceStack> cmdClaim(){return Commands.literal("claim").executes(TownCommands::claim);}
    private static LiteralArgumentBuilder<CommandSourceStack> cmdUnclaim(){return Commands.literal("unclaim").executes(TownCommands::unclaim);}
    private static LiteralArgumentBuilder<CommandSourceStack> cmdInfo(){return Commands.literal("info").executes(TownCommands::info);}
    private static LiteralArgumentBuilder<CommandSourceStack> cmdInvite(){return Commands.literal("invite")
            .then(Commands.argument("player",StringArgumentType.word()).executes(TownCommands::invite));}
    private static LiteralArgumentBuilder<CommandSourceStack> cmdAccept() {
        return Commands.literal("accept")
                .then(Commands.argument("name", StringArgumentType.word())
                        .suggests(TOWN_INVITE_SUGGEST)
                        .executes(TownCommands::accept));

    }

    private static LiteralArgumentBuilder<CommandSourceStack> cmdLeave(){return Commands.literal("leave").executes(TownCommands::leave);}
    private static LiteralArgumentBuilder<CommandSourceStack> cmdKick(){return Commands.literal("kick")
            .then(Commands.argument("player",StringArgumentType.word()).executes(TownCommands::kick));}
    private static LiteralArgumentBuilder<CommandSourceStack> cmdRank(){return Commands.literal("rank")
            .then(Commands.literal("add")
                    .then(Commands.argument("player",StringArgumentType.word())
                            .then(Commands.argument("rank",StringArgumentType.word())
                                    .suggests(RANK_SUGGEST)
                                    .executes(TownCommands::rankAdd))))
            .then(Commands.literal("remove")
                    .then(Commands.argument("player",StringArgumentType.word())
                            .executes(TownCommands::rankRemove)));}
    private static LiteralArgumentBuilder<CommandSourceStack> cmdTownPvp(){return Commands.literal("pvp")
            .then(Commands.argument("flag",BoolArgumentType.bool()).executes(TownCommands::townPvp));}
    private static LiteralArgumentBuilder<CommandSourceStack> cmdTownExplosion(){return Commands.literal("explosion")
            .then(Commands.argument("flag",BoolArgumentType.bool()).executes(TownCommands::townExplosion));}

    /* ---- chunk subtree ---- */
    private static LiteralArgumentBuilder<CommandSourceStack> cmdChunk(){
        return Commands.literal("chunk").then(cmdChunkPvp()).then(cmdChunkExplosion()).then(cmdChunkPerm());
    }
    private static LiteralArgumentBuilder<CommandSourceStack> cmdChunkPvp(){return Commands.literal("pvp")
            .then(Commands.literal("reset").executes(TownCommands::chunkPvpReset))
            .then(Commands.argument("flag",BoolArgumentType.bool()).executes(TownCommands::chunkPvpFlag));}
    private static LiteralArgumentBuilder<CommandSourceStack> cmdChunkExplosion(){return Commands.literal("explosion")
            .then(Commands.literal("reset").executes(TownCommands::chunkExplosionReset))
            .then(Commands.argument("flag",BoolArgumentType.bool()).executes(TownCommands::chunkExplosionFlag));}
    private static LiteralArgumentBuilder<CommandSourceStack> cmdChunkPerm(){return Commands.literal("perm")
            .then(Commands.argument("player",StringArgumentType.word())
                    .then(Commands.argument("perm",StringArgumentType.word()).suggests(CHUNK_PERM_SUGGEST)
                            .then(Commands.argument("value",BoolArgumentType.bool())
                                    .executes(TownCommands::chunkPerm))));}

    /* ------------------------------------------------------------------ */
    /*                               H E L P E R S                        */
    /* ------------------------------------------------------------------ */
    private static boolean isAdjacent(ChunkPos a,ChunkPos b){
        int dx=Math.abs(a.x-b.x),dz=Math.abs(a.z-b.z);
        return (dx==1&&dz==0)||(dx==0&&dz==1);
    }
    private static int fail(CommandContext<CommandSourceStack> ctx,String msg){
        ctx.getSource().sendFailure(Component.literal(msg)); return 0;
    }
    private static void success(CommandContext<CommandSourceStack> ctx,String msg){
        ctx.getSource().sendSuccess(()->Component.literal(msg),false);
    }
}
