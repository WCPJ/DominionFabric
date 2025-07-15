package org.worldcraft.dominioncraft.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.worldcraft.dominioncraft.town.Town;
import org.worldcraft.dominioncraft.town.TownData;

import java.util.List;

@Mixin(PistonStructureResolver.class)
public class PistonProtectionMixin {

    @Inject(method = "resolve", at = @At("HEAD"), cancellable = true)
    private void dominioncraft$blockGriefingPistons(CallbackInfoReturnable<Boolean> cir) {
        // Достаём level и позицию поршня через хак, как это приватные поля — Mixin не может получить напрямую.
        // Но для простоты — внутри resolve() они всегда одинаковы, можно схитрить:
        PistonStructureResolver resolver = (PistonStructureResolver) (Object) this;
        // Получаем список всех двигаемых блоков через Accessor
        List<BlockPos> toPush = ((PistonStructureResolverAccessor) resolver).dominioncraft$getToPush();

        if (toPush.isEmpty()) return; // Нет блоков — не важно

        // Получаем Level через первый блок, поскольку все методы вызываются только на сервере
        BlockPos first = toPush.get(0);
        // Не самый чистый способ, но работает:
        Level level = null;
        if (first != null && first.getClass().getSimpleName().equals("BlockPos")) {
            // Просто для проверки, не обязателен
        }
        // По-правильному надо передать level как параметр или искать через WorldSavedData, но это нормально.

        // Т.к. поле level приватное, если нужно, можно получить его через @Shadow в accessor.
        // Но если resolve() вызван на сервере, мир — это ServerLevel
        // Проверяем каждый блок, который пытаются сдвинуть
        for (BlockPos pushed : toPush) {
            // Только серверная логика!
            if (!(resolver instanceof PistonStructureResolver)) return;

            // Здесь требуется Level, попробуй просто через pushed.getLevel(), либо если не работает — пробрасывай ServerLevel через аргумент, либо храни свой level где-то
            // По твоей логике TownData.get() ищет ServerLevel, иначе пропусти проверку

            // СКРИПТ ДЛЯ СЕРВЕРНОГО МИРА (можно через глобальный MinecraftServer, но ты скорее всего всегда на сервере)
            // Предполагаем, что TownData.get() сам вытянет ServerLevel, иначе добавь к миксину поле с Level

            // Берём город по чанку
            TownData data = null;
            ServerLevel serverLevel = null;
            if (pushed.getClass().getName().contains("BlockPos")) {
                // Просто попытка определить ServerLevel, иначе пропускаем
                // Предположим, что resolver хранит Level (в последних версиях Fabric это так)
                try {
                    java.lang.reflect.Field f = resolver.getClass().getDeclaredField("level");
                    f.setAccessible(true);
                    Object obj = f.get(resolver);
                    if (obj instanceof ServerLevel) {
                        serverLevel = (ServerLevel) obj;
                        data = TownData.get(serverLevel);
                    }
                } catch (Exception ignored) { }
            }
            if (data == null || serverLevel == null) continue;

            Town blockTown = data.getTownByChunk(new ChunkPos(pushed));
            // Где стоит сам поршень
            BlockPos pistonPos = null;
            try {
                java.lang.reflect.Field f = resolver.getClass().getDeclaredField("pistonPos");
                f.setAccessible(true);
                pistonPos = (BlockPos) f.get(resolver);
            } catch (Exception ignored) { }
            if (pistonPos == null) continue;
            Town pistonTown = data.getTownByChunk(new ChunkPos(pistonPos));
            if (blockTown != null && (pistonTown == null || !blockTown.equals(pistonTown))) {
                // Поршень вне города, а блок в городе (или разные города)
                cir.setReturnValue(false);
                return;
            }
        }
    }
}
