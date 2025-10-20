package com.behamotten.events;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Persistent storage for all event participants.
 */
public final class EventParticipationData {
    private static final String FILE_NAME = "event_participants.yml";
    private static final String SECTION_PLAYERS = "players";

    private final JavaPlugin plugin;
    private final Path dataFile;
    private final Map<UUID, String> participants = new LinkedHashMap<>();
    private boolean dirty;

    private EventParticipationData(final JavaPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = plugin.getDataFolder().toPath().resolve(FILE_NAME);
        load();
    }

    public static EventParticipationData load(final JavaPlugin plugin) {
        return new EventParticipationData(plugin);
    }

    public int getParticipantCount() {
        return participants.size();
    }

    public ParticipationUpdate addParticipant(final Player player) {
        final UUID uuid = player.getUniqueId();
        final String name = player.getName();
        final String previous = participants.put(uuid, name);
        if (previous == null || !previous.equals(name)) {
            final boolean persisted = markDirty();
            return new ParticipationUpdate(previous == null, persisted);
        }
        return new ParticipationUpdate(false, true);
    }

    public ParticipationUpdate removeParticipant(final UUID uuid) {
        final String removed = participants.remove(uuid);
        if (removed != null) {
            final boolean persisted = markDirty();
            return new ParticipationUpdate(true, persisted);
        }
        return new ParticipationUpdate(false, true);
    }

    public boolean isParticipant(final UUID uuid) {
        return participants.containsKey(uuid);
    }

    public List<String> getParticipantNames() {
        return List.copyOf(participants.values());
    }

    public Optional<String> getRandomParticipantName() {
        final List<String> names = new ArrayList<>(participants.values());
        if (names.isEmpty()) {
            return Optional.empty();
        }
        final int index = ThreadLocalRandom.current().nextInt(names.size());
        return Optional.of(names.get(index));
    }

    public boolean save() {
        if (!dirty && Files.exists(dataFile)) {
            return true;
        }

        try {
            final Path parent = dataFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            final YamlConfiguration configuration = new YamlConfiguration();
            final Map<String, Object> serialized = new LinkedHashMap<>();
            for (final Map.Entry<UUID, String> entry : participants.entrySet()) {
                serialized.put(entry.getKey().toString(), entry.getValue());
            }
            configuration.createSection(SECTION_PLAYERS, serialized);
            configuration.save(dataFile.toFile());
            dirty = false;
            return true;
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Konnte Event-Teilnehmer nicht speichern.", exception);
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Unerwarteter Fehler beim Speichern der Event-Teilnehmer.", exception);
        }
        return false;
    }

    private void load() {
        participants.clear();
        if (!Files.exists(dataFile)) {
            dirty = false;
            return;
        }

        final FileConfiguration configuration = YamlConfiguration.loadConfiguration(dataFile.toFile());
        final ConfigurationSection section = configuration.getConfigurationSection(SECTION_PLAYERS);
        if (section != null) {
            for (final String key : section.getKeys(false)) {
                final String name = section.getString(key);
                if (name == null || name.isBlank()) {
                    continue;
                }
                try {
                    final UUID uuid = UUID.fromString(key);
                    participants.put(uuid, name);
                } catch (final IllegalArgumentException exception) {
                    plugin.getLogger().log(Level.WARNING, "Ungültige UUID in der Teilnehmerdatei: " + key, exception);
                }
            }
        }
        dirty = false;
    }

    private boolean markDirty() {
        dirty = true;
        return save();
    }

    /**
     * Result of a participation update operation.
     */
    public record ParticipationUpdate(boolean changed, boolean persisted) {

        public boolean wasChanged() {
            return changed;
        }

        public boolean wasPersisted() {
            return persisted;
        }
    }
}
