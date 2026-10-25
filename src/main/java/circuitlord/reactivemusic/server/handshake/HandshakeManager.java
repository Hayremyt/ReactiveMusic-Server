package circuitlord.reactivemusic.server.handshake;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

/**
 * Менеджер рукопожатий между клиентом и сервером.
 * <p>
 * Позволяет каждой стороне узнать, установлен ли мод на другой стороне,
 * прежде чем начинать обмен данными о целях мобов и структурах.
 * <p>
 * Состояние <b>персистентно</b>: набор известных игроков с клиентским модом и флаг
 * «сервер имеет мод» сохраняются в файл через {@link #configure(Path)}, поэтому они
 * не теряются при выгрузке мира, рестарте сервера или других сбоях.
 */
public class HandshakeManager {

    /** Активные игроки с клиентским модом (текущее соединение). */
    private final Set<UUID> clientsWithMod = new HashSet<>();

    /** Игроки, однажды подтвердившие наличие мод (персистентно, переживает рестарт). */
    private final Set<UUID> knownClients = new HashSet<>();

    /** Флаг «этот сервер запускает серверный мод» (персистентный маркер). */
    private boolean serverHasMod = true;

    /** Путь к файлу состояния (null — работаем только в памяти). */
    private Path storePath = null;

    /** Версия протокола/мода, сохраняется вместе с состоянием. */
    private String protocolVersion = HandshakeConstants.MOD_VERSION;

    /** Таймаут ожидания ответа от сервера (в миллисекундах). Клиентская сторона. */
    public static final long HANDSHAKE_TIMEOUT_MS = 5000L;

    /** Интервал повторной отправки рукопожатия клиентом (в миллисекундах). */
    public static final long HANDSHAKE_RETRY_MS = 1000L;

    /**
     * Настроить персистентное хранилище и загрузить сохранённое состояние.
     * Вызывается лоадером при инициализации.
     */
    public synchronized void configure(Path storePath) {
        this.storePath = storePath;
        load();
    }

    /** Серверная часть: есть ли у игрока клиентский мод (подтверждён на этом соединении). */
    public synchronized boolean hasClientMod(UUID playerUuid) {
        return clientsWithMod.contains(playerUuid);
    }

    /** Был ли игрок когда-либо замечен с клиентским модом (персистентно). */
    public synchronized boolean isKnownClient(UUID playerUuid) {
        return knownClients.contains(playerUuid);
    }

    /** Серверная часть: имеет ли этот сервер серверный мод. */
    public boolean serverHasMod() {
        return serverHasMod;
    }

    public String getProtocolVersion() {
        return protocolVersion;
    }

    /**
     * Игрок присоединился. Если он уже известен как клиент с модом (по сохранённому состоянию),
     * регистрируем его заранее — так данные не потеряются, даже если HandshakeRequest не дойдёт.
     */
    public synchronized void markKnownOnJoin(UUID playerUuid) {
        if (knownClients.contains(playerUuid)) {
            clientsWithMod.add(playerUuid);
        }
    }

    /** Серверная часть: игрока вызвал серверный мод через рукопожатие. */
    public synchronized void registerClient(UUID playerUuid) {
        clientsWithMod.add(playerUuid);
        if (knownClients.add(playerUuid)) {
            save(); // новый клиент — обновляем персистентное состояние
        }
    }

    /** Серверная часть: удалить активную регистрацию при выходе (постоянная запись сохраняется). */
    public synchronized void unregisterClient(UUID playerUuid) {
        clientsWithMod.remove(playerUuid);
        // knownClients НЕ трогаем — игрок остаётся известным для последующих сессий.
    }

    /** Количество активных игроков с модом. */
    public synchronized int clientCount() {
        return clientsWithMod.size();
    }

    /** Количество известных (персистентных) клиентов с модом. */
    public synchronized int knownClientCount() {
        return knownClients.size();
    }

    /**
     * Сброс ТОЛЬКО активных соединений (например, при полной остановке сервера).
     * Персистентные данные (knownClients, serverHasMod) сохраняются.
     */
    public synchronized void clearActive() {
        clientsWithMod.clear();
    }

    // ---- персистентность ----

    private void load() {
        if (storePath == null) return;
        try {
            if (Files.exists(storePath)) {
                Properties props = new Properties();
                try (InputStream in = Files.newInputStream(storePath)) {
                    props.load(in);
                }
                serverHasMod = Boolean.parseBoolean(props.getProperty("serverHasMod", "true"));
                protocolVersion = props.getProperty("protocolVersion", protocolVersion);
                knownClients.clear();
                String list = props.getProperty("knownClients", "");
                if (!list.isBlank()) {
                    for (String part : list.split(",")) {
                        String trimmed = part.trim();
                        if (trimmed.isEmpty()) continue;
                        try {
                            knownClients.add(UUID.fromString(trimmed));
                        } catch (IllegalArgumentException ignored) {
                            // битый UUID — пропускаем
                        }
                    }
                }
            } else {
                save(); // создаём файл с дефолтным состоянием при первом запуске
            }
        } catch (Exception ignored) {
            // при любой ошибке чтения продолжаем работать в памяти
        }
    }

    private void save() {
        if (storePath == null) return;
        try {
            Path parent = storePath.getParent();
            if (parent != null) Files.createDirectories(parent);
            Properties props = new Properties();
            props.setProperty("serverHasMod", Boolean.toString(serverHasMod));
            props.setProperty("protocolVersion", protocolVersion);
            StringBuilder sb = new StringBuilder();
            for (UUID uuid : knownClients) {
                if (sb.length() > 0) sb.append(',');
                sb.append(uuid.toString());
            }
            props.setProperty("knownClients", sb.toString());
            try (OutputStream out = Files.newOutputStream(storePath)) {
                props.store(out, "ReactiveMusic server handshake state");
            }
        } catch (Exception ignored) {
            // если не удалось записать — продолжаем, данные останутся в памяти
        }
    }
}
