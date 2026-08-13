package me.neonjava.advancedhomes.telemetry;

import me.neonjava.advancedhomes.AdvancedHomesPlugin;
import org.bukkit.Bukkit;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.logging.Level;

public class TelemetryReporter {
    private final AdvancedHomesPlugin plugin;
    private final String serverId;
    private static final String DEFAULT_ENDPOINT = "http://localhost:3030/api/telemetry/ping";

    public TelemetryReporter(AdvancedHomesPlugin plugin) {
        this.plugin = plugin;
        this.serverId = UUID.nameUUIDFromBytes((Bukkit.getIp() + ":" + Bukkit.getPort() + ":" + Bukkit.getWorldContainer().getAbsolutePath()).getBytes(StandardCharsets.UTF_8)).toString();
    }

    public void start() {
        // Send initial ping asynchronously 5 seconds after boot
        Bukkit.getAsyncScheduler().runDelayed(plugin, (task) -> sendPing(), 5L, java.util.concurrent.TimeUnit.SECONDS);

        // Schedule periodic heartbeat every 5 minutes
        Bukkit.getAsyncScheduler().runAtFixedRate(plugin, (task) -> sendPing(), 5L, 5L, java.util.concurrent.TimeUnit.MINUTES);
    }

    private void sendPing() {
        try {
            URL url = new URL(DEFAULT_ENDPOINT);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);

            String jsonPayload = String.format(
                    "{\"pluginName\":\"%s\",\"pluginVersion\":\"%s\",\"serverId\":\"%s\",\"serverVersion\":\"%s\",\"onlinePlayers\":%d,\"maxPlayers\":%d,\"port\":%d}",
                    plugin.getDescription().getName(),
                    plugin.getDescription().getVersion(),
                    serverId,
                    Bukkit.getVersion().replace("\"", "\\\""),
                    Bukkit.getOnlinePlayers().size(),
                    Bukkit.getMaxPlayers(),
                    Bukkit.getPort()
            );

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                plugin.getLogger().fine("Telemetry heartbeat sent successfully.");
            }
        } catch (Exception e) {
            // Quietly handle connection failures when telemetry server is offline
            plugin.getLogger().log(Level.FINE, "Telemetry ping offline: " + e.getMessage());
        }
    }
}
