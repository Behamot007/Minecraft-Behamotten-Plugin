package com.behamotten.events;

import java.util.List;

import com.mojang.brigadier.arguments.StringArgumentType;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Registers and implements the commands that manage event participation.
 */
public final class EventCommandRegistrar {

    @SubscribeEvent
    public void onRegisterCommands(final RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("setEvents")
            .requires(source -> source.getEntity() instanceof ServerPlayer)
            .executes(context -> executeSetEvents(context.getSource())));

        event.getDispatcher().register(Commands.literal("unsetEvents")
            .requires(source -> source.getEntity() instanceof ServerPlayer)
            .executes(context -> executeUnsetEvents(context.getSource())));

        event.getDispatcher().register(Commands.literal("getAllEventUser")
            .requires(source -> source.hasPermission(2))
            .executes(context -> executeListParticipants(context.getSource()))
            .then(Commands.argument("selector", StringArgumentType.word())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("@r"), builder))
                .executes(context -> executeListParticipants(context.getSource(), StringArgumentType.getString(context, "selector")))));
    }

    private int executeSetEvents(final CommandSourceStack source) {
        final ServerPlayer player = source.getPlayerOrException();
        final EventParticipationData data = EventParticipationData.get(source.getServer());
        final boolean added = data.addParticipant(player);
        if (added) {
            source.sendSuccess(() -> Component.translatable("commands.behamotten.set_events.added", player.getGameProfile().getName()), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.behamotten.set_events.already", player.getGameProfile().getName()), false);
        }
        return 1;
    }

    private int executeUnsetEvents(final CommandSourceStack source) {
        final ServerPlayer player = source.getPlayerOrException();
        final EventParticipationData data = EventParticipationData.get(source.getServer());
        final boolean removed = data.removeParticipant(player.getUUID());
        if (removed) {
            source.sendSuccess(() -> Component.translatable("commands.behamotten.unset_events.removed", player.getGameProfile().getName()), false);
        } else {
            source.sendSuccess(() -> Component.translatable("commands.behamotten.unset_events.not_found", player.getGameProfile().getName()), false);
        }
        return removed ? 1 : 0;
    }

    private int executeListParticipants(final CommandSourceStack source) {
        final EventParticipationData data = EventParticipationData.get(source.getServer());
        final List<String> participants = data.getParticipantNames();
        if (participants.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("commands.behamotten.get_all.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("commands.behamotten.get_all.list", String.join(", ", participants), participants.size()), false);
        return participants.size();
    }

    private int executeListParticipants(final CommandSourceStack source, final String selector) {
        if ("@r".equals(selector)) {
            final EventParticipationData data = EventParticipationData.get(source.getServer());
            return data.getRandomParticipantName()
                .map(name -> {
                    source.sendSuccess(() -> Component.translatable("commands.behamotten.get_all.random", name), false);
                    return 1;
                })
                .orElseGet(() -> {
                    source.sendSuccess(() -> Component.translatable("commands.behamotten.get_all.empty"), false);
                    return 0;
                });
        }
        source.sendFailure(Component.translatable("commands.behamotten.get_all.invalid_selector", selector));
        return 0;
    }
}
