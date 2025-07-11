package org.worldcraft.dominioncraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import org.worldcraft.dominioncraft.Dominioncraft;

/**
 * Принимаем пакет HUD с сервера и кешируем данные для отрисовки.
 */
public class HudClient implements ClientModInitializer {

    /* — публичные поля, которые читает HUD-рендер — */
    public static String territory = "…";
    public static String town      = "…";
    public static String rank      = "…";
    public static String mayor     = "…";
    public static int    claims    = 0;
    public static boolean chunkPvp = false;

    @Override
    public void onInitializeClient() {

        /* регистрация приёмника */
        ClientPlayNetworking.registerGlobalReceiver(
                Dominioncraft.HUD_PACKET,
                (client, handler, buf, responder) -> handle(buf));
    }

    private static void handle(FriendlyByteBuf buf) {          // читаем то, что сервер прислал
        final String terr = buf.readUtf();
        final String twn  = buf.readUtf();
        final String rng  = buf.readUtf();
        final String myr  = buf.readUtf();
        final int    clm  = buf.readInt();
        final boolean pvp = buf.readBoolean();

        net.minecraft.client.Minecraft.getInstance().execute(() -> {
            territory = terr;
            town      = twn;
            rank      = rng;
            mayor     = myr;
            claims    = clm;
            chunkPvp  = pvp;
        });
    }
}
