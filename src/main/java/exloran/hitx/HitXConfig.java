package exloran.hitx;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "hitx")
public class HitXConfig implements ConfigData {
    public int keyHitboxes = -1;
    public int keyAura = -1;
    public int keyTrigger = -1;
    
    public boolean fakeHitbox = false; 

    public float xzExpand = 1.0f;
    public float yExpand = 1.0f;
    public float yOffset = 0.0f;

    // ── HitColor Ayarları (Otomatik Kaydedilecek) ──
    public boolean hitColorActive = false;
    public int hcRed   = 0;
    public int hcGreen = 180;
    public int hcBlue  = 255;
    public int hcAlpha = 120;
}
