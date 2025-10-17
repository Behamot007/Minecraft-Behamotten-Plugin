package com.mojang.authlib;

import java.util.Objects;
import java.util.UUID;

/**
 * Minimal representation of a player profile.
 */
public class GameProfile {
    private final UUID id;
    private final String name;

    public GameProfile(final UUID id, final String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
