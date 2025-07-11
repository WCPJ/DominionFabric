package org.worldcraft.dominioncraft.town;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import java.util.function.Consumer;

/**
 * Утилитный класс-«шлюз» для доступа к {@link TownData} любого мира.
 *
 * <p>Используется там, где контекст однозначно не привязан
 * к конкретному {@link ServerLevel}, но нужно пройтись по всем мирам
 * (например, при сохранении/загрузке, глобальных командах ивентов).</p>
 *
 * <p>Экземпляры не создаются; все методы статические.</p>
 */
public final class TownDataManager {

    /** Закрытый конструктор, чтобы запретить инстанцирование. */
    private TownDataManager() {}

    /* ------------------------------------------------------------------ */
    /*                             helpers                                */
    /* ------------------------------------------------------------------ */

    /**
     * Быстро получить {@link TownData} по конкретному миру.
     *
     * @param level мир-сервер (Overworld, Nether, …)
     * @return объект-хранилище Town’ов для этого мира
     */
    public static TownData data(ServerLevel level) {
        return TownData.get(level);
    }

    /**
     * Выполнить действие для {@code TownData} каждого мира сервера.
     *
     * @param server  экземпляр {@link MinecraftServer}
     * @param action  лямбда, принимающая {@code TownData}
     */
    public static void forEachWorld(MinecraftServer server,
                                    Consumer<TownData> action) {

        for (ServerLevel lvl : server.getAllLevels()) {
            action.accept(data(lvl));
        }
    }
}
