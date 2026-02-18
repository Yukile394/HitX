package com.exloran.hitx;

import com.exloran.hitx.config.HitXConfig;
import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.hit.EntityHitResult;

public class HitX implements ClientModInitializer {

    private static int hitTime = 0;
    private static int combo = 0;
    private static int comboTimer = 0;

    private static float lastHealth = -1;
    private static float lastDamage = 0;

    private static boolean critical = false;
    private static boolean kill = false;

    private static float shake = 0;

    @Override
    public void onInitializeClient() {

        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            if (!config.enabled) return;
            if (client.player == null) return;

            if (client.crosshairTarget instanceof EntityHitResult hit) {
                if (hit.getEntity() instanceof LivingEntity target) {

                    if (lastHealth < 0) {
                        lastHealth = target.getHealth();
                    }

                    if (client.player.handSwinging) {

                        float newHealth = target.getHealth();
                        lastDamage = Math.max(0, lastHealth - newHealth);
                        lastHealth = newHealth;

                        hitTime = config.duration;
                        combo++;
                        comboTimer = 40;

                        critical = client.player.fallDistance > 0 && !client.player.isOnGround();
                        kill = newHealth <= 0;

                        shake = 4f;

                        if (config.sound) {
                            client.player.playSound(
                                    SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP,
                                    0.7f,
                                    critical ? 0.7f : 1.5f
                            );
                        }
                    }
                }
            }

            if (hitTime > 0) hitTime--;

            if (comboTimer > 0) {
                comboTimer--;
            } else {
                combo = 0;
            }

            if (shake > 0) shake *= 0.7f;
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {

            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            if (!config.enabled) return;
            if (hitTime <= 0) return;

            MinecraftClient client = MinecraftClient.getInstance();

            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();

            int cx = width / 2 + (int)(Math.random() * shake - shake / 2);
            int cy = height / 2 + (int)(Math.random() * shake - shake / 2);

            float progress = (float) hitTime / config.duration;

            // Smooth easing
            float eased = (float)Math.sin(progress * Math.PI);

            int alpha = (int) (255 * eased);

            int baseColor = switch (config.color) {
                case WHITE -> 0xFFFFFF;
                case RED -> 0xFF0000;
                case GREEN -> 0x00FF00;
                case BLUE -> 0x00AAFF;
                default -> 0xFFFF00;
            };

            if (critical) baseColor = 0xFF2222;

            int color = (alpha << 24) | baseColor;

            int dynamicSize = (int)(config.size * (1.0f + (1 - eased)));

            drawRing(drawContext, cx, cy, dynamicSize, 2, color);

            // Kill shockwave
            if (kill) {
                drawRing(drawContext, cx, cy, dynamicSize + 12, 3,
                        (alpha << 24) | 0xFFFFFF);
            }

            // Damage number
            if (lastDamage > 0) {
                drawContext.drawCenteredTextWithShadow(
                        client.textRenderer,
                        Text.literal("-" + String.format("%.1f", lastDamage)),
                        cx,
                        cy - dynamicSize - 12,
                        0xFF5555
                );
            }

            // Combo counter
            if (combo > 1) {
                drawContext.drawCenteredTextWithShadow(
                        client.textRenderer,
                        Text.literal("x" + combo),
                        cx,
                        cy + dynamicSize + 8,
                        0xFFFFFF
                );
            }

            // Critical flash overlay
            if (critical) {
                int flashAlpha = (int)(120 * eased);
                drawContext.fill(0, 0, width, height,
                        (flashAlpha << 24) | 0xFF0000);
            }
        });
    }

    private void drawRing(DrawContext context, int cx, int cy,
                          int radius, int thickness, int color) {

        for (int i = 0; i < 360; i += 6) {

            double rad = Math.toRadians(i);

            int x1 = cx + (int)(Math.cos(rad) * radius);
            int y1 = cy + (int)(Math.sin(rad) * radius);

            int x2 = cx + (int)(Math.cos(rad) * (radius - thickness));
            int y2 = cy + (int)(Math.sin(rad) * (radius - thickness));

            context.fill(x1, y1, x2 + 1, y2 + 1, color);
        }
    }
                }
