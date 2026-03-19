package com.fcstatchecker;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class FcStatCheckerPluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(FcStatCheckerPlugin.class);
        RuneLite.main(args);
    }
}