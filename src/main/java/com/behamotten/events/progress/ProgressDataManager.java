package com.behamotten.events.progress;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Centralizes the export of advancement and quest data into structured JSON files.
 */
public final class ProgressDataManager {
    private static final String MASTER_FILE_NAME = "progress_master.json";
    private static final String PLAYERS_DIRECTORY_NAME = "progress_players";
    private static final String QUEST_DEFINITIONS_FILE_NAME = "ftbquests_definitions.json";
    private static final String PLAYER_UPDATE_LOG_FILE_NAME = "progress_player_updates.log";

    private final JavaPlugin plugin;
    private final Path masterFile;
    private final Path playersDirectory;
    private final Path questDefinitionsFile;
    private final Path playerUpdateLogFile;

    private final Map<String, MasterEntry> masterEntries = new LinkedHashMap<>();
    private final Map<UUID, PlayerProgress> playerProgress = new LinkedHashMap<>();
    private final Set<UUID> dirtyPlayers = new HashSet<>();
    private boolean masterDirty;
    private boolean masterFileLocked;

    private ProgressDataManager(final JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        final Path dataFolder = plugin.getDataFolder().toPath();
        this.masterFile = dataFolder.resolve(MASTER_FILE_NAME);
        this.playersDirectory = dataFolder.resolve(PLAYERS_DIRECTORY_NAME);
        this.questDefinitionsFile = dataFolder.resolve(QUEST_DEFINITIONS_FILE_NAME);
        this.playerUpdateLogFile = dataFolder.resolve(PLAYER_UPDATE_LOG_FILE_NAME);
        this.masterFileLocked = Files.exists(masterFile);
        loadMasterFile();
    }

    public static ProgressDataManager load(final JavaPlugin plugin) {
        return new ProgressDataManager(plugin);
    }

    public int getMasterEntryCount() {
        return masterEntries.size();
    }

    public Path getQuestDefinitionsFile() {
        return questDefinitionsFile;
    }

    public int synchronizeAdvancements(final Iterator<Advancement> advancements) {
        if (advancements == null) {
            return 0;
        }
        int processed = 0;
        while (advancements.hasNext()) {
            final Advancement advancement = advancements.next();
            if (advancement == null) {
                continue;
            }
            if (createEntryFromAdvancement(advancement) != null) {
                processed++;
            }
        }
        return processed;
    }

    public MasterEntry registerAdvancement(final Advancement advancement) {
        final MasterEntry entry = createEntryFromAdvancement(advancement);
        if (entry == null) {
            return null;
        }
        return upsertMasterEntry(entry);
    }

    public MasterEntry registerQuest(final QuestDefinition questDefinition) {
        if (questDefinition == null) {
            return null;
        }
        final MasterEntry entry = questDefinition.normalize().toMasterEntry();
        return upsertMasterEntry(entry);
    }

    public int importQuestDefinitions() {
        return importQuestDefinitions(questDefinitionsFile);
    }

    public int importQuestDefinitions(final Path definitionFile) {
        if (definitionFile == null || !Files.exists(definitionFile)) {
            return 0;
        }
        try (Reader reader = Files.newBufferedReader(definitionFile, StandardCharsets.UTF_8)) {
            final StringBuilder builder = new StringBuilder();
            final char[] buffer = new char[2048];
            int read;
            while ((read = reader.read(buffer)) != -1) {
                builder.append(buffer, 0, read);
            }
            final Object parsed = SimpleJson.parse(builder.toString());
            if (!(parsed instanceof Map<?, ?>)) {
                return 0;
            }
            final Map<?, ?> root = (Map<?, ?>) parsed;
            final Object questsObj = root.get("quests");
            if (!(questsObj instanceof List<?>)) {
                return 0;
            }
            final List<?> questList = (List<?>) questsObj;
            int imported = 0;
            for (final Object questElement : questList) {
                final QuestDefinition quest = parseQuestDefinition(questElement);
                if (quest != null) {
                    if (registerQuest(quest) != null) {
                        imported++;
                    }
                }
            }
            return imported;
        } catch (final IOException | SimpleJson.JsonException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Konnte Quest-Definitionen nicht laden: " + definitionFile, exception);
            return 0;
        }
    }

