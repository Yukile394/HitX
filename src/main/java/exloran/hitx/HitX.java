package com.exloran.hitx;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

public class HitX {

    private static int hitTime = 0;
    private static final int MAX_TIME = 8;

    public static void init() {

        // Oyuncu vurma kontrolü
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (client.crosshairTarget instanceof EntityHitResult hit) {
                if (hit.getEntity() instanceof LivingEntity target) {

                    if (client.player.handSwinging) {
                        hitTime = MAX_TIME;
                    }
                }
            }

            if (hitTime > 0) {
                hitTime--;
            }
        });

        // HUD Render (Ortada X çizimi)
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {

            if (hitTime <= 0) return;

            MinecraftClient client = MinecraftClient.getInstance();

            int width = client.getWindow().getScaledWidth();
            int height = client.getWindow().getScaledHeight();

            int centerX = width / 2;
            int centerY = height / 2;

            float progress = (float) hitTime / MAX_TIME;
            int alpha = (int) (255 * progress);

            int size = 6;

            int color = (alpha << 24) | 0xFFFFFF;

            // X çizimi
            drawContext.fill(centerX - size, centerY - size,
                    centerX + size, centerY - size + 2, color);

            drawContext.fill(centerX - size, centerY + size - 2,
                    centerX + size, centerY + size, color);

            drawContext.fill(centerX - size, centerY - size,
                    centerX - size + 2, centerY + size, color);

            drawContext.fill(centerX + size - 2, centerY - size,
                    centerX + size, centerY + size, color);
        });
    }
}
