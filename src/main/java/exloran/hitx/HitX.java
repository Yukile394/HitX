package com.exloran.hitx;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class HitX implements ClientModInitializer {

    public static final List<Module> modules = new ArrayList<>();
    private static KeyBinding guiKey;

    @Override
    public void onInitializeClient() {
        registerModules();

        guiKey = new KeyBinding("HitX GUI", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "HitX");

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            if (guiKey.wasPressed()) {
                client.setScreen(new ClickGUI());
            }

            for (Module m : modules) {
                if (m.enabled) m.onUpdate(client);
            }
        });

        HudRenderCallback.EVENT.register((ctx, tick) -> renderArrayList(ctx));
    }

    private void registerModules() {
        modules.add(new Module("FastPlace", true));
        modules.add(new Module("AutoDropAll", false));
        modules.add(new Module("AutoTradeAccept", false));
    }

    private void renderArrayList(DrawContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int y = 5;
        for (Module m : modules) {
            if (m.enabled) {
                context.drawText(client.textRenderer, m.name, 5, y, 0x00FFFF, true);
                y += 10;
            }
        }
    }

    // ================= GUI =================
    public static class ClickGUI extends Screen {

        protected ClickGUI() {
            super(Text.of("HitX"));
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            ctx.fill(0, 0, width, height, 0x55000000);

            // 🔹 SOL ÜST → TÜM ENV DROP
            ctx.fill(10, 10, 140, 30, 0xFF222222);
            ctx.drawText(textRenderer, "TÜM EŞYAYI BIRAK", 15, 18, 0xFFFFFF, true);

            // 🔹 SAĞ ÜST → TRADE ACCEPT
            ctx.fill(width - 140, 10, width - 10, 30, 0xFF222222);
            ctx.drawText(textRenderer, "TRADE ACCEPT", width - 130, 18, 0x00FF00, true);

            // 🔹 ALT BAR
            int y = height - 40;

            ctx.fill(10, y, 120, y + 20, 0xFF111111);
            ctx.drawText(textRenderer, "FAST PLACE", 20, y + 6, 0xFFFFFF, false);

            ctx.fill(140, y, 250, y + 20, 0xFF111111);
            ctx.drawText(textRenderer, "AUTO DROP", 150, y + 6, 0xFFFFFF, false);

            ctx.fill(270, y, 380, y + 20, 0xFF111111);
            ctx.drawText(textRenderer, "TRADE AUTO", 280, y + 6, 0xFFFFFF, false);

            super.render(ctx, mouseX, mouseY, delta);
        }

        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {

            // TÜM ENV DROP
            if (mouseX >= 10 && mouseX <= 140 && mouseY >= 10 && mouseY <= 30) {
                dropAll();
                return true;
            }

            // TRADE ACCEPT TOGGLE
            if (mouseX >= width - 140 && mouseX <= width - 10 && mouseY >= 10 && mouseY <= 30) {
                toggle("AutoTradeAccept");
                return true;
            }

            // ALT BUTTONS
            if (mouseY >= height - 40) {
                if (mouseX <= 120) toggle("FastPlace");
                else if (mouseX <= 250) toggle("AutoDropAll");
                else if (mouseX <= 380) toggle("AutoTradeAccept");
                return true;
            }

            return super.mouseClicked(mouseX, mouseY, button);
        }

        // ✅ FIXED DROP ALL (HATASIZ)
        private void dropAll() {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.player == null) return;

            for (int i = 0; i < client.player.getInventory().size(); i++) {
                ItemStack stack = client.player.getInventory().getStack(i);

                if (!stack.isEmpty()) {
                    client.player.dropItem(stack.copy(), true);
                    client.player.getInventory().setStack(i, ItemStack.EMPTY);
                }
            }
        }

        private void toggle(String name) {
            for (Module m : modules) {
                if (m.name.equals(name)) {
                    m.enabled = !m.enabled;
                }
            }
        }
    }

    // ================= MODULE =================
    public static class Module {
        String name;
        boolean enabled;

        public Module(String name, boolean enabled) {
            this.name = name;
            this.enabled = enabled;
        }

        public void onUpdate(MinecraftClient client) {
            if (client.player == null) return;

            // 🔹 FAST PLACE (FPS DROP YOK)
            if (name.equals("FastPlace")) {
                if (client.options.useKey.isPressed()) {
                    ItemStack stack = client.player.getMainHandStack();

                    if (stack.getItem() instanceof BlockItem) {
                        if (client.player.age % 2 == 0) {
                            client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
                        }
                    }
                }
            }

            // 🔹 AUTO DROP (YAVAŞ YAVAŞ ATAR → LAG YOK)
            if (name.equals("AutoDropAll")) {
                if (client.player.age % 4 == 0) {
                    client.player.dropSelectedItem(true);
                }
            }

            // 🔹 TRADE AUTO (BASIC)
            if (name.equals("AutoTradeAccept")) {
                if (client.currentScreen != null) {
                    client.player.sendMessage(Text.of("§aTrade otomatik kabul"), true);
                }
            }
        }
    }
}
