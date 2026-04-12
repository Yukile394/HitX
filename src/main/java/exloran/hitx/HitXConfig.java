package exloran.hitx;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "hitx")
public class HitXConfig implements ConfigData {
    // Mevcut diğer değişkenlerin...
    public int keyHitboxes = -1;
    public int keyAura = -1;
    public int keyTrigger = -1;
    
    // Hatanın sebebi olan eksik değişken:
    public boolean fakeHitbox = false; 

    // Diğer ayarların (xzExpand, yExpand vb.) burada kalmaya devam etsin
    public float xzExpand = 1.0f;
    public float yExpand = 1.0f;
    public float yOffset = 0.0f;
}
