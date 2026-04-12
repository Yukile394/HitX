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
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

public class HitX implements ClientModInitializer {

    public static boolean hitBoxActive = false;
    public static boolean triggerBotActive = false;
    public static boolean auraActive = false;
    
    public static float auraRange = 3.2f;
    public static float elytraRange = 5.5f;
    public static boolean elytraTarget = true;
    public static boolean showAuraParticles = true;

    private boolean mLast = false;
    private LivingEntity currentAuraTarget = null;
    
    // DERLEME HATASI BURADA DÜZELTİLDİ: Identifier.of kullanımı zorunludur.
    private static final Identifier AURA_FX = Identifier.of("hitx", "textures/gui/aura_fx.png");

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);
        HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

        // Envanter/Sandık Hızlı Butonları
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            int bx = W / 2 + 92, by = H / 2 - 50; 
            if (screen instanceof InventoryScreen inv) {
                int id = inv.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.DIAMOND_CHESTPLATE), "§bZırhı Giy", bx, by, 22, 22, b -> {
                    for (int i = 9; i < 45; i++) if (isArmor(inv.getScreenHandler().getSlot(i).getStack())) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                });
            }
            if (screen instanceof GenericContainerScreen chest) {
                int id = chest.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.HOPPER), "§aHepsini Al", bx, by, 22, 22, b -> {
                    for (int i = 0; i < chest.getScreenHandler().getInventory().size(); i++) client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                });
            }
        });

        // Aura Görsel Parçacık (HUD Render)
        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (auraActive && showAuraParticles && currentAuraTarget != null && mc.player != null && !mc.options.hudHidden) {
                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();
                ctx.drawTexture(AURA_FX, sw / 2 - 16, sh / 2 - 16, 0, 0, 32, 32, 32, 32);
            }
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            
            checkToggle(client, GLFW.GLFW_KEY_M, () -> { client.setScreen(new HitXMenu()); return ""; }, mLast, v -> mLast = v);

            if (hitBoxActive) {
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity && e != client.player) {
                        float w = 0.6f * config.xzExpand;
                        float h = 1.8f * config.yExpand;
                        e.setBoundingBox(new Box(e.getX() - w/2, e.getY() + config.yOffset, e.getZ() - w/2, e.getX() + w/2, e.getY() + h + config.yOffset, e.getZ() + w/2));
                    }
                }
            }

            currentAuraTarget = null;
            if (triggerBotActive || auraActive) {
                if (client.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                    LivingEntity target = null;
                    if (triggerBotActive && client.crosshairTarget instanceof EntityHitResult hit) {
                        if (hit.getEntity() instanceof LivingEntity le && le != client.player) target = le;
                    }
                    if (auraActive && target == null) {
                        double dist = (client.player.isFallFlying() && elytraTarget) ? elytraRange : auraRange;
                        for (Entity e : client.world.getEntities()) {
                            if (e instanceof LivingEntity le && le != client.player && le.isAlive() && client.player.canSee(le)) {
                                if (client.player.distanceTo(le) <= dist) { target = le; break; }
                            }
                        }
                    }
                    if (target != null) {
                        currentAuraTarget = target;
                        client.interactionManager.attackEntity(client.player, target);
                        client.player.swingHand(Hand.MAIN_HAND);
                    }
                }
            }
        });
    }

    // --- GELİŞMİŞ PÜRÜZSÜZ SAĞ TIK MENÜSÜ ---
    public class HitXMenu extends Screen {
        private String openSettings = "";
        private int activeSlider = -1;

        protected HitXMenu() { super(Text.literal("HitX")); }

        @Override
        public void render(DrawContext ctx, int mx, int my, float d) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int w = 380, h = 220, x = width/2 - w/2, y = height/2 - h/2;
            
            drawRoundedRect(ctx, x, y, x + w, y + h, 0xF01A1A1A, 6);
            drawRoundedRect(ctx, x, y, x + 160, y + h, 0xFF2A2A2A, 6);
            
            ctx.drawCenteredTextWithShadow(textRenderer, "§lMODÜLLER", x + 80, y + 10, 0xFFFFFF);
            drawModuleBtn(ctx, x + 10, y + 30, 140, 24, mx, my, "Hitboxes", hitBoxActive);
            drawModuleBtn(ctx, x + 10, y + 60, 140, 24, mx, my, "Aura", auraActive);
            drawModuleBtn(ctx, x + 10, y + 90, 140, 24, mx, my, "TriggerBot", triggerBotActive);

            int rx = x + 170;
            if (!openSettings.isEmpty()) {
                ctx.drawTextWithShadow(textRenderer, "§l" + openSettings.toUpperCase() + " AYARLARI", rx, y + 10, 0xFF00FFBB);
                drawRoundedRect(ctx, rx, y + 25, rx + 200, y + h - 10, 0xFF222222, 4);

                if (openSettings.equals("Hitboxes")) {
                    drawSlider(ctx, rx + 10, y + 40, 180, "Genişlik: " + String.format("%.1f", cfg.xzExpand), (cfg.xzExpand - 0.5f) / 4.5f);
                    drawSlider(ctx, rx + 10, y + 75, 180, "Yükseklik: " + String.format("%.1f", cfg.yExpand), (cfg.yExpand - 0.5f) / 3.5f);
                    drawToggleBtn(ctx, rx + 10, y + 110, 180, 20, cfg.fakeHitbox, "Gizli (Fake) Hitbox");
                } else if (openSettings.equals("Aura")) {
                    drawSlider(ctx, rx + 10, y + 40, 180, "Menzil: " + String.format("%.1f", auraRange), (auraRange - 2.0f) / 4.0f);
                    drawToggleBtn(ctx, rx + 10, y + 75, 180, 20, elytraTarget, "Elytra Target");
                    drawToggleBtn(ctx, rx + 10, y + 105, 180, 20, showAuraParticles, "Görsel Efekt");
                }
            }
            super.render(ctx, mx, my, d);
        }

        private void drawModuleBtn(DrawContext ctx, int x, int y, int w, int h, int mx, int my, String n, boolean s) {
            boolean hv = mx >= x && mx <= x + w && my >= y && my <= y + h;
            drawRoundedRect(ctx, x, y, x + w, y + h, s ? 0xFF00FFBB : (hv ? 0xFF505050 : 0xFF404040), 4);
            ctx.drawTextWithShadow(textRenderer, n, x + 10, y + 8, s ? 0x000000 : 0xFFFFFF);
        }

        private void drawSlider(DrawContext ctx, int x, int y, int w, String t, float p) {
            ctx.drawTextWithShadow(textRenderer, t, x, y, 0xFFFFFF);
            drawRoundedRect(ctx, x, y + 12, x + w, y + 20, 0xFF111111, 3);
            drawRoundedRect(ctx, x, y + 12, x + (int)(w * p), y + 20, 0xFF00FFBB, 3);
        }

        private void drawToggleBtn(DrawContext ctx, int x, int y, int w, int h, boolean s, String n) {
            drawRoundedRect(ctx, x, y, x + w, y + h, s ? 0xFF2A5A4A : 0xFF3A3A3A, 4);
            ctx.drawTextWithShadow(textRenderer, n, x + 8, y + 6, s ? 0x00FFBB : 0xAAAAAA);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int w = 380, h = 220, x = width/2 - w/2, y = height/2 - h/2;
            int rx = x + 170;

            if (mx >= x + 10 && mx <= x + 150) {
                if (my >= y + 30 && my <= y + 54) { if (btn == 0) hitBoxActive = !hitBoxActive; else openSettings = "Hitboxes"; return true; }
                if (my >= y + 60 && my <= y + 84) { if (btn == 0) auraActive = !auraActive; else openSettings = "Aura"; return true; }
                if (my >= y + 90 && my <= y + 114) { if (btn == 0) triggerBotActive = !triggerBotActive; else openSettings = "TriggerBot"; return true; }
            }
            
            if (!openSettings.isEmpty() && mx >= rx + 10 && mx <= rx + 190) {
                if (openSettings.equals("Aura") && my >= y + 75 && my <= y + 95) elytraTarget = !elytraTarget;
                if (openSettings.equals("Aura") && my >= y + 105 && my <= y + 125) showAuraParticles = !showAuraParticles;
            }

            return super.mouseClicked(mx, my, btn);
        }
    }

    private void drawRoundedRect(DrawContext ctx, int x1, int y1, int x2, int y2, int c, int r) {
        ctx.fill(x1 + r, y1, x2 - r, y2, c); ctx.fill(x1, y1 + r, x2, y2 - r, c);
    }

    private void iconBtn(Screen s, ItemStack i, String t, int x, int y, int w, int h, ButtonWidget.PressAction a) {
        Screens.getButtons(s).add(new ButtonWidget(x, y, w, h, Text.empty(), a, scr -> Text.empty()) {
            @Override public void renderWidget(DrawContext ctx, int mx, int my, float d) {
                drawRoundedRect(ctx, getX(), getY(), getX()+w, getY()+h, isHovered() ? 0xFF333333 : 0xFF1A1A1A, 4);
                ctx.drawItem(i, getX()+3, getY()+3);
            }
        });
    }

    private void checkToggle(MinecraftClient c, int k, java.util.function.Supplier<String> a, boolean l, java.util.function.Consumer<Boolean> s) {
        boolean n = GLFW.glfwGetKey(c.getWindow().getHandle(), k) == GLFW.GLFW_PRESS;
        if (n && !l) a.get();
        s.accept(n);
    }

    private boolean isArmor(ItemStack s) { return s.getItem().toString().contains("helmet") || s.getItem().toString().contains("chestplate"); }
        }
