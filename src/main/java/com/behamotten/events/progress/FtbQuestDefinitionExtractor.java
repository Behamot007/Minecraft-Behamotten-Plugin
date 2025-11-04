package com.behamotten.events.progress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts quest definitions from the SNBT files that are used by FTB Quests.
 * <p>
 * The extractor is intentionally forgiving – unknown or malformed snippets are captured inside a
 * "raw" attribute so that no information is lost even if the structure differs between mod packs.
 * </p>
 */
final class FtbQuestDefinitionExtractor {

    private final Logger logger;

    FtbQuestDefinitionExtractor(final Logger logger) {
        this.logger = logger != null ? logger : Logger.getLogger(FtbQuestDefinitionExtractor.class.getName());
    }

    QuestExtractionResult extract(final Path questsDirectory) throws IOException {
        final QuestExtractionResult result = new QuestExtractionResult(logger);
        if (questsDirectory == null || !Files.isDirectory(questsDirectory)) {
            return result;
        }
        Files.walk(questsDirectory)
                .filter(path -> Files.isRegularFile(path) && path.getFileName().toString().endsWith(".snbt"))
                .sorted()
                .forEach(path -> processFile(result, questsDirectory, path));
        result.finalizeEntries();
        return result;
    }

    private void processFile(final QuestExtractionResult result, final Path rootDirectory, final Path file) {
        try {
            final String content = Files.readString(file, StandardCharsets.UTF_8);
            final Object parsed = SnbtParser.parse(content);
            if (!(parsed instanceof Map<?, ?>)) {
                result.addWarning("SNBT-Datei besitzt kein Compound-Root-Element: " + file);
                return;
            }
            final Map<String, Object> compound = toCompound((Map<?, ?>) parsed);
            handleChapter(result, rootDirectory, file, compound);
        } catch (final IOException exception) {
            logger.log(Level.SEVERE, "Konnte FTB-Quest-Datei nicht lesen: " + file, exception);
            result.addWarning("Fehler beim Lesen: " + file.getFileName());
        } catch (final SnbtParser.SnbtParseException exception) {
            logger.log(Level.SEVERE, "Konnte FTB-Quest-Datei nicht parsen: " + file, exception);
            result.addWarning("Parser-Fehler: " + file.getFileName());
        }
    }

    private void handleChapter(final QuestExtractionResult result, final Path rootDirectory, final Path file,
            final Map<String, Object> compound) {
        final String fileName = file.getFileName().toString();
        final String baseName = fileName.substring(0, fileName.length() - ".snbt".length());
        final String chapterId = firstNonBlank(asString(compound.get("id")),
                asString(compound.get("chapter")), baseName);
        final String chapterTitle = firstNonBlank(asString(compound.get("title")), asString(compound.get("name")),
                chapterId);
        final EnglishTextSupport.ResolvedText chapterTitleText = EnglishTextSupport
                .fromNullableString(chapterTitle);
        final Map<String, Object> chapterInfo = new LinkedHashMap<>();
        chapterInfo.put("id", safeSegment(chapterId));
        chapterInfo.put("title", chapterTitle);
        chapterInfo.put("file", rootDirectory.relativize(file).toString().replace('\\', '/'));
        final String chapterDescription = flattenDescription(compound.get("description"));
        if (chapterDescription != null && !chapterDescription.isBlank()) {
            chapterInfo.put("description", chapterDescription);
        }
        result.addChapter(chapterInfo);
        chapterTitleText.ensureFallback(chapterTitle);

        final Collection<Map<String, Object>> questList = extractQuestList(compound.get("quests"));
        if (questList.isEmpty()) {
            result.addWarning("Keine Quests in Kapitel-Datei gefunden: " + fileName);
            return;
        }
        int index = 0;
        for (final Map<String, Object> quest : questList) {
            handleQuest(result, chapterInfo, quest, index++);
        }
    }

