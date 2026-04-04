package com.furkan.abuselogger.managers;

import com.furkan.abuselogger.AbuseLogger;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class AlertManager {

    private final AbuseLogger plugin;
    private final File dataFile;
    private final Set<UUID> disabledAlerts = new HashSet<>();

    public AlertManager(AbuseLogger plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "alerts_disabled.yml");
        load();
    }

    public void load() {
        if (!dataFile.exists())
            return;
        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        disabledAlerts.clear();
        for (String uuidStr : config.getStringList("disabled")) {
            try {
                disabledAlerts.add(UUID.fromString(uuidStr));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        FileConfiguration config = new YamlConfiguration();
        config.set("disabled", disabledAlerts.stream().map(UUID::toString).collect(Collectors.toList()));
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().severe("Alert verileri kaydedilemedi: " + e.getMessage());
        }
    }

    public boolean isAlertsEnabled(UUID uuid) {
        return !disabledAlerts.contains(uuid);
    }

    public void toggleAlerts(UUID uuid) {
        if (disabledAlerts.contains(uuid)) {
            disabledAlerts.remove(uuid);
        } else {
            disabledAlerts.add(uuid);
        }
        save();
    }
}
