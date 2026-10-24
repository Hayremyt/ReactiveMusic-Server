package circuitlord.reactivemusic.server;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;

/**
 * Отслеживает точные цели мобов на сервере.
 * <p>
 * Сервер знает реальный target каждого моба (MobEntity#getTarget, brain memory ATTACK_TARGET).
 * Эти данные никогда не присылаются клиенту ванильно, поэтому серверный мод хранит их здесь
 * и рассылает клиентам, которые просили target=me/other.
 */
public class TargetTracker {

    /** UUID моба -> UUID цели (или null, если нет цели). */
    private final Map<UUID, UUID> mobTargets = new HashMap<>();

    /** Последний тик обновления для каждого моба, чтобы清理ть мёртвые записи. */
    private final Map<UUID, Long> lastSeen = new HashMap<>();

    /**
     * Вызывается каждый тик для обновления целей мобов в мире.
     * Можно оптимизировать, сканируя только мобы в радиусе около игроков.
     */
    public void update(ServerWorld world) {
        // Сначала собираем живых мобов и их цели
        Map<UUID, UUID> current = new HashMap<>();
        long now = world.getTime();

        for (var entity : world.getEntities()) {
            if (entity instanceof MobEntity mob) {
                var target = mob.getTarget();
                current.put(mob.getUuid(), target != null ? target.getUuid() : null);
                lastSeen.put(mob.getUuid(), now);
            }
        }

        // Заменяем карту целей
        mobTargets.clear();
        mobTargets.putAll(current);

        // Удаляем старые записи (мобы, которых больше нет)
        lastSeen.entrySet().removeIf(entry -> now - entry.getValue() > 400L); // 20 секунд
    }

    /**
     * Возвращает UUID цели указанного моба, или null если цели нет / моба не существует.
     */
    public UUID getTarget(UUID mobUuid) {
        return mobTargets.get(mobUuid);
    }

    /** Очистка при выгрузке мира. */
    public void clear() {
        mobTargets.clear();
        lastSeen.clear();
    }
}
