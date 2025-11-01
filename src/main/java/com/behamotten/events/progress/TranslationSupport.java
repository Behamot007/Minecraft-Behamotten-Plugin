package com.behamotten.events.progress;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility helpers for rendering adventure components and plain strings into translation entries.
 */
final class TranslationSupport {
    private static final Locale LOCALE_DE = Locale.forLanguageTag("de-DE");
    private static final Locale LOCALE_EN = Locale.forLanguageTag("en-US");

    private TranslationSupport() {
    }

    static Translation fromComponent(final Object component, final Logger logger) {
        if (component == null) {
            return Translation.empty();
        }
        if (!isAdventureComponent(component)) {
            return Translation.fromString(component.toString());
        }
        final Translation translation = Translation.empty();
        translation.setTranslationKey(extractTranslationKey(component, logger));
        translation.setGerman(renderComponent(component, LOCALE_DE, logger));
        translation.setEnglish(renderComponent(component, LOCALE_EN, logger));
        translation.ensureFallback(renderComponent(component, null, logger));
        return translation;
    }

    static Translation fromString(final String value) {
        return Translation.fromString(value);
    }

    static Translation fromNullableString(final String value) {
        if (value == null) {
            return Translation.empty();
        }
        return Translation.fromString(value);
    }

    private static boolean isAdventureComponent(final Object component) {
        try {
            final Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component",
                    false, component.getClass().getClassLoader());
            return componentClass.isInstance(component);
        } catch (final ClassNotFoundException ignored) {
            return false;
        }
    }

    private static String renderComponent(final Object component, final Locale locale, final Logger logger) {
        try {
            final Class<?> componentClass = Class.forName("net.kyori.adventure.text.Component");
            if (!componentClass.isInstance(component)) {
                return component.toString();
            }
            Object current = component;
            if (locale != null) {
                final Class<?> translatorClass = Class.forName("net.kyori.adventure.translation.GlobalTranslator");
                final Method renderMethod = translatorClass.getMethod("render", componentClass, Locale.class);
                final Object rendered = renderMethod.invoke(null, component, locale);
                if (rendered != null && componentClass.isInstance(rendered)) {
                    current = rendered;
                }
            }
            final Class<?> serializerClass = Class
                    .forName("net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer");
            final Method plainTextMethod = serializerClass.getMethod("plainText");
            final Object serializer = plainTextMethod.invoke(null);
            if (serializer != null) {
                final Method serializeMethod = serializerClass.getMethod("serialize", componentClass);
                final Object result = serializeMethod.invoke(serializer, current);
                return result != null ? result.toString() : null;
            }
        } catch (final Throwable throwable) {
            if (logger != null) {
                logger.log(Level.FINEST, "Konnte Component nicht rendern.", throwable);
            }
        }
        return component.toString();
    }

    private static String extractTranslationKey(final Object component, final Logger logger) {
        final String[] methodNames = { "key", "getKey", "translationKey", "getTranslationKey" };
        for (final String methodName : methodNames) {
            try {
                final Method method = component.getClass().getMethod(methodName);
                final Object value = method.invoke(component);
                if (value instanceof CharSequence) {
                    final String key = value.toString().trim();
                    if (!key.isEmpty()) {
                        return key;
                    }
                }
            } catch (final ReflectiveOperationException ignored) {
                // Try next method name.
            } catch (final Throwable throwable) {
                if (logger != null) {
                    logger.log(Level.FINEST, "Konnte Übersetzungsschlüssel nicht extrahieren.", throwable);
                }
            }
        }
        try {
            final Class<?> translatableClass = Class.forName("net.kyori.adventure.text.TranslatableComponent");
            if (translatableClass.isInstance(component)) {
                final Method keyMethod = translatableClass.getMethod("key");
                final Object value = keyMethod.invoke(component);
                if (value instanceof CharSequence) {
                    final String key = value.toString().trim();
                    if (!key.isEmpty()) {
                        return key;
                    }
                }
            }
        } catch (final Throwable ignored) {
            // Ignored, best-effort only.
        }
        return null;
    }

    /**
     * Represents a localized value pair used for the translation files.
     */
    static final class Translation {
        private String translationKey;
        private String german;
        private String english;
        private String fallback;

        private Translation() {
        }

        static Translation empty() {
            return new Translation();
        }

        static Translation fromString(final String value) {
            final Translation translation = new Translation();
            final String normalized = normalize(value);
            translation.german = normalized;
            translation.english = normalized;
            translation.fallback = normalized;
            return translation;
        }

        private static String normalize(final String value) {
            if (value == null) {
                return null;
            }
            final String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }

        private static String firstNonBlank(final String... values) {
            if (values == null) {
                return null;
            }
            for (final String value : values) {
                final String normalized = normalize(value);
                if (normalized != null) {
                    return normalized;
                }
            }
            return null;
        }

        void setTranslationKey(final String key) {
            final String normalized = normalize(key);
            if (normalized != null) {
                this.translationKey = normalized;
            }
        }

        void setGerman(final String value) {
            this.german = normalize(value);
        }

        void setEnglish(final String value) {
            this.english = normalize(value);
        }

        void ensureFallback(final String value) {
            final String normalized = normalize(value);
            if (normalized == null) {
                return;
            }
            if (this.german == null) {
                this.german = normalized;
            }
            if (this.english == null) {
                this.english = normalized;
            }
            if (this.fallback == null) {
                this.fallback = normalized;
            }
        }

        void mergeMissing(final Translation other) {
            if (other == null) {
                return;
            }
            if (this.translationKey == null) {
                this.translationKey = other.translationKey;
            }
            if (this.german == null) {
                this.german = other.german;
            }
            if (this.english == null) {
                this.english = other.english;
            }
            if (this.fallback == null) {
                this.fallback = other.fallback;
            }
        }

        boolean hasText() {
            return firstNonBlank(german, english, fallback) != null;
        }

        String determineId(final String fallbackId) {
            final String resolvedFallback = normalize(fallbackId);
            return translationKey != null ? translationKey : resolvedFallback;
        }

        String germanOr(final String alternative) {
            return firstNonBlank(german, alternative, fallback, english);
        }

        String englishOr(final String alternative) {
            return firstNonBlank(english, alternative, fallback, german);
        }

        String fallbackOr(final String alternative) {
            return firstNonBlank(fallback, alternative, english, german);
        }

        Map<String, String> toMap() {
            final Map<String, String> values = new LinkedHashMap<>();
            if (german != null) {
                values.put("de", german);
            }
            if (english != null) {
                values.put("en", english);
            }
            return values;
        }

        String getTranslationKey() {
            return translationKey;
        }
    }
}
