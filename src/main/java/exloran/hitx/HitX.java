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
    private float selectItemX = 0f;
    private final List<TargetParticle> particles = new ArrayList<>();

    public static boolean hitBoxActive = false;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);
        HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

        // Butonlar (Sandık ve Envanter) - Biraz Aşağı ve İleri Alındı
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            int bx = W / 2 + 92, by = H / 2 - 50; // Konum ayarlama
            
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
            
            // Menü (M), HUD (R), Fake Hitbox (H)
            checkToggle(client, GLFW.GLFW_KEY_M, () -> { client.setScreen(new HitXMenu()); return ""; }, mLast, v -> mLast = v);
            checkToggle(client, GLFW.GLFW_KEY_R, () -> { config.hudVisible = !config.hudVisible; return "§7HUD: " + (config.hudVisible ? "§aAÇIK" : "§cKAPALI"); }, rLast, v -> rLast = v);
            checkToggle(client, GLFW.GLFW_KEY_H, () -> { config.fakeHitbox = !config.fakeHitbox; return "§7Fake Hitbox: " + (config.fakeHitbox ? "§aAÇIK" : "§cKAPALI"); }, hLast, v -> hLast = v);

            // Hitbox Mantığı
            if (hitBoxActive) {
                float wMul = config.fakeHitbox ? 1.2f : config.xzExpand; // Fake ise az büyüt, değilse configten al
                float hMul = config.fakeHitbox ? 1.05f : config.yExpand;
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity && e != client.player) {
                        float w = 0.6f * wMul, h = 1.8f * hMul;
                        e.setBoundingBox(new Box(e.getX()-w/2, e.getY(), e.getZ()-w/2, e.getX()+w/2, e.getY()+h, e.getZ()+w/2));
                    }
                }
            } else {
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

            // 1. SADECE FPS YAZISI (Config Ölçeği Uygulandı)
            ctx.getMatrices().push();
            ctx.getMatrices().translate(config.hudX, config.hudY, 0);
            ctx.getMatrices().scale(config.hudScale, config.hudScale, 1.0f);
            ctx.drawText(mc.textRenderer, mc.getCurrentFps() + " FPS", 0, 0, mainColor, true);
            ctx.getMatrices().pop();

            // 2. TARGET HUD
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

    // --- HITBOX MENÜSÜ ---
    public class HitXMenu extends Screen {
        protected HitXMenu() { super(Text.literal("HitX")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int w = 160, h = 120, x = width/2 - w/2, y = height/2 - h/2;
            drawRoundedRect(ctx, x, y, x + w, y + h, 0xEE050505, 10);
            ctx.fill(x + 5, y, x + w - 5, y + 2, getVibrantRGB(0, 1f, config));
            ctx.drawCenteredTextWithShadow(textRenderer, "§lHITX PANEL", width/2, y + 10, 0xFFFFFF);
            
            // Aktif/Pasif Butonu
            int bx = width/2 - 40, by = y + 30, bw = 80, bh = 20;
            drawRoundedRect(ctx, bx, by, bx + bw, by + bh, hitBoxActive ? 0xFF104010 : 0xFF401010, 5);
            ctx.drawCenteredTextWithShadow(textRenderer, hitBoxActive ? "§aAKTİF" : "§cKAPALI", width/2, by + 6, 0xFFFFFF);

            // Boyut Sliderı
            int sx = width/2 - 60, sy = y + 70, sw = 120, sh = 8;
            ctx.drawCenteredTextWithShadow(textRenderer, "Boyut: " + String.format("%.2f", config.xzExpand), width/2, sy - 12, 0xAAAAAA);
            drawRoundedRect(ctx, sx, sy, sx + sw, sy + sh, 0xFF1A1A1A, 3);
            float fill = (config.xzExpand - 0.5f) / 4.5f;
            drawRoundedRect(ctx, sx, sy, sx + (int)(sw * fill), sy + sh, getVibrantRGB(0, 1f, config), 3);

            // Fake Hitbox Durumu
            ctx.drawCenteredTextWithShadow(textRenderer, "Fake Mod: " + (config.fakeHitbox ? "§aAÇIK" : "§7KAPALI"), width/2, y + 95, 0xFFFFFF);
            
            super.render(ctx, mx, my, d);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int x = width/2 - 80, y = height/2 - 60;
            if (mx >= width/2-40 && mx <= width/2+40 && my >= y+30 && my <= y+50) {
                hitBoxActive = !hitBoxActive;
                return true;
            }
            // Slider Tıklama
            if (mx >= width/2-60 && mx <= width/2+60 && my >= y+70 && my <= y+78) {
                updateSlider(mx); return true;
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            updateSlider(mx); return true;
        }

        private void updateSlider(double mx) {
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            float p = (float)(mx - (width/2 - 60)) / 120f;
            config.xzExpand = 0.5f + (Math.max(0, Math.min(1, p)) * 4.5f);
            config.yExpand = 1.0f + (Math.max(0, Math.min(1, p)) * 1.5f);
        }
    }

    // --- YARDIMCI ARAÇLAR ---
    public static int getVibrantRGB(int offset, float alpha, HitXConfig config) {
        if (!config.rgbAnimation) return ((int)(alpha * 255) << 24) | 0x00FFBB; // Sabit Turkuaz
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
    private boolean isTrash(ItemStack s) { return s.isOf(Items.ROTTEN_FLESH) || s.isOf(Items.DIRT) || s.isOf(Items.COBBLESTONE); }

    public static class TargetParticle {
        float x, y, mx, my, age = 20;
        public TargetParticle(float x, float y) { this.x = x; this.y = y; this.mx = (float)(Math.random()-0.5)*2f; this.my = (float)(Math.random()-0.5)*2f; }
        public boolean update() { x += mx; y += my; return --age < 0; }
        public void render(DrawContext ctx, int bx, int by, int c) { ctx.fill((int)(bx+x), (int)(by+y), (int)(bx+x+2), (int)(by+y+2), ((int)((age/20f)*255) << 24) | (c & 0xFFFFFF)); }
    }
                }
                                 
