package com.behamotten.events.progress;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Extracts quest definitions from the runtime state of the FTB Quests mod using reflection.
 * <p>
 * This acts as a best-effort approach that tolerates missing fields or API changes by skipping
 * unavailable information while still returning the quests and their identifiers.
 * </p>
 */
final class FtbQuestRuntimeDefinitionExtractor {

    private final Logger logger;

    FtbQuestRuntimeDefinitionExtractor(final Logger logger) {
        this.logger = logger != null ? logger : Logger.getLogger(FtbQuestRuntimeDefinitionExtractor.class.getName());
    }

    Optional<FtbQuestDefinitionExtractor.QuestExtractionResult> extract() {
        try {
            final Class<?> serverQuestFileClass = Class.forName("dev.ftb.mods.ftbquests.quest.ServerQuestFile");
            final Object questFile = resolveQuestFileInstance(serverQuestFileClass);
            if (questFile == null) {
                logger.fine("FTB Quests ServerQuestFile.INSTANCE ist nicht verfügbar.");
                return Optional.empty();
            }
            final Collection<?> chapters = extractChapterCollection(questFile);
            if (chapters == null || chapters.isEmpty()) {
                logger.fine("Keine Kapitel im FTB-Quest-Dateiobjekt gefunden.");
                return Optional.empty();
            }
            final FtbQuestDefinitionExtractor.QuestExtractionResult result =
                    new FtbQuestDefinitionExtractor.QuestExtractionResult();
            int chapterIndex = 0;
            for (final Object chapter : chapters) {
                if (chapter == null) {
                    continue;
                }
                handleChapter(result, chapter, chapterIndex++);
            }
            result.finalizeEntries();
            return Optional.of(result);
        } catch (final ClassNotFoundException exception) {
            logger.fine("FTB Quests scheint nicht geladen zu sein (ClassNotFoundException).");
            return Optional.empty();
        }
    }

    private Object resolveQuestFileInstance(final Class<?> serverQuestFileClass) {
        try {
            final Field instanceField = serverQuestFileClass.getDeclaredField("INSTANCE");
            instanceField.setAccessible(true);
            final Object value = instanceField.get(null);
            if (value != null) {
                return value;
            }
        } catch (final ReflectiveOperationException ignored) {
            // Continue with alternative lookups below.
        }
        try {
            final Method getInstance = serverQuestFileClass.getDeclaredMethod("getInstance");
            getInstance.setAccessible(true);
            final Object value = getInstance.invoke(null);
            if (value != null) {
                return value;
            }
        } catch (final ReflectiveOperationException ignored) {
            // Fall through to failure handling.
        }
        logger.fine("Konnte keine Instanz von ServerQuestFile ermitteln.");
        return null;
    }

    private Collection<?> extractChapterCollection(final Object questFile) {
        final Collection<?> viaMethod = invokeCollectionResult(questFile, "getAllChapters", "getChapters",
                "getChapterList", "getChapterMapValues");
        if (viaMethod != null) {
            return viaMethod;
        }
        final Object fieldValue = readFieldValue(questFile, "chapters", "chapterList", "chapterMap");
        if (fieldValue instanceof Map<?, ?>) {
            return ((Map<?, ?>) fieldValue).values();
        }
        if (fieldValue instanceof Collection<?>) {
            return (Collection<?>) fieldValue;
        }
        return null;
    }