    private void handleQuest(final QuestExtractionResult result, final Map<String, Object> chapterInfo,
            final Map<String, Object> quest, final int index) {
        final String chapterId = Objects.toString(chapterInfo.get("id"), "chapter");
        final String rawQuestId = asString(quest.get("id"));
        final String questName = firstNonBlank(asString(quest.get("title")), asString(quest.get("name")),
                "Quest " + (index + 1));
        final EnglishTextSupport.ResolvedText questNameText = EnglishTextSupport
                .fromNullableString(questName);
        final String questId = result.nextUniqueQuestId(chapterId, rawQuestId != null ? rawQuestId : questName);
        final String description = flattenDescription(quest.get("description"));
        final String subtitle = firstNonBlank(asString(quest.get("subtitle")), asString(quest.get("subtitle_id")));
        final String icon = resolveIcon(quest.get("icon"));
        final Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("chapterId", chapterId);
        attributes.put("chapterTitle", Objects.toString(chapterInfo.get("title"), chapterId));
        attributes.put("sourceFile", Objects.toString(chapterInfo.get("file"), ""));
        if (rawQuestId != null && !rawQuestId.isBlank()) {
            attributes.put("questSourceId", rawQuestId);
        }
        if (subtitle != null && !subtitle.isBlank()) {
            attributes.put("subtitle", subtitle);
        }
        final String questDescription = description != null ? description : "";
        final List<String> criteria = describeTasks(quest.get("tasks"));
        final List<String> tags = toStringList(quest.get("tags"));
        attributes.put("rawData", SimpleJson.stringify(deepCopy(quest)));
        if (quest.containsKey("rewards")) {
            attributes.put("rewards", SimpleJson.stringify(deepCopy(quest.get("rewards"))));
        }
        if (quest.containsKey("dependencies")) {
            attributes.put("dependencies", SimpleJson.stringify(deepCopy(quest.get("dependencies"))));
        }

        final Map<String, Object> questEntry = new LinkedHashMap<>();
        questEntry.put("id", questId);
        questNameText.ensureFallback(questName);
        final String questTitle = questNameText.englishOr(questName);
        questEntry.put("name", questTitle);
        final EnglishTextSupport.ResolvedText questDescriptionText = EnglishTextSupport
                .fromNullableString(!questDescription.isBlank() ? questDescription : null);
        questDescriptionText.ensureFallback(!questDescription.isBlank() ? questDescription : null);
        if (!questDescription.isBlank()) {
            questEntry.put("description", questDescriptionText.englishOr(questDescription));
        }
        questEntry.put("chapter", Objects.toString(chapterInfo.get("title"), chapterId));
        if (icon != null && !icon.isBlank()) {
            questEntry.put("icon", icon);
        }
        if (!attributes.isEmpty()) {
            questEntry.put("attributes", attributes);
        }
        if (!criteria.isEmpty()) {
            questEntry.put("criteria", criteria);
        }
        if (!tags.isEmpty()) {
            questEntry.put("tags", tags);
        }
        result.addQuest(questEntry);
        result.addQuestEnglishResource(questId, questTitle);
    }

    private Collection<Map<String, Object>> extractQuestList(final Object questsObject) {
        if (questsObject instanceof Collection<?>) {
            final Collection<?> collection = (Collection<?>) questsObject;
            final List<Map<String, Object>> quests = new ArrayList<>();
            for (final Object element : collection) {
                if (element instanceof Map<?, ?>) {
                    quests.add(toCompound((Map<?, ?>) element));
                }
            }
            return quests;
        }
        return Collections.emptyList();
    }

    private List<String> describeTasks(final Object tasksObject) {
        if (!(tasksObject instanceof Collection<?>)) {
            return List.of();
        }
        final Collection<?> collection = (Collection<?>) tasksObject;
        final List<String> tasks = new ArrayList<>();
        int index = 1;
        for (final Object element : collection) {
            if (element instanceof Map<?, ?>) {
                final Map<String, Object> task = toCompound((Map<?, ?>) element);
                final String type = asString(task.get("type"));
                final String title = firstNonBlank(asString(task.get("title")), asString(task.get("item")),
                        asString(task.get("entity")));
                final StringBuilder builder = new StringBuilder();
                if (type != null) {
                    builder.append(type);
                } else {
                    builder.append("task");
                }
                if (title != null && !title.isBlank()) {
                    builder.append(':').append(title);
                } else {
                    builder.append('#').append(index);
                }
                tasks.add(builder.toString());
            } else {
                tasks.add("task#" + index);
            }
            index++;
        }
        return tasks;
    }

