package dev.lovelace.loveleaderboards.integrations;

import dev.lovelace.lovecore.api.stats.StatChangedEvent;
import dev.lovelace.lovecore.api.stats.StatSubject;
import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.util.UUID;

/**
 * Единственный слушатель метрик экосистемы. Раньше три отдельных класса рефлексией
 * дозванивались до LoveHunt/LoveClans/LoveBrew — один по событию с неверным именем метода,
 * два периодическим опросом чужого API. Источники метрик теперь сами сообщают о себе через
 * {@code LoveCore.StatBus} (см. README ядра), и весь этот слушатель сводится к тому, чтобы
 * разложить событие по категориям, у которых {@code integration} в конфиге совпадает с именем
 * метрики.
 */
public class StatBusIntegration implements Listener {

    private final LoveLeaderboards plugin;

    public StatBusIntegration(LoveLeaderboards plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onStatChanged(StatChangedEvent event) {
        for (Category category : plugin.getCategoryManager().getAllCategories()) {
            if (event.metric().equalsIgnoreCase(category.integration())) {
                apply(category, event);
            }
        }
    }

    private void apply(Category category, StatChangedEvent event) {
        StatSubject subject = event.subject();
        boolean isDelta = event.mode() == StatChangedEvent.Mode.ADD;

        if (subject.kind() == StatSubject.Kind.PLAYER) {
            OfflinePlayer player = Bukkit.getOfflinePlayer(subject.id());
            String name = player.getName() != null ? player.getName() : subject.id().toString();
            if (isDelta) {
                plugin.getLeaderboardManager().updatePlayerScore(subject.id(), name, category.name(), event.value());
            } else {
                plugin.getLeaderboardManager().setPlayerScore(subject.id(), name, category.name(), event.value());
            }
            return;
        }

        String clanId = subject.id().toString();
        String clanName = resolveClanName(subject.id());
        if (isDelta) {
            plugin.getLeaderboardManager().updateClanScore(clanId, clanName, category.name(), event.value());
        } else {
            plugin.getLeaderboardManager().setClanScore(clanId, clanName, category.name(), event.value());
        }
    }

    /**
     * {@link StatSubject} клана несёт только UUID — имени в событии нет, а ни один оракул ядра
     * его не отдаёт ({@code ProfileOracle} резолвит клан только по игроку, не по id клана).
     * Единственный источник правды — сам LoveClans; вызываем его рефлексией так же, как раньше
     * это делал целый {@code LoveClansIntegration}, но теперь только ради имени, а не ради всех
     * метрик разом — сама метрика уже пришла в событии.
     */
    private String resolveClanName(UUID clanId) {
        try {
            Class<?> apiClass = Class.forName("me.lovelace.loveclans.api.LoveClansAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object clanOpt = apiClass.getMethod("getClanById", UUID.class).invoke(api, clanId);
            if (clanOpt instanceof java.util.Optional<?> optional && optional.isPresent()) {
                Object clan = optional.get();
                return String.valueOf(clan.getClass().getMethod("name").invoke(clan));
            }
        } catch (ReflectiveOperationException ignored) {
            // LoveClans не установлен или API изменился — покажем клан по id.
        }
        return clanId.toString();
    }
}
