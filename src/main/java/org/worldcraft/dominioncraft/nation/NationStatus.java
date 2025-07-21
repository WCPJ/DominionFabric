package org.worldcraft.dominioncraft.nation;

/**
 * Статус приёма новых городов.
 *
 * <p>Используется полем {@code Nation.status}. Значение проверяется
 * командой <code>/nation apply</code>:
 * <ul>
 *   <li>{@link #OPEN} — город вступает сразу, без голосования;</li>
 *   <li>{@link #CLOSED} — создаётся заявка, которую должен
 *       одобрить совет (ранги {@code LEADER}/{@code MINISTER}).</li>
 * </ul>
 *
 * <p>Переключается командами <code>/nation open</code> и
 * <code>/nation close</code> при наличии {@code MANAGE_OPEN_STATUS}
 * в правах лидера.</p>
 */
public enum NationStatus {

    /** Нация открыта: <code>/nation apply</code> добавляет город
     *  без дополнительного утверждения. */
    OPEN,

    /** Нация закрыта: каждая заявка города должна быть рассмотрена
     *  правителями онлайн. */
    CLOSED
}
