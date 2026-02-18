package com.exloran.hitx;

import me.shedaniel.autoconfig.ConfigData;
import me.shedaniel.autoconfig.annotation.Config;

@Config(name = "hitx")
public class HitXConfig implements ConfigData {

    // Mod açık / kapalı
    public boolean enabled = true;

    // X boyutu
    public int size = 6;

    // X süresi (tick)
    public int duration = 8;

    // X rengi (white, red, green, vb.)
    public String color = "white";

    // Hasar alınca kırmızıya dönsün mü
    public boolean damageColor = true;

    // Fade efekti
    public boolean fade = true;
}
