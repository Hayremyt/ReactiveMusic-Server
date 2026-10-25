package circuitlord.reactivemusic.server.handshake;

/**
 * Общие константы протокола рукопожатия между клиентом и сервером ReactiveMusic.
 * Используются и клиентской, и серверной частью.
 */
public final class HandshakeConstants {

    /** Таймаут ожидания ответа от сервера (в миллисекундах). */
    public static final long HANDSHAKE_TIMEOUT_MS = 5000L;

    /** Интервал повторной отправки рукопожатия клиентом (в миллисекундах). */
    public static final long HANDSHAKE_RETRY_MS = 1000L;

    /** Идентификатор сетевого канала (используется всеми лоадерами). */
    public static final String CHANNEL_ID = "reactivemusic:server";

    /** Сервер → Клиент: MobTargetPacket (точные цели мобов от серверного мода). */
    public static final String CHANNEL_TARGETS = "reactivemusic:server/targets";

    /** Сервер → Клиент: StructurePacket (структуры вокруг игрока от серверного мода). */
    public static final String CHANNEL_STRUCTURES = "reactivemusic:server/structures";

    private HandshakeConstants() {}
}
