# Prometheus Skill Exporter

A RuneLite plugin that exports all skilling data to Prometheus for visualization in Grafana. Track your skill experience and levels over time with beautiful dashboards.

## Features

- **Automatic Export**: Exports skill experience and levels for all skills at configurable intervals
- **Built-in Metrics Server**: Exposes a `/metrics` endpoint that Prometheus can scrape directly (no external server needed)
- **Optional Push Endpoint**: Also supports pushing to external endpoints like Pushgateway
- **Player Tracking**: Includes player name in metrics labels for multi-player tracking
- **Prometheus Format**: Exports data in standard Prometheus text format

## Configuration

### Plugin Settings

1. **Enable Metrics Server** (default: enabled)
   - When enabled, the plugin exposes a `/metrics` endpoint on the configured port
   - Prometheus can scrape this endpoint directly

2. **Metrics Server Port** (default: 9091)
   - The port number where the metrics endpoint will be available
   - Make sure this port is open in your firewall

3. **Push Endpoint URL** (optional)
   - If you want to also push metrics to an external endpoint (e.g., Pushgateway)
   - Leave empty to only use the built-in metrics server
   - Format: `http://server:port/path`

4. **Export Interval** (default: 5 minutes)
   - How often to update the metrics
   - Lower values provide more granular data but increase network traffic

5. **Show Metrics URL** (button)
   - Click to display the metrics endpoint URL for Prometheus configuration
   - Shows your local IP address and port

## Metrics Exported

The plugin exports the following metrics in Prometheus format:

- `runelite_skill_experience{skill="<skill_name>",player="<player_name>"}` - Current experience for each skill
- `runelite_skill_level{skill="<skill_name>",player="<player_name>"}` - Current level for each skill

Skill names are normalized to lowercase with underscores (e.g., "attack", "magic", "runecraft", "ranged", etc.). The `player` label contains the in-game username, allowing you to track multiple players.

### Example Metrics Output

```
runelite_skill_experience{skill="attack",player="YourUsername"} 13034431 1234567890
runelite_skill_level{skill="attack",player="YourUsername"} 99 1234567890
runelite_skill_experience{skill="strength",player="YourUsername"} 13034431 1234567890
runelite_skill_level{skill="strength",player="YourUsername"} 99 1234567890
```

## Setup Guide

### Option 1: Using Built-in Metrics Server (Recommended)

The plugin exposes its own `/metrics` endpoint that Prometheus can scrape directly.

1. **Configure the Plugin**
   - Enable the plugin in RuneLite
   - Ensure "Enable Metrics Server" is checked
   - Set "Metrics Server Port" (default: 9091)
   - Click "Show Metrics URL" to get your endpoint URL

2. **Configure Prometheus**

   Add this to your `prometheus.yml`:

   ```yaml
   scrape_configs:
     - job_name: 'runelite'
       static_configs:
       - targets: ['YOUR_IP:9091']  # Replace with your computer's IP
         labels:
           source: 'runelite'
       scrape_interval: 30s
   ```

   **Finding Your IP Address:**
   - Windows: Run `ipconfig` and look for IPv4 Address
   - Linux/Mac: Run `ip addr` or `ifconfig` and look for your network interface

3. **Restart Prometheus** or reload the configuration

4. **Verify**
   - Visit `http://YOUR_IP:9091/metrics` in a browser - you should see Prometheus format metrics
   - Check Prometheus targets page to ensure scraping is working
   - Query `runelite_skill_experience` in Prometheus UI

### Option 2: Using Pushgateway

If you prefer to use Pushgateway:

1. **Set up Pushgateway** (see [Prometheus Pushgateway documentation](https://github.com/prometheus/pushgateway))

2. **Configure the Plugin**
   - Enable "Push Endpoint URL"
   - Set URL to: `http://pushgateway-server:9091/metrics/job/runelite`

3. **Configure Prometheus** to scrape Pushgateway:

   ```yaml
   scrape_configs:
     - job_name: 'pushgateway'
       honor_labels: true
       static_configs:
       - targets: ['pushgateway-server:9091']
   ```

## Grafana Setup

1. **Add Prometheus Data Source**
   - In Grafana, go to Configuration → Data Sources
   - Add Prometheus data source pointing to your Prometheus instance

2. **Create Dashboards**

   Here are some useful queries:

   **Experience over time:**
   ```
   runelite_skill_experience{skill="attack",player="YourUsername"}
   ```

   **Experience gained per hour:**
   ```
   rate(runelite_skill_experience{skill="attack",player="YourUsername"}[1h]) * 3600
   ```

   **Total experience across all skills:**
   ```
   sum(runelite_skill_experience{player="YourUsername"})
   ```

   **Level progression:**
   ```
   runelite_skill_level{skill="attack",player="YourUsername"}
   ```

   **Compare multiple players:**
   ```
   runelite_skill_experience{skill="attack"}
   ```

## Development

### Building the Plugin

```bash
cd prometheus-skill-exporter
./gradlew shadowJar
```

The built plugin will be in `build/libs/prometheus-skill-exporter-1.0-SNAPSHOT-all.jar`

### Testing Locally

1. **Using IntelliJ IDEA:**
   - Open the project in IntelliJ
   - Run `PrometheusSkillExporterPluginTest` (add `-ea` to VM options if needed)
   - RuneLite will launch with the plugin loaded

2. **Using Gradle:**
   ```bash
   ./gradlew runPlugin
   ```

### Requirements

- Java 11 or higher
- RuneLite client
- Prometheus server (for scraping metrics)
- Grafana (optional, for visualization)

## Troubleshooting

- **Plugin won't enable**: Make sure the metrics server port is not already in use. Try changing the port in settings.
- **No metrics in Prometheus**: 
  - Check that the plugin is enabled and you're logged into the game
  - Verify the IP address and port in Prometheus config match your RuneLite machine
  - Check firewall settings - port 9091 (or your configured port) must be open
  - Visit `http://YOUR_IP:9091/metrics` directly to see if metrics are available
- **Connection refused**: Ensure the metrics server is enabled and the port is not blocked by firewall
- **Metrics not updating**: Check the export interval setting and make sure you're logged into the game

## Notes

- The plugin only exports data when you are logged into the game
- Failed exports are logged but do not interrupt gameplay
- The metrics server must be accessible from your Prometheus server's network
- Skill data is updated at the configured interval, not in real-time

## License

BSD 2-Clause License
