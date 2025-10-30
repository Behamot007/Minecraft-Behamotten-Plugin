# Behamotten Event Tools

Ein ArcLight-/Paper-kompatibles Bukkit-Plugin für Minecraft 1.21.1, das ein einfaches Event-Teilnahmesystem bereitstellt. Spieler können sich selbst für Event-Aktionen anmelden oder wieder austragen, während Administratoren alle registrierten Teilnehmer im Blick behalten.

## Voraussetzungen

- Java 21 (getestet mit 21.0.8)
- Gradle-Wrapper-Skripte (`gradlew` / `gradlew.bat` mit Gradle 8.10.2, enthalten das benötigte Wrapper-JAR)
- Minecraft 1.21.1 Server mit ArcLight 1.0.2-SNAPSHOT-5857740 oder einem anderen Paper/Purpur/Pufferfish-basierten Server

## Kompilieren

```bash
./gradlew build
```

Das Skript verwendet das im Repository enthaltene `gradle/wrapper/gradle-wrapper.jar` und lädt automatisch Gradle 8.10.2, bevor es einen normalen Gradle-Build ausführt. Die fertige Plugin-JAR liegt anschließend unter `build/libs/behamotten-event-tools-<version>.jar` und kann direkt in den `plugins/`-Ordner Ihres Servers kopiert werden.

## Installation

1. Die generierte JAR-Datei in den `plugins/`-Ordner legen.
2. Server starten oder neu laden (`/reload` wird nicht empfohlen).
3. Beim ersten Start legt das Plugin den Datenordner `plugins/BehamottenEventTools/` sowie die Datei `event_participants.yml` an.

## Verfügbare Befehle

| Befehl | Berechtigung | Beschreibung |
| ------ | ------------ | ------------ |
| `/setevents` | `behamotten.setevents` (Standard: erlaubt) | Fügt den ausführenden Spieler zur Eventliste hinzu. |
| `/unsetevents` | `behamotten.unsetevents` (Standard: erlaubt) | Entfernt den ausführenden Spieler aus der Eventliste. |
| `/getalleventuser` | `behamotten.getall` (Standard: nur Operatoren) | Listet alle registrierten Spieler auf. |
| `/getalleventuser @r` | `behamotten.getall` (Standard: nur Operatoren) | Gibt einen zufälligen registrierten Spieler zurück. |
| `/generatequestmaster` | `behamotten.progress.quest` (Standard: nur Operatoren) | Aktualisiert die Quest-Masterdatei und erstellt optionale Übersetzungen neu. |
| `/generateadvancementmaster` | `behamotten.progress.advancement` (Standard: nur Operatoren) | Aktualisiert die Advancement-Masterdatei sowie die zugehörigen Übersetzungen. |

## Datenpersistenz

Die Liste der registrierten Spieler wird im Plugin-Datenordner (`plugins/BehamottenEventTools/event_participants.yml`) gespeichert und über Neustarts hinweg beibehalten.

## Fortschritts-Export (JSON)

Zusätzlich zum Event-Teilnahmestatus exportiert das Plugin strukturierte JSON-Dateien, die sich für externe Auswertungen eignen. Die Dateien werden lokal im Plugin-Datenordner erzeugt – es erfolgt **kein Versand an externe APIs oder Dienste**.

### Speicherort

- **Advancement-Masterdatei**: `plugins/BehamottenEventTools/progress_master_advancements.json`
- **Quest-Masterdatei**: `plugins/BehamottenEventTools/progress_master_quests.json`
- **Advancement-Übersetzungen**: `plugins/BehamottenEventTools/advancements_translations.json`
- **Quest-Übersetzungen**: `plugins/BehamottenEventTools/ftbquests_translations.json`
- **FTB-Quest-Definitionen**: `plugins/BehamottenEventTools/ftbquests_definitions.json`
- **Spielerdateien**: `plugins/BehamottenEventTools/progress_players/<uuid>.json`
- **Änderungsprotokoll**: `plugins/BehamottenEventTools/progress_player_updates.log`

Die Masterdateien werden **nicht** mehr automatisch beim Serverstart erzeugt. Verwenden Sie stattdessen die Verwaltungsbefehle `/generateadvancementmaster` und `/generatequestmaster`, um die jeweiligen Exporte neu zu schreiben. Beide Befehle melden den Erfolg direkt im Chat und geben bei Problemen eine Fehlermeldung aus. Detaillierte Ursachen (z. B. fehlende Schreibrechte) finden Sie im Server-Log.

Der Quest-Befehl liest die SNBT-Dateien aus `config/ftbquests/quests/`, konvertiert sie in `ftbquests_definitions.json` und erstellt anschließend Master- und Übersetzungsdateien. Der Advancement-Befehl exportiert alle aktuell registrierten Advancements und erzeugt gleichzeitig eine Übersetzungsdatei mit deutschen und englischen Platzhaltern.

Spielerabschlüsse werden weiterhin für **alle** Spieler kontinuierlich aufgezeichnet. Jede Änderung führt zu einer Aktualisierung der jeweiligen Spielerdatei und zu einem neuen Eintrag im Änderungsprotokoll. Beim Herunterfahren (`onDisable`) wird ein abschließender Schreibvorgang für noch offene Spieler durchgeführt.

### Struktur der Master-Dateien (`progress_master_advancements.json` / `progress_master_quests.json`)

```jsonc
{
  "generatedAt": "2024-11-10T17:32:11.235Z",
  "entries": [
    {
      "id": "minecraft:story/root",
      "type": "ADVANCEMENT", // oder "QUEST"
      "name": "Das Abenteuer beginnt",
      "description": "Betritt die Welt",
      "parentId": "minecraft:story/mine_stone",
      "icon": "minecraft:crafting_table",
      "attributes": {
        "announceToChat": true,
        "frame": "TASK",
        "chapter": "Main Quests" // optional, z. B. aus FTB-Quests
      },
      "criteria": [
        "crafted_table"
      ]
    }
  ]
}
```

