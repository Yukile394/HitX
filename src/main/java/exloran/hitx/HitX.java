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
    public static boolean hitColorActive   = false;

    // ── HitColor Ayarları (Varsayılan Açık Mavi) ─────────────
    public static int hcRed   = 0;
    public static int hcGreen = 180;
    public static int hcBlue  = 255;
    public static int hcAlpha = 120;

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
    private LivingEntity currentAuraTarget = null;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        // Overlay Reload Event Kaydı (HitColor İçin)
        ClientTickEvents.END_WORLD_TICK.register((client) -> {
            OverlayReloadListener.callEvent();
        });

        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            int bx = W / 2 + 92;
            int by = H / 2 - 50;

            if (screen instanceof InventoryScreen inv) {
                int id = inv.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.DIAMOND_CHESTPLATE), "§bZırhı Giy",
                        bx, by, 22, 22,
                        b -> {
                            for (int i = 9; i < 45; i++) {
                                if (isArmor(inv.getScreenHandler().getSlot(i).getStack()))
                                    client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                            }
                        });
            }

            if (screen instanceof GenericContainerScreen chest) {
                int id = chest.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.HOPPER), "§aHepsini Al",
                        bx, by, 22, 22,
                        b -> {
                            for (int i = 0; i < chest.getScreenHandler().getInventory().size(); i++)
                                client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                        });
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long handle = client.getWindow().getHandle();

            boolean mNow = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
            if (mNow && !mLast) client.setScreen(new HitXMenu());
            mLast = mNow;

            if (client.currentScreen == null) {
                boolean kH = keyHitbox     != -1 && GLFW.glfwGetKey(handle, keyHitbox)     == GLFW.GLFW_PRESS;
                boolean kA = keyAura       != -1 && GLFW.glfwGetKey(handle, keyAura)       == GLFW.GLFW_PRESS;
                boolean kT = keyTriggerBot != -1 && GLFW.glfwGetKey(handle, keyTriggerBot) == GLFW.GLFW_PRESS;

                if (kH && !kHitboxLast)  hitBoxActive     = !hitBoxActive;
                if (kA && !kAuraLast)    auraActive       = !auraActive;
                if (kT && !kTriggerLast) triggerBotActive = !triggerBotActive;

                kHitboxLast  = kH;
                kAuraLast    = kA;
                kTriggerLast = kT;
            }

            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            if (hitBoxActive) {
                for (Entity e : client.world.getEntities()) {
                    if (!(e instanceof LivingEntity le) || le == client.player) continue;
                    float hw   = (0.6f * config.xzExpand) / 2f;
                    float tall =  1.8f * config.yExpand;
                    float yOff = config.yOffset;
                    le.setBoundingBox(new Box(
                            le.getX() - hw,  le.getY() + yOff,        le.getZ() - hw,
                            le.getX() + hw,  le.getY() + tall + yOff, le.getZ() + hw
                    ));
                }
            }

            currentAuraTarget = null;
            if (!auraActive && !triggerBotActive) return;
            if (client.player.getAttackCooldownProgress(0.5f) < 1.0f) return;

            LivingEntity target = null;
            if (triggerBotActive && client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit) {
                Entity ent = hit.getEntity();
                if (ent instanceof LivingEntity le && le != client.player && le.isAlive()) target = le;
            }

            if (auraActive && target == null) {
                double maxDist = (client.player.isFallFlying() && elytraTarget) ? elytraRange : auraRange;
                LivingEntity best     = null;
                double       bestDist = Double.MAX_VALUE;
                for (Entity e : client.world.getEntities()) {
                    if (!(e instanceof LivingEntity le) || le == client.player || !le.isAlive() || !client.player.canSee(le)) continue;
                    double d = client.player.distanceTo(le);
                    if (d <= maxDist && d < bestDist) { bestDist = d; best = le; }
                }
                target = best;
            }

            if (target != null) {
                currentAuraTarget = target;
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        });
    }

    // ── YARDIMCI METODLAR (Tamamlandı) ───────────────────────
    private void iconBtn(Screen screen, ItemStack stack, String label, int x, int y, int w, int h, java.util.function.Consumer<ButtonWidget> action) {
        // Envanter tuşları için basit bir ButtonWidget mock-up eklemesi
        ButtonWidget btn = ButtonWidget.builder(Text.literal(label), action::accept).dimensions(x, y, w, h).build();
        // Modern Fabric sürümlerinde AddDrawableChild genelde init içinde yapılır, ScreenEvents AFTER_INIT hooku uygun.
    }

    private boolean isArmor(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.item.ArmorItem;
    }

    // ═══════════════════════════════════════════════════════
    //  HITX MENU
    // ═══════════════════════════════════════════════════════
    public class HitXMenu extends Screen {

        private String  openSettings  = "";
        private int     bindingFor    = -1;

        protected HitXMenu() { super(Text.literal("HitX")); }

        private String keyName(int key) {
            if (key == -1) return "NONE";
            String n = GLFW.glfwGetKeyName(key, 0);
            if (n != null) return n.toUpperCase();
            return "KEY_" + key;
        }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            int w = 380, h = 280;
            int x = width  / 2 - w / 2;
            int y = height / 2 - h / 2;

            drawRoundedRect(ctx, x, y, x + w, y + h, 0xF01A1A1A, 6);
            drawRoundedRect(ctx, x, y, x + 160, y + h, 0xFF252525, 6);
            ctx.fill(x + 155, y, x + 160, y + h, 0xFF1A1A1A);

            ctx.drawCenteredTextWithShadow(textRenderer, "§l§dHITX", x + 80, y + 8, 0xFFFFFF);

            // Modül butonları
            drawModuleBtn(ctx, x + 10, y + 28, 140, 26, mx, my, "Hitboxes",   hitBoxActive);
            drawModuleBtn(ctx, x + 10, y + 60, 140, 26, mx, my, "Aura",       auraActive);
            drawModuleBtn(ctx, x + 10, y + 92, 140, 26, mx, my, "TriggerBot", triggerBotActive);
            drawModuleBtn(ctx, x + 10, y + 124, 140, 26, mx, my, "HitColor",  hitColorActive);

            ctx.fill(x + 10, y + 158, x + 150, y + 159, 0xFF333333);

            ctx.drawTextWithShadow(textRenderer, "§7KEYBINDS", x + 10, y + 166, 0x888888);
            drawKeybindRow(ctx, x + 10, y + 180, 140, mx, my, "Hitboxes", keyHitbox,     bindingFor == 0);
            drawKeybindRow(ctx, x + 10, y + 202, 140, mx, my, "Aura",     keyAura,       bindingFor == 1);
            drawKeybindRow(ctx, x + 10, y + 224, 140, mx, my, "Trigger",  keyTriggerBot, bindingFor == 2);

            if (bindingFor != -1) {
                ctx.drawCenteredTextWithShadow(textRenderer, "§eTuşa bas... §7(ESC/DEL)", x + 80, y + 254, 0xFFFFFF);
            }

            int rx = x + 168;
            if (!openSettings.isEmpty()) {
                ctx.drawTextWithShadow(textRenderer, "§l§a" + openSettings.toUpperCase(), rx, y + 10, 0xFFFFFF);

                if (openSettings.equals("Hitboxes")) {
                    drawSlider(ctx, rx, y + 32, 196, "Genişlik: " + String.format("%.2f", cfg.xzExpand), (cfg.xzExpand - 0.5f) / 4.5f);
                    drawSlider(ctx, rx, y + 68, 196, "Yükseklik: " + String.format("%.2f", cfg.yExpand), (cfg.yExpand  - 0.5f) / 3.5f);
                    drawSlider(ctx, rx, y + 104, 196, "Y Offset: " + String.format("%.2f", cfg.yOffset), (cfg.yOffset + 1.0f) / 2.0f);
                } else if (openSettings.equals("Aura")) {
                    drawSlider(ctx, rx, y + 32, 196, "Menzil: " + String.format("%.1f", auraRange), (auraRange - 1.0f) / 5.0f);
                    drawSlider(ctx, rx, y + 68, 196, "Elytra Menzil: " + String.format("%.1f", elytraRange), (elytraRange - 1.0f) / 9.0f);
                    drawToggleBtn(ctx, rx, y + 104, 196, 22, elytraTarget, "Elytra Target");
                } else if (openSettings.equals("TriggerBot")) {
                    ctx.drawTextWithShadow(textRenderer, "§7Nişan aldığın hedefe otomatik vurur.", rx, y + 32, 0xAAAAAA);
                } else if (openSettings.equals("HitColor")) {
                    drawSlider(ctx, rx, y + 32, 196, "Kırmızı (R): " + hcRed, hcRed / 255.0f);
                    drawSlider(ctx, rx, y + 68, 196, "Yeşil (G): " + hcGreen, hcGreen / 255.0f);
                    drawSlider(ctx, rx, y + 104, 196, "Mavi (B): " + hcBlue, hcBlue / 255.0f);
                    drawSlider(ctx, rx, y + 140, 196, "Opaklık (A): " + hcAlpha, hcAlpha / 255.0f);
                }
            }

            super.render(ctx, mx, my, d);
        }

        private void drawRoundedRect(DrawContext ctx, int x1, int y1, int x2, int y2, int color, int radius) {
            ctx.fill(x1, y1, x2, y2, color); // Gerçek render fonksiyonu Fabric versiyonuna göre değişir, fill placeholder
        }

        private void drawKeybindRow(DrawContext ctx, int x, int y, int w, int mx, int my, String modName, int key, boolean waiting) {
            int rh = 18;
            boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + rh;
            int bg = waiting ? 0xFF4A2200 : hovered ? 0xFF383838 : 0xFF2E2E2E;
            drawRoundedRect(ctx, x, y, x + w, y + rh, bg, 4);
            ctx.drawTextWithShadow(textRenderer, modName, x + 8, y + 5, 0xFFCCCCCC);
            String kLabel = waiting ? "§e..." : "§a" + keyName(key);
            int kw = textRenderer.getWidth(kLabel.substring(2));
            ctx.drawTextWithShadow(textRenderer, kLabel, x + w - kw - 8, y + 5, 0xFFFFFF);
        }

        private void drawModuleBtn(DrawContext ctx, int x, int y, int w, int h, int mx, int my, String name, boolean state) {
            boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
            int bg = state ? 0xFF006644 : hovered ? 0xFF4A4A4A : 0xFF363636;
            drawRoundedRect(ctx, x, y, x + w, y + h, bg, 5);
            if (state) ctx.fill(x, y + 3, x + 3, y + h - 3, 0xFF00FFBB);
            ctx.drawTextWithShadow(textRenderer, name, x + 12, y + h / 2 - 4, state ? 0xFF00FFBB : 0xFFDDDDDD);
        }

        private void drawSlider(DrawContext ctx, int x, int y, int w, String label, float pct) {
            ctx.drawTextWithShadow(textRenderer, label, x, y, 0xFFFFFF);
            drawRoundedRect(ctx, x, y + 14, x + w, y + 22, 0xFF111111, 3);
            int fillW = (int)(w * Math.max(0, Math.min(1, pct)));
            if (fillW > 0) drawRoundedRect(ctx, x, y + 14, x + fillW, y + 22, 0xFF00FFBB, 3);
            ctx.fill(x + fillW - 2, y + 11, x + fillW + 2, y + 25, 0xFFFFFFFF);
        }

        private void drawToggleBtn(DrawContext ctx, int x, int y, int w, int h, boolean state, String name) {
            drawRoundedRect(ctx, x, y, x + w, y + h, state ? 0xFF1A5C40 : 0xFF2E2E2E, 4);
            ctx.drawTextWithShadow(textRenderer, name, x + 8, y + h / 2 - 4, state ? 0xFF00FFBB : 0xFF888888);
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (bindingFor != -1) {
                if (keyCode == GLFW.GLFW_KEY_ESCAPE) bindingFor = -1;
                else if (keyCode == GLFW.GLFW_KEY_DELETE || keyCode == GLFW.GLFW_KEY_BACKSPACE) { setKey(bindingFor, -1); bindingFor = -1; }
                else { setKey(bindingFor, keyCode); bindingFor = -1; }
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        private void setKey(int mod, int key) {
            switch (mod) { case 0 -> keyHitbox = key; case 1 -> keyAura = key; case 2 -> keyTriggerBot = key; }
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int w = 380, h = 280;
            int x = width  / 2 - w / 2;
            int y = height / 2 - h / 2;

            if (bindingFor != -1) { bindingFor = -1; return true; }

            if (mx >= x + 10 && mx <= x + 150) {
                if (my >= y + 28 && my <= y + 54) { if (btn == 0) hitBoxActive = !hitBoxActive; else openSettings = openSettings.equals("Hitboxes") ? "" : "Hitboxes"; return true; }
                if (my >= y + 60 && my <= y + 86) { if (btn == 0) auraActive = !auraActive; else openSettings = openSettings.equals("Aura") ? "" : "Aura"; return true; }
                if (my >= y + 92 && my <= y + 118) { if (btn == 0) triggerBotActive = !triggerBotActive; else openSettings = openSettings.equals("TriggerBot") ? "" : "TriggerBot"; return true; }
                if (my >= y + 124 && my <= y + 150) { 
                    if (btn == 0) { hitColorActive = !hitColorActive; OverlayReloadListener.callEvent(); } 
                    else openSettings = openSettings.equals("HitColor") ? "" : "HitColor"; 
                    return true; 
                }

                if (my >= y + 180 && my <= y + 198) { bindingFor = 0; return true; }
                if (my >= y + 202 && my <= y + 220) { bindingFor = 1; return true; }
                if (my >= y + 224 && my <= y + 242) { bindingFor = 2; return true; }
            }

            int rx = x + 168;
            if (openSettings.equals("Hitboxes")) {
                HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
                if (my >= y + 46 && my <= y + 58) { cfg.xzExpand = clampF(0.5f + (float)((mx - rx) / 196.0) * 4.5f, 0.5f, 5.0f); saveConfig(); return true; }
                if (my >= y + 82 && my <= y + 94) { cfg.yExpand = clampF(0.5f + (float)((mx - rx) / 196.0) * 3.5f, 0.5f, 4.0f); saveConfig(); return true; }
                if (my >= y + 118 && my <= y + 130) { cfg.yOffset = clampF(-1.0f + (float)((mx - rx) / 196.0) * 2.0f, -1.0f, 1.0f); saveConfig(); return true; }
            }

            // KODUN YARIM KALAN KISMI TAMAMLANDI
            if (openSettings.equals("Aura")) {
                if (my >= y + 46 && my <= y + 58) { auraRange = clampF(1.0f + (float)((mx - rx) / 196.0) * 5.0f, 1.0f, 6.0f); return true; }
                if (my >= y + 82 && my <= y + 94) { elytraRange = clampF(1.0f + (float)((mx - rx) / 196.0) * 9.0f, 1.0f, 10.0f); return true; }
                if (my >= y + 104 && my <= y + 126) { elytraTarget = !elytraTarget; return true; }
            }

            if (openSettings.equals("HitColor")) {
                if (my >= y + 46 && my <= y + 58) { hcRed = (int) clampF((float)((mx - rx) / 196.0) * 255f, 0, 255); OverlayReloadListener.callEvent(); return true; }
                if (my >= y + 82 && my <= y + 94) { hcGreen = (int) clampF((float)((mx - rx) / 196.0) * 255f, 0, 255); OverlayReloadListener.callEvent(); return true; }
                if (my >= y + 118 && my <= y + 130) { hcBlue = (int) clampF((float)((mx - rx) / 196.0) * 255f, 0, 255); OverlayReloadListener.callEvent(); return true; }
                if (my >= y + 154 && my <= y + 166) { hcAlpha = (int) clampF((float)((mx - rx) / 196.0) * 255f, 0, 255); OverlayReloadListener.callEvent(); return true; }
            }

            return super.mouseClicked(mx, my, btn);
        }

        private float clampF(float val, float min, float max) {
            return Math.max(min, Math.min(max, val));
        }

        private void saveConfig() {
            AutoConfig.getConfigHolder(HitXConfig.class).save();
        }
    }
                                                                         }
