package exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.SlimeEntity;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.VillagerEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

// ══════════════════════════════════════════════════════════════════════════
//  HitX — Standalone Fabric Combat Mod
//  KillAura | TriggerBot | Criticals | HitBoxes
// ══════════════════════════════════════════════════════════════════════════
public class HitX implements ClientModInitializer {

    // ── Modül Durumları ───────────────────────────────────────────────────
    public static boolean auraActive      = false;
    public static boolean triggerActive   = false;
    public static boolean criticalsActive = false;
    public static boolean hitBoxActive    = false;

    // ── KillAura Ayarları ─────────────────────────────────────────────────
    public static float  auraRange       = 3.1f;
    public static float  auraWallRange   = 0.0f;   // duvardaki mesafe
    public static int    auraFov         = 180;     // derece
    public static boolean elytraTarget   = true;
    public static float  elytraRange     = 6.0f;
    public static boolean targetPlayers  = true;
    public static boolean targetMobs     = true;
    public static boolean targetAnimals  = false;
    public static boolean targetVillagers= false;
    public static boolean lockTarget     = true;
    // SmartCrit
    public static boolean smartCrit      = true;
    public static boolean autoJump       = false;
    // OldDelay (CPS tabanlı)
    public static boolean oldDelay       = false;
    public static int    minCPS          = 7;
    public static int    maxCPS          = 12;

    // ── TriggerBot Ayarları ───────────────────────────────────────────────
    public static float  triggerRange    = 3.0f;
    public static int    triggerMinDelay = 2;
    public static int    triggerMaxDelay = 13;
    public static boolean triggerIgnoreWalls = false;
    public static boolean triggerPauseEating = false;

    // ── Criticals Ayarları ────────────────────────────────────────────────
    public static int    critMode        = 0;  // 0=Packet, 1=Jump, 2=AutoCrit

    // ── HitBoxes Ayarları ─────────────────────────────────────────────────
    public static float  hitboxXZ        = 0.4f;   // XZ genişleme
    public static float  hitboxY         = 0.2f;   // Y genişleme

    // ── İç değişkenler ────────────────────────────────────────────────────
    private boolean      menuKeyLast     = false;
    private LivingEntity auraTarget      = null;
    private int          hitTicks        = 0;
    private int          triggerDelay    = 0;

    private final Random random          = new Random();

