package com.exloran.hitx;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {

    private boolean hudOn = true, tagOn = true, particleOn = true;
    private PlayerEntity target = null;
    private float alpha = 0f;
    private boolean rLast = false, nLast = false, pLast = false;
    private static final double RANGE = 6.5, DOT = 0.97;
    private static final float FADE = 0.12f;

    private float selectItemX = 0f;
    private final List<TargetParticle> particles = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            if (screen instanceof GenericContainerScreen chest) {
                int sx = W / 2 + 92, sy = H / 2 - 80, id = chest.getScreenHandler().syncId;
                btn(screen, "Herseyi Al",  sx, sy,      85, 20, b -> { int s = chest.getScreenHandler().getInventory().size(); for (int i = 0; i < s; i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); });
                btn(screen, "Herseyi Koy", sx, sy + 24, 85, 20, b -> { int s = chest.getScreenHandler().getInventory().size(); for (int i = s; i < s + 36; i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); });
                btn(screen, "Herseyi At",  sx, sy + 48, 85, 20, b -> { for (int i = 0; i < chest.getScreenHandler().slots.size(); i++) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player); });
                btn(screen, "Cop At",      sx, sy + 72, 85, 20, b -> { for (int i = 0; i < chest.getScreenHandler().slots.size(); i++) { ItemStack st = chest.getScreenHandler().getSlot(i).getStack(); if (isTrash(st)) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player); } });
            }
            if (screen instanceof InventoryScreen inv) {
                int x = W / 2 - 88, y = H / 2 - 83, id = inv.getScreenHandler().syncId;
                btn(screen, "Zirhi Giy", x - 52, y,      50, 18, b -> { for (int i = 9; i < 45; i++) { ItemStack st = inv.getScreenHandler().getSlot(i).getStack(); if (isArmor(st)) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); } });
                btn(screen, "Temizle",   x - 52, y + 20, 50, 18, b -> { for (int i = 9; i < 45; i++) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player); });
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            boolean r = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
            if (r && !rLast) { hudOn = !hudOn; client.player.sendMessage(Text.literal(hudOn ? "§dHUD Acildi" : "§fHUD Kapatildi"), true); }
            rLast = r;

            boolean n = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_N) == GLFW.GLFW_PRESS;
            if (n && !nLast) { tagOn = !tagOn; client.player.sendMessage(Text.literal(tagOn ? "§dBar Acildi" : "§fBar Kapatildi"), true); }
            nLast = n;

            boolean pKey = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_P) == GLFW.GLFW_PRESS;
            if (pKey && !pLast) { 
                config.particleOn = !config.particleOn; 
                client.player.sendMessage(Text.literal(config.particleOn ? "§dPartiküller Acildi" : "§fPartiküller Kapatildi"), true); 
            }
            pLast = pKey;

            if (client.options.forwardKey.isPressed() && !client.player.horizontalCollision && !client.player.isSneaking() && client.player.getHungerManager().getFoodLevel() > 6)
                client.player.setSprinting(true);

            if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION))
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));

            boolean show = false;
            if (client.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity p && p.isAlive()) { target = p; show = true; }
            if (!show) {
                Vec3d eye = client.player.getCameraPosVec(1f), look = client.player.getRotationVec(1f).normalize();
                List<PlayerEntity> near = client.world.getEntitiesByClass(PlayerEntity.class, client.player.getBoundingBox().expand(RANGE), e -> e != client.player && e.isAlive());
                PlayerEntity best = null; double bd = DOT;
                for (PlayerEntity c : near) { double d = look.dotProduct(c.getCameraPosVec(1f).subtract(eye).normalize()); if (d > bd) { bd = d; best = c; } }
                if (best != null) { target = best; show = true; }
            }
            if (!show) target = null;
            alpha = show && hudOn ? Math.min(1f, alpha + FADE) : Math.max(0f, alpha - FADE);

            if (config.particleOn && hudOn && target != null && alpha > 0.1f) {
                if (client.world.random.nextFloat() < 0.4f) {
                    float px = client.world.random.nextFloat() * 155;
                    float py = client.world.random.nextFloat() * 46;
                    float mx = (client.world.random.nextFloat() - 0.5f) * 1.5f;
                    float my = (client.world.random.nextFloat() - 0.5f) * 1.5f;
                    particles.add(new TargetParticle(px, py, mx, my, 2.0f, 1.2f, 20f));
                }
            }
            particles.removeIf(TargetParticle::update);
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
            float delta = tickCounter.getTickDelta(true);
            long now = System.currentTimeMillis();

            int flopMain = getPinkWhiteFlop(0,   1.0f);
            int flopSec  = getPinkWhiteFlop(150, 1.0f);
            int flopTer  = getPinkWhiteFlop(300, 1.0f);
            int flopPrt  = getPinkWhiteFlop(450, 1.0f);

            ctx.drawText(mc.textRenderer, "FPS " + mc.getCurrentFps(), 5, 5,  flopMain, true);
            ctx.drawText(mc.textRenderer, "HUD " + (hudOn ? "Acik" : "Kapali") + " [R]", 5, 14, flopSec, true);
            ctx.drawText(mc.textRenderer, "Bar " + (tagOn ? "Acik" : "Kapali") + " [N]", 5, 23, flopTer, true);
            ctx.drawText(mc.textRenderer, "Prt " + (config.particleOn ? "Acik" : "Kapali") + " [P]", 5, 32, flopPrt, true);

            renderCustomHotbar(ctx, mc, sw, sh, delta, flopMain);

            if (tagOn && mc.world != null) {
                for (PlayerEntity pl : mc.world.getPlayers()) {
                    if (pl == mc.player || !pl.isAlive()) continue;
                    double dist = mc.player.distanceTo(pl);
                    if (dist > RANGE + 0.5) continue;
                    double wx = config.visuals.sabitBar ? pl.getX() : lerp(pl.lastRenderX, pl.getX(), delta);
                    double wy = config.visuals.sabitBar ? pl.getY() : lerp(pl.lastRenderY, pl.getY(), delta);
                    double wz = config.visuals.sabitBar ? pl.getZ() : lerp(pl.lastRenderZ, pl.getZ(), delta);
                    double[] sc = proj(mc, new Vec3d(wx, wy + pl.getHeight() + 0.35, wz), sw, sh);
                    if (sc == null) continue;
                    int px = (int) sc[0], py = (int) sc[1];
                    float plHp = pl.getHealth(), plMhp = pl.getMaxHealth();
                    float ratio = Math.max(0f, plHp / plMhp);
                    int bw = (int) Math.max(36, 50 - dist * 1.5), bh = 4, bx = px - bw / 2;
                    int fill = Math.max(1, (int) (ratio * bw));
                    int barColor = getHealthColor(ratio, now, pl.getId());
                    ctx.fill(bx - 1, py - 1, bx + bw + 1, py + bh + 1, 0xBB000000);
                    ctx.fill(bx, py, bx + bw, py + bh, 0xFF1A1A1A);
                    ctx.fill(bx, py, bx + fill, py + bh, barColor);
                    if (dist < RANGE - 0.5) {
                        String nm = pl.getName().getString();
                        int tw = mc.textRenderer.getWidth(nm);
                        ctx.fill(px - tw / 2 - 2, py - 12, px + tw / 2 + 2, py - 2, 0xAA000000);
                        ctx.drawText(mc.textRenderer, nm, px - tw / 2, py - 11, getPinkWhiteFlop(pl.getId() * 60, 1.0f), true);
                    }
                }
            }

            if (alpha <= 0.01f || !hudOn) return;
            float hp  = target != null ? target.getHealth() : 0f;
            float mhp = target != null ? target.getMaxHealth() : 20f;
            float r   = Math.max(0f, hp / mhp);
            int a = (int) (alpha * 255);
            int hpA = getPinkWhiteFlop(0, alpha);
            int bW = 155, bH = 46;
            int bX = (sw * config.hudX) / 100 - bW / 2, bY = (sh * config.hudY) / 100 - bH / 2;
            float scH = config.hudScale / 100f;

            ctx.getMatrices().push();
            ctx.getMatrices().translate(bX + bW / 2f, bY + bH / 2f, 200);
            ctx.getMatrices().scale(scH, scH, 1);
            ctx.getMatrices().translate(-bW / 2f, -bH / 2f, 0);

            int bg = (Math.min(a, 230) << 24) | 0x0A0A0A;
            ctx.fill(5, 0, bW - 5, bH, bg);
            ctx.fill(0, 5, bW, bH - 5, bg);
            ctx.fill(5, 0, bW - 5, 2, hpA);
            ctx.fill(5, bH - 2, bW - 5, bH, (Math.min(a, 120) << 24) | (hpA & 0x00FFFFFF));

            if (target != null) {
                try {
                    Identifier sk = mc.getSkinProvider().getSkinTextures(target.getGameProfile()).texture();
                    ctx.drawTexture(sk, 6, (bH - 20) / 2, 20, 20, 8, 8, 8, 8, 64, 64);
                    ctx.drawTexture(sk, 6, (bH - 20) / 2, 20, 20, 40, 8, 8, 8, 64, 64);
                } catch (Exception ignored) {}
            }
            ctx.drawText(mc.textRenderer, "TARGET", 32, 4, hpA, true);
            ctx.drawText(mc.textRenderer, target != null ? target.getName().getString() : "---", 32, 13, (a << 24) | 0xFFFFFF, true);
            ctx.drawText(mc.textRenderer, (int) Math.ceil(hp) + " HP", bW - mc.textRenderer.getWidth((int) Math.ceil(hp) + " HP") - 6, 13, hpA, true);

            int barX = 32, barY = 29, barW = bW - 38, barH = 7;
            ctx.fill(barX, barY, barX + barW, barY + barH, (Math.min(a, 200) << 24) | 0x1A1A1A);
            ctx.fill(barX, barY, barX + (int)(r * barW), barY + barH, applyAlpha(getHealthColor(r, now, target != null ? target.getId() : 0), a));

            if (config.particleOn) {
                for (TargetParticle tp : particles) tp.render(ctx, hpA);
            }
            ctx.getMatrices().pop();
        });
    }

    public static class TargetParticle {
        float x, y, px, py, spawnX, spawnY, motionX, motionY, baseSpeed, maxRadius, size, cachedAlpha;
        int age, maxAge;
        public TargetParticle(float x, float y, float mx, float my, float size, float baseSpeed, float maxRadius) {
            this.x = this.px = this.spawnX = x; this.y = this.py = this.spawnY = y;
            this.motionX = mx; this.motionY = my; this.size = size; this.baseSpeed = baseSpeed; this.maxRadius = maxRadius;
            this.maxAge = 15 + (int)(Math.random() * 20); this.age = this.maxAge;
        }
        public boolean update() {
            age--; if (age < 0) return true;
            px = x; py = y;
            float dx = x - spawnX, dy = y - spawnY;
            float dist = (float) Math.sqrt(dx * dx + dy * dy);
            float ratio = Math.min(1.0f, dist / maxRadius);
            x += motionX * (baseSpeed * (1.0f - ratio * ratio));
            y += motionY * (baseSpeed * (1.0f - ratio * ratio));
            motionX *= 0.9f; motionY *= 0.9f;
            if (dist < maxRadius * 0.8f) motionY += 0.05f;
            cachedAlpha = (float) age / maxAge;
            return false;
        }
        public void render(net.minecraft.client.gui.DrawContext ctx, int color) {
            int a = (int) (cachedAlpha * 255);
            if (a <= 5) return;
            int c = (a << 24) | (color & 0x00FFFFFF);
            ctx.fill((int)x, (int)y, (int)(x + size), (int)(y + size), c);
        }
    }

    private void renderCustomHotbar(net.minecraft.client.gui.DrawContext ctx, MinecraftClient mc, int sw, int sh, float delta, int flopColor) {
        PlayerInventory inventory = mc.player.getInventory();
        int width = 182, height = 22, startX = (sw - width) / 2, startY = sh - 24; 
        selectItemX = lerp(selectItemX, inventory.selectedSlot * 20, delta * 0.4f);
        ctx.fill(startX - 2, startY - 2, startX + width + 2, startY + height + 2, 0xAA000000); 
        int selX = (int) (startX + selectItemX);
        ctx.fill(selX, startY, selX + 22, startY + 22, applyAlpha(flopColor, 100)); 
        ctx.fill(selX, startY, selX + 22, startY + 1, flopColor); 
        ctx.fill(selX, startY + 21, selX + 22, startY + 22, flopColor); 
        ctx.fill(selX, startY, selX + 1, startY + 22, flopColor); 
        ctx.fill(selX + 21, startY, selX + 22, startY + 22, flopColor); 
        for (int i = 0; i < 9; i++) {
            int slotX = startX + i * 20 + 3, slotY = startY + 3;
            ItemStack stack = inventory.main.get(i);
            ctx.drawItem(stack, slotX, slotY);
            ctx.drawItemInSlot(mc.textRenderer, stack, slotX, slotY);
            if (mc.options.hotbarKeys.length > i) {
                String key = mc.options.hotbarKeys[i].getBoundKeyLocalizedText().getString();
                key = convertRussianToEnglish(key);
                ctx.getMatrices().push(); ctx.getMatrices().scale(0.6f, 0.6f, 1f);
                ctx.drawText(mc.textRenderer, key, (int)((slotX+1)/0.6f), (int)((slotY+1)/0.6f), 0xFFAAAAAA, true);
                ctx.getMatrices().pop();
            }
        }
    }

    private String convertRussianToEnglish(String text) {
        if (text == null || text.isEmpty()) return text;
        String russian = "йцукенгшщзхъфывапролджэячсмитьбю.ЙЦУКЕНГШЩЗХЪФЫВАПРОЛДЖЭЯЧСМИТЬБЮ,";
        String english = "qwertyuiop[]asdfghjkl;'zxcvbnm,./QWERTYUIOP{}ASDFGHJKL:\"ZXCVBNM<>?";
        StringBuilder result = new StringBuilder();
        for (char c : text.toCharArray()) {
            int idx = russian.indexOf(c);
            if (idx != -1) result.append(english.charAt(idx)); else result.append(c);
        }
        return result.toString();
    }

    private int getHealthColor(float ratio, long now, int seed) {
        if (ratio > 0.6f) return 0xFF000000 | ((int)(255 * (1f - (ratio - 0.6f) / 0.4f)) << 16) | (0xCC << 8) | 0x44;
        if (ratio > 0.3f) return 0xFF000000 | (0xFF << 16) | ((int)(100 + 100 * (ratio - 0.3f) / 0.3f) << 8) | 0x00;
        int rb = (int) (160 + 95 * ((Math.sin((now + seed * 137) / 200.0) + 1.0) / 2.0));
        return 0xFF000000 | (rb << 16) | (0x22 << 8) | 0x22;
    }

    private int getPinkWhiteFlop(int offset, float alphaMult) {
        double wave = (Math.sin((System.currentTimeMillis() + offset) / 300.0) + 1.0) / 2.0;
        return ((int)(255 * alphaMult) << 24) | (0xFF << 16) | ((int)(130 + 125 * wave) << 8) | (int)(200 + 55 * wave);
    }

    private int applyAlpha(int color, int newAlpha) { return (Math.min(newAlpha, 255) << 24) | (color & 0x00FFFFFF); }

    private double[] proj(MinecraftClient mc, Vec3d world, int sw, int sh) {
        try {
            var cam = mc.gameRenderer.getCamera();
            Vec3d rel = world.subtract(cam.getPos());
            if (mc.player.getRotationVec(1f).dotProduct(rel.normalize()) < 0) return null;
            double yr = Math.toRadians(cam.getYaw()), pr = Math.toRadians(cam.getPitch());
            double rx = rel.x * Math.cos(yr) - rel.z * Math.sin(yr), ry = rel.y, rz = rel.x * Math.sin(yr) + rel.z * Math.cos(yr);
            double ry2 = ry * Math.cos(pr) - rz * Math.sin(pr), rz2 = ry * Math.sin(pr) + rz * Math.cos(pr);
            if (rz2 <= 0.1) return null;
            double p = sw / (2.0 * Math.tan(Math.toRadians(mc.options.getFov().getValue()) / 2.0));
            return new double[]{ sw / 2.0 + (rx / rz2) * p, sh / 2.0 - (ry2 / rz2) * p };
        } catch (Exception e) { return null; }
    }

    private float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private double lerp(double a, double b, float t) { return a + (b - a) * t; }
    private void btn(Screen sc, String t, int x, int y, int w, int h, ButtonWidget.PressAction a) { Screens.getButtons(sc).add(ButtonWidget.builder(Text.literal(t), a).dimensions(x, y, w, h).build()); }
    private boolean isTrash(ItemStack s) { return s.isOf(Items.ROTTEN_FLESH) || s.isOf(Items.POISONOUS_POTATO) || s.isOf(Items.DIRT) || s.isOf(Items.COBBLESTONE) || s.isOf(Items.GRAVEL) || s.isOf(Items.SAND); }
    private boolean isArmor(ItemStack s) { String n = s.getItem().toString().toLowerCase(); return n.contains("helmet") || n.contains("chestplate") || n.contains("leggings") || n.contains("boots"); }
}
