package net.runelite.plugins.prometheusskillexporter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("prometheusskillexporter")
public interface PrometheusSkillExporterConfig extends Config
{
	@ConfigItem(
		keyName = "enableMetricsServer",
		name = "Enable Metrics Server",
		description = "Expose /metrics endpoint for Prometheus to scrape. When enabled, Prometheus should scrape http://your-ip:port/metrics"
	)
	default boolean enableMetricsServer()
	{
		return true;
	}

	@ConfigItem(
		keyName = "metricsServerPort",
		name = "Metrics Server Port",
		description = "Port number to expose metrics on (default: 9091). Make sure this port is open in your firewall. Prometheus will scrape: http://your-computer-ip:this-port/metrics"
	)
	default int metricsServerPort()
	{
		return 9091;
	}

	@ConfigItem(
		keyName = "prometheusEndpoint",
		name = "Push Endpoint URL (Optional)",
		description = "Optional: Also push metrics to external endpoint (e.g., Pushgateway). Leave empty to only use the metrics server. Format: http://server:port/path"
	)
	default String prometheusEndpoint()
	{
		return "";
	}

	@ConfigItem(
		keyName = "exportInterval",
		name = "Export Interval (minutes)",
		description = "How often to update metrics in minutes"
	)
	default int exportInterval()
	{
		return 5;
	}

	@ConfigItem(
		keyName = "testEndpoint",
		name = "Show Metrics URL",
		description = "Click to display the metrics endpoint URL for Prometheus configuration"
	)
	default Runnable testEndpoint()
	{
		return () -> {};
	}
}

