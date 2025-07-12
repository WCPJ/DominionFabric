
package org.worldcraft.dominioncraft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.item.*;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecartContainer;
import net.minecraft.world.entity.vehicle.ChestBoat;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.worldcraft.dominioncraft.town.*;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class UniversalInteractMixin {

    /* -------------------------------------------------------------- */
    /* 1) RIGHT-CLICK BLOCK / ПОПЫТКА ПОСТАВИТЬ БЛОК / ВЕДРО / ОГНИВО */
    /* -------------------------------------------------------------- */
    @Inject(method = "handleUseItemOn", at = @At("HEAD"), cancellable = true)
    private void dominioncraft$useItemOn(ServerboundUseItemOnPacket pkt, CallbackInfo ci) {

        ServerGamePacketListenerImpl conn = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = conn.player;
        ServerLevel level   = player.serverLevel();
        BlockPos pos        = pkt.getHitResult().getBlockPos();
        ItemStack held      = player.getItemInHand(pkt.getHand());
        BlockState state    = level.getBlockState(pos);

        /* Определяем требуемое право */
        TownPermission need = resolveBlockPermission(level, pos, state, held, true);

        if (!hasAccess(level, player, new ChunkPos(pos), need)) {
            deny(player);
            conn.send(new ClientboundBlockUpdatePacket(pos, state));   // откат клиенту
            ci.cancel();
        }
    }

    /* -------------------------------------------- */
    /* 2) RIGHT-CLICK AIR (handleUseItem)           */
    /*    Огниво, ведро, жемчуг, маг. посох и т.п.  */
    /* -------------------------------------------- */
    @Inject(method = "handleUseItem", at = @At("HEAD"), cancellable = true)
    private void dominioncraft$useItem(ServerboundUseItemPacket pkt, CallbackInfo ci) {

        ServerGamePacketListenerImpl conn = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = conn.player;
        ServerLevel level   = player.serverLevel();
        ItemStack   held    = player.getItemInHand(pkt.getHand());

        /* Всё, что потенциально меняет мир → BUILD;
           остальное — INTERACT (кидать жемчуг, пить зелье и т.д. разрешено) */
        if (isWorldAffectingItem(held)) {
            if (!hasAccess(level, player, new ChunkPos(player.blockPosition()), TownPermission.BUILD)) {
                deny(player);
                ci.cancel();
            }
        }
    }

    /* ------------------------------------------ */
    /* 3) INTERACT ENTITY (рамки, животные, лодки) */
    /* ------------------------------------------ */
    @Inject(method = "handleInteract", at = @At("HEAD"), cancellable = true)
    private void dominioncraft$interactEntity(ServerboundInteractPacket pkt, CallbackInfo ci) {

        ServerGamePacketListenerImpl conn = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = conn.player;
        ServerLevel level   = player.serverLevel();

        Entity target = pkt.getTarget(level);
        if (target == null || target instanceof Player) return;  // PvP → другой миксин

        /* контейнер-сущности требуют CONTAINER, остальные животные – ANIMAL */
        TownPermission need = isContainerEntity(target)
                ? TownPermission.CONTAINER
                : TownPermission.ANIMAL;

        if (!hasAccess(level, player, new ChunkPos(target.blockPosition()), need)) {
            deny(player);
            conn.send(new ClientboundTeleportEntityPacket(target));   // откат позиции
            ci.cancel();
        }
    }

    /* --------------------------------------- */
    /* 4) START_DESTROY_BLOCK / ломание блока  */
    /* --------------------------------------- */
    @Inject(method = "handlePlayerAction", at = @At("HEAD"), cancellable = true)
    private void dominioncraft$blockBreak(ServerboundPlayerActionPacket pkt, CallbackInfo ci) {

        if (pkt.getAction() != ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK) return;

        ServerGamePacketListenerImpl conn = (ServerGamePacketListenerImpl) (Object) this;
        ServerPlayer player = conn.player;
        ServerLevel level   = player.serverLevel();
        BlockPos pos        = pkt.getPos();
        BlockState state    = level.getBlockState(pos);

        if (!hasAccess(level, player, new ChunkPos(pos), TownPermission.BREAK)) {
            deny(player);
            conn.send(new ClientboundBlockUpdatePacket(pos, state));
            ci.cancel();
        }
    }

    /* ===================================================================== */
    /*                                HELPERS                                */
    /* ===================================================================== */

    /** Центральная проверка прав. */
    private static boolean hasAccess(ServerLevel lvl, ServerPlayer pl,
                                     ChunkPos chunk, TownPermission perm) {

        TownData data = TownData.get(lvl);
        Town town     = data.getTownByChunk(chunk);
        if (town == null) return true;                              // дикие земли

        if (town.hasPermission(pl.getUUID(), perm)) return true;    // рангово
        TownChunk tc = town.chunk(chunk);
        return tc != null && tc.playerHas(pl.getUUID(), perm);      // override
    }

    private static void deny(Player p) {
        p.displayClientMessage(Component.literal("§cНет прав!"), true);
    }

    /* ---------- право по блоку / предмету ---------- */
    private static TownPermission resolveBlockPermission(BlockGetter level, BlockPos pos,
                                                         BlockState state, ItemStack held,
                                                         boolean placingAttempt) {

        Block blk = state.getBlock();

        /* попытка поставить блок → BUILD */
        if (placingAttempt && held.getItem() instanceof BlockItem) return TownPermission.BUILD;

        if (held.getItem() instanceof BucketItem    // ведро воды/лавы
                || held.getItem() instanceof FlintAndSteelItem
                || held.getItem() instanceof FireChargeItem
                || held.getItem() instanceof SpawnEggItem)        // спавн-яйцо
            return TownPermission.BUILD;

        if (isFarmItem(held) || isFarmBlock(blk))   return TownPermission.FARM;

        if (level.getBlockEntity(pos) != null)      return TownPermission.CONTAINER;

        return TownPermission.INTERACT;             // по умолчанию
    }

    private static boolean isWorldAffectingItem(ItemStack st) {
        return st.getItem() instanceof BucketItem
                || st.getItem() instanceof FlintAndSteelItem
                || st.getItem() instanceof FireChargeItem
                || st.getItem() instanceof BlockItem
                || st.getItem() instanceof SpawnEggItem;
    }

    private static boolean isFarmBlock(Block b) {
        return b instanceof FarmBlock || b instanceof CropBlock
                || b instanceof StemBlock || b instanceof CocoaBlock
                || b instanceof SweetBerryBushBlock || b instanceof NetherWartBlock;
    }

    private static boolean isFarmItem(ItemStack st) {
        return st.getItem() instanceof HoeItem || st.is(Items.BONE_MEAL)
                || (st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock);
    }

    /** контейнер-сущности: любые AbstractMinecartContainer + ChestBoat */
    private static boolean isContainerEntity(Entity e) {
        return e instanceof AbstractMinecartContainer || e instanceof ChestBoat;
    }
}
