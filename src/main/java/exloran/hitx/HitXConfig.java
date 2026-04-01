package com.exloran.hitx;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "hitx")
public class HitXConfig implements ConfigData {

    @ConfigEntry.Category("hud")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public int hudX = 78; // HUD Yatay Konum (%)

    @ConfigEntry.Category("hud")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public int hudY = 40; // HUD Dikey Konum (%)

    @ConfigEntry.Category("hud")
    @ConfigEntry.BoundedDiscrete(min = 50, max = 200)
    public int hudScale = 100; // HUD Boyutu (%)

    @ConfigEntry.Category("hud")
    @ConfigEntry.Gui.Tooltip(count = 1)
    public boolean particleOn = true; // EKLENDİ: Partiküller açık/kapalı

    @ConfigEntry.Gui.CollapsibleObject
    public Visuals visuals = new Visuals();

    public static class Visuals {
        @ConfigEntry.ColorPicker
        public int barColor = 0xFF0000;
        public boolean sabitBar = true; // Kafada sabit dursun mu?
    }
}
