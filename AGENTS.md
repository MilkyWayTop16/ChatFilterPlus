# AGENTS.md — ChatFilterPlus

Инструкции для **Grok Build** и **Claude Code**. Следуй этому файлу при любой работе с репозиторием.

---

## 0. Рабочие принципы (кратко)

### 0.0 Standing workflow (всегда)

Четыре постоянных правила. Детали — §0.3–§0.4 и §2; при конфликте с «исследовать на всякий случай» побеждает этот блок + минимальное исследование (§0.1 / §0.4.1).

1. **Paper / Adventure / Bukkit API через Context7**  
   При добавлении или изменении вызовов Paper API, Adventure, event/chat API — сверяйся с **актуальной** документацией через MCP **Context7** (Paper / paper-api / Adventure и т.п.), а не выдумывай сигнатуры из памяти. Compile-версии по-прежнему из `pom.xml` / §1.

2. **Поиск по проекту — graph-first (codebase-memory; «Serena» = та же обязанность)**  
   Структурные вопросы (найти символ, callers/callees, impact) — сначала **codebase-memory** (`search_graph`, `trace_path`, при необходимости `get_code_snippet` / `get_architecture` / CLI §2). Слепой full-tree `grep`/`find` — **вторичен**, пока не исчерпан graph-first путь.  
   Если в запросе/skill названа **Serena** для поиска по коду — не поднимай второй search-stack: выполняй ту же graph-first обязанность через codebase-memory.

3. **После структурных правок — обновить Codebase Memory**  
   После нетривиальных изменений структуры (новые пакеты/классы/public API, rename, крупный рефакторинг, из-за которого граф устаревает) — **`index_repository`** (mode **`moderate`**, основной repo, не worktree; см. §2.1) **до** того, как снова считать граф авторитетным. Точечные правки без смены структуры — reindex не обязателен.

4. **Не дублировать существующий код (reuse-first)**  
   Согласовано с §0.4 Ponytail и §7: расширяй Manager / `*Config` / `FilterType` / Action tag / engine / command path. Не плоди параллельные фильтры, кэши, action/notification-движки «на всякий случай».

### 0.1 Не исследовать проект без причины

- Не запускай обход графа, полный `search_graph`/`grep`/чтение деревьев «на всякий случай».
- Исследуй **только** то, что нужно для текущей задачи (порядок инструментов — §0.0 п.2 и §2.2).
- AGENTS.md + уже известный контекст сессии — достаточно, пока не упёрся в пробел.
- Широкий `index_repository` — если графа нет / он устарел после структурных правок (§0.0 п.3) / пользователь попросил.

### 0.2 Самостоятельность

Агент должен самостоятельно принимать технические решения.

Если существует объективно лучший вариант
(производительность, читаемость, безопасность, поддерживаемость,
совместимость версий, соответствие архитектуре проекта),
выбирай его автоматически.

Не перечисляй мне 3–5 вариантов без необходимости.
Предлагай один лучший и реализуй его.

Спрашивай пользователя только если решение влияет на:

- функциональность;
- UX;
- формат конфигурации;
- совместимость;
- права;
- удаление существующего поведения.

Во всех остальных случаях принимай решение самостоятельно.

### 0.3 Использование MCP (codebase-memory)

Сводка graph-first — §0.0 п.2. Здесь — операционные правила:

- Структурные вопросы: **`search_graph`**, **`trace_path`** (и при необходимости `get_code_snippet` / `get_architecture`).
- Сначала граф, потом файлы — не наоборот.
- Не читай целые модули, если `trace_path` + snippet закрывают вопрос.
- Если tools MCP недоступны в сессии — CLI `codebase-memory-mcp cli …` (см. §2).
- Paper/Adventure API docs — **Context7** (§0.0 п.1), не подменяй graph-поиском по репо.

### 0.4 Ponytail (lazy senior)

Думай как **ленивый senior**: лучший код — тот, который **не написали**. Reuse-first — §0.0 п.4.

Перед новым кодом пройди лестницу (после понимания задачи, не вместо него):

