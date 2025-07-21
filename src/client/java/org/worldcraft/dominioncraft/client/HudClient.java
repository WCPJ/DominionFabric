/* ===================================================================== *
 *  file: org/worldcraft/dominioncraft/client/HudClient.java             *
 *  desc: клиентский приёмник HUD‑пакета (7 полей)                       *
 * ===================================================================== */
package org.worldcraft.dominioncraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import org.worldcraft.dominioncraft.Dominioncraft;

/**
 * Получает пакет {@code hud_update} от сервера и кеширует значения,
 * которые читает {@link HudOverlay} при отрисовке.
 */
public final class HudClient implements ClientModInitializer {

    /* ── публичные кэш‑поля, читаются в HudOverlay ── */
    public static String territory   = "…";
    public static String town        = "…";
    public static String townRank    = "…";
    public static String mayor       = "…";
    public static String nation      = "…";
    public static String nationRank  = "…";
    public static boolean chunkPvp   = false;

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(
                Dominioncraft.HUD_PACKET,
                (client, handler, buf, responder) -> handle(buf));
    }

    /* ───────────────────────── handle ───────────────────────── */
    private static void handle(FriendlyByteBuf buf) {
        /* порядок ДОЛЖЕН совпадать с sendHudUpdate(...) на сервере */
        final String terr  = buf.readUtf();
        final String twn   = buf.readUtf();
        final String tRank = buf.readUtf();
        final String myr   = buf.readUtf();
        final String nat   = buf.readUtf();
        final String nRank = buf.readUtf();
        final boolean pvp  = buf.readBoolean();

        /* обновляем поля на главном клиентском потоке */
        Minecraft.getInstance().execute(() -> {
            territory  = terr;
            town       = twn;
            townRank   = tRank;
            mayor      = myr;
            nation     = nat;
            nationRank = nRank;
            chunkPvp   = pvp;
        });
    }
}
