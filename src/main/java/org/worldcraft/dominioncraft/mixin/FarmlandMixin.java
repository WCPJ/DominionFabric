package org.worldcraft.dominioncraft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.worldcraft.dominioncraft.town.*;

/**
 * Запрещает топтать грядки без права FARM.
 */
@Mixin(FarmBlock.class)
public abstract class FarmlandMixin {

    /** отменяем превращение farmland → dirt */
    @Inject(method = "fallOn",
            at = @At("HEAD"),
            cancellable = true)
    private void dominioncraft$noTrample(Level level, BlockState state,
                                         BlockPos pos, Entity entity, float fallDistance,
                                         CallbackInfo ci) {

        // интересует только игрок
        if (!(entity instanceof ServerPlayer player)) return;
        if (!(level instanceof ServerLevel server))   return;

        ChunkPos chunk = new ChunkPos(pos);
        if (!hasAccess(server, player, chunk, TownPermission.FARM)) {
            ci.cancel(); // блокируем изменение блока
        }
    }

    /* ───── helpers ───── */

    private static boolean hasAccess(ServerLevel lvl, ServerPlayer pl,
                                     ChunkPos c, TownPermission perm) {
        TownData data = TownData.get(lvl);
        Town town     = data.getTownByChunk(c);
        if (town == null) return true;
        if (town.hasPermission(pl.getUUID(), perm)) return true;
        TownChunk tc = town.chunk(c);
        return tc != null && tc.playerHas(pl.getUUID(), perm);
    }
}