1. Нужно ли это вообще? (YAGNI)
2. Уже есть в проекте? → **reuse**
3. Stdlib / Paper API (docs через Context7, §0.0 п.1) / уже подключённая зависимость?
4. Можно одной строкой / минимальным diff?
5. Только потом — минимальный рабочий код

Правила: без лишних абстракций и зависимостей; deletion > addition; boring > clever; **наименьший diff в правильном месте** (root cause, не симптом).  
Не ленись только в: понимании задачи, security/data-loss, потокобезопасности async-чата, validation на границах, том, что явно попросили.

### 0.4.1 Минимальное исследование

Не исследуй проект ради исследования.

Перед использованием graph ответь себе:

"Мне действительно нужна эта информация для текущей задачи?"

Если нет —
не запускай search_graph,
не открывай десятки файлов,
не читай полрепозитория.

Изучай только тот участок,
который непосредственно относится к задаче.

### 0.5 Несколько файлов — открывай постепенно

Если изменение затрагивает **несколько** файлов:

- **не** открывай их все сразу;
- иди по цепочке: graph/callers → один файл → правка → следующий **только когда стало нужно**;
- не держи в контексте «весь feature-slice» заранее.

### 0.5.1 Контекст важнее повторного исследования

Используй уже накопленный контекст текущей сессии.

Если нужная информация уже известна,
не перечитывай те же файлы повторно.

Не выполняй одинаковые graph-запросы несколько раз,
если архитектура уже понятна.

### 0.6 Стиль и простота

- Держи существующий стиль проекта.
- Не усложняй архитектуру без необходимости.

---

## 1. О проекте

**ChatFilterPlus** — Paper/Spigot-плагин (Java 17) для фильтрации чата: **мат**, **запрещённые слова** (читы/софт), **ссылки/реклама**, **капс**, **анти-спам**, с уведомлениями (игрок/админ/консоль/Discord), стадийными наказаниями, файловыми логами и авто-миграцией конфигов.

Целевой сервер — современный Paper (**1.20.4+**, тестируется на **1.21.x**). MiniMessage-рендеринг требует рантайма **1.18+**; на более старых сборках срабатывает legacy/hex-фолбэк в `HexColors`.

| | |
|--|--|
| **GroupId / artifact** | `org.gw` / `ChatFilterPlus` |
| **Version** | `2.2` (`pom.xml` **и** `plugin.yml` — правь оба) |
| **Main class** | `org.gw.chatfilterplus.ChatFilterPlus` |
| **Сборка** | Maven (`pom.xml`), Java **17** |
| **API** | Paper API `1.20.4-R0.1-SNAPSHOT` (`provided`) |
| **Adventure** | `net.kyori:*` **4.16.0** (`provided`) — версия консистентна с той, что бандлит paper-api 1.20.4 |
| **api-version** | `1.16` (в `plugin.yml`) |
| **Команда** | `/chatfilterplus` (`/cfp`) |
| **Hard depend** | **LuckPerms** (`plugin.yml` `depend`) |
| **Soft depend** | PlaceholderAPI (hook) |
| **Shade** | `bstats` (→ `org.gw.chatfilterplus.bstats`) + `com.google.gson` (→ `org.gw.chatfilterplus.shaded.gson`) |
| **Не shade** | Paper API, adventure, LuckPerms, PlaceholderAPI |

**Не трогать без явной просьбы:**

- `config-version: 2.2` во всех YAML (config, bad-words, links, caps, blocked-words, anti-spam). Bump — только вместе с миграцией.
- Версия в **двух** местах (`pom.xml` + `plugin.yml`) должна совпадать — `UpdateChecker` читает `plugin.yml`.
- Каталоги `target/`, `.idea/`, `.claude/`.
- **Комментарии в Java** держи как в соседних файлах: в новом коде их почти нет; в YAML комментарии ожидаемы (это UX для пользователя).
- Двухпроходный enforce чата/команд (§3.2) и TTL pending-решений — не ломать.
- 36-символьный алфавит `ProfanityEngine` и таблицу `TextNormalizer.mapChar` — не менять без понимания последствий для trie.

---

## 2. Codebase-memory-mcp

