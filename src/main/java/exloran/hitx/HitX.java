package com.exloran.hitx;

import me.shedaniel.autoconfig.AutoConfig;
import me.shedaniel.autoconfig.serializer.GsonConfigSerializer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.GenericContainerScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

public class HitX implements ClientModInitializer {

    private boolean hudOn = true, tagOn = true;
    private PlayerEntity target = null;
    private float alpha = 0f;
    private boolean rLast = false, nLast = false;
    private static final double RANGE = 6.5, DOT = 0.97;
    private static final float FADE = 0.12f;

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            // Sandık içi butonlar
            if (screen instanceof GenericContainerScreen chest) {
                int sx = W/2+92, sy = H/2-80, id = chest.getScreenHandler().syncId;
                btn(screen,"Herseyi Al", sx,sy,85,20,b->{int s=chest.getScreenHandler().getInventory().size();for(int i=0;i<s;i++)client.interactionManager.clickSlot(id,i,0,SlotActionType.QUICK_MOVE,client.player);});
                btn(screen,"Herseyi Koy",sx,sy+24,85,20,b->{int s=chest.getScreenHandler().getInventory().size();for(int i=s;i<s+36;i++)client.interactionManager.clickSlot(id,i,0,SlotActionType.QUICK_MOVE,client.player);});
                btn(screen,"Herseyi At", sx,sy+48,85,20,b->{for(int i=0;i<chest.getScreenHandler().slots.size();i++)client.interactionManager.clickSlot(id,i,1,SlotActionType.THROW,client.player);});
            }

            // Envanter - "Babakral Dupe" ve Kese Mantığı
            if (screen instanceof InventoryScreen inv) {
                int id = inv.getScreenHandler().syncId;

                // Fotoğraftaki birebir buton ayarları (x: 5, y: 275, genişlik: 80)
                Screens.getButtons(screen).add(ButtonWidget.builder(Text.of("Babakral Dupe"), (button) -> {
                    
                    // 1. ADIM: Kese koy ve slot 15'e tıkla (Fotoğraftaki ilk işlem)
                    client.player.networkHandler.sendChatMessage("/kese koy 70"); 
                    client.interactionManager.clickSlot(id, 15, 1, SlotActionType.QUICK_MOVE, client.player);
                    
                    // 2. ADIM: İlanı ver
                    client.player.networkHandler.sendChatMessage("/ah sell 70");

                    // 3. ADIM: 600ms Gecikmeli işlem (Fotoğraftaki Timer yapısı)
                    new Timer().schedule(new TimerTask() {
                        @Override
                        public void run() {
                            // Kese al ve slot 10'a tıkla
                            client.player.networkHandler.sendChatMessage("/kese al 70");
                            client.interactionManager.clickSlot(id, 10, 0, SlotActionType.QUICK_MOVE, client.player);
                        }
                    }, 600);

                }).dimensions(5, 275, 80, 20).build());
            }
        });

        // Tick ve Görsel İşlemler
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player==null||client.world==null) return;
            
            // R ve N tuşları kontrolü
            boolean r=GLFW.glfwGetKey(client.getWindow().getHandle(),GLFW.GLFW_KEY_R)==GLFW.GLFW_PRESS;
            if (r&&!rLast){hudOn=!hudOn;client.player.sendMessage(Text.literal(hudOn?"§aHUD Açık":"§cHUD Kapalı"),true);}
            rLast=r;
            
            boolean n=GLFW.glfwGetKey(client.getWindow().getHandle(),GLFW.GLFW_KEY_N)==GLFW.GLFW_PRESS;
            if (n&&!nLast){tagOn=!tagOn;client.player.sendMessage(Text.literal(tagOn?"§aBar Açık":"§cBar Kapalı"),true);}
            nLast=n;

            // Otomatik Özellikler
            if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION))
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION,400,0,false,false,false));

            if (client.options.forwardKey.isPressed() && !client.player.horizontalCollision && client.player.getHungerManager().getFoodLevel() > 6)
                client.player.setSprinting(true);

            // Hedefleme Sistemi
            boolean show=false;
            if (client.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof PlayerEntity p && p.isAlive()){target=p;show=true;}
            if (!show) {
                Vec3d eye=client.player.getCameraPosVec(1f), look=client.player.getRotationVec(1f).normalize();
                List<PlayerEntity> near=client.world.getEntitiesByClass(PlayerEntity.class, client.player.getBoundingBox().expand(RANGE), e->e!=client.player && e.isAlive());
                PlayerEntity best=null; double bd=DOT;
                for (PlayerEntity c:near){double d=look.dotProduct(c.getCameraPosVec(1f).subtract(eye).normalize()); if(d>bd){bd=d; best=c;}}
                if (best!=null){target=best; show=true;}
            }
            if (!show) target=null;
            alpha=show&&hudOn?Math.min(1f,alpha+FADE):Math.max(0f,alpha-FADE);
        });

        // HUD Render (Dış yapı korunmuştur)
        HudRenderCallback.EVENT.register((ctx, tickCounter)-> {
            // ... (Kendi render kodların buraya gelecek)
        });
    }

    private void btn(Screen sc, String t, int x, int y, int w, int h, ButtonWidget.PressAction a){
        Screens.getButtons(sc).add(ButtonWidget.builder(Text.literal(t), a).dimensions(x, y, w, h).build());
    }

    private float lerp(float a, float b, float t){return a+(b-a)*t;}
    private double lerp(double a, double b, float t){return a+(b-a)*t;}
    private int col(float r) {
        int hR, hG;
        if (r>0.5f){float t=(r-0.5f)/0.5f; hR=(int)(80+175*t); hG=(int)(200+10*t);}
        else{float t=r/0.5f; hR=220; hG=(int)(30+170*t);}
        return 0xFF000000|(hR<<16)|(hG<<8)|0x44;
    }
}