    private void handleChapter(final FtbQuestDefinitionExtractor.QuestExtractionResult result, final Object chapter,
            final int index) {
        final String chapterIdRaw = firstNonBlank(extractString(chapter, "getCodeString", "getCodeName", "getId"),
                asString(readFieldValue(chapter, "filename", "file", "id")), "chapter" + (index + 1));
        final String chapterId = safeSegment(chapterIdRaw);
        final Object chapterTitleComponent = invoke(chapter, "getTitle");
        final TranslationSupport.Translation chapterTitleTranslation = TranslationSupport
                .fromComponent(chapterTitleComponent, logger);
        final String chapterTitle = firstNonBlank(chapterTitleTranslation.fallbackOr(null),
                componentToString(invoke(chapter, "getName")), componentToString(readFieldValue(chapter, "title")),
                chapterIdRaw);
        final Object chapterDescriptionComponent = invoke(chapter, "getDescription");
        final TranslationSupport.Translation chapterDescriptionTranslation = TranslationSupport
                .fromComponent(chapterDescriptionComponent, logger);
        final String chapterDescription = firstNonBlank(chapterDescriptionTranslation.fallbackOr(null),
                componentToString(readFieldValue(chapter, "description")));

        final Map<String, Object> chapterInfo = new LinkedHashMap<>();
        chapterInfo.put("id", chapterId);
        chapterInfo.put("title", chapterTitle);
        if (chapterDescription != null && !chapterDescription.isBlank()) {
            chapterInfo.put("description", chapterDescription);
        }
        result.addChapter(chapterInfo);
        chapterTitleTranslation.ensureFallback(chapterTitle);
        result.addTranslation("chapter:" + chapterId, "title", chapterTitleTranslation);
        if (chapterDescription != null && !chapterDescription.isBlank()) {
            chapterDescriptionTranslation.ensureFallback(chapterDescription);
            result.addTranslation("chapter:" + chapterId, "description", chapterDescriptionTranslation);
        }

        final Collection<?> quests = extractQuestCollection(chapter);
        if (quests == null || quests.isEmpty()) {
            result.addWarning("Keine Quests in Kapitel gefunden: " + chapterTitle);
            return;
        }
        int questIndex = 0;
        for (final Object quest : quests) {
            if (quest == null) {
                continue;
            }
            try {
                handleQuest(result, chapterInfo, quest, questIndex++);
            } catch (final RuntimeException exception) {
                logger.log(Level.WARNING, "Fehler beim Verarbeiten einer Quest (" + chapterTitle + ")", exception);
                result.addWarning("Quest konnte nicht verarbeitet werden: " + chapterTitle + "#" + questIndex);
            }
        }
    }

    private Collection<?> extractQuestCollection(final Object chapter) {
        final Collection<?> viaMethod = invokeCollectionResult(chapter, "getQuests", "getQuestList", "getQuestObjects");
        if (viaMethod != null) {
            return viaMethod;
        }
        final Object fieldValue = readFieldValue(chapter, "quests", "questList", "questObjects");
        if (fieldValue instanceof Collection<?>) {
            return (Collection<?>) fieldValue;
        }
        if (fieldValue instanceof Map<?, ?>) {
            return ((Map<?, ?>) fieldValue).values();
        }
        return null;
    }

