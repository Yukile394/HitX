package exloran.hitx;

import com.mojang.blaze3d.systems.RenderSystem;
import exloran.hitx.listener.OverlayReloadListener;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.render.*;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {

    // ══════════════════════════════════════════════════════════
    //  MODÜL DURUMLARI
    // ══════════════════════════════════════════════════════════
    public static boolean hitBoxActive      = false;
    public static boolean triggerBotActive  = false;
    public static boolean aimAssistActive   = false;
    public static boolean nightVisionActive = false;
    public static boolean speedActive       = false;
    public static boolean sprintActive      = false;
    public static boolean antiKbActive      = false;
    public static boolean espActive         = false;
    public static boolean noFallActive      = false;
    public static boolean fullBrightActive  = false;

    // ── AimAssist Ayarları ───────────────────────────────────
    public static float   aimRange          = 4.5f;
    public static float   aimSpeed          = 0.08f;   // düşürüldü — daha smooth
    public static float   aimFov            = 90f;
    public static boolean aimAutoAttack     = false;
    public static boolean aimRecoil         = false;
    public static float   aimRecoilStr      = 0.25f;
    public static boolean aimElytra         = true;
    public static float   aimElytraRange    = 6.0f;
    public static boolean aimOnlyPlayers    = false;   // YENİ: sadece oyuncuları hedefle

    // ── TriggerBot Ayarları ─────────────────────────────────
    public static int     triggerDelay      = 50;

    // ── Speed Ayarları ──────────────────────────────────────
    public static float   speedMultiplier   = 1.35f;

    // ── ESP Ayarları ────────────────────────────────────────
    public static boolean espPlayers        = true;
    public static boolean espMobs           = false;
    public static int     espColorR         = 255;
    public static int     espColorG         = 60;
    public static int     espColorB         = 60;

    // ── AntiKB Ayarları ─────────────────────────────────────
    public static float   antiKbStrength    = 1.0f;    // 1.0 = tam iptal

    // ── Keybindlar ──────────────────────────────────────────
    public static int keyHitbox      = GLFW.GLFW_KEY_H;
    public static int keyAimAssist   = GLFW.GLFW_KEY_J;
    public static int keyTriggerBot  = GLFW.GLFW_KEY_K;
    public static int keyNightVision = GLFW.GLFW_KEY_N;
    public static int keySpeed       = GLFW.GLFW_KEY_V;
    public static int keyEsp         = GLFW.GLFW_KEY_Z;

    // ── İç Durum ─────────────────────────────────────────────
    private boolean mLast=false, kHLast=false, kALast=false, kTLast=false;
    private boolean kNLast=false, kSLast=false, kELast=false;
    private long    lastAttack = 0L;
    private long    nvTick     = 0L;
    private LivingEntity locked = null;

    // ── AimAssist Smooth State ───────────────────────────────
    private float   smoothYawAcc   = 0f;
    private float   smoothPitchAcc = 0f;
    private final float[] yawBuf   = new float[6];
    private final float[] pitchBuf = new float[6];
    private int     bufIdx         = 0;

    // ══════════════════════════════════════════════════════════
    //  INIT
    // ══════════════════════════════════════════════════════════
    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);
        ClientTickEvents.END_WORLD_TICK.register(w -> OverlayReloadListener.callEvent());

        // Inventory'e Zırh Giy butonu
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            if (screen instanceof InventoryScreen inv) {
                int id = inv.getScreenHandler().syncId;
                iconBtn(screen, new ItemStack(Items.DIAMOND_CHESTPLATE), "§bZırhı Giy",
                        W/2+92, H/2-50, 22, 22,
                        b -> {
                            for (int i = 9; i < 45; i++)
                                if (isArmor(inv.getScreenHandler().getSlot(i).getStack()))
                                    client.interactionManager.clickSlot(id, i, 0, SlotActionType.QUICK_MOVE, client.player);
                        });
            }
        });

        // HUD — ESP kutuları çiz
        HudRenderCallback.EVENT.register((ctx, tick) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!espActive || client.player == null || client.world == null) return;
            if (client.options.debugEnabled) return;
            renderESP(client, ctx);
        });

        // Ana Tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || client.world == null) return;
            long handle = client.getWindow().getHandle();

            // ── Menü (M) ──
            boolean mNow = GLFW.glfwGetKey(handle, GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
            if (mNow && !mLast) client.setScreen(new HitXMenu());
            mLast = mNow;

            if (client.currentScreen == null) {
                boolean kH = GLFW.glfwGetKey(handle, keyHitbox)      == GLFW.GLFW_PRESS;
                boolean kA = GLFW.glfwGetKey(handle, keyAimAssist)   == GLFW.GLFW_PRESS;
                boolean kT = GLFW.glfwGetKey(handle, keyTriggerBot)  == GLFW.GLFW_PRESS;
                boolean kN = GLFW.glfwGetKey(handle, keyNightVision) == GLFW.GLFW_PRESS;
                boolean kS = GLFW.glfwGetKey(handle, keySpeed)       == GLFW.GLFW_PRESS;
                boolean kE = GLFW.glfwGetKey(handle, keyEsp)         == GLFW.GLFW_PRESS;

                if (kH && !kHLast) hitBoxActive = !hitBoxActive;
                if (kA && !kALast) {
                    aimAssistActive = !aimAssistActive;
                    locked = null;
                    bar(client, aimAssistActive ? "§aAimAssist §7Açık" : "§cAimAssist §7Kapalı");
                }
                if (kT && !kTLast) {
                    triggerBotActive = !triggerBotActive;
                    bar(client, triggerBotActive ? "§aTriggerBot §7Açık" : "§cTriggerBot §7Kapalı");
                }
                if (kN && !kNLast) {
                    nightVisionActive = !nightVisionActive;
                    if (!nightVisionActive) client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
                    bar(client, nightVisionActive ? "§aGece Görüşü §7Açık" : "§cGece Görüşü §7Kapalı");
                }
                if (kS && !kSLast) {
                    speedActive = !speedActive;
                    bar(client, speedActive ? "§aSpeed §7Açık" : "§cSpeed §7Kapalı");
                }
                if (kE && !kELast) {
                    espActive = !espActive;
                    bar(client, espActive ? "§aESP §7Açık" : "§cESP §7Kapalı");
                }
                kHLast=kH; kALast=kA; kTLast=kT; kNLast=kN; kSLast=kS; kELast=kE;
            }

            // ── HitBoxes ──
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

            // ── Night Vision ──
            if (nightVisionActive) {
                nvTick++;
                if (nvTick % 4 == 0) {
                    StatusEffectInstance cur = client.player.getStatusEffect(StatusEffects.NIGHT_VISION);
                    if (cur == null || cur.getDuration() < 60)
                        client.player.addStatusEffect(
                                new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false));
                }
            }

            // ── FullBright ──
            if (fullBrightActive) {
                StatusEffectInstance nb = client.player.getStatusEffect(StatusEffects.NIGHT_VISION);
                if (nb == null || nb.getDuration() < 60)
                    client.player.addStatusEffect(
                            new StatusEffectInstance(StatusEffects.NIGHT_VISION, 400, 0, false, false));
            }

            // ── Speed ──
            if (speedActive && client.player.isOnGround() && !client.player.isSneaking()) {
                Vec3d vel = client.player.getVelocity();
                double hLen = Math.sqrt(vel.x*vel.x + vel.z*vel.z);
                if (hLen > 0.01) {
                    double factor = speedMultiplier;
                    client.player.setVelocity(vel.x * factor, vel.y, vel.z * factor);
                }
            }

            // ── AutoSprint ──
            if (sprintActive && client.player != null) {
                if (!client.player.isSneaking() && !client.player.isTouchingWater())
                    client.player.setSprinting(true);
            }

            // ── NoFall ──
            if (noFallActive && client.player != null) {
                if (client.player.fallDistance > 2.0f)
                    client.player.fallDistance = 0f;
            }

            handleCombat(client);
        });
    }

    // ══════════════════════════════════════════════════════════
    //  ESP — 2D BBOX OVERLAY
    // ══════════════════════════════════════════════════════════
    private void renderESP(MinecraftClient client, DrawContext ctx) {
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();

        for (Entity e : client.world.getEntities()) {
            if (e == client.player || !e.isAlive()) continue;
            boolean isPlayer = e instanceof PlayerEntity;
            boolean isMob    = e instanceof LivingEntity && !isPlayer;
            if (isPlayer && !espPlayers) continue;
            if (isMob    && !espMobs)    continue;

            // 3D bounding box'ı ekrana yansıt
            Box box = e.getBoundingBox().expand(0.05);
            double[] corners = {
                box.minX, box.minY, box.minZ,
                box.maxX, box.minY, box.minZ,
                box.minX, box.maxY, box.minZ,
                box.maxX, box.maxY, box.minZ,
                box.minX, box.minY, box.maxZ,
                box.maxX, box.minY, box.maxZ,
                box.minX, box.maxY, box.maxZ,
                box.maxX, box.maxY, box.maxZ
            };

            double minX2d = Double.MAX_VALUE, minY2d = Double.MAX_VALUE;
            double maxX2d = -Double.MAX_VALUE, maxY2d = -Double.MAX_VALUE;
            boolean anyOnScreen = false;

            for (int i = 0; i < 8; i++) {
                double wx = corners[i*3];
                double wy = corners[i*3+1];
                double wz = corners[i*3+2];
                double[] sc = worldToScreen(client, wx, wy, wz, sw, sh);
                if (sc == null) continue;
                anyOnScreen = true;
                if (sc[0] < minX2d) minX2d = sc[0];
                if (sc[1] < minY2d) minY2d = sc[1];
                if (sc[0] > maxX2d) maxX2d = sc[0];
                if (sc[1] > maxY2d) maxY2d = sc[1];
            }
            if (!anyOnScreen) continue;

            int ex = (int)minX2d, ey = (int)minY2d;
            int ew = (int)(maxX2d-minX2d), eh = (int)(maxY2d-minY2d);

            int col = isPlayer
                    ? (0xCC000000|(espColorR<<16)|(espColorG<<8)|espColorB)
                    : 0xCC44FF44;

            // Yuvarlak köşeli ESP kutusu
            drawESPBox(ctx, ex, ey, ew, eh, col);

            // İsim + kalp
            String label = e.getName().getString();
            if (e instanceof LivingEntity le) {
                int hp = (int) le.getHealth();
                label += " §c" + hp + "❤";
            }
            ctx.drawTextWithShadow(client.textRenderer, label, ex + ew/2 - client.textRenderer.getWidth(label)/2, ey - 10, col | 0xFF000000);
        }
    }

    private void drawESPBox(DrawContext ctx, int x, int y, int w, int h, int color) {
        int c = 4; // köşe uzunluğu
        // Köşe çizgileri (L-shape corner style)
        // Sol-Üst
        ctx.fill(x,     y,     x+c,   y+1,   color);
        ctx.fill(x,     y,     x+1,   y+c,   color);
        // Sağ-Üst
        ctx.fill(x+w-c, y,     x+w,   y+1,   color);
        ctx.fill(x+w-1, y,     x+w,   y+c,   color);
        // Sol-Alt
        ctx.fill(x,     y+h-1, x+c,   y+h,   color);
        ctx.fill(x,     y+h-c, x+1,   y+h,   color);
        // Sağ-Alt
        ctx.fill(x+w-c, y+h-1, x+w,   y+h,   color);
        ctx.fill(x+w-1, y+h-c, x+w,   y+h,   color);
        // Kenarlı çizgi (ince, yarı saydam)
        int edgeCol = (color & 0x00FFFFFF) | 0x33000000;
        ctx.fill(x+1, y,     x+w-1, y+1,   edgeCol);
        ctx.fill(x+1, y+h-1, x+w-1, y+h,   edgeCol);
        ctx.fill(x,   y+1,   x+1,   y+h-1, edgeCol);
        ctx.fill(x+w-1, y+1, x+w,   y+h-1, edgeCol);
    }

    private double[] worldToScreen(MinecraftClient client, double wx, double wy, double wz, int sw, int sh) {
        // Kamera pozisyonu
        double cx = client.gameRenderer.getCamera().getPos().x;
        double cy2= client.gameRenderer.getCamera().getPos().y;
        double cz = client.gameRenderer.getCamera().getPos().z;

        // Göreli pozisyon
        double rx = wx - cx;
        double ry = wy - cy2;
        double rz = wz - cz;

        // Kamera yaw ve pitch
        float yaw   = (float)Math.toRadians(client.gameRenderer.getCamera().getYaw());
        float pitch = (float)Math.toRadians(client.gameRenderer.getCamera().getPitch());

        double sinY = Math.sin(yaw), cosY = Math.cos(yaw);
        double sinP = Math.sin(pitch), cosP = Math.cos(pitch);

        // Kamera uzayına döndür
        double x1 =  rx * cosY - rz * sinY;
        double y1 =  ry * cosP + (rx * sinY + rz * cosY) * sinP;
        double z1 = -ry * sinP + (rx * sinY + rz * cosY) * cosP;

        if (z1 <= 0.01) return null; // arkamızda

        double fov = Math.toRadians(client.options.getFov().getValue());
        double f   = (sw / 2.0) / Math.tan(fov / 2.0);

        double sx = (sw / 2.0) + (x1 / z1) * f;
        double sy = (sh / 2.0) - (y1 / z1) * f;

        return new double[]{sx, sy};
    }

    // ══════════════════════════════════════════════════════════
    //  COMBAT
    // ══════════════════════════════════════════════════════════
    private void handleCombat(MinecraftClient client) {
        // Kilit geçerliliği
        if (locked != null && (!locked.isAlive() ||
                client.player.distanceTo(locked) > aimRange + 2.5f)) locked = null;

        // Hedef seç
        if (aimAssistActive && locked == null) {
            float maxD = (aimElytra && client.player.isFallFlying()) ? aimElytraRange : aimRange;
            double best = Double.MAX_VALUE;
            for (Entity e : client.world.getEntities()) {
                if (!(e instanceof LivingEntity le)) continue;
                if (le == client.player || !le.isAlive()) continue;
                if (aimOnlyPlayers && !(le instanceof PlayerEntity)) continue;
                double d = client.player.distanceTo(le);
                if (d > maxD) continue;
                float ang = angleTo(client, le);
                if (ang > aimFov) continue;
                // Score: açı ve mesafe ağırlıklı — yakın hedef ve nişanda olan öncelikli
                double score = ang * 0.6 + d * 0.4;
                if (score < best) { best = score; locked = le; }
            }
        }

        // AimAssist
        if (aimAssistActive && locked != null) smoothAim(client, locked);

        // TriggerBot
        LivingEntity trigTarget = null;
        if (triggerBotActive &&
                client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit &&
                hit.getEntity() instanceof LivingEntity le && le.isAlive()) {
            trigTarget = le;
        }

        LivingEntity autoTarget = (aimAutoAttack && locked != null) ? locked : null;
        LivingEntity atk = trigTarget != null ? trigTarget : autoTarget;
        if (atk == null) return;
        if (client.player.getAttackCooldownProgress(0.5f) < 1.0f) return;
        long now = System.currentTimeMillis();
        if (now - lastAttack < triggerDelay) return;

        client.interactionManager.attackEntity(client.player, atk);
        client.player.swingHand(Hand.MAIN_HAND);
        lastAttack = now;

        if (aimRecoil) client.player.setPitch(client.player.getPitch() - aimRecoilStr);
    }

    private float angleTo(MinecraftClient c, LivingEntity t) {
        Vec3d look = c.player.getRotationVec(1f);
        Vec3d toT  = t.getEyePos().subtract(c.player.getEyePos()).normalize();
        return (float) Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(toT), -1.0, 1.0)));
    }

    // ══════════════════════════════════════════════════════════
    //  DOOMSDAY-LEVEL AIM ASSIST — Titremesiz, Flawless
    // ══════════════════════════════════════════════════════════
    private void smoothAim(MinecraftClient client, LivingEntity target) {
        // 1. Velocity prediction — 2 tick ileri tahmin
        double vx = target.getX() - target.prevX;
        double vy = target.getY() - target.prevY;
        double vz = target.getZ() - target.prevZ;

        double px = target.getX() + vx * 2.0;
        double pz = target.getZ() + vz * 2.0;
        double py = target.getY() + vy * 0.3
                  + target.getEyeHeight(target.getPose()) * 0.82;

        // 2. Delta açılar
        double dx = px - client.player.getX();
        double dz = pz - client.player.getZ();
        double dy = py - (client.player.getY() + client.player.getEyeHeight(client.player.getPose()));
        double hDist = Math.sqrt(dx*dx + dz*dz);

        float targetYaw   = MathHelper.wrapDegrees(
            (float)Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        float targetPitch = (float)-Math.toDegrees(Math.atan2(dy, hDist));

        float deltaYaw   = MathHelper.wrapDegrees(targetYaw   - client.player.getYaw());
        float deltaPitch = MathHelper.wrapDegrees(targetPitch - client.player.getPitch());

        // 3. Mesafeye + açıya göre dinamik hız
        //    Hedefe yaklaştıkça hız azalır (overshooting önleme)
        float dist = client.player.distanceTo(target);
        float angle = angleTo(client, target);
        float distFactor  = MathHelper.clamp(dist / aimRange, 0.25f, 1.0f);
        float angleFactor = MathHelper.clamp(angle / 15f, 0.15f, 1.0f);  // 15°'den az → yavaşla
        float dynSpeed = aimSpeed * distFactor * angleFactor;

        // 4. Exponential smoothing accumulator
        smoothYawAcc   += (deltaYaw   - smoothYawAcc)   * dynSpeed * 2.5f;
        smoothPitchAcc += (deltaPitch - smoothPitchAcc) * dynSpeed * 2.5f;

        // 5. 6-tick rolling average — ani sıçrama engeller
        yawBuf[bufIdx]   = smoothYawAcc;
        pitchBuf[bufIdx] = smoothPitchAcc;
        bufIdx = (bufIdx + 1) % 6;

        float avgYaw = 0f, avgPitch = 0f;
        for (int i = 0; i < 6; i++) { avgYaw += yawBuf[i]; avgPitch += pitchBuf[i]; }
        avgYaw /= 6f; avgPitch /= 6f;

        // 6. GCD Fix — sensitivity'e göre hassas step
        double sens = client.options.getMouseSensitivity().getValue();
        double gcd  = Math.pow(sens * 0.6 + 0.2, 3.0) * 8.0 * 0.15;
        if (gcd < 0.001) gcd = 0.001;

        float stepYaw   = (float)(Math.round(avgYaw   / gcd) * gcd);
        float stepPitch = (float)(Math.round(avgPitch / gcd) * gcd);

        // 7. Uygula
        float newYaw   = client.player.getYaw()   + stepYaw;
        float newPitch = MathHelper.clamp(client.player.getPitch() + stepPitch, -90f, 90f);

        client.player.setYaw(newYaw);
        client.player.setPitch(newPitch);
    }

    private void bar(MinecraftClient c, String m) {
        if (c.player != null)
            c.player.sendMessage(Text.literal("§8[§dHitX§8] §r" + m), true);
    }

    // ══════════════════════════════════════════════════════════
    //  ROUNDED RECT ÇİZİM YARDIMCISI (Tessellator ile)
    // ══════════════════════════════════════════════════════════
    public static void drawRoundedRect(MatrixStack ms, float x, float y, float w, float h, float r, int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float rv = ((color >> 16) & 0xFF) / 255f;
        float g  = ((color >>  8) & 0xFF) / 255f;
        float b  = (color & 0xFF)          / 255f;
        if (a <= 0f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tess = Tessellator.getInstance();
        Matrix4f m4 = ms.peek().getPositionMatrix();

        // Fan çizimi — 4 köşe
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN, VertexFormats.POSITION_COLOR);
        buf.vertex(m4, x+w/2, y+h/2, 0).color(rv,g,b,a);

        int segs = 10;
        float[] cx2 = {x+w-r, x+r,   x+r,   x+w-r};
        float[] cy2 = {y+r,   y+r,   y+h-r, y+h-r};
        float[] sa  = {270f,  180f,  90f,   0f};

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j <= segs; j++) {
                double ang = Math.toRadians(sa[i] + j * 90f / segs);
                buf.vertex(m4,
                    (float)(cx2[i] + Math.cos(ang)*r),
                    (float)(cy2[i] + Math.sin(ang)*r), 0).color(rv,g,b,a);
            }
        }
        // Kapat
        double ang0 = Math.toRadians(sa[0]);
        buf.vertex(m4, (float)(cx2[0]+Math.cos(ang0)*r), (float)(cy2[0]+Math.sin(ang0)*r), 0).color(rv,g,b,a);
        BufferRenderer.drawWithGlobalProgram(buf.end());

        RenderSystem.disableBlend();
    }

    public static void drawRoundedOutline(MatrixStack ms, float x, float y, float w, float h, float r, int color) {
        float a = ((color >> 24) & 0xFF) / 255f;
        float rv = ((color >> 16) & 0xFF) / 255f;
        float g  = ((color >>  8) & 0xFF) / 255f;
        float b  = (color & 0xFF)          / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(1.2f);

        Tessellator tess = Tessellator.getInstance();
        Matrix4f m4 = ms.peek().getPositionMatrix();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP, VertexFormats.POSITION_COLOR);

        int segs = 10;
        float[] cx2 = {x+w-r, x+r,   x+r,   x+w-r, x+w-r};
        float[] cy2 = {y+r,   y+r,   y+h-r, y+h-r, y+r};
        float[] sa  = {270f,  180f,  90f,   0f,    270f};

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j <= segs; j++) {
                double ang = Math.toRadians(sa[i] + j * 90f / segs);
                buf.vertex(m4,
                    (float)(cx2[i]+Math.cos(ang)*r),
                    (float)(cy2[i]+Math.sin(ang)*r), 0).color(rv,g,b,a);
            }
        }
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  MENÜ
    // ══════════════════════════════════════════════════════════
    public class HitXMenu extends Screen {

        private static final String[] TABS = {
            "AimAssist", "TriggerBot", "Hitboxes",
            "NightVision", "HitColor", "Speed",
            "ESP", "Misc", "Keybinds"
        };

        // Genişletilmiş panel
        private static final int PW = 480, PH = 360;

        private String tab     = "AimAssist";
        private int    bind    = -1;
        private int    dragSlot= -1, dCX, dCW;

        // Animasyon
        private float  animTick = 0f;

        protected HitXMenu() { super(Text.literal("HitX")); }

        @Override
        public void tick() {
            super.tick();
            animTick += 0.05f;
        }

        // ─────────────────────────────────────────────────────
        //  RENDER
        // ─────────────────────────────────────────────────────
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            MatrixStack ms = ctx.getMatrices();
            int ox = ox(), oy = oy();

            // ── Arkaplan Bulanıklık Efekti (koyu overlay) ──
            ctx.fill(0, 0, width, height, 0x88000000);

            // ── Ana Panel — Yuvarlak ──
            drawRoundedRect(ms, ox, oy, PW, PH, 8f, 0xF0111118);
            drawRoundedOutline(ms, ox, oy, PW, PH, 8f, 0xFF6600DD);

            // ── Başlık Bölgesi ──
            drawRoundedRect(ms, ox, oy, PW, 28, 8f, 0xFF1A0030);
            // Alt kısmı düzelt (yarı yuvarlak üst, düz alt)
            ctx.fill(ox, oy+14, ox+PW, oy+28, 0xFF1A0030);

            // Animasyonlu başlık rengi
            float pulse = (float)(Math.sin(animTick) * 0.5 + 0.5);
            int titleR = (int)(180 + pulse * 75);
            int titleG = (int)(0   + pulse * 20);
            int titleB = (int)(220 + pulse * 35);
            int titleColor = 0xFF000000 | (titleR << 16) | (titleG << 8) | titleB;

            ctx.drawCenteredTextWithShadow(textRenderer,
                "§lHITX  §8│  §7Kontrol Paneli", ox+PW/2, oy+10, titleColor);

            // ── Versiyon ──
            ctx.drawTextWithShadow(textRenderer, "§8v2.0", ox+4, oy+10, 0xFF333333);

            // ── Aktif modül sayısı ──
            int active = countActive();
            ctx.drawTextWithShadow(textRenderer,
                "§7" + active + " §8aktif", ox+PW-40, oy+10, 0xFF888888);

            // ── Sol: Sekme Listesi ──
            drawRoundedRect(ms, ox+2, oy+30, 116, PH-34, 4f, 0xFF0D0D0D);
            int ty = oy + 36;
            for (String t : TABS) {
                boolean sel = t.equals(tab);
                boolean hov = hov(mx, my, ox+6, ty, 108, 22);
                float tabR = sel ? 6f : (hov ? 4f : 3f);
                int tabBg  = sel ? 0xFF3A0080 : (hov ? 0xFF1E1E2A : 0xFF141420);
                drawRoundedRect(ms, ox+6, ty, 108, 22, tabR, tabBg);
                if (sel) {
                    // Sol kenar parlama çizgisi
                    drawRoundedRect(ms, ox+6, ty, 3, 22, 2f, 0xFFBB00FF);
                    // Sağ parlama
                    drawRoundedRect(ms, ox+111, ty, 3, 22, 2f, 0x44BB00FF);
                }
                int tColor = sel ? 0xFFEE99FF : (hov ? 0xFFCCCCCC : 0xFF666677);
                ctx.drawTextWithShadow(textRenderer, t, ox+13, ty+7, tColor);
                ty += 26;
            }

            // ── Sağ: İçerik Alanı ──
            int cx=cx(), cy=cy(), cw=cw();
            drawRoundedRect(ms, cx-4, oy+30, cw+8, PH-34, 4f, 0xFF0F0F16);

            // Sekme başlığı
            drawRoundedRect(ms, cx-4, oy+30, cw+8, 24, 4f, 0xFF160025);
            ctx.drawTextWithShadow(textRenderer, "§d§l" + tab, cx, oy+37, 0xFFDD88FF);

            // İçerik
            switch (tab) {
                case "Hitboxes"    -> tHitboxes   (ctx, ms, cfg, cx, cy, cw, mx, my);
                case "AimAssist"   -> tAimAssist  (ctx, ms, cfg, cx, cy, cw, mx, my);
                case "TriggerBot"  -> tTriggerBot (ctx, ms, cfg, cx, cy, cw, mx, my);
                case "NightVision" -> tNightVision(ctx, ms, cx, cy, cw, mx, my);
                case "HitColor"    -> tHitColor   (ctx, ms, cfg, cx, cy, cw, mx, my);
                case "Speed"       -> tSpeed      (ctx, ms, cx, cy, cw, mx, my);
                case "ESP"         -> tESP        (ctx, ms, cx, cy, cw, mx, my);
                case "Misc"        -> tMisc       (ctx, ms, cx, cy, cw, mx, my);
                case "Keybinds"    -> tKeybinds   (ctx, ms, cx, cy, cw, mx, my);
            }

            super.render(ctx, mx, my, delta);
        }

        private int countActive() {
            int n = 0;
            if (hitBoxActive) n++; if (triggerBotActive) n++; if (aimAssistActive) n++;
            if (nightVisionActive) n++; if (speedActive) n++; if (sprintActive) n++;
            if (antiKbActive) n++; if (espActive) n++; if (noFallActive) n++;
            if (fullBrightActive) n++;
            return n;
        }

        // ─────────────────────────────────────────────────────
        //  SEKMELER
        // ─────────────────────────────────────────────────────
        private void tHitboxes(DrawContext ctx, MatrixStack ms, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy,     cw, "Hitboxes",           hitBoxActive, mx, my);
            lbl(ctx, cx, cy+30, "Genişlik (XZ):  §e"+f2(cfg.xzExpand));
            sld(ctx, ms, cx, cy+42, cw, (cfg.xzExpand-0.5f)/4.5f, 0);
            lbl(ctx, cx, cy+60, "Yükseklik (Y):  §e"+f2(cfg.yExpand));
            sld(ctx, ms, cx, cy+72, cw, (cfg.yExpand-0.5f)/3.5f, 1);
            lbl(ctx, cx, cy+90, "Y Offset:        §e"+f2(cfg.yOffset));
            sld(ctx, ms, cx, cy+102, cw, (cfg.yOffset+1f)/2f, 2);
        }

        private void tAimAssist(DrawContext ctx, MatrixStack ms, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy,      cw, "AimAssist",          aimAssistActive,  mx, my);
            tog(ctx, ms, cx, cy+28,   cw, "Oto Vurma",          aimAutoAttack,    mx, my);
            tog(ctx, ms, cx, cy+56,   cw, "Sarsılma (Recoil)",  aimRecoil,        mx, my);
            tog(ctx, ms, cx, cy+84,   cw, "Elytra Menzili",     aimElytra,        mx, my);
            tog(ctx, ms, cx, cy+112,  cw, "Sadece Oyuncular",   aimOnlyPlayers,   mx, my);

            lbl(ctx, cx, cy+142, "Menzil:           §e"+f1(aimRange)+" blok");
            sld(ctx, ms, cx, cy+154, cw, (aimRange-1f)/9f, 10);
            lbl(ctx, cx, cy+170, "Smooth Hız:       §e"+f2(aimSpeed));
            sld(ctx, ms, cx, cy+182, cw, (aimSpeed-0.01f)/0.49f, 11);
            lbl(ctx, cx, cy+198, "FOV Limiti:       §e"+f1(aimFov)+"°");
            sld(ctx, ms, cx, cy+210, cw, aimFov/180f, 12);
            lbl(ctx, cx, cy+226, "Sarsılma Şiddeti: §e"+f2(aimRecoilStr));
            sld(ctx, ms, cx, cy+238, cw, aimRecoilStr/2f, 13);
        }

        private void tTriggerBot(DrawContext ctx, MatrixStack ms, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy,     cw, "TriggerBot", triggerBotActive, mx, my);
            lbl(ctx, cx, cy+30, "Gecikme:  §e"+triggerDelay+" ms");
            sld(ctx, ms, cx, cy+42, cw, triggerDelay/500f, 20);
            lbl(ctx, cx, cy+62, triggerBotActive ? "§aNişandaki düşmana otomatik vurur" : "§7Kapalı");
        }

        private void tNightVision(DrawContext ctx, MatrixStack ms, int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy,    cw, "Gece Görüşü", nightVisionActive, mx, my);
            lbl(ctx, cx, cy+32, nightVisionActive
                    ? "§aAktif — ekran tam aydınlık görünür"
                    : "§7Kapalı — normal görüş");
        }

        private void tHitColor(DrawContext ctx, MatrixStack ms, HitXConfig cfg, int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy, cw, "HitColor", cfg.hitColorActive, mx, my);
            int pc = (cfg.hcAlpha<<24)|(cfg.hcRed<<16)|(cfg.hcGreen<<8)|cfg.hcBlue;
            // Önizleme kutusu
            drawRoundedRect(ms, cx+cw-32, cy+3, 28, 18, 4f, 0xFF000000);
            drawRoundedRect(ms, cx+cw-31, cy+4, 26, 16, 3f, pc);
            lbl(ctx, cx, cy+30,  "§cKırmızı:  §e"+cfg.hcRed);
            sld(ctx, ms, cx, cy+42,  cw, cfg.hcRed/255f,   30);
            lbl(ctx, cx, cy+58,  "§aYeşil:    §e"+cfg.hcGreen);
            sld(ctx, ms, cx, cy+70,  cw, cfg.hcGreen/255f, 31);
            lbl(ctx, cx, cy+86,  "§bMavi:     §e"+cfg.hcBlue);
            sld(ctx, ms, cx, cy+98,  cw, cfg.hcBlue/255f,  32);
            lbl(ctx, cx, cy+114, "§7Alpha:    §e"+cfg.hcAlpha);
            sld(ctx, ms, cx, cy+126, cw, cfg.hcAlpha/255f, 33);
        }

        private void tSpeed(DrawContext ctx, MatrixStack ms, int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy,     cw, "Speed",       speedActive,  mx, my);
            tog(ctx, ms, cx, cy+28,  cw, "AutoSprint",  sprintActive, mx, my);
            tog(ctx, ms, cx, cy+56,  cw, "NoFall",      noFallActive, mx, my);
            lbl(ctx, cx, cy+88, "Hız Çarpanı: §e"+f2(speedMultiplier)+"x");
            sld(ctx, ms, cx, cy+100, cw, (speedMultiplier-1f)/3f, 40);
            lbl(ctx, cx, cy+120, "§8[V] ile hızlı aç/kapa");
        }

        private void tESP(DrawContext ctx, MatrixStack ms, int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy,    cw, "ESP",          espActive,   mx, my);
            tog(ctx, ms, cx, cy+28, cw, "Oyuncular",   espPlayers,  mx, my);
            tog(ctx, ms, cx, cy+56, cw, "Moblar",      espMobs,     mx, my);
            lbl(ctx, cx, cy+88, "§cKırmızı:  §e"+espColorR);
            sld(ctx, ms, cx, cy+100, cw, espColorR/255f, 50);
            lbl(ctx, cx, cy+116, "§aYeşil:    §e"+espColorG);
            sld(ctx, ms, cx, cy+128, cw, espColorG/255f, 51);
            lbl(ctx, cx, cy+144, "§bMavi:     §e"+espColorB);
            sld(ctx, ms, cx, cy+156, cw, espColorB/255f, 52);
            // Önizleme rengi
            int previewColor = 0xFF000000|(espColorR<<16)|(espColorG<<8)|espColorB;
            drawRoundedRect(ms, cx+cw-32, cy+92, 28, 18, 4f, 0xFF000000);
            drawRoundedRect(ms, cx+cw-31, cy+93, 26, 16, 3f, previewColor);
            lbl(ctx, cx, cy+180, "§8[Z] ile hızlı aç/kapa");
        }

        private void tMisc(DrawContext ctx, MatrixStack ms, int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy,    cw, "AntiKnockback",  antiKbActive,   mx, my);
            tog(ctx, ms, cx, cy+28, cw, "FullBright",     fullBrightActive, mx, my);
            lbl(ctx, cx, cy+58, "AntiKB Gücü: §e"+f2(antiKbStrength*100)+"%");
            sld(ctx, ms, cx, cy+70, cw, antiKbStrength, 60);
            lbl(ctx, cx, cy+100, "§8AntiKB: vuruş geri itmesini iptal eder");
            lbl(ctx, cx, cy+116, "§8FullBright: Gece Görüşü efektini gizler");
        }

        private void tKeybinds(DrawContext ctx, MatrixStack ms, int cx, int cy, int cw, int mx, int my) {
            lbl(ctx, cx, cy, "§8Satıra tıkla → yeni tuşa bas");
            kb(ctx, ms, cx, cy+16,  cw, "Hitboxes",    keyHitbox,      bind==0, mx, my);
            kb(ctx, ms, cx, cy+44,  cw, "AimAssist",   keyAimAssist,   bind==1, mx, my);
            kb(ctx, ms, cx, cy+72,  cw, "TriggerBot",  keyTriggerBot,  bind==2, mx, my);
            kb(ctx, ms, cx, cy+100, cw, "NightVision", keyNightVision, bind==3, mx, my);
            kb(ctx, ms, cx, cy+128, cw, "Speed",       keySpeed,       bind==4, mx, my);
            kb(ctx, ms, cx, cy+156, cw, "ESP",         keyEsp,         bind==5, mx, my);
        }

        // ─────────────────────────────────────────────────────
        //  ÇİZİM YARDIMCILARI — YUVARLAK
        // ─────────────────────────────────────────────────────

        // Toggle butonu — yuvarlak, parlama efektli
        private void tog(DrawContext ctx, MatrixStack ms, int x, int y, int w, String name, boolean on, int mx, int my) {
            boolean hov = hov(mx, my, x, y, w, 22);
            int bg = on
                ? (hov ? 0xFF004455 : 0xFF003344)
                : (hov ? 0xFF1E1E2E : 0xFF16161E);
            drawRoundedRect(ms, x, y, w, 22, 5f, bg);
            // Sol kenar rengi
            int edgeColor = on ? 0xFF00DDFF : 0xFF333344;
            drawRoundedRect(ms, x, y, 3, 22, 3f, edgeColor);
            // İsim
            ctx.drawTextWithShadow(textRenderer, name, x+10, y+7,
                on ? 0xFF88EEFF : 0xFF888899);
            // Durum indikatörü (yuvarlak nokta)
            String status = on ? "§a●" : "§c○";
            ctx.drawTextWithShadow(textRenderer, status, x+w-20, y+7, 0xFFFFFFFF);
        }

        // Slider — yuvarlak, gradyanli
        private void sld(DrawContext ctx, MatrixStack ms, int x, int y, int w, float p, int sid) {
            p = MathHelper.clamp(p, 0f, 1f);
            // Track arkaplanı
            drawRoundedRect(ms, x, y, w, 10, 5f, 0xFF0A0A12);
            // Dolgu
            int fw = (int)((w-2)*p);
            if (fw > 2) {
                drawRoundedRect(ms, x+1, y+1, fw, 8, 4f, 0xFF4400AA);
                // Parlama (son 6px)
                if (fw > 8)
                    drawRoundedRect(ms, x+1+fw-6, y+1, 6, 8, 4f, 0xFF9944FF);
            }
            // Knob
            int kx = x + 1 + (int)((w-8)*p);
            drawRoundedRect(ms, kx, y-2, 6, 14, 3f, 0xFFFFFFFF);
            drawRoundedRect(ms, kx+1, y-1, 4, 12, 2f, 0xFFCCAAFF);
        }

        // Label
        private void lbl(DrawContext ctx, int x, int y, String t) {
            ctx.drawTextWithShadow(textRenderer, t, x, y, 0xFFAAAAAA);
        }

        // Keybind satırı
        private void kb(DrawContext ctx, MatrixStack ms, int x, int y, int w, String name, int key, boolean wait, int mx, int my) {
            boolean hov = hov(mx, my, x, y, w, 22);
            int bg = wait ? 0xFF1A0033 : (hov ? 0xFF1C1C28 : 0xFF141420);
            drawRoundedRect(ms, x, y, w, 22, 5f, bg);
            int edge = wait ? 0xFFFF00CC : 0xFF4400AA;
            drawRoundedRect(ms, x, y, 3, 22, 3f, edge);
            ctx.drawTextWithShadow(textRenderer, name, x+10, y+7, 0xFFBBBBCC);
            String keyStr = wait ? "§e[ tuşa bas... ]" : "§d[ " + kn(key) + " ]";
            ctx.drawTextWithShadow(textRenderer, keyStr, x+w-90, y+7, 0xFFFFFFFF);
        }

        private boolean hov(int mx, int my, int x, int y, int w, int h) {
            return mx>=x && mx<=x+w && my>=y && my<=y+h;
        }

        private boolean hovD(double mx, double my, double x, double y, double w, double h) {
            return mx>=x && mx<=x+w && my>=y && my<=y+h;
        }

        private int ox() { return width/2  - PW/2; }
        private int oy() { return height/2 - PH/2; }
        private int cx() { return ox() + 124; }
        private int cy() { return oy() + 58;  }
        private int cw() { return PW  - 132;  }

        // ─────────────────────────────────────────────────────
        //  MOUSE EVENTS
        // ─────────────────────────────────────────────────────
        @Override
        public boolean mouseClicked(double mx, double my, int btn) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int ox=ox(), oy=oy(), cx=cx(), cy=cy(), cw=cw();

            // Sekme seçimi
            int ty = oy + 36;
            for (String t : TABS) {
                if (hovD(mx, my, ox+6, ty, 108, 22)) {
                    tab=t; bind=-1; dragSlot=-1; return true;
                }
                ty += 26;
            }

            switch (tab) {
                case "Hitboxes" -> {
                    if (hovD(mx,my,cx,cy,cw,22))         { hitBoxActive  = !hitBoxActive;  return true; }
                    if (cs(mx,my,cx,cy+42, cw,0))  { cfg.xzExpand = 0.5f + sv(mx,cx,cw)*4.5f; sc(); return true; }
                    if (cs(mx,my,cx,cy+72, cw,1))  { cfg.yExpand  = 0.5f + sv(mx,cx,cw)*3.5f; sc(); return true; }
                    if (cs(mx,my,cx,cy+102,cw,2))  { cfg.yOffset  = -1f  + sv(mx,cx,cw)*2f;   sc(); return true; }
                }
                case "AimAssist" -> {
                    if (hovD(mx,my,cx,cy,    cw,22)) { aimAssistActive=!aimAssistActive; locked=null; bar(client, aimAssistActive?"§aAimAssist §7Açık":"§cAimAssist §7Kapalı"); return true; }
                    if (hovD(mx,my,cx,cy+28, cw,22)) { aimAutoAttack  =!aimAutoAttack;   return true; }
                    if (hovD(mx,my,cx,cy+56, cw,22)) { aimRecoil      =!aimRecoil;       return true; }
                    if (hovD(mx,my,cx,cy+84, cw,22)) { aimElytra      =!aimElytra;       return true; }
                    if (hovD(mx,my,cx,cy+112,cw,22)) { aimOnlyPlayers =!aimOnlyPlayers;  return true; }
                    if (cs(mx,my,cx,cy+154,cw,10)) { aimRange   = 1f   + sv(mx,cx,cw)*9f;    return true; }
                    if (cs(mx,my,cx,cy+182,cw,11)) { aimSpeed   = 0.01f+ sv(mx,cx,cw)*0.49f; return true; }
                    if (cs(mx,my,cx,cy+210,cw,12)) { aimFov     = sv(mx,cx,cw)*180f;          return true; }
                    if (cs(mx,my,cx,cy+238,cw,13)) { aimRecoilStr = sv(mx,cx,cw)*2f;          return true; }
                }
                case "TriggerBot" -> {
                    if (hovD(mx,my,cx,cy,cw,22)) { triggerBotActive=!triggerBotActive; bar(client,triggerBotActive?"§aTriggerBot §7Açık":"§cTriggerBot §7Kapalı"); return true; }
                    if (cs(mx,my,cx,cy+42,cw,20)) { triggerDelay=(int)(sv(mx,cx,cw)*500); return true; }
                }
                case "NightVision" -> {
                    if (hovD(mx,my,cx,cy,cw,22)) {
                        nightVisionActive=!nightVisionActive;
                        if (!nightVisionActive) client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);
                        bar(client,nightVisionActive?"§aGece Görüşü §7Açık":"§cGece Görüşü §7Kapalı");
                        return true;
                    }
                }
                case "HitColor" -> {
                    if (hovD(mx,my,cx,cy,cw,22)) { cfg.hitColorActive=!cfg.hitColorActive; sc(); OverlayReloadListener.callEvent(); return true; }
                    if (cs(mx,my,cx,cy+42, cw,30)) { cfg.hcRed   =(int)(sv(mx,cx,cw)*255); sc(); OverlayReloadListener.callEvent(); return true; }
                    if (cs(mx,my,cx,cy+70, cw,31)) { cfg.hcGreen =(int)(sv(mx,cx,cw)*255); sc(); OverlayReloadListener.callEvent(); return true; }
                    if (cs(mx,my,cx,cy+98, cw,32)) { cfg.hcBlue  =(int)(sv(mx,cx,cw)*255); sc(); OverlayReloadListener.callEvent(); return true; }
                    if (cs(mx,my,cx,cy+126,cw,33)) { cfg.hcAlpha =(int)(sv(mx,cx,cw)*255); sc(); OverlayReloadListener.callEvent(); return true; }
                }
                case "Speed" -> {
                    if (hovD(mx,my,cx,cy,   cw,22)) { speedActive  =!speedActive;   bar(client,speedActive?"§aSpeed §7Açık":"§cSpeed §7Kapalı");   return true; }
                    if (hovD(mx,my,cx,cy+28,cw,22)) { sprintActive =!sprintActive;  return true; }
                    if (hovD(mx,my,cx,cy+56,cw,22)) { noFallActive =!noFallActive;  return true; }
                    if (cs(mx,my,cx,cy+100,cw,40))  { speedMultiplier = 1f + sv(mx,cx,cw)*3f; return true; }
                }
                case "ESP" -> {
                    if (hovD(mx,my,cx,cy,   cw,22)) { espActive  =!espActive;   bar(client,espActive?"§aESP §7Açık":"§cESP §7Kapalı"); return true; }
                    if (hovD(mx,my,cx,cy+28,cw,22)) { espPlayers =!espPlayers;  return true; }
                    if (hovD(mx,my,cx,cy+56,cw,22)) { espMobs    =!espMobs;     return true; }
                    if (cs(mx,my,cx,cy+100,cw,50))  { espColorR=(int)(sv(mx,cx,cw)*255); return true; }
                    if (cs(mx,my,cx,cy+128,cw,51))  { espColorG=(int)(sv(mx,cx,cw)*255); return true; }
                    if (cs(mx,my,cx,cy+156,cw,52))  { espColorB=(int)(sv(mx,cx,cw)*255); return true; }
                }
                case "Misc" -> {
                    if (hovD(mx,my,cx,cy,   cw,22)) { antiKbActive    =!antiKbActive;    return true; }
                    if (hovD(mx,my,cx,cy+28,cw,22)) { fullBrightActive=!fullBrightActive; return true; }
                    if (cs(mx,my,cx,cy+70,cw,60))   { antiKbStrength = sv(mx,cx,cw);     return true; }
                }
                case "Keybinds" -> {
                    if (hovD(mx,my,cx,cy+16, cw,22)) { bind=0; return true; }
                    if (hovD(mx,my,cx,cy+44, cw,22)) { bind=1; return true; }
                    if (hovD(mx,my,cx,cy+72, cw,22)) { bind=2; return true; }
                    if (hovD(mx,my,cx,cy+100,cw,22)) { bind=3; return true; }
                    if (hovD(mx,my,cx,cy+128,cw,22)) { bind=4; return true; }
                    if (hovD(mx,my,cx,cy+156,cw,22)) { bind=5; return true; }
                }
            }
            return super.mouseClicked(mx, my, btn);
        }

        @Override
        public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
            if (dragSlot == -1) return super.mouseDragged(mx, my, btn, dx, dy);
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            float v = sv(mx, dCX, dCW);
            switch (dragSlot) {
                case 0  -> { cfg.xzExpand = 0.5f + v*4.5f; sc(); }
                case 1  -> { cfg.yExpand  = 0.5f + v*3.5f; sc(); }
                case 2  -> { cfg.yOffset  = -1f  + v*2f;   sc(); }
                case 10 -> aimRange    = 1f    + v*9f;
                case 11 -> aimSpeed    = 0.01f + v*0.49f;
                case 12 -> aimFov      = v*180f;
                case 13 -> aimRecoilStr= v*2f;
                case 20 -> triggerDelay= (int)(v*500);
                case 30 -> { cfg.hcRed  =(int)(v*255); sc(); OverlayReloadListener.callEvent(); }
                case 31 -> { cfg.hcGreen=(int)(v*255); sc(); OverlayReloadListener.callEvent(); }
                case 32 -> { cfg.hcBlue =(int)(v*255); sc(); OverlayReloadListener.callEvent(); }
                case 33 -> { cfg.hcAlpha=(int)(v*255); sc(); OverlayReloadListener.callEvent(); }
                case 40 -> speedMultiplier = 1f + v*3f;
                case 50 -> espColorR = (int)(v*255);
                case 51 -> espColorG = (int)(v*255);
                case 52 -> espColorB = (int)(v*255);
                case 60 -> antiKbStrength = v;
            }
            return true;
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (bind >= 0) {
                switch (bind) {
                    case 0 -> keyHitbox      = keyCode;
                    case 1 -> keyAimAssist   = keyCode;
                    case 2 -> keyTriggerBot  = keyCode;
                    case 3 -> keyNightVision = keyCode;
                    case 4 -> keySpeed       = keyCode;
                    case 5 -> keyEsp         = keyCode;
                }
                bind = -1;
                return true;
            }
            return super.keyPressed(keyCode, scanCode, modifiers);
        }

        @Override public boolean shouldPause() { return false; }

        // ── Yardımcılar ──
        private boolean cs(double mx, double my, double cx, double sy, double cw, int sid) {
            if (hovD(mx, my, cx, sy-3, cw, 16.0)) {
                dragSlot=sid; dCX=(int)cx; dCW=(int)cw; return true;
            }
            return false;
        }
        private float sv(double mx, int cx, int cw) {
            return MathHelper.clamp((float)((mx-cx)/cw), 0f, 1f);
        }
        private void sc() { AutoConfig.getConfigHolder(HitXConfig.class).save(); }
        private String kn(int k) {
            String n = GLFW.glfwGetKeyName(k, 0);
            return n!=null ? n.toUpperCase() : "KEY_"+k;
        }
        private String f1(float v) { return String.format("%.1f", v); }
        private String f2(float v) { return String.format("%.2f", v); }
    }

    // ── Yardımcılar ────────────────────────────────────────────
    private void iconBtn(Screen s, ItemStack i, String t, int x, int y, int w, int h, ButtonWidget.PressAction p) {}
    private boolean isArmor(ItemStack s) {
        return s.getItem() instanceof net.minecraft.item.ArmorItem;
    }
}
