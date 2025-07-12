package org.worldcraft.dominioncraft;

// ─────────────────────── Fabric API ───────────────────────
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;


// ─────────────────────── Minecraft (Mojang mappings) ───────
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.HoeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.decoration.*;
import net.minecraft.world.entity.vehicle.ChestBoat;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.entity.vehicle.MinecartHopper;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.phys.EntityHitResult;

// ─────────────────────── Towny logic ───────────────────────
import org.worldcraft.dominioncraft.town.Town;
import org.worldcraft.dominioncraft.town.TownChunk;
import org.worldcraft.dominioncraft.town.TownData;
import org.worldcraft.dominioncraft.town.TownPermission;

public final class TownProtectionEvents {

    private TownProtectionEvents() {}

    // ────────────────────────── entrypoint ──────────────────────────
    public static void register() {

        /* ───── RIGHT-CLICK BLOCK (build / container / interact) ───── */
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world.isClientSide()) return InteractionResult.PASS;

            BlockPos pos      = hit.getBlockPos();
            BlockState state  = world.getBlockState(pos);
            TownPermission p  = switchBlockPermission(state, player.getItemInHand(hand));

            if (!hasAccess((ServerLevel) world, player, new ChunkPos(pos), p)) {
                deny(player);
                ((ServerPlayer) player).connection.send(new ClientboundBlockUpdatePacket(pos, state));
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        /* ───── USE ITEM (hoe, bone meal, семена …) ───── */
        UseItemCallback.EVENT.register((player, world, hand) -> {
            ItemStack stack = player.getItemInHand(hand);
            if (world.isClientSide()) return InteractionResultHolder.pass(stack);

            TownPermission p = switchItemPermission(stack);
            if (!hasAccess((ServerLevel) world, player, new ChunkPos(player.blockPosition()), p)) {
                deny(player);
                return InteractionResultHolder.fail(stack);
            }
            return InteractionResultHolder.pass(stack);
        });

        /* ───── RIGHT-CLICK ENTITY (животные, жители, рамки …) ───── */
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClientSide()) return InteractionResult.PASS;

            TownPermission p = switchEntityPermission(entity);
            if (!hasAccess((ServerLevel) world, player, new ChunkPos(entity.blockPosition()), p)) {
                deny(player);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        /* ───── LEFT-CLICK BLOCK (break / farm) ───── */
        AttackBlockCallback.EVENT.register((player, world, hand, pos, dir) -> {
            if (world.isClientSide()) return InteractionResult.PASS;

            BlockState state  = world.getBlockState(pos);
            TownPermission p  = isFarmBlock(state) ? TownPermission.FARM : TownPermission.BREAK;

            if (!hasAccess((ServerLevel) world, player, new ChunkPos(pos), p)) {
                deny(player);
                ((ServerPlayer) player).connection.send(new ClientboundBlockUpdatePacket(pos, state));
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });

        /* ───── окончательная проверка ломания ───── */
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, be) -> {
            if (world.isClientSide()) return true;
            TownPermission p = isFarmBlock(state) ? TownPermission.FARM : TownPermission.BREAK;
            return hasAccess((ServerLevel) world, player, new ChunkPos(pos), p);
        });

        /* ───── АТАКА СУЩНОСТИ (животные/рамки — без PvP) ───── */
        AttackEntityCallback.EVENT.register((attacker, world, hand, target, hit) -> {
            if (world.isClientSide()) return InteractionResult.PASS;

            if (isProtectedEntity(target)
                    && !hasAccess((ServerLevel) world, attacker,
                    new ChunkPos(target.blockPosition()), TownPermission.ANIMAL)) {
                deny(attacker);
                return InteractionResult.FAIL;
            }
            return InteractionResult.PASS;
        });



    }

    // ─────────────────────────── helpers ────────────────────────────
    private static boolean hasAccess(ServerLevel lvl, Player pl, ChunkPos chunk, TownPermission perm) {
        TownData data   = TownData.get(lvl);
        Town town       = data.getTownByChunk(chunk);
        if (town == null)                           return true; // дикие земли
        if (town.hasPermission(pl.getUUID(), perm)) return true; // ранговое право
        TownChunk tc = town.chunk(chunk);
        return tc != null && tc.playerHas(pl.getUUID(), perm);   // персональный override
    }

    private static void deny(Player p)            { deny(p, "§cНет прав."); }
    private static void deny(Player p, String m)  { p.displayClientMessage(Component.literal(m), true); }

    // ---------- определить нужное PERMISSION по блоку / предмету ----------
    private static TownPermission switchBlockPermission(BlockState st, ItemStack held) {
        Block b = st.getBlock();

        // контейнеры
        if (b instanceof ChestBlock || b instanceof BarrelBlock || b instanceof ShulkerBoxBlock
                || b instanceof EnderChestBlock || b instanceof AbstractFurnaceBlock
                || b instanceof HopperBlock || b instanceof DispenserBlock) {
            return TownPermission.CONTAINER;
        }
        // фермерство
        if (isFarmBlock(st)) return TownPermission.FARM;

        // простое взаимодействие
        if (b instanceof DoorBlock || b instanceof TrapDoorBlock || b instanceof FenceGateBlock
                || b instanceof LeverBlock || b instanceof ButtonBlock || b instanceof PressurePlateBlock
                || b instanceof NoteBlock || b instanceof BellBlock || b instanceof RepeaterBlock
                || b instanceof ComparatorBlock || b instanceof CakeBlock || b instanceof LecternBlock) {
            return TownPermission.INTERACT;
        }

        // если в руке блок — BUILD, иначе просто INTERACT
        return held.getItem() instanceof BlockItem ? TownPermission.BUILD : TownPermission.INTERACT;
    }

    private static TownPermission switchItemPermission(ItemStack st) {
        if (st.getItem() instanceof HoeItem || st.is(Items.BONE_MEAL)
                || (st.getItem() instanceof BlockItem bi && bi.getBlock() instanceof CropBlock)) {
            return TownPermission.FARM;
        }
        if (st.is(Items.WHEAT) || st.is(Items.CARROT) || st.is(Items.POTATO)
                || st.is(Items.BEETROOT) || st.is(Items.WHEAT_SEEDS)) {
            return TownPermission.ANIMAL;
        }
        return TownPermission.INTERACT;
    }

    private static TownPermission switchEntityPermission(Entity e) {
        if (e instanceof MinecartChest || e instanceof MinecartHopper
                || e instanceof MinecartFurnace || e instanceof ChestBoat) {
            return TownPermission.CONTAINER;
        }
        if (isProtectedEntity(e)) return TownPermission.ANIMAL;
        return TownPermission.INTERACT;
    }

    // ---------- util-checks ----------
    private static boolean isFarmBlock(BlockState st) {
        Block b = st.getBlock();
        return b instanceof FarmBlock || b instanceof CropBlock
                || b instanceof StemBlock || b instanceof CocoaBlock
                || b instanceof SweetBerryBushBlock || b instanceof NetherWartBlock;
    }

    private static boolean isProtectedEntity(Entity e) {
        return e instanceof Animal || e instanceof Villager
                || e instanceof ItemFrame || e instanceof GlowItemFrame
                || e instanceof Painting || e instanceof ArmorStand;
    }
}
