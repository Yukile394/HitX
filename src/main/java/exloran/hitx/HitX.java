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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {

    private PlayerEntity target = null;
    private float alpha = 0f;
    private boolean rLast = false, mLast = false, hLast = false;
    private static final double RANGE = 6.5, DOT = 0.97;
    private static final float FADE = 0.15f;
    private final List<TargetParticle> particles = new ArrayList<>();

    public static boolean hitBoxActive = false;

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
            checkToggle(client, GLFW.GLFW_KEY_R, () -> { config.hudVisible = !config.hudVisible; return "§7HUD: " + (config.hudVisible ? "§aAÇIK" : "§cKAPALI"); }, rLast, v -> rLast = v);
            checkToggle(client, GLFW.GLFW_KEY_H, () -> { config.fakeHitbox = !config.fakeHitbox; return "§7Fake Hitbox: " + (config.fakeHitbox ? "§aAÇIK" : "§cKAPALI"); }, hLast, v -> hLast = v);

            // Gelişmiş Hitbox Mantığı
            if (hitBoxActive) {
                float wMul = config.xzExpand;
                float hMul = config.yExpand;
                float yOff = config.yOffset; // Yeni Config değişkeni (yukarı/aşağı kaydırma)

                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity && e != client.player) {
                        float w = 0.6f * wMul;
                        float h = 1.8f * hMul;
                        
                        if (config.fakeHitbox) {
                            // Gizli Hitbox: Sadece oyuncu hedefe çok yakınken veya vururken hitbox genişler
                            // Diğer zamanlarda F3+B'de normal gözükür.
                            boolean isTargeted = target == e || client.player.distanceTo(e) < 5.0f;
                            if (isTargeted) {
                                e.setBoundingBox(new Box(e.getX() - w/2, e.getY() + yOff, e.getZ() - w/2, e.getX() + w/2, e.getY() + h + yOff, e.getZ() + w/2));
                            } else {
                                // Varsayılan boyutlara geri döndür
                                e.setBoundingBox(new Box(e.getX()-0.3, e.getY(), e.getZ()-0.3, e.getX()+0.3, e.getY()+1.8, e.getZ()+0.3));
                            }
                        } else {
                            // Normal Dev Hitbox (Sürekli görünür)
                            e.setBoundingBox(new Box(e.getX() - w/2, e.getY() + yOff, e.getZ() - w/2, e.getX() + w/2, e.getY() + h + yOff, e.getZ() + w/2));
                        }
                    }
                }
            } else {
                // Mod kapalıysa her şeyi normale çevir
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity && e != client.player && (e.getBoundingBox().maxX - e.getBoundingBox().minX) > 0.7) {
                        e.setBoundingBox(new Box(e.getX()-0.3, e.getY(), e.getZ()-0.3, e.getX()+0.3, e.getY()+1.8, e.getZ()+0.3));
                    }
                }
            }

            // Target Bulma
            boolean show = false;
            if (client.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity pl && pl.isAlive()) { target = pl; show = true; }
            if (!show) {
                Vec3d eye = client.player.getCameraPosVec(1f), look = client.player.getRotationVec(1f).normalize();
                for (PlayerEntity c : client.world.getEntitiesByClass(PlayerEntity.class, client.player.getBoundingBox().expand(RANGE), ent -> ent != client.player && ent.isAlive())) {
                    if (look.dotProduct(c.getCameraPosVec(1f).subtract(eye).normalize()) > DOT) { target = c; show = true; break; }
                }
            }
            if (!show) target = null;
            alpha = show && config.hudVisible ? Math.min(1f, alpha + FADE) : Math.max(0f, alpha - FADE);
            if (config.particleOn && target != null && alpha > 0.5f) particles.add(new TargetParticle(client.world.random.nextFloat() * 155, client.world.random.nextFloat() * 46));
            particles.removeIf(TargetParticle::update);
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            int mainColor = getVibrantRGB(0, 1.0f, config);

            // FPS Yazısı
            ctx.getMatrices().push();
            ctx.getMatrices().translate(config.hudX, config.hudY, 0);
            ctx.getMatrices().scale(config.hudScale, config.hudScale, 1.0f);
            ctx.drawText(mc.textRenderer, mc.getCurrentFps() + " FPS", 0, 0, mainColor, true);
            ctx.getMatrices().pop();

            // Target HUD
            if (alpha > 0.01f) {
                ctx.getMatrices().push();
                int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
                ctx.getMatrices().translate(sw/2f, sh/2f + 40, 0);
                ctx.getMatrices().scale(config.hudScale, config.hudScale, 1.0f);
                
                int bW = 150, bH = 42, bX = -bW/2, bY = 0;
                drawRoundedRect(ctx, bX, bY, bX + bW, bY + bH, (int)(alpha * 200) << 24 | 0x0A0A0A, 6);
                ctx.fill(bX + 2, bY, bX + bW - 2, bY + 1, getVibrantRGB(0, alpha, config));
                
                if (target != null) {
                    ctx.drawTexture(mc.getSkinProvider().getSkinTextures(target.getGameProfile()).texture(), bX + 5, bY + 5, 20, 20, 8, 8, 8, 8, 64, 64);
                    ctx.drawText(mc.textRenderer, target.getName().getString(), bX + 32, bY + 8, 0xFFFFFF, true);
                    float r = target.getHealth() / target.getMaxHealth();
                    ctx.fill(bX + 32, bY + 22, bX + 140, bY + 26, 0xFF222222);
                    ctx.fill(bX + 32, bY + 22, bX + 32 + (int)(r * 108), bY + 26, getVibrantRGB(0, alpha, config));
                }
                ctx.getMatrices().pop();
            }
        });
    }

    // --- GELİŞMİŞ HITBOX MENÜSÜ ---
    public class HitXMenu extends Screen {
        private int activeSlider = -1; // 0: Genişlik, 1: Yükseklik, 2: Yukarı/Aşağı

        protected HitXMenu() { super(Text.literal("HitX")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int w = 180, h = 180, x = width/2 - w/2, y = height/2 - h/2; // Menü boyutu büyütüldü
            
            drawRoundedRect(ctx, x, y, x + w, y + h, 0xEE050505, 10);
            ctx.fill(x + 5, y, x + w - 5, y + 2, getVibrantRGB(0, 1f, config));
            ctx.drawCenteredTextWithShadow(textRenderer, "§lHITX GELİŞMİŞ PANEL", width/2, y + 10, 0xFFFFFF);
            
            // Aktif/Pasif Butonu
            int bx = width/2 - 40, by = y + 30, bw = 80, bh = 20;
            drawRoundedRect(ctx, bx, by, bx + bw, by + bh, hitBoxActive ? 0xFF104010 : 0xFF401010, 5);
            ctx.drawCenteredTextWithShadow(textRenderer, hitBoxActive ? "§aSİSTEM AKTİF" : "§cSİSTEM KAPALI", width/2, by + 6, 0xFFFFFF);

            int sx = width/2 - 70, sw = 140, sh = 8; // Slider genişliği
            
            // 1. Slider: Genişlik (X/Z)
            int sy1 = y + 75;
            ctx.drawCenteredTextWithShadow(textRenderer, "Genişlik: " + String.format("%.2f", config.xzExpand), width/2, sy1 - 12, 0xAAAAAA);
            drawRoundedRect(ctx, sx, sy1, sx + sw, sy1 + sh, 0xFF1A1A1A, 3);
            float fill1 = (config.xzExpand - 0.5f) / 4.5f;
            drawRoundedRect(ctx, sx, sy1, sx + (int)(sw * fill1), sy1 + sh, getVibrantRGB(0, 1f, config), 3);

            // 2. Slider: Yükseklik (Y)
            int sy2 = y + 105;
            ctx.drawCenteredTextWithShadow(textRenderer, "Yükseklik: " + String.format("%.2f", config.yExpand), width/2, sy2 - 12, 0xAAAAAA);
            drawRoundedRect(ctx, sx, sy2, sx + sw, sy2 + sh, 0xFF1A1A1A, 3);
            float fill2 = (config.yExpand - 0.5f) / 3.5f;
            drawRoundedRect(ctx, sx, sy2, sx + (int)(sw * fill2), sy2 + sh, getVibrantRGB(500, 1f, config), 3);

            // 3. Slider: Yukarı/Aşağı (Y-Offset)
            int sy3 = y + 135;
            ctx.drawCenteredTextWithShadow(textRenderer, "Yukarı/Aşağı: " + String.format("%.2f", config.yOffset), width/2, sy3 - 12, 0xAAAAAA);
            drawRoundedRect(ctx, sx, sy3, sx + sw, sy3 + sh, 0xFF1A1A1A, 3);
            float fill3 = (config.yOffset + 2.0f) / 4.0f; // -2.0 ile +2.0 arası
            drawRoundedRect(ctx, sx, sy3, sx + (int)(sw * fill3), sy3 + sh, getVibrantRGB(1000, 1f, config), 3);

            // Fake Hitbox Durumu (Butonlaştırıldı)
            int fbx = width/2 - 50, fby = y + 155, fbw = 100, fbh = 16;
            drawRoundedRect(ctx, fbx, fby, fbx + fbw, fby + fbh, config.fakeHitbox ? 0xFF204060 : 0xFF202020, 4);
            ctx.drawCenteredTextWithShadow(textRenderer, "Gizli Hitbox: " + (config.fakeHitbox ? "§aAÇIK" : "§7KAPALI"), width/2, fby + 4, 0xFFFFFF);
            
            super.render(ctx, mx, my, d);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int w = 180, h = 180, y = height/2 - h/2;
            int sx = width/2 - 70, sw = 140, sh = 8;

            // Ana Şalter Tıklama
            if (mx >= width/2-40 && mx <= width/2+40 && my >= y+30 && my <= y+50) {
                hitBoxActive = !hitBoxActive; return true;
            }
            // Fake Hitbox Tıklama
            if (mx >= width/2-50 && mx <= width/2+50 && my >= y+155 && my <= y+171) {
                config.fakeHitbox = !config.fakeHitbox; return true;
            }

            // Slider Tıklama Kontrolleri (Genişletilmiş Tıklama Alanı)
            if (mx >= sx - 5 && mx <= sx + sw + 5) {
                if (my >= y + 65 && my <= y + 85) { activeSlider = 0; updateSlider(mx); return true; }
                if (my >= y + 95 && my <= y + 115) { activeSlider = 1; updateSlider(mx); return true; }
                if (my >= y + 125 && my <= y + 145) { activeSlider = 2; updateSlider(mx); return true; }
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean mouseReleased(double mx, double my, int btn) {
            activeSlider = -1; // Sürükleme bitti
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
            float p = (float)(mx - (width/2 - 70)) / 140f;
            p = Math.max(0, Math.min(1, p)); // 0 ile 1 arasına sabitle

            if (activeSlider == 0) config.xzExpand = 0.5f + (p * 4.5f); // 0.5 ile 5.0 arası
            else if (activeSlider == 1) config.yExpand = 0.5f + (p * 3.5f); // 0.5 ile 4.0 arası
            else if (activeSlider == 2) config.yOffset = -2.0f + (p * 4.0f); // -2.0 ile 2.0 arası
        }
    }

    // --- YARDIMCI ARAÇLAR ---
    public static int getVibrantRGB(int offset, float alpha, HitXConfig config) {
        if (!config.rgbAnimation) return ((int)(alpha * 255) << 24) | 0x00FFBB;
        float h = ((System.currentTimeMillis() * (int)config.rgbSpeed + offset) % 4000) / 4000f;
        return ((int)(alpha * 255) << 24) | (Color.HSBtoRGB(h, 0.7f, 1f) & 0xFFFFFF);
    }

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

    private boolean isArmor(ItemStack s) { String n = s.getItem().toString(); return n.contains("helmet") || n.contains("chestplate") || n.contains("leggings") || n.contains("boots"); }

    public static class TargetParticle {
        float x, y, mx, my, age = 20;
        public TargetParticle(float x, float y) { this.x = x; this.y = y; this.mx = (float)(Math.random()-0.5)*2f; this.my = (float)(Math.random()-0.5)*2f; }
        public boolean update() { x += mx; y += my; return --age < 0; }
        public void render(DrawContext ctx, int bx, int by, int c) { ctx.fill((int)(bx+x), (int)(by+y), (int)(bx+x+2), (int)(by+y+2), ((int)((age/20f)*255) << 24) | (c & 0xFFFFFF)); }
    }
                }
                    
