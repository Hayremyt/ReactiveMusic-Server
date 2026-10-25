package circuitlord.reactivemusic.server;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

/**
 * Отслеживает точные цели мобов на сервере.
 * <p>
 * Сервер знает реальный target каждого моба (MobEntity#getTarget, brain memory ATTACK_TARGET).
 * Эти данные никогда не присылаются клиенту ванильно, поэтому серверный мод хранит их здесь
 * и рассылает клиентам, которые просили target=me/other.
 * <p>
 * Помимо целей хранятся последние известные позиции мобов, чтобы сервер мог отправить пакет
 * о цели только тем игрокам, которые находятся рядом с мобом (в радиусе скана клиента),
 * и не утекал инфой игрокам за сотни блоков.
 */
public class TargetTracker {

    /** UUID моба -> UUID цели (или null, если нет цели). */
    private final Map<UUID, UUID> mobTargets = new HashMap<>();

    /** UUID моба -> последняя известная позиция моба. */
    private final Map<UUID, BlockPos> mobPositions = new HashMap<>();

    /** Последний тик обновления для каждого моба, чтобы чистить мёртвые записи. */
    private final Map<UUID, Long> lastSeen = new HashMap<>();

    /**
     * Вызывается каждый тик для обновления целей мобов в мире.
     * Возвращает только те мобы, у которых цель изменилась по сравнению с предыдущим тиком
     * (включая мобов, которые исчезли — для них значение null).
     */
    public Map<UUID, UUID> update(ServerWorld world) {
        Map<UUID, UUID> previous = new HashMap<>(mobTargets);
        Map<UUID, UUID> current = new HashMap<>();
        long now = world.getTime();

        for (var entity : world.getEntities()) {
            if (entity instanceof MobEntity mob) {
                var target = mob.getTarget();
                current.put(mob.getUuid(), target != null ? target.getUuid() : null);
                mobPositions.put(mob.getUuid(), mob.getBlockPos());
                lastSeen.put(mob.getUuid(), now);
            }
        }

        // Заменяем карту целей
        mobTargets.clear();
        mobTargets.putAll(current);

        // Удаляем старые записи (мобы, которых больше нет)
        lastSeen.entrySet().removeIf(entry -> now - entry.getValue() > 400L); // 20 секунд
        mobPositions.entrySet().removeIf(entry -> !lastSeen.containsKey(entry.getKey()));

        // Считаем изменения
        Map<UUID, UUID> changes = new HashMap<>();
        for (var entry : current.entrySet()) {
            UUID mob = entry.getKey();
            UUID newTarget = entry.getValue();
            UUID oldTarget = previous.get(mob);
            if (!Objects.equals(oldTarget, newTarget)) {
                changes.put(mob, newTarget);
            }
        }
        // Мобы, которые были ранее, но исчезли
        for (var mob : previous.keySet()) {
            if (!current.containsKey(mob)) {
                changes.put(mob, null);
            }
        }

        return changes;
    }

    /**
     * Возвращает UUID цели указанного моба, или null если цели нет / моба не существует.
     */
    public UUID getTarget(UUID mobUuid) {
        return mobTargets.get(mobUuid);
    }

    /** Возвращает последнюю известную позицию моба, или null если моба нет в отслеживании. */
    public BlockPos getMobPosition(UUID mobUuid) {
        return mobPositions.get(mobUuid);
    }

    /** Очистка при выгрузке мира. */
    public void clear() {
        mobTargets.clear();
        mobPositions.clear();
        lastSeen.clear();
    }
}
