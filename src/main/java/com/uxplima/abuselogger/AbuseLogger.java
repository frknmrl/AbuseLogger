package com.uxplima.abuselogger;

import com.uxplima.abuselogger.commands.CommandManager;
import com.uxplima.abuselogger.listeners.ClaimListener;
import com.uxplima.abuselogger.managers.AlertManager;
import com.uxplima.abuselogger.managers.LoggerManager;
import com.uxplima.abuselogger.managers.WebhookManager;
import com.uxplima.claim.app.facade.ClaimFacade;
import com.uxplima.claim.bukkit.api.UxmClaimBukkitAPI;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.plugin.java.JavaPlugin;

public class AbuseLogger extends JavaPlugin {

    private static AbuseLogger instance;
    private LoggerManager loggerManager;
    private AlertManager alertManager;
    private WebhookManager webhookManager;
    private ClaimFacade claimFacade;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        // Initialize uxmClaims API
        try {
            this.claimFacade = UxmClaimBukkitAPI.getInstance().claimFacade();
        } catch (Exception e) {
            getLogger().severe("uxmClaims API bulunamadı! Eklenti kapatılıyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Initialize Managers
        this.loggerManager = new LoggerManager(this);
        this.alertManager = new AlertManager(this);
        this.webhookManager = new WebhookManager(this);

        // Register Commands
        getServer().getCommandMap().register("abuselogger", new CommandManager(this));

        // Register Listeners
        getServer().getPluginManager().registerEvents(new ClaimListener(this), this);

        getLogger().info("AbuseLogger başarıyla aktif edildi!");
    }

    @Override
    public void onDisable() {
        if (loggerManager != null) {
            loggerManager.shutdown();
        }
    }

    public static AbuseLogger getInstance() {
        return instance;
    }

    public LoggerManager getLoggerManager() {
        return loggerManager;
    }

    public AlertManager getAlertManager() {
        return alertManager;
    }

    public WebhookManager getWebhookManager() {
        return webhookManager;
    }

    public ClaimFacade getClaimFacade() {
        return claimFacade;
    }

    public MiniMessage getMiniMessage() {
        return miniMessage;
    }

    public void reload() {
        reloadConfig();
        alertManager.load();
    }
}
