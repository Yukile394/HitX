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
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {

    // --- HITBOX AYARLARI (Ayarlar ekranı için static) ---
    public static boolean hitBoxActive = false;
    public static float xzExpand = 0.1f;
    public static float yExpand = 0.0f; // ThunderHack tarzı dikey genişletme

    private boolean hLast = false;
    private float selectItemX = 0f;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        // --- ENVANTER BUTONLARI ---
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            if (screen instanceof GenericContainerScreen chest) {
                int sx = W / 2 + 92, sy = H / 2 - 80, id = chest.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.HOPPER), "Herşeyi Al", sx, sy, 24, 20, b -> { for (int i = 0; i < chest.getScreenHandler().getInventory().size(); i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); });
                iconBtn(screen, new ItemStack(Items.CHEST), "Herşeyi Koy", sx, sy + 24, 24, 20, b -> { int s = chest.getScreenHandler().getInventory().size(); for (int i = s; i < s + 36; i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); });
            }
            if (screen instanceof InventoryScreen inv) {
                int x = W / 2 - 25, y = H / 2 - 83, id = inv.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.DIAMOND_CHESTPLATE), "Zırhı Giy", x, y, 24, 20, b -> { for (int i = 9; i < 45; i++) { ItemStack st = inv.getScreenHandler().getSlot(i).getStack(); if (isArmor(st)) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); } });
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // --- HITBOX TUŞ KONTROLÜ (H TUŞU) ---
            boolean h = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_H) == GLFW.GLFW_PRESS;
            if (h && !hLast) {
                hitBoxActive = !hitBoxActive;
                client.player.sendMessage(Text.literal(hitBoxActive ? "§dHitBox: AKTİF" : "§fHitBox: DEVRE DIŞI"), true);
            }
            hLast = h;

            // --- OTOMATİK SPRINT VE GECE GÖRÜŞÜ ---
            if (client.options.forwardKey.isPressed() && !client.player.horizontalCollision && client.player.getHungerManager().getFoodLevel() > 6)
                client.player.setSprinting(true);
            
            if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION))
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));

            // --- HITBOX MEKANİĞİ (THUNDERHACK MANTIĞI) ---
            if (hitBoxActive) {
                for (PlayerEntity player : client.world.getPlayers()) {
                    if (player == client.player || !player.isAlive()) continue;
                    // Orijinal kutuyu alıp genişletiyoruz
                    player.setBoundingBox(player.getType().getDimensions().getBoxAt(player.getPos()).expand(xzExpand, yExpand, xzExpand));
                }
            }
        });

        // --- HUD RENDER (SADECE FPS VE HOTBAR) ---
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            
            int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
            float delta = tickCounter.getTickDelta(true);
            int flop = getPinkWhiteFlop(0, 1.0f);

            // Sadece FPS göstergesi kalsın dedin:
            ctx.drawText(mc.textRenderer, "FPS " + mc.getCurrentFps(), 5, 5, flop, true);
            if(hitBoxActive) ctx.drawText(mc.textRenderer, "HitBox " + String.format("%.1f", xzExpand), 5, 15, 0xFF55FF55, true);

            renderPadejHotbar(ctx, mc, sw, sh, delta, flop);
        });
    }

    private void renderPadejHotbar(DrawContext ctx, MinecraftClient mc, int sw, int sh, float delta, int flop) {
        PlayerInventory inv = mc.player.getInventory();
        int w = 182, h = 22, x = (sw - w) / 2, y = sh - 25;
        selectItemX = lerp(selectItemX, inv.selectedSlot * 20f, delta * 0.25f);
        
        ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0x88000000);
        int sx = (int)(x + selectItemX);
        ctx.fill(sx, y, sx + 22, y + 22, (flop & 0x00FFFFFF) | (120 << 24));
        ctx.fill(sx, y, sx + 22, y + 1, flop);
        ctx.fill(sx, y + 21, sx + 22, y + 22, flop);

        for (int i = 0; i < 9; i++) {
            ctx.drawItem(inv.main.get(i), x + i * 20 + 3, y + 3);
        }
    }

    // --- ÖZEL BUTON TASARIMI ---
    private static class FlopIconButton extends ButtonWidget {
        private final ItemStack icon;
        public FlopIconButton(int x, int y, int w, int h, ItemStack icon, String t, PressAction a) {
            super(x, y, w, h, Text.literal(t), a, DEFAULT_NARRATION_SUPPLIER);
            this.icon = icon;
            this.setTooltip(Tooltip.of(Text.literal(t)));
        }
        @Override
        public void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
            int flop = getPinkWhiteFlop(this.isHovered() ? 0 : 300, 1.0f);
            ctx.fill(getX(), getY(), getX() + getWidth(), getY() + getHeight(), 0xFF222222);
            ctx.fill(getX(), getY(), getX() + getWidth(), getY() + 1, flop);
            ctx.drawItem(this.icon, getX() + (getWidth() - 16) / 2, getY() + (getHeight() - 16) / 2);
        }
    }

    public static int getPinkWhiteFlop(int o, float a) {
        double w = (Math.sin((System.currentTimeMillis() + o) / 300.0) + 1.0) / 2.0;
        return ((int)(255 * a) << 24) | (0xFF << 16) | ((int)(130 + 125 * w) << 8) | (int)(200 + 55 * w);
    }

    private float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private boolean isArmor(ItemStack s) { String n = s.getItem().toString().toLowerCase(); return n.contains("helmet") || n.contains("chestplate") || n.contains("leggings") || n.contains("boots"); }
    private void iconBtn(Screen s, ItemStack icon, String t, int x, int y, int w, int h, ButtonWidget.PressAction a) { Screens.getButtons(s).add(new FlopIconButton(x, y, w, h, icon, t, a)); }
}
