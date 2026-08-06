package dev.lovelace.loveleaderboards.integrations;

import dev.lovelace.lovecore.api.stats.StatChangedEvent;
import dev.lovelace.lovecore.api.stats.StatSubject;
import dev.lovelace.loveleaderboards.LoveLeaderboards;
import dev.lovelace.loveleaderboards.models.Category;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;

import java.util.UUID;
import java.util.logging.Level;

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
     * Регистрирует обработчик расформирования клана напрямую через {@link HandlerList}
     * LoveClans, полученный рефлексией. <b>Нельзя</b> объявить это как обычный
     * {@code @EventHandler}-метод с параметром {@code org.bukkit.event.Event} — Bukkit при
     * регистрации слушателя ищет статический {@code getHandlerList()} у объявленного типа
     * параметра, поднимаясь по иерархии классов, и у самого абстрактного {@link Event} такого
     * метода нет. Это валит регистрацию {@code registerEvents(...)} целиком (не только этот
     * метод) с {@code IllegalPluginAccessException: Unable to find handler list for event
     * org.bukkit.event.Event. Static getHandlerList method required!} — именно это и не давало
     * подключиться к LoveCore.
     *
     * <p>LoveClans — softdepend, поэтому биндимся к его событию рефлексией так же, как раньше
     * это делал целый {@code LoveClansIntegration}, а не прямой ссылкой на класс события.
     * Если LoveClans не установлен или класс события не найден — тихо пропускаем, остальная
     * интеграция с LoveCore это не блокирует.</p>
     */
    public void registerClanDisbandBridge(LoveLeaderboards plugin) {
        try {
            Class<? extends Event> eventClass = Class.forName("me.lovelace.loveclans.api.events.ClanDisbandEvent")
                    .asSubclass(Event.class);
            // Confirms the class really carries the static getHandlerList() Bukkit needs, so a
            // future LoveClans refactor fails loudly here (caught below) instead of resurrecting
            // this exact bug through the back door.
            eventClass.getMethod("getHandlerList");
            EventExecutor executor = (listener, event) -> onClanDisband(event);
            Bukkit.getPluginManager().registerEvent(eventClass, this, EventPriority.MONITOR, executor, plugin, true);
        } catch (ReflectiveOperationException | ClassCastException e) {
            plugin.getLogger().log(Level.FINE, "LoveClans clan-disband bridge unavailable", e);
        }
    }

    /**
     * {@link StatSubject} клана несёт только UUID — имени в событии нет, а ни один оракул ядра
     * его не отдаёт ({@code ProfileOracle} резолвит клан только по игроку, не по id клана).
     * Единственный источник правды — сам LoveClans; методы события читаем рефлексией
     * ({@code clan()}, {@code actorId()} у {@code ClanDisbandEvent}, {@code id()}/{@code name()}
     * у {@code Clan}) — только ради имени, а не ради всех метрик разом, сама метрика уже пришла
     * через {@link StatChangedEvent}.
     */
    private void onClanDisband(Event event) {
        try {
            String clanId = null;
            String clanName = null;
            Object clan = event.getClass().getMethod("clan").invoke(event);
            if (clan != null) {
                try {
                    Object idObj = clan.getClass().getMethod("id").invoke(clan);
                    if (idObj != null) clanId = idObj.toString();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.FINE, "Reflection clan().id() failed", e);
                }
                try {
                    Object nameObj = clan.getClass().getMethod("name").invoke(clan);
                    if (nameObj != null) clanName = nameObj.toString();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.FINE, "Reflection clan().name() failed", e);
                }
            }

            if (clanId != null || clanName != null) {
                plugin.getLeaderboardManager().removeClan(clanId, clanName);
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Clan disband reflection handler failed", t);
        }
    }

    private String resolveClanName(UUID clanId) {
        try {
            Class<?> apiClass = Class.forName("me.lovelace.loveclans.api.LoveClansAPI");
            Object api = apiClass.getMethod("getInstance").invoke(null);
            Object clanOpt = apiClass.getMethod("getClanById", UUID.class).invoke(api, clanId);
            if (clanOpt instanceof java.util.Optional<?> optional && optional.isPresent()) {
                Object clan = optional.get();
                return String.valueOf(clan.getClass().getMethod("name").invoke(clan));
            }
        } catch (ReflectiveOperationException e) {
            plugin.getLogger().log(java.util.logging.Level.FINE, "LoveClans API resolveClanName reflective call failed: " + e.getMessage());
        }
        return clanId.toString();
    }
}
