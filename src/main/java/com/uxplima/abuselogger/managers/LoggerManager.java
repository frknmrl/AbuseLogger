package com.uxplima.abuselogger.managers;

import com.uxplima.abuselogger.AbuseLogger;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
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
    private final File backupFolder;
    private final ExecutorService executor;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy - HH:mm:ss");
    private final DateTimeFormatter backupMonthFormat = DateTimeFormatter.ofPattern("MM-yyyy");

    private YearMonth currentMonth;

    public LoggerManager(AbuseLogger plugin) {
        this.plugin = plugin;
        this.logFile = new File(plugin.getDataFolder(), "log.txt");
        this.backupFolder = new File(plugin.getDataFolder(), "backup");
        this.executor = Executors.newSingleThreadExecutor();

        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        if (!backupFolder.exists()) {
            backupFolder.mkdirs();
        }

        this.currentMonth = YearMonth.now();
    }

    /**
     * Mevcut ayı kontrol eder; ay değişmişse log.txt'yi backup klasörüne taşır.
     * Bu metot executor thread'i içinde çağrılmalıdır.
     */
    private void checkAndRotate() {
        YearMonth now = YearMonth.now();
        if (now.equals(currentMonth)) {
            return;
        }

        // Ay değişti: eski log'u arşivle
        if (logFile.exists()) {
            String archiveName = "old-log-" + currentMonth.format(backupMonthFormat) + ".txt";
            File archiveFile = new File(backupFolder, archiveName);
            try {
                Files.move(logFile.toPath(), archiveFile.toPath());
                plugin.getLogger().info("Log dosyası arşivlendi: backup/" + archiveName);
            } catch (IOException e) {
                plugin.getLogger().severe("Log dosyası arşivlenemedi: " + e.getMessage());
            }
        }

        currentMonth = now;
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
            checkAndRotate();
            try (PrintWriter out = new PrintWriter(new BufferedWriter(
                    new OutputStreamWriter(new FileOutputStream(logFile, true), StandardCharsets.UTF_8)))) {
                out.println(entry);
            } catch (IOException e) {
                plugin.getLogger().severe("log.txt dosyasına yazılamadı: " + e.getMessage());
            }
        });
    }

    /**
     * Bir oyuncunun son {count} kaydını log.txt üzerinden döndürür.
     */
    public CompletableFuture<List<String>> getLastEntriesByPlayer(String playerName, int count) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> results = new ArrayList<>();
            if (!logFile.exists()) return results;

            String logFormat = plugin.getConfig().getString("messages.log_format",
                    "[{date}] {player} isimli oyuncu {claim} isimli arazinin etrafında {action}. Koordinatlar: {x}, {y}, {z}");

            String playerPart = "{player}";
            int playerIndex = logFormat.indexOf(playerPart);
            String suffix = "";
            if (playerIndex != -1 && playerIndex + playerPart.length() < logFormat.length()) {
                String afterPlayer = logFormat.substring(playerIndex + playerPart.length());
                int endOfFixed = afterPlayer.indexOf("{");
                suffix = endOfFixed != -1 ? afterPlayer.substring(0, endOfFixed) : afterPlayer;
            }

            String searchString = playerName + suffix;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8))) {
                List<String> allLines = new ArrayList<>();
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

    /**
     * Belirtilen arazi ismine ait son {count} kaydı döndürür.
     */
    public CompletableFuture<List<String>> getLastEntriesByClaim(String claimName, int count) {
        return CompletableFuture.supplyAsync(() -> {
            List<String> results = new ArrayList<>();
            if (!logFile.exists()) return results;

            String logFormat = plugin.getConfig().getString("messages.log_format",
                    "[{date}] {player} isimli oyuncu {claim} isimli arazinin etrafında {action}. Koordinatlar: {x}, {y}, {z}");

            // {claim}'in hemen sonrasındaki sabit metni bul (arazi ismi için arama ancore'u)
            String claimPart = "{claim}";
            int claimIndex = logFormat.indexOf(claimPart);
            String claimSuffix = "";
            if (claimIndex != -1 && claimIndex + claimPart.length() < logFormat.length()) {
                String afterClaim = logFormat.substring(claimIndex + claimPart.length());
                int endOfFixed = afterClaim.indexOf("{");
                claimSuffix = endOfFixed != -1 ? afterClaim.substring(0, endOfFixed) : afterClaim;
            }

            String searchString = claimName + claimSuffix;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8))) {
                List<String> allLines = new ArrayList<>();
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
