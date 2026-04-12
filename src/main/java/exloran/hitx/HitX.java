package exloran.hitx;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
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

    // ── İç Değişkenler ───────────────────────────────────────
    private boolean      mLast              = false;
    private LivingEntity currentAuraTarget  = null;

    // ─────────────────────────────────────────────────────────
    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        // ── Envanter ekran butonları ──────────────────────────
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            int bx = W / 2 + 92;
            int by = H / 2 - 50;

            if (screen instanceof InventoryScreen inv) {
                int id = inv.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.DIAMOND_CHESTPLATE), "§bZırhı Giy",
                        bx, by, 22, 22,
                        b -> {
                            for (int i = 9; i < 45; i++) {
                                if (isArmor(inv.getScreenHandler().getSlot(i).getStack())) {
                                    client.interactionManager.clickSlot(
                                            id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                                }
                            }
                        });
            }

            if (screen instanceof GenericContainerScreen chest) {
                int id = chest.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.HOPPER), "§aHepsini Al",
                        bx, by, 22, 22,
                        b -> {
                            for (int i = 0; i < chest.getScreenHandler().getInventory().size(); i++) {
                                client.interactionManager.clickSlot(
                                        id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                            }
                        });
            }
        });

        // ── Ana Tick ─────────────────────────────────────────
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // M tuşu → Menü
            boolean mNow = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_M)
                           == GLFW.GLFW_PRESS;
            if (mNow && !mLast) client.setScreen(new HitXMenu());
            mLast = mNow;

            // Config her tick taze alınıyor
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            // ── HitBoxes ──────────────────────────────────────
            if (hitBoxActive) {
                for (Entity e : client.world.getEntities()) {
                    if (!(e instanceof LivingEntity le)) continue;
                    if (le == client.player)              continue;

                    float hw   = (0.6f * config.xzExpand) / 2f;
                    float tall = 1.8f  * config.yExpand;
                    float yOff = config.yOffset;

                    le.setBoundingBox(new Box(
                            le.getX() - hw,  le.getY() + yOff,        le.getZ() - hw,
                            le.getX() + hw,  le.getY() + tall + yOff, le.getZ() + hw
                    ));
                }
            }

            // ── Aura + TriggerBot ─────────────────────────────
            currentAuraTarget = null;
            if (!auraActive && !triggerBotActive) return;
            if (client.player.getAttackCooldownProgress(0.5f) < 1.0f) return;

            LivingEntity target = null;

            // TriggerBot: nişan hedefte mi?
            if (triggerBotActive && client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit) {
                Entity ent = hit.getEntity();
                if (ent instanceof LivingEntity le && le != client.player && le.isAlive()) {
                    target = le;
                }
            }

            // Aura: en yakın hedefi bul
            if (auraActive && target == null) {
                double maxDist = (client.player.isFallFlying() && elytraTarget) ? elytraRange : auraRange;
                LivingEntity best     = null;
                double       bestDist = Double.MAX_VALUE;

                for (Entity e : client.world.getEntities()) {
                    if (!(e instanceof LivingEntity le)) continue;
                    if (le == client.player)              continue;
                    if (!le.isAlive())                    continue;
                    if (!client.player.canSee(le))        continue;

                    double d = client.player.distanceTo(le);
                    if (d <= maxDist && d < bestDist) {
                        bestDist = d;
                        best     = le;
                    }
                }
                target = best;
            }

            // Vur
            if (target != null) {
                currentAuraTarget = target;
                client.interactionManager.attackEntity(client.player, target);
                client.player.swingHand(Hand.MAIN_HAND);
            }
        });
    }

    // ═══════════════════════════════════════════════════════
    //  HITX MENU  ─  Tam GUI
    // ═══════════════════════════════════════════════════════
    public class HitXMenu extends Screen {

        private String openSettings = "";

        protected HitXMenu() { super(Text.literal("HitX")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            int w = 380, h = 220;
            int x = width  / 2 - w / 2;
            int y = height / 2 - h / 2;

            // Ana kart
            drawRoundedRect(ctx, x,       y, x + w, y + h, 0xF01A1A1A, 6);
            // Sol panel
            drawRoundedRect(ctx, x,       y, x + 160, y + h, 0xFF252525, 6);
            ctx.fill(x + 155, y, x + 160, y + h, 0xFF1A1A1A); // düz kenar

            // Başlık
            ctx.drawCenteredTextWithShadow(textRenderer, "§l§dHITX", x + 80, y + 8, 0xFFFFFF);

            // Modül butonları
            drawModuleBtn(ctx, x + 10, y + 28, 140, 26, mx, my, "Hitboxes",   hitBoxActive);
            drawModuleBtn(ctx, x + 10, y + 60, 140, 26, mx, my, "Aura",       auraActive);
            drawModuleBtn(ctx, x + 10, y + 92, 140, 26, mx, my, "TriggerBot", triggerBotActive);

            // Sağ panel — ayarlar
            int rx = x + 168;
            if (!openSettings.isEmpty()) {
                ctx.drawTextWithShadow(textRenderer, "§l§a" + openSettings.toUpperCase(),
                        rx, y + 10, 0xFFFFFF);

                if (openSettings.equals("Hitboxes")) {
                    // XZ slider
                    drawSlider(ctx, rx, y + 32, 196,
                            "Genişlik: " + String.format("%.2f", cfg.xzExpand),
                            (cfg.xzExpand - 0.5f) / 4.5f);
                    // Y slider
                    drawSlider(ctx, rx, y + 68, 196,
                            "Yükseklik: " + String.format("%.2f", cfg.yExpand),
                            (cfg.yExpand  - 0.5f) / 3.5f);
                    // Y offset slider
                    drawSlider(ctx, rx, y + 104, 196,
                            "Y Offset: " + String.format("%.2f", cfg.yOffset),
                            (cfg.yOffset + 1.0f) / 2.0f);
                    ctx.drawTextWithShadow(textRenderer,
                            "§7Tıklayarak küçült/büyüt", rx, y + 148, 0x888888);

                } else if (openSettings.equals("Aura")) {
                    drawSlider(ctx, rx, y + 32, 196,
                            "Menzil: " + String.format("%.1f", auraRange),
                            (auraRange - 1.0f) / 5.0f);
                    drawSlider(ctx, rx, y + 68, 196,
                            "Elytra Menzil: " + String.format("%.1f", elytraRange),
                            (elytraRange - 1.0f) / 9.0f);
                    drawToggleBtn(ctx, rx, y + 104, 196, 22, elytraTarget, "Elytra Target");

                } else if (openSettings.equals("TriggerBot")) {
                    ctx.drawTextWithShadow(textRenderer,
                            "§7Nişan aldığın hedefe otomatik vurur.",
                            rx, y + 32, 0xAAAAAA);
                    ctx.drawTextWithShadow(textRenderer,
                            "§7Cooldown dolunca ateş eder.",
                            rx, y + 48, 0xAAAAAA);
                }
            }

            super.render(ctx, mx, my, d);
        }

        // ── Modül Butonu ──────────────────────────────────────
        private void drawModuleBtn(DrawContext ctx,
                                   int x, int y, int w, int h,
                                   int mx, int my,
                                   String name, boolean state) {
            boolean hovered = mx >= x && mx <= x + w && my >= y && my <= y + h;
            int bg = state   ? 0xFF006644
                   : hovered ? 0xFF4A4A4A
                              : 0xFF363636;
            drawRoundedRect(ctx, x, y, x + w, y + h, bg, 5);

            // Sol aktif çizgi
            if (state) ctx.fill(x, y + 3, x + 3, y + h - 3, 0xFF00FFBB);

            ctx.drawTextWithShadow(textRenderer, name,
                    x + 12, y + h / 2 - 4,
                    state ? 0xFF00FFBB : 0xFFDDDDDD);

            // Durum etiketi
            String tag = state ? "§aON" : "§8OFF";
            ctx.drawTextWithShadow(textRenderer, tag,
                    x + w - textRenderer.getWidth(tag.substring(2)) - 8,
                    y + h / 2 - 4, 0xFFFFFF);
        }

        // ── Slider ────────────────────────────────────────────
        private void drawSlider(DrawContext ctx,
                                int x, int y, int w,
                                String label, float pct) {
            ctx.drawTextWithShadow(textRenderer, label, x, y, 0xFFFFFF);
            // track
            drawRoundedRect(ctx, x, y + 14, x + w, y + 22, 0xFF111111, 3);
            // fill
            int fillW = (int)(w * Math.max(0, Math.min(1, pct)));
            if (fillW > 0)
                drawRoundedRect(ctx, x, y + 14, x + fillW, y + 22, 0xFF00FFBB, 3);
            // knob
            ctx.fill(x + fillW - 2, y + 11, x + fillW + 2, y + 25, 0xFFFFFFFF);
        }

        // ── Toggle Butonu ─────────────────────────────────────
        private void drawToggleBtn(DrawContext ctx,
                                   int x, int y, int w, int h,
                                   boolean state, String name) {
            drawRoundedRect(ctx, x, y, x + w, y + h,
                    state ? 0xFF1A5C40 : 0xFF2E2E2E, 4);
            ctx.drawTextWithShadow(textRenderer, name,
                    x + 8, y + h / 2 - 4,
                    state ? 0xFF00FFBB : 0xFF888888);
        }

        // ── Mouse Click ───────────────────────────────────────
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int w = 380, h = 220;
            int x = width  / 2 - w / 2;
            int y = height / 2 - h / 2;

            // Sol panel — modül toggle / sağ tık ayarlar
            if (mx >= x + 10 && mx <= x + 150) {
                if (my >= y + 28 && my <= y + 54) {
                    if (btn == 0) hitBoxActive     = !hitBoxActive;
                    else          openSettings     = openSettings.equals("Hitboxes") ? "" : "Hitboxes";
                    return true;
                }
                if (my >= y + 60 && my <= y + 86) {
                    if (btn == 0) auraActive       = !auraActive;
                    else          openSettings     = openSettings.equals("Aura") ? "" : "Aura";
                    return true;
                }
                if (my >= y + 92 && my <= y + 118) {
                    if (btn == 0) triggerBotActive = !triggerBotActive;
                    else          openSettings     = openSettings.equals("TriggerBot") ? "" : "TriggerBot";
                    return true;
                }
            }

            // Sağ panel — slider tıklamaları
            int rx = x + 168;
            if (openSettings.equals("Hitboxes")) {
                HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
                // XZ slider
                if (my >= y + 46 && my <= y + 58) {
                    float pct = (float)((mx - rx) / 196.0);
                    cfg.xzExpand = clampF(0.5f + pct * 4.5f, 0.5f, 5.0f);
                    saveConfig();
                    return true;
                }
                // Y slider
                if (my >= y + 82 && my <= y + 94) {
                    float pct = (float)((mx - rx) / 196.0);
                    cfg.yExpand = clampF(0.5f + pct * 3.5f, 0.5f, 4.0f);
                    saveConfig();
                    return true;
                }
                // Y Offset slider
                if (my >= y + 118 && my <= y + 130) {
                    float pct = (float)((mx - rx) / 196.0);
                    cfg.yOffset = clampF(-1.0f + pct * 2.0f, -1.0f, 1.0f);
                    saveConfig();
                    return true;
                }
            }

            if (openSettings.equals("Aura")) {
                // Range slider
                if (my >= y + 46 && my <= y + 58) {
                    float pct = (float)((mx - rx) / 196.0);
                    auraRange = clampF(1.0f + pct * 5.0f, 1.0f, 6.0f);
                    return true;
                }
                // ElytraRange slider
                if (my >= y + 82 && my <= y + 94) {
                    float pct = (float)((mx - rx) / 196.0);
                    elytraRange = clampF(1.0f + pct * 9.0f, 1.0f, 10.0f);
                    return true;
                }
                // ElytraTarget toggle
                if (my >= y + 104 && my <= y + 126) {
                    elytraTarget = !elytraTarget;
                    return true;
                }
            }

            return super.mouseClicked(mx, my, btn);
        }

        @Override public boolean shouldPause()      { return false; }
        @Override public boolean shouldCloseOnEsc() { return true;  }

    } // end HitXMenu

    // ── Yuvarlak Dikdörtgen ──────────────────────────────────
    private void drawRoundedRect(DrawContext ctx,
                                 int x1, int y1, int x2, int y2,
                                 int color, int r) {
        ctx.fill(x1 + r, y1,      x2 - r, y2,      color);
        ctx.fill(x1,     y1 + r,  x1 + r, y2 - r,  color);
        ctx.fill(x2 - r, y1 + r,  x2,     y2 - r,  color);
        for (int dx = 0; dx < r; dx++) {
            for (int dy = 0; dy < r; dy++) {
                if (Math.sqrt((double)(r-dx)*(r-dx) + (double)(r-dy)*(r-dy)) <= r) {
                    ctx.fill(x1+dx,       y1+dy,       x1+dx+1,     y1+dy+1,     color);
                    ctx.fill(x2-r+dx,     y1+dy,       x2-r+dx+1,   y1+dy+1,     color);
                    ctx.fill(x1+dx,       y2-r+dy,     x1+dx+1,     y2-r+dy+1,   color);
                    ctx.fill(x2-r+dx,     y2-r+dy,     x2-r+dx+1,   y2-r+dy+1,   color);
                }
            }
        }
    }

    // ── İkon Butonu ──────────────────────────────────────────
    private void iconBtn(Screen s, ItemStack item, String tooltip,
                         int x, int y, int w, int h,
                         ButtonWidget.PressAction action) {
        Screens.getButtons(s).add(
                new ButtonWidget(x, y, w, h, Text.empty(), action,
                        scr -> Text.empty()) {
                    @Override
                    public void renderWidget(DrawContext ctx, int mx, int my, float d) {
                        drawRoundedRect(ctx, getX(), getY(),
                                getX() + w, getY() + h,
                                isHovered() ? 0xFF333333 : 0xFF1A1A1A, 4);
                        ctx.drawItem(item, getX() + 3, getY() + 3);
                    }
                });
    }

    // ── Config Kaydet ─────────────────────────────────────────
    private void saveConfig() {
        AutoConfig.getConfigHolder(HitXConfig.class).save();
    }

    // ── Yardımcı ─────────────────────────────────────────────
    private float clampF(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private boolean isArmor(ItemStack s) {
        String n = s.getItem().toString();
        return n.contains("helmet") || n.contains("chestplate")
            || n.contains("leggings") || n.contains("boots");
    }

} // end HitX
