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

## Datenpersistenz

Die Liste der registrierten Spieler wird im Plugin-Datenordner (`plugins/BehamottenEventTools/event_participants.yml`) gespeichert und über Neustarts hinweg beibehalten.

## Fortschritts-Export (JSON)

Zusätzlich zum Event-Teilnahmestatus exportiert das Plugin strukturierte JSON-Dateien, die sich für externe Auswertungen eignen. Die Dateien werden lokal im Plugin-Datenordner erzeugt – es erfolgt **kein Versand an externe APIs oder Dienste**.

### Speicherort

- **Master-Datei**: `plugins/BehamottenEventTools/progress_master.json`
- **Spielerdateien**: `plugins/BehamottenEventTools/progress_players/<uuid>.json`
- **(Optional) Quest-Definitionen**: `plugins/BehamottenEventTools/ftbquests_definitions.json`

Die Master- und Spielerdateien werden bei Plugin-Start synchronisiert und nach jedem neuen Abschluss gespeichert. Beim Herunterfahren (`onDisable`) wird ein abschließender Schreibvorgang ausgeführt.

### Struktur der Master-Datei (`progress_master.json`)

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
| `generatedAt` | ISO-8601-Zeitstempel | Zeitpunkt, zu dem die Datei zuletzt geschrieben wurde. |
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

### Import von FTB-Quests (`ftbquests_definitions.json`)

Wird im Datenordner eine Datei mit folgender Struktur abgelegt, importiert das Plugin die Quest-Einträge beim Start und fügt sie der Master-Datei hinzu:

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
