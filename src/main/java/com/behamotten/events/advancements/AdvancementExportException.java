package com.behamotten.events.advancements;

/**
 * Exception raised when the advancement export cannot be completed.
 */
public final class AdvancementExportException extends Exception {
    private static final long serialVersionUID = 1L;

    public AdvancementExportException(final String message) {
        super(message);
    }

    public AdvancementExportException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