Проект индексируется через **codebase-memory-mcp**. Граф — основной источник структурных знаний.

Имя проекта в графе:

`C-Users-Stas-IdeaProjects-d09cd0bed0b8-d0bfd0bbd0b0d0b3d0b8d0bdd18b-ChatFilterPlus`

Проверка: `list_projects`, `index_status --project <name>`.

### 2.1 Когда индексировать

Обязательность после структурных правок — §0.0 п.3.

- Первый заход / после крупного рефакторинга / «граф устарел» / новые пакеты·классы·public API / rename → `index_repository`.
- Режим по умолчанию: **`moderate`** (type-aware CALLS + similarity/semantic).
- Исключения индекса: `.idea`, `target`, `.claude`, `.git`.
- **Индексируй основной репозиторий**, а не worktree в `.claude/worktrees/*` — актуальный код там.

Пример CLI:

```bash
codebase-memory-mcp cli index_repository \
  --repo-path "C:/Users/Stas/IdeaProjects/Мои плагины/ChatFilterPlus" \
  --mode moderate
```

### 2.2 Правила использования графа

| Задача | Инструмент |
|--------|------------|
| Найти класс/метод по имени | `search_graph` (`query`, `name_pattern`, `label`, `file_pattern`) |
| Кто вызывает / кого вызывает | `trace_path` (`direction=inbound\|outbound\|both`) |
| Обзор архитектуры / кластеры | `get_architecture` |
| Прочитать тело символа | `get_code_snippet` (после search) |
| Текст по содержимому | `search_code` / встроенный grep |
| Влияние локальных правок | `detect_changes` |

**Приоритет исследования (обязательно):**

1. Только если нужно для задачи — `search_graph` / `trace_path` / `get_architecture`
2. `get_code_snippet` или точечное чтение **одного** нужного файла
3. Следующий файл — только когда без него нельзя продолжить (не открывай пачку сразу)
4. Не вычитывать «полпроекта» ради одного метода
5. При неоднозначном имени (`load`, `reload`, `handleMessage`, `evaluate`) — сначала `search_graph`, затем `trace_path` с точным `function_name`

### 2.3 Если MCP tools недоступны в сессии

Допустимо CLI:

```text
codebase-memory-mcp cli search_graph --project <name> --name-pattern ".*ProfanityEngine.*"
codebase-memory-mcp cli trace_path --project <name> --function-name processMessage --direction both --depth 2
```

Не подменяй граф слепым `find`/`grep` по всему дереву, пока не исчерпан graph-first путь.

---

## 3. Архитектура

### 3.1 Слои и пакеты

Корень: `org.gw.chatfilterplus`

| Пакет | Роль |
|-------|------|
| *(root)* | `ChatFilterPlus` — lifecycle, wiring менеджеров, динамическая регистрация chat-listener, reload, `log`/`console`/`error` |
| `managers` | Оркестрация и фильтры: `ChatManager`, `FilterProcessor`, `MessageCacheManager`, `WordsManager`, `BlockedWordsManager`, `LinksManager`, `AdaptiveAdFilter`, `CapsManager`, `AntiSpamManager`, `NotificationManager`, `PunishmentManager`, `LogCleanupManager`, `ConfigManager`, enum `FilterType` |
| `configs` | Config-объекты `*Config` + `ActionManager` (tag-пайплайн), `ConfigUpdater`, `ConfigUtils`, `LegacyPermissionMigrator`, `SpamConfigMigrator` |
| `commands` | `/cfp`: `CommandsHandler` (dispatch) → `WordsCommand` / `LinksCommand` / `CapsCommand` / `NotifyCommand` / `ReloadCommand` / `HelpCommand` + `CommandsTabCompleter` |
| `listeners` | `CommandFilterListener` (двухпроходная фильтрация команд), `CommandSendListener` |
| `utils` | `HexColors`, `TextNormalizer`, `AdTextAnalyzer`, `WordNormalizer` (+`SafeWordsTrie`), `PermissionCompat`, `AntiSpamResult`, `PlaceholderUtil`, `UpdateChecker`, `BStats` |
| `managers.profanity` | `ProfanityEngine` (trie-движок мата) + `ProfanityTrie` / `CompactView` / `ObfuscationDetector` |

