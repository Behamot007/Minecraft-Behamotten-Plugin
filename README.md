# Behamotten Event Tools

Ein NeoForge-Mod für Minecraft 1.21.1, der ein einfaches Event-Teilnahmesystem bereitstellt. Spieler können sich selbst für Event-Aktionen anmelden oder wieder austragen, während Administratoren alle registrierten Teilnehmer im Blick behalten.

## Voraussetzungen

- Java 21 (getestet mit 21.0.8)
- Gradle-Wrapper-Skripte (`gradlew` / `gradlew.bat` mit Gradle 8.10.2, enthalten das benötigte Wrapper-JAR)
- Minecraft 1.21.1 Server mit NeoForge 21.1.209 (getestet auf ArcLight 1.0.2-SNAPSHOT-5857740)

## Kompilieren

```bash
./gradlew build
```

Das Skript verwendet das im Repository enthaltene `gradle/wrapper/gradle-wrapper.jar` und lädt automatisch Gradle 8.10.2, bevor es einen normalen Gradle-Build ausführt. Die fertige, serverfähige Mod liegt anschließend unter `build/libs/behamotten-event-tools-<version>.jar` und kann direkt in den `mods/`-Ordner Ihres Servers kopiert werden.

## Entwicklermodus (optional)

Der Build konfiguriert einen NeoForge-`server`-Run. Für lokale Tests kann der Server mit den Entwicklungsdaten gestartet werden:

```bash
./gradlew runServer
```

Der Server verwendet das Verzeichnis `run/server` innerhalb des Projekts. Standardmäßig wird ohne GUI gestartet.

## Verfügbare Befehle

| Befehl | Berechtigung | Beschreibung |
| ------ | ------------ | ------------ |
| `/setEvents` | Alle Spieler | Fügt den ausführenden Spieler zur Eventliste hinzu. |
| `/unsetEvents` | Alle Spieler | Entfernt den ausführenden Spieler aus der Eventliste. |
| `/getAllEventUser` | Operator (Permission Level ≥ 2) | Listet alle registrierten Spieler auf. |
| `/getAllEventUser @r` | Operator (Permission Level ≥ 2) | Gibt einen zufälligen registrierten Spieler zurück. |

Alle Nachrichten werden im Spiel auf Englisch angezeigt (siehe `src/main/resources/assets/behamotten/lang/en_us.json`).

## Datenpersistenz

Die Liste der registrierten Spieler wird serverseitig gespeichert und über Neustarts hinweg beibehalten. Die Daten liegen in den Welt-Speicherdaten (`world/data/behamotten_event_participants.dat`).

## Lizenz

Dieses Projekt verwendet die MIT-Lizenz. Eine Kopie befindet sich in der Mod-Datei (`mods.toml`).
