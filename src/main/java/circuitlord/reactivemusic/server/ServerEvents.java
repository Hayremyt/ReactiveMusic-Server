package circuitlord.reactivemusic.server;

import circuitlord.reactivemusic.server.handshake.HandshakeManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

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

    public TargetTracker getTargetTracker() {
        return targetTracker;
    }

    public StructureScanner getStructureScanner() {
        return structureScanner;
    }

    public HandshakeManager getHandshakeManager() {
        return handshakeManager;
    }

    /**
     * Вызывается каждый серверный тик.
     * Обновляет цели мобов и, при необходимости, структуры.
     * Данные о целях и структурах отправляются только игрокам,
     * у которых есть клиентский мод (подтверждено через рукопожатие).
     */
    public void onServerTick(ServerWorld world) {
        if (targetTracker == null) return;

        targetTracker.update(world);
        ticksSinceUpdate++;

        // Структуры сканируем реже — раз в 100 тиков (5 секунд)
        if (ticksSinceUpdate >= 100) {
            for (var player : world.getPlayers()) {
                if (handshakeManager.hasClientMod(player.getUuid())) {
                    structureScanner.scan(world, player.getBlockPos(), 1);
                }
            }
            ticksSinceUpdate = 0;
        }
    }

    /**
     * Игрок зашёл на сервер — ждем HandshakeRequest от клиента.
     */
    public void onPlayerJoin(ServerPlayerEntity player) {
        // Рукопожатие инициируется клиентом. Сервер ждет HandshakeRequest
        // и в ответ шлет HandshakeResponse (в сетевом хендлере лоадера).
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
     * Игрок ушёл с сервера — очищаем его из списка рукопожатий.
     */
    public void onPlayerLeave(ServerPlayerEntity player) {
        handshakeManager.unregisterClient(player.getUuid());
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
        structureScanner.clear();
        handshakeManager.reset();
        ticksSinceUpdate = 0;
    }
}
