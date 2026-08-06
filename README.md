# LoveLeaderboards

Продвинутая система лидерборда для Love* экосистемы с поддержкой множественных метрик и интеграцией с игровыми системами.

## Общая структура

LoveLeaderboards предоставляет интерактивный GUI-лидерборд, который отслеживает метрики из различных систем (охоты, контракты, клановые войны, поведение, адаптация). Плагин собирает статистику из LoveCore StatBus, обновляет БД в реальном времени и отображает топ игроков по различным критериям в красивом интерфейсе с пагинацией.

## Команды

| Команда | Алиасы | Описание | Пермишин |
|---|---|---|---|
| `/leaderboard` | `lb`, `top`, `loveleaderboards`, `топ` | Открыть главное меню лидерборда | `loveleaderboards.use` |
| `/loveleaderboardsadmin` | — | Администраторские команды | `loveleaderboards.admin` |
| `/leaderboardadmin` (устарело) | `lba`, `lbadmin` | Перенаправляет на `/loveleaderboardsadmin` | `loveleaderboards.admin` |

### Подкоманды `/loveleaderboardsadmin`

- `reload` — перезагрузить config.yml
- `reset <metricId>` — сбросить лидерборд по метрике
- `stats <player> <metricId>` — показать детальную статистику игрока
- `export <metricId> <file>` — экспортировать лидерборд в CSV

## Пермишины

| Пермишин | Описание | Default |
|---|---|---|
| `loveleaderboards.use` | Доступ к `/leaderboard` и меню | true |
| `loveleaderboards.admin` | Доступ ко всем админ-командам | op |

## Конфигурация (`config.yml`)

### Основные параметры

```yaml
plugin:
  enabled: true
  debug: false

# Обновление метрик
update:
  frequency-seconds: 60            # обновляет лидерборд каждые 60 сек
  batch-size: 100                  # сколько игроков обновлять одновременно
  enable-real-time: true           # реактивное обновление (на каждое событие)

# Пределы
leaderboard:
  top-size: 100                    # показывать топ 100 игроков
  pages-per-leaderboard: 5         # максимум страниц (5 страниц × 20 = 100)
  entries-per-page: 20             # игроков на странице

# Метрики
metrics:
  # Охоты (из LoveHunt)
  hunts_completed:
    enabled: true
    display-name: "<green>Hunt Master</green>"
    description: "Охот выполнено"
    source: "lovehunt"
    icon: "DIAMOND_SWORD"
    
  # Контракты (из LoveContracts)
  contracts_completed:
    enabled: true
    display-name: "<yellow>Contract Master</yellow>"
    description: "Контрактов выполнено"
    source: "lovecontracts"
    icon: "PAPER"
    
  # Клановые контракты
  clan_contracts_completed:
    enabled: true
    display-name: "<blue>Clan Oath Master</blue>"
    description: "Клановых обетов выполнено"
    source: "loveclans"
    icon: "BOOK"
    
  # Клановые войны
  clan_wars_won:
    enabled: true
    display-name: "<red>War Master</red>"
    description: "Войн выиграно"
    source: "loveclans"
    icon: "IRON_SWORD"
    
  # Поведение
  behavior_reputation:
    enabled: true
    display-name: "<gold>Behavioral Excellence</gold>"
    description: "Средний рейтинг поведения"
    source: "lovebehavior"
    icon: "EMERALD"
    
  # Адаптация
  adaptations_unlocked:
    enabled: true
    display-name: "<aqua>Adaptation Master</aqua>"
    description: "Адаптаций разблокировано"
    source: "loveadaptation"
    icon: "ENCHANTING_TABLE"
    
  # Кастомные метрики
  total_playtime_hours:
    enabled: true
    display-name: "<light_purple>Veteran Player</light_purple>"
    description: "Часов игры"
    source: "statbus"
    icon: "CLOCK"
    icon-format: "{{value}} hours"

# GUI
gui:
  title: "<gradient:#55FF55:#55FFFF>Leaderboards</gradient>"
  rows: 6                           # рядов в интерфейсе
  auto-update: true
  update-interval-ms: 5000          # обновляет GUI каждые 5 сек

# База данных
database:
  file: "leaderboards.db"
  pool-size: 10
  auto-backup: true
  backup-interval-hours: 6

# Логирование
logging:
  enabled: true
  log-metric-updates: false         # не логировать каждое обновление
  log-level: "INFO"
```

