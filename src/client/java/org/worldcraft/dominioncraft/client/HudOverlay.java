/* ===================================================================== *
 *  file: org/worldcraft/dominioncraft/client/HudOverlay.java            *
 *  desc: клиентский HUD с данными города + нации                        *
 * ===================================================================== */
package org.worldcraft.dominioncraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Рисует информ‑панель Dominion HUD в левом‑верхнем углу.
 * Данные берёт из {@link HudClient} (обновляются сервером каждую секунду).
 */
public final class HudOverlay implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(HudOverlay::render);
    }

    /* ───────────────────────── render ───────────────────────── */
    private static void render(GuiGraphics g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Font font = mc.font;

        /* рамка */
        int width  = 140;
        int height = 106;      // 7 инфо‑строк = 6*13 px + заголовок
        int x = 8, y = 8;

        g.fill(x - 3, y - 3, x + width + 3, y + height + 3, 0xA0000000);
        int col = 0xFFCC9900;
        g.fill(x - 3, y - 3, x + width + 3, y - 2, col);
        g.fill(x - 3, y + height + 2, x + width + 3, y + height + 3, col);
        g.fill(x - 3, y - 3, x - 2, y + height + 3, col);
        g.fill(x + width + 2, y - 3, x + width + 3, y + height + 3, col);

        /* заголовок */
        g.drawString(font,
                Component.literal(ChatFormatting.GOLD + "" + ChatFormatting.BOLD + "Dominion HUD"),
                x + 6, y + 6, 0xFFFFFF, false);

        int dy = y + 22;   // первая строка инфо

        drawLine(g, font, "Территория:",   HudClient.territory,   x, dy); dy += 13;
        drawLine(g, font, "Ваш город:",    HudClient.town,        x, dy); dy += 13;
        drawLine(g, font, "Ранг (город):", HudClient.townRank,    x, dy); dy += 13;
        drawLine(g, font, "Мэр:",          HudClient.mayor,       x, dy); dy += 13;
        drawLine(g, font, "Нация:",        HudClient.nation,      x, dy); dy += 13;
        drawLine(g, font, "Ранг (нация):", HudClient.nationRank,  x, dy); dy += 13;
        drawLine(g, font, "PvP чанка:",
                HudClient.chunkPvp ? ChatFormatting.GREEN + "ON"
                        : ChatFormatting.RED   + "OFF",
                x, dy);
    }

    /** одна строка "ключ: значение" */
    private static void drawLine(GuiGraphics g, Font font,
                                 String key, String val, int x, int y) {
        g.drawString(font, Component.literal("§7" + key),
                x + 6, y, 0xFFFFFF, false);
        g.drawString(font, Component.literal("§f" + val),
                x + 74, y, 0xFFFFFF, false);
    }
}
