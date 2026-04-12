package exloran.hitx;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;

public class HitX implements ClientModInitializer {

    // Mod Sistem Durumları
    public static boolean hitBoxActive = false;
    public static boolean triggerBotActive = false;
    public static boolean auraActive = false;
    
    private boolean mLast = false, hLast = false;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);
        HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

        // Butonlar (Sandık ve Envanter)
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            int bx = W / 2 + 92, by = H / 2 - 50; 
            
            if (screen instanceof InventoryScreen inv) {
                int id = inv.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.DIAMOND_CHESTPLATE), "§bZırhı Giy", bx, by, 22, 22, b -> {
                    for (int i = 9; i < 45; i++) if (isArmor(inv.getScreenHandler().getSlot(i).getStack())) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                });
                iconBtn(screen, new ItemStack(Items.BARRIER), "§cHerşeyi At", bx, by + 25, 22, 22, b -> {
                    for (int i = 9; i < 45; i++) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player);
                });
            }

            if (screen instanceof GenericContainerScreen chest) {
                int id = chest.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.HOPPER), "§aHepsini Al", bx, by, 22, 22, b -> {
                    for (int i = 0; i < chest.getScreenHandler().getInventory().size(); i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                });
                iconBtn(screen, new ItemStack(Items.CHEST), "§eHerşeyi Koy", bx, by + 25, 22, 22, b -> {
                    int s = chest.getScreenHandler().getInventory().size();
                    for (int i = s; i < s + 36; i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                });
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            
            // Tuş Kontrolleri
            checkToggle(client, GLFW.GLFW_KEY_M, () -> { client.setScreen(new HitXMenu()); return ""; }, mLast, v -> mLast = v);
            checkToggle(client, GLFW.GLFW_KEY_H, () -> { config.fakeHitbox = !config.fakeHitbox; return "§7Fake Hitbox: " + (config.fakeHitbox ? "§aAÇIK" : "§cKAPALI"); }, hLast, v -> hLast = v);

            // Gelişmiş Hitbox Mantığı
            if (hitBoxActive) {
                float wMul = config.xzExpand;
                float hMul = config.yExpand;
                float yOff = config.yOffset;

                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity && e != client.player) {
                        float w = 0.6f * wMul;
                        float h = 1.8f * hMul;
                        
                        if (config.fakeHitbox) {
                            boolean isTargeted = client.player.distanceTo(e) < 5.0f;
                            if (isTargeted) {
                                e.setBoundingBox(new Box(e.getX() - w/2, e.getY() + yOff, e.getZ() - w/2, e.getX() + w/2, e.getY() + h + yOff, e.getZ() + w/2));
                            } else {
                                e.setBoundingBox(new Box(e.getX()-0.3, e.getY(), e.getZ()-0.3, e.getX()+0.3, e.getY()+1.8, e.getZ()+0.3));
                            }
                        } else {
                            e.setBoundingBox(new Box(e.getX() - w/2, e.getY() + yOff, e.getZ() - w/2, e.getX() + w/2, e.getY() + h + yOff, e.getZ() + w/2));
                        }
                    }
                }
            } else {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity && e != client.player && (e.getBoundingBox().maxX - e.getBoundingBox().minX) > 0.7) {
                        e.setBoundingBox(new Box(e.getX()-0.3, e.getY(), e.getZ()-0.3, e.getX()+0.3, e.getY()+1.8, e.getZ()+0.3));
                    }
                }
            }

            // Savaş Modülleri (Aura & TriggerBot)
            if (triggerBotActive || auraActive) {
                // Silahın bekleme süresi (cooldown) dolmuş mu kontrol et
                if (client.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                    Entity targetToAttack = null;

                    // TriggerBot: Ekranda (Crosshair) bakılan varlık
                    if (triggerBotActive && client.crosshairTarget instanceof EntityHitResult hitRes) {
                        if (hitRes.getEntity() instanceof LivingEntity le && le != client.player && le.isAlive()) {
                            targetToAttack = le;
                        }
                    }

                    // Aura (KillAura): Yakındaki ilk varlık
                    if (auraActive && targetToAttack == null) {
                        double closestDist = 20.25; // 4.5 blok menzil (Karesi)
                        for (Entity e : client.world.getEntities()) {
                            if (e instanceof LivingEntity le && le != client.player && le.isAlive()) {
                                double distSq = client.player.squaredDistanceTo(le);
                                if (distSq <= closestDist) {
                                    closestDist = distSq;
                                    targetToAttack = le;
                                }
                            }
                        }
                    }

                    // Vurma İşlemi
                    if (targetToAttack != null) {
                        client.interactionManager.attackEntity(client.player, targetToAttack);
                        client.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            }
        });
    }

    // --- GELİŞMİŞ THUNDER THEME MENÜ ---
    public class HitXMenu extends Screen {
        private int activeSlider = -1;

        // ThunderHack Tema Renkleri
        private final int TH_BG = 0xFA251B29;       // Koyu arka plan
        private final int TH_HEADER = 0xFA32233C;   // Üst Logo Kısmı
        private final int TH_ON = 0xFF855DA2;       // Aktif Mod rengi (Açık Mor)
        private final int TH_OFF = 0xFF32233C;      // Kapalı Mod rengi (Koyu Mor)
        private final int TH_SETTING = 0xFF19141E;  // Ayar arka planı

        protected HitXMenu() { super(Text.literal("HitX")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int w = 220, h = 250, x = width/2 - w/2, y = height/2 - h/2;
            
            // Ana Arka Plan ve Header
            drawRoundedRect(ctx, x, y, x + w, y + h, TH_BG, 8);
            drawRoundedRect(ctx, x, y, x + w, y + 25, TH_HEADER, 8);
            ctx.fill(x, y + 15, x + w, y + 25, TH_HEADER); // Alt kısmı düzleştirmek için
            
            ctx.drawCenteredTextWithShadow(textRenderer, "§lTHUNDER HITX+", width/2, y + 8, 0xFFFFFF);

            // Mod Butonları
            int btnX = x + 15;
            int btnW = w - 30;
            int btnH = 22;

            drawToggleBtn(ctx, btnX, y + 35, btnW, btnH, hitBoxActive, "Gelişmiş Hitbox");
            drawToggleBtn(ctx, btnX, y + 62, btnW, btnH, triggerBotActive, "TriggerBot");
            drawToggleBtn(ctx, btnX, y + 89, btnW, btnH, auraActive, "Aura (KillAura)");
            drawToggleBtn(ctx, btnX, y + 116, btnW, btnH, config.fakeHitbox, "Gizli (Fake) Hitbox");

            // Slider Alanı
            int sx = width/2 - 80, sw = 160, sh = 8;
            
            int sy1 = y + 165;
            ctx.drawCenteredTextWithShadow(textRenderer, "Genişlik: " + String.format("%.2f", config.xzExpand), width/2, sy1 - 12, 0xAAAAAA);
            drawRoundedRect(ctx, sx, sy1, sx + sw, sy1 + sh, TH_SETTING, 3);
            float fill1 = (config.xzExpand - 0.5f) / 4.5f;
            drawRoundedRect(ctx, sx, sy1, sx + (int)(sw * fill1), sy1 + sh, TH_ON, 3);

            int sy2 = y + 195;
            ctx.drawCenteredTextWithShadow(textRenderer, "Yükseklik: " + String.format("%.2f", config.yExpand), width/2, sy2 - 12, 0xAAAAAA);
            drawRoundedRect(ctx, sx, sy2, sx + sw, sy2 + sh, TH_SETTING, 3);
            float fill2 = (config.yExpand - 0.5f) / 3.5f;
            drawRoundedRect(ctx, sx, sy2, sx + (int)(sw * fill2), sy2 + sh, TH_ON, 3);

            int sy3 = y + 225;
            ctx.drawCenteredTextWithShadow(textRenderer, "Yukarı/Aşağı: " + String.format("%.2f", config.yOffset), width/2, sy3 - 12, 0xAAAAAA);
            drawRoundedRect(ctx, sx, sy3, sx + sw, sy3 + sh, TH_SETTING, 3);
            float fill3 = (config.yOffset + 2.0f) / 4.0f;
            drawRoundedRect(ctx, sx, sy3, sx + (int)(sw * fill3), sy3 + sh, TH_ON, 3);
            
            super.render(ctx, mx, my, d);
        }

        private void drawToggleBtn(DrawContext ctx, int x, int y, int w, int h, boolean state, String name) {
            drawRoundedRect(ctx, x, y, x + w, y + h, state ? TH_ON : TH_OFF, 5);
            String status = state ? "§aAÇIK" : "§7KAPALI";
            ctx.drawTextWithShadow(textRenderer, name, x + 8, y + 7, 0xFFFFFF);
            ctx.drawTextWithShadow(textRenderer, status, x + w - textRenderer.getWidth(status) - 8, y + 7, 0xFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int w = 220, h = 250;
            int x = width/2 - w/2;
            int y = height/2 - h/2;
            
            int btnX = x + 15;
            int btnW = w - 30;
            int btnH = 22;

            // Toggles Tıklama Kontrolü
            if (mx >= btnX && mx <= btnX + btnW) {
                if (my >= y + 35 && my <= y + 35 + btnH) { hitBoxActive = !hitBoxActive; return true; }
                if (my >= y + 62 && my <= y + 62 + btnH) { triggerBotActive = !triggerBotActive; return true; }
                if (my >= y + 89 && my <= y + 89 + btnH) { auraActive = !auraActive; return true; }
                if (my >= y + 116 && my <= y + 116 + btnH) { config.fakeHitbox = !config.fakeHitbox; return true; }
            }

            // Slider Tıklama Kontrolleri
            int sx = width/2 - 80, sw = 160;
            if (mx >= sx - 5 && mx <= sx + sw + 5) {
                if (my >= y + 155 && my <= y + 175) { activeSlider = 0; updateSlider(mx); return true; }
                if (my >= y + 185 && my <= y + 205) { activeSlider = 1; updateSlider(mx); return true; }
                if (my >= y + 215 && my <= y + 235) { activeSlider = 2; updateSlider(mx); return true; }
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int btn) {
            activeSlider = -1;
            return super.mouseReleased(mx, my, btn);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (activeSlider != -1) {
                updateSlider(mx);
                return true;
            }
            return super.mouseDragged(mx, my, btn, dx, dy);
        }

        private void updateSlider(double mx) {
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            float p = (float)(mx - (width/2 - 80)) / 160f;
            p = Math.max(0, Math.min(1, p));

            if (activeSlider == 0) config.xzExpand = 0.5f + (p * 4.5f);
            else if (activeSlider == 1) config.yExpand = 0.5f + (p * 3.5f);
            else if (activeSlider == 2) config.yOffset = -2.0f + (p * 4.0f);
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
            
