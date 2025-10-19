package com.behamotten.events;

import java.io.File;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

final class EventParticipationDataTest {

    void run() {
        addParticipantDoesNotFailWhenDataFolderLacksParentDirectory();
    }

    private void addParticipantDoesNotFailWhenDataFolderLacksParentDirectory() {
        final JavaPlugin plugin = new RootFolderJavaPlugin();
        final EventParticipationData data = EventParticipationData.load(plugin);
        final Player player = new TestPlayer(UUID.randomUUID(), "UnitTester");

        try {
            final boolean added = data.addParticipant(player);
            if (!added) {
                throw new AssertionError("Expected player to be newly added");
            }
        } catch (final RuntimeException exception) {
            throw new AssertionError("addParticipant should not throw when data folder has no parent", exception);
        }

        if (!data.isParticipant(player.getUniqueId())) {
            throw new AssertionError("player should be persisted as participant");
        }
    }

    private static final class RootFolderJavaPlugin extends JavaPlugin {
        @Override
        public File getDataFolder() {
            return new File("");
        }
    }

    private static final class TestPlayer implements Player {
        private final UUID uuid;
        private final String name;

        private TestPlayer(final UUID uuid, final String name) {
            this.uuid = uuid;
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

        @Override
        public void sendMessage(final String message) {
            // no-op for tests
        }
    }
}
