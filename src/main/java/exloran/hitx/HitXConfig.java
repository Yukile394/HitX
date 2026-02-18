package com.exloran.hitx.config;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "hitx")
public class HitXConfig implements ConfigData {

    public boolean enabled = true;
    public boolean sound = true;
    public boolean comboCounter = true;
    public boolean criticalEffect = true;
    public boolean killEffect = true;

    @ConfigEntry.BoundedDiscrete(min = 4, max = 30)
    public int size = 10;

    @ConfigEntry.BoundedDiscrete(min = 5, max = 20)
    public int duration = 10;

    public ColorMode color = ColorMode.YELLOW;

    public enum ColorMode {
        WHITE,
        YELLOW,
        RED,
        GREEN,
        BLUE
    }
}
