/* ===================================================================== *
 *  file: org/worldcraft/dominioncraft/Dominioncraft.java                *
 *  desc: главный мод‑инишиалайзер + HUD‑синхронизация                    *
 * ===================================================================== */
package org.worldcraft.dominioncraft;

import net.fabricmc.api.ModInitializer;

/* Fabric API */
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/* Mojang / MC */
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/* Мод: города / нации */
import org.worldcraft.dominioncraft.bluemap.BlueMapAutoSync;
import org.worldcraft.dominioncraft.command.TownCommands;
import org.worldcraft.dominioncraft.nation.*;
import org.worldcraft.dominioncraft.town.*;

public class Dominioncraft implements ModInitializer {

    /** Канал, по которому сервер шлёт HUD‑обновления клиенту. */
    public static final ResourceLocation HUD_PACKET =
            new ResourceLocation("dominioncraft", "hud_update");

    /* ─────────────────────────── INIT ─────────────────────────── */
    @Override
    public void onInitialize() {

        /* команды /town и /nation */
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, dedicated, env) -> {
                    TownCommands.register(dispatcher);
                    NationCommands.register(dispatcher);
                });

        /* защита блоков/мобов */
        TownProtectionEvents.register();

        /* таймеры нации (выборы / референдум) */
        NationTimers.init();
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            BlueMapAutoSync.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            BlueMapAutoSync.stop();
        });

        /* HUD: при логине и каждую секунду */
        ServerPlayConnectionEvents.JOIN.register((handler, sender, srv) ->
                pushHudToPlayer(handler.getPlayer()));

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;   // раз в секунду
            for (ServerPlayer p : server.getPlayerList().getPlayers())
                pushHudToPlayer(p);
        });

        System.out.println("[DominionCraft] Инициализация завершена.");
    }

    /* ─────────────────────── HUD helper‑методы ─────────────────────── */

    /** Собирает данные и шлёт один HUD‑пакет игроку. */
    private void pushHudToPlayer(ServerPlayer p) {
        ServerLevel lvl = p.serverLevel();
        TownData td = TownData.get(lvl);
        NationData nd = NationData.get(lvl);

        /* ── территория чанка ── */
        ChunkPos  pos = new ChunkPos(p.blockPosition());
        Town      chunkTown = td.getTownByChunk(pos);
        String    territory = chunkTown == null ? "Дикие земли" : chunkTown.getName();
        boolean   chunkPvp  = chunkTown == null ? true : chunkTown.isChunkPvp(pos);

        /* ── город игрока ── */
        Town playerTown = td.getTownOfPlayer(p.getUUID());
        String townName   = playerTown == null ? "Без города" : playerTown.getName();
        String townRank   = playerTown == null ? "—"          : playerTown.getRank(p.getUUID()).name();
        String mayorName  = playerTown == null ? "—"          : playerTown.getMayorName(p.getServer());

        /* ── нация ── */
        String nationName = "Без нации";
        String nationRank = "—";
        if (playerTown != null && playerTown.getNation() != null) {
            Nation n = nd.get(playerTown.getNation());
            if (n != null) {
                nationName = n.getName();
                NationRank r = n.getRank(p.getUUID());
                nationRank  = (r == null) ? "—" : r.name();
            }
        }

        sendHudUpdate(p,
                territory, townName, townRank, mayorName,
                nationName, nationRank, chunkPvp);
    }

    /** Пакет: territory, town, townRank, mayor, nation, nationRank, pvp */
    public static void sendHudUpdate(ServerPlayer player,
                                     String territory,
                                     String town,
                                     String townRank,
                                     String mayor,
                                     String nation,
                                     String nationRank,
                                     boolean chunkPvp) {

        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(territory);
        buf.writeUtf(town);
        buf.writeUtf(townRank);
        buf.writeUtf(mayor);
        buf.writeUtf(nation);
        buf.writeUtf(nationRank);
        buf.writeBoolean(chunkPvp);

        ServerPlayNetworking.send(player, HUD_PACKET, buf);
    }
}
