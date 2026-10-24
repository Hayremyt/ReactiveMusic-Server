package circuitlord.reactivemusic.server;

import java.util.HashSet;
import java.util.Set;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;

/**
 * Сканирует структуры вокруг игрока на сервере.
 * <p>
 * Клиент не получает данные о структурах от ваниллы, поэтому сервер сканирует
 * чанки вокруг игрока и отправляет список найденных структур.
 */
public class StructureScanner {

    /** Названия структур, найденных вокруг игрока в последнем сканировании. */
    private final Set<String> nearbyStructures = new HashSet<>();

    /**
     * Сканирует 3x3 чанков вокруг позиции игрока на наличие структур.
     * Вызывается редко (раз в несколько секунд), так как структуры не меняются динамически.
     */
    public void scan(ServerWorld world, BlockPos playerPos, int radiusChunks) {
        nearbyStructures.clear();

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
                        nearbyStructures.add(id.toString().toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
        }
    }

    /** Проверяет, есть ли структура с данным идентификатором (подстрока) рядом с игроком. */
    public boolean hasStructure(String substr) {
        for (var s : nearbyStructures) {
            if (s.contains(substr.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    /** Получить копию множества найденных структур. */
    public Set<String> getStructures() {
        return new HashSet<>(nearbyStructures);
    }

    public void clear() {
        nearbyStructures.clear();
    }
}
