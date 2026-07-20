# dev-tests — стенды детекции

Ручные стенды для проверки фильтров на регрессии. Не участвуют в сборке плагина
(лежат вне `src/`, Maven их не компилирует).

Гоняют **боевой код** (`ProfanityEngine`, `LinksManager`, `AdaptiveAdFilter`, `ConfigUpdater`),
а не его копию — поэтому цифры отражают реальное поведение плагина.

## Запуск

Все команды — **из корня репозитория** (пути внутри стендов относительные).

```bash
# 1) собрать плагин и выгрузить classpath зависимостей
mvn -q compile
mvn -q dependency:build-classpath -Dmdep.outputFile=cp.txt

# 2) classpath (Windows: ';' вместо ':')
CP="target/classes;$(cat cp.txt);dev-tests"

# 3) стенды мата (Bukkit не нужен)
javac -encoding UTF-8 -proc:none -cp "$CP" -d dev-tests dev-tests/Corpus.java dev-tests/Bench.java dev-tests/FpHunt.java dev-tests/NameShield.java
java -Dfile.encoding=UTF-8 -cp "$CP" FpHunt
java -Dfile.encoding=UTF-8 -cp "$CP" Bench

# 4) стенды ссылок и миграции (нужен --add-opens)
javac -encoding UTF-8 -proc:none -cp "$CP" -d dev-tests dev-tests/AdBench.java dev-tests/MigrationCheck.java
java -Dfile.encoding=UTF-8 --add-opens jdk.unsupported/sun.misc=ALL-UNNAMED -cp "$CP" AdBench
java -Dfile.encoding=UTF-8 --add-opens jdk.unsupported/sun.misc=ALL-UNNAMED -cp "$CP" MigrationCheck
```

Отчёты пишутся рядом в `dev-tests/` (`fp_report.txt`, `ad_report.txt`, `migration_report.txt`)
в UTF-8 — консоль Windows кириллицу не покажет, читать надо файлы.

## Что проверяет

| Стенд | Что | Ориентир |
|---|---|---|
| `FpHunt` | мат: ~918k сообщений (реальные фразы + сгенерированные последовательности безобидных слов), омографы `шишка`/`щука`, обфускация | 0 сгенерированных FP; 32/32 must-catch; 20/20 омографов чисто |
| `Bench` | мат: precision/recall на корпусе + защита safe-words | FP 0.0%; recall ~93% |
| `NameShield` | защита никнеймов от ложных срабатываний | секции 1 и 3 корректны |
| `AdBench` | ссылки/реклама: обычная речь vs обходы (t.me, discord, домены, пробелы, скобки, гомоглифы) | FP ≤1; recall ≥99% |
| `MigrationCheck` | апгрейд `links.yml` 2.2→2.3: доезжают ли новые TLD | все зоны на месте, пользовательские списки не тронуты |

Корпуса — обычные UTF-8 файлы, по сообщению на строку (`#` — комментарий).
Расширять их при добавлении новых кейсов.

## Известные остатки (осознанные компромиссы)

- `члены` / `хер` — dual-use слова, намеренно оставлены в словаре.
- `just for fun` — англ. идиома на реальной зоне `.fun`; ру-аудитории не мешает.
- `t(точка)me слеш chan` — «слеш» не нормализуется в `/`.
- `хyй` (латинская `y`), `п*зда` — обходы, которые нельзя закрыть без роста FP.

## Осторожно

`AdBench` и `MigrationCheck` поднимают объекты через `sun.misc.Unsafe` + рефлексию,
чтобы обойти конструкторы, требующие Bukkit-сервер. Это **хрупко**: переименование
приватных полей (`settingsRef`, `configManager`, `links`, `promoKeywords`, `states`)
или метода `buildSettings()` / `mergeMissingKeys()` молча сломает стенд.
Если стенд падает после рефакторинга — чинить надо стенд, а не считать это багом плагина.
