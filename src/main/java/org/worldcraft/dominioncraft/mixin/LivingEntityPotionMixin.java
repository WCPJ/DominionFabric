package org.worldcraft.dominioncraft.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.level.ChunkPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.worldcraft.dominioncraft.town.*;

/**
 * Если игрок бросает вредное зелье / смесь на чужой территории,
 * урон и негативные эффекты по мирным сущностям отменяются,
 * пока у игрока нет права {@link TownPermission#ANIMAL}.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityPotionMixin {

    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void dominioncraft$blockPotionGrief(DamageSource source, float amount,
                                                CallbackInfoReturnable<Boolean> cir) {

        /* Цель — этот LivingEntity */
        LivingEntity victim = (LivingEntity)(Object)this;

        /* Только сервер & только мирные: не игрок и не Monster */
        if (victim.level().isClientSide()
                || victim instanceof Player
                || victim instanceof Monster) return;

        /* Источник — сплэш/лингер-зелье, брошенное игроком? */
        Entity direct = source.getDirectEntity();
        if (!(direct instanceof ThrownPotion potion
                && potion.getOwner() instanceof ServerPlayer attacker)) return;

        ServerLevel level = (ServerLevel) victim.level();
        ChunkPos    chunk = new ChunkPos(victim.blockPosition());

        /* Проверка права ANIMAL у бросающего */
        if (!hasAccess(level, attacker, chunk, TownPermission.ANIMAL)) {
            // отменяем и урон, и эффекты
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    /* ───────── helper ───────── */

    private static boolean hasAccess(ServerLevel lvl, ServerPlayer pl,
                                     ChunkPos chunk, TownPermission perm) {

        TownData data = TownData.get(lvl);
        Town town     = data.getTownByChunk(chunk);
        if (town == null) return true;                           // дикие земли
        if (town.hasPermission(pl.getUUID(), perm)) return true; // ранговое
        TownChunk tc = town.chunk(chunk);
        return tc != null && tc.playerHas(pl.getUUID(), perm);   // override
    }
}
