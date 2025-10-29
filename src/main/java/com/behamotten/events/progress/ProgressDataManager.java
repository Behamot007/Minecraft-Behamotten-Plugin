package com.behamotten.events.progress;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Method;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
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
    private static final String ADVANCEMENT_MASTER_FILE_NAME = "progress_master_advancements.json";
    private static final String QUEST_MASTER_FILE_NAME = "progress_master_quests.json";
    private static final String LEGACY_MASTER_FILE_NAME = "progress_master.json";
    private static final String PLAYERS_DIRECTORY_NAME = "progress_players";
    private static final String QUEST_DEFINITIONS_FILE_NAME = "ftbquests_definitions.json";
    private static final String PLAYER_UPDATE_LOG_FILE_NAME = "progress_player_updates.log";

    private final JavaPlugin plugin;
    private final Path legacyMasterFile;
    private final Path playersDirectory;
    private final Path questDefinitionsFile;
    private final Path playerUpdateLogFile;
    private final MasterFileContext advancementMaster;
    private final MasterFileContext questMaster;
    private final Map<UUID, PlayerProgress> playerProgress = new LinkedHashMap<>();
    private final Set<UUID> dirtyPlayers = new HashSet<>();

    private ProgressDataManager(final JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        final Path dataFolder = plugin.getDataFolder().toPath();
        this.legacyMasterFile = dataFolder.resolve(LEGACY_MASTER_FILE_NAME);
        this.playersDirectory = dataFolder.resolve(PLAYERS_DIRECTORY_NAME);
        this.questDefinitionsFile = dataFolder.resolve(QUEST_DEFINITIONS_FILE_NAME);
        this.playerUpdateLogFile = dataFolder.resolve(PLAYER_UPDATE_LOG_FILE_NAME);
        this.advancementMaster = new MasterFileContext(
                dataFolder.resolve(ADVANCEMENT_MASTER_FILE_NAME), MasterEntry.EntryType.ADVANCEMENT);
        this.questMaster = new MasterFileContext(
                dataFolder.resolve(QUEST_MASTER_FILE_NAME), MasterEntry.EntryType.QUEST);
        loadMasterFile(advancementMaster);
        loadMasterFile(questMaster);
    }

    public static ProgressDataManager load(final JavaPlugin plugin) {
        return new ProgressDataManager(plugin);
    }

    public int getMasterEntryCount() {
        return advancementMaster.entries.size() + questMaster.entries.size();
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
        return upsertMasterEntry(advancementMaster, entry);
    }

    public MasterEntry registerQuest(final QuestDefinition questDefinition) {
        if (questDefinition == null) {
            return null;
        }
        final MasterEntry entry = questDefinition.normalize().toMasterEntry();
        return upsertMasterEntry(questMaster, entry);
    }

    public int importQuestDefinitions() {
        return importQuestDefinitions(questDefinitionsFile);
    }

    public int importQuestDefinitions(final Path definitionFile) {
        if (definitionFile == null || !Files.exists(definitionFile)) {
            plugin.getLogger().severe(() -> "Quest-Definitionen nicht gefunden: " + definitionFile);
            markMasterInitializationRequired(questMaster, "Quest-Definitionen fehlen");
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
                plugin.getLogger().severe(() -> "Unerwartetes Format der Quest-Definitionen: " + definitionFile);
                markMasterInitializationRequired(questMaster, "Ungültiges Format");
                return 0;
            }
            final Map<?, ?> root = (Map<?, ?>) parsed;
            final Object questsObj = root.get("quests");
            if (!(questsObj instanceof List<?>)) {
                plugin.getLogger().severe(() -> "Quest-Liste fehlt im Definitionsdokument: " + definitionFile);
                markMasterInitializationRequired(questMaster, "Quest-Liste fehlt");
                return 0;
            }
            final List<?> questList = (List<?>) questsObj;
            if (questList.isEmpty()) {
                plugin.getLogger().warning(
                        () -> "Quest-Definitionen enthalten keine Quests und werden neu initialisiert: " + definitionFile);
                markMasterInitializationRequired(questMaster, "Keine Quests gefunden");
            }
            int imported = 0;
            for (final Object questElement : questList) {
                final QuestDefinition quest = parseQuestDefinition(questElement);
                if (quest != null) {
                    if (registerQuest(quest) != null) {
                        imported++;
                    }
                } else {
                    plugin.getLogger().warning(
                            () -> "Quest-Definition konnte nicht verarbeitet werden und wurde ignoriert: "
                                    + String.valueOf(questElement));
                }
            }
            final int totalImported = imported;
            plugin.getLogger().info(() -> "Quest-Definitionen importiert: " + totalImported + " aus " + definitionFile);
            if (totalImported == 0) {
                plugin.getLogger().warning(
                        () -> "Es wurden keine Quest-Definitionen importiert. Bitte prüfen Sie die Quelldatei: "
                                + definitionFile);
            }
            return totalImported;
        } catch (final IOException | SimpleJson.JsonException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Konnte Quest-Definitionen nicht laden: " + definitionFile, exception);
            markMasterInitializationRequired(questMaster, "Fehler beim Laden der Quest-Definitionen");
            return 0;
        }
    }

    public MasterRefreshResult refreshMasterExports(final Iterator<Advancement> advancements) {
        plugin.getLogger().info("Starte Neuinitialisierung der Fortschritts-Masterdateien.");
        deleteLegacyMasterFile();
        resetMaster(advancementMaster, "Neuinitialisierung angefordert");
        resetMaster(questMaster, "Neuinitialisierung angefordert");
        deleteMasterFile(advancementMaster);
        deleteMasterFile(questMaster);
        final int synchronizedAdvancements = synchronizeAdvancements(advancements);
        final int importedQuests = importQuestDefinitions();
        finalizeMasterExport();
        plugin.getLogger().info(() -> "Neuinitialisierung abgeschlossen. Advancements: " + synchronizedAdvancements
                + ", Quests: " + importedQuests);
        return new MasterRefreshResult(synchronizedAdvancements, importedQuests);
    }

    public void finalizeMasterExport() {
        saveMasterIfDirty(advancementMaster);
        saveMasterIfDirty(questMaster);
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
        finalizeMasterExport();
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

    private void loadMasterFile(final MasterFileContext context) {
        context.entries.clear();
        final Path file = context.file;
        plugin.getLogger().info(() -> "Lade " + context.entryType + "-Master-Datei: " + file);
        if (!Files.exists(file)) {
            plugin.getLogger().warning(() -> context.entryType + "-Master-Datei nicht gefunden, starte mit leerer Sammlung: "
                    + file);
            markMasterInitializationRequired(context, "Datei nicht vorhanden");
            return;
        }
        try {
            final String content = Files.readString(file, StandardCharsets.UTF_8);
            final Object parsed = SimpleJson.parse(content);
            if (parsed instanceof Map<?, ?>) {
                final Map<?, ?> root = (Map<?, ?>) parsed;
                final Object entriesObj = root.get("entries");
                if (entriesObj instanceof List<?>) {
                    final List<?> list = (List<?>) entriesObj;
                    for (final Object element : list) {
                        final MasterEntry entry = parseMasterEntry(element);
                        if (entry != null) {
                            if (entry.getType() != context.entryType) {
                                plugin.getLogger().warning(() -> "Eintrag '" + entry.getId()
                                        + "' besitzt den Typ " + entry.getType()
                                        + " und passt nicht zur Master-Datei. Der Eintrag wird ignoriert.");
                                continue;
                            }
                            context.entries.put(entry.getId(), entry);
                        } else {
                            plugin.getLogger().warning(() -> "Eintrag in " + context.entryType
                                    + "-Master-Datei konnte nicht gelesen werden und wurde ignoriert.");
                        }
                    }
                }
            }
            if (context.entries.isEmpty()) {
                plugin.getLogger().warning(() -> context.entryType
                        + "-Master-Datei enthält keine Einträge und wird neu erstellt: " + file);
                markMasterInitializationRequired(context, "Keine Einträge vorhanden");
                return;
            }
            plugin.getLogger().info(
                    () -> "Geladene " + context.entryType + "-Master-Einträge: " + context.entries.size());
            context.dirty = false;
            context.locked = false;
            context.initializationReason = null;
        } catch (final IOException | SimpleJson.JsonException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Konnte " + context.entryType + "-Master-Datei nicht laden: " + file, exception);
            markMasterInitializationRequired(context, "Lese-/Parserfehler");
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

    private MasterEntry upsertMasterEntry(final MasterFileContext context, final MasterEntry entry) {
        if (entry == null || entry.getId() == null) {
            plugin.getLogger().warning("Ungültiger Master-Eintrag ohne ID wurde ignoriert.");
            return null;
        }
        if (entry.getType() != context.entryType) {
            plugin.getLogger().severe(() -> "Eintrag '" + entry.getId() + "' besitzt den Typ " + entry.getType()
                    + " und kann nicht in " + context.entryType + "-Master-Datei geschrieben werden.");
            return null;
        }
        final MasterEntry normalized = entry.normalize();
        final MasterEntry existing = context.entries.get(normalized.getId());
        if (!normalized.equals(existing)) {
            context.entries.put(normalized.getId(), normalized);
            if (context.locked) {
                plugin.getLogger().info(() -> context.entryType
                        + "-Master-Datei wird für Aktualisierungen freigegeben (neuer Eintrag: "
                        + normalized.getId() + ")");
                context.locked = false;
                context.initializationReason = null;
            }
            context.dirty = true;
            plugin.getLogger().info(() -> context.entryType + "-Master-Eintrag aktualisiert: " + normalized.getId());
        }
        return context.entries.get(normalized.getId());
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
        final Object titleComponent = resolveDisplayComponent(display, "title", "getTitle");
        final Object descriptionComponent = resolveDisplayComponent(display, "description", "getDescription");
        final String title = titleComponent != null ? componentToPlain(titleComponent) : id;
        final String description = descriptionComponent != null ? componentToPlain(descriptionComponent) : null;
        final String parentId = resolveParentId(advancement);
        final ItemStack iconStack = resolveDisplayValue(display, ItemStack.class, "icon", "getIcon");
        final String icon = iconStack != null ? stringifyItem(iconStack) : null;
        final Map<String, Object> attributes = new LinkedHashMap<>();
        if (display != null) {
            final Boolean announceToChat = resolveDisplayValue(display, Boolean.class,
                    "doesAnnounceToChat", "shouldAnnounceToChat");
            final Boolean showToast = resolveDisplayValue(display, Boolean.class,
                    "doesShowToast", "shouldShowToast");
            final Boolean hidden = resolveDisplayValue(display, Boolean.class, "isHidden");
            attributes.put("announceToChat", Boolean.TRUE.equals(announceToChat));
            attributes.put("showToast", Boolean.TRUE.equals(showToast));
            attributes.put("hidden", Boolean.TRUE.equals(hidden));
            final NamespacedKey background = resolveDisplayValue(display, NamespacedKey.class,
                    "background", "getBackground");
            if (background != null) {
                attributes.put("background", background.toString());
            }
            final Object frame = resolveDisplayValue(display, Object.class, "frame", "getFrame");
            if (frame instanceof Enum<?>) {
                attributes.put("frame", ((Enum<?>) frame).name());
            } else if (frame != null) {
                attributes.put("frame", frame.toString());
            }
        }
        final List<String> criteria = toSortedList(safeCriteria(advancement));
        return new MasterEntry(id, MasterEntry.EntryType.ADVANCEMENT, title, description, parentId, icon,
                sanitizeAttributes(attributes), criteria);
    }

    private String resolveParentId(final Advancement advancement) {
        if (advancement == null) {
            return null;
        }
        final Object parentCandidate = invokeZeroArgumentMethod(advancement,
                "getParent", "parent", "getParentKey", "parentKey", "getParentNamespacedKey");
        return extractParentId(parentCandidate);
    }

    private String extractParentId(final Object candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate instanceof Optional<?>) {
            final Optional<?> optional = (Optional<?>) candidate;
            return optional.map(this::extractParentId).orElse(null);
        }
        if (candidate instanceof Advancement) {
            final Advancement parentAdvancement = (Advancement) candidate;
            final NamespacedKey key = parentAdvancement.getKey();
            return key != null ? key.toString() : null;
        }
        if (candidate instanceof NamespacedKey) {
            return candidate.toString();
        }
        if (candidate instanceof CharSequence) {
            return candidate.toString();
        }
        final Object keyCandidate = invokeZeroArgumentMethod(candidate, "getKey", "key",
                "getNamespacedKey", "namespacedKey");
        if (keyCandidate != null && keyCandidate != candidate) {
            final String key = extractParentId(keyCandidate);
            if (key != null) {
                return key;
            }
        }
        final Object idCandidate = invokeZeroArgumentMethod(candidate, "getId", "id");
        if (idCandidate != null && idCandidate != candidate) {
            final String id = extractParentId(idCandidate);
            if (id != null) {
                return id;
            }
        }
        return null;
    }

    private Object invokeZeroArgumentMethod(final Object target, final String... methodNames) {
        if (target == null || methodNames == null || methodNames.length == 0) {
            return null;
        }
        final Class<?> targetClass = target.getClass();
        for (final String methodName : methodNames) {
            if (methodName == null || methodName.isBlank()) {
                continue;
            }
            try {
                final Method method = targetClass.getMethod(methodName);
                final Object value = method.invoke(target);
                if (value != null) {
                    return value;
                }
            } catch (final NoSuchMethodException ignored) {
                // Try the next candidate name for the current server version.
            } catch (final ReflectiveOperationException | IllegalArgumentException exception) {
                plugin.getLogger().log(Level.FINEST,
                        "Konnte " + targetClass.getName() + "#" + methodName + " nicht aufrufen.", exception);
                return null;
            }
        }
        return null;
    }

    private Object resolveDisplayComponent(final AdvancementDisplay display, final String... methodNames) {
        final Class<?> componentClass = loadClass("net.kyori.adventure.text.Component");
        final Class<?> effectiveClass = componentClass != null ? componentClass : Object.class;
        return resolveDisplayValueInternal(display, effectiveClass, methodNames);
    }

    private <T> T resolveDisplayValue(final AdvancementDisplay display, final Class<T> type,
            final String... methodNames) {
        if (type == null) {
            return null;
        }
        final Object value = resolveDisplayValueInternal(display, type, methodNames);
        if (value == null || !type.isInstance(value)) {
            return null;
        }
        return type.cast(value);
    }

    private Object resolveDisplayValueInternal(final AdvancementDisplay display, final Class<?> type,
            final String... methodNames) {
        if (display == null || methodNames == null || methodNames.length == 0) {
            return null;
        }
        final Class<?> displayClass = display.getClass();
        for (final String methodName : methodNames) {
            if (methodName == null || methodName.isBlank()) {
                continue;
            }
            try {
                final Method method = displayClass.getMethod(methodName);
                final Object value = method.invoke(display);
                if (value == null) {
                    return null;
                }
                if (type == null || type.isInstance(value)) {
                    return value;
                }
            } catch (final NoSuchMethodException ignored) {
                // Try the next candidate name for the current server version.
            } catch (final ReflectiveOperationException | IllegalArgumentException exception) {
                plugin.getLogger().log(Level.FINEST,
                        "Konnte AdvancementDisplay#" + methodName + " nicht aufrufen.", exception);
                return null;
            }
        }
        return null;
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

    private String componentToPlain(final Object component) {
        if (component == null) {
            return null;
        }
        try {
            final Class<?> componentClass = loadClass("net.kyori.adventure.text.Component");
            final Class<?> serializerClass = loadClass(
                    "net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            if (componentClass != null && serializerClass != null && componentClass.isInstance(component)) {
                final Method plainTextMethod = serializerClass.getMethod("plainText");
                final Object serializer = plainTextMethod.invoke(null);
                if (serializer != null) {
                    final Method serializeMethod = serializerClass.getMethod("serialize", componentClass);
                    final Object result = serializeMethod.invoke(serializer, component);
                    return result != null ? result.toString() : null;
                }
            }
        } catch (final Throwable throwable) {
            plugin.getLogger().log(Level.FINEST, "Konnte Component nicht als Klartext serialisieren.", throwable);
        }
        return component.toString();
    }

    private Class<?> loadClass(final String className) {
        if (className == null || className.isBlank()) {
            return null;
        }
        try {
            final ClassLoader loader = ProgressDataManager.class.getClassLoader();
            return Class.forName(className, false, loader);
        } catch (final ClassNotFoundException | LinkageError exception) {
            return null;
        }
    }

    private String stringifyItem(final ItemStack itemStack) {
        if (itemStack == null) {
            return null;
        }
        Object type = null;
        try {
            final Method getTypeMethod = itemStack.getClass().getMethod("getType");
            type = getTypeMethod.invoke(itemStack);
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            plugin.getLogger().log(Level.FINEST, "Konnte ItemStack-Typ nicht ermitteln.", exception);
        }
        if (type == null) {
            return itemStack.toString();
        }
        final String identifier = stringifyItemType(type);
        if (identifier != null) {
            return identifier;
        }
        return type.toString();
    }

    private String stringifyItemType(final Object type) {
        if (type == null) {
            return null;
        }
        if (type instanceof CharSequence) {
            return type.toString();
        }
        if (type instanceof Enum<?>) {
            return ((Enum<?>) type).name();
        }
        final Object keyCandidate = invokeZeroArgumentMethod(type, "getKey", "key");
        final String key = stringifyKeyCandidate(keyCandidate);
        if (key != null) {
            return key;
        }
        final Object namespacedKeyCandidate = invokeZeroArgumentMethod(type, "getNamespacedKey", "namespacedKey");
        final String namespacedKey = stringifyKeyCandidate(namespacedKeyCandidate);
        if (namespacedKey != null) {
            return namespacedKey;
        }
        final Object translationKeyCandidate = invokeZeroArgumentMethod(type, "getTranslationKey", "translationKey");
        if (translationKeyCandidate instanceof CharSequence) {
            return translationKeyCandidate.toString();
        }
        return null;
    }

    private String stringifyKeyCandidate(final Object candidate) {
        if (candidate == null) {
            return null;
        }
        if (candidate instanceof NamespacedKey) {
            return candidate.toString();
        }
        if (candidate instanceof CharSequence) {
            return candidate.toString();
        }
        final Object nestedKey = invokeZeroArgumentMethod(candidate, "getKey", "key");
        if (nestedKey != null && nestedKey != candidate) {
            final String nested = stringifyKeyCandidate(nestedKey);
            if (nested != null) {
                return nested;
            }
        }
        return candidate.toString();
    }

    private void saveMasterIfDirty(final MasterFileContext context) {
        if (context.locked) {
            plugin.getLogger().info(() -> context.entryType + "-Master-Datei ist fixiert und aktuell: " + context.file);
            context.dirty = false;
            return;
        }
        if (!context.dirty) {
            plugin.getLogger().info(() -> context.entryType + "-Master-Datei ist bereits aktuell: " + context.file);
            return;
        }
        if (context.initializationReason != null && !context.initializationReason.isBlank()) {
            final String reason = context.initializationReason;
            plugin.getLogger().warning(() -> "Initialisiere " + context.entryType + "-Master-Datei (Grund: " + reason
                    + "). Bitte prüfen Sie die Quelldaten und aktualisieren Sie die Datei bei Bedarf: " + context.file);
        }
        try {
            ensureDataFolders();
            final Map<String, Object> root = new LinkedHashMap<>();
            root.put("generatedAt", Instant.now().toString());
            final List<Map<String, Object>> entries = new ArrayList<>();
            for (final MasterEntry entry : context.entries.values()) {
                entries.add(toMasterMap(entry));
            }
            root.put("entries", entries);
            final String json = SimpleJson.stringify(root);
            plugin.getLogger().info(() -> "Schreibe " + context.entryType + "-Master-Datei mit "
                    + context.entries.size() + " Einträgen: " + context.file);
            try (Writer writer = Files.newBufferedWriter(context.file, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                writer.write(json);
                writer.write(System.lineSeparator());
            }
            context.dirty = false;
            context.locked = true;
            context.initializationReason = null;
            plugin.getLogger().info(
                    () -> context.entryType + "-Master-Datei erfolgreich aktualisiert: " + context.file);
        } catch (final IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Konnte " + context.entryType + "-Master-Datei nicht speichern: " + context.file, exception);
        }
    }

    private void markMasterInitializationRequired(final MasterFileContext context, final String reason) {
        context.dirty = true;
        context.locked = false;
        if (reason == null || reason.isBlank()) {
            context.initializationReason = "Unbekannter Grund";
        } else {
            context.initializationReason = reason;
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

    private void resetMaster(final MasterFileContext context, final String reason) {
        context.entries.clear();
        context.dirty = true;
        context.locked = false;
        context.initializationReason = reason;
    }

    private void deleteMasterFile(final MasterFileContext context) {
        try {
            if (Files.deleteIfExists(context.file)) {
                plugin.getLogger().info(() -> context.entryType + "-Master-Datei gelöscht: " + context.file);
            }
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Konnte " + context.entryType + "-Master-Datei nicht löschen: " + context.file, exception);
        }
    }

    private void deleteLegacyMasterFile() {
        try {
            if (Files.deleteIfExists(legacyMasterFile)) {
                plugin.getLogger().info(() -> "Veraltete Master-Datei entfernt: " + legacyMasterFile);
            }
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Konnte veraltete Master-Datei nicht löschen: " + legacyMasterFile, exception);
        }
    }

    private void ensureDataFolders() throws IOException {
        final Path advancementParent = advancementMaster.file.getParent();
        if (advancementParent != null) {
            Files.createDirectories(advancementParent);
        }
        final Path questParent = questMaster.file.getParent();
        if (questParent != null) {
            Files.createDirectories(questParent);
        }
        Files.createDirectories(playersDirectory);
        final Path logParent = playerUpdateLogFile.getParent();
        if (logParent != null) {
            Files.createDirectories(logParent);
        }
    }

    public static final class MasterRefreshResult {
        private final int advancementCount;
        private final int questCount;

        public MasterRefreshResult(final int advancementCount, final int questCount) {
            this.advancementCount = advancementCount;
            this.questCount = questCount;
        }

        public int getAdvancementCount() {
            return advancementCount;
        }

        public int getQuestCount() {
            return questCount;
        }

        @Override
        public String toString() {
            return "MasterRefreshResult{" +
                    "advancementCount=" + advancementCount +
                    ", questCount=" + questCount +
                    '}';
        }
    }

    private static final class MasterFileContext {
        private final Path file;
        private final MasterEntry.EntryType entryType;
        private final Map<String, MasterEntry> entries = new LinkedHashMap<>();
        private boolean dirty;
        private boolean locked;
        private String initializationReason;

        private MasterFileContext(final Path file, final MasterEntry.EntryType entryType) {
            this.file = file;
            this.entryType = entryType;
            this.dirty = false;
            this.locked = false;
            this.initializationReason = null;
        }
    }
}
