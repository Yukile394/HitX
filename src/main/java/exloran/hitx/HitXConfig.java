package com.exloran.hitx;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "hitx")
public class HitXConfig implements ConfigData {

    @ConfigEntry.Category("hud")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public int hudX = 78;

    @ConfigEntry.Category("hud")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public int hudY = 40;

    @ConfigEntry.Category("hud")
    @ConfigEntry.BoundedDiscrete(min = 50, max = 200)
    public int hudScale = 100;

    @ConfigEntry.Category("hud")
    public boolean particleOn = true;

    @ConfigEntry.Gui.CollapsibleObject
    public Visuals visuals = new Visuals();

    public static class Visuals {
        @ConfigEntry.ColorPicker
        public int barColor = 0xFF0000;
        public boolean sabitBar = true;
    }
}
