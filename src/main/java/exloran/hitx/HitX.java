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
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

public class HitX implements ClientModInitializer {

    // Mod Sistem Durumları
    public static boolean hitBoxActive = false;
    public static boolean triggerBotActive = false;
    public static boolean auraActive = false;
    
    // Alt Ayarlar
    public static float auraRange = 3.2f;
    public static float elytraRange = 5.5f;
    public static boolean elytraTarget = true;
    public static boolean showAuraParticles = true;

    private boolean mLast = false;
    private LivingEntity currentAuraTarget = null;
    
    // Parçacık Görseli Tanımlaması
    private static final Identifier AURA_FX = new Identifier("hitx", "textures/gui/aura_fx.png");

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);
        HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

        // Envanter/Sandık Hızlı Butonları
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            int bx = W / 2 + 92, by = H / 2 - 50; 
            if (screen instanceof InventoryScreen inv) {
                int id = inv.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.DIAMOND_CHESTPLATE), "§bZırhı Giy", bx, by, 22, 22, b -> {
                    for (int i = 9; i < 45; i++) if (isArmor(inv.getScreenHandler().getSlot(i).getStack())) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                });
            }
            if (screen instanceof GenericContainerScreen chest) {
                int id = chest.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.HOPPER), "§aHepsini Al", bx, by, 22, 22, b -> {
                    for (int i = 0; i < chest.getScreenHandler().getInventory().size(); i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                });
            }
        });

        // Aura Görsel Parçacık (HUD Render)
        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (auraActive && showAuraParticles && currentAuraTarget != null && mc.player != null && !mc.options.hudHidden) {
                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();
                // Ekranda hedef varken crosshair etrafında görsel çıkarır
                ctx.drawTexture(AURA_FX, sw / 2 - 16, sh / 2 - 16, 0, 0, 32, 32, 32, 32);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            
            // Menü Tuşu (M)
            checkToggle(client, GLFW.GLFW_KEY_M, () -> { client.setScreen(new HitXMenu()); return ""; }, mLast, v -> mLast = v);

            // Gelişmiş Hitbox Mantığı
            if (hitBoxActive) {
                float wMul = config.xzExpand;
                float hMul = config.yExpand;
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity && e != client.player) {
                        float w = 0.6f * wMul;
                        float h = 1.8f * hMul;
                        if (config.fakeHitbox) {
                            if (client.player.distanceTo(e) < 5.0f) e.setBoundingBox(new Box(e.getX() - w/2, e.getY() + config.yOffset, e.getZ() - w/2, e.getX() + w/2, e.getY() + h + config.yOffset, e.getZ() + w/2));
                            else e.setBoundingBox(new Box(e.getX()-0.3, e.getY(), e.getZ()-0.3, e.getX()+0.3, e.getY()+1.8, e.getZ()+0.3));
                        } else {
                            e.setBoundingBox(new Box(e.getX() - w/2, e.getY() + config.yOffset, e.getZ() - w/2, e.getX() + w/2, e.getY() + h + config.yOffset, e.getZ() + w/2));
                        }
                    }
                }
            }

            currentAuraTarget = null; // Her tick sıfırla

            // Savaş Modülleri (Aura & TriggerBot) - Anti-Cheat Korumalı
            if (triggerBotActive || auraActive) {
                // Sadece bekleme süresi %100 olunca vur (Log düşmesini önler)
                if (client.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                    LivingEntity targetToAttack = null;

                    // TriggerBot
                    if (triggerBotActive && client.crosshairTarget instanceof EntityHitResult hitRes) {
                        if (hitRes.getEntity() instanceof LivingEntity le && le != client.player && le.isAlive()) {
                            targetToAttack = le;
                        }
                    }

                    // Aura
                    if (auraActive && targetToAttack == null) {
                        boolean isFlying = client.player.isFallFlying();
                        double maxDist = (isFlying && elytraTarget) ? elytraRange : auraRange;
                        double closestDist = maxDist * maxDist; 

                        for (Entity e : client.world.getEntities()) {
                            if (e instanceof LivingEntity le && le != client.player && le.isAlive()) {
                                // Anti-Cheat Bypass: Sadece görüş açısındaysa ve duvardan geçmiyorsa vur
                                if (client.player.canSee(e)) {
                                    double distSq = client.player.squaredDistanceTo(le);
                                    if (distSq <= closestDist) {
                                        closestDist = distSq;
                                        targetToAttack = le;
                                    }
                                }
                            }
                        }
                    }

                    if (targetToAttack != null) {
                        currentAuraTarget = targetToAttack; // Görsel efekt için kaydet
                        client.interactionManager.attackEntity(client.player, targetToAttack);
                        client.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            }
        });
    }

    // --- GELİŞMİŞ PÜRÜZSÜZ SAĞ TIK MENÜSÜ ---
    public class HitXMenu extends Screen {
        private String openSettings = ""; // Hangi modun ayarı açık?
        private int activeSlider = -1;

        private final int BG_COLOR = 0xF01A1A1A;       // Pürüzsüz Koyu Arka Plan
        private final int HEADER_COLOR = 0xFF2A2A2A;   // Üst Logo
        private final int ACCENT_ON = 0xFF00FFBB;      // Aktif Mod (Camgöbeği)
        private final int ACCENT_OFF = 0xFF404040;     // Kapalı Mod
        private final int PANEL_BG = 0xFF222222;       // Sağ Panel

        protected HitXMenu() { super(Text.literal("HitX")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            
            int w = 380, h = 220, x = width/2 - w/2, y = height/2 - h/2;
            
            // Ana Gövde
            drawRoundedRect(ctx, x, y, x + w, y + h, BG_COLOR, 6);
            drawRoundedRect(ctx, x, y, x + 160, y + h, HEADER_COLOR, 6); // Sol Modül Paneli
            
            ctx.drawCenteredTextWithShadow(textRenderer, "§lMODÜLLER", x + 80, y + 10, 0xFFFFFF);

            // Modül Butonları (Sol Taraf)
            drawModuleBtn(ctx, x + 10, y + 30, 140, 24, mx, my, "Hitboxes", hitBoxActive);
            drawModuleBtn(ctx, x + 10, y + 60, 140, 24, mx, my, "Aura", auraActive);
            drawModuleBtn(ctx, x + 10, y + 90, 140, 24, mx, my, "TriggerBot", triggerBotActive);

            // Sağ Taraf - Ayar Paneli
            int rx = x + 170;
            if (openSettings.isEmpty()) {
                ctx.drawCenteredTextWithShadow(textRenderer, "§7Ayarları görmek için", rx + 100, y + 100, 0xAAAAAA);
                ctx.drawCenteredTextWithShadow(textRenderer, "§7modüllerin üzerine §fSAĞ TIKLA", rx + 100, y + 115, 0xAAAAAA);
            } else {
                ctx.drawTextWithShadow(textRenderer, "§l" + openSettings.toUpperCase() + " AYARLARI", rx, y + 10, ACCENT_ON);
                drawRoundedRect(ctx, rx, y + 25, rx + 200, y + h - 10, PANEL_BG, 4);

                if (openSettings.equals("Hitboxes")) {
                    drawSlider(ctx, rx + 10, y + 40, 180, "Genişlik: " + String.format("%.1f", config.xzExpand), (config.xzExpand - 0.5f) / 4.5f);
                    drawSlider(ctx, rx + 10, y + 75, 180, "Yükseklik: " + String.format("%.1f", config.yExpand), (config.yExpand - 0.5f) / 3.5f);
                    drawSlider(ctx, rx + 10, y + 110, 180, "Y-Ekseni: " + String.format("%.1f", config.yOffset), (config.yOffset + 2.0f) / 4.0f);
                    drawToggleBtn(ctx, rx + 10, y + 145, 180, 20, config.fakeHitbox, "Gizli (Fake) Hitbox");
                } 
                else if (openSettings.equals("Aura")) {
                    drawSlider(ctx, rx + 10, y + 40, 180, "Normal Menzil: " + String.format("%.1f", auraRange), (auraRange - 2.0f) / 4.0f);
                    drawSlider(ctx, rx + 10, y + 75, 180, "Elytra Menzili: " + String.format("%.1f", elytraRange), (elytraRange - 3.0f) / 4.0f);
                    drawToggleBtn(ctx, rx + 10, y + 110, 180, 20, elytraTarget, "Elytra Target");
                    drawToggleBtn(ctx, rx + 10, y + 140, 180, 20, showAuraParticles, "Görsel Parçacık (Aura_FX)");
                }
            }
            super.render(ctx, mx, my, d);
        }

        private void drawModuleBtn(DrawContext ctx, int x, int y, int w, int h, int mx, int my, String name, boolean state) {
            boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
            int color = state ? ACCENT_ON : (hover ? 0xFF505050 : ACCENT_OFF);
            drawRoundedRect(ctx, x, y, x + w, y + h, color, 4);
            ctx.drawTextWithShadow(textRenderer, name, x + 10, y + 8, state ? 0x000000 : 0xFFFFFF);
            if (openSettings.equals(name)) {
                ctx.fill(x + w - 4, y + 4, x + w, y + h - 4, 0xFFFFFFFF); // Seçili olduğunu gösteren çizgi
            }
        }

        private void drawToggleBtn(DrawContext ctx, int x, int y, int w, int h, boolean state, String name) {
            drawRoundedRect(ctx, x, y, x + w, y + h, state ? 0xFF2A5A4A : 0xFF3A3A3A, 4);
            ctx.drawTextWithShadow(textRenderer, name, x + 8, y + 6, state ? 0x00FFBB : 0xAAAAAA);
        }

        private void drawSlider(DrawContext ctx, int x, int y, int w, String text, float percent) {
            ctx.drawTextWithShadow(textRenderer, text, x, y, 0xFFFFFF);
            drawRoundedRect(ctx, x, y + 12, x + w, y + 20, 0xFF111111, 3);
            drawRoundedRect(ctx, x, y + 12, x + (int)(w * percent), y + 20, ACCENT_ON, 3);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int w = 380, h = 220, x = width/2 - w/2, y = height/2 - h/2;
            int rx = x + 170;

            // Sol Taraf Modül Tıklamaları
            checkModClick(mx, my, btn, x + 10, y + 30, 140, 24, "Hitboxes", () -> hitBoxActive = !hitBoxActive);
            checkModClick(mx, my, btn, x + 10, y + 60, 140, 24, "Aura", () -> auraActive = !auraActive);
            checkModClick(mx, my, btn, x + 10, y + 90, 140, 24, "TriggerBot", () -> triggerBotActive = !triggerBotActive);

            // Sağ Taraf Ayar Tıklamaları
            if (openSettings.equals("Hitboxes")) {
                if (mx >= rx + 10 && mx <= rx + 190) {
                    if (my >= y + 52 && my <= y + 60) { activeSlider = 0; updateSlider(mx, rx + 10); return true; }
                    if (my >= y + 87 && my <= y + 95) { activeSlider = 1; updateSlider(mx, rx + 10); return true; }
                    if (my >= y + 122 && my <= y + 130) { activeSlider = 2; updateSlider(mx, rx + 10); return true; }
                    if (my >= y + 145 && my <= y + 165 && btn == 0) { config.fakeHitbox = !config.fakeHitbox; return true; }
                }
            } else if (openSettings.equals("Aura")) {
                if (mx >= rx + 10 && mx <= rx + 190) {
                    if (my >= y + 52 && my <= y + 60) { activeSlider = 3; updateSlider(mx, rx + 10); return true; }
                    if (my >= y + 87 && my <= y + 95) { activeSlider = 4; updateSlider(mx, rx + 10); return true; }
                    if (my >= y + 110 && my <= y + 130 && btn == 0) { elytraTarget = !elytraTarget; return true; }
                    if (my >= y + 140 && my <= y + 160 && btn == 0) { showAuraParticles = !showAuraParticles; return true; }
                }
            }
            return super.mouseClicked(mx, my, btn);
        }

        private void checkModClick(double mx, double my, int btn, int bx, int by, int bw, int bh, String modName, Runnable toggleAction) {
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + bh) {
                if (btn == 0) toggleAction.run(); // Sol tık aç/kapat
                else if (btn == 1) openSettings = modName; // Sağ tık ayarları aç
            }
        }

        @Override
        public boolean mouseReleased(double mx, double my, int btn) { activeSlider = -1; return super.mouseReleased(mx, my, btn); }

        @Override
        public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (activeSlider != -1) {
                int w = 380, x = width/2 - w/2;
                updateSlider(mx, x + 180);
                return true;
            }
            return super.mouseDragged(mx, my, btn, dx, dy);
        }

        private void updateSlider(double mx, int startX) {
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            float p = (float)(mx - startX) / 180f;
            p = Math.max(0, Math.min(1, p));

            if (activeSlider == 0) config.xzExpand = 0.5f + (p * 4.5f);
            else if (activeSlider == 1) config.yExpand = 0.5f + (p * 3.5f);
            else if (activeSlider == 2) config.yOffset = -2.0f + (p * 4.0f);
            else if (activeSlider == 3) auraRange = 2.0f + (p * 4.0f);
            else if (activeSlider == 4) elytraRange = 3.0f + (p * 4.0f);
        }
    }

    // --- YARDIMCI ARAÇLAR ---
    private void drawRoundedRect(DrawContext ctx, int x1, int y1, int x2, int y2, int color, int r) {
        ctx.fill(x1 + r, y1, x2 - r, y2, color); ctx.fill(x1, y1 + r, x2, y2 - r, color);
        ctx.fill(x1+1, y1+1, x1+r, y1+r, color); ctx.fill(x2-r, y1+1, x2-1, y1+r, color);
    }

    private void iconBtn(Screen s, ItemStack i, String t, int x, int y, int w, int h, ButtonWidget.PressAction a) {
        Screens.getButtons(s).add(new ButtonWidget(x, y, w, h, Text.literal(""), a, scr -> Text.empty()) {
            @Override public void renderWidget(DrawContext ctx, int mx, int my, float d) {
                drawRoundedRect(ctx, getX(), getY(), getX()+w, getY()+h, isHovered() ? 0xFF333333 : 0xFF1A1A1A, 4);
                ctx.drawItem(i, getX()+3, getY()+3);
                if (isHovered()) ctx.drawText(MinecraftClient.getInstance().textRenderer, t, mx+10, my-10, 0xFFFFFF, true);
            }
        });
    }

    private void checkToggle(MinecraftClient c, int k, java.util.function.Supplier<String> a, boolean l, java.util.function.Consumer<Boolean> s) {
        boolean n = GLFW.glfwGetKey(c.getWindow().getHandle(), k) == GLFW.GLFW_PRESS;
        if (n && !l) { String m = a.get(); if (!m.isEmpty()) c.player.sendMessage(Text.literal(m), true); }
        s.accept(n);
    }

    private boolean isArmor(ItemStack s) { 
        String n = s.getItem().toString(); 
        return n.contains("helmet") || n.contains("chestplate") || n.contains("leggings") || n.contains("boots"); 
    }
}
