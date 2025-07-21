package org.worldcraft.dominioncraft.bluemap;

import de.bluecolored.bluemap.api.BlueMapAPI;
import de.bluecolored.bluemap.api.markers.MarkerSet;

import de.bluecolored.bluemap.api.markers.ShapeMarker;
import de.bluecolored.bluemap.api.math.Color;
import com.flowpowered.math.vector.Vector2d;
import com.flowpowered.math.vector.Vector3d;
import de.bluecolored.bluemap.api.math.Shape;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import org.worldcraft.dominioncraft.nation.*;
import org.worldcraft.dominioncraft.town.*;

import java.util.*;

/* =================================================================== */
/*   BlueMap: города DominionCraft  —  внешний контур + прозрачная     */
/* =================================================================== */
public final class BlueMapTownMarkers {

    /* -------- маленькая record для рёбер (x1,z1)→(x2,z2) ---------- */
    private record Edge(int x1, int z1, int x2, int z2) {}

    private static final String SET_ID = "dominioncraft-towns";
    private static final int[]  NATION_COL = {
            0x2E86AB, 0xFF5733, 0x27AE60,
            0xAF7AC5, 0xF1C40F, 0xE67E22
    };

    /* =============== safe‑public =============== */
    public static void updateIfPresent(MinecraftServer srv) {
        if (!BlueMapCompat.isBlueMapPresent()) return;
        try { update(srv); } catch (Throwable t) { t.printStackTrace(); }
    }

    /* ================= core ==================== */
    private static void update(MinecraftServer srv) {
        BlueMapAPI.onEnable(api -> {

            for (ServerLevel lvl : srv.getAllLevels()) {

                String worldId = lvl.dimension().location().toString();
                api.getWorld(worldId).ifPresent(bmWorld -> bmWorld.getMaps().forEach(map -> {

                    MarkerSet set = map.getMarkerSets()
                            .computeIfAbsent(SET_ID, MarkerSet::new);
                    set.getMarkers().clear();

                    TownData   td = TownData.get(lvl);
                    NationData nd = NationData.get(lvl);
                    Map<UUID, Nation> nations = new HashMap<>();
                    nd.all().forEach(n -> nations.put(n.getId(), n));

                    /* --------- по всем городам --------- */
                    td.getTownMap().values().forEach(town -> {

                        /* цвет по нации / default */
                        int base = 0x00C9FF;
                        if (town.getNation() != null && nations.containsKey(town.getNation())) {
                            base = NATION_COL[Math.abs(town.getNation().hashCode()) % NATION_COL.length];
                        }
                        Color lineC = argb(base, 255);
                        Color fillC = argb(base, 60);          // ~24 % непрозрачность

                        /* внешний контур */
                        List<Vector2d> border = buildOutline(town);
                        if (border.isEmpty()) return;

                        String id = "town-" + town.getId();
                        Vector2d start = border.get(0);
                        Vector3d origin = new Vector3d(start.getX(), 320, start.getY());

                        ShapeMarker m = new ShapeMarker(id, origin, new Shape(border), 0);
                        m.setLineColor(lineC);
                        m.setFillColor(fillC);
                        m.setDepthTestEnabled(false);
                        m.setLabel(town.getName());

                        /* ---------- Формируем подробное описание ---------- */
                        StringBuilder info = new StringBuilder();
                        info.append("<b>").append(town.getName()).append("</b><br>");

                        /* мэр */
                        info.append("Мэр: ").append(town.getMayorName(srv)).append("<br>");

                        /* нация (если есть) */
                        if (town.getNation() != null && nations.containsKey(town.getNation())) {
                            info.append("Нация: ").append(nations.get(town.getNation()).getName()).append("<br>");
                        }

                        /* жители — перевод UUID → ник + сортировка */
                        List<String> residents = town.getMembers().stream()
                                .map(u -> srv.getProfileCache().get(u)
                                        .map(gp -> gp.getName()).orElse(u.toString()))
                                .sorted(String.CASE_INSENSITIVE_ORDER)
                                .toList();

                        info.append("Жителей (").append(residents.size()).append("):<br>");
                        info.append(String.join(", ", residents)).append("<br>");

                        info.append("Чанков: ").append(town.getClaimCount());

                        m.setDetail(info.toString());

                        set.getMarkers().put(id, m);
                    });

                }));
            }
        });
    }

