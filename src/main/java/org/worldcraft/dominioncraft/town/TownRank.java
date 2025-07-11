package org.worldcraft.dominioncraft.town;

/**
 * Базовые ранги внутри города.
 *
 * <p>Значение по возрастанию прав (справа налево):
 * <ul>
 *   <li>{@code RECRUIT}  – новобранец, минимальный набор прав
 *   <li>{@code MEMBER}   – полноценный житель
 *   <li>{@code ASSISTANT}– заместитель мэра
 *   <li>{@code MAYOR}    – глава города
 * </ul>
 *
 * <p>NB: {@link org.worldcraft.dominioncraft.town.Town} задаёт,
 * какие {@link TownPermission} привязаны к каждому рангу.
 */
public enum TownRank {
    MAYOR,
    ASSISTANT,
    MEMBER,
    RECRUIT
}
