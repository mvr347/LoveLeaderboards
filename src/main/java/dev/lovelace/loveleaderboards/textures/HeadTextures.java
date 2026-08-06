package dev.lovelace.loveleaderboards.textures;

/**
 * Централизованное хранилище base64 текстур голов (skull textures), используемых в GUI LoveLeaderboards.
 * <p>
 * Все base64-литералы текстур голов должны объявляться здесь, а не хардкодиться по месту
 * использования — так плагин следует единой точке правды для GUI-текстур, вместо дублирования
 * одних и тех же строк в разных классах. Это лишь запасные значения по умолчанию: их всегда можно
 * переопределить через {@code heads.yml} или {@code config.yml} — см. {@code BaseGui#getButtonHead}.
 */
public final class HeadTextures {

    private HeadTextures() {
        // Утилитарный класс-константа, инстанцирование не предполагается
    }

    /**
     * Иконка кнопки переключения на топ игроков.
     */
    public static final String GUI_TYPE_PLAYER =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjU3YzdlOTZhODAyYzI3MDgwYzdmODA1MzgxNDM2OGVhOTRkZjg2NDQ1OTEyMGU1MTU1NzE4YjUwM2MzZWQ3In19fQ==";

    /**
     * Иконка кнопки переключения на топ кланов.
     */
    public static final String GUI_TYPE_CLAN =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYjJiNWY5NjhjYzg4ZDNlOTg2NWQ2ZTdhOGQ1YmU3NWVhNzNhMGEzOTRiNTFlYWE1Zjk0YzA0NzU5ZGNkYTAyZCJ9fX0=";

    /**
     * Иконка кнопки переключения периода (весь период / за месяц).
     */
    public static final String GUI_PERIOD =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzVmYzFkYWRhOWM2NWE3YWJjZTM0MjQxNDBkM2FiMjI0ZmNjNTM5OGNiOGNmZDY3NWY0MzY4NjhiZTZmNTRmZCJ9fX0=";

    /**
     * Иконка по умолчанию для выбора категории лидерборда.
     */
    public static final String GUI_CATEGORY =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTQ3YWUyMDllOGE1MjU5MWNjMjBhYzBjOWVjNmE1Y2IzZGMwNGYyMzhhYzJkNzQzYjFkNTRmMTFlOWM1Yzg1In19fQ==";

    /**
     * Иконка кнопки сравнения игроков; используется также для стрелки «предыдущая страница».
     */
    public static final String GUI_COMPARISON =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMzdhZWU5YTc1YmYwZGY3ODk3MTgzMDE1Y2NhMGIyZDdiNzliYjNjMzRlYTU0MjRjNjc5NGJiNGZhOTVjMTZiZiJ9fX0=";

    /**
     * Иконка стрелки «следующая страница» для пагинации.
     */
    public static final String GUI_NEXT_PAGE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjgyYWQxYjRjY2RiNjZkNTkyNzJmMTc3YWMwZGRhZDE4YzA0NzJjNzcyNTM1ZTUwZmE5ZDkxNGIyMjFhNjc5NSJ9fX0=";

    /**
     * Иконка кнопки «назад».
     */
    public static final String GUI_BACK =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODY1MmM2NTEyMjI1NWMwNDY3ZmFlNzA5ODcyODRmOTc2YWMxYWUzN2VjZTQ2YmMzZmNhMjdjZTMyN2JiMWE3ZCJ9fX0=";

    /**
     * Иконка кнопки «закрыть».
     */
    public static final String GUI_CLOSE =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZjc2NDMzZjRmZWQ2ZmMyYThjMzU5YzExZTUwOTZhZGE5OWU4ZjQxNGZmZmNmNzlkZDAxY2MyYjIzZDkyNGZhNyJ9fX0=";

    /**
     * Заглушка «нет клана»; используется также для пустых слотов таблицы.
     */
    public static final String GUI_NO_CLAN =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTgzNTJhZjkyYWNhNzc0N2FjMjllNTE0MmFlOTEyYWVlMWViY2E5MDhiMzFjMWYxZTY4YzU5MmFhZjkyZTYzNiJ9fX0=";

    /**
     * Иконка по умолчанию, когда ни heads.yml, ни config.yml, ни один из перечисленных выше
     * ключей не подходит под запрошенную кнопку.
     */
    public static final String GUI_DEFAULT =
            "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNDQ4MWRmZTJiMmY5OWUzZGVjZTRjMzQ3MjY0MzM1ZjUzMTgzZjEzYjE4YTkxN2RkYjcyMzEzZTlkMDc0NjNmZCJ9fX0=";
}
