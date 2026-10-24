package circuitlord.reactivemusic.server;

/**
 * Настройки серверной части ReactiveMusic.
 * Читаются из конфига сервера (если задан), иначе используются значения по умолчанию.
 */
public class ServerConfig {

    /** Интервал между отправками пакетов с данными о целях мобов клиентам (в тиках, 20 = 1 секунда). */
    public int targetUpdateInterval = 10;

    /** Максимальный радиус сканирования целей мобов вокруг игрока (в блоках). */
    public int targetScanRadius = 64;

    /** Отправлять ли данные о структурах клиентам (для STRUCT= на dedicated). */
    public boolean sendStructureData = true;

    /** Радиус сканирования структур вокруг игрока (в чанках, 1 = 3x3 чанк). */
    public int structureScanRadius = 1;

    /** Включить серверную часть вообще. Если false — сервер ничего не шлёт, клиент использует эвристику. */
    public boolean enabled = true;
}
