package circuitlord.reactivemusic.server;

/**
 * Статус цели моба относительно конкретного игрока.
 * <p>
 * Сервер считает его под каждого получателя и шлёт только статус (без UUID цели),
 * поэтому клиент никогда не узнаёт, на кого именно нацелен моб — только «на меня / на другого / ни на кого».
 */
public enum MobTargetStatus {
    /** Моб ни на кого не нацелен (или перестал быть релевантным) — «забудь». */
    NONE,
    /** Моб нацелен на этого игрока. */
    ME,
    /** Моб нацелен на кого-то другого. */
    OTHER;

    public static MobTargetStatus byOrdinal(int ordinal) {
        MobTargetStatus[] values = values();
        if (ordinal < 0 || ordinal >= values.length) return NONE;
        return values[ordinal];
    }
}
