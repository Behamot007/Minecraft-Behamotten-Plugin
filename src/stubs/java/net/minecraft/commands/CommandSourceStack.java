package net.minecraft.commands;

import java.util.Objects;
import java.util.function.Supplier;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Simplified command source implementation for compilation.
 */
public class CommandSourceStack {
    private final MinecraftServer server;
    private final ServerPlayer player;

    public CommandSourceStack(final MinecraftServer server, final ServerPlayer player) {
        this.server = Objects.requireNonNull(server, "server");
        this.player = Objects.requireNonNull(player, "player");
    }

    public Object getEntity() {
        return player;
    }

    public ServerPlayer getPlayerOrException() {
        return player;
    }

    public MinecraftServer getServer() {
        return server;
    }

    public void sendSuccess(final Supplier<Component> message, final boolean broadcastToOps) {
        message.get();
    }

    public void sendFailure(final Component message) {
        Objects.requireNonNull(message, "message");
    }

    public boolean hasPermission(final int level) {
        return level <= 2;
    }
}
