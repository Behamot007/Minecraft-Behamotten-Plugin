package com.behamotten.events;

/**
 * Simple test runner that executes our manual unit tests without relying on external frameworks.
 */
public final class AllTests {

    private AllTests() {
    }

    public static void main(final String[] args) {
        new EventParticipationDataTest().run();
        new EventCommandRegistrarTest().run();
        System.out.println("All tests passed.");
    }
}
