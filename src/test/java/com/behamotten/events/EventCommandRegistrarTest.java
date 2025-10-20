package com.behamotten.events;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class EventCommandRegistrarTest {

    void run() {
        final TestEnvironment setEventsEnv = createEnvironment();
        setEventsRegistersPlayer(setEventsEnv.participationData(), setEventsEnv.registrar());

        final TestEnvironment consoleEnv = createEnvironment();
        setEventsFromConsoleShowsError(consoleEnv.registrar());

        final TestEnvironment unsetEnv = createEnvironment();
        unsetEventsRemovesPlayer(unsetEnv.participationData(), unsetEnv.registrar());

        final TestEnvironment unsetMissingEnv = createEnvironment();
        unsetEventsWhenNotParticipantShowsWarning(unsetMissingEnv.participationData(), unsetMissingEnv.registrar());

        final TestEnvironment listEnv = createEnvironment();
        listEventsShowsParticipants(listEnv.participationData(), listEnv.registrar());

        final TestEnvironment emptyListEnv = createEnvironment();
        listEventsWhenEmptyShowsMessage(emptyListEnv.registrar());

        final TestEnvironment randomEnv = createEnvironment();
        randomSelectorReturnsParticipant(randomEnv.participationData(), randomEnv.registrar());

        final TestEnvironment invalidSelectorEnv = createEnvironment();
        invalidSelectorShowsError(invalidSelectorEnv.registrar());

        final TestEnvironment saveFailureEnv = createEnvironment();
        setEventsWarnsWhenSaveFails(saveFailureEnv.registrar());

        final TestEnvironment unsetSaveFailureEnv = createEnvironment();
        unsetEventsWarnsWhenSaveFails(unsetSaveFailureEnv.participationData(), unsetSaveFailureEnv.registrar());
    }

    private void setEventsRegistersPlayer(
            final EventParticipationData participationData, final EventCommandRegistrar registrar) {
        final TestPlayer player = new TestPlayer("Tester");
        final boolean handled = registrar.onCommand(player, new Command("setevents"), "setEvents", new String[0]);

        if (!handled) {
            throw new AssertionError("Expected command to be handled");
        }
        assertMessages(player.messages, ChatColor.GREEN + "Du bist jetzt für Events registriert.");
        if (!participationData.isParticipant(player.getUniqueId())) {
            throw new AssertionError("Player should be marked as participant");
        }
    }

    private void setEventsFromConsoleShowsError(final EventCommandRegistrar registrar) {
        final TestSender console = new TestSender();
        final boolean handled = registrar.onCommand(console, new Command("setevents"), "setevents", new String[0]);

        if (!handled) {
            throw new AssertionError("Expected command to be handled");
        }
        assertMessages(console.messages, ChatColor.RED + "Dieser Befehl kann nur von Spielern verwendet werden.");
    }

    private void unsetEventsRemovesPlayer(
            final EventParticipationData participationData, final EventCommandRegistrar registrar) {
        final TestPlayer player = new TestPlayer("Tester");
        participationData.addParticipant(player);

        final boolean handled = registrar.onCommand(player, new Command("unsetevents"), "unsetevents", new String[0]);

        if (!handled) {
            throw new AssertionError("Expected command to be handled");
        }
        assertMessages(player.messages, ChatColor.GREEN + "Du wurdest von den Event-Teilnehmern entfernt.");
        if (!participationData.getParticipantNames().isEmpty()) {
            throw new AssertionError("Participant list should be empty after removal");
        }
    }

    private void unsetEventsWhenNotParticipantShowsWarning(
            final EventParticipationData participationData, final EventCommandRegistrar registrar) {
        final TestPlayer player = new TestPlayer("Tester");

        final boolean handled = registrar.onCommand(player, new Command("unsetevents"), "unsetevents", new String[0]);

        if (!handled) {
            throw new AssertionError("Expected command to be handled");
        }
        assertMessages(player.messages, ChatColor.YELLOW + "Du warst nicht für Events registriert.");
        if (participationData.isParticipant(player.getUniqueId())) {
            throw new AssertionError("Player should not be marked as participant");
        }
    }

    private void listEventsShowsParticipants(
            final EventParticipationData participationData, final EventCommandRegistrar registrar) {
        final TestSender sender = new TestSender();
        participationData.addParticipant(new TestPlayer("Alice"));
        participationData.addParticipant(new TestPlayer("Bob"));

        final boolean handled = registrar.onCommand(sender, new Command("getalleventuser"), "getalleventuser", new String[0]);

        if (!handled) {
            throw new AssertionError("Expected command to be handled");
        }
        assertMessages(sender.messages, ChatColor.GOLD + "Event-Teilnehmer (2): " + ChatColor.YELLOW + "Alice, Bob");
    }

    private void listEventsWhenEmptyShowsMessage(final EventCommandRegistrar registrar) {
        final TestSender sender = new TestSender();

        final boolean handled = registrar.onCommand(sender, new Command("getalleventuser"), "getalleventuser", new String[0]);

        if (!handled) {
            throw new AssertionError("Expected command to be handled");
        }
        assertMessages(sender.messages, ChatColor.RED + "Es sind keine Event-Teilnehmer registriert.");
    }

    private void randomSelectorReturnsParticipant(
            final EventParticipationData participationData, final EventCommandRegistrar registrar) {
        final TestSender sender = new TestSender();
        final TestPlayer participant = new TestPlayer("Alice");
        participationData.addParticipant(participant);

        final boolean handled = registrar.onCommand(
                sender,
                new Command("getalleventuser"),
                "getalleventuser",
                new String[] {"@r"});

        if (!handled) {
            throw new AssertionError("Expected command to be handled");
        }
        if (sender.messages.size() != 1) {
            throw new AssertionError("Expected exactly one message for random selector");
        }
        if (!sender.messages.get(0).contains(participant.getName())) {
            throw new AssertionError("Random selector response should contain participant name");
        }
    }

    private void invalidSelectorShowsError(final EventCommandRegistrar registrar) {
        final TestSender sender = new TestSender();

        final boolean handled = registrar.onCommand(
                sender,
                new Command("getalleventuser"),
                "getalleventuser",
                new String[] {"@a"});

        if (!handled) {
            throw new AssertionError("Expected command to be handled");
        }
        assertMessages(
                sender.messages,
                ChatColor.RED + "Unbekannter Selektor '@a'. Verwende @r für eine zufällige Auswahl.");
    }

    private void setEventsWarnsWhenSaveFails(final EventCommandRegistrar registrar) {
        final TestPlayer player = new TestPlayer("Tester");
        final String property = "behamotten.test.failSave";
        final String previousValue = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            final boolean handled = registrar.onCommand(player, new Command("setevents"), "setevents", new String[0]);

            if (!handled) {
                throw new AssertionError("Expected command to be handled");
            }
            assertMessages(
                    player.messages,
                    ChatColor.GREEN + "Du bist jetzt für Events registriert.",
                    ChatColor.RED
                            + "Achtung: Die Teilnehmerdaten konnten nicht gespeichert werden. Bitte informiere einen Administrator.");
        } finally {
            restoreProperty(property, previousValue);
        }
    }

    private void unsetEventsWarnsWhenSaveFails(
            final EventParticipationData participationData, final EventCommandRegistrar registrar) {
        final TestPlayer player = new TestPlayer("Tester");
        participationData.addParticipant(player);

        final String property = "behamotten.test.failSave";
        final String previousValue = System.getProperty(property);
        System.setProperty(property, "true");
        try {
            final boolean handled = registrar.onCommand(player, new Command("unsetevents"), "unsetevents", new String[0]);

            if (!handled) {
                throw new AssertionError("Expected command to be handled");
            }
            assertMessages(
                    player.messages,
                    ChatColor.GREEN + "Du wurdest von den Event-Teilnehmern entfernt.",
                    ChatColor.RED
                            + "Achtung: Die Teilnehmerdaten konnten nicht gespeichert werden. Bitte informiere einen Administrator.");
        } finally {
            restoreProperty(property, previousValue);
        }
    }

    private void restoreProperty(final String key, final String previousValue) {
        if (previousValue == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, previousValue);
        }
    }

    private TestEnvironment createEnvironment() {
        try {
            final Path directory = Files.createTempDirectory("behamotten-plugin-test");
            final TestJavaPlugin plugin = new TestJavaPlugin(directory.toFile());
            final EventParticipationData participationData = EventParticipationData.load(plugin);
            final EventCommandRegistrar registrar = new EventCommandRegistrar(plugin, participationData);
            return new TestEnvironment(plugin, participationData, registrar);
        } catch (final IOException exception) {
            throw new AssertionError("Failed to create temporary plugin directory", exception);
        }
    }

    private static final class TestEnvironment {
        private final TestJavaPlugin plugin;
        private final EventParticipationData participationData;
        private final EventCommandRegistrar registrar;

        private TestEnvironment(
                final TestJavaPlugin plugin,
                final EventParticipationData participationData,
                final EventCommandRegistrar registrar) {
            this.plugin = plugin;
            this.participationData = participationData;
            this.registrar = registrar;
        }

        private TestJavaPlugin plugin() {
            return plugin;
        }

        private EventParticipationData participationData() {
            return participationData;
        }

        private EventCommandRegistrar registrar() {
            return registrar;
        }
    }

    private void assertMessages(final List<String> actual, final String... expected) {
        if (actual.size() != expected.length) {
            throw new AssertionError(
                    "Expected exactly " + expected.length + " message(s) but got " + actual.size());
        }
        for (int i = 0; i < expected.length; i++) {
            if (!expected[i].equals(actual.get(i))) {
                throw new AssertionError(
                        "Unexpected message at index "
                                + i
                                + ". Expected '"
                                + expected[i]
                                + "' but got '"
                                + actual.get(i)
                                + "'");
            }
        }
    }

    private static final class TestJavaPlugin extends JavaPlugin {
        private final File dataFolder;

        private TestJavaPlugin(final File dataFolder) {
            this.dataFolder = dataFolder;
        }

        @Override
        public File getDataFolder() {
            return dataFolder;
        }
    }

    private static class TestSender implements CommandSender {
        final List<String> messages = new ArrayList<>();

        @Override
        public void sendMessage(final String message) {
            messages.add(message);
        }
    }

    private static final class TestPlayer extends TestSender implements Player {
        private final UUID uuid;
        private final String name;

        private TestPlayer(final String name) {
            this.uuid = UUID.nameUUIDFromBytes(name.toLowerCase(Locale.ROOT).getBytes());
            this.name = name;
        }

        @Override
        public UUID getUniqueId() {
            return uuid;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
