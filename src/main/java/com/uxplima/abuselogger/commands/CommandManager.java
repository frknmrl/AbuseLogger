package com.uxplima.abuselogger.commands;

import com.uxplima.abuselogger.AbuseLogger;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class CommandManager implements CommandExecutor, TabCompleter {

    private final AbuseLogger plugin;

    public CommandManager(AbuseLogger plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label,
            @NotNull String[] args) {
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
                if (args.length < 2) {
                    sender.sendMessage(plugin.getMiniMessage()
                            .deserialize(plugin.getConfig().getString("messages.player_not_found")));
                    return true;
                }
                String target = args[1];
                plugin.getLoggerManager().getLastEntries(target, 5).thenAccept(logs -> {
                    if (logs.isEmpty()) {
                        sender.sendMessage(plugin.getMiniMessage().deserialize(
                                plugin.getConfig().getString("messages.no_records").replace("{player}", target)));
                        return;
                    }
                    sender.sendMessage(plugin.getMiniMessage().deserialize(
                            plugin.getConfig().getString("messages.info_header").replace("{player}", target)));

                    String infoFormat = plugin.getConfig().getString("messages.info_format");
                    for (String logLine : logs) {
                        // Very basic parsing based on the fixed log_format
                        // [Date] Player isimli oyuncu Claim isimli arazinin etrafında Action.
                        // Koordinatlar: X, Y, Z
                        try {
                            String date = logLine.substring(logLine.indexOf("[") + 1, logLine.indexOf("]"));
                            String rem = logLine.substring(logLine.indexOf("]") + 2);
                            String claim = rem.substring(rem.indexOf("oyuncu ") + 7, rem.indexOf(" isimli arazinin"));
                            String action = rem.substring(rem.indexOf("etrafında ") + 10,
                                    rem.indexOf(". Koordinatlar"));

                            sender.sendMessage(plugin.getMiniMessage().deserialize(infoFormat
                                    .replace("{date}", date)
                                    .replace("{action}", action)
                                    .replace("{claim}", claim)));
                        } catch (Exception e) {
                            // Fallback if line is malformed
                            sender.sendMessage(Component.text(logLine));
                        }
                    }
                });
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias,
            @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(Arrays.asList("reload", "alerts", "info"));
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("info")) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
