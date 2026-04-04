package com.furkan.abuselogger.commands;

import com.furkan.abuselogger.AbuseLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CommandManager extends Command {

    private final AbuseLogger plugin;

    public CommandManager(AbuseLogger plugin) {
        super("abuselogger", "AbuseLogger ana komutu", "/abuselogger", Arrays.asList("alogger", "abuse"));
        this.plugin = plugin;
    }

    @Override
    public boolean execute(@NotNull CommandSender sender, @NotNull String commandLabel, @NotNull String[] args) {
        if (args.length == 0)
            return false;

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("abuse.logger.admin")) {
                    sender.sendMessage(plugin.getMiniMessage()
                            .deserialize(plugin.getConfig().getString("messages.no_permission")));
                    return true;
                }
                plugin.reload();
                sender.sendMessage(
                        plugin.getMiniMessage().deserialize(plugin.getConfig().getString("messages.reload")));
            }
            case "alerts" -> {
                if (!(sender instanceof Player player))
                    return true;
                if (!player.hasPermission("abuse.logger.toggle")) {
                    player.sendMessage(plugin.getMiniMessage()
                            .deserialize(plugin.getConfig().getString("messages.no_permission")));
                    return true;
                }
                plugin.getAlertManager().toggleAlerts(player.getUniqueId());
                boolean enabled = plugin.getAlertManager().isAlertsEnabled(player.getUniqueId());
                String msgKey = enabled ? "messages.alerts_enabled" : "messages.alerts_disabled";
                player.sendMessage(plugin.getMiniMessage().deserialize(plugin.getConfig().getString(msgKey)));
            }
            case "info" -> {
                if (!sender.hasPermission("abuse.logger.admin")) {
                    sender.sendMessage(plugin.getMiniMessage()
                            .deserialize(plugin.getConfig().getString("messages.no_permission")));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(plugin.getMiniMessage()
                            .deserialize(plugin.getConfig().getString("messages.info_usage")));
                    return true;
                }

                String infoType = args[1].toLowerCase();
                String target = args[2];
                int count = plugin.getConfig().getInt("info-count", 5);

                switch (infoType) {
                    case "player" -> handlePlayerInfo(sender, target, count);
                    case "claim" -> handleClaimInfo(sender, target, count);
                    default -> sender.sendMessage(plugin.getMiniMessage()
                            .deserialize(plugin.getConfig().getString("messages.info_usage")));
                }
            }
        }

        return true;
    }

    private void handlePlayerInfo(CommandSender sender, String playerName, int count) {
        plugin.getLoggerManager().getLastEntriesByPlayer(playerName, count).thenAccept(logs -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (logs.isEmpty()) {
                    sender.sendMessage(plugin.getMiniMessage().deserialize(
                            plugin.getConfig().getString("messages.no_records")
                                    .replace("{player}", playerName)));
                    return;
                }
                sender.sendMessage(plugin.getMiniMessage().deserialize(
                        plugin.getConfig().getString("messages.info_header")
                                .replace("{player}", playerName)
                                .replace("{count}", String.valueOf(count))));

                String infoFormat = plugin.getConfig().getString("messages.info_format");
                for (String logLine : logs) {
                    int bracket1 = logLine.indexOf("[");
                    int bracket2 = logLine.indexOf("]");
                    int actionStart = logLine.indexOf("etrafında ") + 10;
                    int coordsStart = logLine.indexOf(". Koordinatlar: ");
                    int claimStart = logLine.indexOf("oyuncu ") + 7;
                    int claimEnd = logLine.indexOf(" isimli arazinin");

                    if (bracket1 != -1 && bracket2 != -1 && actionStart >= 10 && coordsStart != -1
                            && claimStart >= 7 && claimEnd != -1 && claimEnd > claimStart) {

                        String date = logLine.substring(bracket1 + 1, bracket2);
                        String action = logLine.substring(actionStart, coordsStart);
                        String claim = logLine.substring(claimStart, claimEnd);

                        String coordsStr = logLine.substring(coordsStart + 16);
                        String[] splits = coordsStr.split(", ");
                        String x = splits.length > 0 ? splits[0].trim() : "?";
                        String y = splits.length > 1 ? splits[1].trim() : "?";
                        String z = splits.length > 2 ? splits[2].trim() : "?";

                        sender.sendMessage(plugin.getMiniMessage().deserialize(infoFormat
                                .replace("{date}", date)
                                .replace("{action}", action)
                                .replace("{claim}", claim)
                                .replace("{x}", x).replace("{y}", y).replace("{z}", z)));
                    } else {
                        // Fallback
                        sender.sendMessage(Component.text(logLine));
                    }
                }
            });
        });
    }

    private void handleClaimInfo(CommandSender sender, String claimName, int count) {
        plugin.getLoggerManager().getLastEntriesByClaim(claimName, count).thenAccept(logs -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (logs.isEmpty()) {
                    sender.sendMessage(plugin.getMiniMessage().deserialize(
                            plugin.getConfig().getString("messages.no_records_claim")
                                    .replace("{claim}", claimName)));
                    return;
                }
                sender.sendMessage(plugin.getMiniMessage().deserialize(
                        plugin.getConfig().getString("messages.info_claim_header")
                                .replace("{claim}", claimName)
                                .replace("{count}", String.valueOf(count))));

                String infoFormat = plugin.getConfig().getString("messages.info_claim_format");
                for (String logLine : logs) {
                    int bracket1 = logLine.indexOf("[");
                    int bracket2 = logLine.indexOf("]");
                    int playerStart = bracket2 + 2;
                    int playerEnd = logLine.indexOf(" isimli oyuncu");
                    int actionStart = logLine.indexOf("etrafında ") + 10;
                    int coordsStart = logLine.indexOf(". Koordinatlar: ");
                    int claimStart = logLine.indexOf("oyuncu ") + 7;
                    int claimEnd = logLine.indexOf(" isimli arazinin");

                    if (bracket1 != -1 && bracket2 != -1 && playerStart > 1 && playerEnd != -1
                            && actionStart >= 10 && coordsStart != -1 && claimStart >= 7 && claimEnd != -1) {

                        String date = logLine.substring(bracket1 + 1, bracket2);
                        String player = logLine.substring(playerStart, playerEnd);
                        String action = logLine.substring(actionStart, coordsStart);
                        String claim = logLine.substring(claimStart, claimEnd);

                        String coordsStr = logLine.substring(coordsStart + 16);
                        String[] splits = coordsStr.split(", ");
                        String x = splits.length > 0 ? splits[0].trim() : "?";
                        String y = splits.length > 1 ? splits[1].trim() : "?";
                        String z = splits.length > 2 ? splits[2].trim() : "?";

                        sender.sendMessage(plugin.getMiniMessage().deserialize(infoFormat
                                .replace("{date}", date)
                                .replace("{player}", player)
                                .replace("{action}", action)
                                .replace("{claim}", claim)
                                .replace("{x}", x).replace("{y}", y).replace("{z}", z)));
                    } else {
                        // Fallback
                        sender.sendMessage(Component.text(logLine));
                    }
                }
            });
        });
    }

    @Override
    public @NotNull List<String> tabComplete(@NotNull CommandSender sender, @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "alerts", "info").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }

        if (args[0].equalsIgnoreCase("info")) {
            if (args.length == 2) {
                return Arrays.asList("player", "claim").stream()
                        .filter(s -> s.startsWith(args[1].toLowerCase()))
                        .collect(Collectors.toList());
            }
            if (args.length == 3) {
                if (args[1].equalsIgnoreCase("player")) {
                    // Online oyuncular
                    return Bukkit.getOnlinePlayers().stream()
                            .map(Player::getName)
                            .filter(n -> n.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList());
                }
                if (args[1].equalsIgnoreCase("claim")) {
                    return new ArrayList<>();
                }
            }
        }

        return new ArrayList<>();
    }
}
