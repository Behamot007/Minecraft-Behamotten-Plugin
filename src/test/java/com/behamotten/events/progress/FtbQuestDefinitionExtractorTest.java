package com.behamotten.events.progress;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public final class FtbQuestDefinitionExtractorTest {

    public void run() {
        try {
            extractSingleQuestChapter();
        } catch (final IOException exception) {
            throw new AssertionError("I/O failure during extractor test", exception);
        }
    }

    private void extractSingleQuestChapter() throws IOException {
        final Path tempDirectory = Files.createTempDirectory("ftbquests-test");
        final Path questsDirectory = tempDirectory.resolve("config/ftbquests/quests");
        Files.createDirectories(questsDirectory);
        final Path questFile = questsDirectory.resolve("chapter_one.snbt");
        final String snbt = "{" +
                "id: \"ChapterOne\"," +
                "title: \"Erste Schritte\"," +
                "description: [\"Willkommen\", \"Beginne dein Abenteuer\"]," +
                "quests: [{id: \"quest_start\", title: \"Holz sammeln\", description: [\"Sammle Holz\"]," +
                "icon: {item: \"minecraft:oak_log\"}, tasks: [{type: \"item\", item: \"minecraft:oak_log\"}]}]" +
                "}";
        Files.writeString(questFile, snbt, StandardCharsets.UTF_8);

        final FtbQuestDefinitionExtractor extractor = new FtbQuestDefinitionExtractor(Logger.getAnonymousLogger());
        final FtbQuestDefinitionExtractor.QuestExtractionResult result = extractor.extract(questsDirectory);
        if (result.isEmpty()) {
            throw new AssertionError("Extractor should produce quest definitions");
        }
        final Map<String, Object> definitionDocument = result.toDefinitionDocument(
                Instant.parse("2024-01-01T00:00:00Z"), "config/ftbquests/quests");
        final List<?> quests = castToList(definitionDocument.get("quests"));
        if (quests.size() != 1) {
            throw new AssertionError("Exactly one quest expected in the test export");
        }
        final Map<?, ?> quest = castToMap(quests.get(0));
        assertEquals("ftbquests:chapterone/quest_start", quest.get("id"), "Quest identifier should be normalized");
        assertEquals("Holz sammeln", quest.get("name"), "Quest title should be propagated");
        assertEquals("Erste Schritte", quest.get("chapter"), "Chapter title should be used as chapter field");
        final Map<?, ?> attributes = castToMap(quest.get("attributes"));
        if (!attributes.containsKey("rawData")) {
            throw new AssertionError("Raw quest data attribute must be present");
        }
    }

    @SuppressWarnings("unchecked")
    private List<?> castToList(final Object value) {
        if (!(value instanceof List<?>)) {
            throw new AssertionError("Expected list but received: " + value);
        }
        return (List<?>) value;
    }

    @SuppressWarnings("unchecked")
    private Map<?, ?> castToMap(final Object value) {
        if (!(value instanceof Map<?, ?>)) {
            throw new AssertionError("Expected map but received: " + value);
        }
        return (Map<?, ?>) value;
    }

    private void assertEquals(final Object expected, final Object actual, final String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " (expected=" + expected + ", actual=" + actual + ")");
        }
    }
}