    private Map<String, Object> toCompound(final Map<?, ?> source) {
        final Map<String, Object> target = new LinkedHashMap<>();
        for (final Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            target.put(entry.getKey().toString(), normalizeValue(entry.getValue()));
        }
        return target;
    }

    private Object normalizeValue(final Object value) {
        if (value instanceof Map<?, ?>) {
            return toCompound((Map<?, ?>) value);
        }
        if (value instanceof List<?>) {
            final List<?> list = (List<?>) value;
            final List<Object> normalized = new ArrayList<>(list.size());
            for (final Object element : list) {
                normalized.add(normalizeValue(element));
            }
            return normalized;
        }
        if (value instanceof Number || value instanceof Boolean || value instanceof String) {
            return value;
        }
        return value != null ? value.toString() : null;
    }

    private Object deepCopy(final Object value) {
        final Object normalized = normalizeValue(value);
        if (normalized instanceof Map<?, ?>) {
            final Map<?, ?> map = (Map<?, ?>) normalized;
            final Map<String, Object> copy = new LinkedHashMap<>();
            for (final Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() == null) {
                    continue;
                }
                copy.put(entry.getKey().toString(), deepCopy(entry.getValue()));
            }
            return copy;
        }
        if (normalized instanceof List<?>) {
            final List<?> list = (List<?>) normalized;
            final List<Object> copy = new ArrayList<>(list.size());
            for (final Object element : list) {
                copy.add(deepCopy(element));
            }
            return copy;
        }
        return normalized;
    }

    private String flattenDescription(final Object value) {
        if (value instanceof String) {
            return ((String) value).trim();
        }
        if (value instanceof Collection<?>) {
            final Collection<?> collection = (Collection<?>) value;
            final List<String> lines = new ArrayList<>();
            for (final Object element : collection) {
                final String line = flattenDescription(element);
                if (line != null && !line.isBlank()) {
                    lines.add(line.trim());
                }
            }
            return String.join("\n", lines);
        }
        if (value instanceof Map<?, ?>) {
            final Map<?, ?> map = (Map<?, ?>) value;
            final Object text = map.containsKey("text") ? map.get("text") : map.get("value");
            return flattenDescription(text);
        }
        return null;
    }

    private List<String> toStringList(final Object value) {
        if (!(value instanceof Collection<?>)) {
            return List.of();
        }
        final Collection<?> collection = (Collection<?>) value;
        final List<String> result = new ArrayList<>();
        for (final Object element : collection) {
            final String text = asString(element);
            if (text != null && !text.isBlank()) {
                result.add(text);
            }
        }
        return result;
    }

    private String resolveIcon(final Object iconObject) {
        if (iconObject instanceof String) {
            return (String) iconObject;
        }
        if (iconObject instanceof Map<?, ?>) {
            final Map<?, ?> map = (Map<?, ?>) iconObject;
            final String id = firstNonBlank(asString(map.get("id")), asString(map.get("item")),
                    asString(map.get("name")));
            if (id != null) {
                return id;
            }
            if (map.containsKey("stack")) {
                return resolveIcon(map.get("stack"));
            }
        }
        return null;
    }

    private static String safeSegment(final String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        final String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        final String trimmed = normalized.replaceAll("_+", "_");
        final String withoutEdgeUnderscore = trimmed.replaceAll("^_+|_+$", "");
        return withoutEdgeUnderscore.isEmpty() ? "segment" : withoutEdgeUnderscore;
    }

    private static String asString(final Object value) {
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

    private static String firstNonBlank(final String... values) {
        if (values == null) {
            return null;
        }
        for (final String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    static final class QuestExtractionResult {
        private final Logger logger;
        private final List<Map<String, Object>> quests = new ArrayList<>();
        private final List<Map<String, Object>> chapters = new ArrayList<>();
        private final List<Map<String, Object>> englishResources = new ArrayList<>();
        private final Set<String> resourceIds = new LinkedHashSet<>();
        private final Set<String> usedQuestIds = new LinkedHashSet<>();
        private final List<String> warnings = new ArrayList<>();

        QuestExtractionResult(final Logger logger) {
            this.logger = logger != null ? logger : Logger.getLogger(FtbQuestDefinitionExtractor.class.getName());
        }

        void addWarning(final String warning) {
            warnings.add(warning);
        }

        void addChapter(final Map<String, Object> chapter) {
            chapters.add(chapter);
        }

        void addQuest(final Map<String, Object> quest) {
            quests.add(quest);
        }

        void addQuestEnglishResource(final String questId, final String englishTitle) {
            final String sanitizedId = questId != null ? questId.trim() : null;
            final String sanitizedTitle = englishTitle != null ? englishTitle.trim() : null;
            if (sanitizedId == null || sanitizedId.isEmpty()) {
                logger.warning("Englischer Ressourceneintrag ohne Quest-ID wurde verworfen.");
                return;
            }
            if (sanitizedTitle == null || sanitizedTitle.isEmpty()) {
                logger.severe("Englischer Ressourceneintrag ohne Titel – betroffene Quest: " + sanitizedId);
                return;
            }
            if (!resourceIds.add(sanitizedId)) {
                logger.warning("Duplizierter Quest-Ressourceneintrag ignoriert: " + sanitizedId);
                return;
            }
            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", sanitizedId);
            entry.put("title", sanitizedTitle);
            englishResources.add(entry);
        }

        String nextUniqueQuestId(final String chapterId, final String desiredId) {
            final String base = "ftbquests:" + safeSegment(chapterId) + "/" + safeSegment(desiredId);
            if (usedQuestIds.add(base)) {
                return base;
            }
            int counter = 2;
            while (!usedQuestIds.add(base + "-" + counter)) {
                counter++;
            }
            return base + "-" + counter;
        }

        boolean isEmpty() {
            return quests.isEmpty();
        }

        int getQuestCount() {
            return quests.size();
        }

        int getChapterCount() {
            return chapters.size();
        }

        int getEnglishResourceCount() {
            return englishResources.size();
        }

        List<String> getWarnings() {
            return List.copyOf(warnings);
        }

        void finalizeEntries() {
            quests.sort(Comparator
                    .comparing((Map<String, Object> map) -> Objects.toString(map.get("id"), "")));
            chapters.sort(Comparator
                    .comparing((Map<String, Object> map) -> Objects.toString(map.get("id"), "")));
            englishResources.sort(Comparator
                    .comparing((Map<String, Object> map) -> Objects.toString(map.get("id"), "")));
            warnings.sort(String::compareTo);
        }

        Map<String, Object> toDefinitionDocument(final Instant generatedAt, final String sourceDescription) {
            final Map<String, Object> root = new LinkedHashMap<>();
            root.put("generatedAt", generatedAt.toString());
            root.put("source", sourceDescription);
            root.put("questCount", quests.size());
            root.put("chapterCount", chapters.size());
            root.put("quests", new ArrayList<>(quests));
            if (!chapters.isEmpty()) {
                root.put("chapters", new ArrayList<>(chapters));
            }
            if (!warnings.isEmpty()) {
                root.put("warnings", new ArrayList<>(warnings));
            }
            return root;
        }

        Map<String, Object> toEnglishResourceDocument(final Instant generatedAt, final String sourceDescription) {
            final Map<String, Object> root = new LinkedHashMap<>();
            root.put("generatedAt", generatedAt.toString());
            root.put("source", sourceDescription);
            root.put("entryCount", englishResources.size());
            root.put("entries", new ArrayList<>(englishResources));
            return root;
        }
    }
}
