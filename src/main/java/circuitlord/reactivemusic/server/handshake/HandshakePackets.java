package circuitlord.reactivemusic.server.handshake;

import net.minecraft.network.PacketByteBuf;

/**
 * Пакеты рукопожатия между клиентом и сервером.
 * <p>
 * Регистрация и отправка зависят от лоадера (Fabric/Forge/NeoForge).
 */
public class HandshakePackets {

    /**
     * Клиент → Сервер: "у меня установлен клиентский мод ReactiveMusic".
     * Отправляется при входе на сервер.
     */
    public static class HandshakeRequest {
        public final String clientVersion;

        public HandshakeRequest(String clientVersion) {
            this.clientVersion = clientVersion;
        }

        public void write(PacketByteBuf buf) {
            buf.writeString(clientVersion);
        }

        public static HandshakeRequest read(PacketByteBuf buf) {
            return new HandshakeRequest(buf.readString());
        }
    }

    /**
     * Сервер → Клиент: "у меня установлен серверный мод ReactiveMusic".
     * Отправляется в ответ на HandshakeRequest.
     */
    public static class HandshakeResponse {
        public final String serverVersion;

        public HandshakeResponse(String serverVersion) {
            this.serverVersion = serverVersion;
        }

        public void write(PacketByteBuf buf) {
            buf.writeString(serverVersion);
        }

        public static HandshakeResponse read(PacketByteBuf buf) {
            return new HandshakeResponse(buf.readString());
        }
    }
}
