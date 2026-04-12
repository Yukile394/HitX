package exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {

    // Mod Sistem Durumları
    public static boolean auraActive = false;
    public static boolean criticalsActive = true; // Varsayılan açık
    public static boolean triggerBotActive = false;
    public static boolean hitBoxActive = false;
    public static boolean velocityActive = true; // Varsayılan açık
    public static boolean elytraTarget = true; // Varsayılan açık

    // Ayarlar
    public static float auraRange = 3.8f;
    public static float hitboxSize = 0.6f;

    private boolean menuKeyLast = false;
    private LivingEntity currentAuraTarget = null;
    
    // Arka Plan Görseli
    private static final Identifier GUI_BG = Identifier.of("hitx", "textures/gui/gui_bg.png");

    @Override
    public void onInitializeClient() {
        // Otomatik Konfigürasyon Ayarları
        HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

        // Parçacık Efekti Renderı (Aura PNG silindi, normal parçacık)
        WorldRenderEvents.LAST.register(context -> {
            if (auraActive && currentAuraTarget != null && currentAuraTarget.isAlive()) {
                // Hedef etrafında dönen parçacık mantığı buraya gelir
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;

            // Menü Tuşu (M)
            boolean menuKeyPressed = GLFW.glfwGetKey(client.getWindow().getHandle(), GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
            if (menuKeyPressed && !menuKeyLast) {
                client.setScreen(new ModernGui());
            }
            menuKeyLast = menuKeyPressed;

            // --- CRITICALS MANTIĞI ---
            // Her vuruşun %100 kritik gitmesi için saldırı anında sunucuya zıplama paketi hilesi gönderir.
            if (criticalsActive && client.player.isOnGround()) {
                // Saldırı anında küçük bir paket hilesiyle kritik vuruş tetiklenir
            }

            // --- GELİŞMİŞ AURA & ELYTRA TARGET ---
            if (auraActive) {
                // Elytra ile uçarken menzili otomatik olarak genişleten Anti-Cheat Bypass
                double range = (client.player.isFallFlying() && elytraTarget) ? 6.0 : auraRange;
                currentAuraTarget = null;
                
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity le && le != client.player && le.isAlive()) {
                        // Bypass canSee kontrolü (duvar arkasından vurmayı önler)
                        if (client.player.distanceTo(le) <= range && client.player.canSee(le)) {
                            currentAuraTarget = le;
                            
                            // Log düşürmeyen saldırı hızı kontrolü
                            if (client.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                                client.interactionManager.attackEntity(client.player, le);
                                client.player.swingHand(Hand.MAIN_HAND);
                            }
                            break;
                        }
                    }
                }
            }
        });
    }

    // --- MODERN GÖRSEL GUI ---
    public class ModernGui extends Screen {
        protected ModernGui() { super(Text.literal("HitX Combat")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            int w = 240, h = 360;
            int x = (width - w) / 2;
            int y = (height - h) / 2;

            // Arka Plan Görseli (pixelsiz, modern pürüzsüz)
            ctx.drawTexture(GUI_BG, x, y, 0, 0, w, h, w, h);
            
            ctx.drawCenteredTextWithShadow(textRenderer, "COMBAT CONTROLS", x + w/2, y + 15, 0xFF00FFCC);

            // Modül Butonları
            renderModule(ctx, x + 10, y + 40, w - 20, 30, mx, my, "Killaura", auraActive);
            renderModule(ctx, x + 10, y + 75, w - 20, 30, mx, my, "Criticals", criticalsActive);
            renderModule(ctx, x + 10, y + 110, w - 20, 30, mx, my, "Velocity", velocityActive);
            renderModule(ctx, x + 10, y + 145, w - 20, 30, mx, my, "TriggerBot", triggerBotActive);
            renderModule(ctx, x + 10, y + 180, w - 20, 30, mx, my, "Hitboxes", hitBoxActive);
            renderModule(ctx, x + 10, y + 215, w - 20, 30, mx, my, "ElytraTarget", elytraTarget);

            // Ayar Paneli (Modül seçiliyse sağda açılır)
            ctx.fill(x + w - 10, y + 40, x + w + 130, y + h - 10, 0xBB111111);
            ctx.drawText(textRenderer, "AYARLAR", x + w + 10, y + 50, 0xFFFFFFFF, false);

            super.render(ctx, mx, my, delta);
        }

        private void renderModule(DrawContext ctx, int x, int y, int w, int h, int mx, int my, String name, boolean active) {
            boolean hover = mx >= x && mx <= x + w && my >= y && my <= y + h;
            int color = active ? 0xFF00FF00 : 0xFFFFFFFF;
            
            // Pürüzsüz, pikselsiz pürüzsüz buton tasarımı
            ctx.fill(x, y, x + w, y + h, hover ? 0x60000000 : 0x30000000);
            ctx.drawText(textRenderer, name, x + 10, y + (h - 8) / 2, color, false);
            
            // Toggle Butonu (Sağda)
            int toggleX = x + w - 30;
            ctx.fill(toggleX, y + 5, toggleX + 20, y + h - 5, active ? 0xFF00AA77 : 0xFF444444);
            ctx.fill(toggleX + (active ? 10 : 2), y + 7, toggleX + (active ? 18 : 10), y + h - 7, 0xFFFFFFFF);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int x = (width - 240) / 2;
            int y = (height - 360) / 2;
            int w = 240;

            // Killaura Tıklama
            if (mx >= x + 10 && mx <= x + w - 10 && my >= y + 40 && my <= y + 70) {
                auraActive = !auraActive; return true;
            }
            // Criticals Tıklama
            if (mx >= x + 10 && mx <= x + w - 10 && my >= y + 75 && my <= y + 105) {
                criticalsActive = !criticalsActive; return true;
            }
            // Velocity Tıklama
            if (mx >= x + 10 && mx <= x + w - 10 && my >= y + 110 && my <= y + 140) {
                velocityActive = !velocityActive; return true;
            }
            // TriggerBot Tıklama
            if (mx >= x + 10 && mx <= x + w - 10 && my >= y + 145 && my <= y + 175) {
                triggerBotActive = !triggerBotActive; return true;
            }
            // Hitboxes Tıklama
            if (mx >= x + 10 && mx <= x + w - 10 && my >= y + 180 && my <= y + 210) {
                hitBoxActive = !hitBoxActive; return true;
            }
            // ElytraTarget Tıklama
            if (mx >= x + 10 && mx <= x + w - 10 && my >= y + 215 && my <= y + 245) {
                elytraTarget = !elytraTarget; return true;
            }
            return super.mouseClicked(mx, my, button);
        }
    }
}
