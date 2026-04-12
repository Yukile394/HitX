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

    public static int keyHitboxes = GLFW.GLFW_KEY_UNKNOWN;
    public static int keyAura = GLFW.GLFW_KEY_UNKNOWN;
    public static int keyTrigger = GLFW.GLFW_KEY_UNKNOWN;

    private static String bindingModule = "";

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);
        HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
        
        // Config'den tuşları güvenli bir şekilde çekiyoruz
        keyHitboxes = cfg.keyHitboxes;
        keyAura = cfg.keyAura;
        keyTrigger = cfg.keyTrigger;

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Menü Tuşu
            boolean mNow = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
            if (mNow && !mLast) client.setScreen(new HitXMenu());
            mLast = mNow;

            // Keybind Toggle Mantığı (Basit sürüm)
            // Not: Gelişmiş bas-çek kontrolü eklenebilir.
        });
    }

    public class HitXMenu extends Screen {
        private String openSettings = "";

        protected HitXMenu() { super(Text.literal("HitX")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            int w = 380, h = 220;
            int x = width / 2 - w / 2;
            int y = height / 2 - h / 2;

            // Ana Kart ve Sol Panel - drawRoundedRect artık sınıf içinde olduğu için hata vermez
            drawRoundedRect(ctx, x, y, x + w, y + h, 0xF01A1A1A, 6);
            drawRoundedRect(ctx, x, y, x + 160, y + h, 0xFF252525, 6);

            ctx.drawCenteredTextWithShadow(textRenderer, "§l§dHITX PREMIUM", x + 80, y + 8, 0xFFFFFF);

            drawModuleWithKey(ctx, x + 10, y + 28, 140, 26, mx, my, "Hitboxes", hitBoxActive, keyHitboxes, "Hitboxes");
            drawModuleWithKey(ctx, x + 10, y + 60, 140, 26, mx, my, "Aura", auraActive, keyAura, "Aura");
            drawModuleWithKey(ctx, x + 10, y + 92, 140, 26, mx, my, "TriggerBot", triggerBotActive, keyTrigger, "TriggerBot");

            super.render(ctx, mx, my, d);
        }

        private void drawModuleWithKey(DrawContext ctx, int x, int y, int w, int h, int mx, int my, String name, boolean state, int key, String modId) {
            boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
            int bg = state ? 0xFF006644 : (hovered ? 0xFF4A4A4A : 0xFF363636);
            drawRoundedRect(ctx, x, y, x + w, y + h, bg, 5);

            ctx.drawTextWithShadow(textRenderer, name, x + 8, y + h / 2 - 4, state ? 0xFF00FFBB : 0xFFDDDDDD);

            // Keybind Kutusu
            int kx = x + w - 45;
            int ky = y + 5;
            int kw = 35;
            int kh = h - 10;
            boolean kHover = mx >= kx && mx <= kx + kw && my >= ky && my <= ky + kh;
            
            drawRoundedRect(ctx, kx, ky, kx + kw, ky + kh, kHover ? 0xFF555555 : 0xFF1A1A1A, 3);
            
            String keyName = bindingModule.equals(modId) ? "..." : (key <= 0 ? "NONE" : GLFW.glfwGetKeyName(key, 0));
            if (keyName == null && key > 0) keyName = "K" + key; 
            
            ctx.drawCenteredTextWithShadow(textRenderer, keyName == null ? "NONE" : keyName.toUpperCase(), kx + kw / 2, ky + kh / 2 - 4, 0xFF00FFBB);
        }

        // --- Hatalı olan metodu buraya, sınıf içine taşıdık ---
        private void drawRoundedRect(DrawContext ctx, int x1, int y1, int x2, int y2, int color, int r) {
            ctx.fill(x1 + r, y1, x2 - r, y2, color);
            ctx.fill(x1, y1 + r, x1 + r, y2 - r, color);
            ctx.fill(x2 - r, y1 + r, x2, y2 - r, color);
            // Köşeler için basit doldurma
            ctx.fill(x1, y1, x1 + r, y1 + r, color);
            ctx.fill(x2 - r, y1, x2, y1 + r, color);
            ctx.fill(x1, y2 - r, x1 + r, y2, color);
            ctx.fill(x2 - r, y2 - r, x2, y2, color);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int x = width / 2 - 190;
            int y = height / 2 - 110;

            for (int i = 0; i < 3; i++) {
                int btnY = y + 28 + (i * 32);
                // Keybind kutusuna tıklama kontrolü (kx koordinatına göre)
                if (mx >= x + 105 && mx <= x + 145 && my >= btnY + 5 && my <= btnY + 21) {
                    bindingModule = (i == 0) ? "Hitboxes" : (i == 1) ? "Aura" : "TriggerBot";
                    return true;
                }
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!bindingModule.isEmpty()) {
                HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
                int assignedKey = (keyCode == GLFW.GLFW_KEY_ESCAPE) ? GLFW.GLFW_KEY_UNKNOWN : keyCode;
                
                if (bindingModule.equals("Hitboxes")) { keyHitboxes = assignedKey; cfg.keyHitboxes = assignedKey; }
                else if (bindingModule.equals("Aura")) { keyAura = assignedKey; cfg.keyAura = assignedKey; }
                else if (bindingModule.equals("TriggerBot")) { keyTrigger = assignedKey; cfg.keyTrigger = assignedKey; }
                
                AutoConfig.getConfigHolder(HitXConfig.class).save();
                bindingModule = "";
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }
    }
}
