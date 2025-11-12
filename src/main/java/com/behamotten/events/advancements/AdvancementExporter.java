package com.behamotten.events.advancements;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.logging.Logger;

import org.bukkit.NamespacedKey;
import org.bukkit.advancement.Advancement;
import org.bukkit.advancement.AdvancementDisplay;
import org.bukkit.advancement.AdvancementProgress;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Collects data about all registered advancements and stores them in a single JSON file.
 */
public final class AdvancementExporter {
    private static final DateTimeFormatter ISO_INSTANT = DateTimeFormatter.ISO_INSTANT;

    private final JavaPlugin plugin;
    private final Player player;
    private final Logger logger;
    private final Path outputFile;
    private final Class<?> adventureComponentClass;
    private final PlainTextRenderer plainSerializer;
    private final AdvancementDisplayAdapter displayAdapter;

    public AdvancementExporter(final JavaPlugin plugin, final Player player) {
        this.plugin = plugin;
        this.player = player;
        this.logger = plugin.getLogger();
        this.outputFile = plugin.getDataFolder().toPath().resolve("advancements_export.json");
        this.adventureComponentClass = resolveAdventureComponentClass(this.logger);
        this.plainSerializer = PlainTextRenderer.create(this.logger, this.adventureComponentClass);
        this.displayAdapter = new AdvancementDisplayAdapter(this.logger, this.adventureComponentClass);
    }

    /**
     * Exports all advancements and returns a summary of the operation.
     */
    public ExportResult export() throws AdvancementExportException {
        ensureDataFolder();
        final PlainTextRenderer plainSerializer = this.plainSerializer;
        final List<Map<String, Object>> advancementEntries = new ArrayList<>();
        final Map<String, GroupInfo> groupIndex = new LinkedHashMap<>();
        final Instant generationTime = Instant.now();
        int processedAdvancements = 0;

        final var iterator = plugin.getServer().advancementIterator();
        if (iterator == null) {
            throw new AdvancementExportException("Server returned no advancements to export.");
        }

        while (iterator.hasNext()) {
            final Advancement advancement = iterator.next();
            if (advancement == null) {
                logger.severe("Encountered a null advancement while exporting. Skipping entry.");
                continue;
            }
            processedAdvancements++;
            awardAllCriteria(advancement);
            final String advancementId = advancement.getKey().toString();
            final AdvancementDisplay display = advancement.getDisplay();
            final Object titleComponent = displayAdapter.resolveTitle(display);
            final String title = resolveDisplayText(titleComponent, advancementId, plainSerializer);
            final Object descriptionComponent = displayAdapter.resolveDescription(display);
            final String description = resolveDisplayText(descriptionComponent, "", plainSerializer);
            final GroupInfo groupInfo = resolveGroupInfo(advancement, title, groupIndex, plainSerializer);

            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("advancaments_id", advancementId);
            entry.put("advancaments_title", title);
            if (!description.isBlank()) {
                entry.put("advancaments_description", description);
            }
            entry.put("source_file", buildSourcePath(advancement.getKey()));
            entry.put("dependencies", buildDependencies(advancement));
            entry.put("group_id", groupInfo.id);
            advancementEntries.add(entry);
        }

        final Map<String, Object> document = new LinkedHashMap<>();
        final Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("generated_at", ISO_INSTANT.format(generationTime));
        meta.put("advancaments_found", processedAdvancements);
        meta.put("group_titles_found", groupIndex.size());
        document.put("meta", meta);
        document.put("groups", buildGroupArray(groupIndex));
        document.put("advancaments", advancementEntries);

        try {
            final String json = JsonWriter.stringify(document) + System.lineSeparator();
            Files.writeString(outputFile, json);
        } catch (final IOException exception) {
            throw new AdvancementExportException("Failed to write advancement export file.", exception);
        }

        return new ExportResult(outputFile, advancementEntries.size(), groupIndex.size());
    }

