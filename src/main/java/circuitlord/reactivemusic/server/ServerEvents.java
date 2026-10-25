package circuitlord.reactivemusic.server;

import circuitlord.reactivemusic.server.handshake.HandshakeManager;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Серверные события: тик, вход/выход игрока, обновление целей и структур.
 * <p>
 * Регистрируется в каждом лоадере (Fabric/Forge/NeoForge) отдельно,
 * так как API событий отличается.
 * <p>
 * Данные считаются и хранятся <b>для каждого игрока отдельно</b>:
 * <ul>
 *   <li>{@code playerTargetViews} — что каждый игрок уже знает (моб -> цель);</li>
 *   <li>{@code pendingTargetUpdates} — дельта, которую нужно отправить конкретному игроку;</li>
 *   <li>{@code lastPlayerStructures} — структура (набор id), в которой находится каждый игрок;</li>
 *   <li>{@code pendingStructureChanges} — структура, которую нужно отправить конкретному игроку.</li>
 * </ul>
 * Моб виден игроку только если он находится в ТОМ ЖЕ измерении и в радиусе симуляции сервера,
 * поэтому утечки между игроками/мирами нет.
 */
public class ServerEvents {

    private final StructureScanner structureScanner = new StructureScanner();
    private final HandshakeManager handshakeManager = new HandshakeManager();
    private int ticksSinceUpdate = 0;

    /** Для каждого игрока: текущий вид целей мобов (UUID моба -> UUID цели). */
    private final Map<UUID, Map<UUID, UUID>> playerTargetViews = new HashMap<>();

    /** Для каждого игрока: дельта целей, ожидающая отправки (target == null => «забудь моба»). */
    private final Map<UUID, List<TargetUpdate>> pendingTargetUpdates = new HashMap<>();

    /** Для каждого игрока: последняя известная структура (набор id). */
    private final Map<UUID, Set<String>> lastPlayerStructures = new HashMap<>();

    /** Для каждого игрока: структура, ожидающая отправки. */
    private final Map<UUID, Set<String>> pendingStructureChanges = new HashMap<>();

    /** Одно обновление цели моба для конкретного игрока. targetUuid == null => забыть моба. */
    public static final class TargetUpdate {
        public final UUID mobUuid;
        public final UUID targetUuid;

        public TargetUpdate(UUID mobUuid, UUID targetUuid) {
            this.mobUuid = mobUuid;
            this.targetUuid = targetUuid;
        }
    }

    public HandshakeManager getHandshakeManager() {
        return handshakeManager;
    }

    /** Дельта целей для игрока, ожидающая отправки (или null, если нечего слать). */
    public List<TargetUpdate> drainTargetUpdates(UUID playerUuid) {
        return pendingTargetUpdates.remove(playerUuid);
    }

    /** Структура для игрока, ожидающая отправки (или null, если нечего слать). */
    public Set<String> drainStructureUpdate(UUID playerUuid) {
        return pendingStructureChanges.remove(playerUuid);
    }

    /**
     * Вызывается каждый серверный тик.
     * Пересчитывает per-player состояние целей и (реже) структур.
     */
    public void onServerTick(MinecraftServer server) {
        reconcileTargets(server);

        ticksSinceUpdate++;
        if (ticksSinceUpdate >= 100) { // структуры — раз в 5 секунд
            for (ServerWorld world : server.getWorlds()) {
                for (var player : world.getPlayers()) {
                    reconcileStructure(player, world);
                }
            }
            ticksSinceUpdate = 0;
        }
    }

