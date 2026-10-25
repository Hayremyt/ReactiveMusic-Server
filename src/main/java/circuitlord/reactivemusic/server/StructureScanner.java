package circuitlord.reactivemusic.server;

import java.util.HashSet;
import java.util.Set;
import java.util.Locale;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

/**
 * Сканирует структуры вокруг позиции игрока на сервере.
 * <p>
 * Клиент не получает данные о структурах от ваниллы, поэтому сервер сканирует
 * чанки вокруг игрока и возвращает список структур, в bounding box которых
 * находится сам игрок.
 */
public class StructureScanner {

    /**
     * Сканирует чанки вокруг позиции игрока на наличие структур.
     * Возвращает множество ID структур, в bounding box которых находится игрок.
     * Вызывается редко (раз в несколько секунд), так как структура игрока
     * меняется только при перемещении между структурами.
     */
    public Set<String> scan(ServerWorld world, BlockPos playerPos, int radiusChunks) {
        Set<String> found = new HashSet<>();

        var structureRegistry = world.getRegistryManager().get(Registry.STRUCTURE_KEY);

        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                var chunkPos = new BlockPos(
                    playerPos.getX() + dx * 16,
                    playerPos.getY(),
                    playerPos.getZ() + dz * 16
                );
                var chunk = world.getChunkManager().getChunk(chunkPos.getX() >> 4, chunkPos.getZ() >> 4);
                if (chunk == null) continue;

                for (var entry : chunk.getStructureStarts().entrySet()) {
                    var start = entry.getValue();
                    if (start == null || !start.hasChildren()) continue;
                    var box = start.getBoundingBox();
                    if (box == null) continue;
                    if (!box.contains(playerPos)) continue;

                    var id = structureRegistry.getId(entry.getKey());
                    if (id != null) {
                        found.add(id.toString().toLowerCase(Locale.ROOT));
                    }
                }
            }
        }

        return found;
    }

    /** Проверяет, есть ли структура с данным идентификатором (подстрока) во множестве. */
    public static boolean hasStructure(Set<String> structures, String substr) {
        for (var s : structures) {
            if (s.contains(substr.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
