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
            
            // Sandık veya Sunucu Menüsü (Siparişler vb.)
            if (screen instanceof GenericContainerScreen chest) {
                int sx = W / 2 + 92, sy = H / 2 - 80, id = chest.getScreenHandler().syncId;
                btn(screen, "Herseyi Al", sx, sy, 85, 20, b -> { int s = chest.getScreenHandler().getInventory().size(); for (int i = 0; i < s; i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); });
                btn(screen, "Herseyi Koy", sx, sy + 24, 85, 20, b -> { int s = chest.getScreenHandler().getInventory().size(); for (int i = s; i < s + 36; i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); });
                btn(screen, "Herseyi At", sx, sy + 48, 85, 20, b -> { for (int i = 0; i < chest.getScreenHandler().slots.size(); i++) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player); });
                btn(screen, "Cop At", sx, sy + 72, 85, 20, b -> { for (int i = 0; i < chest.getScreenHandler().slots.size(); i++) { ItemStack st = chest.getScreenHandler().getSlot(i).getStack(); if (isTrash(st)) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player); } });
                
                // YENİ BUTON: Sol Üste Eklenen Delayli Oto Order Butonu
                btn(screen, "Order Oto", 5, 5, 65, 20, b -> startAutoOrder(client));
            }
            
            // Kendi Envanterin
            if (screen instanceof InventoryScreen inv) {
                int x = W / 2 - 88, y = H / 2 - 83, id = inv.getScreenHandler().syncId;
                btn(screen, "Zirhi Giy", x - 52, y, 50, 18, b -> { for (int i = 9; i < 45; i++) { ItemStack st = inv.getScreenHandler().getSlot(i).getStack(); if (isArmor(st)) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player); } });
                btn(screen, "Temizle", x - 52, y + 20, 50, 18, b -> { for (int i = 9; i < 45; i++) client.interactionManager.clickSlot(id, i, 1, SlotActionType.THROW, client.player); });
                
                // YENİ BUTON: Kendi envanterinde de sol üstte görünsün
                btn(screen, "Order Oto", 5, 5, 65, 20, b -> startAutoOrder(client));
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            
            boolean r = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
            if (r && !rLast) { hudOn = !hudOn; client.player.sendMessage(Text.literal(hudOn ? "§dHUD Acildi" : "§fHUD Kapatildi"), true); }
            rLast = r;
            
            boolean n = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_N) == GLFW.GLFW_PRESS;
            if (n && !nLast) { tagOn = !tagOn; client.player.sendMessage(Text.literal(tagOn ? "§dBar Acildi" : "§fBar Kapatildi"), true); }
            nLast = n;

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
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
            float delta = tickCounter.getTickDelta(true);

            int flopColorMain = getPinkWhiteFlop(0, 1.0f);
            int flopColorSec = getPinkWhiteFlop(150, 1.0f);

            ctx.drawText(mc.textRenderer, "FPS " + mc.getCurrentFps(), 5, 5, flopColorMain, true);
            ctx.drawText(mc.textRenderer, "HUD " + (hudOn ? "Acik" : "Kapali") + " [R]", 5, 14, flopColorSec, true);
            ctx.drawText(mc.textRenderer, "Bar " + (tagOn ? "Acik" : "Kapali") + " [N]", 5, 23, getPinkWhiteFlop(300, 1.0f), true);

            if (tagOn && mc.world != null) {
                for (PlayerEntity pl : mc.world.getPlayers()) {
                    if (pl == mc.player || !pl.isAlive()) continue;
                    double dist = mc.player.distanceTo(pl);
                    if (dist > RANGE + 0.5) continue;
                    
                    double x = config.visuals.sabitBar ? pl.getX() : lerp(pl.lastRenderX, pl.getX(), delta);
                    double y = config.visuals.sabitBar ? pl.getY() : lerp(pl.lastRenderY, pl.getY(), delta);
                    double z = config.visuals.sabitBar ? pl.getZ() : lerp(pl.lastRenderZ, pl.getZ(), delta);
                    
                    double[] sc = proj(mc, new Vec3d(x, y + pl.getHeight() + 0.4, z), sw, sh);
                    if (sc == null) continue;
                    
                    float r = Math.max(0f, pl.getHealth() / pl.getMaxHealth());
                    int px = (int) sc[0], py = (int) sc[1], bw = (int) (50 * (1.0 - dist / (RANGE + 2) * 0.3)), bh = 4;
                    int bx = px - bw / 2, fill = Math.max(1, (int) (r * bw));
                    
                    int tagFlop = getPinkWhiteFlop((int)(pl.getId() * 50), 1.0f); 
                    
                    ctx.fill(bx - 1, py - 1, bx + bw + 1, py + bh + 1, 0xAA000000);
                    ctx.fill(bx, py, bx + fill, py + bh, tagFlop);
                    if (dist < RANGE - 1) {
                        String nm = pl.getName().getString();
                        ctx.drawText(mc.textRenderer, nm, px - mc.textRenderer.getWidth(nm) / 2, py - 10, flopColorMain, true);
                    }
                }
            }

            if (alpha <= 0.01f || !hudOn) return;
            float hp = target != null ? target.getHealth() : 0f, mhp = target != null ? target.getMaxHealth() : 20f, r = Math.max(0f, hp / mhp);
            int a = (int) (alpha * 255);
            int hpA = getPinkWhiteFlop(0, alpha);
            int bW = 155, bH = 46;
            
            int bX = (sw * config.hudX) / 100 - (bW / 2);
            int bY = (sh * config.hudY) / 100 - (bH / 2);
            float scale = config.hudScale / 100f;

            ctx.getMatrices().push();
            ctx.getMatrices().translate(bX + bW / 2f, bY + bH / 2f, 200);
            ctx.getMatrices().scale(scale, scale, 1);
            ctx.getMatrices().translate(-bW / 2f, -bH / 2f, 0);

            int bg = (Math.min(a, 230) << 24) | 0x0A0A0A;
            ctx.fill(5, 0, bW - 5, bH, bg); ctx.fill(0, 5, bW, bH - 5, bg);
            ctx.fill(5, 0, bW - 5, 2, hpA);
            
            if (target != null) { try { Identifier sk = mc.getSkinProvider().getSkinTextures(target.getGameProfile()).texture(); int hx = 6, hy = (bH - 20) / 2; ctx.fill(hx - 1, hy - 1, hx + 21, hy + 21, (Math.min(a, 100) << 24) | 0x000000); ctx.drawTexture(sk, hx, hy, 20, 20, 8, 8, 8, 8, 64, 64); ctx.drawTexture(sk, hx, hy, 20, 20, 40, 8, 8, 8, 64, 64); } catch (Exception ignored) {} }
            
            ctx.drawText(mc.textRenderer, "TARGET", 32, 4, hpA, true);
            ctx.drawText(mc.textRenderer, target != null ? target.getName().getString() : "---", 32, 13, (a << 24) | 0xFFFFFF, true);
            String hs = (int) Math.ceil(hp) + " HP";
            ctx.drawText(mc.textRenderer, hs, bW - mc.textRenderer.getWidth(hs) - 6, 13, hpA, true);
            
            int barX = 32, barY = 29, barW = bW - 38, barH = 7, fill = Math.max(1, (int) (r * barW));
            ctx.fill(barX, barY, barX + barW, barY + barH, (Math.min(a, 200) << 24) | 0x1A1A1A);
            ctx.fill(barX, barY, barX + fill, barY + barH, hpA);
            ctx.getMatrices().pop();
        });
    }

    // --- OTO ORDER YARDIMCI METODU ---
    private void startAutoOrder(MinecraftClient client) {
        if (client.player == null) return;
        
        // İşlemin başladığını chate yaz (pembe renk ile)
        client.player.sendMessage(Text.literal("§d[HitX] §fOto Order Başlatıldı!"), false);

        // Arayüz dondurmamak için işlemleri ayrı bir Thread(İş Parçacığı) üzerinde yapıyoruz.
        new Thread(() -> {
            try {
                // 1. Adım: Eğer siparişe önce menüden tıklanması gerekiyorsa bu kodu açabilirsin.
                // Şu an sadece senin envanterindeki eşyaları sandığa/menüye shiftliyor.
                
                // GECİKME (Delay) - Oyunun menü yüklenmesini beklemesi için milisaniye
                Thread.sleep(300); // 300 ms delay (sunucu yavaşsa 500-600 yapabilirsin)
                
                // 2. Adım: Menüdeki işlemi yap
                client.execute(() -> {
                    if (client.currentScreen instanceof GenericContainerScreen chest) {
                        int syncId = chest.getScreenHandler().syncId;
                        int containerSize = chest.getScreenHandler().getInventory().size(); // Sandığın boyutu
                        int totalSlots = chest.getScreenHandler().slots.size(); // Toplam boyut (Sandık + Senin Envanterin)
                        
                        // Sadece senin envanterindeki kısımları döngüye al
                        for (int i = containerSize; i < totalSlots; i++) {
                            ItemStack stack = chest.getScreenHandler().getSlot(i).getStack();
                            
                            // Eğer slottaki eşya boş değilse hızlıca (Shift-Click) gönder
                            if (!stack.isEmpty() && !isArmor(stack)) { 
                                client.interactionManager.clickSlot(syncId, i, 0, SlotActionType.QUICK_MOVE, client.player);
                            }
                        }
                    }
                });
                
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    // --- YARDIMCI METOTLAR ---

    private int getPinkWhiteFlop(int offset, float alphaMult) {
        double wave = (Math.sin((System.currentTimeMillis() + offset) / 300.0) + 1.0) / 2.0; 
        int a = (int) (255 * alphaMult);
        int r = 255;
        int g = (int) (130 + (125 * wave)); 
        int b = (int) (200 + (55 * wave));
        return (a << 24) | (r << 16) | (g << 8) | b;
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