    /**
     * Для каждого игрока с модом строит актуальный вид целей мобов рядом (в его мире, в радиусе
     * симуляции) и вычисляет дельту относительно прошлого вида этого игрока.
     */
    private void reconcileTargets(MinecraftServer server) {
        double radius = server.getPlayerManager().getViewDistance() * 16.0; // view distance в чанках
        double r2 = radius * radius;

        for (ServerWorld world : server.getWorlds()) {
            for (var player : world.getPlayers()) {
                UUID playerUuid = player.getUuid();
                if (!handshakeManager.hasClientMod(playerUuid)) continue;

                double px = player.getX();
                double py = player.getY();
                double pz = player.getZ();
                Box box = new Box(px - radius, py - radius, pz - radius,
                                  px + radius, py + radius, pz + radius);

                Map<UUID, UUID> newView = new HashMap<>();
                for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, box, e -> e.isAlive())) {
                    var target = mob.getTarget();
                    if (target == null) continue; // моб ни на кого не нацелен — не храним
                    double dx = mob.getX() - px;
                    double dy = mob.getY() - py;
                    double dz = mob.getZ() - pz;
                    if (dx * dx + dy * dy + dz * dz > r2) continue;
                    newView.put(mob.getUuid(), target.getUuid());
                }

                Map<UUID, UUID> oldView = playerTargetViews.getOrDefault(playerUuid, Map.of());
                List<TargetUpdate> updates = new ArrayList<>();

                // Новые цели и изменения
                for (var entry : newView.entrySet()) {
                    UUID mob = entry.getKey();
                    UUID newTarget = entry.getValue();
                    if (!Objects.equals(oldView.get(mob), newTarget)) {
                        updates.add(new TargetUpdate(mob, newTarget));
                    }
                }
                // Мобы, которые выпали из вида (ушли/умерли/перестали целиться) — «забудь»
                for (var mob : oldView.keySet()) {
                    if (!newView.containsKey(mob)) {
                        updates.add(new TargetUpdate(mob, null));
                    }
                }

                if (!updates.isEmpty()) {
                    pendingTargetUpdates.computeIfAbsent(playerUuid, k -> new ArrayList<>()).addAll(updates);
                }
                playerTargetViews.put(playerUuid, newView);
            }
        }
    }

    /** Пересчитывает структуру для одного игрока и ставит в очередь, если изменилась. */
    private void reconcileStructure(ServerPlayerEntity player, ServerWorld world) {
        UUID playerUuid = player.getUuid();
        if (!handshakeManager.hasClientMod(playerUuid)) return;

        var current = structureScanner.scan(world, player.getBlockPos(), 1);
        var previous = lastPlayerStructures.get(playerUuid);
        if (!Objects.equals(current, previous)) {
            pendingStructureChanges.put(playerUuid, current);
        }
        lastPlayerStructures.put(playerUuid, current);
    }

    /** Игрок зашёл на сервер — ждём HandshakeRequest. Известных клиентов регистрируем заранее. */
    public void onPlayerJoin(ServerPlayerEntity player) {
        handshakeManager.markKnownOnJoin(player.getUuid());
    }

    /**
     * Клиент прислал HandshakeRequest — регистрируем и сразу синхронизируем структуру
     * (цели мобов придут в ближайшем тике через reconcileTargets — прошлый вид пуст).
     */
    public void onHandshakeRequest(ServerPlayerEntity player) {
        handshakeManager.registerClient(player.getUuid());
        // Отправка HandshakeResponse — в ветке лоадера.

        // Немедленная синхронизация структуры после подтверждения мода.
        //? if >=1.21.9 {
        /*ServerWorld world = (ServerWorld) player.getEntityWorld();
        *///?} else {
        ServerWorld world = (ServerWorld) player.getWorld();
        //?}
        if (world != null) {
            var current = structureScanner.scan(world, player.getBlockPos(), 1);
            lastPlayerStructures.put(player.getUuid(), current);
            pendingStructureChanges.put(player.getUuid(), current);
        }
    }

    /**
     * Игрок сменил измерение — принудительно синхронизируем структуру нового мира.
     * Цели мобов пересчитаются в ближайшем тике (вид строится по новому миру).
     */
    public void onPlayerChangedDimension(ServerPlayerEntity player) {
        //? if >=1.21.9 {
        /*ServerWorld world = (ServerWorld) player.getEntityWorld();
        *///?} else {
        ServerWorld world = (ServerWorld) player.getWorld();
        //?}
        if (world == null) return;
        var current = structureScanner.scan(world, player.getBlockPos(), 1);
        lastPlayerStructures.put(player.getUuid(), current);
        pendingStructureChanges.put(player.getUuid(), current);
        // Сбросить вид целей, чтобы на новом мире ушли все актуальные цели (старые «забудутся»)
        // — это произойдёт автоматически в reconcileTargets (старый вид не совпадёт с новым).
    }

    /** Игрок ушёл — чистим всё, что про него хранили. */
    public void onPlayerLeave(ServerPlayerEntity player) {
        UUID uuid = player.getUuid();
        handshakeManager.unregisterClient(uuid);
        playerTargetViews.remove(uuid);
        pendingTargetUpdates.remove(uuid);
        lastPlayerStructures.remove(uuid);
        pendingStructureChanges.remove(uuid);
    }

    /** Полное очищение при выгрузке мира. Состояние рукопожатий НЕ трогаем. */
    public void onWorldUnload(ServerWorld world) {
        playerTargetViews.clear();
        pendingTargetUpdates.clear();
        lastPlayerStructures.clear();
        pendingStructureChanges.clear();
        ticksSinceUpdate = 0;
    }
}
