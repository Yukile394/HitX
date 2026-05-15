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
import net.minecraft.item.ShieldItem;
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

    // ── AuraTarget (ThunderHack tarzı) ──────────────────────
    public static boolean auraActive        = false;
    public static float   auraRange         = 3.5f;
    public static float   auraSpeed         = 0.12f;
    public static boolean auraOnlyPlayers   = false;
    public static boolean auraAutoAttack    = true;
    // ElytraTarget
    public static boolean elytraTargetActive = false;
    public static float   elytraTargetRange  = 7.0f;

    // ── AimAssist Ayarları ───────────────────────────────────
    public static float   aimRange          = 4.5f;
    public static float   aimSpeed          = 0.08f;
    public static float   aimFov            = 90f;
    public static boolean aimAutoAttack     = false;
    public static boolean aimRecoil         = false;
    public static float   aimRecoilStr      = 0.25f;
    public static boolean aimElytra         = true;
    public static float   aimElytraRange    = 6.0f;
    public static boolean aimOnlyPlayers    = false;

    // ── TriggerBot Ayarları ─────────────────────────────────
    public static int     triggerDelay      = 50;
    // ── Trigger Engelleme Seçenekleri ───────────────────────
    public static boolean triggerBlockOnShield   = true;   // Hedef kalkan tutuyorsa vurma
    public static boolean triggerBlockOnGap      = true;   // Hedef gapple/yemek yiyorsa vurma
    public static boolean triggerBlockSelfShield = true;   // Kendisi kalkan tutarken vurma
    public static boolean triggerBlockSelfGap    = true;   // Kendisi gapple yerken vurma

    // ── Speed Ayarları ──────────────────────────────────────
    public static float   speedMultiplier   = 1.35f;

    // ── ESP Ayarları ────────────────────────────────────────
    public static boolean espPlayers        = true;
    public static boolean espMobs           = false;
    public static int     espColorR         = 255;
    public static int     espColorG         = 60;
    public static int     espColorB         = 60;

    // ── AntiKB Ayarları ─────────────────────────────────────
    public static float   antiKbStrength    = 1.0f;

    // ── Keybindlar ──────────────────────────────────────────
    public static int keyHitbox       = GLFW.GLFW_KEY_H;
    public static int keyAimAssist    = GLFW.GLFW_KEY_J;
    public static int keyTriggerBot   = GLFW.GLFW_KEY_K;
    public static int keyNightVision  = GLFW.GLFW_KEY_N;
    public static int keySpeed        = GLFW.GLFW_KEY_V;
    public static int keyEsp          = GLFW.GLFW_KEY_Z;
    public static int keyAura         = GLFW.GLFW_KEY_G;

    // ── İç Durum ─────────────────────────────────────────────
    private boolean mLast=false,kHLast=false,kALast=false,kTLast=false;
    private boolean kNLast=false,kSLast=false,kELast=false,kGLast=false;
    private long    lastAttack = 0L;
    private long    nvTick     = 0L;
    private LivingEntity locked = null;
    private LivingEntity auraLocked = null;

    // ── AimAssist Smooth State ───────────────────────────────
    private float   smoothYawAcc   = 0f;
    private float   smoothPitchAcc = 0f;
    private final float[] yawBuf   = new float[6];
    private final float[] pitchBuf = new float[6];
    private int     bufIdx         = 0;

    // ── AuraTarget Smooth State ──────────────────────────────
    private float   auraYawAcc    = 0f;
    private float   auraPitchAcc  = 0f;
    private final float[] aYawBuf   = new float[4];
    private final float[] aPitchBuf = new float[4];
    private int     aBufIdx        = 0;

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
            if (client.getDebugHud().shouldShowDebugHud()) return;
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
                boolean kG = GLFW.glfwGetKey(handle, keyAura)        == GLFW.GLFW_PRESS;

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
                if (kG && !kGLast) {
                    auraActive = !auraActive;
                    auraLocked = null;
                    bar(client, auraActive ? "§aAura §7Açık" : "§cAura §7Kapalı");
                }
                kHLast=kH; kALast=kA; kTLast=kT; kNLast=kN; kSLast=kS; kELast=kE; kGLast=kG;
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
                    client.player.setVelocity(vel.x * speedMultiplier, vel.y, vel.z * speedMultiplier);
                }
            }

            // ── AutoSprint ──
            if (sprintActive && !client.player.isSneaking() && !client.player.isTouchingWater())
                client.player.setSprinting(true);

            // ── NoFall ──
            if (noFallActive && client.player.fallDistance > 2.0f)
                client.player.fallDistance = 0f;

            handleCombat(client);
            handleAura(client);
        });
    }

    // ══════════════════════════════════════════════════════════
    //  ESP — DÜZELTILMIŞ TAM HEDEF KİLİT + YUVARLAK KUTULAR
    // ══════════════════════════════════════════════════════════
    private void renderESP(MinecraftClient client, DrawContext ctx) {
        int sw = client.getWindow().getScaledWidth();
        int sh = client.getWindow().getScaledHeight();
        double px = client.gameRenderer.getCamera().getPos().x;
        double py = client.gameRenderer.getCamera().getPos().y;
        double pz = client.gameRenderer.getCamera().getPos().z;

        // Kamera matrisi — yaw/pitch doğru hesap
        float yawRad   = (float) Math.toRadians(client.gameRenderer.getCamera().getYaw()   + 180f);
        float pitchRad = (float) Math.toRadians(client.gameRenderer.getCamera().getPitch());

        double sinY = Math.sin(yawRad),  cosY = Math.cos(yawRad);
        double sinP = Math.sin(pitchRad), cosP = Math.cos(pitchRad);
        double fov  = Math.toRadians(client.options.getFov().getValue());
        double fovF = (sw * 0.5) / Math.tan(fov * 0.5);

        for (Entity e : client.world.getEntities()) {
            if (e == client.player || !e.isAlive()) continue;
            boolean isPlayer = e instanceof PlayerEntity;
            boolean isMob    = e instanceof LivingEntity && !isPlayer;
            if (isPlayer && !espPlayers) continue;
            if (isMob    && !espMobs)    continue;

            Box box = e.getBoundingBox().expand(0.05);

            // 8 köşeyi doğru kamera uzayına taşı
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
            double maxX2d =-Double.MAX_VALUE, maxY2d =-Double.MAX_VALUE;
            boolean anyVisible = false;

            for (int i = 0; i < 8; i++) {
                double rx = corners[i*3  ] - px;
                double ry = corners[i*3+1] - py;
                double rz = corners[i*3+2] - pz;

                // Kamera uzayı döndürmesi (yaw önce, pitch sonra)
                double x1 = rx * cosY - rz * sinY;
                double z2 = rx * sinY + rz * cosY;
                double y1 = ry * cosP - z2 * sinP;
                double z1 = ry * sinP + z2 * cosP;

                if (z1 < 0.1) continue; // arkada veya çok yakın → atla

                double sx = sw * 0.5 + (x1 / z1) * fovF;
                double sy = sh * 0.5 - (y1 / z1) * fovF;

                // Ekran sınırlarını biraz genişlet (kısmen görünür entiteler için)
                if (sx < -sw || sx > sw*2 || sy < -sh || sy > sh*2) continue;

                anyVisible = true;
                if (sx < minX2d) minX2d = sx;
                if (sy < minY2d) minY2d = sy;
                if (sx > maxX2d) maxX2d = sx;
                if (sy > maxY2d) maxY2d = sy;
            }
            if (!anyVisible) continue;

            // Minimum kutu boyutu — çok küçük görünmesin
            if (maxX2d - minX2d < 4) { double mid = (minX2d+maxX2d)/2; minX2d=mid-2; maxX2d=mid+2; }
            if (maxY2d - minY2d < 4) { double mid = (minY2d+maxY2d)/2; minY2d=mid-2; maxY2d=mid+2; }

            int ex = (int)minX2d, ey = (int)minY2d;
            int ew = (int)(maxX2d-minX2d), eh = (int)(maxY2d-minY2d);

            int col = isPlayer
                    ? (0xCC000000|(espColorR<<16)|(espColorG<<8)|espColorB)
                    : 0xCC44FF44;

            // ── TAM YUVARLAK ESP KUTUSU (kare yok, tam oval köşe) ──
            drawRoundedESPBox(ctx.getMatrices(), ex, ey, ew, eh, col);

            // İsim + kalp etiketi
            String label = e.getName().getString();
            if (e instanceof LivingEntity le) {
                int hp = (int) le.getHealth();
                label += "  §c" + hp + "❤";
            }
            // Uzaklık
            double dist = client.player.distanceTo(e);
            label += "  §7" + (int)dist + "m";

            int lx = ex + ew/2 - client.textRenderer.getWidth(label)/2;
            ctx.drawTextWithShadow(client.textRenderer, label, lx, ey - 11, col | 0xFF000000);
        }
    }

    /**
     * TAM yuvarlak köşeli ESP kutusu — Minecraft karesi yok, köşe-only da yok.
     * Tam outline + ince iç dolgu.
     */
    private void drawRoundedESPBox(MatrixStack ms, int x, int y, int w, int h, int color) {
        float r = Math.min(5f, Math.min(w, h) * 0.15f);

        // İç şeffaf dolgu
        int fillCol = (color & 0x00FFFFFF) | 0x18000000;
        drawRoundedRect(ms, x, y, w, h, r, fillCol);

        // Dış outline — tam yuvarlak
        drawRoundedOutline(ms, x, y, w, h, r, color);

        // Üst/Alt köşe parlama (gradient vurgu)
        int glowCol = (color & 0x00FFFFFF) | 0x55000000;
        drawRoundedOutline(ms, x-1, y-1, w+2, h+2, r+1, glowCol);
    }

    // ══════════════════════════════════════════════════════════
    //  COMBAT — TriggerBot + AimAssist
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
                double score = ang * 0.6 + d * 0.4;
                if (score < best) { best = score; locked = le; }
            }
        }

        if (aimAssistActive && locked != null) smoothAim(client, locked);

        // ── TriggerBot ──
        LivingEntity trigTarget = null;
        if (triggerBotActive &&
                client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit &&
                hit.getEntity() instanceof LivingEntity le && le.isAlive()) {
            trigTarget = le;
        }

        LivingEntity autoTarget = (aimAutoAttack && locked != null) ? locked : null;
        LivingEntity atk = trigTarget != null ? trigTarget : autoTarget;
        if (atk == null) return;

        // ── Engelleme Kontrolleri ──────────────────────────────
        // 1) Hedef kalkan tutuyorsa vurma
        if (triggerBlockOnShield && isBlocking(atk)) return;

        // 2) Hedef gapple/yiyecek kullanıyorsa vurma
        if (triggerBlockOnGap && isEating(atk)) return;

        // 3) Kendisi kalkan tutuyorsa vurma
        if (triggerBlockSelfShield && isBlocking(client.player)) return;

        // 4) Kendisi gapple yerken vurma (saldırı kesilir, reveal etme)
        if (triggerBlockSelfGap && isEating(client.player)) return;

        // ── Saldırı Koşulları ──
        if (client.player.getAttackCooldownProgress(0.5f) < 1.0f) return;
        long now = System.currentTimeMillis();
        if (now - lastAttack < triggerDelay) return;

        client.interactionManager.attackEntity(client.player, atk);
        client.player.swingHand(Hand.MAIN_HAND);
        lastAttack = now;

        if (aimRecoil) client.player.setPitch(client.player.getPitch() - aimRecoilStr);
    }

    // ══════════════════════════════════════════════════════════
    //  AURA / ELYTRA TARGET — ThunderHack Tarzı
    //  Hedefi takip eder, efektli smooth aim, kare yok
    // ══════════════════════════════════════════════════════════
    private void handleAura(MinecraftClient client) {
        boolean elytraMode = elytraTargetActive && client.player.isFallFlying();

        if (!auraActive && !elytraMode) {
            auraLocked = null;
            return;
        }

        float range = elytraMode ? elytraTargetRange : auraRange;

        // Kilit geçerliliği
        if (auraLocked != null && (!auraLocked.isAlive() ||
                client.player.distanceTo(auraLocked) > range + 1.5f))
            auraLocked = null;

        // En iyi hedef seç — açı + mesafe bazlı (ThunderHack mantığı)
        if (auraLocked == null) {
            double best = Double.MAX_VALUE;
            for (Entity e : client.world.getEntities()) {
                if (!(e instanceof LivingEntity le)) continue;
                if (le == client.player || !le.isAlive()) continue;
                if (auraOnlyPlayers && !(le instanceof PlayerEntity)) continue;
                double d = client.player.distanceTo(le);
                if (d > range) continue;
                float ang = angleTo(client, le);
                // Çok uzak açıdaki hedeflere gitme — daha doğal görünüm
                double score = ang * 0.5 + d * 0.5;
                if (score < best) { best = score; auraLocked = le; }
            }
        }

        if (auraLocked == null) return;

        // ── Smooth Aim (ThunderHack tarzı — ani dönme yok) ──
        smoothAuraAim(client, auraLocked);

        // ── Otomatik Vurma ──
        if (auraAutoAttack) {
            // Kalkan/yemek engellemeleri aura için de geçerli
            if (triggerBlockOnShield && isBlocking(auraLocked)) return;
            if (triggerBlockOnGap    && isEating(auraLocked))   return;
            if (triggerBlockSelfShield && isBlocking(client.player)) return;
            if (triggerBlockSelfGap    && isEating(client.player))   return;

            if (client.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
                long now = System.currentTimeMillis();
                if (now - lastAttack >= triggerDelay) {
                    client.interactionManager.attackEntity(client.player, auraLocked);
                    client.player.swingHand(Hand.MAIN_HAND);
                    lastAttack = now;
                }
            }
        }
    }

    /** ThunderHack tarzı smooth aim — buffer + exponential + GCD fix */
    private void smoothAuraAim(MinecraftClient client, LivingEntity target) {
        double vx = target.getX() - target.prevX;
        double vz = target.getZ() - target.prevZ;
        double vy = target.getY() - target.prevY;

        double px = target.getX() + vx * 1.5;
        double pz = target.getZ() + vz * 1.5;
        double py = target.getY() + vy * 0.3
                  + target.getEyeHeight(target.getPose()) * 0.78;

        double dx = px - client.player.getX();
        double dz = pz - client.player.getZ();
        double dy = py - (client.player.getY() + client.player.getEyeHeight(client.player.getPose()));
        double hDist = Math.sqrt(dx*dx + dz*dz);

        float targetYaw   = MathHelper.wrapDegrees(
            (float)Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        float targetPitch = (float)-Math.toDegrees(Math.atan2(dy, hDist));

        float deltaYaw   = MathHelper.wrapDegrees(targetYaw   - client.player.getYaw());
        float deltaPitch = MathHelper.wrapDegrees(targetPitch - client.player.getPitch());

        float dist  = client.player.distanceTo(target);
        float angle = angleTo(client, target);
        float distF = MathHelper.clamp(dist / auraRange, 0.2f, 1.0f);
        float angF  = MathHelper.clamp(angle / 12f, 0.1f, 1.0f);
        float spd   = auraSpeed * distF * angF;

        auraYawAcc   += (deltaYaw   - auraYawAcc)   * spd * 3f;
        auraPitchAcc += (deltaPitch - auraPitchAcc)  * spd * 3f;

        aYawBuf[aBufIdx]   = auraYawAcc;
        aPitchBuf[aBufIdx] = auraPitchAcc;
        aBufIdx = (aBufIdx + 1) % 4;

        float ay = 0f, ap = 0f;
        for (int i = 0; i < 4; i++) { ay += aYawBuf[i]; ap += aPitchBuf[i]; }
        ay /= 4f; ap /= 4f;

        double sens = client.options.getMouseSensitivity().getValue();
        double gcd  = Math.pow(sens * 0.6 + 0.2, 3.0) * 8.0 * 0.15;
        if (gcd < 0.001) gcd = 0.001;

        float sy2 = (float)(Math.round(ay / gcd) * gcd);
        float sp2 = (float)(Math.round(ap / gcd) * gcd);

        client.player.setYaw  (client.player.getYaw()   + sy2);
        client.player.setPitch(MathHelper.clamp(client.player.getPitch() + sp2, -90f, 90f));
    }

    // ══════════════════════════════════════════════════════════
    //  YARDIMCI — Kalkan / Yemek Tespiti
    // ══════════════════════════════════════════════════════════

    /** Entity'nin şu an kalkan (shield) tutup tutmadığını kontrol eder */
    private boolean isBlocking(LivingEntity e) {
        if (e == null) return false;
        // isBlocking() metodu 1.21 API — aktif shield block
        return e.isBlocking();
    }

    /**
     * Entity'nin şu an yiyecek/içecek kullanıp kullanmadığını kontrol eder.
     * Golden Apple (gapple), Enchanted Apple, herhangi bir Food item dahil.
     */
    private boolean isEating(LivingEntity e) {
        if (e == null) return false;
        if (!e.isUsingItem()) return false;
        ItemStack using = e.getActiveItem();
        if (using.isEmpty()) return false;
        net.minecraft.item.Item item = using.getItem();
        // Food component (gapple, bread, vs.) veya bilinen içecekler
        return using.getComponents().contains(net.minecraft.component.DataComponentTypes.FOOD)
            || item == Items.MILK_BUCKET
            || item == Items.POTION
            || item == Items.SPLASH_POTION
            || item == Items.LINGERING_POTION;
    }

    // ══════════════════════════════════════════════════════════
    //  AIM ASSIST SMOOTH (Gelişmiş)
    // ══════════════════════════════════════════════════════════
    private float angleTo(MinecraftClient c, LivingEntity t) {
        Vec3d look = c.player.getRotationVec(1f);
        Vec3d toT  = t.getEyePos().subtract(c.player.getEyePos()).normalize();
        return (float) Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(toT), -1.0, 1.0)));
    }

    private void smoothAim(MinecraftClient client, LivingEntity target) {
        double vx = target.getX() - target.prevX;
        double vy = target.getY() - target.prevY;
        double vz = target.getZ() - target.prevZ;

        double px = target.getX() + vx * 2.0;
        double pz = target.getZ() + vz * 2.0;
        double py = target.getY() + vy * 0.3
                  + target.getEyeHeight(target.getPose()) * 0.82;

        double dx = px - client.player.getX();
        double dz = pz - client.player.getZ();
        double dy = py - (client.player.getY() + client.player.getEyeHeight(client.player.getPose()));
        double hDist = Math.sqrt(dx*dx + dz*dz);

        float targetYaw   = MathHelper.wrapDegrees(
            (float)Math.toDegrees(Math.atan2(dz, dx)) - 90f);
        float targetPitch = (float)-Math.toDegrees(Math.atan2(dy, hDist));

        float deltaYaw   = MathHelper.wrapDegrees(targetYaw   - client.player.getYaw());
        float deltaPitch = MathHelper.wrapDegrees(targetPitch - client.player.getPitch());

        float dist = client.player.distanceTo(target);
        float angle = angleTo(client, target);
        float distFactor  = MathHelper.clamp(dist / aimRange, 0.25f, 1.0f);
        float angleFactor = MathHelper.clamp(angle / 15f, 0.15f, 1.0f);
        float dynSpeed    = aimSpeed * distFactor * angleFactor;

        smoothYawAcc   += (deltaYaw   - smoothYawAcc)   * dynSpeed * 2.5f;
        smoothPitchAcc += (deltaPitch - smoothPitchAcc) * dynSpeed * 2.5f;

        yawBuf[bufIdx]   = smoothYawAcc;
        pitchBuf[bufIdx] = smoothPitchAcc;
        bufIdx = (bufIdx + 1) % 6;

        float avgYaw = 0f, avgPitch = 0f;
        for (int i = 0; i < 6; i++) { avgYaw += yawBuf[i]; avgPitch += pitchBuf[i]; }
        avgYaw /= 6f; avgPitch /= 6f;

        double sens = client.options.getMouseSensitivity().getValue();
        double gcd  = Math.pow(sens * 0.6 + 0.2, 3.0) * 8.0 * 0.15;
        if (gcd < 0.001) gcd = 0.001;

        float stepYaw   = (float)(Math.round(avgYaw   / gcd) * gcd);
        float stepPitch = (float)(Math.round(avgPitch / gcd) * gcd);

        client.player.setYaw  (client.player.getYaw()   + stepYaw);
        client.player.setPitch(MathHelper.clamp(client.player.getPitch() + stepPitch, -90f, 90f));
    }

    private void bar(MinecraftClient c, String m) {
        if (c.player != null)
            c.player.sendMessage(Text.literal("§8[§dHitX§8] §r" + m), true);
    }

    // ══════════════════════════════════════════════════════════
    //  ROUNDED RECT — TAM YUVARLAK (Tessellator tabanlı)
    // ══════════════════════════════════════════════════════════
    public static void drawRoundedRect(MatrixStack ms, float x, float y,
                                       float w, float h, float r, int color) {
        float a  = ((color >> 24) & 0xFF) / 255f;
        float rv = ((color >> 16) & 0xFF) / 255f;
        float g  = ((color >>  8) & 0xFF) / 255f;
        float b  = (color & 0xFF)          / 255f;
        if (a <= 0f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Matrix4f m4 = ms.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();

        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN,
                                       VertexFormats.POSITION_COLOR);
        buf.vertex(m4, x + w / 2f, y + h / 2f, 0f).color(rv, g, b, a);

        int segs = 16; // daha smooth köşe
        float[] cxArr  = {x+w-r, x+r,   x+r,   x+w-r};
        float[] cyArr  = {y+r,   y+r,   y+h-r, y+h-r};
        float[] startA = {270f,  180f,  90f,   0f};

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j <= segs; j++) {
                double ang = Math.toRadians(startA[i] + j * 90.0 / segs);
                buf.vertex(m4,
                    (float)(cxArr[i] + Math.cos(ang) * r),
                    (float)(cyArr[i] + Math.sin(ang) * r),
                    0f).color(rv, g, b, a);
            }
        }
        double closeAng = Math.toRadians(startA[0]);
        buf.vertex(m4,
            (float)(cxArr[0] + Math.cos(closeAng) * r),
            (float)(cyArr[0] + Math.sin(closeAng) * r),
            0f).color(rv, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    public static void drawRoundedOutline(MatrixStack ms, float x, float y,
                                          float w, float h, float r, int color) {
        float a  = ((color >> 24) & 0xFF) / 255f;
        float rv = ((color >> 16) & 0xFF) / 255f;
        float g  = ((color >>  8) & 0xFF) / 255f;
        float b  = (color & 0xFF)          / 255f;
        if (a <= 0f) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(1.5f);

        Matrix4f m4 = ms.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP,
                                       VertexFormats.POSITION_COLOR);

        int segs = 16;
        float[] cxArr  = {x+w-r, x+r,   x+r,   x+w-r};
        float[] cyArr  = {y+r,   y+r,   y+h-r, y+h-r};
        float[] startA = {270f,  180f,  90f,   0f};

        for (int i = 0; i < 4; i++) {
            for (int j = 0; j <= segs; j++) {
                double ang = Math.toRadians(startA[i] + j * 90.0 / segs);
                buf.vertex(m4,
                    (float)(cxArr[i] + Math.cos(ang) * r),
                    (float)(cyArr[i] + Math.sin(ang) * r),
                    0f).color(rv, g, b, a);
            }
        }
        double closeAng = Math.toRadians(startA[0]);
        buf.vertex(m4,
            (float)(cxArr[0] + Math.cos(closeAng) * r),
            (float)(cyArr[0] + Math.sin(closeAng) * r),
            0f).color(rv, g, b, a);

        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    // ══════════════════════════════════════════════════════════
    //  MENÜ
    // ══════════════════════════════════════════════════════════
    public class HitXMenu extends Screen {

        private static final String[] TABS = {
            "AimAssist", "Aura", "TriggerBot", "Hitboxes",
            "NightVision", "HitColor", "Speed",
            "ESP", "Misc", "Keybinds"
        };

        private static final int PW = 480, PH = 380;

        private String tab      = "AimAssist";
        private int    bind     = -1;
        private int    dragSlot = -1, dCX, dCW;
        private float  animTick = 0f;

        protected HitXMenu() { super(Text.literal("HitX")); }

        @Override public void tick() { super.tick(); animTick += 0.05f; }

        // ─────────────────────────────────────────────────────
        //  RENDER
        // ─────────────────────────────────────────────────────
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            MatrixStack ms = ctx.getMatrices();
            int ox = ox(), oy = oy();

            ctx.fill(0, 0, width, height, 0x88000000);

            drawRoundedRect(ms, ox, oy, PW, PH, 8f, 0xF0111118);
            drawRoundedOutline(ms, ox, oy, PW, PH, 8f, 0xFF6600DD);

            drawRoundedRect(ms, ox, oy, PW, 28, 8f, 0xFF1A0030);
            ctx.fill(ox, oy+14, ox+PW, oy+28, 0xFF1A0030);

            float pulse = (float)(Math.sin(animTick) * 0.5 + 0.5);
            int titleR = (int)(180 + pulse * 75);
            int titleG = (int)(0   + pulse * 20);
            int titleB = (int)(220 + pulse * 35);
            int titleColor = 0xFF000000 | (titleR << 16) | (titleG << 8) | titleB;

            ctx.drawCenteredTextWithShadow(textRenderer,
                "§lHITX  §8│  §7Kontrol Paneli", ox+PW/2, oy+10, titleColor);
            ctx.drawTextWithShadow(textRenderer, "§8v2.1", ox+4, oy+10, 0xFF333333);
            ctx.drawTextWithShadow(textRenderer,
                "§7" + countActive() + " §8aktif", ox+PW-40, oy+10, 0xFF888888);

            // Sol sekme listesi
            drawRoundedRect(ms, ox+2, oy+30, 116, PH-34, 4f, 0xFF0D0D0D);
            int ty = oy + 36;
            for (String t : TABS) {
                boolean sel = t.equals(tab);
                boolean hov = hov(mx, my, ox+6, ty, 108, 22);
                drawRoundedRect(ms, ox+6, ty, 108, 22,
                    sel ? 6f : (hov ? 4f : 3f),
                    sel ? 0xFF3A0080 : (hov ? 0xFF1E1E2A : 0xFF141420));
                if (sel) {
                    drawRoundedRect(ms, ox+6,   ty, 3, 22, 2f, 0xFFBB00FF);
                    drawRoundedRect(ms, ox+111, ty, 3, 22, 2f, 0x44BB00FF);
                }
                ctx.drawTextWithShadow(textRenderer, t, ox+13, ty+7,
                    sel ? 0xFFEE99FF : (hov ? 0xFFCCCCCC : 0xFF666677));
                ty += 26;
            }

            // Sağ içerik alanı
            int cx=cx(), cy=cy(), cw=cw();
            drawRoundedRect(ms, cx-4, oy+30, cw+8, PH-34, 4f, 0xFF0F0F16);
            drawRoundedRect(ms, cx-4, oy+30, cw+8, 24, 4f, 0xFF160025);
            ctx.drawTextWithShadow(textRenderer, "§d§l" + tab, cx, oy+37, 0xFFDD88FF);

            switch (tab) {
                case "Hitboxes"    -> tHitboxes   (ctx, ms, cfg, cx, cy, cw, mx, my);
                case "AimAssist"   -> tAimAssist  (ctx, ms, cfg, cx, cy, cw, mx, my);
                case "Aura"        -> tAura        (ctx, ms, cx, cy, cw, mx, my);
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
            if (fullBrightActive) n++; if (auraActive) n++; if (elytraTargetActive) n++;
            return n;
        }

        // ─────────────────────────────────────────────────────
        //  SEKMELER
        // ─────────────────────────────────────────────────────
        private void tHitboxes(DrawContext ctx, MatrixStack ms, HitXConfig cfg,
                                int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy,     cw, "Hitboxes",      hitBoxActive, mx, my);
            lbl(ctx, cx, cy+30, "Genişlik (XZ):  §e"+f2(cfg.xzExpand));
            sld(ctx, ms, cx, cy+42, cw, (cfg.xzExpand-0.5f)/4.5f, 0);
            lbl(ctx, cx, cy+60, "Yükseklik (Y):  §e"+f2(cfg.yExpand));
            sld(ctx, ms, cx, cy+72, cw, (cfg.yExpand-0.5f)/3.5f, 1);
            lbl(ctx, cx, cy+90, "Y Offset:        §e"+f2(cfg.yOffset));
            sld(ctx, ms, cx, cy+102, cw, (cfg.yOffset+1f)/2f, 2);
        }

        private void tAimAssist(DrawContext ctx, MatrixStack ms, HitXConfig cfg,
                                 int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy,      cw, "AimAssist",         aimAssistActive, mx, my);
            tog(ctx, ms, cx, cy+28,   cw, "Oto Vurma",         aimAutoAttack,   mx, my);
            tog(ctx, ms, cx, cy+56,   cw, "Sarsılma (Recoil)", aimRecoil,       mx, my);
            tog(ctx, ms, cx, cy+84,   cw, "Elytra Menzili",    aimElytra,       mx, my);
            tog(ctx, ms, cx, cy+112,  cw, "Sadece Oyuncular",  aimOnlyPlayers,  mx, my);
            lbl(ctx, cx, cy+142, "Menzil:           §e"+f1(aimRange)+" blok");
            sld(ctx, ms, cx, cy+154, cw, (aimRange-1f)/9f, 10);
            lbl(ctx, cx, cy+170, "Smooth Hız:       §e"+f2(aimSpeed));
            sld(ctx, ms, cx, cy+182, cw, (aimSpeed-0.01f)/0.49f, 11);
            lbl(ctx, cx, cy+198, "FOV Limiti:       §e"+f1(aimFov)+"°");
            sld(ctx, ms, cx, cy+210, cw, aimFov/180f, 12);
            lbl(ctx, cx, cy+226, "Sarsılma Şiddeti: §e"+f2(aimRecoilStr));
            sld(ctx, ms, cx, cy+238, cw, aimRecoilStr/2f, 13);
        }

        /** Aura / ElytraTarget sekmesi — ThunderHack tarzı */
        private void tAura(DrawContext ctx, MatrixStack ms,
                           int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy,     cw, "Aura (KA)",          auraActive,          mx, my);
            tog(ctx, ms, cx, cy+28,  cw, "ElytraTarget",       elytraTargetActive,  mx, my);
            tog(ctx, ms, cx, cy+56,  cw, "Oto Vurma",          auraAutoAttack,      mx, my);
            tog(ctx, ms, cx, cy+84,  cw, "Sadece Oyuncular",   auraOnlyPlayers,     mx, my);
            lbl(ctx, cx, cy+114, "Aura Menzili:   §e"+f1(auraRange)+" blok");
            sld(ctx, ms, cx, cy+126, cw, (auraRange-1f)/6f, 70);
            lbl(ctx, cx, cy+142, "Aura Hızı:      §e"+f2(auraSpeed));
            sld(ctx, ms, cx, cy+154, cw, (auraSpeed-0.01f)/0.39f, 71);
            lbl(ctx, cx, cy+170, "Elytra Menzili: §e"+f1(elytraTargetRange)+" blok");
            sld(ctx, ms, cx, cy+182, cw, (elytraTargetRange-2f)/10f, 72);
            lbl(ctx, cx, cy+202, "§8[G] ile hızlı aç/kapa");
            // Aktif hedef göstergesi
            if (auraLocked != null && auraLocked.isAlive()) {
                String tName = auraLocked.getName().getString();
                lbl(ctx, cx, cy+218, "§7Hedef: §d" + tName);
            } else {
                lbl(ctx, cx, cy+218, "§7Hedef: §8yok");
            }
        }

        private void tTriggerBot(DrawContext ctx, MatrixStack ms, HitXConfig cfg,
                                  int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy,     cw, "TriggerBot",           triggerBotActive,      mx, my);
            lbl(ctx, cx, cy+30, "Gecikme:  §e"+triggerDelay+" ms");
            sld(ctx, ms, cx, cy+42,  cw, triggerDelay/500f, 20);
            lbl(ctx, cx, cy+62, "§8── Engelleme Ayarları ──");
            tog(ctx, ms, cx, cy+76,  cw, "Kalkan KC vurma",      triggerBlockOnShield,  mx, my);
            tog(ctx, ms, cx, cy+104, cw, "Gap yerken vurma",     triggerBlockOnGap,     mx, my);
            tog(ctx, ms, cx, cy+132, cw, "KC tutarken vurma",    triggerBlockSelfShield, mx, my);
            tog(ctx, ms, cx, cy+160, cw, "Gap yerken saldırma",  triggerBlockSelfGap,   mx, my);
            lbl(ctx, cx, cy+192, triggerBotActive ? "§aAktif — nişandaki hedefe vurur" : "§7Kapalı");
        }

        private void tNightVision(DrawContext ctx, MatrixStack ms,
                                  int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy, cw, "Gece Görüşü", nightVisionActive, mx, my);
            lbl(ctx, cx, cy+32, nightVisionActive
                    ? "§aAktif — ekran tam aydınlık görünür"
                    : "§7Kapalı — normal görüş");
        }

        private void tHitColor(DrawContext ctx, MatrixStack ms, HitXConfig cfg,
                                int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy, cw, "HitColor", cfg.hitColorActive, mx, my);
            int pc = (cfg.hcAlpha<<24)|(cfg.hcRed<<16)|(cfg.hcGreen<<8)|cfg.hcBlue;
            drawRoundedRect(ms, cx+cw-32, cy+3,  28, 18, 4f, 0xFF000000);
            drawRoundedRect(ms, cx+cw-31, cy+4,  26, 16, 3f, pc);
            lbl(ctx, cx, cy+30,  "§cKırmızı:  §e"+cfg.hcRed);
            sld(ctx, ms, cx, cy+42,  cw, cfg.hcRed/255f,   30);
            lbl(ctx, cx, cy+58,  "§aYeşil:    §e"+cfg.hcGreen);
            sld(ctx, ms, cx, cy+70,  cw, cfg.hcGreen/255f, 31);
            lbl(ctx, cx, cy+86,  "§bMavi:     §e"+cfg.hcBlue);
            sld(ctx, ms, cx, cy+98,  cw, cfg.hcBlue/255f,  32);
            lbl(ctx, cx, cy+114, "§7Alpha:    §e"+cfg.hcAlpha);
            sld(ctx, ms, cx, cy+126, cw, cfg.hcAlpha/255f, 33);
        }

        private void tSpeed(DrawContext ctx, MatrixStack ms,
                            int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy,     cw, "Speed",      speedActive,  mx, my);
            tog(ctx, ms, cx, cy+28,  cw, "AutoSprint", sprintActive, mx, my);
            tog(ctx, ms, cx, cy+56,  cw, "NoFall",     noFallActive, mx, my);
            lbl(ctx, cx, cy+88, "Hız Çarpanı: §e"+f2(speedMultiplier)+"x");
            sld(ctx, ms, cx, cy+100, cw, (speedMultiplier-1f)/3f, 40);
            lbl(ctx, cx, cy+120, "§8[V] ile hızlı aç/kapa");
        }

        private void tESP(DrawContext ctx, MatrixStack ms,
                          int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy,    cw, "ESP",        espActive,  mx, my);
            tog(ctx, ms, cx, cy+28, cw, "Oyuncular",  espPlayers, mx, my);
            tog(ctx, ms, cx, cy+56, cw, "Moblar",     espMobs,    mx, my);
            lbl(ctx, cx, cy+88,  "§cKırmızı:  §e"+espColorR);
            sld(ctx, ms, cx, cy+100, cw, espColorR/255f, 50);
            lbl(ctx, cx, cy+116, "§aYeşil:    §e"+espColorG);
            sld(ctx, ms, cx, cy+128, cw, espColorG/255f, 51);
            lbl(ctx, cx, cy+144, "§bMavi:     §e"+espColorB);
            sld(ctx, ms, cx, cy+156, cw, espColorB/255f, 52);
            int previewColor = 0xFF000000|(espColorR<<16)|(espColorG<<8)|espColorB;
            drawRoundedRect(ms, cx+cw-32, cy+92, 28, 18, 4f, 0xFF000000);
            drawRoundedRect(ms, cx+cw-31, cy+93, 26, 16, 3f, previewColor);
            lbl(ctx, cx, cy+180, "§8[Z] ile hızlı aç/kapa");
        }

        private void tMisc(DrawContext ctx, MatrixStack ms,
                           int cx, int cy, int cw, int mx, int my) {
            tog(ctx, ms, cx, cy,    cw, "AntiKnockback", antiKbActive,     mx, my);
            tog(ctx, ms, cx, cy+28, cw, "FullBright",    fullBrightActive, mx, my);
            lbl(ctx, cx, cy+58, "AntiKB Gücü: §e"+f2(antiKbStrength*100)+"%");
            sld(ctx, ms, cx, cy+70, cw, antiKbStrength, 60);
            lbl(ctx, cx, cy+100, "§8AntiKB: vuruş geri itmesini iptal eder");
            lbl(ctx, cx, cy+116, "§8FullBright: Gece Görüşü efektini gizler");
        }

        private void tKeybinds(DrawContext ctx, MatrixStack ms,
                               int cx, int cy, int cw, int mx, int my) {
            lbl(ctx, cx, cy, "§8Satıra tıkla → yeni tuşa bas");
            kb(ctx, ms, cx, cy+16,  cw, "Hitboxes",    keyHitbox,      bind==0, mx, my);
            kb(ctx, ms, cx, cy+44,  cw, "AimAssist",   keyAimAssist,   bind==1, mx, my);
            kb(ctx, ms, cx, cy+72,  cw, "TriggerBot",  keyTriggerBot,  bind==2, mx, my);
            kb(ctx, ms, cx, cy+100, cw, "NightVision", keyNightVision, bind==3, mx, my);
            kb(ctx, ms, cx, cy+128, cw, "Speed",       keySpeed,       bind==4, mx, my);
            kb(ctx, ms, cx, cy+156, cw, "ESP",         keyEsp,         bind==5, mx, my);
            kb(ctx, ms, cx, cy+184, cw, "Aura",        keyAura,        bind==6, mx, my);
        }

        // ─────────────────────────────────────────────────────
        //  ÇİZİM YARDIMCILARI
        // ─────────────────────────────────────────────────────
        private void tog(DrawContext ctx, MatrixStack ms, int x, int y, int w,
                         String name, boolean on, int mx, int my) {
            boolean hov = hov(mx, my, x, y, w, 22);
            drawRoundedRect(ms, x, y, w, 22, 5f,
                on ? (hov ? 0xFF004455 : 0xFF003344)
                   : (hov ? 0xFF1E1E2E : 0xFF16161E));
            drawRoundedRect(ms, x, y, 3, 22, 3f, on ? 0xFF00DDFF : 0xFF333344);
            ctx.drawTextWithShadow(textRenderer, name, x+10, y+7,
                on ? 0xFF88EEFF : 0xFF888899);
            ctx.drawTextWithShadow(textRenderer, on ? "§a●" : "§c○", x+w-20, y+7, 0xFFFFFFFF);
        }

        private void sld(DrawContext ctx, MatrixStack ms, int x, int y, int w, float p, int sid) {
            p = MathHelper.clamp(p, 0f, 1f);
            drawRoundedRect(ms, x, y, w, 10, 5f, 0xFF0A0A12);
            int fw = (int)((w-2)*p);
            if (fw > 2) {
                drawRoundedRect(ms, x+1, y+1, fw, 8, 4f, 0xFF4400AA);
                if (fw > 8) drawRoundedRect(ms, x+1+fw-6, y+1, 6, 8, 4f, 0xFF9944FF);
            }
            int kx = x + 1 + (int)((w-8)*p);
            drawRoundedRect(ms, kx, y-2, 6, 14, 3f, 0xFFFFFFFF);
            drawRoundedRect(ms, kx+1, y-1, 4, 12, 2f, 0xFFCCAAFF);
        }

        private void lbl(DrawContext ctx, int x, int y, String t) {
            ctx.drawTextWithShadow(textRenderer, t, x, y, 0xFFAAAAAA);
        }

        private void kb(DrawContext ctx, MatrixStack ms, int x, int y, int w,
                        String name, int key, boolean wait, int mx, int my) {
            boolean hov = hov(mx, my, x, y, w, 22);
            drawRoundedRect(ms, x, y, w, 22, 5f,
                wait ? 0xFF1A0033 : (hov ? 0xFF1C1C28 : 0xFF141420));
            drawRoundedRect(ms, x, y, 3, 22, 3f, wait ? 0xFFFF00CC : 0xFF4400AA);
            ctx.drawTextWithShadow(textRenderer, name, x+10, y+7, 0xFFBBBBCC);
            String ks = wait ? "§e[ tuşa bas... ]" : "§d[ " + kn(key) + " ]";
            ctx.drawTextWithShadow(textRenderer, ks, x+w-90, y+7, 0xFFFFFFFF);
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
        //  MOUSE / KEYBOARD EVENTS
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
                    if (hovD(mx,my,cx,cy,cw,22))          { hitBoxActive=!hitBoxActive; return true; }
                    if (cs(mx,my,cx,cy+42,  cw,0))  { cfg.xzExpand=0.5f+sv(mx,cx,cw)*4.5f; sc(); return true; }
                    if (cs(mx,my,cx,cy+72,  cw,1))  { cfg.yExpand =0.5f+sv(mx,cx,cw)*3.5f; sc(); return true; }
                    if (cs(mx,my,cx,cy+102, cw,2))  { cfg.yOffset =-1f +sv(mx,cx,cw)*2f;   sc(); return true; }
                }
                case "AimAssist" -> {
                    if (hovD(mx,my,cx,cy,    cw,22)) { aimAssistActive=!aimAssistActive; locked=null; bar(client,aimAssistActive?"§aAimAssist §7Açık":"§cAimAssist §7Kapalı"); return true; }
                    if (hovD(mx,my,cx,cy+28, cw,22)) { aimAutoAttack  =!aimAutoAttack;   return true; }
                    if (hovD(mx,my,cx,cy+56, cw,22)) { aimRecoil      =!aimRecoil;       return true; }
                    if (hovD(mx,my,cx,cy+84, cw,22)) { aimElytra      =!aimElytra;       return true; }
                    if (hovD(mx,my,cx,cy+112,cw,22)) { aimOnlyPlayers =!aimOnlyPlayers;  return true; }
                    if (cs(mx,my,cx,cy+154,cw,10)) { aimRange   =1f   +sv(mx,cx,cw)*9f;    return true; }
                    if (cs(mx,my,cx,cy+182,cw,11)) { aimSpeed   =0.01f+sv(mx,cx,cw)*0.49f; return true; }
                    if (cs(mx,my,cx,cy+210,cw,12)) { aimFov     =sv(mx,cx,cw)*180f;         return true; }
                    if (cs(mx,my,cx,cy+238,cw,13)) { aimRecoilStr=sv(mx,cx,cw)*2f;          return true; }
                }
                case "Aura" -> {
                    if (hovD(mx,my,cx,cy,     cw,22)) { auraActive=!auraActive; auraLocked=null; bar(client,auraActive?"§aAura §7Açık":"§cAura §7Kapalı"); return true; }
                    if (hovD(mx,my,cx,cy+28,  cw,22)) { elytraTargetActive=!elytraTargetActive; return true; }
                    if (hovD(mx,my,cx,cy+56,  cw,22)) { auraAutoAttack=!auraAutoAttack;         return true; }
                    if (hovD(mx,my,cx,cy+84,  cw,22)) { auraOnlyPlayers=!auraOnlyPlayers;       return true; }
                    if (cs(mx,my,cx,cy+126,cw,70)) { auraRange       =1f   +sv(mx,cx,cw)*6f;    return true; }
                    if (cs(mx,my,cx,cy+154,cw,71)) { auraSpeed       =0.01f+sv(mx,cx,cw)*0.39f; return true; }
                    if (cs(mx,my,cx,cy+182,cw,72)) { elytraTargetRange=2f  +sv(mx,cx,cw)*10f;   return true; }
                }
                case "TriggerBot" -> {
                    if (hovD(mx,my,cx,cy,    cw,22)) { triggerBotActive=!triggerBotActive; bar(client,triggerBotActive?"§aTriggerBot §7Açık":"§cTriggerBot §7Kapalı"); return true; }
                    if (cs(mx,my,cx,cy+42,   cw,20)) { triggerDelay=(int)(sv(mx,cx,cw)*500); return true; }
                    if (hovD(mx,my,cx,cy+76, cw,22)) { triggerBlockOnShield   =!triggerBlockOnShield;   return true; }
                    if (hovD(mx,my,cx,cy+104,cw,22)) { triggerBlockOnGap      =!triggerBlockOnGap;      return true; }
                    if (hovD(mx,my,cx,cy+132,cw,22)) { triggerBlockSelfShield =!triggerBlockSelfShield; return true; }
                    if (hovD(mx,my,cx,cy+160,cw,22)) { triggerBlockSelfGap    =!triggerBlockSelfGap;    return true; }
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
                    if (hovD(mx,my,cx,cy,   cw,22)) { speedActive  =!speedActive;   bar(client,speedActive?"§aSpeed §7Açık":"§cSpeed §7Kapalı"); return true; }
                    if (hovD(mx,my,cx,cy+28,cw,22)) { sprintActive =!sprintActive;  return true; }
                    if (hovD(mx,my,cx,cy+56,cw,22)) { noFallActive =!noFallActive;  return true; }
                    if (cs(mx,my,cx,cy+100, cw,40)) { speedMultiplier=1f+sv(mx,cx,cw)*3f; return true; }
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
                    if (cs(mx,my,cx,cy+70,  cw,60)) { antiKbStrength=sv(mx,cx,cw); return true; }
                }
                case "Keybinds" -> {
                    if (hovD(mx,my,cx,cy+16, cw,22)) { bind=0; return true; }
                    if (hovD(mx,my,cx,cy+44, cw,22)) { bind=1; return true; }
                    if (hovD(mx,my,cx,cy+72, cw,22)) { bind=2; return true; }
                    if (hovD(mx,my,cx,cy+100,cw,22)) { bind=3; return true; }
                    if (hovD(mx,my,cx,cy+128,cw,22)) { bind=4; return true; }
                    if (hovD(mx,my,cx,cy+156,cw,22)) { bind=5; return true; }
                    if (hovD(mx,my,cx,cy+184,cw,22)) { bind=6; return true; }
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
                case 0  -> { cfg.xzExpand = 0.5f+v*4.5f; sc(); }
                case 1  -> { cfg.yExpand  = 0.5f+v*3.5f; sc(); }
                case 2  -> { cfg.yOffset  = -1f +v*2f;   sc(); }
                case 10 -> aimRange    = 1f   +v*9f;
                case 11 -> aimSpeed    = 0.01f+v*0.49f;
                case 12 -> aimFov      = v*180f;
                case 13 -> aimRecoilStr= v*2f;
                case 20 -> triggerDelay= (int)(v*500);
                case 30 -> { cfg.hcRed  =(int)(v*255); sc(); OverlayReloadListener.callEvent(); }
                case 31 -> { cfg.hcGreen=(int)(v*255); sc(); OverlayReloadListener.callEvent(); }
                case 32 -> { cfg.hcBlue =(int)(v*255); sc(); OverlayReloadListener.callEvent(); }
                case 33 -> { cfg.hcAlpha=(int)(v*255); sc(); OverlayReloadListener.callEvent(); }
                case 40 -> speedMultiplier = 1f+v*3f;
                case 50 -> espColorR = (int)(v*255);
                case 51 -> espColorG = (int)(v*255);
                case 52 -> espColorB = (int)(v*255);
                case 60 -> antiKbStrength = v;
                case 70 -> auraRange        = 1f   +v*6f;
                case 71 -> auraSpeed        = 0.01f+v*0.39f;
                case 72 -> elytraTargetRange = 2f  +v*10f;
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
                    case 6 -> keyAura        = keyCode;
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
    private void iconBtn(Screen s, ItemStack i, String t, int x, int y, int w, int h,
                         ButtonWidget.PressAction p) {}
    private boolean isArmor(ItemStack s) {
        return s.getItem() instanceof net.minecraft.item.ArmorItem;
    }
}
