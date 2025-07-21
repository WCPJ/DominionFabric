package org.worldcraft.dominioncraft.nation;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import java.util.EnumMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Утилиты для работы с конституцией нации.
 *
 * <p>Парсит скобочные метки вида <code>(Key: true)</code>
 * и превращает их в {@code EnumMap<ConstitutionKey,Boolean>}.</p>
 */
public final class ConstitutionUtil {

    private ConstitutionUtil() {}   // static‑only

    /** <code>(Key: true)</code> или <code>(Key:false)</code>  — без пробелов важно. */
    private static final Pattern RULE_PATTERN =
            Pattern.compile("\\((\\w+)\\s*:\\s*(true|false)\\)",
                    Pattern.CASE_INSENSITIVE);

    /**
     * Вычисляет карту правил из текста конституции.
     *
     * @param text Markdown‑строка (может быть многострочной)
     * @return карта правил; ключи без указания в тексте не попадают в map
     */
    public static EnumMap<ConstitutionKey, Boolean> extractRules(String text) {
        EnumMap<ConstitutionKey, Boolean> map = new EnumMap<>(ConstitutionKey.class);
        Matcher m = RULE_PATTERN.matcher(text);
        while (m.find()) {
            String keyRaw = m.group(1).toUpperCase(Locale.ROOT);
            boolean value = Boolean.parseBoolean(m.group(2));
            try {
                ConstitutionKey key = ConstitutionKey.valueOf(keyRaw);
                map.put(key, value);
            } catch (IllegalArgumentException ignored) {
                // неизвестное ключевое слово — просто пропускаем
            }
        }
        return map;
    }

    /* ===== helper для сохранения карты в NBT (опционально) ===== */

    public static ListTag rulesToNbt(EnumMap<ConstitutionKey, Boolean> rules) {
        ListTag list = new ListTag();
        for (var e : rules.entrySet()) {
            list.add(StringTag.valueOf(e.getKey().name() + ":" + e.getValue()));
        }
        return list;
    }

    public static EnumMap<ConstitutionKey, Boolean> rulesFromNbt(ListTag list) {
        EnumMap<ConstitutionKey, Boolean> map = new EnumMap<>(ConstitutionKey.class);
        for (int i = 0; i < list.size(); i++) {
            String[] kv = list.getString(i).split(":");
            if (kv.length != 2) continue;
            try {
                ConstitutionKey k = ConstitutionKey.valueOf(kv[0]);
                map.put(k, Boolean.parseBoolean(kv[1]));
            } catch (IllegalArgumentException ignored) { }
        }
        return map;
    }
}
