package com.exloran.hitx;

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
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {

    private boolean hudOn = true, tagOn = true;
    private PlayerEntity target = null;
    private float alpha = 0f;
    private boolean rLast, nLast, pLast, kLast;
    private static final double RANGE = 6.5, DOT = 0.97;
    private static final float FADE = 0.08f; // Daha yumuşak geçiş

    private float selectItemX = 0f;
    private final List<TargetParticle> particles = new ArrayList<>();

    // HitBox Ayarları (Kod içine gömülü, Config'e bağlanabilir)
    public static float xzExpand = 0.3f;
    public static float yExpand = 0.1f;
    public static boolean hitBoxActive = true;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            if (screen instanceof GenericContainerScreen chest) {
                int sx = W / 2 + 92, sy = H / 2 - 80, id = chest.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.HOPPER), "Hepsini Çek", sx, sy, 24, 22, b -> { for (int i = 0; i < chest.getScreenHandler().getInventory().size(); i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); });
                iconBtn(screen, new ItemStack(Items.CHEST), "Hepsini Aktar", sx, sy + 26, 24, 22, b -> { int s = chest.getScreenHandler().getInventory().size(); for (int i = s; i < s + 36; i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); });
                iconBtn(screen, new ItemStack(Items.LAVA_BUCKET), "Çöpleri Temizle", sx, sy + 52, 24, 22, b -> { for (int i = 0; i < chest.getScreenHandler().slots.size(); i++) { if (isTrash(chest.getScreenHandler().getSlot(i).getStack())) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player); } });
            }
            if (screen instanceof InventoryScreen inv) {
                int x = W / 2 - 25, y = H / 2 - 83, id = inv.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.NETHERITE_CHESTPLATE), "Zırh Kuşan", x, y, 24, 22, b -> { for (int i = 9; i < 45; i++) { if (isArmor(inv.getScreenHandler().getSlot(i).getStack())) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); } });
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            // Tuş Kontrolleri
            handleKeys(client, config);

            // Sprint & Night Vision
            if (client.options.forwardKey.isPressed() && client.player.getHungerManager().getFoodLevel() > 6) client.player.setSprinting(true);
            if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION)) client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));

            // Target Bulma
            updateTarget(client);
            
            alpha = (target != null && hudOn) ? Math.min(1f, alpha + FADE) : Math.max(0f, alpha - FADE);

            if (config.particleOn && target != null && alpha > 0.5f) {
                if (client.world.random.nextFloat() < 0.4f) particles.add(new TargetParticle(client.world.random.nextFloat() * 155, client.world.random.nextFloat() * 46));
            }
            particles.removeIf(TargetParticle::update);
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            renderVisuals(ctx, mc, tick.getTickDelta(true));
        });
    }

    private void handleKeys(MinecraftClient client, HitXConfig config) {
        long h = client.getWindow().getHandle();
        boolean r = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
        boolean n = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_N) == GLFW.GLFW_PRESS;
        boolean p = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_P) == GLFW.GLFW_PRESS;
        boolean k = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_K) == GLFW.GLFW_PRESS;

        if (r && !rLast) hudOn = !hudOn;
        if (n && !nLast) tagOn = !tagOn;
        if (p && !pLast) config.particleOn = !config.particleOn;
        if (k && !kLast) client.setScreen(new HitXSettingsScreen()); // Menü Tuşu

        rLast = r; nLast = n; pLast = p; kLast = k;
    }

    private void renderVisuals(DrawContext ctx, MinecraftClient mc, float delta) {
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        int color = getPinkWhiteFlop(0, 1.0f);

        // FPS & Bilgi
        ctx.drawText(mc.textRenderer, "§dHit§fX §7| " + mc.getCurrentFps() + " FPS", 5, 5, -1, true);
        
        renderHotbar(ctx, mc, sw, sh, delta, color);

        if (alpha > 0.01f) {
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int bW = 160, bH = 50, bX = (sw * config.hudX) / 100 - bW / 2, bY = (sh * config.hudY) / 100 - bH / 2;
            
            ctx.getMatrices().push();
            ctx.getMatrices().translate(bX, bY, 0);
            ctx.fill(-1, -1, bW + 1, bH + 1, applyAlpha(color, (int)(alpha * 255))); // Parlayan Kenarlık
            ctx.fill(0, 0, bW, bH, (int)(alpha * 200) << 24 | 0x0A0A0A); // Arka Plan
            
            if (target != null) {
                Identifier sk = mc.getSkinProvider().getSkinTextures(target.getGameProfile()).texture();
                ctx.drawTexture(sk, 6, 6, 25, 25, 8, 8, 8, 8, 64, 64);
                ctx.drawText(mc.textRenderer, target.getName().getString(), 38, 10, -1, true);
                
                float healthPerc = target.getHealth() / target.getMaxHealth();
                ctx.fill(38, 28, 38 + 110, 34, 0x44FFFFFF);
                ctx.fill(38, 28, 38 + (int)(healthPerc * 110), 34, color);
                ctx.drawText(mc.textRenderer, String.format("%.1f", target.getHealth()), bW - 35, 38, color, true);
            }
            ctx.getMatrices().pop();
        }
    }

    private void renderHotbar(DrawContext ctx, MinecraftClient mc, int sw, int sh, float delta, int color) {
        int x = sw / 2 - 91, y = sh - 22;
        selectItemX = lerp(selectItemX, mc.player.getInventory().selectedSlot * 20f, delta * 0.2f);
        ctx.fill(x + (int)selectItemX - 1, y - 1, x + (int)selectItemX + 21, y + 21, color); // Seçili slot ışığı
    }

    private void updateTarget(MinecraftClient client) {
        if (client.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity pl) {
            target = pl;
        } else {
            Vec3d eye = client.player.getCameraPosVec(1f), look = client.player.getRotationVec(1f);
            target = client.world.getEntitiesByClass(PlayerEntity.class, client.player.getBoundingBox().expand(RANGE), 
                ent -> ent != client.player && ent.isAlive() && look.dotProduct(ent.getPos().subtract(eye).normalize()) > DOT)
                .stream().findFirst().orElse(null);
        }
    }

    // --- Modern Buton Sınıfı ---
    private static class FlopIconButton extends ButtonWidget {
        private final ItemStack icon;
        public FlopIconButton(int x, int y, int w, int h, ItemStack icon, String t, PressAction a) {
            super(x, y, w, h, Text.literal(t), a, DEFAULT_NARRATION_SUPPLIER);
            this.icon = icon;
            this.setTooltip(Tooltip.of(Text.literal(t)));
        }

        @Override
        protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            int color = getPinkWhiteFlop(this.isHovered() ? 100 : 0, 1.0f);
            ctx.fill(getX(), getY(), getX() + width, getY() + height, this.isHovered() ? 0x66FFFFFF : 0x44000000);
            ctx.fill(getX(), getY(), getX() + width, getY() + 1, color); // Üst çizgi neon
            ctx.drawItem(icon, getX() + (width - 16) / 2, getY() + (height - 16) / 2);
        }
    }

    // --- Yardımcı Metotlar ---
    public static int getPinkWhiteFlop(int offset, float alpha) {
        double speed = System.currentTimeMillis() / 400.0;
        int r = 255;
        int g = (int) (150 + Math.sin(speed + offset) * 100);
        int b = (int) (200 + Math.cos(speed + offset) * 55);
        return ((int)(alpha * 255) << 24) | (r << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, b));
    }

    private static int applyAlpha(int c, int a) { return (a << 24) | (c & 0xFFFFFF); }
    private float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private void iconBtn(Screen s, ItemStack i, String t, int x, int y, int w, int h, ButtonWidget.PressAction a) { Screens.getButtons(s).add(new FlopIconButton(x, y, w, h, i, t, a)); }
    private boolean isTrash(ItemStack s) { return s.isOf(Items.ROTTEN_FLESH) || s.isOf(Items.DIRT) || s.isOf(Items.COBBLESTONE); }
    private boolean isArmor(ItemStack s) { String n = s.getItem().toString(); return n.contains("helmet") || n.contains("chestplate") || n.contains("leggings") || n.contains("boots"); }
}
