package org.worldcraft.dominioncraft.bluemap;

import net.minecraft.server.MinecraftServer;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BlueMapAutoSync {
    private static ScheduledExecutorService executor;

    public static void start(MinecraftServer server) {
        if (executor != null) return;
        executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleAtFixedRate(() -> {
            BlueMapTownMarkers.updateIfPresent(server);
        }, 5, 10, TimeUnit.SECONDS); // каждые 10 секунд
    }

    public static void stop() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }
}
