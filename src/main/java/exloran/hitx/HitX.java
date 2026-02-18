package com.exloran.hitx;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;

public class HitX implements ClientModInitializer {

    private static int hitTime = 0;
    private static float animationScale = 1f;

    @Override
    public void onInitializeClient() {

        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {

            if (client.player == null) return;

            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            if (client.crosshairTarget instanceof EntityHitResult hit) {
                if (hit.getEntity() instanceof LivingEntity) {
                    if (client.player.handSwinging) {
                        hitTime = config.duration;
                        animationScale = 1.6f; // pop efekti
                    }
                }
            }

            if (hitTime > 0) {
                hitTime--;
                animationScale -= 0.05f;
                if (animationScale < 1f) animationScale = 1f;
            }
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {

            if (hitTime <= 0) return;

            HitXConfig config = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

            renderHitMarker(drawContext, config);
        });
    }

    private void renderHitMarker(DrawContext context, HitXConfig config) {

        MinecraftClient client = MinecraftClient.getInstance();

        int width = client.getWindow().getScaledWidth();
        int height = client.getWindow().getScaledHeight();

        int centerX = width / 2;
        int centerY = height / 2;

        float progress = (float) hitTime / config.duration;
        int alpha = (int) (255 * progress);

        int size = (int) (config.size * animationScale);

        int color = (alpha << 24) |
                (config.red << 16) |
                (config.green << 8) |
                config.blue;

        switch (config.effectLevel) {

            case 1 -> drawCircle(context, centerX, centerY, size, color);

            case 2 -> {
                drawCircle(context, centerX, centerY, size, color);
                drawCross(context, centerX, centerY, size, color);
            }

            case 3 -> {
                drawCircle(context, centerX, centerY, size, color);
                drawCross(context, centerX, centerY, size, color);
                drawPulse(context, centerX, centerY, size + 4, alpha);
            }
        }
    }

    private void drawCircle(DrawContext context, int cx, int cy, int radius, int color) {
        for (int i = 0; i < 360; i += 6) {
            double rad = Math.toRadians(i);
            int x = (int) (cx + Math.cos(rad) * radius);
            int y = (int) (cy + Math.sin(rad) * radius);
            context.fill(x, y, x + 2, y + 2, color);
        }
    }

    private void drawCross(DrawContext context, int cx, int cy, int size, int color) {
        int thickness = 2;

        context.fill(cx - size, cy - thickness, cx + size, cy + thickness, color);
        context.fill(cx - thickness, cy - size, cx + thickness, cy + size, color);
    }

    private void drawPulse(DrawContext context, int cx, int cy, int radius, int alpha) {

        int pulseAlpha = (int) (alpha * 0.4f);
        int pulseColor = (pulseAlpha << 24) | 0xFFFFFF;

        for (int i = 0; i < 360; i += 12) {
            double rad = Math.toRadians(i);
            int x = (int) (cx + Math.cos(rad) * radius);
            int y = (int) (cy + Math.sin(rad) * radius);
            context.fill(x, y, x + 3, y + 3, pulseColor);
        }
    }
                           }
