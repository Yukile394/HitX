package exloran.hitx;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "hitx")
public class HitXConfig implements ConfigData {
    // Mevcut ayarların (xzExpand vb.) altına şunları ekle:
    public int keyHitboxes = -1; // GLFW_KEY_UNKNOWN
    public int keyAura = -1;
    public int keyTrigger = -1;
    
    // Diğer ayarların burada kalmaya devam etsin...
    public float xzExpand = 1.0f;
    public float yExpand = 1.0f;
    public float yOffset = 0.0f;
}
