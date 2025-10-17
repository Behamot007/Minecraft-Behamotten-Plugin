package net.minecraft.server.level;

import java.util.Objects;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

/**
 * Simplified player implementation that stores a UUID and profile.
 */
public class ServerPlayer {
    private final UUID uuid;
    private final GameProfile profile;

    public ServerPlayer(final UUID uuid, final GameProfile profile) {
        this.uuid = Objects.requireNonNull(uuid, "uuid");
        this.profile = Objects.requireNonNull(profile, "profile");
    }

    public UUID getUUID() {
        return uuid;
    }

    public GameProfile getGameProfile() {
        return profile;
    }
}
