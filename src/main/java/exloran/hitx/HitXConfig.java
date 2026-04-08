package exloran.hitx;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "hitx")
public class HitXConfig implements ConfigData {
    public boolean particleOn = true;
    public int hudX = 50;
    public int hudY = 50;
    public int hudScale = 100;
}
