package org.worldcraft.dominioncraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;

import org.worldcraft.dominioncraft.Dominioncraft;

/**
 * Принимает пакет hud_update от сервера и
 * кеширует данные для отрисовки HUD-панели.
 */
public class HudClient implements ClientModInitializer {

    /* — актуальные значения, которые читает HudOverlay — */
    public static String territory = "…";
    public static String town      = "…";
    public static String rank      = "…";
    public static String mayor     = "…";
    public static boolean chunkPvp = false;

    @Override
    public void onInitializeClient() {
        /* регистрируем глобальный приёмник пакета */
        ClientPlayNetworking.registerGlobalReceiver(
                Dominioncraft.HUD_PACKET,
                (client, handler, buf, responder) -> handle(buf));
    }

    /** Читает пакет в рабочем (клиентском) потоке. */
    private static void handle(FriendlyByteBuf buf) {
        /* читаем в переменные */
        final String terr = buf.readUtf();
        final String twn  = buf.readUtf();
        final String rng  = buf.readUtf();
        final String myr  = buf.readUtf();
        final boolean pvp = buf.readBoolean();

        /* вызываем на главном потоке клиента, чтобы избежать гонок */
        Minecraft.getInstance().execute(() -> {
            territory = terr;
            town      = twn;
            rank      = rng;
            mayor     = myr;
            chunkPvp  = pvp;
        });
    }
}
