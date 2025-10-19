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

## Lizenz

Dieses Projekt verwendet die MIT-Lizenz. Eine Kopie befindet sich in der Datei `LICENSE`.
