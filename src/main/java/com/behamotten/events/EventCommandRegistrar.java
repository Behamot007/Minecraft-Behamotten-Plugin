package com.behamotten.events;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Registers and implements the commands that manage event participation.
 */
public final class EventCommandRegistrar implements CommandExecutor, TabCompleter {

    private final JavaPlugin plugin;
    private final EventParticipationData participationData;

    public EventCommandRegistrar(final JavaPlugin plugin, final EventParticipationData participationData) {
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.participationData = Objects.requireNonNull(participationData, "participationData");
    }

    public void registerCommands() {
        register("setevents");
        register("unsetevents");
        register("getalleventuser");
    }

    private void register(final String commandName) {
        final PluginCommand command = plugin.getCommand(commandName);
        if (command == null) {
            plugin.getLogger().severe("Command '" + commandName + "' is not defined in plugin.yml.");
            return;
        }
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        final String name = command.getName().toLowerCase(Locale.ROOT);
        switch (name) {
            case "setevents":
                return handleSetEvents(sender);
            case "unsetevents":
                return handleUnsetEvents(sender);
            case "getalleventuser":
                return handleGetAllEventUser(sender, args);
            default:
                return false;
        }
    }

    private boolean handleSetEvents(final CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern verwendet werden.");
            return true;
        }
        final Player player = (Player) sender;
        final EventParticipationData.ParticipationUpdate update = participationData.addParticipant(player);
        if (update.wasChanged()) {
            sender.sendMessage(ChatColor.GREEN + "Du bist jetzt für Events registriert.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Du warst bereits für Events registriert.");
        }
        warnOnPersistenceFailure(sender, update);
        return true;
    }

    private boolean handleUnsetEvents(final CommandSender sender) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Dieser Befehl kann nur von Spielern verwendet werden.");
            return true;
        }
        final Player player = (Player) sender;
        final EventParticipationData.ParticipationUpdate update =
                participationData.removeParticipant(player.getUniqueId());
        if (update.wasChanged()) {
            sender.sendMessage(ChatColor.GREEN + "Du wurdest von den Event-Teilnehmern entfernt.");
        } else {
            sender.sendMessage(ChatColor.YELLOW + "Du warst nicht für Events registriert.");
        }
        warnOnPersistenceFailure(sender, update);
        return true;
    }

    private boolean handleGetAllEventUser(final CommandSender sender, final String[] args) {
        if (args.length == 0) {
            final List<String> participants = participationData.getParticipantNames();
            if (participants.isEmpty()) {
                sender.sendMessage(ChatColor.RED + "Es sind keine Event-Teilnehmer registriert.");
                return true;
            }
            final String joinedNames = String.join(", ", participants);
            sender.sendMessage(ChatColor.GOLD + "Event-Teilnehmer (" + participants.size() + "): " + ChatColor.YELLOW + joinedNames);
            return true;
        }

        if (args.length >= 1) {
            final String selector = args[0];
            if ("@r".equalsIgnoreCase(selector)) {
                final Optional<String> random = participationData.getRandomParticipantName();
                if (random.isPresent()) {
                    sender.sendMessage(ChatColor.GOLD + "Zufällig ausgewählter Teilnehmer: " + ChatColor.YELLOW + random.get());
                } else {
                    sender.sendMessage(ChatColor.RED + "Es sind keine Event-Teilnehmer registriert.");
                }
                return true;
            }
            sender.sendMessage(ChatColor.RED + "Unbekannter Selektor '" + selector + "'. Verwende @r für eine zufällige Auswahl.");
            return true;
        }

        return true;
    }

    private void warnOnPersistenceFailure(
            final CommandSender sender, final EventParticipationData.ParticipationUpdate update) {
        if (!update.wasPersisted()) {
            sender.sendMessage(ChatColor.RED
                    + "Achtung: Die Teilnehmerdaten konnten nicht gespeichert werden. Bitte informiere einen Administrator.");
        }
    }

    @Override
    public List<String> onTabComplete(final CommandSender sender, final Command command, final String alias, final String[] args) {
        final String name = command.getName().toLowerCase(Locale.ROOT);
        if ("getalleventuser".equals(name) && args.length == 1) {
            return List.of("@r");
        }
        return Collections.emptyList();
    }
}
