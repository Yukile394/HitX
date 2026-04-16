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

    // ── İç Değişkenler ───────────────────────────────────────
    private boolean mLast             = false;
    private boolean kHitboxLast       = false;
    private boolean kAuraLast         = false;
    private boolean kTriggerLast      = false;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        // HitColor dünyada güncellenmesi için listener
        ClientTickEvents.END_WORLD_TICK.register((client) -> OverlayReloadListener.callEvent());

        // Envanter butonları
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            int bx = W / 2 + 92;
            int by = H / 2 - 50;

            if (screen instanceof InventoryScreen inv) {
                int id = inv.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.DIAMOND_CHESTPLATE), "§bZırhı Giy", bx, by, 22, 22, b -> {
                    for (int i = 9; i < 45; i++) {
                        if (isArmor(inv.getScreenHandler().getSlot(i).getStack()))
                            client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                    }
                });
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long handle = client.getWindow().getHandle();
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            // M -> Menü
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

            // HitBoxes
            if (hitBoxActive) {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity le && le != client.player) {
                        float hw = (0.6f * config.xzExpand) / 2f;
                        float tall = 1.8f * config.yExpand;
                        le.setBoundingBox(new Box(le.getX()-hw, le.getY()+config.yOffset, le.getZ()-hw, le.getX()+hw, le.getY()+tall+config.yOffset, le.getZ()+hw));
                    }
                }
            }

            // Aura & Trigger
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
            double maxDist = client.player.isFallFlying() ? elytraRange : auraRange;
            for (Entity e : client.world.getEntities()) {
                if (e instanceof LivingEntity le && le != client.player && le.isAlive() && client.player.distanceTo(le) <= maxDist) {
                    target = le; break;
                }
            }
        }
        if (target != null) {
            client.interactionManager.attackEntity(client.player, target);
            client.player.swingHand(Hand.MAIN_HAND);
        }
    }

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

            drawRoundedRect(ctx, x, y, x + w, y + h, 0xF01A1A1A, 6);
            drawRoundedRect(ctx, x, y, x + 160, y + h, 0xFF252525, 6);
            ctx.drawCenteredTextWithShadow(textRenderer, "§l§dHITX", x + 80, y + 8, 0xFFFFFF);

            // Modüller
            drawModuleBtn(ctx, x + 10, y + 28, 140, 26, mx, my, "Hitboxes", hitBoxActive);
            drawModuleBtn(ctx, x + 10, y + 60, 140, 26, mx, my, "Aura", auraActive);
            drawModuleBtn(ctx, x + 10, y + 92, 140, 26, mx, my, "TriggerBot", triggerBotActive);
            drawModuleBtn(ctx, x + 10, y + 124, 140, 26, mx, my, "HitColor", cfg.hitColorActive);

            // Keybindlar
            ctx.drawTextWithShadow(textRenderer, "§7KEYBINDS", x + 10, y + 155, 0x888888);
            drawKeybindRow(ctx, x + 10, y + 170, 140, mx, my, "Hitboxes", keyHitbox, bindingFor == 0);
            drawKeybindRow(ctx, x + 10, y + 192, 140, mx, my, "Aura", keyAura, bindingFor == 1);

            // Ayarlar Paneli
            int rx = x + 168;
            if (!openSettings.isEmpty()) {
                ctx.drawTextWithShadow(textRenderer, "§l§a" + openSettings.toUpperCase(), rx, y + 10, 0xFFFFFF);
                if (openSettings.equals("Hitboxes")) {
                    drawSlider(ctx, rx, y + 32, 196, "Genişlik: " + String.format("%.2f", cfg.xzExpand), (cfg.xzExpand - 0.5f) / 4.5f);
                    drawSlider(ctx, rx, y + 68, 196, "Yükseklik: " + String.format("%.2f", cfg.yExpand), (cfg.yExpand - 0.5f) / 3.5f);
                } else if (openSettings.equals("HitColor")) {
                    drawSlider(ctx, rx, y + 32, 196, "§cRed: " + cfg.hcRed, cfg.hcRed / 255f);
                    drawSlider(ctx, rx, y + 68, 196, "§aGreen: " + cfg.hcGreen, cfg.hcGreen / 255f);
                    drawSlider(ctx, rx, y + 104, 196, "§bBlue: " + cfg.hcBlue, cfg.hcBlue / 255f);
                    drawSlider(ctx, rx, y + 140, 196, "§7Alpha: " + cfg.hcAlpha, cfg.hcAlpha / 255f);
                }
            }
            super.render(ctx, mx, my, d);
        }

        private void drawModuleBtn(DrawContext ctx, int x, int y, int w, int h, int mx, int my, String name, boolean state) {
            int bg = state ? 0xFF006644 : (mx >= x && mx <= x + w && my >= y && my <= y + h ? 0xFF4A4A4A : 0xFF363636);
            drawRoundedRect(ctx, x, y, x + w, y + h, bg, 5);
            ctx.drawTextWithShadow(textRenderer, name, x + 12, y + h / 2 - 4, state ? 0xFF00FFBB : 0xFFDDDDDD);
        }

        private void drawKeybindRow(DrawContext ctx, int x, int y, int w, int mx, int my, String mod, int key, boolean wait) {
            drawRoundedRect(ctx, x, y, x + w, y + 18, wait ? 0xFF4A2200 : 0xFF2E2E2E, 4);
            ctx.drawTextWithShadow(textRenderer, mod, x + 8, y + 5, 0xCCCCCC);
            ctx.drawTextWithShadow(textRenderer, wait ? "§e..." : "§a" + keyName(key), x + w - 40, y + 5, 0xFFFFFF);
        }

        private void drawSlider(DrawContext ctx, int x, int y, int w, String label, float pct) {
            ctx.drawTextWithShadow(textRenderer, label, x, y, 0xFFFFFF);
            drawRoundedRect(ctx, x, y + 14, x + w, y + 22, 0xFF111111, 3);
            ctx.fill(x, y + 14, x + (int)(w * pct), y + 22, 0xFF00FFBB);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int x = width / 2 - 190; int y = height / 2 - 140; int rx = x + 168;

            if (mx >= x + 10 && mx <= x + 150) {
                if (my >= y + 28 && my <= y + 54) { if (btn == 0) hitBoxActive = !hitBoxActive; else openSettings = "Hitboxes"; return true; }
                if (my >= y + 60 && my <= y + 86) { if (btn == 0) auraActive = !auraActive; else openSettings = "Aura"; return true; }
                if (my >= y + 92 && my <= y + 118) { if (btn == 0) triggerBotActive = !triggerBotActive; else openSettings = "TriggerBot"; return true; }
                if (my >= y + 124 && my <= y + 150) { 
                    if (btn == 0) { cfg.hitColorActive = !cfg.hitColorActive; saveConfig(); OverlayReloadListener.callEvent(); } 
                    else openSettings = "HitColor"; 
                    return true; 
                }
                if (my >= y + 170 && my <= y + 188) { bindingFor = 0; return true; }
                if (my >= y + 192 && my <= y + 210) { bindingFor = 1; return true; }
            }

            if (openSettings.equals("HitColor")) {
                if (my >= y + 46 && my <= y + 54) { cfg.hcRed = (int)(clampF((mx - rx) / 196f, 0, 1) * 255); saveConfig(); OverlayReloadListener.callEvent(); return true; }
                if (my >= y + 82 && my <= y + 90) { cfg.hcGreen = (int)(clampF((mx - rx) / 196f, 0, 1) * 255); saveConfig(); OverlayReloadListener.callEvent(); return true; }
                if (my >= y + 118 && my <= y + 126) { cfg.hcBlue = (int)(clampF((mx - rx) / 196f, 0, 1) * 255); saveConfig(); OverlayReloadListener.callEvent(); return true; }
                if (my >= y + 154 && my <= y + 162) { cfg.hcAlpha = (int)(clampF((mx - rx) / 196f, 0, 1) * 255); saveConfig(); OverlayReloadListener.callEvent(); return true; }
            }
            if (openSettings.equals("Hitboxes")) {
                if (my >= y + 46 && my <= y + 54) { cfg.xzExpand = clampF(0.5f + (float)((mx - rx) / 196f) * 4.5f, 0.5f, 5.0f); saveConfig(); return true; }
                if (my >= y + 82 && my <= y + 90) { cfg.yExpand = clampF(0.5f + (float)((mx - rx) / 196f) * 3.5f, 0.5f, 4.0f); saveConfig(); return true; }
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean keyPressed(int k, int s, int m) {
            if (bindingFor == 0) { keyHitbox = k; bindingFor = -1; return true; }
            if (bindingFor == 1) { keyAura = k; bindingFor = -1; return true; }
            return super.keyPressed(k, s, m);
        }

        private float clampF(double v, float min, float max) { return (float)Math.max(min, Math.min(max, v)); }
        private void saveConfig() { AutoConfig.getConfigHolder(HitXConfig.class).save(); }
        private void drawRoundedRect(DrawContext ctx, int x1, int y1, int x2, int y2, int col, int r) { ctx.fill(x1, y1, x2, y2, col); }
        private String keyName(int key) { return GLFW.glfwGetKeyName(key, 0) != null ? GLFW.glfwGetKeyName(key, 0).toUpperCase() : "KEY_" + key; }
    }

    private void iconBtn(Screen s, ItemStack i, String t, int x, int y, int w, int h, ButtonWidget.PressAction p) {}
    private boolean isArmor(ItemStack s) { return s.getItem() instanceof net.minecraft.item.ArmorItem; }
                               }
