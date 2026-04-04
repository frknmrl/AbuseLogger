package com.uxplima.abuselogger.managers;

import com.uxplima.abuselogger.AbuseLogger;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

public class WebhookManager {

    private final AbuseLogger plugin;

    public WebhookManager(AbuseLogger plugin) {
        this.plugin = plugin;
    }

    public void sendWebhook(String player, String claim, String action, String x, String y, String z) {
        if (!plugin.getConfig().getBoolean("webhook.enabled", false)) {
            return;
        }

        String webhookUrl = plugin.getConfig().getString("webhook.url", "");
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equals("YOUR_WEBHOOK_URL_HERE")) {
            return;
        }

        String title = escapeJson(plugin.getConfig().getString("webhook.embed.title", "AbuseLogger"));
        String description = plugin.getConfig().getString("webhook.embed.description", "")
                .replace("{player}", player)
                .replace("{claim}", claim)
                .replace("{action}", action)
                .replace("{x}", x)
                .replace("{y}", y)
                .replace("{z}", z);
        description = escapeJson(description);

        String colorHex = plugin.getConfig().getString("webhook.embed.color", "#CC0000").replace("#", "");
        int color = 13369344; // Default red
        try {
            color = Integer.parseInt(colorHex, 16);
        } catch (NumberFormatException ignored) {
        }

        String thumbnailUrl = plugin.getConfig().getString("webhook.embed.thumbnail-url", "");
        String footer = plugin.getConfig().getString("webhook.embed.footer", "");
        boolean timestamp = plugin.getConfig().getBoolean("webhook.embed.timestamp", false);

        StringBuilder json = new StringBuilder();
        json.append("{\"embeds\": [{");
        json.append("\"title\": \"").append(title).append("\",");
        json.append("\"description\": \"").append(description).append("\",");
        json.append("\"color\": ").append(color);

        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            json.append(",\"thumbnail\": {\"url\": \"").append(escapeJson(thumbnailUrl)).append("\"}");
        }

        if (footer != null && !footer.isEmpty()) {
            json.append(",\"footer\": {\"text\": \"").append(escapeJson(footer)).append("\"}");
        }

        if (timestamp) {
            json.append(",\"timestamp\": \"").append(Instant.now().toString()).append("\"");
        }

        json.append("}]}");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.addRequestProperty("Content-Type", "application/json");
                connection.addRequestProperty("User-Agent", "AbuseLogger");
                connection.setDoOutput(true);
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                try (OutputStream stream = connection.getOutputStream()) {
                    stream.write(json.toString().getBytes(StandardCharsets.UTF_8));
                    stream.flush();
                }

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    plugin.getLogger().warning("Discord webhook hatası: HTTP " + responseCode);
                }

                connection.getInputStream().close();
                connection.disconnect();
            } catch (Exception e) {
                plugin.getLogger().warning("Discord webhook gönderilemedi: " + e.getMessage());
            }
        });
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
