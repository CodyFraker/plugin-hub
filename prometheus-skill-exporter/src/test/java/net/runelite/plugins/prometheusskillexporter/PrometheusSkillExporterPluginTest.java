package net.runelite.plugins.prometheusskillexporter;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PrometheusSkillExporterPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PrometheusSkillExporterPlugin.class);
		RuneLite.main(args);
	}
}

