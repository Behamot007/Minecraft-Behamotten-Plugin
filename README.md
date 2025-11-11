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
| `/exportadvancements` | `behamotten.export.advancements` (Standard: nur Operatoren) | Erstellt eine konsolidierte JSON-Datei mit allen aktuell bekannten Advancements. |

## Datenpersistenz

Die Liste der registrierten Spieler wird im Plugin-Datenordner (`plugins/BehamottenEventTools/event_participants.yml`) gespeichert und über Neustarts hinweg beibehalten.

## Advancement-Export (JSON)

Mit `/exportadvancements` erzeugt das Plugin eine einzelne Datei `plugins/BehamottenEventTools/advancements_export.json`. Während des Exports erhält der ausführende Spieler automatisch alle bekannten Advancements, damit auch versteckte Einträge zuverlässig aufgelistet werden. Das Ergebnis besteht ausschließlich aus lokal gespeicherten Daten.

### Beispielstruktur

```jsonc
{
  "meta": {
    "generated_at": "2025-11-10T20:19:26.520948Z",
    "advancaments_found": 2636,
    "group_titles_found": 13
  },
  "groups": [
    {
      "id": "minecraft:adventure/root",
      "title": "Adventure"
    }
  ],
  "advancaments": [
    {
      "advancaments_id": "minecraft:adventure/arbalistic",
      "advancaments_title": "Arbalistic",
      "advancaments_description": "Kill five unique mobs with a single crossbow shot.",
      "source_file": "data/minecraft/advancements/adventure/arbalistic.json",
      "dependencies": [
        "minecraft:adventure/root"
      ],
      "group_id": "minecraft:adventure/root"
    }
  ]
}
```

Die Datei folgt diesen Regeln:

- `meta.generated_at` enthält einen ISO-8601-Zeitstempel (UTC).
- `meta.advancaments_found` entspricht der Anzahl aller verarbeiteten Advancements.
- `meta.group_titles_found` gibt die Anzahl der erkannten Obergruppen an. Eine Gruppe entspricht dem Wurzel-Advancement der jeweiligen Reihe.
- `groups` listet alle Gruppen mit ihrer eindeutigen ID (der Schlüssel des Wurzel-Advancements) und dem auf Englisch aufgelösten Titel.
- `advancaments` enthält jedes Advancement mit Kennung, Titel, optionaler Beschreibung, dem vermuteten Quellpfad in den Vanilla-Daten (`data/<namespace>/advancements/<path>.json`), einer Liste von Abhängigkeiten (aktuelles Eltern-Advancement) sowie der zugehörigen Gruppe.

Fehler beim Zugriff auf den Server oder beim Schreiben der Datei werden sowohl im Chat als auch im Server-Log gemeldet.

## Lizenz

Dieses Projekt verwendet die MIT-Lizenz. Eine Kopie befindet sich in der Datei `LICENSE`.
