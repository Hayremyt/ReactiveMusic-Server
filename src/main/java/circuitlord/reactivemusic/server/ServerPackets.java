package circuitlord.reactivemusic.server;

import java.util.UUID;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Описание сетевых пакетов серверной части.
 * <p>
 * Реализация отправки зависит от лоадера (Fabric ServerPlayNetworking /
 * Forge SimpleChannel / NeoForge SimpleChannel) и реализуется в каждой
 * ветке отдельно. Этот класс содержит только логику кодирования/декодирования.
 */
public class ServerPackets {

    /** Пакет: сервер -> клиент, список целей мобов. */
    public static class MobTargetPacket {
        public final UUID mobUuid;
        public final UUID targetUuid; // null если цели нет

        public MobTargetPacket(UUID mobUuid, UUID targetUuid) {
            this.mobUuid = mobUuid;
            this.targetUuid = targetUuid;
        }

        public void write(PacketByteBuf buf) {
            buf.writeUuid(mobUuid);
            if (targetUuid == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                buf.writeUuid(targetUuid);
            }
        }

        public static MobTargetPacket read(PacketByteBuf buf) {
            UUID mobUuid = buf.readUuid();
            boolean hasTarget = buf.readBoolean();
            UUID targetUuid = hasTarget ? buf.readUuid() : null;
            return new MobTargetPacket(mobUuid, targetUuid);
        }
    }

    /** Пакет: сервер -> клиент, список структур вокруг игрока. */
    public static class StructurePacket {
        public final String[] structures;

        public StructurePacket(String[] structures) {
            this.structures = structures;
        }

        public void write(PacketByteBuf buf) {
            buf.writeInt(structures.length);
            for (var s : structures) {
                buf.writeString(s);
            }
        }

        public static StructurePacket read(PacketByteBuf buf) {
            int len = buf.readInt();
            var arr = new String[len];
            for (int i = 0; i < len; i++) {
                arr[i] = buf.readString();
            }
            return new StructurePacket(arr);
        }
    }

    /**
     * Отправить клиенту обновление цели одного моба.
     * Реализуется в лоадер-специфичном коде.
     */
    public static void sendMobTarget(ServerPlayerEntity player, MobTargetPacket packet) {
        // Заглушка — переопределяется в лоадер- branche
        throw new UnsupportedOperationException("Loader-specific implementation required");
    }

    /** Отправить клиенту список структур. */
    public static void sendStructures(ServerPlayerEntity player, StructurePacket packet) {
        throw new UnsupportedOperationException("Loader-specific implementation required");
    }
}
