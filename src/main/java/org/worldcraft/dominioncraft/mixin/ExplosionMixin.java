package org.worldcraft.dominioncraft.mixin;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.worldcraft.dominioncraft.town.*;

import java.util.HashSet;
import java.util.Set;

@Mixin(Explosion.class)
public abstract class ExplosionMixin {
    @Shadow @Final private Level level;
    @Shadow @Final private Entity source;

    @Inject(method = "explode", at = @At("HEAD"), cancellable = true)
    private void dominioncraft$protectExplosions(CallbackInfo ci) {
        // Только сервер
        if (!(level instanceof ServerLevel serverLevel)) return;

        Explosion explosion = (Explosion)(Object)this;

        // Собираем все чанки, которые будут реально разрушены
        Set<ChunkPos> affectedChunks = new HashSet<>();
        for (var blockPos : explosion.getToBlow()) {
            affectedChunks.add(new ChunkPos(blockPos));
        }

        TownData d = TownData.get(serverLevel);

        for (ChunkPos pos : affectedChunks) {
            Town t = d.getTownByChunk(pos);
            if (t == null) continue; // дикие земли

            // Глобальный/локальный запрет взрывов
            boolean allowExplosion = t.isChunkExplosion(pos);
            if (!allowExplosion) {
                if (source instanceof net.minecraft.server.level.ServerPlayer player) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§cВзрывы запрещены в этом городе!"), true
                    );
                }
                ci.cancel();
                return;
            }

            // Если инициатор игрок — проверяем права
            if (source instanceof net.minecraft.server.level.ServerPlayer player) {
                boolean allowed = t.hasPermission(player.getUUID(), TownPermission.EXPLOSION)
                        || (t.chunk(pos) != null && t.chunk(pos).playerHas(player.getUUID(), TownPermission.EXPLOSION));
                if (!allowed) {
                    player.displayClientMessage(
                            net.minecraft.network.chat.Component.literal("§cУ вас нет права на взрывы в этом городе!"), true
                    );
                    ci.cancel();
                    return;
                }
            }
        }
    }
}
