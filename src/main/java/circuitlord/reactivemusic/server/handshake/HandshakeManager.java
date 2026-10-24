package circuitlord.reactivemusic.server.handshake;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Менеджер рукопожатий между клиентом и сервером.
 * <p>
 * Позволяет каждой стороне узнать, установлен ли мод на другой стороне,
 * прежде чем начинать обмен данными о целях мобов и структурах.
 * <p>
 * Этот класс содержит только общую логику. Сетевая отправка/получение
 * реализуется в каждой ветке лоадера (Fabric/Forge/NeoForge).
 */
public class HandshakeManager {

    /** Игроки, у которых есть клиентский мод (зарегистрированы через рукопожатие). */
    private final Set<UUID> clientsWithMod = new HashSet<>();

    /** Игроки, которым сервер подтвердил наличие серверного мода. */
    private final Set<UUID> clientsAcknowledged = new HashSet<>();

    /** Таймаут ожидания ответа от сервера (в миллисекундах). Клиентская сторона. */
    public static final long HANDSHAKE_TIMEOUT_MS = 5000L;

    /** Интервал повторной отправки рукопожатия клиентом (в миллисекундах). */
    public static final long HANDSHAKE_RETRY_MS = 1000L;

    /** Серверная часть: есть ли у игрока клиентский мод. */
    public boolean hasClientMod(UUID playerUuid) {
        return clientsWithMod.contains(playerUuid);
    }

    /** Серверная часть: подтверждён ли серверный мод для этого игрока. */
    public boolean isAcknowledged(UUID playerUuid) {
        return clientsAcknowledged.contains(playerUuid);
    }

    /** Серверная часть: игрока вызовет серверный мод при рукопожатии. */
    public void registerClient(UUID playerUuid) {
        clientsWithMod.add(playerUuid);
    }

    /** Серверная часть: удалить игрока при выходе. */
    public void unregisterClient(UUID playerUuid) {
        clientsWithMod.remove(playerUuid);
        clientsAcknowledged.remove(playerUuid);
    }

    /** Клиентская часть: сервер подтвердил наличие серверного мода. */
    public void acknowledgeServer(UUID playerUuid) {
        clientsAcknowledged.add(playerUuid);
    }

    /** Клиентская часть: сбросить состояние (например, при выходе с сервера). */
    public void reset() {
        clientsWithMod.clear();
        clientsAcknowledged.clear();
    }

    /** Клиентская часть: сколько игроков подтвердили серверный мод. */
    public int acknowledgedCount() {
        return clientsAcknowledged.size();
    }

    /** Серверная часть: сколько игроков имеют клиентский мод. */
    public int clientCount() {
        return clientsWithMod.size();
    }
}
