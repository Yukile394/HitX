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

    // AYARLAR EKRANININ ARADIĞI DEĞİŞKENLER BURADA:
    public static boolean hudOn = true;
    public static boolean hitBoxActive = false;
    public static float xzExpand = 0.1f;
    
    private PlayerEntity target = null;
    private float alpha = 0f;
    private boolean rLast = false;
    private static final double RANGE = 6.5, DOT = 0.97;
    private static final float FADE = 0.12f;

    private float selectItemX = 0f;
    private final List<TargetParticle> particles = new ArrayList<>();

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            if (screen instanceof GenericContainerScreen chest) {
                int id = chest.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.HOPPER), "Hepsini Al", W / 2 + 92, H / 2 - 80, 24, 20, b -> {
                    for (int i = 0; i < chest.getScreenHandler().getInventory().size(); i++) 
                        client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                });
            }
            if (screen instanceof InventoryScreen inv) {
                int id = inv.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.DIAMOND_CHESTPLATE), "Zırh Giy", W / 2 - 25, H / 2 - 83, 24, 20, b -> {
                    for (int i = 9; i < 45; i++) {
                        if (isArmor(inv.getScreenHandler().getSlot(i).getStack()))
                            client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                    }
                });
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            boolean r = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
            if (r && !rLast) hudOn = !hudOn; rLast = r;

            if (client.options.forwardKey.isPressed() && !client.player.horizontalCollision && client.player.getHungerManager().getFoodLevel() > 6)
                client.player.setSprinting(true);
            
            if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION))
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));

            boolean show = false;
            if (client.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity pl && pl.isAlive()) { target = pl; show = true; }
            
            alpha = (show && hudOn) ? Math.min(1f, alpha + FADE) : Math.max(0f, alpha - FADE);

            if (config.particleOn && hudOn && target != null && alpha > 0.1f) {
                if (client.world.random.nextFloat() < 0.3f) particles.add(new TargetParticle(client.world.random.nextFloat() * 155, client.world.random.nextFloat() * 46));
            }
            particles.removeIf(TargetParticle::update);
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
            float delta = tickCounter.getTickDelta(true);
            int flop = getPinkWhiteFlop(0, 1.0f);

            renderPadejHotbar(ctx, mc, sw, sh, delta, flop);
            if (alpha > 0.01f && hudOn) renderTargetHUD(ctx, mc, config, sw, sh, flop);
        });
    }

    private void renderTargetHUD(DrawContext ctx, MinecraftClient mc, HitXConfig config, int sw, int sh, int hpColor) {
        int bW = 155, bH = 46, bX = (sw * config.hudX) / 100 - bW / 2, bY = (sh * config.hudY) / 100 - bH / 2;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(bX + bW/2f, bY + bH/2f, 0);
        ctx.getMatrices().scale(config.hudScale/100f, config.hudScale/100f, 1);
        ctx.getMatrices().translate(-bW/2f, -bH/2f, 0);
        ctx.fill(0, 0, bW, bH, (int)(alpha * 180) << 24 | 0x050505);
        ctx.fill(0, 0, bW, 1, (hpColor & 0xFFFFFF) | ((int)(alpha * 255) << 24));
        if (target != null) {
            Identifier sk = mc.getSkinProvider().getSkinTextures(target.getGameProfile()).texture();
            ctx.drawTexture(sk, 5, 5, 20, 20, 8, 8, 8, 8, 64, 64);
            ctx.drawText(mc.textRenderer, target.getName().getString(), 32, 10, 0xFFFFFF, true);
            float r = target.getHealth() / target.getMaxHealth();
            ctx.fill(32, 25, 32 + 110, 30, 0xFF222222);
            ctx.fill(32, 25, 32 + (int)(r * 110), 30, hpColor);
        }
        for (TargetParticle p : particles) p.render(ctx, hpColor);
        ctx.getMatrices().pop();
    }

    private void renderPadejHotbar(DrawContext ctx, MinecraftClient mc, int sw, int sh, float delta, int flop) {
        PlayerInventory inv = mc.player.getInventory();
        int w = 182, h = 22, x = (sw - w) / 2, y = sh - 25;
        selectItemX = x + (inv.selectedSlot * 20f);
        ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x88000000);
        ctx.fill((int)selectItemX, y, (int)selectItemX + 22, y + 22, (flop & 0xFFFFFF) | (120 << 24));
        for (int i = 0; i < 9; i++) ctx.drawItem(inv.main.get(i), x + i * 20 + 3, y + 3);
    }

    private static class FlopIconButton extends ButtonWidget {
        private final ItemStack icon;
        public FlopIconButton(int x, int y, int w, int h, ItemStack icon, String t, PressAction a) {
            super(x, y, w, h, Text.literal(t), a, DEFAULT_NARRATION_SUPPLIER);
            this.icon = icon;
            this.setTooltip(Tooltip.of(Text.literal(t)));
        }
        @Override
        public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF222222);
            ctx.drawItem(this.icon, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
        }
    }

    public static class TargetParticle {
        float x, y, mx, my, age = 20;
        public TargetParticle(float x, float y) { this.x = x; this.y = y; this.mx = (float)(Math.random()-0.5)*2f; this.my = (float)(Math.random()-0.5)*2f; }
        public boolean update() { x += mx; y += my; age--; return age < 0; }
        public void render(DrawContext ctx, int c) { ctx.fill((int)x, (int)y, (int)x+2, (int)y+2, ((int)((age/20f)*255) << 24) | (c & 0xFFFFFF)); }
    }

    public static int getPinkWhiteFlop(int o, float a) {
        double w = (Math.sin((System.currentTimeMillis() + o) / 300.0) + 1.0) / 2.0;
        return ((int)(255 * a) << 24) | (0xFF << 16) | ((int)(130 + 125 * w) << 8) | (int)(200 + 55 * w);
    }

    private boolean isArmor(ItemStack s) { String n = s.getItem().toString(); return n.contains("helmet") || n.contains("chestplate") || n.contains("leggings") || n.contains("boots"); }
    private void iconBtn(Screen s, ItemStack i, String t, int x, int y, int w, int h, ButtonWidget.PressAction a) { Screens.getButtons(s).add(new FlopIconButton(x, y, w, h, i, t, a)); }
        }