    // ══════════════════════════════════════════════════════════════════════
    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
    }

    // ── Ana Tick ──────────────────────────────────────────────────────────
    private void onTick(MinecraftClient mc) {
        if (mc.player == null || mc.world == null) return;

        // M tuşu → Menü
        boolean mKey = GLFW.glfwGetKey(mc.getWindow().getHandle(), GLFW.GLFW_KEY_M) == GLFW.GLFW_PRESS;
        if (mKey && !menuKeyLast) mc.setScreen(new ModernGui());
        menuKeyLast = mKey;

        // ── KillAura ──────────────────────────────────────────────────────
        if (auraActive) {
            tickAura(mc);
        } else {
            auraTarget = null;
        }

        // ── TriggerBot ────────────────────────────────────────────────────
        if (triggerActive) {
            tickTrigger(mc);
        }

        hitTicks--;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  KILLAURA LOGIC  (Thunder Aura'dan adapte)
    // ══════════════════════════════════════════════════════════════════════
    private void tickAura(MinecraftClient mc) {
        if (mc.player.isUsingItem()) return;

        double range = (mc.player.isFallFlying() && elytraTarget) ? elytraRange : auraRange;

        // Hedef seç
        if (!lockTarget || auraTarget == null || !isValidTarget(auraTarget, mc)) {
            auraTarget = findTarget(mc, range);
        }
        if (auraTarget == null) return;

        // AutoJump (SmartCrit için)
        if (!mc.options.jumpKey.isPressed() && mc.player.isOnGround() && autoJump && smartCrit) {
            mc.player.jump();
        }

        // Hedefe smooth rotation
        smoothLookAt(mc, auraTarget);

        // Saldırı zamanı geldi mi?
        if (canAttack(mc)) {
            // Criticals
            if (criticalsActive && mc.player.isOnGround()) {
                sendCritPackets(mc);
            }
            mc.interactionManager.attackEntity(mc.player, auraTarget);
            mc.player.swingHand(Hand.MAIN_HAND);
            hitTicks = calcHitTicks();
        }
    }

    // ── Hedef Seçimi ──────────────────────────────────────────────────────
    private LivingEntity findTarget(MinecraftClient mc, double range) {
        List<LivingEntity> candidates = new ArrayList<>();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le)) continue;
            if (!isValidTarget(le, mc))            continue;
            if (mc.player.distanceTo(le) > range)  continue;

            // FOV kontrolü
            if (auraFov < 180) {
                Vec3d look = mc.player.getRotationVec(1.0f);
                Vec3d toTarget = le.getPos().subtract(mc.player.getPos()).normalize();
                double dot = look.dotProduct(toTarget);
                double fovRad = Math.cos(Math.toRadians(auraFov));
                if (dot < fovRad) continue;
            }

            candidates.add(le);
        }
        if (candidates.isEmpty()) return null;
        // En yakın hedef
        candidates.sort(Comparator.comparingDouble(e -> mc.player.distanceTo(e)));
        return candidates.get(0);
    }

    private boolean isValidTarget(LivingEntity le, MinecraftClient mc) {
        if (le == mc.player)                          return false;
        if (!le.isAlive() || le.isDead())             return false;
        if (le instanceof ArmorStandEntity)           return false;
        if (le instanceof PlayerEntity p) {
            if (!targetPlayers)                       return false;
            if (p.isCreative())                       return false;
        } else if (le instanceof HostileEntity) {
            if (!targetMobs)                          return false;
        } else if (le instanceof AnimalEntity) {
            if (!targetAnimals)                       return false;
        } else if (le instanceof VillagerEntity) {
            if (!targetVillagers)                     return false;
        } else if (le instanceof MobEntity) {
            if (!targetMobs)                          return false;
        } else {
            return false;
        }
        return true;
    }

    // ── Smooth Rotation ───────────────────────────────────────────────────
    private void smoothLookAt(MinecraftClient mc, LivingEntity target) {
        double dx = target.getX() - mc.player.getX();
        double dz = target.getZ() - mc.player.getZ();
        double dy = (target.getY() + target.getHeight() * 0.5)
                  - (mc.player.getY() + mc.player.getEyeHeight(mc.player.getPose()));

        float targetYaw   = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float targetPitch = (float) Math.toDegrees(-Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));

        float curYaw   = mc.player.getYaw();
        float curPitch = mc.player.getPitch();

        // Max adım: 75 derece/tick (Thunder minYawStep/maxYawStep)
        float yawStep   = 65 + random.nextInt(11);
        float pitchStep = 8.0f;

        float newYaw   = rotateToward(curYaw,   targetYaw,   yawStep);
        float newPitch = rotateToward(curPitch, targetPitch, pitchStep);

        mc.player.setYaw(newYaw);
        mc.player.setPitch(MathHelper.clamp(newPitch, -90f, 90f));
    }

    private float rotateToward(float current, float target, float maxStep) {
        float delta = wrapDeg(target - current);
        if (Math.abs(delta) <= maxStep) return target;
        return current + Math.signum(delta) * maxStep;
    }

    private float wrapDeg(float d) {
        d = d % 360;
        if (d > 180)  d -= 360;
        if (d < -180) d += 360;
        return d;
    }

    // ── Saldırı Kontrolü ─────────────────────────────────────────────────
    private boolean canAttack(MinecraftClient mc) {
        if (hitTicks > 0) return false;
        float cd = mc.player.getAttackCooldownProgress(0.5f);
        float required = mc.player.isOnGround() ? 1.0f : 0.9f;
        return cd >= required;
    }

    private int calcHitTicks() {
        if (oldDelay) {
            int cps = minCPS + random.nextInt(Math.max(1, maxCPS - minCPS + 1));
            return 1 + (int)(20f / cps);
        }
        return 10 + random.nextInt(3); // ~11-13 tick default
    }

    // ══════════════════════════════════════════════════════════════════════
    //  TRIGGERBOT LOGIC  (Thunder TriggerBot'dan adapte)
    // ══════════════════════════════════════════════════════════════════════
    private void tickTrigger(MinecraftClient mc) {
        if (mc.player.isUsingItem() && triggerPauseEating) return;

        if (triggerDelay > 0) {
            triggerDelay--;
            return;
        }

        HitResult hr = mc.crosshairTarget;
        if (!(hr instanceof EntityHitResult ehr)) return;

        Entity t = ehr.getEntity();
        if (!(t instanceof LivingEntity le)) return;
        if (!le.isAlive())                   return;
        if (mc.player.distanceTo(le) > triggerRange) return;

        if (mc.player.getAttackCooldownProgress(0.5f) >= 1.0f) {
            if (criticalsActive && mc.player.isOnGround()) {
                sendCritPackets(mc);
            }
            mc.interactionManager.attackEntity(mc.player, le);
            mc.player.swingHand(Hand.MAIN_HAND);
            triggerDelay = triggerMinDelay + random.nextInt(Math.max(1, triggerMaxDelay - triggerMinDelay + 1));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  CRITICALS — 3 aşamalı NCP-bypass paket hop
    // ══════════════════════════════════════════════════════════════════════
    private void sendCritPackets(MinecraftClient mc) {
        if (mc.getNetworkHandler() == null) return;
        double x = mc.player.getX();
        double y = mc.player.getY();
        double z = mc.player.getZ();
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.0625, z, false));
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y + 0.000,  z, false));
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y - 0.0625, z, false));
        mc.getNetworkHandler().sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(x, y,          z, true ));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  MODERN GUI  ─  Tamamen saf drawContext, PNG YOK
    //  Sol tık → toggle  |  Sağ tık → ayar paneli
    // ══════════════════════════════════════════════════════════════════════
    public class ModernGui extends Screen {

        // Kart
        private static final int W = 270;
        private static final int H = 340;

        // Modüller
        private static final String[] NAMES  = { "KillAura", "TriggerBot", "Criticals", "HitBoxes" };
        private static final int      ROW_H  = 48;
        private static final int      ROW_Y0 = 48;
        private static final int      PAD    = 10;

        private int selected = -1;  // ayar paneli açık modül
        private int hoverRow = -1;

        protected ModernGui() { super(Text.literal("HitX")); }

        // ── Durum ─────────────────────────────────────────────────────────
        private boolean getState(int i) {
            return switch (i) {
                case 0 -> auraActive;
                case 1 -> triggerActive;
                case 2 -> criticalsActive;
                case 3 -> hitBoxActive;
                default -> false;
            };
        }

        private void toggle(int i) {
            switch (i) {
                case 0 -> auraActive      = !auraActive;
                case 1 -> triggerActive   = !triggerActive;
                case 2 -> criticalsActive = !criticalsActive;
                case 3 -> hitBoxActive    = !hitBoxActive;
            }
        }

        // ── Render ────────────────────────────────────────────────────────
        @Override
        public void render(DrawContext ctx, int mx, int my, float delta) {
            int cx = (width  - W) / 2;
            int cy = (height - H) / 2;

            // Hover
            hoverRow = -1;
            for (int i = 0; i < NAMES.length; i++) {
                int ry = cy + ROW_Y0 + i * ROW_H;
                if (inBounds(mx, my, cx + PAD, ry, W - PAD * 2, ROW_H - 6)) hoverRow = i;
            }

            // Ana kart
            drawRRect(ctx, cx, cy, W, H, 12, 0xFF0B0016);

            // Üst mor şerit
            ctx.fill(cx + 12, cy,      cx + W - 12, cy + 2,   0xFFBB33FF);
            drawRRect(ctx, cx, cy, W, 44, 12, 0xFF130020);
            ctx.fill(cx, cy + 30, cx + W, cy + 44, 0xFF0B0016);

            // Başlık
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "\u2694 HITX \u2694", cx + W / 2, cy + 10, 0xFFDD77FF);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    "M = menu  |  Left = toggle  |  Right = settings",
                    cx + W / 2, cy + 28, 0xFF444455);

            // Satırlar
            for (int i = 0; i < NAMES.length; i++) drawRow(ctx, cx, cy, i);

            // Ayar paneli
            if (selected >= 0) drawSettings(ctx, cx, cy);

            super.render(ctx, mx, my, delta);
        }

        // ── Modül Satırı ──────────────────────────────────────────────────
        private void drawRow(DrawContext ctx, int cx, int cy, int idx) {
            boolean active = getState(idx);
            boolean hover  = hoverRow == idx;
            boolean sel    = selected == idx;

            int rx = cx + PAD;
            int ry = cy + ROW_Y0 + idx * ROW_H;
            int rw = W - PAD * 2;
            int rh = ROW_H - 6;

            int bg = sel ? 0xCC1C0038 : hover ? 0xAA140030 : 0x990A001A;
            drawRRect(ctx, rx, ry, rw, rh, 7, bg);

            // Sol aktif çizgisi
            if (active) ctx.fill(rx, ry + 5, rx + 3, ry + rh - 5, 0xFFCC44FF);

            // İsim
            ctx.drawTextWithShadow(textRenderer, NAMES[idx],
                    rx + 14, ry + rh / 2 - 4, active ? 0xFFFFFFFF : 0xFF888899);

            // ON rozet
            if (active) {
                int lx = rx + rw - 80; int ly = ry + rh / 2 - 6;
                ctx.fill(lx, ly, lx + 30, ly + 13, 0xBB220044);
                ctx.fill(lx, ly, lx + 30, ly + 1,  0xFFBB33FF);
                ctx.drawCenteredTextWithShadow(textRenderer, "ON", lx + 15, ly + 3, 0xFFCC44FF);
            }

            // Toggle switch
            drawSwitch(ctx, rx + rw - 40, ry + rh / 2 - 7, active);
        }

        // ── Toggle Switch ─────────────────────────────────────────────────
        private void drawSwitch(DrawContext ctx, int x, int y, boolean on) {
            ctx.fill(x, y + 3, x + 30, y + 12, on ? 0xAA770099 : 0xAA222233);
            int kx = on ? x + 18 : x + 2;
            ctx.fill(kx, y, kx + 10, y + 15, on ? 0xFFDD66FF : 0xFF555566);
            ctx.fill(kx + 1, y + 1, kx + 9, y + 14, on ? 0xFFFFAAFF : 0xFF777788);
        }

        // ── Ayar Paneli ───────────────────────────────────────────────────
        private void drawSettings(DrawContext ctx, int cx, int cy) {
            int px = cx + PAD;
            int py = cy + ROW_Y0 + NAMES.length * ROW_H + 4;
            int pw = W - PAD * 2;
            int ph = selected == 0 ? 108 : 72;

            drawRRect(ctx, px, py, pw, ph, 7, 0xCC090018);
            ctx.fill(px, py, px + pw, py + 2, 0xFFBB33FF);
            ctx.drawTextWithShadow(textRenderer,
                    "\u26A9 " + NAMES[selected] + " Settings",
                    px + 10, py + 8, 0xFFDD88FF);

            switch (selected) {
                case 0 -> { // KillAura
                    ctx.drawText(textRenderer,
                            "Range: " + String.format("%.1f", auraRange) +
                            "  WallRange: " + String.format("%.1f", auraWallRange),
                            px + 10, py + 24, 0xFFCCCCCC, false);
                    ctx.drawText(textRenderer,
                            "[-] [+]  range         [-] [+]  wallrange",
                            px + 10, py + 36, 0xFF888899, false);
                    ctx.drawText(textRenderer,
                            "FOV: " + auraFov + "  ElytraRange: " + String.format("%.1f", elytraRange),
                            px + 10, py + 52, 0xFFCCCCCC, false);
                    ctx.drawText(textRenderer,
                            "Targets:  Players=" + b(targetPlayers) +
                            " Mobs=" + b(targetMobs) +
                            " Animals=" + b(targetAnimals),
                            px + 10, py + 66, 0xFFAAAAAA, false);
                    ctx.drawText(textRenderer,
                            "SmartCrit=" + b(smartCrit) +
                            "  AutoJump=" + b(autoJump) +
                            "  LockTarget=" + b(lockTarget),
                            px + 10, py + 80, 0xFFAAAAAA, false);
                    ctx.drawText(textRenderer,
                            "OldDelay=" + b(oldDelay) +
                            "  CPS=" + minCPS + "-" + maxCPS,
                            px + 10, py + 94, 0xFF888899, false);
                }
                case 1 -> { // TriggerBot
                    ctx.drawText(textRenderer,
                            "Range: " + String.format("%.1f", triggerRange),
                            px + 10, py + 24, 0xFFCCCCCC, false);
                    ctx.drawText(textRenderer,
                            "[-] [+]  adjust range",
                            px + 10, py + 36, 0xFF888899, false);
                    ctx.drawText(textRenderer,
                            "Delay: " + triggerMinDelay + "-" + triggerMaxDelay + " ticks",
                            px + 10, py + 52, 0xFFCCCCCC, false);
                }
                case 2 -> { // Criticals
                    ctx.drawText(textRenderer,
                            "Mode: " + (critMode == 0 ? "Packet (NCP)" : critMode == 1 ? "Jump" : "AutoCrit"),
                            px + 10, py + 24, 0xFFCCCCCC, false);
                    ctx.drawText(textRenderer,
                            "[Click] to cycle mode",
                            px + 10, py + 38, 0xFF888899, false);
                    ctx.drawText(textRenderer,
                            "Packet=most compat  Jump=vanilla  AutoCrit=fallDist",
                            px + 10, py + 52, 0xFF555566, false);
                }
                case 3 -> { // HitBoxes
                    ctx.drawText(textRenderer,
                            "XZ expand: +" + String.format("%.2f", hitboxXZ) +
                            "  Y expand: +" + String.format("%.2f", hitboxY),
                            px + 10, py + 24, 0xFFCCCCCC, false);
                    ctx.drawText(textRenderer,
                            "[-] [+]  XZ            [-] [+]  Y",
                            px + 10, py + 38, 0xFF888899, false);
                    ctx.drawText(textRenderer,
                            "Requires HitboxMixin in fabric.mod.json",
                            px + 10, py + 52, 0xFF555566, false);
                }
            }
        }

        private String b(boolean v) { return v ? "\u00a7aON\u00a7r" : "\u00a7cOFF\u00a7r"; }

        // ── Mouse Click ───────────────────────────────────────────────────
        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            int cx = (width  - W) / 2;
            int cy = (height - H) / 2;

            // Satır tıklamaları
            for (int i = 0; i < NAMES.length; i++) {
                int rx = cx + PAD;
                int ry = cy + ROW_Y0 + i * ROW_H;
                int rw = W - PAD * 2;
                int rh = ROW_H - 6;
                if (inBounds(mx, my, rx, ry, rw, rh)) {
                    if (button == 0) toggle(i);
                    else if (button == 1) selected = (selected == i) ? -1 : i;
                    return true;
                }
            }

            // Ayar paneli tıklamaları
            if (selected >= 0) {
                int px = cx + PAD + 10;
                int py = cy + ROW_Y0 + NAMES.length * ROW_H + 4;

                // KillAura
                if (selected == 0) {
                    if (inBounds(my, py + 32, py + 44)) {
                        if (inBounds(mx, px, px + 12))       auraRange    = clampF(auraRange    - 0.1f, 1.0f, 6.0f);
                        else if (inBounds(mx, px + 18, px + 30)) auraRange = clampF(auraRange   + 0.1f, 1.0f, 6.0f);
                        else if (inBounds(mx, px + 110, px + 122)) auraWallRange = clampF(auraWallRange - 0.1f, 0f, 6f);
                        else if (inBounds(mx, px + 128, px + 140)) auraWallRange = clampF(auraWallRange + 0.1f, 0f, 6f);
                        return true;
                    }
                    if (inBounds(my, py + 62, py + 74)) {
                        if (inBounds(mx, px, px + 80))         targetPlayers  = !targetPlayers;
                        else if (inBounds(mx, px + 85, px + 160)) targetMobs   = !targetMobs;
                        else                                    targetAnimals  = !targetAnimals;
                        return true;
                    }
                    if (inBounds(my, py + 76, py + 88)) {
                        if (inBounds(mx, px, px + 75))         smartCrit   = !smartCrit;
                        else if (inBounds(mx, px + 80, px + 155)) autoJump  = !autoJump;
                        else                                    lockTarget  = !lockTarget;
                        return true;
                    }
                    if (inBounds(my, py + 90, py + 102)) {
                        oldDelay = !oldDelay;
                        return true;
                    }
                }

                // TriggerBot
                if (selected == 1 && inBounds(my, py + 32, py + 44)) {
                    if (inBounds(mx, px, px + 12))           triggerRange = clampF(triggerRange - 0.1f, 1f, 7f);
                    else if (inBounds(mx, px + 18, px + 30)) triggerRange = clampF(triggerRange + 0.1f, 1f, 7f);
                    return true;
                }

                // Criticals
                if (selected == 2 && inBounds(my, py + 34, py + 50)) {
                    critMode = (critMode + 1) % 3;
                    return true;
                }

                // HitBoxes
                if (selected == 3 && inBounds(my, py + 34, py + 50)) {
                    if (inBounds(mx, px, px + 12))             hitboxXZ = clampF(hitboxXZ - 0.05f, 0.05f, 2.0f);
                    else if (inBounds(mx, px + 18, px + 30))   hitboxXZ = clampF(hitboxXZ + 0.05f, 0.05f, 2.0f);
                    else if (inBounds(mx, px + 110, px + 122)) hitboxY  = clampF(hitboxY  - 0.05f, 0.05f, 2.0f);
                    else if (inBounds(mx, px + 128, px + 140)) hitboxY  = clampF(hitboxY  + 0.05f, 0.05f, 2.0f);
                    return true;
                }
            }

            return super.mouseClicked(mx, my, button);
        }

        // ── Yuvarlak Dikdörtgen ───────────────────────────────────────────
        private void drawRRect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
            ctx.fill(x + r, y,         x + w - r, y + h,     color);
            ctx.fill(x,     y + r,     x + r,     y + h - r, color);
            ctx.fill(x + w - r, y + r, x + w,     y + h - r, color);
            for (int dx = 0; dx < r; dx++) {
                for (int dy = 0; dy < r; dy++) {
                    if (Math.sqrt((double)(r-dx)*(r-dx)+(double)(r-dy)*(r-dy)) <= r) {
                        ctx.fill(x+dx,         y+dy,         x+dx+1,         y+dy+1,         color);
                        ctx.fill(x+w-r+dx,     y+dy,         x+w-r+dx+1,     y+dy+1,         color);
                        ctx.fill(x+dx,         y+h-r+dy,     x+dx+1,         y+h-r+dy+1,     color);
                        ctx.fill(x+w-r+dx,     y+h-r+dy,     x+w-r+dx+1,     y+h-r+dy+1,     color);
                    }
                }
            }
        }

        // ── Yardımcılar ───────────────────────────────────────────────────
        private boolean inBounds(double v, double min, double max) { return v >= min && v <= max; }
        private boolean inBounds(double mx, double my, int x, int y, int w, int h) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
        private float clampF(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

        @Override public boolean shouldPause()      { return false; }
        @Override public boolean shouldCloseOnEsc() { return true;  }

    } // end ModernGui

} // end HitX
