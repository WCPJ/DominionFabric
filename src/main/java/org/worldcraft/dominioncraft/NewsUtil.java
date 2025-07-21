package org.worldcraft.dominioncraft;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import org.worldcraft.dominioncraft.nation.Nation;
import org.worldcraft.dominioncraft.town.Town;
import org.worldcraft.dominioncraft.town.TownData;
import java.util.UUID;

public final class NewsUtil {

    // Оповестить всех членов нации
    public static void broadcastNation(MinecraftServer server, Nation nation, String message) {
        TownData townData = TownData.get(server.overworld());
        for (UUID townId : nation.getTowns()) {
            Town town = townData.getTown(townId);
            if (town != null) {
                for (UUID uuid : town.getMembers()) {
                    ServerPlayer p = server.getPlayerList().getPlayer(uuid);
                    if (p != null) {
                        p.sendSystemMessage(Component.literal(message));
                    }
                }
            }
        }
    }

    // Оповестить всех жителей города
    public static void broadcastTown(MinecraftServer server, Town town, String message) {
        for (UUID uuid : town.getMembers()) {
            ServerPlayer p = server.getPlayerList().getPlayer(uuid);
            if (p != null) {
                p.sendSystemMessage(Component.literal(message));
            }
        }
    }

    // Глобальная новость (для всех игроков онлайн)
    public static void broadcastGlobal(MinecraftServer server, String message) {
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(message));
        }
    }
}