    public void finalizeMasterExport() {
        saveMasterIfDirty();
    }

    public void recordAdvancementCompletion(final Player player, final Advancement advancement) {
        if (player == null || advancement == null) {
            return;
        }
        final MasterEntry entry = registerAdvancement(advancement);
        if (entry == null) {
            return;
        }
        final PlayerProgress progress = getOrCreatePlayerProgress(player.getUniqueId(), player.getName());
        final Map<String, String> details = Map.of("source", "advancement");
        final boolean updated = progress.recordCompletion(entry, Instant.now(), entry.getCriteria(), details);
        if (updated) {
            dirtyPlayers.add(player.getUniqueId());
            savePlayerProgress(player.getUniqueId(), progress);
        }
    }

    public void recordQuestCompletion(final UUID playerId, final String playerName,
            final QuestDefinition questDefinition, final Instant completionTime,
            final Collection<String> completedCriteria, final Map<String, String> details) {
        if (playerId == null || questDefinition == null || completionTime == null) {
            return;
        }
        final MasterEntry entry = registerQuest(questDefinition);
        if (entry == null) {
            return;
        }
        final PlayerProgress progress = getOrCreatePlayerProgress(playerId, playerName);
        final Map<String, String> completionDetails = new LinkedHashMap<>();
        if (details != null) {
            completionDetails.putAll(details);
        }
        completionDetails.putIfAbsent("source", "quest");
        final boolean updated = progress.recordCompletion(entry, completionTime, completedCriteria, completionDetails);
        if (updated) {
            dirtyPlayers.add(playerId);
            savePlayerProgress(playerId, progress);
        }
    }

    public void saveAll() {
        saveMasterIfDirty();
        if (dirtyPlayers.isEmpty()) {
            return;
        }
        final Set<UUID> pending = new HashSet<>(dirtyPlayers);
        for (final UUID playerId : pending) {
            final PlayerProgress progress = playerProgress.get(playerId);
            if (progress != null) {
                savePlayerProgress(playerId, progress);
            } else {
                dirtyPlayers.remove(playerId);
            }
        }
    }

    private void loadMasterFile() {
        masterEntries.clear();
        if (!Files.exists(masterFile)) {
            masterDirty = true;
            return;
        }
        try {
            final String content = Files.readString(masterFile, StandardCharsets.UTF_8);
            final Object parsed = SimpleJson.parse(content);
            if (parsed instanceof Map<?, ?>) {
                final Map<?, ?> root = (Map<?, ?>) parsed;
                final Object entriesObj = root.get("entries");
                if (entriesObj instanceof List<?>) {
                    final List<?> list = (List<?>) entriesObj;
                    for (final Object element : list) {
                        final MasterEntry entry = parseMasterEntry(element);
                        if (entry != null) {
                            masterEntries.put(entry.getId(), entry);
                        }
                    }
                }
            }
            masterDirty = false;
        } catch (final IOException | SimpleJson.JsonException exception) {
            plugin.getLogger().log(Level.SEVERE, "Konnte Master-Datei nicht laden.", exception);
            masterDirty = true;
        }
    }

    private PlayerProgress getOrCreatePlayerProgress(final UUID playerId, final String playerName) {
        PlayerProgress progress = playerProgress.get(playerId);
        if (progress == null) {
            progress = loadPlayerProgress(playerId);
            playerProgress.put(playerId, progress);
        }
        if (progress.updateLastKnownName(playerName)) {
            dirtyPlayers.add(playerId);
        }
        return progress;
    }

