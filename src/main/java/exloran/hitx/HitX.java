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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
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

    private boolean hudOn = true, tagOn = true;
    private PlayerEntity target = null;
    private float alpha = 0f;
    private boolean rLast = false, nLast = false, pLast = false, mLast = false;
    private static final double RANGE = 6.5, DOT = 0.97;
    private static final float FADE = 0.15f;
    private float selectItemX = 0f;
    private final List<TargetParticle> particles = new ArrayList<>();

    // --- HITBOX AYARLARI ---
    public static boolean hitBoxActive = false;
    public static boolean thickMode = false; // Sağ tık ile açılan renksiz kalın mod
    public static float hbWidthPercent = 2.04f;
    public static float hbHeightPercent = 1.13f;
    public static float xzExpand = 2.04f;
    public static float yExpand = 1.13f;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        // Envanter Butonları
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            if (screen instanceof InventoryScreen inv) {
                int x = W / 2 + 78, y = H / 2 - 75, id = inv.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.DIAMOND_CHESTPLATE), "§bZırhı Giy", x, y, 22, 22, b -> {
                    for (int i = 9; i < 45; i++) if (isArmor(inv.getScreenHandler().getSlot(i).getStack())) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                });
                iconBtn(screen, new ItemStack(Items.BARRIER), "§cHerşeyi At", x, y + 25, 22, 22, b -> {
                    for (int i = 9; i < 45; i++) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player);
                });
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            hbWidthPercent = xzExpand; hbHeightPercent = yExpand;

            // Menü Açma (M Tuşu)
            boolean m = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
            if (m && !mLast) client.setScreen(new HitXMenu());
            mLast = m;

            // Diğer Tuşlar
            checkToggle(client, GLFW.GLFW_KEY_R, () -> { hudOn = !hudOn; return ""; }, rLast, val -> rLast = val);
            
            // Hitbox Uygulama
            if (hitBoxActive) {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity && e != client.player) {
                        float w = 0.6f * hbWidthPercent, h = 1.8f * hbHeightPercent;
                        e.setBoundingBox(new Box(e.getX()-w/2, e.getY(), e.getZ()-w/2, e.getX()+w/2, e.getY()+h, e.getZ()+w/2));
                    }
                }
            } else {
                // Resetleme
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity && e != client.player && e.getBoundingBox().getXLength() > 0.7) {
                        e.setBoundingBox(new Box(e.getX()-0.3, e.getY(), e.getZ()-0.3, e.getX()+0.3, e.getY()+1.8, e.getZ()+0.3));
                    }
                }
            }

            // Target Mantığı
            boolean show = false;
            if (client.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity pl && pl.isAlive()) { target = pl; show = true; }
            if (!show) {
                Vec3d eye = client.player.getCameraPosVec(1f), look = client.player.getRotationVec(1f).normalize();
                for (PlayerEntity c : client.world.getEntitiesByClass(PlayerEntity.class, client.player.getBoundingBox().expand(RANGE), ent -> ent != client.player && ent.isAlive())) {
                    if (look.dotProduct(c.getCameraPosVec(1f).subtract(eye).normalize()) > DOT) { target = c; show = true; break; }
                }
            }
            if (!show) target = null;
            alpha = show && hudOn ? Math.min(1f, alpha + FADE) : Math.max(0f, alpha - FADE);
            if (config.particleOn && target != null && alpha > 0.5f) particles.add(new TargetParticle(client.world.random.nextFloat() * 155, client.world.random.nextFloat() * 46));
            particles.removeIf(TargetParticle::update);
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
            int mainColor = getVibrantRGB(0, 1.0f);

            // Sadece FPS Yazısı
            ctx.drawText(mc.textRenderer, "§lHitX §r| " + mc.getCurrentFps() + " FPS", 10, 10, mainColor, true);

            renderHotbar(ctx, mc, sw, sh, tick.getTickDelta(true), mainColor);

            // Target HUD (Modern & Yuvarlak)
            if (alpha > 0.01f) {
                int bW = 155, bH = 46, bX = sw/2 - bW/2, bY = sh/2 + 20;
                drawRoundedRect(ctx, bX, bY, bX + bW, bY + bH, (int)(alpha * 200) << 24 | 0x0A0A0A, 6);
                ctx.fill(bX + 3, bY, bX + bW - 3, bY + 1, getVibrantRGB(0, alpha));
                if (target != null) {
                    ctx.drawTexture(mc.getSkinProvider().getSkinTextures(target.getGameProfile()).texture(), bX + 6, bY + 7, 20, 20, 8, 8, 8, 8, 64, 64);
                    ctx.drawText(mc.textRenderer, target.getName().getString(), bX + 34, bY + 10, 0xFFFFFF, true);
                    float r = target.getHealth() / target.getMaxHealth();
                    ctx.fill(bX + 34, bY + 25, bX + 144, bY + 30, 0xFF222222);
                    ctx.fill(bX + 34, bY + 25, bX + 34 + (int)(r * 110), bY + 30, getVibrantRGB(0, alpha));
                }
                for (TargetParticle p : particles) p.render(ctx, bX, bY, getVibrantRGB(0, alpha));
            }
        });
    }

    // --- ÖZEL HİTBOX MENÜSÜ ---
    public class HitXMenu extends Screen {
        protected HitXMenu() { super(Text.literal("HitX")); }
        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            int w = 140, h = 80, x = width/2 - w/2, y = height/2 - h/2;
            drawRoundedRect(ctx, x, y, x + w, y + h, 0xEE0A0A0A, 8);
            ctx.fill(x + 2, y, x + w - 2, y + 1, getVibrantRGB(0, 1f));
            ctx.drawCenteredTextWithShadow(textRenderer, "§lHITX KONTROL", width/2, y + 10, 0xFFFFFF);
            
            // Buton Çizimi (Manuel)
            int bx = width/2 - 50, by = y + 35, bw = 100, bh = 25;
            boolean hover = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
            drawRoundedRect(ctx, bx, by, bx + bw, by + bh, hover ? 0xFF2A2A2A : 0xFF1A1A1A, 5);
            String status = hitBoxActive ? (thickMode ? "§7KALIN (RENKSİZ)" : "§aAKTİF") : "§cKAPALI";
            ctx.drawCenteredTextWithShadow(textRenderer, status, width/2, by + 8, 0xFFFFFF);
            
            super.render(ctx, mx, my, d);
        }
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int bx = width/2 - 50, by = height/2 - 5, bw = 100, bh = 25;
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + bh) {
                if (btn == 0) { // Sol Tık: Normal Aç/Kapat
                    hitBoxActive = !hitBoxActive;
                    thickMode = false;
                } else if (btn == 1) { // Sağ Tık: Kalın Renksiz Mod
                    hitBoxActive = true;
                    thickMode = !thickMode;
                }
                MinecraftClient.getInstance().player.playSound(net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 1f, 1f);
                return true;
            }
            return super.mouseClicked(mx, my, btn);
        }
    }

    // --- YARDIMCI METOTLAR ---
    private void drawRoundedRect(DrawContext ctx, int x1, int y1, int x2, int y2, int color, int r) {
        ctx.fill(x1 + r, y1, x2 - r, y2, color);
        ctx.fill(x1, y1 + r, x2, y2 - r, color);
        ctx.fill(x1+1, y1+1, x1+r, y1+r, color); ctx.fill(x2-r, y1+1, x2-1, y1+r, color);
        ctx.fill(x1+1, y2-r, x1+r, y2-1, color); ctx.fill(x2-r, y2-r, x2-1, y2-1, color);
    }

    public static int getVibrantRGB(int offset, float alpha) {
        float h = ((System.currentTimeMillis() + offset) % 4000) / 4000f;
        return ((int)(alpha * 255) << 24) | (Color.HSBtoRGB(h, 0.8f, 1f) & 0xFFFFFF);
    }

    private void renderHotbar(DrawContext ctx, MinecraftClient mc, int sw, int sh, float d, int color) {
        int w = 182, h = 22, x = (sw-w)/2, y = sh-25;
        selectItemX = lerp(selectItemX, mc.player.getInventory().selectedSlot * 20f, d * 0.2f);
        drawRoundedRect(ctx, x-2, y-2, x+w+2, y+h+2, 0xAA000000, 4);
        int sx = (int)(x + selectItemX);
        ctx.fill(sx, y, sx+22, y+22, (80 << 24) | (color & 0xFFFFFF));
        ctx.fill(sx, y, sx+22, y+1, color);
        for (int i=0; i<9; i++) ctx.drawItem(mc.player.getInventory().main.get(i), x+i*20+3, y+3);
    }

    private void iconBtn(Screen s, ItemStack i, String t, int x, int y, int w, int h, ButtonWidget.PressAction a) {
        Screens.getButtons(s).add(new ButtonWidget(x, y, w, h, Text.literal(""), a, scr -> Text.empty()) {
            @Override public void renderWidget(DrawContext ctx, int mx, int my, float d) {
                drawRoundedRect(ctx, getX(), getY(), getX()+getWidth(), getY()+getHeight(), isHovered() ? 0xFF2A2A2A : 0xFF1A1A1A, 4);
                ctx.drawItem(i, getX()+3, getY()+3);
                if (isHovered()) {
                    int tw = MinecraftClient.getInstance().textRenderer.getWidth(t);
                    drawRoundedRect(ctx, mx+8, my-12, mx+tw+14, my+2, 0xEE050505, 3);
                    ctx.drawText(MinecraftClient.getInstance().textRenderer, t, mx+11, my-9, 0xFFFFFF, true);
                }
            }
        });
    }

    private void checkToggle(MinecraftClient c, int k, java.util.function.Supplier<String> a, boolean l, java.util.function.Consumer<Boolean> s) {
        boolean n = GLFW.glfwGetKey(c.getWindow().getHandle(), k) == GLFW.GLFW_PRESS;
        if (n && !l) { String msg = a.get(); if (!msg.isEmpty()) c.player.sendMessage(Text.literal(msg), true); }
        s.accept(n);
    }

    private float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private boolean isTrash(ItemStack s) { return s.isOf(Items.ROTTEN_FLESH) || s.isOf(Items.DIRT) || s.isOf(Items.COBBLESTONE); }
    private boolean isArmor(ItemStack s) { String n = s.getItem().toString(); return n.contains("helmet") || n.contains("chestplate") || n.contains("leggings") || n.contains("boots"); }

    public static class TargetParticle {
        float x, y, mx, my, age = 20;
        public TargetParticle(float x, float y) { this.x = x; this.y = y; this.mx = (float)(Math.random()-0.5)*2f; this.my = (float)(Math.random()-0.5)*2f; }
        public boolean update() { x += mx; y += my; return --age < 0; }
        public void render(DrawContext ctx, int bx, int by, int c) { ctx.fill((int)(bx+x), (int)(by+y), (int)(bx+x+2), (int)(by+y+2), ((int)((age/20f)*255) << 24) | (c & 0xFFFFFF)); }
    }
}