### 3.2 Ключевые компоненты (runtime)

```
ChatFilterPlus (JavaPlugin)
 ├─ ConfigManager
 │   ├─ MainConfig / BadWordsConfig / LinksConfig / CapsConfig / BlockedWordsConfig / AntiSpamConfig
 │   └─ ActionManager                (пре-парсинг [tag]-действий, кэш ParsedAction)
 ├─ WordsManager        → ProfanityEngine (AtomicReference, hot-swap на reload)
 ├─ BlockedWordsManager → ProfanityEngine
 ├─ CapsManager
 ├─ LinksManager        → AdaptiveAdFilter (per-player suspicion)
 ├─ BlockedWordsManager / AntiSpamManager
 ├─ NotificationManager (player/admin/console/discord async)
 ├─ LogCleanupManager   (single-thread executor, append + cleanup)
 ├─ PunishmentManager   (EnumMap<FilterType>, стадии, cooldowns)
 ├─ MessageCacheManager → FilterProcessor
 ├─ ChatManager         (Listener: chat + командный enforce, pending-решения)
 └─ UpdateChecker / BStats
```

**Пайплайн фильтрации сообщения** (`FilterProcessor.processMessage`) — строгий порядок:

`BLOCKED_WORDS → BAD_WORDS → LINKS → CAPS`

- `BLOCKED_WORDS` / `BAD_WORDS`: `ProfanityEngine.findMatches` (trie по compact-виду + fuzzy trie). `BAD_WORDS` → динамическая звёздная замена (`п***с`); `BLOCKED_WORDS` → блок/замена.
- `LINKS`: `LinksManager.findBlockedLinks` (smart-detection, гомоглифы, де-обфускация точки + `AdaptiveAdFilter`).
- `CAPS`: `CapsManager.isCaps` / `fixCaps`.
- Результат — `MessageCacheManager.CachedMessage` (filteredMessage, badWords, links, blockedWords, isCaps, timestamp).

**Порядок приоритетов срабатывания** для решения «блок / замена / наказание / уведомление» задан в `FilterType.PRIORITY_ORDER` = `BLOCKED_WORDS, BAD_WORDS, LINKS, CAPS`. Первый активный фильтр определяет наказание.

**Обработка чата (двухпроходная)** — регистрируется в `ChatFilterPlus.registerChatListener()`:

1. **Основной проход** на приоритете из `settings.compatibility.event-priority` → `ChatManager.onPlayerChat` → `handleMessage`:
   - `collectBypassedFilters` (EnumSet: фильтр выключен ИЛИ игрок его обходит),
   - `AntiSpamManager.checkSpam` (флуд символов → похожие сообщения → общий кулдаун),
   - `MessageCacheManager.analyzeAndCacheMessage`,
   - `shouldBlockMessage` / `determineFinalMessage`,
   - уведомления откладываются на 1 тик (`runTaskLater`) — **нельзя** трогать Bukkit API из async-потока чата напрямую.
   - Решение сохраняется в `pendingChatDecisions` (TTL 3с).
2. **Enforce-проход** на `HIGHEST` → `enforcePlayerChat`: переприменяет решение, если другой плагин отменил/переписал событие (защита от конфликтов). Блок реализуется через `setCancelled(true)` + очистку `getRecipients()`.

**Обработка команд (двухпроходная)** — `CommandFilterListener`:

- `LOWEST` → `handleCommandMessage` (то же ядро `handleMessage`) → `pendingCommandDecisions`.
- `HIGHEST` → `enforceCommand`.
- Список фильтруемых команд — `settings.command-filtering.commands`.

**Reload** (`ChatFilterPlus.reloadConfigs`): перечитать YAML → пересобрать `ProfanityEngine` в менеджерах → reload остальных менеджеров/кэша/уведомлений → **снять и заново зарегистрировать** chat-listener (приоритет мог измениться).

### 3.3 Повторяющиеся паттерны проекта

