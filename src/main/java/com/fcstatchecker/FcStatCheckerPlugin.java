package com.fcstatchecker;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.util.ImageUtil;
import java.awt.image.BufferedImage;

@Slf4j
@PluginDescriptor(
        name = "FC Stat Checker",
        description = "Check if Friends Chat members meet a stat requirement",
        tags = {"friends", "chat", "stats", "skill", "check"}
)
public class FcStatCheckerPlugin extends Plugin
{
    @Inject
    private ClientToolbar clientToolbar;

    @Inject
    private FcStatCheckerPanel panel;

    @Inject
    private FcStatCheckerConfig config;

    private NavigationButton navButton;

    @Override
    protected void startUp()
    {
        BufferedImage icon = ImageUtil.loadImageResource(getClass(), "/skill_icons_small/overall.png");

        navButton = NavigationButton.builder()
                .tooltip("FC Stat Checker")
                .icon(icon)
                .priority(10)
                .panel(panel)
                .build();

        clientToolbar.addNavigation(navButton);
        log.info("FC Stat Checker started");
    }

    @Override
    protected void shutDown()
    {
        clientToolbar.removeNavigation(navButton);
        log.info("FC Stat Checker stopped");
    }

    @Provides
    FcStatCheckerConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FcStatCheckerConfig.class);
    }
}