package org.worldcraft.dominioncraft.town;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;

import java.util.LinkedHashMap;
import java.util.Map;

public class TownRequirements {

    // Что требуется для создания города (можешь изменить под себя)
    public static final Map<Item, Integer> REQUIREMENTS = new LinkedHashMap<>() {{
        put(Items.COAL, 16);
        put(Items.IRON_INGOT, 8);
        put(Items.OAK_LOG, 64);
        put(Items.SPRUCE_LOG, 64);
    }};

    /**
     * Проверяет, хватает ли у игрока всех нужных предметов.
     */
    public static boolean hasAll(ServerPlayer player) {
        var inv = player.getInventory();
        for (var entry : REQUIREMENTS.entrySet()) {
            int found = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.getItem() == entry.getKey()) {
                    found += stack.getCount();
                }
            }
            if (found < entry.getValue()) return false;
        }
        return true;
    }

    /**
     * Удаляет из инвентаря игрока нужные ресурсы (только если их хватает!).
     */
    public static void removeRequiredItems(ServerPlayer player) {
        var inv = player.getInventory();
        for (var entry : REQUIREMENTS.entrySet()) {
            int needed = entry.getValue();
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.getItem() == entry.getKey()) {
                    int take = Math.min(stack.getCount(), needed);
                    stack.shrink(take);
                    needed -= take;
                    if (needed <= 0) break;
                }
            }
        }
    }

    /**
     * Возвращает Map с недостающими ресурсами (Item, количество).
     */
    public static Map<Item, Integer> getMissingItems(ServerPlayer player) {
        var inv = player.getInventory();
        Map<Item, Integer> missing = new LinkedHashMap<>();
        for (var entry : REQUIREMENTS.entrySet()) {
            int found = 0;
            for (int i = 0; i < inv.getContainerSize(); i++) {
                ItemStack stack = inv.getItem(i);
                if (stack.getItem() == entry.getKey()) found += stack.getCount();
            }
            int need = entry.getValue() - found;
            if (need > 0) missing.put(entry.getKey(), need);
        }
        return missing;
    }

    /**
     * Красиво выводит недостающие предметы для сообщения игроку.
     */
    public static String missingText(Map<Item, Integer> missing) {
        if (missing.isEmpty()) return "§aВсе необходимые ресурсы есть!";
        StringBuilder sb = new StringBuilder("§cНе хватает: ");
        for (var entry : missing.entrySet()) {
            sb.append(entry.getValue()).append(" ").append(getItemName(entry.getKey())).append(", ");
        }
        if (sb.length() > 2) sb.setLength(sb.length() - 2); // Убираем запятую
        return sb.toString();
    }

    /**
     * Получить локализованное название предмета (по умолчанию англ. если без русификатора)
     */
    public static String getItemName(Item item) {
        return item.getDescription().getString();
    }
}