    private PlayerProgress loadPlayerProgress(final UUID playerId) {
        final Path file = playersDirectory.resolve(playerId.toString() + ".json");
        if (!Files.exists(file)) {
            return new PlayerProgress(playerId, null, new LinkedHashMap<>()).normalize();
        }
        try {
            final String content = Files.readString(file, StandardCharsets.UTF_8);
            final Object parsed = SimpleJson.parse(content);
            if (!(parsed instanceof Map<?, ?>)) {
                return new PlayerProgress(playerId, null, new LinkedHashMap<>()).normalize();
            }
            final Map<?, ?> root = (Map<?, ?>) parsed;
            final String lastKnownName = asString(root.get("lastKnownName"));
            final Map<String, CompletionRecord> completions = new LinkedHashMap<>();
            final Object completionsObj = root.get("completions");
            if (completionsObj instanceof List<?>) {
                final List<?> list = (List<?>) completionsObj;
                for (final Object element : list) {
                    final CompletionRecord record = parseCompletionRecord(element);
                    if (record != null) {
                        completions.put(record.getEntryId(), record);
                    }
                }
            }
            return new PlayerProgress(playerId, lastKnownName, completions).normalize();
        } catch (final IOException | SimpleJson.JsonException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Konnte Spielerfortschritt nicht laden: " + playerId, exception);
            return new PlayerProgress(playerId, null, new LinkedHashMap<>()).normalize();
        }
    }

    private MasterEntry upsertMasterEntry(final MasterEntry entry) {
        if (entry == null || entry.getId() == null) {
            return null;
        }
        final MasterEntry normalized = entry.normalize();
        final MasterEntry existing = masterEntries.get(normalized.getId());
        if (masterFileLocked) {
            if (existing == null) {
                plugin.getLogger().log(Level.WARNING,
                        "Neuer Master-Eintrag '" + normalized.getId()
                                + "' wurde ignoriert, da die Master-Datei bereits fixiert ist.");
                return null;
            }
            return existing;
        }
        if (!normalized.equals(existing)) {
            masterEntries.put(normalized.getId(), normalized);
            masterDirty = true;
        }
        return masterEntries.get(normalized.getId());
    }

    private MasterEntry parseMasterEntry(final Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        final Map<?, ?> map = (Map<?, ?>) value;
        final String id = asString(map.get("id"));
        if (id == null) {
            return null;
        }
        final MasterEntry.EntryType type = parseEntryType(asString(map.get("type")));
        final String name = asString(map.get("name"));
        final String description = asString(map.get("description"));
        final String parentId = asString(map.get("parentId"));
        final String icon = asString(map.get("icon"));
        final Map<String, Object> attributes = toAttributeMap(map.get("attributes"));
        final List<String> criteria = toStringList(map.get("criteria"));
        return new MasterEntry(id, type != null ? type : MasterEntry.EntryType.ADVANCEMENT, name, description,
                parentId, icon, attributes, criteria);
    }

    private CompletionRecord parseCompletionRecord(final Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        final Map<?, ?> map = (Map<?, ?>) value;
        final String entryId = asString(map.get("entryId"));
        if (entryId == null) {
            return null;
        }
        final MasterEntry.EntryType type = parseEntryType(asString(map.get("type")));
        final Instant completedAt = parseInstant(asString(map.get("completedAt")));
        if (type == null || completedAt == null) {
            return null;
        }
        final List<String> completedCriteria = toStringList(map.get("completedCriteria"));
        final Map<String, String> details = toStringMap(map.get("details"));
        return new CompletionRecord(entryId, type, completedAt, completedCriteria, details);
    }