1. **FilterType как единый источник истины**
   - Enum `FilterType` (`BAD_WORDS`, `LINKS`, `CAPS`, `BLOCKED_WORDS`, `ANTI_SPAM`) хранит: ключ конфига, аксессор к нужному `FileConfiguration`, пути (`filterPath`/`logPath`/`punishmentPath`/`notificationPath`), имена лог-файлов, bypass-пермишен, дефолтные шаблоны, `PRIORITY_ORDER`.
   - **Новый фильтр** = новая константа enum + config-объект, не разбрасывай `switch` по коду. Точечные `switch` по типу допустимы только в `ConfigManager` и `ChatManager.isTriggered/detectedItems`.

2. **Config object + `loadWithUpdate`**
   - Каждый YAML — свой `*Config` в `configs`, грузится через `ConfigUtils.loadWithUpdate` → `ConfigUpdater` (merge отсутствующих ключей, бэкап, `config-version`).
   - Хранимые поля `volatile`, списки чистятся `ConfigUtils.cleanStringList/cleanWordList`.
   - Не читай «сырые» ключи в hot-path — предпочитай геттеры `ConfigManager` / кэшированные поля.

3. **ProfanityEngine (trie, не regex)**
   - Старый `PatternFactory` (regex на слово) **удалён** — не возрождай.
   - Движок: `TrieNode[36]` (compact-алфавит) + fuzzy-trie; `CompactView` мапит символы через `TextNormalizer.mapChar`, схлопывает повторы и хранит `originIndex` для возврата к позициям в оригинале.
   - `PrecisionOptions.forLevel`: `high` = fuzzy только при обфускации, `minConfidence 80`; иначе `70`. `safeExact` — точный вайтлист.
   - Инстанс живёт за `AtomicReference` (`WordsManager.engineRef`) — hot-swap на reload, без гонок в async-чате.

4. **LinksManager + AdaptiveAdFilter**
   - `findBlockedLinks` → список `LinkMatch(start,end,text)`; нормализация: снятие схемы/пути **до** схлопывания разделителей, гомоглифы (`HOMOGLYPHS`, применяются после `toLowerCase`), де-обфускация точки только по явным маркерам.
   - Домены вайтлиста прогоняются через ту же нормализацию, что и ссылки из чата (иначе ключи не совпадут).
   - `AdaptiveAdFilter`: per-player `PlayerAdState` (уровень подозрения, история `AdFingerprint`, known handles/keywords). Эскалация на повторную «рекламоподобную» похожесть. Конфиг — `links.yml → filter.adaptive-ad-filter.*`.
   - `getSuspicionLevel(uuid) > 0` заставляет `MessageCacheManager` **обходить кэш** (подозрительные игроки всегда переоцениваются).

5. **MessageCacheManager (LRU + TTL)**
   - `LinkedHashMap` (access-order) под `synchronizedMap`, вытеснение через `removeEldestEntry`; фоновая чистка по TTL.
   - Сообщения со **ссылками не кэшируются**; подозрительные (adaptive) — тоже мимо кэша.
   - Ключ кэша учитывает bypass-флаги.

6. **Actions pipeline (UX)**
   - Пути `actions.*` / `notifications.<filter>.*` во всех YAML.
   - Теги `[message]`, `[message-console]`, `[broadcast]`, `[sound]`, `[title]`, `[subtitle]`, `[actionbar]`, `[console-command]`, `[player-command]`, `[effect]`, `[teleport]`, `[give-item]` — в `ActionManager`.
   - Для `[message]`: снять **один** ведущий пробел после `]`, **не** `trim()` всего content.
   - Плейсхолдеры `{key}` (+ PlaceholderAPI через `PlaceholderUtil`, если доступен).
   - `ActionManager` пре-парсит секции в `ParsedAction` при reload — не парси строку экшена на каждый вызов.

7. **Notifications + Punishments (per-FilterType)**
   - `NotificationManager`: `notifyPlayer/notifyAdmins/notifyConsole/notifyDiscord`; Discord — async HTTP.
   - `PunishmentManager`: `EnumMap<FilterType,…>` для файлов логов, счётчиков нарушений и cooldown-ов; стадии из `punishments.<filter>.stages.*`; команды исполняются на main-thread.