## Источники метрик

### LoveHunt

- `hunts_completed` — количество охот, выполненных игроком
- `hunts_total_reward` — общая награда из охот

### LoveContracts

- `contracts_completed` — количество контрактов, выполненных игроком
- `contracts_daily_streak` — текущая серия побед
- `contracts_success_rate` — процент успеха

### LoveClans

- `clan_contracts_completed` — клановые обеты, выполненные членом
- `clan_wars_won` — войн, выигранных кланом
- `clan_territory_controlled` — территорий под контролем клана
- `clan_level` — уровень клана
- `clan_influence` — влияние клана

### LoveBehavior

- `behavior_reputation` — средний рейтинг поведения
- `behavior_level` — текущий уровень поведения (0-6)

### LoveAdaptation

- `adaptations_unlocked` — количество разблокированных адаптаций
- `total_adaptation_progress` — общий прогресс всех адаптаций

### StatBus (LoveCore)

- Пользовательские метрики из `StatBus.recordMetric(player, metric, value)`

## Интеграции

### LoveHunt

Автоматически отслеживает статистику охот через StatBus.

### LoveContracts

Отслеживает выполнение контрактов и серии побед через StatBus.

### LoveClans

Показывает статистику клановых войн, территорий, уровней кланов.

### LoveBehavior

Отслеживает рейтинг поведения и уровни вежливости.

### LoveAdaptation

Показывает количество разблокированных адаптаций и общий прогресс.

### PlaceholderAPI

Плейсхолдеры для отображения позиции игрока в лидерборде и его значение метрики.

## Механика лидерборда

1. **Сбор метрик** → LoveCore StatBus собирает события из всех плагинов
2. **Обновление БД** → LoveLeaderboards периодически читает StatBus и обновляет локальную БД
3. **Сортировка** → лидерборд отсортирован по убыванию значения метрики
4. **Отображение** → GUI показывает топ игроков с их позицией, именем и значением

## Зависимости

### Обязательные

- **Paper 1.21** — базовый Minecraft сервер

### Мягкие зависимости

- **LoveCore** — StatBus для сбора метрик
- **LoveHunt** — статистика охот
- **LoveClans** — статистика кланов
- **PlaceholderAPI** — интеграция плейсхолдеров

## Установка и сборка

```bash
mvn package
```

Java 21, Paper 1.21. База данных: SQLite.

## Структура данных

- `leaderboard_entries` — записи лидерборда (игрок, метрика, значение, позиция, дата обновления)
- `metric_definitions` — определения метрик (ID, название, источник, иконка)
- `player_metrics` — кэш текущих значений метрик игроков (для быстрого доступа)

## PlaceholderAPI

| Плейсхолдер | Описание |
|---|---|
| `%loveleaderboards_rank_<metricId>%` | Позиция игрока по метрике |
| `%loveleaderboards_value_<metricId>%` | Значение метрики у игрока |
| `%loveleaderboards_top_1_name%` | Имя 1-го места |
| `%loveleaderboards_top_1_value%` | Значение у 1-го места |

## Расширение с новыми метриками

Для добавления новой метрики:

1. Убедитесь, что источник отправляет события в `LoveCore.StatBus`
2. Добавьте новую метрику в `config.yml` с уникальным ID
3. Перезагрузите конфиг командой `/loveleaderboardsadmin reload`
4. Новая метрика появится в интерфейсе лидерборда