    private void ensureDataFolder() throws AdvancementExportException {
        final Path dataFolder = plugin.getDataFolder().toPath();
        if (Files.exists(dataFolder)) {
            if (!Files.isDirectory(dataFolder)) {
                throw new AdvancementExportException("Plugin data path exists but is not a directory: " + dataFolder);
            }
            return;
        }
        try {
            Files.createDirectories(dataFolder);
        } catch (final IOException exception) {
            throw new AdvancementExportException("Could not create plugin data folder: " + dataFolder, exception);
        }
    }

    private void awardAllCriteria(final Advancement advancement) {
        final AdvancementProgress progress = player.getAdvancementProgress(advancement);
        if (progress == null) {
            logger.severe(() -> "Advancement progress returned null for " + advancement.getKey());
            return;
        }
        final Collection<String> remainingCriteria = progress.getRemainingCriteria();
        if (remainingCriteria == null) {
            logger.severe(() -> "Remaining criteria not available for advancement " + advancement.getKey());
            return;
        }
        for (final String criterion : new ArrayList<>(remainingCriteria)) {
            if (criterion == null || criterion.isBlank()) {
                logger.warning(() -> "Encountered invalid criterion while awarding advancement "
                        + advancement.getKey());
                continue;
            }
            final boolean awarded = progress.awardCriteria(criterion);
            if (!awarded) {
                logger.warning(() -> "Could not award criterion '" + criterion + "' for advancement "
                        + advancement.getKey());
            }
        }
    }

    private GroupInfo resolveGroupInfo(final Advancement advancement, final String fallbackTitle,
            final Map<String, GroupInfo> groupIndex, final PlainTextRenderer plainSerializer) {
        final Advancement root = findRoot(advancement);
        final NamespacedKey groupKey = root != null ? root.getKey() : advancement.getKey();
        final AdvancementDisplay display = root != null ? root.getDisplay() : advancement.getDisplay();
        final String groupId = groupKey.toString();
        final Object groupTitleComponent = displayAdapter.resolveTitle(display);
        final String groupTitle = resolveDisplayText(groupTitleComponent, fallbackTitle, plainSerializer);
        return groupIndex.compute(groupId, (key, existing) -> {
            if (existing == null) {
                return new GroupInfo(groupId, groupTitle);
            }
            if (existing.title.isBlank() && !groupTitle.isBlank()) {
                return new GroupInfo(groupId, groupTitle);
            }
            return existing;
        });
    }

    private Advancement findRoot(final Advancement advancement) {
        Advancement current = advancement;
        int depthGuard = 0;
        while (current != null && current.getParent() != null) {
            current = current.getParent();
            depthGuard++;
            if (depthGuard > 512) {
                logger.severe(() -> "Detected a potential advancement parent cycle involving "
                        + advancement.getKey());
                break;
            }
        }
        return current;
    }

    private String resolveDisplayText(final Object component, final String fallback,
            final PlainTextRenderer serializer) {
        Object resolvedComponent = component;
        if (component != null) {
            resolvedComponent = translate(component);
        }
        final String serialized = serializer.serialize(resolvedComponent);
        final String normalized = serialized != null ? serialized.replace("\r\n", "\n").replace('\r', '\n').trim()
                : "";
        if (!normalized.isEmpty()) {
            return normalized;
        }
        return fallback != null ? fallback : "";
    }

    private Object translate(final Object component) {
        final Class<?> componentClass = this.adventureComponentClass;
        if (componentClass == null || !componentClass.isInstance(component)) {
            return component;
        }
        try {
            final Class<?> translatorClass = Class.forName("net.kyori.adventure.translation.GlobalTranslator");
            final var renderMethod = translatorClass.getMethod("render", componentClass, Locale.class);
            final Object translated = renderMethod.invoke(null, component, Locale.ENGLISH);
            if (componentClass.isInstance(translated)) {
                return translated;
            }
        } catch (final ReflectiveOperationException | RuntimeException exception) {
            logger.fine(() -> "Could not translate component to English: " + exception.getMessage());
        }
        return component;
    }