8. **Двухпроходный enforce (chat + command)**
   - Каждое решение кэшируется в `pending*Decisions` с TTL `DECISION_TTL_MILLIS` (3с) и переприменяется на `HIGHEST`.
   - Нужно для совместимости с другими чат-плагинами (aggressive-mode). Не удаляй второй проход при рефакторинге listener'ов.

9. **Version-safe HexColors**
   - MiniMessage (нативные теги `<gradient>`, `<rainbow>`, hover/click) + legacy `&`/`&#RRGGBB`/`&x` + raw `#RRGGBB` → Adventure `Component`.
   - `createMiniMessage()` ловит `Throwable` → при отсутствии MiniMessage в рантайме падает в legacy/section-фолбэк.
   - Конвертер пропускает уже готовые `<…>`-теги нетронутыми (иначе hex внутри `<gradient:#..>` ломает синтаксис).
   - `TRANSLATE_CACHE` — LRU на результаты. Консоль — `translateForConsole`.

10. **Мигрторы конфигов**
    - `ConfigUpdater` — merge недостающих ключей + бэкап, версия `config-version`.
    - `LegacyPermissionMigrator` — миграция прав 2.1 → 2.2 (async).
    - `SpamConfigMigrator` — миграция секций анти-спама.
    - `PermissionCompat` — рантайм-совместимость bypass-прав (алиас `antispam` → `spam`).

---

## 4. Соглашения по именованию и структуре

### Java

- Пакеты: lowercase `org.gw.chatfilterplus.<layer>`.
- Классы: `PascalCase`; managers `*Manager`, config-объекты `*Config`, listeners `*Listener`, команды `*Command`, мигрторы `*Migrator`.
- Методы/поля: `camelCase`. Shared state — `Concurrent*` / `Atomic*` / `volatile`.
- Команды: `*Command.execute(sender, args)`, регистрация в `CommandsHandler` (switch по `args[0]` + проверка права → делегирование).
- Records для лёгких DTO: `ProfanityEngine.Match`, `LinksManager.LinkMatch`, `AdaptiveAdFilter.AdHit`, `ActionManager.ParsedAction`, `FilterProcessor.EngineResult/LinkResult`.
- Lombok `@Getter` — как в соседних файлах.
- Новый public API — только по необходимости; `private`/`final`/`static` где достаточно.

### YAML / resources (7 файлов)

| Файл | Назначение |
|------|------------|
| `config.yml` | settings (logs, cache, bstats, update-checker, compatibility, command-filtering, admin-self-notify) + `actions.*` |
| `bad-words.yml` | фильтр мата: `filter.bad-words`, `safe-words`, `bad-words`, level, lookalikes, уведомления, наказания |
| `blocked-words.yml` | запрещённые слова (читы/софт) |
| `links.yml` | ссылки + `filter.smart-detection.*` + `filter.adaptive-ad-filter.*` |
| `caps.yml` | анти-капс + приоритеты относительно мата/запрещённых |
| `anti-spam.yml` | general-cooldown / similar-message / character-flood |
| `plugin.yml` | command `/cfp`, permissions, `depend: [LuckPerms]`, `softdepend: [PlaceholderAPI]` |

- Ключи: **kebab-case**. `config-version: 2.2` — не повышать без миграции.
- Материалы/звуки/эффекты: Bukkit enum-имена.
- User-facing строки — на русском, с hex/MiniMessage.

### Permissions (plugin.yml)

- Bypass фильтров: `chatfilterplus.bypass.chatfilter.{badwords,links,caps,blockedwords,spam}` (+ legacy-алиас `.antispam` → `.spam`).
- Bypass наказаний: `chatfilterplus.bypass.punishment.{…}`.
- Команды: `chatfilterplus.words` / `.links` / `.caps` / `.notify` / `.reload` (+ дети `.words.add/remove/list`, `.links.whitelist.*`, `.links.keywords.*`, `.caps.whitelist.*`).
- `chatfilterplus.admin.notify`, `chatfilterplus.admin` (агрегат).

