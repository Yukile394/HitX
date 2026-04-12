package exloran.hitx;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;
import me.shedaniel.autoconfig.annotation.ConfigEntry;

@Config(name = "hitx")
public class HitXConfig implements ConfigData {

    // HUD
    public boolean hudVisible   = true;
    public int     hudX         = 10;
    public int     hudY         = 10;
    public float   hudScale     = 1.0f;
    public boolean rgbAnimation = true;
    public float   rgbSpeed     = 1.0f;

    // HitBox
    @ConfigEntry.Gui.Tooltip
    public float xzExpand = 2.0402f;  // XZ genişleme çarpanı

    @ConfigEntry.Gui.Tooltip
    public float yExpand  = 1.1305f;  // Y yükseklik çarpanı

    @ConfigEntry.Gui.Tooltip
    public float yOffset  = 0.0f;     // Yukarı/aşağı kaydırma

    public boolean fakeHitbox = false;
    public boolean particleOn = true;
}
