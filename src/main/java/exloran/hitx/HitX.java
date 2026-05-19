package exloran.hitx;

import com.mojang.blaze3d.systems.RenderSystem;
import exloran.hitx.listener.OverlayReloadListener;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

public class HitX implements ClientModInitializer {

    // ── Modüller ─────────────────────────────────────────────
    public static boolean hitBoxActive      = false;
    public static boolean triggerBotActive  = false;
    public static boolean hitColorActive    = false;

    // ── Ayarlar ──────────────────────────────────────────────
    // HitColor
    public static int hitColorR = 255, hitColorG = 50, hitColorB = 50, hitColorA = 200;

    // ── Tuşlar ───────────────────────────────────────────────
    public static int keyTriggerBot  = GLFW.GLFW_KEY_K;
    public static int keyHitbox      = GLFW.GLFW_KEY_H;

    // ── İç durum ─────────────────────────────────────────────
    private boolean mLast, kTrigLast, kHbLast;
    private long lastAttack = 0L;
    public static int triggerDelay  = 50;
    public static boolean trigBlkShield = true;
    public static boolean trigBlkGap    = true;
    public static boolean trigBlkSelf   = true;

    // ══════════════════════════════════════════════════════════
    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);
        ClientTickEvents.END_WORLD_TICK.register(w -> OverlayReloadListener.callEvent());

        // 3D Neon kutu + zemin halkası renderer
        NeonBoxRenderer.register();

        // HUD — tamamen kapalı, renderHUD çağrılmıyor

        // Ana tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long h = client.getWindow().getHandle();

            // Menü aç
            boolean mNow = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
            if (mNow && !mLast) client.setScreen(new HitXMenu());
            mLast = mNow;

            if (client.currentScreen == null) {
                boolean kTrig = GLFW.glfwGetKey(h, keyTriggerBot)  == GLFW.GLFW_PRESS;
                boolean kHb   = GLFW.glfwGetKey(h, keyHitbox)      == GLFW.GLFW_PRESS;

                if (kTrig && !kTrigLast) { triggerBotActive=!triggerBotActive; bar(client, triggerBotActive?"§aTriggerBot":"§cTriggerBot"); }
                if (kHb   && !kHbLast)   { hitBoxActive=!hitBoxActive; bar(client, hitBoxActive?"§aHitbox":"§cHitbox"); }

                kTrigLast=kTrig; kHbLast=kHb;
            }

            // Hitbox genişlet
            if (hitBoxActive) {
                HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
                for (Entity e : client.world.getEntities()) {
                    if (e instanceof LivingEntity le && le != client.player) {
                        float hw = (0.6f * cfg.xzExpand) / 2f;
                        float ht = 1.8f * cfg.yExpand;
                        le.setBoundingBox(new Box(
                            le.getX()-hw, le.getY()+cfg.yOffset, le.getZ()-hw,
                            le.getX()+hw, le.getY()+ht+cfg.yOffset, le.getZ()+hw));
                    }
                }
            }

            handleTrigger(client);
        });
    }

    // ══════════════════════════════════════════════════════════
    //  TRIGGERBOT
    // ══════════════════════════════════════════════════════════
    private void handleTrigger(MinecraftClient c) {
        if (!triggerBotActive) return;
        LivingEntity atk = null;
        if (c.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hr
                && hr.getEntity() instanceof LivingEntity le && le.isAlive())
            atk = le;
        if (atk == null) return;
        if (trigBlkShield && atk.isBlocking()) return;
        if (trigBlkGap    && isEating(atk)) return;
        if (trigBlkSelf   && c.player.isBlocking()) return;
        if (c.player.getAttackCooldownProgress(0.5f) < 1.0f) return;
        long now = System.currentTimeMillis();
        if (now - lastAttack < triggerDelay) return;
        c.interactionManager.attackEntity(c.player, atk);
        c.player.swingHand(Hand.MAIN_HAND);
        lastAttack = now;
    }

    // ══════════════════════════════════════════════════════════
    //  ROUNDED RECT ─ Menü için
    // ══════════════════════════════════════════════════════════
    public static void fillRound(MatrixStack ms, float x, float y, float w, float h, float r, int color) {
        float a=((color>>24)&0xFF)/255f, rv=((color>>16)&0xFF)/255f,
              g=((color>>8)&0xFF)/255f,  b=(color&0xFF)/255f;
        if (a <= 0) return;
        r = Math.min(r, Math.min(w,h)*0.499f);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Matrix4f m4 = ms.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buf.vertex(m4, x+w/2f, y+h/2f, 0).color(rv,g,b,a);
        float[] cx={x+w-r,x+r,x+r,x+w-r}, cy={y+r,y+r,y+h-r,y+h-r}, sa={270f,180f,90f,0f};
        for (int i=0;i<4;i++) for (int j=0;j<=12;j++) {
            double ang=Math.toRadians(sa[i]+j*7.5);
            buf.vertex(m4,(float)(cx[i]+Math.cos(ang)*r),(float)(cy[i]+Math.sin(ang)*r),0).color(rv,g,b,a);
        }
        double ca=Math.toRadians(sa[0]);
        buf.vertex(m4,(float)(cx[0]+Math.cos(ca)*r),(float)(cy[0]+Math.sin(ca)*r),0).color(rv,g,b,a);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    public static void outlineRound(MatrixStack ms, float x, float y, float w, float h, float r, int color) {
        float a=((color>>24)&0xFF)/255f, rv=((color>>16)&0xFF)/255f,
              g=((color>>8)&0xFF)/255f,  b=(color&0xFF)/255f;
        if (a <= 0) return;
        r = Math.min(r, Math.min(w,h)*0.499f);
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(1.2f);
        Matrix4f m4 = ms.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);
        float[] cx={x+w-r,x+r,x+r,x+w-r}, cy={y+r,y+r,y+h-r,y+h-r}, sa={270f,180f,90f,0f};
        for (int i=0;i<4;i++) for (int j=0;j<=12;j++) {
            double ang=Math.toRadians(sa[i]+j*7.5);
            buf.vertex(m4,(float)(cx[i]+Math.cos(ang)*r),(float)(cy[i]+Math.sin(ang)*r),0).color(rv,g,b,a);
        }
        double ca=Math.toRadians(sa[0]);
        buf.vertex(m4,(float)(cx[0]+Math.cos(ca)*r),(float)(cy[0]+Math.sin(ca)*r),0).color(rv,g,b,a);
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  MENÜ — Sadece TriggerBot, Hitboxes, HitColor
    // ══════════════════════════════════════════════════════════
    public class HitXMenu extends Screen {

        private static final int MW = 320, MH = 200;
        private float anim = 0f;

        protected HitXMenu() { super(Text.literal("HitX")); }

        @Override public void tick() { super.tick(); anim += 0.04f; }
        @Override public boolean shouldPause() { return false; }

        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            MatrixStack ms = ctx.getMatrices();
            int ox = ox(), oy = oy();

            // Arka plan
            ctx.fill(0, 0, width, height, 0x55000000);

            // Ana panel
            fillRound(ms, ox, oy, MW, MH, 6f, 0xF0101015);
            outlineRound(ms, ox, oy, MW, MH, 6f, 0xFF1C1C2A);

            // Başlık şeridi
            fillRound(ms, ox, oy, MW, 22, 6f, 0xFF0C0C16);
            ctx.fill(ox, oy+11, ox+MW, oy+22, 0xFF0C0C16);
            ctx.drawCenteredTextWithShadow(textRenderer, "§dHitX", ox+MW/2, oy+6, 0xFFCC88FF);

            // Modül listesi
            String[] mods = {"TriggerBot", "Hitboxes", "HitColor"};
            int my2 = oy + 30;
            for (String mod : mods) {
                boolean on = isOn(mod);
                boolean hov = hov(mx, my, ox+8, my2, MW-16, 18);
                if (on)       fillRound(ms, ox+8, my2, MW-16, 18, 3f, 0xFF180030);
                else if (hov) fillRound(ms, ox+8, my2, MW-16, 18, 3f, 0xFF141420);
                if (on) ctx.fill(ox+8, my2, ox+9, my2+18, 0xFFAA44FF);
                ctx.drawTextWithShadow(textRenderer, mod, ox+14, my2+5,
                        on ? 0xFFEEDDFF : (hov ? 0xFFAAAAAA : 0xFF444455));
                if (on) ctx.drawTextWithShadow(textRenderer, "§a●", ox+MW-22, my2+5, 0xFFFFFFFF);
                my2 += 20;
            }

            // Alt bilgi
            ctx.drawTextWithShadow(textRenderer, "§8HitX — M to close", ox+4, oy+MH-10, 0xFF2A2A3A);

            super.render(ctx, mx, my, delta);
        }

        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            int ox = ox(), oy = oy();
            String[] mods = {"TriggerBot", "Hitboxes", "HitColor"};
            int my2 = oy + 30;
            for (String mod : mods) {
                if (hovD(mx, my, ox+8, my2, MW-16, 18)) { toggle(mod); return true; }
                my2 += 20;
            }
            return super.mouseClicked(mx, my, btn);
        }

        private boolean isOn(String n) {
            return switch(n) {
                case "TriggerBot" -> triggerBotActive;
                case "Hitboxes"   -> hitBoxActive;
                case "HitColor"   -> hitColorActive;
                default -> false;
            };
        }

        private void toggle(String n) {
            MinecraftClient c = MinecraftClient.getInstance();
            switch(n) {
                case "TriggerBot" -> triggerBotActive = !triggerBotActive;
                case "Hitboxes"   -> hitBoxActive     = !hitBoxActive;
                case "HitColor"   -> hitColorActive   = !hitColorActive;
            }
        }

        private int ox() { return width/2 - MW/2; }
        private int oy() { return height/2 - MH/2; }
        private boolean hov(int mx, int my, int x, int y, int w, int h) { return mx>=x&&mx<=x+w&&my>=y&&my<=y+h; }
        private boolean hovD(double mx, double my, double x, double y, double w, double h) { return mx>=x&&mx<=x+w&&my>=y&&my<=y+h; }
    }

    // ── Yardımcılar ──────────────────────────────────────────
    private boolean isEating(LivingEntity e) {
        if (e==null || !e.isUsingItem()) return false;
        ItemStack u = e.getActiveItem();
        if (u.isEmpty()) return false;
        return u.getComponents().contains(net.minecraft.component.DataComponentTypes.FOOD)
            || u.getItem()==Items.MILK_BUCKET
            || u.getItem()==Items.POTION
            || u.getItem()==Items.SPLASH_POTION
            || u.getItem()==Items.LINGERING_POTION;
    }

    private void bar(MinecraftClient c, String m) {
        if (c.player != null)
            c.player.sendMessage(Text.literal("§8[§dHitX§8] §r" + m), true);
    }
}
