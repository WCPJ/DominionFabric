package org.worldcraft.dominioncraft.nation;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.worldcraft.dominioncraft.town.Town;
import org.worldcraft.dominioncraft.town.TownData;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class NationTimers {

    // Интервал напоминаний о референдуме (30 минут)
    private static final long REMINDER_EVERY_TICKS = 30 * 60 * 20L;
    // Длительность референдума (24 часа)
    private static final long REFERENDUM_DURATION_TICKS = 24 * 60 * 60 * 20L;

    private NationTimers() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            var level = server.overworld();
            long now = level.getGameTime();
            NationData nd = NationData.get(level);

            for (Nation n : nd.all()) {
                // 1. Тайм-аут заявок и сецессий
                removeExpiredApplications(server, n, now);
                removeExpiredSecessions(server, n, now);

                // 2. Выборы (республика)
                if (n.getGovernment() == GovernmentType.REPUBLIC) {
                    handleElection(server, n, now);
                }

                // 3. Референдумы (любая нация)
                handleReferendum(server, n, now);
            }

            nd.setDirty(); // сохраняем если что-то изменилось
        });
    }

    /* ============================= Election ============================= */

    private static void handleElection(net.minecraft.server.MinecraftServer server, Nation n, long now) {
        if (n.getElection() == null && now >= n.getNextElectionTick()) {
            n.setElection(new Election(now));
            broadcast(server, n, "§6В республике §e" + n.getName()
                    + " §6открылась регистрация кандидатов!");
        }

        Election e = n.getElection();
        if (e != null) {
            e.tick(now);

            if (e.getPhase() == Election.Phase.VOTING && now == e.getRegEndTick()) {
                broadcast(server, n, "§6Регистрация закрыта, начинается голосование!");
            }

            if (e.getPhase() == Election.Phase.FINISHED) {
                UUID win = e.getWinner();
                if (win != null) {
                    n.setLeader(win);
                    broadcast(server, n, "§e" + server.getProfileCache()
                            .get(win).map(p -> p.getName()).orElse("Новый лидер")
                            + " §6побеждает на выборах и становится президентом!");
                } else {
                    broadcast(server, n, "§6Выборы признаны несостоявшимися.");
                }
                n.setElection(null);
                n.setNextElectionTick(now + NationCommands.TICKS_14_DAYS);
            }
        }
    }

    /* ============================ Referendum ============================ */

    private static void handleReferendum(net.minecraft.server.MinecraftServer server, Nation n, long now) {
        Referendum ref = n.referendum;
        if (ref == null) return;

        if (now >= ref.endTick) {
            boolean passed = ref.isPassed();
            if (passed) {
                broadcast(server, n, "§6Референдум завершён: решение ПРИНЯТО.");
                if (ref.isDissolutionReferendum()) {
                    deleteNationAfterReferendum(server, n);
                }
            } else {
                broadcast(server, n, "§6Референдум завершён: решение ОТКЛОНЕНО.");
            }
            n.referendum = null;
            return;
        }


        // Напоминание раз в 30 минут
        if ((now - ref.startTick) % REMINDER_EVERY_TICKS == 0) {
            broadcast(server, n, "§6Напоминаем: в нации «" + n.getName()
                    + "» идёт референдум!\n§7Голосуйте командой: /nation referendum vote <да/нет>");
        }
    }

    // Примерная логика удаления нации после референдума
    private static void deleteNationAfterReferendum(net.minecraft.server.MinecraftServer server, Nation n) {
        var level = server.overworld();
        TownData td = TownData.get(level);

        // Убираем ссылку на нацию из городов
        for (UUID townId : n.getTowns()) {
            Town t = td.getTown(townId);
            if (t != null) t.setNation(null);
        }

        NationData.get(level).deleteNation(n);
        broadcastGlobal(server, "§6[Мировые новости] Нация «" + n.getName() + "» распущена по итогам референдума!");
    }

    /* ========================= Timeouts/Applications ========================= */

    private static void removeExpiredApplications(net.minecraft.server.MinecraftServer srv,
                                                  Nation n, long now) {
        Iterator<Map.Entry<UUID, Application>> it = n.applications.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Application> en = it.next();
            Application a = en.getValue();
            if (a.millisLeft() > 0) continue;
            it.remove();
            Town t = TownData.get(srv.overworld()).getTown(a.townId);
            String townName = t != null ? t.getName() : "Город";
            broadcast(srv, n, "§eЗаявка города §b" + townName + " §eистекла и отклонена.");
        }
    }

    private static void removeExpiredSecessions(net.minecraft.server.MinecraftServer srv,
                                                Nation n, long now) {
        Iterator<Map.Entry<UUID, SecessionRequest>> it = n.secessions.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, SecessionRequest> en = it.next();
            SecessionRequest s = en.getValue();
            if (now - (s.created / 50) < NationCommands.TICKS_24_HOURS * 3) continue;
            it.remove();
            Town t = TownData.get(srv.overworld()).getTown(s.townId);
            String townName = t != null ? t.getName() : "Город";
            broadcast(srv, n, "§eЗапрос на выход города §b" + townName + " §eистёк и отклонён.");
        }
    }

    /* ============================ Broadcast ============================ */

    /** Всем членам нации */
    private static void broadcast(net.minecraft.server.MinecraftServer srv,
                                  Nation n, String msg) {
        var level = srv.overworld();
        TownData td = TownData.get(level);
        for (UUID townId : n.getTowns()) {
            Town t = td.getTown(townId);
            if (t != null) {
                t.getMembers().forEach(uuid -> {
                    ServerPlayer p = srv.getPlayerList().getPlayer(uuid);
                    if (p != null) p.sendSystemMessage(Component.literal(msg));
                });
            }
        }
    }

    /** Глобально всем на сервере */
    private static void broadcastGlobal(net.minecraft.server.MinecraftServer srv, String msg) {
        for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
            p.sendSystemMessage(Component.literal(msg));
        }
    }
}