    private QuestDefinition parseQuestDefinition(final Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return null;
        }
        final Map<?, ?> map = (Map<?, ?>) value;
        final String id = asString(map.get("id"));
        if (id == null) {
            return null;
        }
        final String name = asString(map.get("name"));
        final String description = asString(map.get("description"));
        final String chapter = asString(map.get("chapter"));
        final String icon = asString(map.get("icon"));
        final Map<String, String> attributes = toStringMap(map.get("attributes"));
        final List<String> criteria = toStringList(map.get("criteria"));
        final List<String> tags = toStringList(map.get("tags"));
        return new QuestDefinition(id, name, description, chapter, icon, attributes, criteria, tags);
    }

    private MasterEntry createEntryFromAdvancement(final Advancement advancement) {
        if (advancement == null) {
            return null;
        }
        final NamespacedKey key = advancement.getKey();
        final String id = key != null ? key.toString() : null;
        if (id == null) {
            return null;
        }
        final AdvancementDisplay display = advancement.getDisplay();
        final String title = display != null ? componentToPlain(display.title()) : id;
        final String description = display != null ? componentToPlain(display.description()) : null;
        final String parentId = advancement.getParent() != null && advancement.getParent().getKey() != null
                ? advancement.getParent().getKey().toString() : null;
        final String icon = display != null ? stringifyItem(display.icon()) : null;
        final Map<String, Object> attributes = new LinkedHashMap<>();
        if (display != null) {
            attributes.put("announceToChat", display.doesAnnounceToChat());
            attributes.put("showToast", display.doesShowToast());
            attributes.put("hidden", display.isHidden());
            final NamespacedKey background = display.background();
            if (background != null) {
                attributes.put("background", background.toString());
            }
            if (display.frame() != null) {
                attributes.put("frame", display.frame().name());
            }
        }
        final List<String> criteria = toSortedList(safeCriteria(advancement));
        return new MasterEntry(id, MasterEntry.EntryType.ADVANCEMENT, title, description, parentId, icon,
                sanitizeAttributes(attributes), criteria);
    }

    private Collection<String> safeCriteria(final Advancement advancement) {
        try {
            final Collection<String> criteria = advancement.getCriteria();
            return criteria != null ? criteria : Collections.emptyList();
        } catch (final Throwable throwable) {
            return Collections.emptyList();
        }
    }

    private List<String> toSortedList(final Collection<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        final List<String> list = new ArrayList<>(values);
        list.removeIf(Objects::isNull);
        Collections.sort(list);
        return list;
    }

    private Map<String, Object> sanitizeAttributes(final Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return Map.of();
        }
        final Map<String, Object> sanitized = new LinkedHashMap<>();
        for (final Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getKey() == null || entry.getKey().isBlank()) {
                continue;
            }
            final Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            if (value instanceof Collection<?>) {
                sanitized.put(entry.getKey(), new ArrayList<>((Collection<?>) value));
            } else {
                sanitized.put(entry.getKey(), value);
            }
        }
        return sanitized;
    }

    private String componentToPlain(final Component component) {
        if (component == null) {
            return null;
        }
        try {
            return PlainTextComponentSerializer.plainText().serialize(component);
        } catch (final Throwable throwable) {
            return component.toString();
        }
    }

    private String stringifyItem(final ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        final Object type = itemStack.getType();
        return type != null ? type.toString() : null;
    }

    private void saveMasterIfDirty() {
        if (masterFileLocked || !masterDirty) {
            masterDirty = false;
            return;
        }
        try {
            ensureDataFolders();
            final Map<String, Object> root = new LinkedHashMap<>();
            root.put("generatedAt", Instant.now().toString());
            final List<Map<String, Object>> entries = new ArrayList<>();
            for (final MasterEntry entry : masterEntries.values()) {
                entries.add(toMasterMap(entry));
            }
            root.put("entries", entries);
            final String json = SimpleJson.stringify(root);
            try (Writer writer = Files.newBufferedWriter(masterFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                writer.write(json);
                writer.write(System.lineSeparator());
            }
            masterDirty = false;
            masterFileLocked = true;
        } catch (final IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Konnte Master-Datei nicht speichern.", exception);
        }
    }

    private void savePlayerProgress(final UUID playerId, final PlayerProgress progress) {
        try {
            ensureDataFolders();
            final Instant exportedAt = Instant.now();
            final Map<String, Object> root = new LinkedHashMap<>();
            root.put("playerId", playerId.toString());
            if (progress.getLastKnownName() != null) {
                root.put("lastKnownName", progress.getLastKnownName());
            }
            root.put("exportedAt", exportedAt.toString());
            final List<Map<String, Object>> completions = new ArrayList<>();
            for (final CompletionRecord record : progress.getCompletions()) {
                completions.add(toCompletionMap(record));
            }
            root.put("completions", completions);
            final String json = SimpleJson.stringify(root);
            final Path file = playersDirectory.resolve(playerId.toString() + ".json");
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                writer.write(json);
                writer.write(System.lineSeparator());
            }
            dirtyPlayers.remove(playerId);
            appendPlayerUpdateLog(playerId, exportedAt, progress.getLastKnownName());
        } catch (final IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Konnte Spielerfortschritt nicht speichern: " + playerId, exception);
        }
    }

    private void appendPlayerUpdateLog(final UUID playerId, final Instant exportedAt, final String playerName)
            throws IOException {
        final Map<String, Object> logEntry = new LinkedHashMap<>();
        logEntry.put("playerId", playerId.toString());
        logEntry.put("updatedAt", exportedAt.toString());
        if (playerName != null && !playerName.isBlank()) {
            logEntry.put("lastKnownName", playerName);
        }
        final String serialized = SimpleJson.stringify(logEntry) + System.lineSeparator();
        Files.writeString(playerUpdateLogFile, serialized, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    private Map<String, Object> toMasterMap(final MasterEntry entry) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entry.getId());
        map.put("type", entry.getType().name());
        if (entry.getName() != null) {
            map.put("name", entry.getName());
        }
        if (entry.getDescription() != null) {
            map.put("description", entry.getDescription());
        }
        if (entry.getParentId() != null) {
            map.put("parentId", entry.getParentId());
        }
        if (entry.getIcon() != null) {
            map.put("icon", entry.getIcon());
        }
        if (!entry.getAttributes().isEmpty()) {
            map.put("attributes", new LinkedHashMap<>(entry.getAttributes()));
        }
        if (!entry.getCriteria().isEmpty()) {
            map.put("criteria", new ArrayList<>(entry.getCriteria()));
        }
        return map;
    }

    private Map<String, Object> toCompletionMap(final CompletionRecord record) {
        final Map<String, Object> map = new LinkedHashMap<>();
        map.put("entryId", record.getEntryId());
        map.put("type", record.getType().name());
        map.put("completedAt", record.getCompletedAt().toString());
        if (!record.getCompletedCriteria().isEmpty()) {
            map.put("completedCriteria", new ArrayList<>(record.getCompletedCriteria()));
        }
        if (!record.getDetails().isEmpty()) {
            map.put("details", new LinkedHashMap<>(record.getDetails()));
        }
        return map;
    }

    private MasterEntry.EntryType parseEntryType(final String value) {
        if (value == null) {
            return null;
        }
        try {
            return MasterEntry.EntryType.valueOf(value.trim().toUpperCase());
        } catch (final IllegalArgumentException exception) {
            return null;
        }
    }

    private Instant parseInstant(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (final DateTimeParseException exception) {
            return null;
        }
    }

    private String asString(final Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return (String) value;
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        return null;
    }

    private Map<String, Object> toAttributeMap(final Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return new LinkedHashMap<>();
        }
        final Map<?, ?> map = (Map<?, ?>) value;
        final Map<String, Object> result = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            result.put(entry.getKey().toString(), entry.getValue());
        }
        return sanitizeAttributes(result);
    }

    private Map<String, String> toStringMap(final Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return new LinkedHashMap<>();
        }
        final Map<?, ?> map = (Map<?, ?>) value;
        final Map<String, String> result = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> entry : map.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) {
                continue;
            }
            result.put(entry.getKey().toString(), asString(entry.getValue()));
        }
        return result;
    }

    private List<String> toStringList(final Object value) {
        if (!(value instanceof Collection<?>)) {
            return new ArrayList<>();
        }
        final Collection<?> collection = (Collection<?>) value;
        final List<String> result = new ArrayList<>();
        for (final Object element : collection) {
            final String stringValue = asString(element);
            if (stringValue != null) {
                result.add(stringValue);
            }
        }
        return result;
    }

    private void ensureDataFolders() throws IOException {
        final Path parent = masterFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.createDirectories(playersDirectory);
    }
}