    private Class<?> resolveAdventureComponentClass(final Logger logger) {
        try {
            return Class.forName("net.kyori.adventure.text.Component");
        } catch (final ClassNotFoundException exception) {
            logger.fine(() -> "Adventure component classes are unavailable: " + exception.getMessage());
            return null;
        }
    }

    private List<String> buildDependencies(final Advancement advancement) {
        final List<String> dependencies = new ArrayList<>(1);
        final Advancement parent = advancement.getParent();
        if (parent != null) {
            dependencies.add(parent.getKey().toString());
        }
        return dependencies;
    }

    private String buildSourcePath(final NamespacedKey key) {
        final String namespace = normalizeSegment(key.getNamespace());
        final String path = normalizeSegment(key.getKey());
        return "data/" + namespace + "/advancements/" + path + ".json";
    }

    private String normalizeSegment(final String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.toLowerCase(Locale.ROOT);
    }

    private List<Map<String, Object>> buildGroupArray(final Map<String, GroupInfo> groupIndex) {
        final List<Map<String, Object>> groups = new ArrayList<>(groupIndex.size());
        for (final GroupInfo info : groupIndex.values()) {
            final Map<String, Object> groupEntry = new LinkedHashMap<>();
            groupEntry.put("id", info.id);
            groupEntry.put("title", info.title.isBlank() ? info.id : info.title);
            groups.add(groupEntry);
        }
        return groups;
    }

    /**
     * Reflection based adapter that resolves advancement display components across API versions.
     */
    private static final class AdvancementDisplayAdapter {
        private final Accessor titleAccessor;
        private final Accessor descriptionAccessor;

        AdvancementDisplayAdapter(final Logger logger, final Class<?> componentClass) {
            this.titleAccessor = new Accessor(logger, componentClass, "title", "title", "getTitle");
            this.descriptionAccessor = new Accessor(logger, componentClass, "description", "description",
                    "getDescription");
        }

        Object resolveTitle(final AdvancementDisplay display) {
            return titleAccessor.resolve(display);
        }

        Object resolveDescription(final AdvancementDisplay display) {
            return descriptionAccessor.resolve(display);
        }

        private static final class Accessor {
            private final Logger logger;
            private final String label;
            private final Method method;
            private boolean missingLogged;
            private boolean invocationLogged;
            private boolean typeLogged;
            private boolean lookupFailureLogged;
            private final Class<?> componentClass;

            Accessor(final Logger logger, final Class<?> componentClass, final String label,
                    final String... candidateNames) {
                this.logger = Objects.requireNonNull(logger, "logger");
                this.label = Objects.requireNonNull(label, "label");
                this.componentClass = componentClass;
                this.method = resolveAccessor(candidateNames);
            }

            private Method resolveAccessor(final String... candidateNames) {
                final Class<AdvancementDisplay> displayClass = AdvancementDisplay.class;
                for (final String name : candidateNames) {
                    try {
                        final Method method = displayClass.getMethod(name);
                        if (componentClass == null || componentClass.isAssignableFrom(method.getReturnType())) {
                            method.setAccessible(true);
                            return method;
                        }
                    } catch (final NoSuchMethodException exception) {
                        // Continue searching the next candidate.
                    } catch (final SecurityException exception) {
                        if (!lookupFailureLogged) {
                            lookupFailureLogged = true;
                            logger.fine(() -> "Could not access advancement display method '" + name + "': "
                                    + exception.getMessage());
                        }
                    } catch (final NoClassDefFoundError error) {
                        if (!lookupFailureLogged) {
                            lookupFailureLogged = true;
                            logger.fine(() -> "Advancement display depends on unavailable Adventure classes: "
                                    + error.getMessage());
                        }
                        return null;
                    }
                }
                return null;
            }

