package org.worldcraft.dominioncraft;

import net.fabricmc.api.ModInitializer;

/* Fabric events */
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/* MC / Mojang */
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.*;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/* собственные */
import org.worldcraft.dominioncraft.command.TownCommands;
import org.worldcraft.dominioncraft.town.*;

import java.util.*;

/**
 * Главный класс мода DominionCraft:
 * <ul>
 *   <li>регистрирует /town-команды</li>
 *   <li>проверяет права при использовании блоков, ломании, PvP</li>
 *   <li>шлёт кастомный HUD-пакет игроку при входе и при смене чанка</li>
 * </ul>
 */
public class Dominioncraft implements ModInitializer {

    /* ────────────────────── сетевой канал (HUD) ─────────────────────── */
    public static final ResourceLocation HUD_PACKET =
            new ResourceLocation("dominioncraft", "hud_update");

    /* кеш «последний известный чанк игрока» → чтобы не спамить пакетами */
    private static final Map<UUID, ChunkPos> LAST_CHUNK = new HashMap<>();

    /* ───────────────────────── helper-методы ────────────────────────── */

    /** Проверка доступа к действию (build/break/… ) в указанном чанке. */
    private boolean hasAccess(ServerLevel lvl, Player player, ChunkPos chunk, TownPermission perm) {
        Town town = TownData.get(lvl).getTownByChunk(chunk);
        if (town == null) return true;                       // дикие земли
        if (town.hasPermission(player.getUUID(), perm)) return true;   // ранговое право
        TownChunk tc = town.chunk(chunk);
        return tc != null && tc.playerHas(player.getUUID(), perm);     // override-право
    }

    private static boolean isContainer(BlockState s) {
        Block b = s.getBlock();
        return b instanceof ChestBlock || b instanceof BarrelBlock
                || b instanceof ShulkerBoxBlock || b instanceof EnderChestBlock
                || b instanceof AbstractFurnaceBlock
                || (b instanceof EntityBlock eb && eb.newBlockEntity(BlockPos.ZERO, s) instanceof Container);
    }
    private static boolean isInteract(BlockState s) {
        Block b = s.getBlock();
        return b instanceof DoorBlock || b instanceof TrapDoorBlock
                || b instanceof FenceGateBlock || b instanceof LeverBlock
                || b instanceof ButtonBlock || b instanceof PressurePlateBlock
                || b instanceof NoteBlock || b instanceof BellBlock
                || b instanceof RepeaterBlock || b instanceof ComparatorBlock
                || b instanceof CakeBlock;
    }

    /* ───────────────────────────── init ─────────────────────────────── */

    @Override
    public void onInitialize() {

        /* ---------- команды ---------- */
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, env) -> TownCommands.register(dispatcher));

        /* ---------- use-block ---------- */
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (level.isClientSide()) return InteractionResult.PASS;

            ServerLevel srv   = (ServerLevel) level;
            ChunkPos   chunk  = new ChunkPos(hit.getBlockPos());
            BlockState state  = level.getBlockState(hit.getBlockPos());
            ItemStack  held   = player.getItemInHand(hand);

            TownPermission need = isContainer(state)                 ? TownPermission.CONTAINER
                    : isInteract(state)                  ? TownPermission.INTERACT
                    : held.getItem() instanceof BlockItem? TownPermission.BUILD
                    :                                      TownPermission.INTERACT;

            if (!hasAccess(srv, player, chunk, need)) {
                player.displayClientMessage(Component.literal("§cНет прав."), true);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        /* ---------- use-entity ---------- */
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (level.isClientSide()) return InteractionResult.PASS;

            boolean container = entity instanceof MinecartChest   || entity instanceof MinecartHopper
                    || entity instanceof MinecartFurnace || entity instanceof ChestBoat;

            TownPermission need = container ? TownPermission.CONTAINER : TownPermission.INTERACT;
            if (!hasAccess((ServerLevel) level, player, new ChunkPos(entity.blockPosition()), need)) {
                player.displayClientMessage(Component.literal("§cНет прав."), true);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        /* ---------- break-block ---------- */
        AttackBlockCallback.EVENT.register((player, level, hand, pos, dir) -> {
            if (level.isClientSide()) return InteractionResult.PASS;
            if (!hasAccess((ServerLevel) level, player, new ChunkPos(pos), TownPermission.BREAK)) {
                player.displayClientMessage(Component.literal("§cНет прав ломать!"), true);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        /* ---------- PvP-hit ---------- */
        AttackEntityCallback.EVENT.register((attacker, level, hand, target, hit) -> {
            if (level.isClientSide() || !(target instanceof Player victim)) return InteractionResult.PASS;

            TownData d = TownData.get((ServerLevel) level);
            ChunkPos a = new ChunkPos(attacker.blockPosition());
            ChunkPos v = new ChunkPos(victim.blockPosition());

            boolean aPvp = Optional.ofNullable(d.getTownByChunk(a)).map(t -> t.isChunkPvp(a)).orElse(true);
            boolean vPvp = Optional.ofNullable(d.getTownByChunk(v)).map(t -> t.isChunkPvp(v)).orElse(true);

            if (!aPvp || !vPvp) {
                attacker.displayClientMessage(Component.literal("§cPvP запрещено здесь."), true);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        /* ----------HUD: отправляем при логине---------- */
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.getPlayer();
            pushHudToPlayer(p);
            LAST_CHUNK.put(p.getUUID(), new ChunkPos(p.blockPosition()));
        });

        /* ----------HUD: проверяем смену чанка раз в секунду---------- */
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;   // 20 тик = 1 с

            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                ChunkPos now = new ChunkPos(p.blockPosition());
                ChunkPos prev = LAST_CHUNK.get(p.getUUID());
                if (!now.equals(prev)) {
                    pushHudToPlayer(p);
                    LAST_CHUNK.put(p.getUUID(), now);
                }
            }
        });

        System.out.println("[DominionCraft] Initialization complete.");
    }

    /* ───────────────────── функции HUD-синхронизации ────────────────── */

    /** Собрать актуальные данные о городе/чанке и отправить игроку. */
    private void pushHudToPlayer(ServerPlayer p) {
        ServerLevel lvl = p.serverLevel();
        ChunkPos pos    = new ChunkPos(p.blockPosition());
        TownData d      = TownData.get(lvl);
        Town town       = d.getTownByChunk(pos);

        String territory = (town == null) ? "Дикие земли" : "Город";
        String townName  = (town == null) ? "—" : town.getName();
        String rank      = (town == null) ? "—" : town.getRank(p.getUUID()).name();
        String mayor     = (town == null) ? "—" : town.getMayorName(p.getServer());
        int    claims    = (town == null) ? 0  : town.getClaimCount();
        boolean chunkPvp = (town == null) ? true : town.isChunkPvp(pos);

        sendHudUpdate(p, territory, townName, rank, mayor, claims, chunkPvp);
    }

    /** Отправить клиенту пакет HUD-обновления. */
    public static void sendHudUpdate(ServerPlayer player,
                                     String territory, String town, String rank,
                                     String mayor, int claims, boolean chunkPvp) {

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(territory);
        buf.writeUtf(town);
        buf.writeUtf(rank);
        buf.writeUtf(mayor);
        buf.writeInt(claims);
        buf.writeBoolean(chunkPvp);

        ServerPlayNetworking.send(player, HUD_PACKET, buf);
    }
}
