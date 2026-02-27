package com.uxplima.abuselogger.managers;

import com.uxplima.abuselogger.AbuseLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class LoggerManager {

    private final AbuseLogger plugin;
    private final File logFile;
    private final ExecutorService executor;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy - HH:mm:ss");

    public LoggerManager(AbuseLogger plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "log.txt");
        this.executor = Executors.newSingleThreadExecutor();

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
    }

    public void log(String playerName, String claimName, String action, double x, double y, double z) {
        String date = dateFormat.format(new Date());
        String format = plugin.getConfig().getString("messages.log_format",
                "[{date}] {player} isimli oyuncu {claim} isimli arazinin etrafında {action}. Koordinatlar: {x}, {y}, {z}");

        String entry = format
                .replace("{date}", date)
                .replace("{player}", playerName)
                .replace("{claim}", claimName)
                .replace("{action}", action)
                .replace("{x}", String.valueOf((int) x))
                .replace("{y}", String.valueOf((int) y))
                .replace("{z}", String.valueOf((int) z));

        executor.execute(() -> {
            try (PrintWriter out = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8)))) {
                out.println(entry);
            } catch (IOException e) {
                plugin.getLogger().severe("log.txt dosyasına yazılamadı: " + e.getMessage());
            }
        });
    }

    public CompletableFuture<List<String>> getLastEntries(String playerName, int count) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> results = new ArrayList<>();
            if (!logFile.exists())
                return results;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8))) {
                List<String> allLines = new ArrayList<>();
                String logFormat = plugin.getConfig().getString("messages.log_format",
                        "[{date}] {player} isimli oyuncu {claim} isimli arazinin etrafında {action}. Koordinatlar: {x}, {y}, {z}");

                // Determine the prefix/suffix around {player} in the log_format
                // Default: "[{date}] {player} isimli oyuncu"
                // We'll search for things like "] PlayerX isimli oyuncu"

                String playerPart = "{player}";
                int playerIndex = logFormat.indexOf(playerPart);

                String suffix = "";
                if (playerIndex != -1 && playerIndex + playerPart.length() < logFormat.length()) {
                    String afterPlayer = logFormat.substring(playerIndex + playerPart.length());
                    // Take the first few fixed characters after {player} to act as an anchor
                    // e.g., " isimli oyuncu"
                    int endOfFixed = afterPlayer.indexOf("{");
                    if (endOfFixed != -1) {
                        suffix = afterPlayer.substring(0, endOfFixed);
                    } else {
                        suffix = afterPlayer;
                    }
                }

                String searchString = playerName + suffix;

                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(searchString)) {
                        allLines.add(line);
                    }
                }

                int size = allLines.size();
                for (int i = Math.max(0, size - count); i < size; i++) {
                    results.add(allLines.get(i));
                }
            } catch (IOException e) {
                plugin.getLogger().severe("Log dosyası okunurken hata: " + e.getMessage());
            }
            return results;
        }, executor);
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
        }
    }
}