    private void handleQuest(final FtbQuestDefinitionExtractor.QuestExtractionResult result,
            final Map<String, Object> chapterInfo, final Object quest, final int index) {
        final String chapterId = Objects.toString(chapterInfo.get("id"), "chapter");
        final String chapterTitle = Objects.toString(chapterInfo.get("title"), chapterId);
        final String rawQuestId = firstNonBlank(extractString(quest, "getCodeString", "getCodeName", "getId"),
                asString(readFieldValue(quest, "id")), "quest" + (index + 1));
        final String questId = result.nextUniqueQuestId(chapterId, rawQuestId);
        final Object questTitleComponent = invoke(quest, "getTitle");
        final TranslationSupport.Translation questTitleTranslation = TranslationSupport
                .fromComponent(questTitleComponent, logger);
        final String questName = firstNonBlank(questTitleTranslation.fallbackOr(null),
                componentToString(invoke(quest, "getName")), rawQuestId);
        final Object descriptionComponent = invoke(quest, "getDescription");
        final TranslationSupport.Translation questDescriptionTranslation = TranslationSupport
                .fromComponent(descriptionComponent, logger);
        final String description = firstNonBlank(questDescriptionTranslation.fallbackOr(null),
                componentToString(readFieldValue(quest, "description")));
        final Object subtitleComponent = invoke(quest, "getSubtitle");
        final TranslationSupport.Translation subtitleTranslation = TranslationSupport
                .fromComponent(subtitleComponent, logger);
        final String subtitle = firstNonBlank(subtitleTranslation.fallbackOr(null),
                componentToString(readFieldValue(quest, "subtitle")));
        final String icon = extractIcon(quest);
        final List<String> criteria = describeTasks(extractTaskCollection(quest));
        final List<String> tags = extractTags(quest);

        final Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("chapterId", chapterId);
        attributes.put("chapterTitle", chapterTitle);
        attributes.put("source", "ftbquests-runtime");
        if (rawQuestId != null && !rawQuestId.isBlank()) {
            attributes.put("questSourceId", rawQuestId);
        }
        if (subtitle != null && !subtitle.isBlank()) {
            attributes.put("subtitle", subtitle);
        }

        final Map<String, Object> questEntry = new LinkedHashMap<>();
        questEntry.put("id", questId);
        questTitleTranslation.ensureFallback(questName);
        questEntry.put("name", questTitleTranslation.englishOr(questName));
        questEntry.put("chapter", chapterTitle);
        questDescriptionTranslation.ensureFallback(description);
        if (description != null && !description.isBlank()) {
            questEntry.put("description", questDescriptionTranslation.englishOr(description));
        }
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
        result.addTranslation(questId, "name", questTitleTranslation);
        if (description != null && !description.isBlank()) {
            result.addTranslation(questId, "description", questDescriptionTranslation);
        }
        if (subtitle != null && !subtitle.isBlank()) {
            subtitleTranslation.ensureFallback(subtitle);
            result.addTranslation(questId, "subtitle", subtitleTranslation);
        }
    }

    private Collection<?> extractTaskCollection(final Object quest) {
        final Collection<?> viaMethod = invokeCollectionResult(quest, "getTasks", "getTaskList");
        if (viaMethod != null) {
            return viaMethod;
        }
        final Object fieldValue = readFieldValue(quest, "tasks", "taskList");
        if (fieldValue instanceof Collection<?>) {
            return (Collection<?>) fieldValue;
        }
        if (fieldValue != null && fieldValue.getClass().isArray()) {
            return toCollection(fieldValue);
        }
        return null;
    }