---

## 5. Производительность

1. **Async-чат**: `AsyncPlayerChatEvent` приходит НЕ в main-thread. Любой Bukkit API (getPlayer, эффекты, команды, титлы) — только через `runTask`/`runTaskLater`. Уведомления уже отложены на 1 тик.
2. **Кэш**: `MessageCacheManager` O(1) LRU; не кэшируй ссылки/подозрительных; не заводи второй кэш рядом.
3. **ProfanityEngine**: trie-обход по `CompactView` за один проход; не возвращай regex-per-word.
4. **Движки за `AtomicReference`**: пересобирай целиком на reload, не мутируй на лету.
5. **Логи**: `LogCleanupManager` — single-thread executor (append + cleanup), не пиши в файл из чат-потока напрямую.
6. **HTTP** (Discord webhook, UpdateChecker): async, с таймаутами; UpdateChecker уважает `MIN_CHECK_INTERVAL`.
7. **ActionManager**: секции пре-парсятся при reload; не парси теги на каждый вызов.
8. **Shade** только `bstats` + `gson` — не тащи новые fat-deps.

---

## 6. Качество кода

### Обязательно

- Компиляция под **Java 17** и Paper API из `pom.xml` (`mvn -q -DskipTests package`).
- Потокобезопасность async-чата (см. §5.1); shared state — `Concurrent*`/`Atomic*`.
- Null-safety для player/world/item; early-return.
- Сообщения игроку — через `ActionManager` / `HexColors`, не хардкодь длинные UX-строки в командах.
- Bypass-права — через `PermissionCompat`, не ad-hoc строки.
- Версия синхронна в `pom.xml` **и** `plugin.yml`.
- Adventure держи консистентной версии с paper-api (сейчас **4.16.0**) — смешение версий ломает `MiniMessage.miniMessage()` в рантайме.

### Запрещено без причины

- Новые тяжёлые зависимости в shade.
- Bukkit API из async chat-потока без scheduler.
- Возврат regex-движка мата (`PatternFactory`) вместо `ProfanityEngine`.
- Bump `config-version` без миграции.
- Удаление второго (enforce) прохода chat/command listener.
- Второй параллельный action/notification/cache движок.

### Тесты / проверка

- Компиляция после нетривиальных правок.
- Ручные сценарии: мат (+lookalikes/обфускация), запрещённое слово, ссылка (обычная/обфусцированная/адаптивная реклама), капс, анти-спам (флуд/похожие/кулдаун), фильтрация в командах (`/msg` и т.п.), `/cfp reload`, апдейт-чекер, конфликт с другим чат-плагином (enforce-проход).

---

## 7. Правила рефакторинга

1. **Сначала reuse** — расширь Manager/Config/FilterType/ActionManager tag, не плоди параллельные системы.
2. Сохраняй точки входа: `ChatManager.handleMessage` / `onPlayerChat` / `enforcePlayerChat` / `handleCommandMessage` / `enforceCommand`, `FilterProcessor.processMessage`, `ProfanityEngine.findMatches`, `LinksManager.findBlockedLinks`, `ConfigManager.executeActions*`, `PunishmentManager.handlePunishment`, `HexColors.translate*`.
3. Меняешь `FilterType` — проверь все места сборки путей/шаблонов и `PRIORITY_ORDER`.
4. Меняешь пайплайн фильтрации — проверь порядок `BLOCKED→BAD→LINKS→CAPS`, кэш, приоритеты капса, наказание «первого активного».
5. Меняешь listener — сохрани оба прохода и pending-TTL.
6. `detect_changes` / `trace_path inbound` перед удалением метода.

Перед созданием нового класса,
менеджера,
утилиты,
enum,
интерфейса
или сервиса

обязательно проверить,
нельзя ли расширить уже существующую реализацию.

Предпочитать изменение существующего кода
созданию нового.

---

## 8. Принятие решений и вопросы

