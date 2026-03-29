package com.exloran.hitx;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "hitx")
public class HitXConfig implements ConfigData {

    @ConfigEntry.Category("hud")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public int hudXPercent = 75; // %75 sağda (Kırmızı alan)

    @ConfigEntry.Category("hud")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public int hudYPercent = 45; // %45 yukarda (Kırmızı alan)

    @ConfigEntry.BoundedDiscrete(min = 4, max = 20)
    public int duration = 8;

    @ConfigEntry.BoundedDiscrete(min = 4, max = 30)
    public int size = 8;

    @ConfigEntry.ColorPicker
    public int red = 255;
    
    public int green = 255;
    public int blue = 255;

    @ConfigEntry.BoundedDiscrete(min = 1, max = 3)
    public int effectLevel = 3;
}
