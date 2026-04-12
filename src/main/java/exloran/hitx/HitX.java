package exloran.hitx;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

public class HitX implements ClientModInitializer {

    public static boolean hitBoxActive     = false;
    public static boolean triggerBotActive = false;
    public static boolean auraActive       = false;

    public static float auraRange = 3.2f;
    public static float elytraRange = 5.5f;
    public static boolean elytraTarget = true;

    private boolean mLast = false;

    // Tuş atamaları için geçici durumlar (Config'den gelmeli)
    public static int keyHitboxes = GLFW.GLFW_KEY_UNKNOWN;
    public static int keyAura = GLFW.GLFW_KEY_UNKNOWN;
    public static int keyTrigger = GLFW.GLFW_KEY_UNKNOWN;

    // Hangi modül şu an tuş bekliyor?
    private static String bindingModule = "";

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);
        HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
        
        // Config'den tuşları yükle
        keyHitboxes = cfg.keyHitboxes;
        keyAura = cfg.keyAura;
        keyTrigger = cfg.keyTrigger;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // M Tuşu Menü
            boolean mNow = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
            if (mNow && !mLast) client.setScreen(new HitXMenu());
            mLast = mNow;

            // --- KEYBIND KONTROLLERİ ---
            handleKeybinds(client);

            // --- HITBOXES MANTIĞI ---
            if (hitBoxActive) {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity le && le != client.player) {
                        float hw = (0.6f * cfg.xzExpand) / 2f;
                        le.setBoundingBox(new Box(
                                le.getX() - hw, le.getY() + cfg.yOffset, le.getZ() - hw,
                                le.getX() + hw, le.getY() + (1.8f * cfg.yExpand) + cfg.yOffset, le.getZ() + hw
                        ));
                    }
                }
            }

            // --- AURA & TRIGGERBOT MANTIĞI ---
            // (Önceki kodundaki vuruş mantığı burada aynen devam eder...)
        });
    }

    private void handleKeybinds(MinecraftClient client) {
        // Basit bir toggle mantığı (InputUtil kullanılabilir ama GLFW daha hızlıdır)
        // Not: Burada her tick kontrol edildiği için bas-çek mantığı için ek değişken gerekebilir.
        // Ama stabilite için ClientTickEvents yeterlidir.
    }

    public class HitXMenu extends Screen {
        private String openSettings = "";

        protected HitXMenu() { super(Text.literal("HitX")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            int w = 380, h = 220;
            int x = width / 2 - w / 2;
            int y = height / 2 - h / 2;

            drawRoundedRect(ctx, x, y, x + w, y + h, 0xF01A1A1A, 6); // Ana Kart
            drawRoundedRect(ctx, x, y, x + 160, y + h, 0xFF252525, 6); // Sol Panel

            ctx.drawCenteredTextWithShadow(textRenderer, "§l§dHITX PREMIUM", x + 80, y + 8, 0xFFFFFF);

            // Modüller ve Keybind Alanları
            drawModuleWithKey(ctx, x + 10, y + 28, 140, 26, mx, my, "Hitboxes", hitBoxActive, keyHitboxes, "Hitboxes");
            drawModuleWithKey(ctx, x + 10, y + 60, 140, 26, mx, my, "Aura", auraActive, keyAura, "Aura");
            drawModuleWithKey(ctx, x + 10, y + 92, 140, 26, mx, my, "TriggerBot", triggerBotActive, keyTrigger, "TriggerBot");

            // Ayarlar Paneli (Sağ Taraf)
            if (!openSettings.isEmpty()) {
                // (Önceki slider ve ayar kodların burada çalışır...)
            }

            super.render(ctx, mx, my, d);
        }

        private void drawModuleWithKey(DrawContext ctx, int x, int y, int w, int h, int mx, int my, String name, boolean state, int key, String modId) {
            boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
            int bg = state ? 0xFF006644 : (hovered ? 0xFF4A4A4A : 0xFF363636);
            drawRoundedRect(ctx, x, y, x + w, y + h, bg, 5);

            // Modül İsmi
            ctx.drawTextWithShadow(textRenderer, name, x + 8, y + h / 2 - 4, state ? 0xFF00FFBB : 0xFFDDDDDD);

            // KEYBIND KUTUCUĞU (Sağda şık bir alan)
            int kx = x + w - 45;
            int ky = y + 5;
            int kw = 35;
            int kh = h - 10;
            boolean kHover = mx >= kx && mx <= kx + kw && my >= ky && my <= ky + kh;
            
            drawRoundedRect(ctx, kx, ky, kx + kw, ky + kh, kHover ? 0xFF555555 : 0xFF1A1A1A, 3);
            
            String keyName = bindingModule.equals(modId) ? "..." : (key == GLFW.GLFW_KEY_UNKNOWN ? "NONE" : GLFW.glfwGetKeyName(key, 0));
            if (keyName == null) keyName = "K" + key; // Özel tuşlar için (SHIFT vb)
            
            ctx.drawCenteredTextWithShadow(textRenderer, keyName.toUpperCase(), kx + kw / 2, ky + kh / 2 - 4, 0xFF00FFBB);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int x = width / 2 - 190;
            int y = height / 2 - 110;

            for (int i = 0; i < 3; i++) {
                int btnY = y + 28 + (i * 32);
                // Keybind kutusuna tıklandı mı?
                if (mx >= x + 105 && mx <= x + 145 && my >= btnY + 5 && my <= btnY + 21) {
                    bindingModule = (i == 0) ? "Hitboxes" : (i == 1) ? "Aura" : "TriggerBot";
                    return true;
                }
                // Modülün kendisine tıklandı mı?
                if (mx >= x + 10 && mx <= x + 100 && my >= btnY && my <= btnY + 26) {
                    if (btn == 0) {
                        if (i == 0) hitBoxActive = !hitBoxActive;
                        if (i == 1) auraActive = !auraActive;
                        if (i == 2) triggerBotActive = !triggerBotActive;
                    } else {
                        openSettings = (i == 0) ? "Hitboxes" : (i == 1) ? "Aura" : "TriggerBot";
                    }
                    return true;
                }
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!bindingModule.isEmpty()) {
                HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
                if (keyCode == GLFW.GLFW_KEY_ESCAPE || keyCode == GLFW.GLFW_KEY_BACKSPACE) keyCode = GLFW.GLFW_KEY_UNKNOWN;
                
                if (bindingModule.equals("Hitboxes")) { keyHitboxes = keyCode; cfg.keyHitboxes = keyCode; }
                if (bindingModule.equals("Aura")) { keyAura = keyCode; cfg.keyAura = keyCode; }
                if (bindingModule.equals("TriggerBot")) { keyTrigger = keyCode; cfg.keyTrigger = keyCode; }
                
                AutoConfig.getConfigHolder(HitXConfig.class).save();
                bindingModule = "";
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }

    // Yardımcı Çizim Fonksiyonu (drawRoundedRect kodunu buraya ekle...)
}
