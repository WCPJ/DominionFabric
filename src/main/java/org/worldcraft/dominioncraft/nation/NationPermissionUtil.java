package org.worldcraft.dominioncraft.nation;

import java.util.UUID;

/**
 * Вспомогательные проверки прав внутри нации.
 */
public final class NationPermissionUtil {

    private NationPermissionUtil() {}

    /**
     * Проверить, есть ли у игрока конкретное право в нации.
     *
     * @param n    нация
     * @param uuid игрок
     * @param perm требуемое право
     * @return true, если разрешено
     */
    public static boolean has(Nation n, UUID uuid, NationPermission perm) {
        return n != null && n.hasPermission(uuid, perm);
    }

    /** Лидер ли игрок. */
    public static boolean isLeader(Nation n, UUID uuid) {
        return n != null && uuid.equals(n.getLeader());
    }
}