            Object resolve(final AdvancementDisplay display) {
                if (display == null) {
                    return null;
                }
                if (method == null) {
                    if (!missingLogged) {
                        missingLogged = true;
                        logger.warning(() -> "Advancement display does not expose a '" + label
                                + "' accessor. Exported data may be missing text.");
                    }
                    return null;
                }
                try {
                    final Object value = method.invoke(display);
                    if (componentClass == null || componentClass.isInstance(value)) {
                        return value;
                    }
                    if (!typeLogged) {
                        typeLogged = true;
                        logger.warning(() -> "Unexpected return type from advancement display '" + method.getName()
                                + "': " + (value != null ? value.getClass().getName() : "null"));
                    }
                } catch (final ReflectiveOperationException | RuntimeException exception) {
                    if (!invocationLogged) {
                        invocationLogged = true;
                        logger.warning(() -> "Failed to invoke advancement display '" + method.getName() + "': "
                                + exception.getMessage());
                    }
                }
                return null;
            }
        }
    }

    /**
     * Helper that attempts to serialize Adventure components to plain text while gracefully
     * handling environments where the Adventure plain text serializer is not available.
     */
    private static final class PlainTextRenderer {
        private final Logger logger;
        private final Object serializerInstance;
        private final java.lang.reflect.Method serializeMethod;
        private final Class<?> componentClass;

        private PlainTextRenderer(final Logger logger, final Object serializerInstance,
                final java.lang.reflect.Method serializeMethod, final Class<?> componentClass) {
            this.logger = logger;
            this.serializerInstance = serializerInstance;
            this.serializeMethod = serializeMethod;
            this.componentClass = componentClass;
        }

        static PlainTextRenderer create(final Logger logger, final Class<?> componentClass) {
            Objects.requireNonNull(logger, "logger");
            if (componentClass == null) {
                logger.warning(() -> "Adventure component classes are unavailable. Falling back to"
                        + " Component#toString().");
                return new PlainTextRenderer(logger, null, null, null);
            }
            try {
                final Class<?> serializerClass = Class
                        .forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
                final var plainTextMethod = serializerClass.getMethod("plainText");
                final Object instance = plainTextMethod.invoke(null);
                final var serializeMethod = serializerClass.getMethod("serialize", componentClass);
                return new PlainTextRenderer(logger, instance, serializeMethod, componentClass);
            } catch (final ReflectiveOperationException | RuntimeException exception) {
                logger.warning(() -> "Plain text serializer from Adventure is unavailable. Falling back to"
                        + " Component#toString(): " + exception.getMessage());
                return new PlainTextRenderer(logger, null, null, componentClass);
            }
        }

        String serialize(final Object component) {
            if (component == null) {
                return "";
            }
            if (serializerInstance != null && serializeMethod != null && componentClass != null
                    && componentClass.isInstance(component)) {
                try {
                    final Object result = serializeMethod.invoke(serializerInstance, component);
                    if (result instanceof String) {
                        return (String) result;
                    }
                } catch (final ReflectiveOperationException | RuntimeException exception) {
                    logger.fine(() -> "Failed to serialize component using Adventure serializer: "
                            + exception.getMessage());
                }
            }
            final String fallback = component.toString();
            return fallback != null ? fallback : "";
        }
    }

    /**
     * Result summary returned after exporting advancements.
     */
    public static final class ExportResult {
        private final Path outputFile;
        private final int advancementCount;
        private final int groupCount;

        ExportResult(final Path outputFile, final int advancementCount, final int groupCount) {
            this.outputFile = Objects.requireNonNull(outputFile, "outputFile");
            this.advancementCount = advancementCount;
            this.groupCount = groupCount;
        }

        public Path outputFile() {
            return outputFile;
        }

        public int advancementCount() {
            return advancementCount;
        }

        public int groupCount() {
            return groupCount;
        }
    }

    private static final class GroupInfo {
        private final String id;
        private final String title;

        GroupInfo(final String id, final String title) {
            this.id = id;
            this.title = title != null ? title : "";
        }
    }
}
