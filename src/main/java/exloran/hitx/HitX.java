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
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
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
    private boolean rLast = false, nLast = false, pLast = false, hLast = false;
    private static final double RANGE = 6.5, DOT = 0.97;
    private static final float FADE = 0.15f;
    private float selectItemX = 0f;
    private final List<TargetParticle> particles = new ArrayList<>();

    public static boolean hitBoxActive = false;
    public static boolean invisibleHb = true;
    public static float hbWidthPercent = 2.0402f;
    public static float hbHeightPercent = 1.1305f;
    
    public static float xzExpand = 0.1f;
    public static float yExpand = 0.0f;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            if (screen instanceof GenericContainerScreen chest) {
                int sx = W / 2 + 92, sy = H / 2 - 80, id = chest.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.HOPPER), "Hepsini Al", sx, sy, 24, 20, b -> { int s = chest.getScreenHandler().getInventory().size(); for (int i = 0; i < s; i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); });
                iconBtn(screen, new ItemStack(Items.CHEST), "Hepsini Koy", sx, sy + 24, 24, 20, b -> { int s = chest.getScreenHandler().getInventory().size(); for (int i = s; i < s + 36; i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); });
                iconBtn(screen, new ItemStack(Items.LAVA_BUCKET), "Çöpleri Sil", sx, sy + 48, 24, 20, b -> { for (int i = 0; i < chest.getScreenHandler().slots.size(); i++) { if (isTrash(chest.getScreenHandler().getSlot(i).getStack())) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player); } });
            }
            if (screen instanceof InventoryScreen inv) {
                int x = W / 2 - 25, y = H / 2 - 83, id = inv.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.DIAMOND_CHESTPLATE), "Zırh Giy", x, y, 24, 20, b -> { for (int i = 9; i < 45; i++) { if (isArmor(inv.getScreenHandler().getSlot(i).getStack())) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); } });
                iconBtn(screen, new ItemStack(Items.BARRIER), "Temizle", x + 28, y, 24, 20, b -> { for (int i = 9; i < 45; i++) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player); });
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            checkToggle(client, GLFW.GLFW_KEY_R, () -> { hudOn = !hudOn; return hudOn ? "§dHUD: ON" : "§7HUD: OFF"; }, rLast, val -> rLast = val);
            checkToggle(client, GLFW.GLFW_KEY_N, () -> { tagOn = !tagOn; return tagOn ? "§bBar: ON" : "§7Bar: OFF"; }, nLast, val -> nLast = val);
            checkToggle(client, GLFW.GLFW_KEY_P, () -> { config.particleOn = !config.particleOn; return config.particleOn ? "§5Efekt: ON" : "§7Efekt: OFF"; }, pLast, val -> pLast = val);

            boolean h = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_H) == GLFW.GLFW_PRESS;
            if (h && !hLast) {
                hitBoxActive = !hitBoxActive;
                client.player.sendMessage(Text.literal(hitBoxActive ? "§aHitbox Aktif" : "§cHitbox Kapalı"), true);
                if (!hitBoxActive) {
                    for (Entity e : client.world.getEntities()) {
                        if (e instanceof LivingEntity && e != client.player) {
                            e.setBoundingBox(new Box(e.getX()-0.3, e.getY(), e.getZ()-0.3, e.getX()+0.3, e.getY()+1.8, e.getZ()+0.3));
                        }
                    }
                }
            }
            hLast = h;

            if (client.options.forwardKey.isPressed() && !client.player.horizontalCollision && client.player.getHungerManager().getFoodLevel() > 6) client.player.setSprinting(true);
            if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));

            if (hitBoxActive) {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity && e != client.player && (invisibleHb || !e.isInvisible())) {
                        float w = 0.6f * hbWidthPercent, hght = 1.8f * hbHeightPercent;
                        e.setBoundingBox(new Box(e.getX()-w/2, e.getY(), e.getZ()-w/2, e.getX()+w/2, e.getY()+hght, e.getZ()+w/2));
                    }
                }
            }

            boolean show = false;
            if (client.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity pl && pl.isAlive()) { target = pl; show = true; }
            if (!show) {
                Vec3d eye = client.player.getCameraPosVec(1f), look = client.player.getRotationVec(1f).normalize();
                PlayerEntity best = null; double bd = DOT;
                for (PlayerEntity c : client.world.getEntitiesByClass(PlayerEntity.class, client.player.getBoundingBox().expand(RANGE), ent -> ent != client.player && ent.isAlive())) {
                    double d = look.dotProduct(c.getCameraPosVec(1f).subtract(eye).normalize());
                    if (d > bd) { bd = d; best = c; }
                }
                if (best != null) { target = best; show = true; }
            }
            if (!show) target = null;
            alpha = show && hudOn ? Math.min(1f, alpha + FADE) : Math.max(0f, alpha - FADE);

            if (config.particleOn && target != null && alpha > 0.1f && client.world.random.nextFloat() < 0.3f)
                particles.add(new TargetParticle(client.world.random.nextFloat() * 155, client.world.random.nextFloat() * 46));
            particles.removeIf(TargetParticle::update);
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
            int mainColor = getVibrantRGB(0, 1.0f);

            ctx.drawText(mc.textRenderer, "§lHitX §r| " + mc.getCurrentFps() + " FPS", 6, 6, mainColor, true);

            renderHotbar(ctx, mc, sw, sh, tick.getTickDelta(true), mainColor);

            if (tagOn) {
                for (PlayerEntity pl : mc.world.getPlayers()) {
                    if (pl == mc.player || !pl.isAlive() || mc.player.distanceTo(pl) > RANGE + 2) continue;
                    double[] sc = proj(mc, new Vec3d(lerp(pl.lastRenderX, pl.getX(), tick.getTickDelta(true)), lerp(pl.lastRenderY, pl.getY(), tick.getTickDelta(true)) + pl.getHeight() + 0.3, lerp(pl.lastRenderZ, pl.getZ(), tick.getTickDelta(true))), sw, sh);
                    if (sc != null) {
                        int bx = (int) sc[0] - 20, py = (int) sc[1]; float r = pl.getHealth() / pl.getMaxHealth();
                        ctx.fill(bx - 1, py - 1, bx + 41, py + 3, 0x99000000);
                        ctx.fill(bx, py, bx + (int)(r * 40), py + 2, getHealthColor(r));
                    }
                }
            }

            if (alpha > 0.01f) {
                int bW = 155, bH = 46, bX = (sw * config.hudX) / 100 - bW / 2, bY = (sh * config.hudY) / 100 - bH / 2;
                int dynamicColor = getVibrantRGB(0, alpha);
                ctx.getMatrices().push();
                ctx.getMatrices().translate(bX + bW/2f, bY + bH/2f, 0);
                ctx.getMatrices().scale(config.hudScale/100f, config.hudScale/100f, 1);
                ctx.getMatrices().translate(-bW/2f, -bH/2f, 0);
                drawSmoothRect(ctx, 0, 0, bW, bH, (int)(alpha * 200) << 24 | 0x0A0A0A);
                ctx.fill(2, 0, bW - 2, 1, dynamicColor);
                if (target != null) {
                    ctx.drawTexture(mc.getSkinProvider().getSkinTextures(target.getGameProfile()).texture(), 6, 7, 20, 20, 8, 8, 8, 8, 64, 64);
                    ctx.drawText(mc.textRenderer, target.getName().getString(), 34, 10, 0xFFFFFF, true);
                    float r = Math.max(0, Math.min(1, target.getHealth() / target.getMaxHealth()));
                    ctx.fill(34, 25, 144, 30, 0xFF222222);
                    ctx.fill(34, 25, 34 + (int)(r * 110), 30, dynamicColor);
                    ctx.drawText(mc.textRenderer, (int)target.getHealth() + " HP", bW - 38, 32, dynamicColor, true);
                }
                for (TargetParticle p : particles) p.render(ctx, dynamicColor);
                ctx.getMatrices().pop();
            }
        });
    }

    private void renderHotbar(DrawContext ctx, MinecraftClient mc, int sw, int sh, float d, int color) {
        int w = 182, h = 22, x = (sw - w) / 2, y = sh - 25;
        selectItemX = lerp(selectItemX, mc.player.getInventory().selectedSlot * 20f, d * 0.2f);
        drawSmoothRect(ctx, x - 2, y - 2, x + w + 2, y + h + 2, 0x99000000);
        int sx = (int)(x + selectItemX);
        ctx.fill(sx, y, sx + 22, y + 22, (100 << 24) | (color & 0xFFFFFF));
        ctx.fill(sx, y, sx + 22, y + 1, color);
        ctx.fill(sx, y + 21, sx + 22, y + 22, color);
        for (int i = 0; i < 9; i++) {
            ItemStack s = mc.player.getInventory().main.get(i);
            ctx.drawItem(s, x + i * 20 + 3, y + 3);
            ctx.drawItemInSlot(mc.textRenderer, s, x + i * 20 + 3, y + 3);
        }
    }

    private void drawSmoothRect(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1 + 1, y1, x2 - 1, y2, color);
        ctx.fill(x1, y1 + 1, x2, y2 - 1, color);
    }

    public static int getVibrantRGB(int offset, float alpha) {
        float hue = ((System.currentTimeMillis() + offset) % 4000) / 4000f;
        int rgb = Color.HSBtoRGB(hue, 0.8f, 1.0f);
        return ((int)(alpha * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private int getHealthColor(float r) {
        return 0xFF000000 | (r > 0.5 ? ((int)(255 * (1-r)*2) << 16 | 255 << 8) : (255 << 16 | (int)(255 * r * 2) << 8));
    }

    private void checkToggle(MinecraftClient c, int key, java.util.function.Supplier<String> action, boolean last, java.util.function.Consumer<Boolean> setter) {
        boolean now = GLFW.glfwGetKey(c.getWindow().getHandle(), key) == GLFW.GLFW_PRESS;
        if (now && !last) c.player.sendMessage(Text.literal(action.get()), true);
        setter.accept(now);
    }

    private void iconBtn(Screen s, ItemStack i, String t, int x, int y, int w, int h, ButtonWidget.PressAction a) {
        Screens.getButtons(s).add(new ButtonWidget(x, y, w, h, Text.literal(""), a, screen -> Text.empty()) {
            @Override public void renderWidget(DrawContext ctx, int mx, int my, float d) {
                int c = getVibrantRGB(isHovered() ? 0 : 500, 1f);
                drawSmoothRect(ctx, getX(), getY(), getX()+getWidth(), getY()+getHeight(), 0xFF1A1A1A);
                if (isHovered()) ctx.fill(getX(), getY(), getX()+getWidth(), getY()+1, c);
                ctx.drawItem(i, getX() + (getWidth()-16)/2, getY() + (getHeight()-16)/2);
            }
        });
    }

    private double[] proj(MinecraftClient mc, Vec3d w, int sw, int sh) {
        try {
            var cam = mc.gameRenderer.getCamera(); Vec3d rel = w.subtract(cam.getPos());
            if (mc.player.getRotationVec(1f).dotProduct(rel.normalize()) < 0) return null;
            double yr = Math.toRadians(cam.getYaw()), pr = Math.toRadians(cam.getPitch()),
                   rx = rel.x * Math.cos(yr) - rel.z * Math.sin(yr), rz = rel.x * Math.sin(yr) + rel.z * Math.cos(yr),
                   ry2 = rel.y * Math.cos(pr) - rz * Math.sin(pr), rz2 = rel.y * Math.sin(pr) + rz * Math.cos(pr);
            if (rz2 <= 0.1) return null;
            double f = (sh / 2.0) / Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0);
            return new double[]{ sw / 2.0 + (rx / rz2) * f, sh / 2.0 - (ry2 / rz2) * f };
        } catch (Exception e) { return null; }
    }

    private float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private double lerp(double a, double b, float t) { return a + (b - a) * t; }
    private boolean isTrash(ItemStack s) { return s.isOf(Items.ROTTEN_FLESH) || s.isOf(Items.DIRT) || s.isOf(Items.COBBLESTONE) || s.isOf(Items.GRAVEL); }
    private boolean isArmor(ItemStack s) { String n = s.getItem().toString(); return n.contains("helmet") || n.contains("chestplate") || n.contains("leggings") || n.contains("boots"); }

    public static class TargetParticle {
        float x, y, mx, my, age = 20;
        public TargetParticle(float x, float y) { this.x = x; this.y = y; this.mx = (float)(Math.random()-0.5)*1.5f; this.my = (float)(Math.random()-0.5)*1.5f; }
        public boolean update() { x += mx; y += my; return --age < 0; }
        public void render(DrawContext ctx, int c) { ctx.fill((int)x, (int)y, (int)x+2, (int)y+2, ((int)((age/20f)*255) << 24) | (c & 0xFFFFFF)); }
    }
}
