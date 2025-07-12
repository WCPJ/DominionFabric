/* ========================================================================= *
 *  file: org/worldcraft/dominioncraft/mixin/LivingEntityMixin.java          *
 *  purpose: тотальная блокировка нежелательного урона                        *
 * ========================================================================= */
package org.worldcraft.dominioncraft.mixin;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ChunkPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import org.worldcraft.dominioncraft.town.*;

/**
 * ГЛАВНЫЙ страж: блокирует любой урон, который
 * нарушает правила PvP и защиты мирных мобов.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {

    /* ------------------------------------------------------------------ */
    /*                             HURT                                   */
    /* ------------------------------------------------------------------ */
    @Inject(method = "hurt", at = @At("HEAD"), cancellable = true)
    private void dominioncraft$enforceTownRules(DamageSource src, float amount,
                                                CallbackInfoReturnable<Boolean> cir) {

        /* --- контекст --- */
        LivingEntity victim = (LivingEntity) (Object) this;
        if (victim.level().isClientSide()) return;          // работаем только на сервере

        Player attacker = findAttacker(src);
        if (attacker == null) return;                       // урон не от игрока

        /* ------------------------------------------------------------------
         * 1. PvP-блок: игрок → игрок
         * ------------------------------------------------------------------ */
        if (victim instanceof ServerPlayer vPlayer) {
            if (!isPvpAllowed(vPlayer, attacker)) {
                deny(attacker, "§cPvP запрещено здесь.");
                cir.setReturnValue(false);
                cir.cancel();
            }
            return;                                         // для PvP дальше не идём
        }

        /* ------------------------------------------------------------------
         * 2. Защита мирных существ
         * ------------------------------------------------------------------ */
        if (isPeaceful(victim) && !hasAnimalPerm(attacker, victim)) {
            deny(attacker, "§cНет права атаковать существ!");
            cir.setReturnValue(false);
            cir.cancel();
        }
    }

    /* ------------------------------------------------------------------ */
    /*                            HELPERS                                  */
    /* ------------------------------------------------------------------ */

    /** Ищем игрока-инициатора в DamageSource. */
    private static Player findAttacker(DamageSource src) {
        Entity trueSrc   = src.getEntity();        // «cause»
        Entity directSrc = src.getDirectEntity();  // непосредственный

        if (trueSrc instanceof Player p) return p;
        if (directSrc instanceof Player p) return p;

        if (directSrc instanceof Projectile proj && proj.getOwner() instanceof Player p)
            return p;

        // Другие случаи (AreaEffectCloud и т.п.) можно расширить при необходимости
        return null;
    }

    /** Проверка PvP-флагов обоих чанков. */
    private static boolean isPvpAllowed(ServerPlayer victim, Player attacker) {
        ServerLevel lvl = victim.serverLevel();
        TownData data   = TownData.get(lvl);

        ChunkPos vPos = new ChunkPos(victim.blockPosition());
        ChunkPos aPos = new ChunkPos(attacker.blockPosition());

        boolean vFlag = true;
        boolean aFlag = true;

        Town vTown = data.getTownByChunk(vPos);
        if (vTown != null) vFlag = vTown.isChunkPvp(vPos);

        Town aTown = data.getTownByChunk(aPos);
        if (aTown != null) aFlag = aTown.isChunkPvp(aPos);

        return vFlag && aFlag;   // PvP разрешено, только если оба чанка «ON»
    }

    /** Проверяет право ANIMAL у атакующего. */
    private static boolean hasAnimalPerm(Player attacker, LivingEntity victim) {
        ServerPlayer sp = (ServerPlayer) attacker;                     // гарантировано сервер
        ServerLevel lvl = sp.serverLevel();

        TownData data  = TownData.get(lvl);
        ChunkPos pos   = new ChunkPos(victim.blockPosition());
        Town     town  = data.getTownByChunk(pos);

        if (town == null) return true;                                 // дикие земли

        if (town.hasPermission(sp.getUUID(), TownPermission.ANIMAL)) return true;
        TownChunk tc = town.chunk(pos);
        return tc != null && tc.playerHas(sp.getUUID(), TownPermission.ANIMAL);
    }

    /** Мирная сущность = не игрок и не Monster. */
    private static boolean isPeaceful(Entity e) {
        return !(e instanceof Player) && !(e instanceof Monster);
    }

    private static void deny(Player p, String msg) {
        if (p instanceof ServerPlayer sp)
            sp.displayClientMessage(Component.literal(msg), true);
    }
}
