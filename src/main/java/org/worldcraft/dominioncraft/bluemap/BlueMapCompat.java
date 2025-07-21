package org.worldcraft.dominioncraft.bluemap;

import net.fabricmc.loader.api.FabricLoader;

public class BlueMapCompat {
    public static boolean isBlueMapPresent() {
        return FabricLoader.getInstance().isModLoaded("bluemap");
    }
}
