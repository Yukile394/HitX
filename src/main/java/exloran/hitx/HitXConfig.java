package exloran.hitx;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "hitx")
public class HitXConfig implements ConfigData {
    // Görsel Ayarlar
    public boolean particleOn = true;
    public int hudX = 50;
    public int hudY = 50;
    public int hudScale = 100;

    // --- HITBOX AYARLARI ---
    // @ConfigEntry.BoundedDiscrete ile min ve max limit koyuyoruz (Örn: 100 = %100 normal boy)
    
    @ConfigEntry.Gui.Tooltip
    public float xzExpand = 2.0402f; // Genişlik çarpanı

    @ConfigEntry.Gui.Tooltip
    public float yExpand = 1.1305f;  // Yükseklik çarpanı
}
