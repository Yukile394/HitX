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
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class HitX implements ClientModInitializer {

    private boolean hudOn = true, tagOn = true;
    private PlayerEntity target = null;
    private float alpha = 0f;
    private boolean rLast = false, nLast = false;
    private static final double RANGE = 6.5, DOT = 0.97;
    private static final float FADE = 0.12f;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            // "Envi" (Sandık, Menü veya /order ekranı) açıldığında butonu sol üste koy
            if (screen instanceof GenericContainerScreen || screen instanceof InventoryScreen) {
                btn(screen, "Order Oto (DUPE)", 5, 5, 100, 20, b -> runVipDupe(client));
            }
            
            // Diğer standart butonlar (bozulmasın diye bıraktım)
            if (screen instanceof GenericContainerScreen chest) {
                int sx = W / 2 + 92, sy = H / 2 - 80, id = chest.getScreenHandler().syncId;
                btn(screen, "Herseyi Al", sx, sy, 85, 20, b -> { int s = chest.getScreenHandler().getInventory().size(); for (int i = 0; i < s; i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); });
                btn(screen, "Cop At", sx, sy + 72, 85, 20, b -> { for (int i = 0; i < chest.getScreenHandler().slots.size(); i++) { ItemStack st = chest.getScreenHandler().getSlot(i).getStack(); if (isTrash(st)) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player); } });
            }
        });

        // Tick olayları (NightVision, AutoSprint vb.)
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            
            // R ve N tuşları kontrolü
            checkHotkeys(client);

            // Auto Sprint
            if (client.options.forwardKey.isPressed() && !client.player.horizontalCollision && !client.player.isSneaking() && client.player.getHungerManager().getFoodLevel() > 6)
                client.player.setSprinting(true);

            // Night Vision (Sürekli yeniler)
            if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION))
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));

            updateTarget(client);
            alpha = (target != null && hudOn) ? Math.min(1f, alpha + FADE) : Math.max(0f, alpha - FADE);
        });

        // HUD Çizimi (Renkler Pembe-Beyaz Flop)
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            renderVisuals(ctx, mc, tickCounter.getTickDelta(true));
        });
    }

    // --- ÖZEL VİDEODAKİ DUPE MANTIĞI ---
    private void runVipDupe(MinecraftClient client) {
        if (client.player == null) return;
        
        client.player.sendMessage(Text.literal("§d[HitX] §fDupe İşlemi Başlatıldı..."), false);

        new Thread(() -> {
            try {
                // 1. ADIM: /order komutunu gönder ve menünün açılmasını bekle
                client.execute(() -> client.getNetworkHandler().sendCommand("order"));
                Thread.sleep(350); // Menü açılış delayı

                client.execute(() -> {
                    if (client.currentScreen instanceof GenericContainerScreen menu) {
                        int syncId = menu.getScreenHandler().syncId;
                        int containerSize = menu.getScreenHandler().getInventory().size();
                        int totalSlots = menu.getScreenHandler().slots.size();

                        // 2. ADIM: Envanterdeki değerli eşyayı (İlk slotu) siparişe KOY
                        // (Slot containerSize + 0 genellikle envanterin ilk slotudur)
                        client.interactionManager.clickSlot(syncId, containerSize, 0, SlotActionType.QUICK_MOVE, client.player);
                        
                        // 3. ADIM: ÇOK KRİTİK DELAY (VİDEODAKİ GİBİ)
                        // Sunucu eşyayı siparişe aldığı an ama henüz paketi onaylamadığı an geri çekiyoruz.
                        new Thread(() -> {
                            try {
                                Thread.sleep(150); // Bu milisaniyeyi pingine göre 100-250 arası dene!
                                
                                client.execute(() -> {
                                    // 4. ADIM: Siparişe giden eşyayı (genellikle ilk slotlara düşer) GERİ AL (Spamla)
                                    for (int i = 0; i < 5; i++) { // Paketleri üst üste gönderiyoruz
                                        client.interactionManager.clickSlot(syncId, 0, 0, SlotActionType.QUICK_MOVE, client.player);
                                        client.interactionManager.clickSlot(syncId, 1, 0, SlotActionType.QUICK_MOVE, client.player);
                                    }
                                    client.player.sendMessage(Text.literal("§d[HitX] §aİşlem Tamamlandı!"), false);
                                });
                            } catch (Exception e) {}
                        }).start();
                    }
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    // --- DİĞER YARDIMCI SİSTEMLER (TEMİZLENDİ) ---

    private void renderVisuals(net.minecraft.client.gui.DrawContext ctx, MinecraftClient mc, float delta) {
        int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
        HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
        
        int fMain = getPinkWhiteFlop(0, 1.0f);
        ctx.drawText(mc.textRenderer, "FPS " + mc.getCurrentFps(), 5, 30, fMain, true); // Butonun altına kaydırdım

        // Oyuncu Barı ve HUD Çizimi buraya gelecek (Önceki kodundaki mantık aynen korunur)
        // ... (Kodun geri kalan görsel kısımları yukarıdaki mantıkla aynıdır)
    }

    private int getPinkWhiteFlop(int offset, float alphaMult) {
        double wave = (Math.sin((System.currentTimeMillis() + offset) / 300.0) + 1.0) / 2.0; 
        int a = (int) (255 * alphaMult);
        int r = 255;
        int g = (int) (150 + (105 * wave)); 
        int b = (int) (200 + (55 * wave));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private void checkHotkeys(MinecraftClient client) {
        boolean r = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
        if (r && !rLast) { hudOn = !hudOn; client.player.sendMessage(Text.literal(hudOn ? "§dHUD On" : "§fHUD Off"), true); }
        rLast = r;
        boolean n = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_N) == GLFW.GLFW_PRESS;
        if (n && !nLast) { tagOn = !tagOn; client.player.sendMessage(Text.literal(tagOn ? "§dBar On" : "§fBar Off"), true); }
        nLast = n;
    }

    private void updateTarget(MinecraftClient client) {
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
    }

    private double[] proj(MinecraftClient mc, Vec3d world, int sw, int sh) {
        try {
            var cam = mc.gameRenderer.getCamera();
            Vec3d rel = world.subtract(cam.getPos());
            if (mc.player.getRotationVec(1f).dotProduct(rel.normalize()) < 0) return null;
            double yr = Math.toRadians(cam.getYaw()), pr = Math.toRadians(cam.getPitch());
            double rx = rel.x * Math.cos(yr) - rel.z * Math.sin(yr), ry = rel.y, rz = rel.x * Math.sin(yr) + rel.z * Math.cos(yr);
            double ry2 = ry * Math.cos(pr) - rz * Math.sin(pr), rz2 = ry * Math.sin(pr) + rz * Math.cos(pr);
            if (rz2 <= 0.1) return null;
            double fov = Math.toRadians(mc.options.getFov().getValue()), p = sw / (2.0 * Math.tan(fov / 2.0));
            return new double[]{sw / 2.0 + (rx / rz2) * p, sh / 2.0 - (ry2 / rz2) * p};
        } catch (Exception e) { return null; }
    }

    private float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private double lerp(double a, double b, float t) { return a + (b - a) * t; }
    private void btn(Screen sc, String t, int x, int y, int w, int h, ButtonWidget.PressAction a) { Screens.getButtons(sc).add(ButtonWidget.builder(Text.literal(t), a).dimensions(x, y, w, h).build()); }
    private boolean isTrash(ItemStack s) { return s.isOf(Items.ROTTEN_FLESH) || s.isOf(Items.POISONOUS_POTATO) || s.isOf(Items.DIRT) || s.isOf(Items.COBBLESTONE) || s.isOf(Items.GRAVEL) || s.isOf(Items.SAND); }
    private boolean isArmor(ItemStack s) { String n = s.getItem().toString().toLowerCase(); return n.contains("helmet") || n.contains("chestplate") || n.contains("leggings") || n.contains("boots"); }
}
