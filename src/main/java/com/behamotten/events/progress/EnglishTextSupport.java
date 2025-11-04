package com.behamotten.events.progress;

import java.lang.reflect.Method;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Utility helpers that derive English text representations from Adventure components or plain
 * string values. The helpers focus solely on the English locale because the plugin only exports
 * English resource bundles now.
 */
final class EnglishTextSupport {
    private static final Locale LOCALE_EN = Locale.forLanguageTag("en-US");

    private EnglishTextSupport() {
    }

    /**
     * Creates a {@link ResolvedText} from an Adventure component or arbitrary value.
     *
     * @param component the source object, possibly an Adventure component
     * @param logger    logger used to report failures
     * @return the resolved text information; never {@code null}
     */
    static ResolvedText fromComponent(final Object component, final Logger logger) {
        if (component == null) {
            return ResolvedText.empty();
        }
        if (!isAdventureComponent(component)) {
            return ResolvedText.fromString(component.toString());
        }
        final ResolvedText text = ResolvedText.empty();
        text.setTranslationKey(extractTranslationKey(component, logger));
        text.setEnglish(renderComponent(component, LOCALE_EN, logger));
        text.ensureFallback(renderComponent(component, null, logger));
        return text;
    }

    /**
     * Creates a {@link ResolvedText} from the provided string value. {@code null} values yield an
     * empty resolved text instance.
     */
    static ResolvedText fromNullableString(final String value) {
        if (value == null) {
            return ResolvedText.empty();
        }
        return fromString(value);
    }

    /**
     * Creates a {@link ResolvedText} where English and fallback are both the provided value.
     */
    static ResolvedText fromString(final String value) {
        return ResolvedText.fromString(value);
    }

    private static boolean isAdventureComponent(final Object component) {
        try {
            final Class<?> componentClass = Class
                    .forName("net.kyori.adventure.text.Component", false, component.getClass().getClassLoader());
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
                return result != null ? normalize(result.toString()) : null;
            }
        } catch (final Throwable throwable) {
            if (logger != null) {
                logger.log(Level.SEVERE, "Failed to render Adventure component into English text.", throwable);
            }
        }
        return normalize(component.toString());
    }

    private static String extractTranslationKey(final Object component, final Logger logger) {
        final String[] methodNames = {"key", "getKey", "translationKey", "getTranslationKey"};
        for (final String methodName : methodNames) {
            try {
                final Method method = component.getClass().getMethod(methodName);
                final Object value = method.invoke(component);
                if (value instanceof CharSequence) {
                    final String key = normalize(value.toString());
                    if (key != null) {
                        return key;
                    }
                }
            } catch (final ReflectiveOperationException ignored) {
                // Try next method name.
            } catch (final Throwable throwable) {
                if (logger != null) {
                    logger.log(Level.WARNING, "Failed to extract translation key from Adventure component.", throwable);
                }
            }
        }
        try {
            final Class<?> translatableClass = Class.forName("net.kyori.adventure.text.TranslatableComponent");
            if (translatableClass.isInstance(component)) {
                final Method keyMethod = translatableClass.getMethod("key");
                final Object value = keyMethod.invoke(component);
                if (value instanceof CharSequence) {
                    return normalize(value.toString());
                }
            }
        } catch (final Throwable throwable) {
            if (logger != null) {
                logger.log(Level.FINEST, "TranslatableComponent API unavailable while resolving key.", throwable);
            }
        }
        return null;
    }

    private static String normalize(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Represents the result of resolving English text and optional fallback information.
     */
    static final class ResolvedText {
        private String translationKey;
        private String english;
        private String fallback;

        private ResolvedText() {
        }

        static ResolvedText empty() {
            return new ResolvedText();
        }

        static ResolvedText fromString(final String value) {
            final ResolvedText text = new ResolvedText();
            final String normalized = normalize(value);
            text.english = normalized;
            text.fallback = normalized;
            return text;
        }

        void setTranslationKey(final String key) {
            final String normalized = normalize(key);
            if (normalized != null) {
                this.translationKey = normalized;
            }
        }

        void setEnglish(final String value) {
            this.english = normalize(value);
        }

        void ensureFallback(final String value) {
            final String normalized = normalize(value);
            if (normalized == null) {
                return;
            }
            if (english == null) {
                english = normalized;
            }
            if (fallback == null) {
                fallback = normalized;
            }
        }

        String englishOr(final String alternative) {
            if (english != null) {
                return english;
            }
            if (fallback != null) {
                return fallback;
            }
            return normalize(alternative);
        }

        String fallbackOr(final String alternative) {
            if (fallback != null) {
                return fallback;
            }
            if (english != null) {
                return english;
            }
            return normalize(alternative);
        }

        String getTranslationKey() {
            return translationKey;
        }

        boolean hasEnglishText() {
            return english != null && !english.isBlank();
        }
    }
}

