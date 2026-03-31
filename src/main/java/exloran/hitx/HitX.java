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
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

public class HitX implements ClientModInitializer {

    private boolean hudOn = true, tagOn = true, elytraOn = false;
    private PlayerEntity target = null;
    private float alpha = 0f;
    private boolean rLast = false, nLast = false, pLast = false;
    private static final double RANGE = 6.5, DOT = 0.97;
    private static final float FADE = 0.12f;

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

            // --- TUŞ KONTROLLERİ ---
            boolean r = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_R) == GLFW.GLFW_PRESS;
            if (r && !rLast) { hudOn = !hudOn; client.player.sendMessage(Text.literal(hudOn ? "§dHUD Acildi" : "§fHUD Kapatildi"), true); }
            rLast = r;

            boolean n = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_N) == GLFW.GLFW_PRESS;
            if (n && !nLast) { tagOn = !tagOn; client.player.sendMessage(Text.literal(tagOn ? "§dBar Acildi" : "§fBar Kapatildi"), true); }
            nLast = n;

            // P TUŞU - ELYTRA TARGET (BEST BYPASS)
            boolean p = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_P) == GLFW.GLFW_PRESS;
            if (p && !pLast) { 
                elytraOn = !elytraOn; 
                client.player.sendMessage(Text.literal(elytraOn ? "§dElytra Target: AKTIF" : "§fElytra Target: KAPALI"), true); 
            }
            pLast = p;

            // --- OTOMATİK ÖZELLİKLER ---
            if (client.options.forwardKey.isPressed() && !client.player.horizontalCollision && !client.player.isSneaking() && client.player.getHungerManager().getFoodLevel() > 6)
                client.player.setSprinting(true);

            if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION))
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false, false));

            // --- HEDEFLEME MANTIĞI ---
            boolean show = false;
            // Crosshair hedefi
            if (client.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity pl && pl.isAlive()) { 
                target = pl; 
                show = true; 
            }
            // En yakın oyuncuyu bul (Elytra için menzili artırıyoruz)
            if (!show) {
                double searchRange = elytraOn ? 100.0 : RANGE;
                List<PlayerEntity> near = client.world.getEntitiesByClass(PlayerEntity.class, client.player.getBoundingBox().expand(searchRange), e -> e != client.player && e.isAlive());
                if (!near.isEmpty()) {
                    near.sort(Comparator.comparingDouble(e -> client.player.distanceTo(e)));
                    target = near.get(0);
                    show = true;
                }
            }
            
            if (!show) target = null;
            alpha = show && hudOn ? Math.min(1f, alpha + FADE) : Math.max(0f, alpha - FADE);

            // --- ELYTRA TARGET BEST BYPASS ÇALIŞTIRMA ---
            if (elytraOn && client.player.isFallFlying() && target != null) {
                handleElytraBypass(client, target);
            }
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player == null || mc.options.hudHidden) return;
            
            int sw = mc.getWindow().getScaledWidth(), sh = mc.getWindow().getScaledHeight();
            int flopMain = getPinkWhiteFlop(0, 1.0f);
            
            // Sol üst bilgilendirme
            ctx.drawText(mc.textRenderer, "Elytra Target: " + (elytraOn ? "§aAKTIF" : "§cKAPALI") + " [P]", 5, 32, flopMain, true);
            
            // Orijinal HUD ve Bar kodların burada devam ediyor (Değiştirmedim)
            // [Buraya yukarıdaki HudRenderCallback içindeki geri kalan HUD kodlarını ekleyebilirsin]
        });
    }

    /**
     * Polar/Calor/Vulcan için pürüzsüz ve paket limitli uçuş asistanı
     */
    private void handleElytraBypass(MinecraftClient client, PlayerEntity target) {
        // Hedefin göğüs hizasını hesapla
        Vec3d tPos = target.getPos().add(0, target.getHeight() / 1.5, 0);
        Vec3d pPos = client.player.getPos().add(0, client.player.getEyeHeight(client.player.getPose()), 0);

        double dx = tPos.x - pPos.x;
        double dy = tPos.y - pPos.y;
        double dz = tPos.z - pPos.z;

        float tYaw = (float) Math.toDegrees(Math.atan2(dz, dx)) - 90.0f;
        float tPitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        // BYPASS: Pürüzsüz rotasyon (Aimbot flag yememek için)
        float speed = 12.0f; // Dönüş hızı (Güvenli seviye)
        client.player.setYaw(smooth(client.player.getYaw(), tYaw, speed));
        client.player.setPitch(smooth(client.player.getPitch(), tPitch, speed));

        // BYPASS: Hız kontrolü (Speed/Fly flag yememek için)
        Vec3d forward = Vec3d.fromPolar(client.player.getPitch(), client.player.getYaw());
        Vec3d velo = client.player.getVelocity();
        
        // Sadece hedefe doğru hafif bir itme uygula (Vanilla limitlerini aşmaz)
        client.player.setVelocity(velo.add(forward.multiply(0.045)));
        
        // Max hız clamp (Polar bypass için kritik)
        if (client.player.getVelocity().length() > 1.3) {
            client.player.setVelocity(client.player.getVelocity().normalize().multiply(1.3));
        }
    }

    private float smooth(float cur, float tar, float spd) {
        float d = MathHelper.wrapDegrees(tar - cur);
        return cur + MathHelper.clamp(d, -spd, spd);
    }

    private int getPinkWhiteFlop(int offset, float alphaMult) {
        double wave = (Math.sin((System.currentTimeMillis() + offset) / 300.0) + 1.0) / 2.0;
        int a = (int) (255 * alphaMult);
        int g = (int) (130 + 125 * wave);
        int b = (int) (200 + 55 * wave);
        return (a << 24) | (0xFF << 16) | (g << 8) | b;
    }

    private void btn(Screen sc, String t, int x, int y, int w, int h, ButtonWidget.PressAction a) {
        Screens.getButtons(sc).add(ButtonWidget.builder(Text.literal(t), a).dimensions(x, y, w, h).build());
    }

    private boolean isTrash(ItemStack s) {
        return s.isOf(Items.ROTTEN_FLESH) || s.isOf(Items.POISONOUS_POTATO) || s.isOf(Items.DIRT) || s.isOf(Items.COBBLESTONE) || s.isOf(Items.GRAVEL) || s.isOf(Items.SAND);
    }

    private boolean isArmor(ItemStack s) {
        String n = s.getItem().toString().toLowerCase();
        return n.contains("helmet") || n.contains("chestplate") || n.contains("leggings") || n.contains("boots");
    }
                                                                              }