| Feld | Typ | Beschreibung |
| ---- | --- | ------------- |
| `generatedAt` | ISO-8601-Zeitstempel | Zeitpunkt, zu dem die Datei erstellt wurde. |
| `entries` | Array | Liste aller bekannten Achievements/Quests. |
| `entries[].id` | String | Eindeutige Kennung (`namespace:path` für Advancements, frei wählbar für Quests). |
| `entries[].type` | String | `ADVANCEMENT` oder `QUEST`. |
| `entries[].name` | String, optional | Titel des Eintrags. |
| `entries[].description` | String, optional | Beschreibung des Eintrags. |
| `entries[].parentId` | String, optional | Übergeordnete Achievement-ID (falls vorhanden). |
| `entries[].icon` | String, optional | Zeichenkettenrepräsentation des Icons (Item-Namensraum). |
| `entries[].attributes` | Objekt, optional | Zusätzliche Merkmale (Anzeigeoptionen, Quest-Metadaten usw.). |
| `entries[].criteria` | Array<String> | Liste aller Kriterien/Tasks, die für diesen Eintrag existieren. |

### Struktur einer Spielerdatei (`progress_players/<uuid>.json`)

```jsonc
{
  "playerId": "c0ffee00-4b1d-4ead-babe-001122334455",
  "lastKnownName": "Spieler123", // optional
  "exportedAt": "2024-11-10T17:33:02.017Z",
  "completions": [
    {
      "entryId": "minecraft:story/root",
      "type": "ADVANCEMENT",
      "completedAt": "2024-11-02T19:45:12.901Z",
      "completedCriteria": [
        "crafted_table"
      ],
      "details": {
        "source": "advancement",
        "world": "world"
      }
    }
  ]
}
```

| Feld | Typ | Beschreibung |
| ---- | --- | ------------- |
| `playerId` | UUID als String | UUID des Spielers. |
| `lastKnownName` | String, optional | Letzter bekannter Spielername. |
| `exportedAt` | ISO-8601-Zeitstempel | Zeitpunkt des Dateiexports. |
| `completions` | Array | Liste aller abgeschlossenen Einträge. |
| `completions[].entryId` | String | Fremdschlüssel auf `entries[].id` der Master-Datei. |
| `completions[].type` | String | `ADVANCEMENT` oder `QUEST`. |
| `completions[].completedAt` | ISO-8601-Zeitstempel | Abschlusszeitpunkt. |
| `completions[].completedCriteria` | Array<String>, optional | Erfüllte Kriterien/Tasks. |
| `completions[].details` | Objekt, optional | Zusätzliche Metadaten (z. B. `source`, Weltname, Belohnungsinformationen). |

### Struktur der Übersetzungsdateien (`advancements_translations.json` / `ftbquests_translations.json`)

```jsonc
{
  "generatedAt": "2024-11-10T17:32:11.235Z",
  "source": "advancements",
  "entryCount": 2,
  "entries": [
    {
      "id": "minecraft:story/root",
      "field": "name",
      "de": "Das Abenteuer beginnt",
      "en": "The Adventure Begins"
    },
    {
      "id": "minecraft:story/root",
      "field": "description",
      "de": "Betritt die Welt",
      "en": "Enter the world"
    }
  ]
}
```

Jeder Eintrag enthält die referenzierte ID, das Feld (`name` oder `description`) sowie deutsche und englische Übersetzungen. Das Quest-Äquivalent folgt der gleichen Struktur.

### Struktur des Änderungsprotokolls (`progress_player_updates.log`)

Das Änderungsprotokoll ist eine einfache JSON-Lines-Datei. Für jede gespeicherte Änderung wird eine Zeile mit folgenden Feldern angefügt:

```json
{"playerId":"c0ffee00-4b1d-4ead-babe-001122334455","updatedAt":"2024-11-10T17:33:02.017Z","lastKnownName":"Spieler123"}
```

- `playerId`: UUID des Spielers.
- `updatedAt`: Zeitpunkt, zu dem die zugehörige Spielerdatei zuletzt geschrieben wurde.
- `lastKnownName`: Optional, wenn ein Spielername bekannt ist.

Über dieses Log lässt sich von extern leicht erkennen, welche Spieler-Dateien seit dem letzten Abgleich verändert wurden.

### Import von FTB-Quests (`ftbquests_definitions.json`)

Beim Ausführen von `/generatequestmaster` erzeugt das Plugin – falls noch nicht vorhanden – eine Definitionsdatei mit folgender Struktur (alternativ kann eine manuell gepflegte Datei im gleichen Format verwendet werden):

```jsonc
{
  "quests": [
    {
      "id": "ftbquests:chapter1/quest3",
      "name": "Technik-Stufe 1",
      "description": "Schalte den Ofen frei",
      "chapter": "Kapitel 1",
      "icon": "minecraft:blast_furnace",
      "attributes": {
        "difficulty": "EASY"
      },
      "criteria": ["craft_blast_furnace"],
      "tags": ["tech", "starter"]
    }
  ]
}
```

Alle Felder sind optional, mit Ausnahme der Quest-`id`. Attribute und Tags werden unverändert in die Master-Datei übernommen. Die Zuordnung zu Spielerabschlüssen erfolgt ausschließlich über die `entryId` (Quest-ID).

## Lizenz

Dieses Projekt verwendet die MIT-Lizenz. Eine Kopie befindet sich in der Datei `LICENSE`.
