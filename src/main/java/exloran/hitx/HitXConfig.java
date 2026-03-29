package com.exloran.hitx;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "hitx")
public class HitXConfig implements ConfigData {

    @ConfigEntry.Category("hud")
    public int hudXPercent = 78; // Sağ tarafa yaslar (Kırmızı alan)

    @ConfigEntry.Category("hud")
    public int hudYPercent = 40; // Orta-üst kısma koyar (Kırmızı alan)

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
