package circuitlord.reactivemusic.server;

import circuitlord.reactivemusic.server.handshake.HandshakeManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Серверные события: тик, вход/выход игрока, обновление целей и структур.
 * <p>
 * Регистрируется в каждом лоадере (Fabric/Forge/NeoForge) отдельно,
 * так как API событий unterscheidetтся.
 */
public class ServerEvents {

    private final TargetTracker targetTracker = new TargetTracker();
    private final StructureScanner structureScanner = new StructureScanner();
    private final HandshakeManager handshakeManager = new HandshakeManager();
    private int ticksSinceUpdate = 0;

    /** Изменения целей мобов из последнего тика (моб -> новая цель, null если цели нет). */
    private Map<UUID, UUID> lastTargetChanges = Map.of();

    /** Последние известные структуры для каждого игрока (чтобы слать пакет только при смене). */
    private final Map<UUID, Set<String>> lastPlayerStructures = new HashMap<>();

    /** Структуры, которые нужно отправить конкретному игроку (ключ -> новое множество). */
    private final Map<UUID, Set<String>> pendingStructureChanges = new HashMap<>();

    /** Игроки, которым нужнослать структуру принудительно (вход, смена измерения). */
    private final Set<UUID> playersNeedingStructureUpdate = new HashSet<>();

    public TargetTracker getTargetTracker() {
        return targetTracker;
    }

    public HandshakeManager getHandshakeManager() {
        return handshakeManager;
    }

    /** Изменения целей мобов из последнего тика. */
    public Map<UUID, UUID> getLastTargetChanges() {
        return lastTargetChanges;
    }

    /** Позиция моба из последнего отслеживания (для отправки пакета цели только ближайшим игрокам). */
    public net.minecraft.util.math.BlockPos getMobPosition(UUID mobUuid) {
        return targetTracker.getMobPosition(mobUuid);
    }

    /**
     * Запросить принудительную отправку структуры для игрока (например, при входе или смене измерения).
     */
    public void requestStructureUpdate(UUID playerUuid) {
        playersNeedingStructureUpdate.add(playerUuid);
    }

    /**
     * Возвращает и удаляет новые структуры для игрока, если они изменились с последнего сканирования
     * (или если запрошено принудительное обновление).
     * {@code null} если структура не менялась и не запрашивалось принудительное обновление.
     */
    public Set<String> takeStructureChanges(UUID playerUuid) {
        return pendingStructureChanges.remove(playerUuid);
    }

    /**
     * Вызывается каждый серверный тик.
     * Обновляет цели мобов и, при необходимости, структуры.
     * Данные о целях и структурах отправляются только игрокам,
     * у которых есть клиентский мод (подтверждено через рукопожатие).
     */
    public void onServerTick(ServerWorld world) {
        if (targetTracker == null) return;

        lastTargetChanges = targetTracker.update(world);
        ticksSinceUpdate++;

        // Структуры сканируем реже — раз в 100 тиков (5 секунд)
        if (ticksSinceUpdate >= 100) {
            for (var player : world.getPlayers()) {
                if (!handshakeManager.hasClientMod(player.getUuid())) continue;

                boolean forced = playersNeedingStructureUpdate.remove(player.getUuid());
                var current = structureScanner.scan(world, player.getBlockPos(), 1);
                var previous = lastPlayerStructures.get(player.getUuid());

                if (forced || !Objects.equals(current, previous)) {
                    pendingStructureChanges.put(player.getUuid(), current);
                }

                lastPlayerStructures.put(player.getUuid(), current);
            }
            ticksSinceUpdate = 0;
        }
    }

    /**
     * Игрок зашёл на сервер — ждем HandshakeRequest от клиента.
     * Также запрашиваем принудительную отправку текущей структуры.
     */
    public void onPlayerJoin(ServerPlayerEntity player) {
        // Рукопожатие инициируется клиентом. Сервер ждет HandshakeRequest
        // и в ответ шлет HandshakeResponse (в сетевом хендлере лоадера).
        requestStructureUpdate(player.getUuid());
    }

    /**
     * Сервер получил HandshakeRequest от клиента — регистрируем игрока
     * и отправляем HandshakeResponse. Вызывается из сетевого хендлера лоадера.
     */
    public void onHandshakeRequest(ServerPlayerEntity player) {
        handshakeManager.registerClient(player.getUuid());
        // Отправка HandshakeResponse реализуется в ветке лоадера
    }

    /**
     * Игрок ушёл с сервера — очищаем его из списка рукопожатий и структур.
     */
    public void onPlayerLeave(ServerPlayerEntity player) {
        handshakeManager.unregisterClient(player.getUuid());
        lastPlayerStructures.remove(player.getUuid());
        pendingStructureChanges.remove(player.getUuid());
        playersNeedingStructureUpdate.remove(player.getUuid());
    }

    /** Можно ли отправлять данные о целях мобов данному игроку. */
    public boolean canSendMobTargets(ServerPlayerEntity player) {
        return handshakeManager.hasClientMod(player.getUuid());
    }

    /** Можно ли отправлять данные о структурах данному игроку. */
    public boolean canSendStructureData(ServerPlayerEntity player) {
        return handshakeManager.hasClientMod(player.getUuid());
    }

    /** Полное очищение при выгрузке мира. */
    public void onWorldUnload(ServerWorld world) {
        targetTracker.clear();
        handshakeManager.reset();
        lastTargetChanges = Map.of();
        lastPlayerStructures.clear();
        pendingStructureChanges.clear();
        playersNeedingStructureUpdate.clear();
        ticksSinceUpdate = 0;
    }
}