| Ситуация | Поведение |
|----------|-----------|
| Есть **объективно лучший** вариант (perf async-safety, порядок фильтров, существующий tag/FilterType, версии adventure) | **Делай сам**, кратко объясни в ответе |
| Выбор **меняет поведение фильтра / UX / конфиг-контракт** (что ловится, режимы, наказания, права, формат YAML) | **Спроси** |
| Неясно требование | 1–2 уточнения; не блокируй работу на мелочах |
| Стиль vs новый framework | **Стиль проекта** побеждает |

### Не усложнять

- Не вводи DI-framework, event-bus, multi-module без запроса.
- Не добавляй abstraction layer «на будущее», если 1–2 call sites.
- Новая сущность — только если не вписывается в существующий Manager/Config/FilterType/Action tag/Command.

### Стиль

- Следуй соседним файлам: lombok, early-return, `switch` по `FilterType`/тегам, `Concurrent*`/`Atomic*` для shared state.
- Имена domain-oriented: `handleMessage`, `processMessage`, `findMatches`, `findBlockedLinks`, `checkSpam`, `executeActions`.
- Русский в user-facing строках / console-логах; код и AGENTS — технический ясный язык.

---

## 9. Типовой workflow агента

1. Уточни задачу; **не** исследуй репозиторий, если ответ уже ясен.
2. При необходимости (граф/символ): `search_graph` / `trace_path` — точечно.
3. Открывай файлы **по одному/по мере нужды**, не пачкой «все связанные».
4. Минимальный diff (Ponytail + reuse-first) в существующих слоях.
5. YAML-compat и `config-version: 2.2`; версия синхронна в `pom.xml` + `plugin.yml`.
6. Compile / критичный сценарий при нетривиальных правках.
7. Краткий отчёт: что/зачем, без воды.

---

## 10. Быстрые «не ломай»

| Область | Правило |
|---------|---------|
| Async-чат | Bukkit API только через scheduler; уведомления через `runTaskLater` |
| Порядок фильтров | `BLOCKED_WORDS → BAD_WORDS → LINKS → CAPS` (`FilterType.PRIORITY_ORDER`) |
| Enforce | оба прохода (основной + `HIGHEST`), pending-TTL 3с |
| Движок мата | `ProfanityEngine` (trie), НЕ regex-per-word |
| Engine hot-swap | пересборка за `AtomicReference` на reload |
| Кэш | ссылки/подозрительных не кэшировать; ключ учитывает bypass |
| Adaptive ad | `links.yml → filter.adaptive-ad-filter.*`; suspicion обходит кэш |
| HexColors | версия adventure консистентна с paper-api; `<…>`-теги не мять |
| Message actions | один ведущий пробел после `]`, не `trim()` весь content |
| Shade | только `bstats` + `gson` (relocated) |
| Версия | синхронно в `pom.xml` + `plugin.yml` |
| config-version | `2.2` — без миграции не bump |
| Права | через `PermissionCompat` (+ legacy `antispam`→`spam`) |

---

## 11. Project graph snapshot (ориентир)

После индекса (moderate): ~**1361** nodes, ~**4280** edges (46 Java + 7 YAML файлов).

Ключевые хабы по fan-in (ориентир, не догма):

- `ChatFilterPlus` (plugin hub: `log`/`console`/`error`/getters)
- `ConfigManager.executeActions` / `ActionManager`
- `FilterType` (config-пути, шаблоны, приоритеты)
- `FilterProcessor.processMessage` (пайплайн)
- `ProfanityEngine.findMatches` / `TextNormalizer.normalizeCompact`
- `LinksManager.findBlockedLinks` / `AdaptiveAdFilter.evaluate`
- `MessageCacheManager.analyzeAndCacheMessage`
- `HexColors.translate*`
- `PermissionCompat.hasPermission`

Основные единицы расширения:

- `FilterType` (enum) — новый тип фильтра
- `*Config` + `ConfigManager` — новый конфиг-контракт
- `*Command` + `CommandsHandler` — новая подкоманда
- Теги `ActionManager` — новое UX-действие
- `ProfanityEngine` / `LinksManager` / `AdaptiveAdFilter` — логика детекции

---

*Конец AGENTS.md. При конфликте: этот файл + фактический код > догадки. При сомнении — graph-first, reuse-first, style-first.*
