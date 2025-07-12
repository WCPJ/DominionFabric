package org.worldcraft.dominioncraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Рисует информ-панель Dominion HUD в левом-верхнем углу.
 * Читает данные из {@link HudClient} и обновляется каждый кадр.
 */
public final class HudOverlay implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(HudOverlay::render);
    }

    /* ------------------------------------------------------------------ */
    /*                              RENDER                                */
    /* ------------------------------------------------------------------ */
    private static void render(GuiGraphics g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Font font = mc.font;

        /* ── размеры и позиция ── */
        int width  = 140;
        int height = 80;  // Увеличено с 72 до 80, чтобы последняя строка (PvP) не вылазила за рамку
        int x = 8;        // 8 px от левого края
        int y = 8;        // 8 px от верхнего края

        /* ── фон + рамка ── */
        g.fill(x - 3, y - 3, x + width + 3, y + height + 3, 0xA0000000); // полупрозрачный фон
        int col = 0xFFCC9900;                                            // золото
        g.fill(x - 3, y - 3, x + width + 3, y - 2, col);                 // верх
        g.fill(x - 3, y + height + 2, x + width + 3, y + height + 3, col); // низ
        g.fill(x - 3, y - 3, x - 2, y + height + 3, col);                // левый
        g.fill(x + width + 2, y - 3, x + width + 3, y + height + 3, col); // правый

        /* ── заголовок ── */
        g.drawString(font,
                Component.literal(ChatFormatting.GOLD + "" + ChatFormatting.BOLD + "Dominion HUD"),
                x + 6, y + 6, 0xFFFFFF, false);

        /* ── информационные строки ── */
        int dy = y + 22;

        drawLine(g, font, "Территория:", HudClient.territory, x, dy);      dy += 13;
        drawLine(g, font, "Ваш город:",  HudClient.town,      x, dy);      dy += 13;
        drawLine(g, font, "Ваш ранг:",   HudClient.rank,      x, dy);      dy += 13;
        drawLine(g, font, "Мэр:",        HudClient.mayor,     x, dy);      dy += 13;
        drawLine(g, font, "PvP чанка:",
                HudClient.chunkPvp ? ChatFormatting.GREEN + "ON" : ChatFormatting.RED + "OFF",
                x, dy);
    }

    /** Рисует одну строку ключ-значение. */
    private static void drawLine(GuiGraphics g, Font font, String key, String val, int x, int y) {
        g.drawString(font, Component.literal("§7" + key), x + 6, y, 0xFFFFFF, false);
        g.drawString(font, Component.literal("§f" + val), x + 74, y, 0xFFFFFF, false);
    }
}