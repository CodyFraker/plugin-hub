package net.runelite.plugins.prometheusskillexporter;

import com.google.inject.Provides;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import com.sun.net.httpserver.HttpServer;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@PluginDescriptor(
	name = "Prometheus Skill Exporter"
)
public class PrometheusSkillExporterPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private PrometheusSkillExporterConfig config;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private ScheduledExecutorService executor;

	@Inject
	private ConfigManager configManager;

	private ScheduledFuture<?> scheduledTask;
	private HttpServer metricsServer;
	private final AtomicReference<String> currentMetrics = new AtomicReference<>("# No metrics available yet\n");
	private static final MediaType TEXT_PLAIN = MediaType.parse("text/plain; charset=utf-8");

	@Override
	protected void startUp() throws Exception
	{
		log.debug("Prometheus Skill Exporter started!");
		
		try
		{
			configManager.setConfiguration("prometheusskillexporter", "testEndpoint", (Runnable) this::testEndpoint);
		}
		catch (Exception e)
		{
			log.debug("Could not set button handler", e);
		}
		
		if (config.enableMetricsServer())
		{
			startMetricsServer();
		}
		scheduleExport();
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Prometheus Skill Exporter stopped!");
		if (scheduledTask != null)
		{
			scheduledTask.cancel(false);
		}
		if (metricsServer != null)
		{
			metricsServer.stop(0);
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!event.getGroup().equals("prometheusskillexporter"))
		{
			return;
		}

		if (event.getKey().equals("exportInterval") || event.getKey().equals("prometheusEndpoint"))
		{
			if (scheduledTask != null)
			{
				scheduledTask.cancel(false);
			}
			scheduleExport();
		}

		if (event.getKey().equals("enableMetricsServer") || event.getKey().equals("metricsServerPort"))
		{
			if (metricsServer != null)
			{
				metricsServer.stop(0);
				metricsServer = null;
			}
			if (config.enableMetricsServer())
			{
				startMetricsServer();
			}
		}
	}

	private void startMetricsServer()
	{
		try
		{
			int port = config.metricsServerPort();
			metricsServer = HttpServer.create(new InetSocketAddress(port), 0);
			metricsServer.createContext("/metrics", exchange ->
			{
				if ("GET".equals(exchange.getRequestMethod()))
				{
					String metrics = currentMetrics.get();
					exchange.getResponseHeaders().set("Content-Type", "text/plain; version=0.0.4; charset=utf-8");
					exchange.sendResponseHeaders(200, metrics.getBytes(StandardCharsets.UTF_8).length);
					try (OutputStream os = exchange.getResponseBody())
					{
						os.write(metrics.getBytes(StandardCharsets.UTF_8));
					}
				}
				else
				{
					exchange.sendResponseHeaders(405, -1);
				}
			});
			metricsServer.setExecutor(executor);
			metricsServer.start();
			log.info("Metrics server started on port {}", port);
		}
		catch (IOException e)
		{
			log.error("Failed to start metrics server", e);
		}
	}

	private void scheduleExport()
	{
		int intervalMinutes = config.exportInterval();
		if (intervalMinutes <= 0)
		{
			log.warn("Export interval must be greater than 0, using default of 5 minutes");
			intervalMinutes = 5;
		}

		scheduledTask = executor.scheduleWithFixedDelay(
			() ->
			{
				try
				{
					updateMetrics();
				}
				catch (Exception e)
				{
					log.error("Error updating skill data", e);
				}
			},
			0,
			intervalMinutes,
			TimeUnit.MINUTES
		);
		log.info("Scheduled skill data update every {} minutes", intervalMinutes);
	}

	private void updateMetrics()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			log.debug("Not logged in, skipping metrics update");
			return;
		}

		String playerName = client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
			? client.getLocalPlayer().getName()
			: "unknown";

		StringBuilder metrics = new StringBuilder();
		long timestamp = System.currentTimeMillis() / 1000;

		for (Skill skill : Skill.values())
		{
			if (skill.getName().equals("Overall"))
			{
				continue;
			}

			int experience = client.getSkillExperience(skill);
			int level = client.getRealSkillLevel(skill);
			String skillName = skill.getName().toLowerCase().replace(" ", "_");

			metrics.append(String.format("runelite_skill_experience{skill=\"%s\",player=\"%s\"} %d %d%n", skillName, playerName, experience, timestamp));
			metrics.append(String.format("runelite_skill_level{skill=\"%s\",player=\"%s\"} %d %d%n", skillName, playerName, level, timestamp));
		}

		String metricsBody = metrics.toString();
		currentMetrics.set(metricsBody);

		if (config.enableMetricsServer())
		{
			log.debug("Metrics updated and available at /metrics endpoint");
		}

		String endpoint = config.prometheusEndpoint();
		if (endpoint != null && !endpoint.isEmpty())
		{
			int metricsPort = config.enableMetricsServer() ? config.metricsServerPort() : -1;
			String localIp = getLocalIpAddress();
			String metricsServerUrl = "http://" + localIp + ":" + metricsPort + "/metrics";
			String metricsServerUrlLocalhost = "http://localhost:" + metricsPort + "/metrics";
			
			if (endpoint.equals(metricsServerUrl) || endpoint.equals(metricsServerUrlLocalhost) || 
				endpoint.startsWith("http://127.0.0.1:" + metricsPort) || endpoint.startsWith("http://localhost:" + metricsPort))
			{
				log.debug("Skipping push to metrics server endpoint (use GET /metrics instead)");
				return;
			}
			RequestBody body = RequestBody.create(TEXT_PLAIN, metricsBody);
			Request request = new Request.Builder()
				.url(endpoint)
				.post(body)
				.build();

			okHttpClient.newCall(request).enqueue(new Callback()
			{
				@Override
				public void onFailure(Call call, IOException e)
				{
					log.error("Failed to push skill data to endpoint", e);
				}

				@Override
				public void onResponse(Call call, Response response) throws IOException
				{
					if (!response.isSuccessful())
					{
						log.error("Push endpoint returned error: {} - {}", response.code(), response.message());
					}
					else
					{
						log.debug("Successfully pushed skill data to endpoint");
					}
					response.close();
				}
			});
		}
	}

	@Provides
	PrometheusSkillExporterConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PrometheusSkillExporterConfig.class);
	}

	private void testEndpoint()
	{
		executor.execute(() ->
		{
			if (config.enableMetricsServer())
			{
				int port = config.metricsServerPort();
				String localIp = getLocalIpAddress();
				
				if (metricsServer != null)
				{
					try
					{
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
							String.format("Prometheus Skill Exporter: Metrics endpoint: http://%s:%d/metrics", localIp, port), null);
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
							"Add this to your Prometheus scrape_configs. Make sure port " + port + " is open in your firewall.", null);
					}
					catch (AssertionError e)
					{
						log.warn("Could not send chat message (not on client thread): {}", e.getMessage());
					}
				}
				else
				{
					try
					{
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
							"Prometheus Skill Exporter: Metrics server is not running. Enable it in settings.", null);
					}
					catch (AssertionError e)
					{
						log.warn("Could not send chat message (not on client thread): {}", e.getMessage());
					}
				}
				return;
			}

			String endpoint = config.prometheusEndpoint();
			if (endpoint == null || endpoint.isEmpty())
			{
				try
				{
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Prometheus Skill Exporter: No endpoint configured. Enable metrics server or set push endpoint.", null);
				}
				catch (AssertionError e)
				{
					log.warn("Could not send chat message (not on client thread): {}", e.getMessage());
				}
				return;
			}

			try
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", "Prometheus Skill Exporter: Testing push endpoint connection...", null);
			}
			catch (AssertionError e)
			{
				log.warn("Could not send chat message (not on client thread): {}", e.getMessage());
			}

			String playerName = client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null
				? client.getLocalPlayer().getName()
				: "test_user";

			StringBuilder metrics = new StringBuilder();
			long timestamp = System.currentTimeMillis() / 1000;

			metrics.append(String.format("# Test metrics from RuneLite%n"));
			metrics.append(String.format("runelite_test_metric{player=\"%s\"} 1 %d%n", playerName, timestamp));

			String metricsBody = metrics.toString();
			RequestBody body = RequestBody.create(TEXT_PLAIN, metricsBody);

			Request request = new Request.Builder()
				.url(endpoint)
				.post(body)
				.build();

			okHttpClient.newCall(request).enqueue(new Callback()
			{
				@Override
				public void onFailure(Call call, IOException e)
				{
					try
					{
						client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
							"Prometheus Skill Exporter: Test failed - " + e.getMessage(), null);
					}
					catch (AssertionError ex)
					{
						log.warn("Could not send chat message (not on client thread): {}", ex.getMessage());
					}
					log.error("Endpoint test failed", e);
				}

				@Override
				public void onResponse(Call call, Response response) throws IOException
				{
					if (response.isSuccessful())
					{
						try
						{
							client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
								"Prometheus Skill Exporter: Test successful! Endpoint is reachable.", null);
						}
						catch (AssertionError ex)
						{
							log.warn("Could not send chat message (not on client thread): {}", ex.getMessage());
						}
						log.info("Endpoint test successful");
					}
					else
					{
						try
						{
							client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", 
								"Prometheus Skill Exporter: Test failed - Server returned " + response.code() + " " + response.message(), null);
						}
						catch (AssertionError ex)
						{
							log.warn("Could not send chat message (not on client thread): {}", ex.getMessage());
						}
						log.error("Endpoint test failed: {} - {}", response.code(), response.message());
					}
					response.close();
				}
			});
		});
	}

	private String getLocalIpAddress()
	{
		try
		{
			java.util.Enumeration<java.net.NetworkInterface> interfaces = java.net.NetworkInterface.getNetworkInterfaces();
			while (interfaces.hasMoreElements())
			{
				java.net.NetworkInterface networkInterface = interfaces.nextElement();
				if (networkInterface.isLoopback() || !networkInterface.isUp())
				{
					continue;
				}
				
				java.util.Enumeration<java.net.InetAddress> addresses = networkInterface.getInetAddresses();
				while (addresses.hasMoreElements())
				{
					java.net.InetAddress addr = addresses.nextElement();
					if (!addr.isLoopbackAddress() && addr instanceof java.net.Inet4Address)
					{
						return addr.getHostAddress();
					}
				}
			}
		}
		catch (Exception e)
		{
			log.debug("Failed to get local IP", e);
		}
		return "localhost";
	}
}