    private List<String> describeTasks(final Collection<?> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }
        final List<String> descriptions = new ArrayList<>();
        int index = 1;
        for (final Object task : tasks) {
            if (task == null) {
                continue;
            }
            final Object typeObj = invoke(task, "getType");
            final String type = typeObj != null ? typeObj.toString() : Objects.toString(readFieldValue(task, "type"), "task");
            final String title = firstNonBlank(componentToString(invoke(task, "getTitle")),
                    componentToString(readFieldValue(task, "title")),
                    componentToString(invoke(task, "getItem")), componentToString(readFieldValue(task, "item")));
            final StringBuilder builder = new StringBuilder();
            builder.append(type != null ? type : "task");
            if (title != null && !title.isBlank()) {
                builder.append(':').append(title);
            } else {
                builder.append('#').append(index);
            }
            descriptions.add(builder.toString());
            index++;
        }
        return descriptions;
    }

    private List<String> extractTags(final Object quest) {
        final Object tagsObj = invoke(quest, "getTags");
        if (tagsObj instanceof Collection<?>) {
            return stringifyCollection((Collection<?>) tagsObj);
        }
        if (tagsObj instanceof Map<?, ?>) {
            final Map<?, ?> map = (Map<?, ?>) tagsObj;
            return stringifyCollection(map.keySet());
        }
        final Object fieldValue = readFieldValue(quest, "tags", "tagSet", "tagNames");
        if (fieldValue instanceof Collection<?>) {
            return stringifyCollection((Collection<?>) fieldValue);
        }
        if (fieldValue != null && fieldValue.getClass().isArray()) {
            return stringifyCollection(toCollection(fieldValue));
        }
        return List.of();
    }

    private String extractIcon(final Object quest) {
        final Object iconObj = invoke(quest, "getIcon");
        if (iconObj != null) {
            final Object asString = invoke(iconObj, "toString");
            if (asString != null && !Objects.toString(asString, "").isBlank()) {
                return Objects.toString(asString, "");
            }
            return iconObj.toString();
        }
        final Object fieldValue = readFieldValue(quest, "icon");
        return fieldValue != null ? fieldValue.toString() : null;
    }

    private Collection<?> invokeCollectionResult(final Object target, final String... methodNames) {
        for (final String name : methodNames) {
            final Object value = invoke(target, name);
            if (value instanceof Collection<?>) {
                return (Collection<?>) value;
            }
            if (value instanceof Map<?, ?>) {
                return ((Map<?, ?>) value).values();
            }
            if (value != null && value.getClass().isArray()) {
                return toCollection(value);
            }
        }
        return null;
    }

    private Object invoke(final Object target, final String methodName) {
        if (target == null || methodName == null) {
            return null;
        }
        try {
            final Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (final NoSuchMethodException ignored) {
            // Ignore and return null.
        } catch (final IllegalAccessException | InvocationTargetException exception) {
            logger.log(Level.FINEST,
                    "Reflexionsaufruf fehlgeschlagen: " + methodName + " auf " + target.getClass().getName(),
                    exception);
        }
        return null;
    }

    private Object readFieldValue(final Object target, final String... fieldNames) {
        if (target == null || fieldNames == null) {
            return null;
        }
        for (final String fieldName : fieldNames) {
            if (fieldName == null) {
                continue;
            }
            try {
                final Field field = target.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                final Object value = field.get(target);
                if (value != null) {
                    return value;
                }
            } catch (final ReflectiveOperationException ignored) {
                // Try next field name.
            }
        }
        return null;
    }

    private List<String> stringifyCollection(final Collection<?> collection) {
        if (collection == null || collection.isEmpty()) {
            return List.of();
        }
        final List<String> values = new ArrayList<>();
        for (final Object element : collection) {
            final String value = Objects.toString(element, null);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private Collection<?> toCollection(final Object arrayObject) {
        if (arrayObject == null || !arrayObject.getClass().isArray()) {
            return List.of();
        }
        final int length = Array.getLength(arrayObject);
        final List<Object> list = new ArrayList<>(length);
        for (int i = 0; i < length; i++) {
            list.add(Array.get(arrayObject, i));
        }
        return list;
    }

    private String extractString(final Object target, final String... methodNames) {
        if (target == null || methodNames == null) {
            return null;
        }
        for (final String methodName : methodNames) {
            final Object value = invoke(target, methodName);
            final String stringValue = asString(value);
            if (stringValue != null && !stringValue.isBlank()) {
                return stringValue;
            }
        }
        return null;
    }

    private static String componentToString(final Object component) {
        if (component == null) {
            return null;
        }
        if (component instanceof CharSequence) {
            final String value = component.toString();
            return value.isBlank() ? null : value;
        }
        try {
            final Method getString = component.getClass().getMethod("getString");
            getString.setAccessible(true);
            final Object value = getString.invoke(component);
            if (value instanceof String) {
                final String stringValue = (String) value;
                if (!stringValue.isBlank()) {
                    return stringValue;
                }
            }
        } catch (final ReflectiveOperationException ignored) {
            // Fallback to toString() below.
        }
        final String fallback = component.toString();
        return fallback.isBlank() ? null : fallback;
    }

    private static String safeSegment(final String value) {
        if (value == null) {
            return "segment";
        }
        final String normalized = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_");
        final String trimmed = normalized.replaceAll("_+", "_");
        final String withoutEdgeUnderscore = trimmed.replaceAll("^_+|_+$", "");
        return withoutEdgeUnderscore.isEmpty() ? "segment" : withoutEdgeUnderscore;
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
}
