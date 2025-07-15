package org.worldcraft.dominioncraft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(PistonStructureResolver.class)
public interface PistonStructureResolverAccessor {
    @Accessor("toPush")
    List<BlockPos> dominioncraft$getToPush();
}
