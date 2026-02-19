package com.exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;

import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class HitX implements ClientModInitializer {

    private static String displayText = "";
    private static int ticks = 0;
    private static final int MAX_TICKS = 120;

    private static float slideOffset = 0;

    @Override
    public void onInitializeClient() {

        ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signedMessage, sender, params, receptionTimestamp) -> {

            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return true;

            String content = message.getString();

            if (content.startsWith("/login ")) {

                String playerName = sender != null
                        ? sender.getName().getString()
                        : "Bilinmeyen";

                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
                String dateStr = dtf.format(LocalDateTime.now());

                displayText = playerName + " » " + content + " | " + dateStr;

                ticks = MAX_TICKS;
                slideOffset = 150f;

                // Ses efekti
                client.player.playSound(SoundEvents.UI_TOAST_IN, 1f, 1f);

                // Log dosyasına yaz
                writeToLog(displayText);

                return false; // normal mesajı gizle
            }

            return true;
        });

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> renderHud(drawContext));
    }

    private void renderHud(DrawContext context) {

        if (ticks <= 0 || displayText.isEmpty()) return;

        MinecraftClient client = MinecraftClient.getInstance();

        int width = client.getWindow().getScaledWidth();
        int textWidth = client.textRenderer.getWidth(displayText);

        float progress = (float) ticks / MAX_TICKS;
        int alpha = (int) (255 * MathHelper.clamp(progress, 0, 1));

        // Kayarak gelme animasyonu
        slideOffset *= 0.85f;
        if (slideOffset < 1f) slideOffset = 0;

        int x = (int) (width - textWidth - 20 + slideOffset);
        int y = 20;

        int bgColor = (alpha / 2 << 24) | 0x222222;

        // Cam efekti arkaplan
        context.fill(x - 10, y - 6, x + textWidth + 10, y + 14, bgColor);

        // RGB kayan gradient
        float hue = (System.currentTimeMillis() % 5000L) / 5000f;
        int rgb = java.awt.Color.HSBtoRGB(hue, 1f, 1f);
        int color = (alpha << 24) | (rgb & 0xFFFFFF);

        context.drawText(client.textRenderer, displayText, x, y, color, false);

        ticks--;
    }

    private void writeToLog(String text) {
        try {
            FileWriter writer = new FileWriter("HitX_LoginLogs.txt", true);
            writer.write(text + "\n");
            writer.close();
        } catch (IOException ignored) {}
    }
}
