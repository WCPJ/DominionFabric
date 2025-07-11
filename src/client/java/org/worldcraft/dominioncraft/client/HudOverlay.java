package org.worldcraft.dominioncraft.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

/**
 * HUD-информер Towny (рисуется поверх игры справа-сверху).
 */
public class HudOverlay implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(HudOverlay::render);
    }

    private static void render(GuiGraphics g, float tickDelta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Font font = mc.font;

        /* ─── геометрия ─── */
        int width  = 180;
        int height = 102;
        int x = mc.getWindow().getGuiScaledWidth() - width - 10;  // 10 px от правого края
        int y = 10;

        /* ─── фон ─── */
        g.fill(x - 3, y - 3, x + width + 3, y + height + 3, 0xA0000000);  // прозрачная подложка

        /* ─── рамка (4 стороны) ─── */
        int col = 0xFFCC9900;  // золотой
        g.fill(x - 3, y - 3, x + width + 3, y - 2, col);           // верх
        g.fill(x - 3, y + height + 2, x + width + 3, y + height + 3, col); // низ
        g.fill(x - 3, y - 3, x - 2, y + height + 3, col);          // левый
        g.fill(x + width + 2, y - 3, x + width + 3, y + height + 3, col);  // правый

        /* ─── заголовок ─── */
        g.drawString(
                font,
                Component.literal(ChatFormatting.GOLD + "" + ChatFormatting.BOLD + "Dominion HUD"),
                x + 6, y + 6,
                0xFFFFFF,
                false
        );

        /* ─── контент ─── */
        int dy = y + 22;

        drawLine(g, font, "Территория:", HudClient.territory, x, dy);          dy += 14;
        drawLine(g, font, "Город:",       HudClient.town,      x, dy);          dy += 14;
        drawLine(g, font, "Мэр:",         HudClient.mayor,     x, dy);          dy += 14;
        drawLine(g, font, "Ваш ранг:",    HudClient.rank,      x, dy);          dy += 14;
        drawLine(g, font, "Клеймов:",     String.valueOf(HudClient.claims), x, dy); dy += 14;
        drawLine(
                g, font,
                "PvP чанка:",
                HudClient.chunkPvp ? ChatFormatting.GREEN + "ON" : ChatFormatting.RED + "OFF",
                x, dy
        );
    }

    private static void drawLine(GuiGraphics g, Font font, String key, String val, int x, int y) {
        g.drawString(font, Component.literal("§7" + key), x + 6, y, 0xFFFFFF, false);
        g.drawString(font, Component.literal("§f" + val), x + 88, y, 0xFFFFFF, false);
    }
}
