package com.behamotten.events;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Persistent storage for all event participants.
 */
public final class EventParticipationData extends SavedData {
    private static final String DATA_NAME = BehamottenEventsMod.MOD_ID + "_event_participants";
    private static final String TAG_PLAYERS = "players";
    private static final String TAG_UUID = "uuid";
    private static final String TAG_NAME = "name";

    private final Map<UUID, String> participants = new LinkedHashMap<>();

    public static EventParticipationData get(final MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(EventParticipationData::load, EventParticipationData::new, DATA_NAME);
    }

    private EventParticipationData() {
    }

    private static EventParticipationData load(final CompoundTag tag) {
        final EventParticipationData data = new EventParticipationData();
        final ListTag playerList = tag.getList(TAG_PLAYERS, Tag.TAG_COMPOUND);
        for (int i = 0; i < playerList.size(); i++) {
            final CompoundTag entry = playerList.getCompound(i);
            if (entry.hasUUID(TAG_UUID)) {
                final UUID uuid = entry.getUUID(TAG_UUID);
                final String name = entry.getString(TAG_NAME);
                if (!name.isBlank()) {
                    data.participants.put(uuid, name);
                }
            }
        }
        return data;
    }

    public boolean addParticipant(final ServerPlayer player) {
        final UUID uuid = player.getUUID();
        final String name = player.getGameProfile().getName();
        final String previous = participants.put(uuid, name);
        if (previous == null || !previous.equals(name)) {
            setDirty();
            return previous == null;
        }
        return false;
    }

    public boolean removeParticipant(final UUID uuid) {
        final String removed = participants.remove(uuid);
        if (removed != null) {
            setDirty();
            return true;
        }
        return false;
    }

    public boolean isParticipant(final UUID uuid) {
        return participants.containsKey(uuid);
    }

    public List<String> getParticipantNames() {
        return List.copyOf(participants.values());
    }

    public Optional<String> getRandomParticipantName() {
        final List<String> names = new ArrayList<>(participants.values());
        if (names.isEmpty()) {
            return Optional.empty();
        }
        final int index = ThreadLocalRandom.current().nextInt(names.size());
        return Optional.of(names.get(index));
    }

    @Override
    public CompoundTag save(final CompoundTag tag) {
        final ListTag list = new ListTag();
        for (final Map.Entry<UUID, String> entry : participants.entrySet()) {
            final CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID(TAG_UUID, entry.getKey());
            playerTag.putString(TAG_NAME, entry.getValue());
            list.add(playerTag);
        }
        tag.put(TAG_PLAYERS, list);
        return tag;
    }
}
