package exloran.hitx;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "hitx")
public class HitXConfig implements ConfigData {
    public boolean particleOn = true;
    public int hudX = 50;
    public int hudY = 50;
    public int hudScale = 100;

    @ConfigEntry.Gui.CollapsibleObject
    public Visuals visuals = new Visuals();

    public static class Visuals {
        public boolean sabitBar = false;
        public int barColor = 0xFFFFFFFF;
    }
}
