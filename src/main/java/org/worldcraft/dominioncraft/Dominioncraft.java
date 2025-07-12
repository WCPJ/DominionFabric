package org.worldcraft.dominioncraft;

import net.fabricmc.api.ModInitializer;

/* Fabric API */
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/* Mojang API */
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/* Мод — города */
import org.worldcraft.dominioncraft.command.TownCommands;
import org.worldcraft.dominioncraft.town.*;

public class Dominioncraft implements ModInitializer {

    /* Канал для HUD-пакета */
    public static final ResourceLocation HUD_PACKET =
            new ResourceLocation("dominioncraft", "hud_update");

    /* ──────────────── INIT ──────────────── */

    @Override
    public void onInitialize() {

        /* /town-команды */
        CommandRegistrationCallback.EVENT.register(
                (dispatcher, registry, env) -> TownCommands.register(dispatcher));
        TownProtectionEvents.register();
        /* ------ блокировка действий (build / container / interact / break) ------ */


        /* ------ HUD-синхронизация ------ */

        /* при логине */
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                pushHudToPlayer(handler.getPlayer()));

        /* каждые 20 тиков (1 с) */
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer p : server.getPlayerList().getPlayers()) pushHudToPlayer(p);
        });

        System.out.println("[DominionCraft] Инициализация завершена.");
    }

    /* ──────────────── HUD helpers ──────────────── */

    /** Собирает данные и шлёт пакет одному игроку. */
    private void pushHudToPlayer(ServerPlayer p) {
        ServerLevel lvl = p.serverLevel();
        ChunkPos pos = new ChunkPos(p.blockPosition());
        TownData d = TownData.get(lvl);

        /* территория чанка */
        Town chunkTown = d.getTownByChunk(pos);
        String territory = (chunkTown == null) ? "Дикие земли" : chunkTown.getName();
        boolean chunkPvp = (chunkTown == null) ? true : chunkTown.isChunkPvp(pos);

        /* город игрока */
        Town playerTown = d.getTownOfPlayer(p.getUUID());
        String townName = (playerTown == null) ? "Без города" : playerTown.getName();
        String rank = (playerTown == null) ? "—" : playerTown.getRank(p.getUUID()).name();
        String mayor = (playerTown == null) ? "—" : playerTown.getMayorName(p.getServer());

        sendHudUpdate(p, territory, townName, rank, mayor, chunkPvp);
    }

    /** Формирует и отправляет HUD-пакет. */
    public static void sendHudUpdate(ServerPlayer player,
                                     String territory, String town,
                                     String rank, String mayor,
                                     boolean chunkPvp) {
        FriendlyByteBuf buf = PacketByteBufs.create();
        buf.writeUtf(territory);
        buf.writeUtf(town);
        buf.writeUtf(rank);
        buf.writeUtf(mayor);
        buf.writeBoolean(chunkPvp);
        ServerPlayNetworking.send(player, HUD_PACKET, buf);
    }

}