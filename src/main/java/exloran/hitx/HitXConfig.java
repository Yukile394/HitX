package com.exloran.hitx;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "hitx")
public class HitXConfig implements ClientModInitializer {
    // ClientModInitializer eklemesi gerekiyorsa ekle yoksa silebilirsin.
    @ConfigEntry.Category("hud")
    public int hudX = 78;

    @ConfigEntry.Category("hud")
    public int hudY = 40;

    @ConfigEntry.Category("hud")
    @ConfigEntry.BoundedDiscrete(min = 50, max = 200)
    public int hudScale = 100;

    @ConfigEntry.Category("hud")
    public boolean particleOn = true;

    @ConfigEntry.Gui.CollapsibleObject
    public Visuals visuals = new Visuals();

    public static class Visuals {
        public boolean sabitBar = true;
    }
}
