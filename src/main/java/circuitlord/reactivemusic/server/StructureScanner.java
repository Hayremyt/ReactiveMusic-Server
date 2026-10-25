package circuitlord.reactivemusic.server;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
//? if >=1.20 {
import net.minecraft.registry.RegistryKeys;
//?} else {
/*import net.minecraft.util.registry.Registry;
*///?}

/**
 * Сканирует структуры вокруг позиции игрока на сервере.
 * <p>
 * Использует {@code StructureAccessor.getStructureContaining}, который находит структуру,
 * реально накрывающую позицию (с учётом ссылок между чанками), поэтому корректно работает и
 * для огромных структур (mansion, ancient city), чей {@code StructureStart} лежит далеко от игрока.
 */
public class StructureScanner {

    /**
     * Возвращает множество ID структур, в bounding box которых находится игрок.
     * {@code radiusChunks} больше не используется (оставлен для совместимости вызовов):
     * аксессор находит содержащую структуру напрямую, независимо от расстояния до её старта.
     */
    public Set<String> scan(ServerWorld world, BlockPos playerPos, int radiusChunks) {
        Set<String> found = new HashSet<>();
        try {
            var accessor = world.getStructureAccessor();

            //? if >=1.21.11 {
            /*var registry = world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
            *///?} else if >=1.20 {
            var registry = world.getRegistryManager().get(RegistryKeys.STRUCTURE);
            //?} else {
            /*var registry = world.getRegistryManager().get(Registry.STRUCTURE_KEY);
            *///?}

            for (var entry : registry.getEntrySet()) {
                var structure = entry.getValue();
                var start = accessor.getStructureContaining(playerPos, structure);
                if (start == null || !start.hasChildren()) continue;
                if (!start.getBoundingBox().contains(playerPos)) continue;

                var id = registry.getId(structure);
                if (id != null) {
                    found.add(id.toString().toLowerCase(Locale.ROOT));
                }
            }
        } catch (Throwable ignored) {
            // не даём сканированию структур ломать серверный тик
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
