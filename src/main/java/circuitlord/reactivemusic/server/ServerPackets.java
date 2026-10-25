package circuitlord.reactivemusic.server;

import java.util.UUID;

import net.minecraft.network.PacketByteBuf;

/**
 * Описание сетевых пакетов серверной части.
 * <p>
 * Реализация отправки зависит от лоадера (Fabric ServerPlayNetworking /
 * Forge SimpleChannel / NeoForge SimpleChannel) и реализуется в каждой
 * ветке отдельно. Этот класс содержит только логику кодирования/декодирования.
 */
public class ServerPackets {

    /** Пакет: сервер -> клиент, статус цели одного моба относительно получателя. */
    public static class MobTargetPacket {
        public final UUID mobUuid;
        public final MobTargetStatus status;

        public MobTargetPacket(UUID mobUuid, MobTargetStatus status) {
            this.mobUuid = mobUuid;
            this.status = status;
        }

        public void write(PacketByteBuf buf) {
            buf.writeUuid(mobUuid);
            buf.writeByte(status.ordinal());
        }

        public static MobTargetPacket read(PacketByteBuf buf) {
            UUID mobUuid = buf.readUuid();
            MobTargetStatus status = MobTargetStatus.byOrdinal(buf.readByte());
            return new MobTargetPacket(mobUuid, status);
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

    private ServerPackets() {}
}