    /* ------------------------------------------------------------------ */
    /*   buildOutline ‑ версия marching squares                           */
    /* ------------------------------------------------------------------ */
    private static List<Vector2d> buildOutline(Town town) {

        /* 1) собираем координаты чанков и bbox */
        Set<Long> occ = new HashSet<>();
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (TownChunk tc : town.allChunks()) {
            int cx = tc.getPos().x, cz = tc.getPos().z;
            occ.add(ChunkPos.asLong(cx, cz));
            if (cx < minX) minX = cx;
            if (cx > maxX) maxX = cx;
            if (cz < minZ) minZ = cz;
            if (cz > maxZ) maxZ = cz;
        }
        if (occ.isEmpty()) return List.of();

        /* 2) marching‑squares: бежим по bbox + 1, ищем переходы 0→1 */
        List<Vector2d> points = new ArrayList<>();
        for (int z = minZ - 1; z <= maxZ; z++) {
            for (int x = minX - 1; x <= maxX; x++) {

                boolean a = occ.contains(ChunkPos.asLong(x,     z));
                boolean b = occ.contains(ChunkPos.asLong(x + 1, z));
                boolean c = occ.contains(ChunkPos.asLong(x,     z + 1));
                boolean d = occ.contains(ChunkPos.asLong(x + 1, z + 1));

                int code = (a ? 1 : 0) | (b ? 2 : 0) | (c ? 4 : 0) | (d ? 8 : 0);
                switch (code) {
                    case 1, 5, 13 -> addEdge(points, x, z, x, z + 1);
                    case 8, 10, 11 -> addEdge(points, x + 1, z + 1, x + 1, z);
                    case 4, 12, 14 -> addEdge(points, x + 1, z, x, z);
                    case 2, 3, 7  -> addEdge(points, x, z + 1, x + 1, z + 1);
                    case 6, 9    -> {                          // ambiguous → split
                        addEdge(points, x, z + 1, x,     z);
                        addEdge(points, x + 1, z, x + 1, z + 1);
                    }
                }
            }
        }

    /* 3) points сейчас содержит ломаную «рисуют‑стирают». Сортируем
          в правильный порядок «следуй по ребру» */
        return orderEdges(points);
    }

    /* добавляем ребро; дубликат‑реверс удаляем (убираем внутренние) */
    private static void addEdge(List<Vector2d> list, int x1, int z1, int x2, int z2) {
        Vector2d a = new Vector2d(x1 * 16, z1 * 16);
        Vector2d b = new Vector2d(x2 * 16, z2 * 16);
        int idx = list.indexOf(b);
        if (idx != -1 && list.get((idx + 1) % list.size()).equals(a)) {
            list.remove(idx);                           // удаляем обратное
        } else {
            list.add(a); list.add(b);
        }
    }

    /* упорядочиваем вершины: граф → замкнутый полигон */
    private static List<Vector2d> orderEdges(List<Vector2d> raw) {
        if (raw.isEmpty()) return List.of();
        Map<Vector2d, Vector2d> next = new HashMap<>();
        for (int i = 0; i < raw.size(); i += 2) next.put(raw.get(i), raw.get(i + 1));

        List<Vector2d> ordered = new ArrayList<>();
        Vector2d start = raw.get(0), curr = start;
        do {
            ordered.add(curr);
            curr = next.get(curr);
        } while (curr != null && !curr.equals(start) && ordered.size() < next.size()+1);

        return ordered;
    }


    private static void addOrToggle(Set<Edge> set, Edge e) {
        Edge rev = new Edge(e.x2, e.z2, e.x1, e.z1);
        if (!set.remove(rev)) set.add(e);
    }

    /* ---------- util ---------- */
    private static Color argb(int rgb, int a) {
        int r = (rgb >> 16) & 0xFF,
                g = (rgb >>  8) & 0xFF,
                b =  rgb        & 0xFF;
        return new Color(r, g, b, a);
    }
}
