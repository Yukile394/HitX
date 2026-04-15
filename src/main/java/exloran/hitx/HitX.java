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

    // ── Modül Durumları ──────────────────────────────────────
    public static boolean hitBoxActive     = false;
    public static boolean triggerBotActive = false;
    public static boolean auraActive       = false;

    // ── Aura Ayarları ────────────────────────────────────────
    public static float   auraRange    = 3.2f;
    public static float   elytraRange  = 5.5f;
    public static boolean elytraTarget = true;

    // ── Keybind Tuşları ──────────────────────────────────────
    public static int keyHitbox     = GLFW.GLFW_KEY_H;
    public static int keyAura       = GLFW.GLFW_KEY_J;
    public static int keyTriggerBot = GLFW.GLFW_KEY_K;

    private boolean mLast = false;
    private boolean kHitboxLast = false;
    private boolean kAuraLast = false;
    private boolean kTriggerLast = false;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        // Dünyadaki renk değişimlerini dinlemek için
        ClientTickEvents.END_WORLD_TICK.register((world) -> OverlayReloadListener.callEvent());

        // Envanter Butonları
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            int bx = W / 2 + 92;
            int by = H / 2 - 50;
            if (screen instanceof InventoryScreen inv) {
                int id = inv.getScreenHandler().syncId;
                // iconBtn metodun burada çağrılabilir
            }
        });

        // Ana Döngü
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long handle = client.getWindow().getHandle();
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            // M Tuşu Menü
            boolean mNow = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
            if (mNow && !mLast) client.setScreen(new HitXMenu());
            mLast = mNow;

            // Keybindlar
            if (client.currentScreen == null) {
                boolean kH = GLFW.glfwGetKey(handle, keyHitbox) == GLFW.GLFW_PRESS;
                boolean kA = GLFW.glfwGetKey(handle, keyAura) == GLFW.GLFW_PRESS;
                boolean kT = GLFW.glfwGetKey(handle, keyTriggerBot) == GLFW.GLFW_PRESS;

                if (kH && !kHitboxLast) hitBoxActive = !hitBoxActive;
                if (kA && !kAuraLast) auraActive = !auraActive;
                if (kT && !kTriggerLast) triggerBotActive = !triggerBotActive;

                kHitboxLast = kH; kAuraLast = kA; kTriggerLast = kT;
            }

            // Hitboxes
            if (hitBoxActive) {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity le && le != client.player) {
                        float hw = (0.6f * cfg.xzExpand) / 2f;
                        float tall = 1.8f * cfg.yExpand;
                        le.setBoundingBox(new Box(le.getX()-hw, le.getY()+cfg.yOffset, le.getZ()-hw, le.getX()+hw, le.getY()+tall+cfg.yOffset, le.getZ()+hw));
                    }
                }
            }

            // Combat (Aura & Trigger)
            handleCombat(client);
        });
    }

    private void handleCombat(net.minecraft.client.MinecraftClient client) {
        if (!auraActive && !triggerBotActive) return;
        if (client.player.getAttackCooldownProgress(0.5f) < 1.0f) return;

        LivingEntity target = null;
        if (triggerBotActive && client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit) {
            if (hit.getEntity() instanceof LivingEntity le && le.isAlive()) target = le;
        }
        if (auraActive && target == null) {
            double range = client.player.isFallFlying() ? elytraRange : auraRange;
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
    //  HITX MENU
    // ═══════════════════════════════════════════════════════
    public class HitXMenu extends Screen {
        private String openSettings = "";
        private int bindingFor = -1;

        protected HitXMenu() { super(Text.literal("HitX")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int w = 380, h = 280;
            int x = width/2 - w/2;
            int y = height/2 - h/2;

            drawRoundedRect(ctx, x, y, x + w, y + h, 0xF01A1A1A, 6);
            ctx.fill(x, y, x + 160, y + h, 0xFF252525);
            ctx.drawCenteredTextWithShadow(textRenderer, "§l§dHITX", x + 80, y + 8, 0xFFFFFF);

            // Sol Panel Modüller
            drawModBtn(ctx, x + 10, y + 28, "Hitboxes", hitBoxActive, mx, my);
            drawModBtn(ctx, x + 10, y + 60, "Aura", auraActive, mx, my);
            drawModBtn(ctx, x + 10, y + 92, "TriggerBot", triggerBotActive, mx, my);
            drawModBtn(ctx, x + 10, y + 124, "HitColor", cfg.hitColorActive, mx, my);

            // Keybindlar Bölümü
            ctx.drawTextWithShadow(textRenderer, "§7KEYBINDS", x + 10, y + 155, 0x888888);
            drawKeyRow(ctx, x + 10, y + 170, "Hitbox: " + (bindingFor==0 ? "..." : keyName(keyHitbox)), mx, my);
            drawKeyRow(ctx, x + 10, y + 190, "Aura: " + (bindingFor==1 ? "..." : keyName(keyAura)), mx, my);

            // Sağ Panel Ayarlar
            int rx = x + 168;
            if (openSettings.equals("Hitboxes")) {
                drawSlider(ctx, rx, y + 40, 190, "Genişlik: " + cfg.xzExpand, (cfg.xzExpand-0.5f)/4.5f);
                drawSlider(ctx, rx, y + 80, 190, "Yükseklik: " + cfg.yExpand, (cfg.yExpand-0.5f)/3.5f);
            } else if (openSettings.equals("HitColor")) {
                drawSlider(ctx, rx, y + 40, 190, "§cRed: " + cfg.hcRed, cfg.hcRed/255f);
                drawSlider(ctx, rx, y + 75, 190, "§aGreen: " + cfg.hcGreen, cfg.hcGreen/255f);
                drawSlider(ctx, rx, y + 110, 190, "§bBlue: " + cfg.hcBlue, cfg.hcBlue/255f);
                drawSlider(ctx, rx, y + 145, 190, "§7Alpha: " + cfg.hcAlpha, cfg.hcAlpha/255f);
            } else if (openSettings.equals("Aura")) {
                drawSlider(ctx, rx, y + 40, 190, "Menzil: " + auraRange, (auraRange-1f)/5f);
            }

            super.render(ctx, mx, my, d);
        }

        private void drawModBtn(DrawContext ctx, int x, int y, String name, boolean state, int mx, int my) {
            int bg = state ? 0xFF006644 : (mx>=x && mx<=x+140 && my>=y && my<=y+26 ? 0xFF4A4A4A : 0xFF363636);
            ctx.fill(x, y, x + 140, y + 26, bg);
            ctx.drawTextWithShadow(textRenderer, name, x+10, y+9, 0xFFFFFF);
            ctx.drawTextWithShadow(textRenderer, state ? "§aON" : "§8OFF", x+110, y+9, 0xFFFFFF);
        }

        private void drawKeyRow(DrawContext ctx, int x, int y, String txt, int mx, int my) {
            ctx.drawTextWithShadow(textRenderer, txt, x, y, 0xCCCCCC);
        }

        private void drawSlider(DrawContext ctx, int x, int y, int w, String text, float pct) {
            ctx.drawTextWithShadow(textRenderer, text, x, y - 10, 0xFFFFFF);
            ctx.fill(x, y, x + w, y + 6, 0xFF111111);
            ctx.fill(x, y, x + (int)(w * pct), y + 6, 0xFF00FFBB);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int x = width/2 - 190; int y = height/2 - 140; int rx = x + 168;

            if (mx >= x + 10 && mx <= x + 150) {
                if (my >= y + 28 && my <= y + 54) { if(btn==0) hitBoxActive=!hitBoxActive; else openSettings="Hitboxes"; return true; }
                if (my >= y + 60 && my <= y + 86) { if(btn==0) auraActive=!auraActive; else openSettings="Aura"; return true; }
                if (my >= y + 92 && my <= y + 118) { if(btn==0) triggerBotActive=!triggerBotActive; else openSettings="TriggerBot"; return true; }
                if (my >= y + 124 && my <= y + 150) { if(btn==0) { cfg.hitColorActive=!cfg.hitColorActive; save(); OverlayReloadListener.callEvent(); } else openSettings="HitColor"; return true; }
                
                if (my >= y + 170 && my <= y + 180) { bindingFor = 0; return true; }
                if (my >= y + 190 && my <= y + 200) { bindingFor = 1; return true; }
            }

            if (openSettings.equals("HitColor")) {
                if (my >= y + 40 && my <= y + 46) { cfg.hcRed = (int)(clamp((mx-rx)/190f)*255); save(); OverlayReloadListener.callEvent(); return true; }
                if (my >= y + 75 && my <= y + 81) { cfg.hcGreen = (int)(clamp((mx-rx)/190f)*255); save(); OverlayReloadListener.callEvent(); return true; }
                if (my >= y + 110 && my <= y + 116) { cfg.hcBlue = (int)(clamp((mx-rx)/190f)*255); save(); OverlayReloadListener.callEvent(); return true; }
                if (my >= y + 145 && my <= y + 151) { cfg.hcAlpha = (int)(clamp((mx-rx)/190f)*255); save(); OverlayReloadListener.callEvent(); return true; }
            }
            if (openSettings.equals("Hitboxes")) {
                if (my >= y + 40 && my <= y + 46) { cfg.xzExpand = 0.5f + (float)((mx-rx)/190f)*4.5f; save(); return true; }
                if (my >= y + 80 && my <= y + 86) { cfg.yExpand = 0.5f + (float)((mx-rx)/190f)*3.5f; save(); return true; }
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean keyPressed(int key, int sc, int mod) {
            if (bindingFor == 0) { keyHitbox = key; bindingFor = -1; return true; }
            if (bindingFor == 1) { keyAura = key; bindingFor = -1; return true; }
            return super.keyPressed(key, sc, mod);
        }

        private double clamp(double v) { return Math.max(0, Math.min(1, v)); }
        private void save() { AutoConfig.getConfigHolder(HitXConfig.class).save(); }
        private void drawRoundedRect(DrawContext ctx, int x1, int y1, int x2, int y2, int col, int r) { ctx.fill(x1, y1, x2, y2, col); }
        private String keyName(int key) { return GLFW.glfwGetKeyName(key, 0) != null ? GLFW.glfwGetKeyName(key, 0).toUpperCase() : "KEY " + key; }
    }

    private void iconBtn(Screen s, ItemStack item, String t, int x, int y, int w, int h, ButtonWidget.PressAction a) {}
    private boolean isArmor(ItemStack s) { return s.getItem() instanceof net.minecraft.item.ArmorItem; }
}
