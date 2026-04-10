package exloran.hitx;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "hitx")
public class HitXConfig implements ConfigData {
    
    // --- HUD AYARLARI ---
    public boolean hudVisible = true;
    public int hudX = 10;
    public int hudY = 10;
    public float hudScale = 1.0f; // HUD Büyüklüğü (1.0 normal)
    
    @ConfigEntry.Gui.Tooltip
    public boolean rgbAnimation = true; // Renk geçişi açık/kapalı
    public float rgbSpeed = 1.0f; // Renk geçiş hızı

    // --- HITBOX AYARLARI ---
    @ConfigEntry.Gui.Tooltip
    public float xzExpand = 2.0402f;
    @ConfigEntry.Gui.Tooltip
    public float yExpand = 1.1305f;

    public boolean fakeHitbox = false; // Fake Hitbox modu
    public boolean particleOn = true;
}
