package exloran.hitx;

import exloran.hitx.listener.OverlayReloadListener;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

public class HitX implements ClientModInitializer {

    // ── Çalışma Zamanı Durumları ─────────────────────────────
    public static boolean hitBoxActive     = false;
    public static boolean triggerBotActive = false;
    public static boolean auraActive       = false;

    // ── İç Değişkenler ───────────────────────────────────────
    private boolean mLast             = false;
    private boolean kHitboxLast       = false;
    private boolean kAuraLast         = false;
    private boolean kTriggerLast      = false;
    private LivingEntity currentAuraTarget = null;

    @Override
    public void onInitializeClient() {
        // Config Kaydı
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        // HitColor'ın dünyada yenilenmesi için listener tetikleyici
        ClientTickEvents.END_WORLD_TICK.register((client) -> {
            OverlayReloadListener.callEvent();
        });

        // Envanter Butonları (Hızlı Zırh & Chest Çekici)
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            int bx = W / 2 + 92;
            int by = H / 2 - 50;

            if (screen instanceof InventoryScreen inv) {
                int id = inv.getScreenHandler().syncId;
                // Bu kısım senin özel iconBtn metodunla çalışır
            }

            if (screen instanceof GenericContainerScreen chest) {
                int id = chest.getScreenHandler().syncId;
                // Hepsini al butonu
            }
        });

        // Ana Döngü (Tick)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long handle = client.getWindow().getHandle();
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            // M Tuşu -> Menü Açma
            boolean mNow = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
            if (mNow && !mLast) client.setScreen(new HitXMenu());
            mLast = mNow;

            // Oyun içindeyken Keybind Kontrolü
            if (client.currentScreen == null) {
                checkKeybinds(handle, cfg);
            }

            // HITBOXES MEKANİĞİ
            if (hitBoxActive) {
                for (Entity e : client.world.getEntities()) {
                    if (!(e instanceof LivingEntity le) || le == client.player) continue;
                    applyCustomHitbox(le, cfg);
                }
            }

            // AURA & TRIGGERBOT MEKANİĞİ
            handleCombat(client, cfg);
        });
    }

    private void checkKeybinds(long handle, HitXConfig cfg) {
        boolean kH = cfg.keyHitboxes != -1 && GLFW.glfwGetKey(handle, cfg.keyHitboxes) == GLFW.GLFW_PRESS;
        boolean kA = cfg.keyAura     != -1 && GLFW.glfwGetKey(handle, cfg.keyAura)     == GLFW.GLFW_PRESS;
        boolean kT = cfg.keyTrigger  != -1 && GLFW.glfwGetKey(handle, cfg.keyTrigger)  == GLFW.GLFW_PRESS;

        if (kH && !kHitboxLast)  hitBoxActive     = !hitBoxActive;
        if (kA && !kAuraLast)    auraActive       = !auraActive;
        if (kT && !kTriggerLast) triggerBotActive = !triggerBotActive;

        kHitboxLast = kH; kAuraLast = kA; kTriggerLast = kT;
    }

    private void applyCustomHitbox(LivingEntity le, HitXConfig cfg) {
        float hw = (0.6f * cfg.xzExpand) / 2f;
        float tall = 1.8f * cfg.yExpand;
        le.setBoundingBox(new Box(
                le.getX() - hw, le.getY() + cfg.yOffset, le.getZ() - hw,
                le.getX() + hw, le.getY() + tall + cfg.yOffset, le.getZ() + hw
        ));
    }

    private void handleCombat(net.minecraft.client.MinecraftClient client, HitXConfig cfg) {
        if (!auraActive && !triggerBotActive) return;
        if (client.player.getAttackCooldownProgress(0.5f) < 1.0f) return;

        LivingEntity target = null;
        if (triggerBotActive && client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit) {
            if (hit.getEntity() instanceof LivingEntity le && le.isAlive()) target = le;
        }

        if (auraActive && target == null) {
            double range = 3.5; // Aura menzili
            for (Entity e : client.world.getEntities()) {
                if (e instanceof LivingEntity le && le != client.player && le.isAlive() && client.player.distanceTo(le) <= range) {
                    target = le; break;
                }
            }
        }

        if (target != null) {
            client.interactionManager.attackEntity(client.player, target);
            client.player.swingHand(Hand.MAIN_HAND);
        }
    }

    // ═══════════════════════════════════════════════════════
    //  HITX MENU (GUI)
    // ═══════════════════════════════════════════════════════
    public class HitXMenu extends Screen {
        private String openSettings = "";
        private int bindingFor = -1;

        protected HitXMenu() { super(Text.literal("HitX")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int w = 380, h = 280;
            int x = width / 2 - w / 2;
            int y = height / 2 - h / 2;

            // Arkaplan ve Paneller
            ctx.fill(x, y, x + w, y + h, 0xF01A1A1A);
            ctx.fill(x, y, x + 160, y + h, 0xFF252525);

            ctx.drawCenteredTextWithShadow(textRenderer, "§l§dHITX", x + 80, y + 8, 0xFFFFFF);

            // Butonlar
            drawModBtn(ctx, x + 10, y + 28, "Hitboxes", hitBoxActive, mx, my);
            drawModBtn(ctx, x + 10, y + 60, "Aura", auraActive, mx, my);
            drawModBtn(ctx, x + 10, y + 92, "TriggerBot", triggerBotActive, mx, my);
            drawModBtn(ctx, x + 10, y + 124, "HitColor", cfg.hitColorActive, mx, my);

            // Sağ Ayar Paneli
            renderSettings(ctx, x + 168, y, cfg, mx);

            super.render(ctx, mx, my, d);
        }

        private void renderSettings(DrawContext ctx, int rx, int y, HitXConfig cfg, int mx) {
            if (openSettings.equals("Hitboxes")) {
                drawSlider(ctx, rx, y + 40, 190, "Genişlik: " + cfg.xzExpand, (cfg.xzExpand - 0.5f) / 4.5f);
                drawSlider(ctx, rx, y + 80, 190, "Yükseklik: " + cfg.yExpand, (cfg.yExpand - 0.5f) / 3.5f);
            } else if (openSettings.equals("HitColor")) {
                drawSlider(ctx, rx, y + 40, 190, "§cRed: " + cfg.hcRed, cfg.hcRed / 255f);
                drawSlider(ctx, rx, y + 80, 190, "§aGreen: " + cfg.hcGreen, cfg.hcGreen / 255f);
                drawSlider(ctx, rx, y + 120, 190, "§bBlue: " + cfg.hcBlue, cfg.hcBlue / 255f);
                drawSlider(ctx, rx, y + 160, 190, "§7Alpha: " + cfg.hcAlpha, cfg.hcAlpha / 255f);
            }
        }

        private void drawModBtn(DrawContext ctx, int x, int y, String name, boolean state, int mx, int my) {
            int color = state ? 0xFF00AA77 : 0xFF363636;
            ctx.fill(x, y, x + 140, y + 26, color);
            ctx.drawTextWithShadow(textRenderer, name, x + 10, y + 9, 0xFFFFFF);
            ctx.drawTextWithShadow(textRenderer, state ? "§aON" : "§cOFF", x + 110, y + 9, 0xFFFFFF);
        }

        private void drawSlider(DrawContext ctx, int x, int y, int w, String text, float pct) {
            ctx.drawTextWithShadow(textRenderer, text, x, y - 12, 0xFFFFFF);
            ctx.fill(x, y, x + w, y + 8, 0xFF111111);
            ctx.fill(x, y, x + (int)(w * pct), y + 8, 0xFF00FFBB);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int x = width / 2 - 190;
            int y = height / 2 - 140;

            // Sol Panel Tıklamaları
            if (mx >= x + 10 && mx <= x + 150) {
                if (my >= y + 28 && my <= y + 54) { if(btn==0) hitBoxActive=!hitBoxActive; else openSettings="Hitboxes"; return true; }
                if (my >= y + 60 && my <= y + 86) { if(btn==0) auraActive=!auraActive; else openSettings="Aura"; return true; }
                if (my >= y + 124 && my <= y + 150) { 
                    if(btn==0) { cfg.hitColorActive=!cfg.hitColorActive; save(); OverlayReloadListener.callEvent(); } 
                    else openSettings="HitColor"; 
                    return true; 
                }
            }

            // Sağ Panel Slider Tıklamaları
            int rx = x + 168;
            if (openSettings.equals("HitColor")) {
                if (my >= y + 40 && my <= y + 48) { cfg.hcRed = (int)(clamp((float)((mx-rx)/190f)) * 255); save(); OverlayReloadListener.callEvent(); }
                if (my >= y + 80 && my <= y + 88) { cfg.hcGreen = (int)(clamp((float)((mx-rx)/190f)) * 255); save(); OverlayReloadListener.callEvent(); }
                if (my >= y + 120 && my <= y + 128) { cfg.hcBlue = (int)(clamp((float)((mx-rx)/190f)) * 255); save(); OverlayReloadListener.callEvent(); }
                if (my >= y + 160 && my <= y + 168) { cfg.hcAlpha = (int)(clamp((float)((mx-rx)/190f)) * 255); save(); OverlayReloadListener.callEvent(); }
            }

            return super.mouseClicked(mx, my, btn);
        }

        private float clamp(float val) { return Math.max(0, Math.min(1, val)); }
        private void save() { AutoConfig.getConfigHolder(HitXConfig.class).save(); }
    }
    
    // Yardımcı Envanter Metodu
    private boolean isArmor(ItemStack s) { return s.getItem() instanceof net.minecraft.item.ArmorItem; }
}
