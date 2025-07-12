package org.worldcraft.dominioncraft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.monster.Monster;   // ← интерфейс всех враждебных мобов
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ChunkPos;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.worldcraft.dominioncraft.town.*;

@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    @Inject(method = "onHit", at = @At("HEAD"), cancellable = true)
    private void dominioncraft$protectPeacefulEntities(HitResult hit, CallbackInfo ci) {
        Projectile self = (Projectile)(Object) this;

        // Только сервер
        if (self.level().isClientSide()) return;

        // Владелец — игрок?
        Entity owner = self.getOwner();
        if (!(owner instanceof ServerPlayer attacker)) return;

        // Попадание в Entity?
        if (hit.getType() != HitResult.Type.ENTITY) return;
        EntityHitResult ehr = (EntityHitResult) hit;
        Entity target = ehr.getEntity();

        // Защищаем ТОЛЬКО мирные сущности
        if (!isProtectedEntity(target)) return;

        ServerLevel level = (ServerLevel) self.level();
        ChunkPos chunk = new ChunkPos(target.blockPosition());

        if (!playerHasAnimalPerm(level, attacker, chunk)) {
            attacker.displayClientMessage(
                    Component.literal("§cНет прав на взаимодействие с этим существом!"), true);
            self.discard();   // удаляем снаряд
            ci.cancel();
        }
    }

    /* ─────────── helpers ─────────── */

    private static boolean playerHasAnimalPerm(ServerLevel lvl, Player pl, ChunkPos chunk) {
        TownData data = TownData.get(lvl);
        Town     town = data.getTownByChunk(chunk);
        if (town == null) return true;                                       // дикие земли
        if (town.hasPermission(pl.getUUID(), TownPermission.ANIMAL)) return true;
        TownChunk tc = town.chunk(chunk);
        return tc != null && tc.playerHas(pl.getUUID(), TownPermission.ANIMAL);
    }

    /**
     * Мирная сущность — всё, что не игрок и не Monster.
     * Включает животных, жителей, рамы, картины, стойки, лодки и т.д.
     */
    private static boolean isProtectedEntity(Entity e) {
        return !(e instanceof Player) && !(e instanceof Monster);
    }
}
