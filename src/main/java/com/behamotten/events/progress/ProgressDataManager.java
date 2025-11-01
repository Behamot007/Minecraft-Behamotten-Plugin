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
import java.util.LinkedHashSet;
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
    private static final String QUEST_TRANSLATIONS_FILE_NAME = "ftbquests_translations.json";
    private static final String ADVANCEMENT_TRANSLATIONS_FILE_NAME = "advancements_translations.json";

    private final JavaPlugin plugin;
    private final Path legacyMasterFile;
    private final Path playersDirectory;
    private final Path questDefinitionsFile;
    private final Path playerUpdateLogFile;
    private final Path questTranslationsFile;
    private final Path advancementTranslationsFile;
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
        this.questTranslationsFile = dataFolder.resolve(QUEST_TRANSLATIONS_FILE_NAME);
        this.advancementTranslationsFile = dataFolder.resolve(ADVANCEMENT_TRANSLATIONS_FILE_NAME);
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
        ensureQuestDefinitions(definitionFile, questTranslationsFile);
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

    private void ensureQuestDefinitions(final Path definitionFile, final Path translationFile) {
        if (definitionFile == null) {
            return;
        }
        final boolean definitionsPresent = Files.exists(definitionFile);
        final boolean translationsPresent = translationFile != null && Files.exists(translationFile);
        if (definitionsPresent && translationsPresent) {
            return;
        }
        final Optional<FtbQuestDefinitionExtractor.QuestExtractionResult> runtimeExtraction =
                extractQuestsFromRuntime();
        if (runtimeExtraction.isPresent()) {
            final FtbQuestDefinitionExtractor.QuestExtractionResult extraction = runtimeExtraction.get();
            final Instant generatedAt = Instant.now();
            final String sourceDescription = "ftbquests-runtime";
            try {
                writeQuestDefinitionArtifacts(extraction, generatedAt, sourceDescription, definitionFile,
                        translationFile, null);
                plugin.getLogger().info(() -> "FTB-Quest-Definitionen aus Laufzeitdaten erzeugt: " + definitionFile
                        + " (" + extraction.getQuestCount() + " Quests)");
            } catch (final IOException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Konnte Quest-Definitionen nicht aus Laufzeitdaten schreiben: " + definitionFile, exception);
            }
            return;
        }
        final Path questsDirectory = locateFtbQuestsDirectory();
        if (questsDirectory == null || !Files.isDirectory(questsDirectory)) {
            plugin.getLogger().warning(() -> "FTB-Quest-Verzeichnis nicht gefunden: " + questsDirectory);
            return;
        }
        plugin.getLogger().info(() -> "Erzeuge FTB-Quest-Definitionen aus SNBT-Dateien: " + questsDirectory);
        try {
            final FtbQuestDefinitionExtractor extractor = new FtbQuestDefinitionExtractor(plugin.getLogger());
            final FtbQuestDefinitionExtractor.QuestExtractionResult result = extractor.extract(questsDirectory);
            if (result.isEmpty()) {
                plugin.getLogger().warning(() -> "Keine FTB-Quests im Verzeichnis gefunden: " + questsDirectory);
                return;
            }
            final Instant generatedAt = Instant.now();
            final String sourceDescription = describeSourceDirectory(questsDirectory);
            writeQuestDefinitionArtifacts(result, generatedAt, sourceDescription, definitionFile, translationFile,
                    null);
            plugin.getLogger().info(() -> "FTB-Quest-Definitionen erzeugt: " + definitionFile + " (" + result.getQuestCount()
                    + " Quests)");
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Konnte FTB-Quest-Definitionen nicht aus SNBT-Dateien erstellen: " + definitionFile, exception);
        }
    }

    private void writeJsonFile(final Path file, final Map<String, Object> content) throws IOException {
        if (file == null || content == null) {
            return;
        }
        final Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        final String json = SimpleJson.stringify(content) + System.lineSeparator();
        Files.writeString(file, json, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private List<Map<String, Object>> buildAdvancementTranslations() {
        final List<Map<String, Object>> entries = new ArrayList<>();
        final Set<String> seenKeys = new LinkedHashSet<>();
        for (final MasterEntry entry : advancementMaster.entries.values()) {
            if (entry == null) {
                continue;
            }
            addTranslationEntry(entries, seenKeys, entry, "name", entry.getName());
            addTranslationEntry(entries, seenKeys, entry, "description", entry.getDescription());
        }
        return entries;
    }

    private void addTranslationEntry(final List<Map<String, Object>> entries, final Set<String> seenKeys,
            final MasterEntry entry, final String field, final String fallbackValue) {
        if (entry == null || field == null) {
            return;
        }
        final Map<String, Object> attributes = entry.getAttributes();
        final String translationKey = asString(attributes.get(field + "TranslationKey"));
        final Map<String, String> translations = toStringMap(attributes.get(field + "Translations"));
        final String id = translationKey != null && !translationKey.isBlank() ? translationKey : entry.getId();
        addTranslationEntry(entries, seenKeys, id, field, translations.get("de"), translations.get("en"),
                fallbackValue);
    }

    private void addTranslationEntry(final List<Map<String, Object>> entries, final Set<String> seenKeys,
            final String id, final String field, final String deValue, final String enValue, final String fallbackValue) {
        if (id == null || field == null) {
            return;
        }
        final String sanitizedFallback = sanitizeTranslationValue(fallbackValue);
        final String german = sanitizeTranslationValue(deValue);
        final String english = sanitizeTranslationValue(enValue);
        final String resolvedGerman = german != null ? german : sanitizedFallback;
        final String resolvedEnglish = english != null ? english : sanitizedFallback;
        if ((resolvedGerman == null || resolvedGerman.isBlank()) && (resolvedEnglish == null
                || resolvedEnglish.isBlank())) {
            return;
        }
        final String key = id + "#" + field;
        if (!seenKeys.add(key)) {
            return;
        }
        final Map<String, Object> translation = new LinkedHashMap<>();
        translation.put("id", id);
        translation.put("field", field);
        translation.put("de", resolvedGerman != null ? resolvedGerman : resolvedEnglish);
        translation.put("en", resolvedEnglish != null ? resolvedEnglish : resolvedGerman);
        entries.add(translation);
    }

    private String sanitizeTranslationValue(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private void writeTranslationFile(final Path file, final Instant generatedAt, final String sourceDescription,
            final List<Map<String, Object>> entries) throws IOException {
        if (file == null) {
            return;
        }
        final List<Map<String, Object>> sanitizedEntries = entries != null ? new ArrayList<>(entries)
                : new ArrayList<>();
        final Map<String, Object> root = new LinkedHashMap<>();
        root.put("generatedAt", (generatedAt != null ? generatedAt : Instant.now()).toString());
        root.put("source", sourceDescription != null ? sourceDescription : "");
        root.put("entryCount", sanitizedEntries.size());
        root.put("entries", sanitizedEntries);
        writeJsonFile(file, root);
    }

    private Path locateFtbQuestsDirectory() {
        final Path dataFolder = plugin.getDataFolder().toPath().toAbsolutePath();
        final List<Path> candidates = new ArrayList<>();
        Path current = dataFolder;
        for (int depth = 0; depth < 5 && current != null; depth++, current = current.getParent()) {
            collectQuestDirectoryCandidates(current, candidates);
        }
        for (final Path candidate : candidates) {
            if (candidate != null && Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        if (!candidates.isEmpty()) {
            plugin.getLogger().fine(() -> "Durchsuchte FTB-Quest-Verzeichnisse: "
                    + candidates.stream().map(path -> path.toString().replace('\\', '/'))
                            .reduce((left, right) -> left + ", " + right).orElse(""));
        }
        return null;
    }

    private void collectQuestDirectoryCandidates(final Path base, final List<Path> candidates) {
        if (base == null) {
            return;
        }
        final Path normalizedBase = base.toAbsolutePath().normalize();
        candidates.add(normalizedBase.resolve("config").resolve("ftbquests").resolve("quests"));
        candidates.add(normalizedBase.resolve("ftbquests").resolve("quests"));
        candidates.add(normalizedBase.resolve("world").resolve("ftbquests").resolve("quests"));
        candidates.add(normalizedBase.resolve("world").resolve("serverconfig").resolve("ftbquests").resolve("quests"));
        candidates.add(normalizedBase.resolve("world").resolve("config").resolve("ftbquests").resolve("quests"));
        candidates.add(normalizedBase.resolve("serverconfig").resolve("ftbquests").resolve("quests"));
        candidates.add(normalizedBase.resolve("defaultconfigs").resolve("ftbquests").resolve("quests"));
        candidates.add(normalizedBase.resolve("local").resolve("ftbquests").resolve("quests"));
    }

    private Optional<FtbQuestDefinitionExtractor.QuestExtractionResult> extractQuestsFromRuntime() {
        try {
            final FtbQuestRuntimeDefinitionExtractor extractor = new FtbQuestRuntimeDefinitionExtractor(
                    plugin.getLogger());
            final Optional<FtbQuestDefinitionExtractor.QuestExtractionResult> extraction = extractor.extract()
                    .filter(result -> !result.isEmpty());
            extraction.ifPresent(result -> plugin.getLogger().info(
                    () -> "FTB-Quest-Daten aus Laufzeit ermittelt: " + result.getQuestCount() + " Quests."));
            return extraction;
        } catch (final RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Fehler bei der Extraktion der FTB-Quest-Daten über die Laufzeit-API.", exception);
            return Optional.empty();
        }
    }

    private ExtractionContext determineQuestExtraction() throws IOException {
        final Optional<FtbQuestDefinitionExtractor.QuestExtractionResult> runtimeExtraction =
                extractQuestsFromRuntime();
        if (runtimeExtraction.isPresent()) {
            return new ExtractionContext(runtimeExtraction.get(), "ftbquests-runtime", null);
        }
        final Path questsDirectory = locateFtbQuestsDirectory();
        if (questsDirectory == null || !Files.isDirectory(questsDirectory)) {
            final String warning = "FTB-Quest-Verzeichnis nicht gefunden: " + questsDirectory;
            return new ExtractionContext(null, null, warning);
        }
        plugin.getLogger().info(() -> "Erzeuge FTB-Quest-Definitionen aus SNBT-Dateien: " + questsDirectory);
        final FtbQuestDefinitionExtractor extractor = new FtbQuestDefinitionExtractor(plugin.getLogger());
        final FtbQuestDefinitionExtractor.QuestExtractionResult extraction = extractor.extract(questsDirectory);
        if (extraction.isEmpty()) {
            final String warning = "Keine FTB-Quests im Verzeichnis gefunden: " + questsDirectory;
            return new ExtractionContext(null, describeSourceDirectory(questsDirectory), warning);
        }
        final String sourceDescription = describeSourceDirectory(questsDirectory);
        return new ExtractionContext(extraction, sourceDescription, null);
    }

    private void writeQuestDefinitionArtifacts(final FtbQuestDefinitionExtractor.QuestExtractionResult extraction,
            final Instant generatedAt, final String sourceDescription, final Path definitionFile,
            final Path translationFile, final List<String> errors) throws IOException {
        if (extraction == null || definitionFile == null) {
            return;
        }
        writeJsonFile(definitionFile, extraction.toDefinitionDocument(generatedAt, sourceDescription));
        if (translationFile == null) {
            return;
        }
        try {
            writeJsonFile(translationFile, extraction.toTranslationDocument(generatedAt, sourceDescription));
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Konnte Quest-Übersetzungsdatei nicht schreiben: " + translationFile, exception);
            if (errors != null) {
                errors.add("Quest-Übersetzungsdatei konnte nicht geschrieben werden: " + translationFile + " ("
                        + exception.getMessage() + ")");
            }
        }
    }

    private static final class ExtractionContext {
        final FtbQuestDefinitionExtractor.QuestExtractionResult extraction;
        final String sourceDescription;
        final String warning;

        ExtractionContext(final FtbQuestDefinitionExtractor.QuestExtractionResult extraction,
                final String sourceDescription, final String warning) {
            this.extraction = extraction;
            this.sourceDescription = sourceDescription;
            this.warning = warning;
        }
    }

    private String describeSourceDirectory(final Path questsDirectory) {
        if (questsDirectory == null) {
            return "";
        }
        try {
            final Path dataFolder = plugin.getDataFolder().toPath();
            final Path pluginsFolder = dataFolder.getParent();
            final Path serverRoot = pluginsFolder != null ? pluginsFolder.getParent() : null;
            if (serverRoot != null) {
                return serverRoot.relativize(questsDirectory).toString().replace('\\', '/');
            }
        } catch (final IllegalArgumentException ignored) {
            // Fallback to absolute path below
        }
        return questsDirectory.toAbsolutePath().toString().replace('\\', '/');
    }

    public AdvancementMasterResult regenerateAdvancementMaster(final Iterator<Advancement> advancements) {
        plugin.getLogger().info("Starte Aktualisierung der Advancement-Masterdatei.");
        deleteLegacyMasterFile();
        resetMaster(advancementMaster, "Neuaufbau angefordert");
        deleteMasterFile(advancementMaster);
        final int synchronizedAdvancements = synchronizeAdvancements(advancements);
        final List<Map<String, Object>> translationEntries = buildAdvancementTranslations();
        final Instant generatedAt = Instant.now();
        final String sourceDescription = "advancements";
        final List<String> errors = new ArrayList<>();
        if (!saveMasterIfDirty(advancementMaster)) {
            errors.add("Konnte Advancement-Masterdatei nicht schreiben: " + advancementMaster.file);
        }
        try {
            writeTranslationFile(advancementTranslationsFile, generatedAt, sourceDescription, translationEntries);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Konnte Advancement-Übersetzungsdatei nicht schreiben: " + advancementTranslationsFile, exception);
            errors.add("Übersetzungsdatei konnte nicht geschrieben werden: " + advancementTranslationsFile + " ("
                    + exception.getMessage() + ")");
        }
        if (errors.isEmpty()) {
            plugin.getLogger().info(() -> "Advancement-Masterdatei aktualisiert: " + synchronizedAdvancements
                    + " Einträge, " + translationEntries.size() + " Übersetzungen.");
        } else {
            plugin.getLogger().warning(() -> "Advancement-Masterdatei wurde mit Fehlern erstellt. Bitte prüfen Sie die Logs.");
        }
        return new AdvancementMasterResult(synchronizedAdvancements, translationEntries.size(), errors);
    }

    public QuestMasterResult regenerateQuestMaster() {
        plugin.getLogger().info("Starte Aktualisierung der Quest-Masterdatei.");
        deleteLegacyMasterFile();
        resetMaster(questMaster, "Neuaufbau angefordert");
        deleteMasterFile(questMaster);
        final List<String> errors = new ArrayList<>();
        try {
            final ExtractionContext extractionContext = determineQuestExtraction();
            if (extractionContext.extraction == null) {
                final String warning = extractionContext.warning != null ? extractionContext.warning
                        : "FTB-Quest-Daten konnten nicht gefunden werden.";
                plugin.getLogger().warning(warning);
                markMasterInitializationRequired(questMaster, "FTB-Quest-Daten fehlen");
                return new QuestMasterResult(0, 0, 0, List.of(warning), List.of(warning));
            }
            final FtbQuestDefinitionExtractor.QuestExtractionResult extraction = extractionContext.extraction;
            final Instant generatedAt = Instant.now();
            try {
                writeQuestDefinitionArtifacts(extraction, generatedAt, extractionContext.sourceDescription,
                        questDefinitionsFile, questTranslationsFile, errors);
            } catch (final IOException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Konnte Quest-Definitionsdateien nicht schreiben: " + questDefinitionsFile, exception);
                errors.add("Quest-Definitionen konnten nicht geschrieben werden: " + questDefinitionsFile + " ("
                        + exception.getMessage() + ")");
            }
            final int importedQuests = importQuestDefinitions(questDefinitionsFile);
            if (!saveMasterIfDirty(questMaster)) {
                errors.add("Konnte Quest-Masterdatei nicht schreiben: " + questMaster.file);
            }
            final List<String> warnings = extraction.getWarnings();
            for (final String warning : warnings) {
                plugin.getLogger().warning(() -> "FTB-Quest-Warnung: " + warning);
            }
            if (errors.isEmpty()) {
                plugin.getLogger().info(() -> "Quest-Masterdatei aktualisiert: " + importedQuests + " Quests in "
                        + extraction.getChapterCount() + " Kapiteln.");
            } else {
                plugin.getLogger()
                        .warning(() -> "Quest-Masterdatei wurde mit Fehlern erstellt. Bitte prüfen Sie die Logs.");
            }
            return new QuestMasterResult(importedQuests, extraction.getChapterCount(),
                    extraction.getTranslationCount(), warnings, errors);
        } catch (final IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Konnte Quest-Masterdatei nicht erstellen.", exception);
            markMasterInitializationRequired(questMaster, "Fehler beim Export der Quests");
            errors.add("Fehler beim Export der Quest-Daten: " + exception.getMessage());
            return new QuestMasterResult(0, 0, 0, List.of(), errors);
        }
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
        final TranslationSupport.Translation titleTranslation = TranslationSupport
                .fromComponent(titleComponent, plugin.getLogger());
        final TranslationSupport.Translation descriptionTranslation = TranslationSupport
                .fromComponent(descriptionComponent, plugin.getLogger());
        titleTranslation.ensureFallback(id);
        final String title = titleTranslation.englishOr(id);
        final String description = descriptionTranslation.fallbackOr(null);
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
        final Map<String, String> titleTranslations = titleTranslation.toMap();
        if (!titleTranslations.isEmpty()) {
            attributes.put("titleTranslations", new LinkedHashMap<>(titleTranslations));
        }
        if (titleTranslation.getTranslationKey() != null) {
            attributes.put("titleTranslationKey", titleTranslation.getTranslationKey());
        }
        final Map<String, String> descriptionTranslations = descriptionTranslation.toMap();
        if (!descriptionTranslations.isEmpty()) {
            attributes.put("descriptionTranslations", new LinkedHashMap<>(descriptionTranslations));
        }
        if (descriptionTranslation.getTranslationKey() != null) {
            attributes.put("descriptionTranslationKey", descriptionTranslation.getTranslationKey());
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

    private boolean saveMasterIfDirty(final MasterFileContext context) {
        if (context.locked) {
            plugin.getLogger().info(() -> context.entryType + "-Master-Datei ist fixiert und aktuell: " + context.file);
            context.dirty = false;
            return true;
        }
        if (!context.dirty) {
            plugin.getLogger().info(() -> context.entryType + "-Master-Datei ist bereits aktuell: " + context.file);
            return true;
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
            return true;
        } catch (final IOException | RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Konnte " + context.entryType + "-Master-Datei nicht speichern: " + context.file, exception);
            context.locked = false;
            context.dirty = true;
            context.initializationReason = "Fehler beim Speichern";
            return false;
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

    public static final class AdvancementMasterResult {
        private final int advancementCount;
        private final int translationCount;
        private final List<String> errors;

        public AdvancementMasterResult(final int advancementCount, final int translationCount,
                final List<String> errors) {
            this.advancementCount = advancementCount;
            this.translationCount = translationCount;
            this.errors = errors != null ? List.copyOf(errors) : List.of();
        }

        public int getAdvancementCount() {
            return advancementCount;
        }

        public int getTranslationCount() {
            return translationCount;
        }

        public List<String> getErrors() {
            return errors;
        }

        @Override
        public String toString() {
            return "AdvancementMasterResult{" +
                    "advancementCount=" + advancementCount +
                    ", translationCount=" + translationCount +
                    ", errors=" + errors +
                    '}';
        }
    }

    public static final class QuestMasterResult {
        private final int questCount;
        private final int chapterCount;
        private final int translationCount;
        private final List<String> warnings;
        private final List<String> errors;

        public QuestMasterResult(final int questCount, final int chapterCount, final int translationCount,
                final List<String> warnings, final List<String> errors) {
            this.questCount = questCount;
            this.chapterCount = chapterCount;
            this.translationCount = translationCount;
            this.warnings = warnings != null ? List.copyOf(warnings) : List.of();
            this.errors = errors != null ? List.copyOf(errors) : List.of();
        }

        public int getQuestCount() {
            return questCount;
        }

        public int getChapterCount() {
            return chapterCount;
        }

        public int getTranslationCount() {
            return translationCount;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public List<String> getErrors() {
            return errors;
        }

        @Override
        public String toString() {
            return "QuestMasterResult{" +
                    "questCount=" + questCount +
                    ", chapterCount=" + chapterCount +
                    ", translationCount=" + translationCount +
                    ", warnings=" + warnings +
                    ", errors=" + errors +
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
