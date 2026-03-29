package com.exloran.hitx;

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
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;
import java.util.List;

public class HitX implements ClientModInitializer {

    private boolean hudOn = true, tagOn = true;
    private PlayerEntity target = null;
    private float alpha = 0f;
    private boolean dupeOn = false;
    private int dupeTimer = 0, dupeStep = 0, dupeCount = 0, dupeMax = 0;
    private String dupePrice = "1000";
    private boolean rLast = false, nLast = false;

    private static final double RANGE = 6.5, DOT = 0.97;
    private static final float FADE = 0.12f;

    @Override
    public void onInitializeClient() {

        // GUI
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {
            if (screen instanceof GenericContainerScreen chest) {
                int sx = W/2+92, sy = H/2-80, id = chest.getScreenHandler().syncId;
                btn(screen,"Herseyi Al", sx,sy,85,20,b->{int s=chest.getScreenHandler().getInventory().size();for(int i=0;i<s;i++)client.interactionManager.clickSlot(id,i,0,SlotActionType.QUICK_MOVE,client.player);});
                btn(screen,"Herseyi Koy",sx,sy+24,85,20,b->{int s=chest.getScreenHandler().getInventory().size();for(int i=s;i<s+36;i++)client.interactionManager.clickSlot(id,i,0,SlotActionType.QUICK_MOVE,client.player);});
                btn(screen,"Herseyi At", sx,sy+48,85,20,b->{for(int i=0;i<chest.getScreenHandler().slots.size();i++)client.interactionManager.clickSlot(id,i,1,SlotActionType.THROW,client.player);});
                btn(screen,"Cop At",     sx,sy+72,85,20,b->{for(int i=0;i<chest.getScreenHandler().slots.size();i++){ItemStack st=chest.getScreenHandler().getSlot(i).getStack();if(isTrash(st))client.interactionManager.clickSlot(id,i,1,SlotActionType.THROW,client.player);}});
            }
            if (screen instanceof InventoryScreen inv) {
                int x=W/2-88, y=H/2-83, id=inv.getScreenHandler().syncId;
                btn(screen,"Zirhi Giy",x-52,y,50,18,b->{for(int i=9;i<45;i++){ItemStack st=inv.getScreenHandler().getSlot(i).getStack();if(isArmor(st))client.interactionManager.clickSlot(id,i,0,SlotActionType.QUICK_MOVE,client.player);}});
                btn(screen,"Temizle",  x-52,y+20,50,18,b->{for(int i=9;i<45;i++)client.interactionManager.clickSlot(id,i,1,SlotActionType.THROW,client.player);});
                int ax=x+228, ay=y+60;
                btn(screen,"-100",ax,   ay,   38,16,b->{dupePrice=String.valueOf(Math.max(1,safeInt(dupePrice,1000)-100));});
                btn(screen,"+100",ax+40,ay,   38,16,b->{dupePrice=String.valueOf(safeInt(dupePrice,1000)+100);});
                btn(screen,"x1",  ax,   ay+18,25,14,b->dupeMax=1);
                btn(screen,"x5",  ax+27,ay+18,25,14,b->dupeMax=5);
                btn(screen,"x10", ax+54,ay+18,25,14,b->dupeMax=10);
                btn(screen,"Sonsuz",ax, ay+34,79,14,b->dupeMax=0);
                btn(screen,dupeOn?"DUR":"DUPE",ax,ay+50,79,18,b->{
                    if(!dupeOn){dupeOn=true;dupeStep=0;dupeTimer=5;dupeCount=0;client.setScreen(null);client.player.sendMessage(Text.literal("§aDupe basladi §e"+dupePrice+" §7x"+(dupeMax==0?"sonsuz":dupeMax)),true);}
                    else{dupeOn=false;dupeStep=0;dupeTimer=0;client.player.sendMessage(Text.literal("§cDupe durdu"),true);}
                });
                ScreenEvents.afterRender(screen).register((sc,ctx,mx,my,d)->{
                    ctx.drawText(client.textRenderer,"§6AH SELL DUPE",ax,ay-10,0xFFAA00,true);
                    ctx.drawText(client.textRenderer,"§fFiyat: §e"+dupePrice,ax,ay-2,0xFFFFFF,false);
                    ctx.drawText(client.textRenderer,"§7Max: §e"+(dupeMax==0?"sonsuz":dupeMax),ax+40,ay+19,0xFFFFFF,false);
                    ctx.drawText(client.textRenderer,dupeOn?"§a● CALISIYOR":"§c● DURDU",ax,ay+68,0xFFFFFF,false);
                });
            }
        });

        // Tick
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player==null||client.world==null) return;

            boolean r=GLFW.glfwGetKey(client.getWindow().getHandle(),GLFW.GLFW_KEY_R)==GLFW.GLFW_PRESS;
            if (r&&!rLast){hudOn=!hudOn;client.player.sendMessage(Text.literal(hudOn?"§aHUD Ac":"§cHUD Kapat"),true);}
            rLast=r;

            boolean n=GLFW.glfwGetKey(client.getWindow().getHandle(),GLFW.GLFW_KEY_N)==GLFW.GLFW_PRESS;
            if (n&&!nLast){tagOn=!tagOn;client.player.sendMessage(Text.literal(tagOn?"§aBar Ac":"§cBar Kapat"),true);}
            nLast=n;

            if (client.options.forwardKey.isPressed()&&!client.player.horizontalCollision&&!client.player.isSneaking()&&client.player.getHungerManager().getFoodLevel()>6)
                client.player.setSprinting(true);

            if (!client.player.hasStatusEffect(StatusEffects.NIGHT_VISION))
                client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION,400,0,false,false,false));

            if (dupeOn&&client.currentScreen==null) {
                dupeTimer--;
                if (dupeTimer<=0) {
                    if (dupeStep==0) {
                        ItemStack h=client.player.getMainHandStack();
                        if (h.isEmpty()){dupeOn=false;client.player.sendMessage(Text.literal("§cElde item yok"),true);}
                        else{client.player.networkHandler.sendChatCommand("ah sell "+dupePrice);client.player.sendMessage(Text.literal("§7/ah sell "+dupePrice),true);dupeTimer=25;dupeStep=1;}
                    } else if (dupeStep==1) {
                        client.player.networkHandler.sendChatCommand("ah cancel");
                        client.player.sendMessage(Text.literal("§7/ah cancel"),true);
                        dupeTimer=20;dupeStep=2;
                    } else {
                        dupeCount++;
                        if (dupeMax>0&&dupeCount>=dupeMax){dupeOn=false;client.player.sendMessage(Text.literal("§aDupe bitti: "+dupeCount+" loop"),true);}
                        else{dupeStep=0;dupeTimer=10;}
                    }
                }
            }

            boolean show=false;
            if (client.crosshairTarget instanceof EntityHitResult e&&e.getEntity() instanceof PlayerEntity p&&p.isAlive()){target=p;show=true;}
            if (!show) {
                Vec3d eye=client.player.getCameraPosVec(1f),look=client.player.getRotationVec(1f).normalize();
                List<PlayerEntity> near=client.world.getEntitiesByClass(PlayerEntity.class,client.player.getBoundingBox().expand(RANGE),e->e!=client.player&&e.isAlive());
                PlayerEntity best=null;double bd=DOT;
                for (PlayerEntity c:near){double d=look.dotProduct(c.getCameraPosVec(1f).subtract(eye).normalize());if(d>bd){bd=d;best=c;}}
                if (best!=null){target=best;show=true;}
            }
            if (!show) target=null;
            alpha=show&&hudOn?Math.min(1f,alpha+FADE):Math.max(0f,alpha-FADE);
        });

        // HUD
        HudRenderCallback.EVENT.register((ctx,td)->{
            MinecraftClient mc=MinecraftClient.getInstance();
            if (mc.player==null||mc.options.hudHidden) return;
            int sw=mc.getWindow().getScaledWidth(),sh=mc.getWindow().getScaledHeight();

            ctx.drawText(mc.textRenderer,"§aFPS §f"+mc.getCurrentFps(),5,5,0xFFFFFF,true);
            ctx.drawText(mc.textRenderer,"§7HUD "+(hudOn?"§a":"§c")+"[R]",5,14,0xFFFFFF,false);
            ctx.drawText(mc.textRenderer,"§7Bar "+(tagOn?"§a":"§c")+"[N]",5,23,0xFFFFFF,false);
            if (dupeOn) ctx.drawText(mc.textRenderer,"§6DUPE §f"+dupePrice+" §7("+dupeCount+"/"+(dupeMax==0?"inf":dupeMax)+")",5,32,0xFFFFFF,true);

            if (mc.player.getHealth()<=6f) {
                String w="!! DUSUK CAN !!";
                ctx.drawText(mc.textRenderer,w,(sw-mc.textRenderer.getWidth(w))/2,sh/2-30,0xFF2222,true);
            }

            // Oyuncu ustu can bari
            if (tagOn&&mc.world!=null) {
                for (PlayerEntity pl:mc.world.getPlayers()) {
                    if (pl==mc.player||!pl.isAlive()) continue;
                    double dist=mc.player.distanceTo(pl);
                    if (dist>RANGE+0.5) continue;
                    float r=Math.max(0f,pl.getHealth()/pl.getMaxHealth());
                    double[] sc=proj(mc,pl.getPos().add(0,pl.getHeight()+0.25,0),sw,sh);
                    if (sc==null) continue;
                    int px=(int)sc[0],py=(int)sc[1],bw=(int)(48*(1.0-dist/(RANGE+1)*0.4)),bh=4;
                    int bx=px-bw/2,fill=Math.max(1,(int)(r*bw)),col=col(r);
                    ctx.fill(bx-1,py-1,bx+bw+1,py+bh+1,0xAA000000);
                    ctx.fill(bx,py,bx+fill,py+bh,col);
                    ctx.fill(bx,py,bx+fill,py+1,0x33FFFFFF);
                    if (dist<RANGE){String nm=pl.getName().getString();ctx.drawText(mc.textRenderer,nm,px-mc.textRenderer.getWidth(nm)/2,py-10,0xFFFFFF,true);}
                }
            }

            if (alpha<=0.01f||!hudOn) return;
            float hp=target!=null?target.getHealth():0f;
            float mhp=target!=null?target.getMaxHealth():20f;
            float r=mhp>0?Math.max(0f,hp/mhp):0f;
            int a=(int)(alpha*255),c=col(r),hpA=(a<<24)|(c&0xFFFFFF);
            int bW=155,bH=46,rad=5,bX=sw/2-91-bW-4,bY=sh-bH-24;

            ctx.getMatrices().push();
            ctx.getMatrices().translate(bX,bY,200);
            int bg=(Math.min(a,230)<<24)|0x0A0A0A;
            ctx.fill(rad,0,bW-rad,bH,bg);ctx.fill(0,rad,bW,bH-rad,bg);
            ctx.fill(rad,0,bW-rad,2,hpA);
            if (target!=null){try{Identifier sk=mc.getSkinProvider().getSkinTextures(target.getGameProfile()).texture();int hx=6,hy=(bH-20)/2;ctx.fill(hx-1,hy-1,hx+21,hy+21,(Math.min(a,100)<<24)|0x000000);ctx.drawTexture(sk,hx,hy,20,20,8,8,8,8,64,64);ctx.drawTexture(sk,hx,hy,20,20,40,8,8,8,64,64);}catch(Exception ignored){}}
            ctx.drawText(mc.textRenderer,"TARGET",32,4,(Math.min(a,180)<<24)|0x88BBFF,false);
            ctx.drawText(mc.textRenderer,target!=null?target.getName().getString():"---",32,13,(a<<24)|0xFFFFFF,true);
            String hs=(int)Math.ceil(hp)+" H";int hw=mc.textRenderer.getWidth(hs);
            ctx.drawText(mc.textRenderer,hs,bW-hw-6,13,hpA,true);
            int barX=32,barY=29,barW=bW-32-6,barH=7,fill=Math.max(1,(int)(r*barW));
            ctx.fill(barX,barY,barX+barW,barY+barH,(Math.min(a,200)<<24)|0x1A1A1A);
            ctx.fill(barX,barY,barX+fill,barY+barH,hpA);
            ctx.fill(barX,barY,barX+fill,barY+2,(Math.min(a/4,50)<<24)|0xFFFFFF);
            ctx.getMatrices().pop();
        });
    }

    private double[] proj(MinecraftClient mc, Vec3d world, int sw, int sh) {
        try {
            var cam=mc.gameRenderer.getCamera();
            Vec3d rel=world.subtract(cam.getPos());
            if (mc.player.getRotationVec(1f).dotProduct(rel.normalize())<0) return null;
            double yr=Math.toRadians(cam.getYaw()),pr=Math.toRadians(cam.getPitch());
            double rx=rel.x*Math.cos(yr)-rel.z*Math.sin(yr);
            double ry=rel.y;
            double rz=rel.x*Math.sin(yr)+rel.z*Math.cos(yr);
            double ry2=ry*Math.cos(pr)-rz*Math.sin(pr);
            double rz2=ry*Math.sin(pr)+rz*Math.cos(pr);
            if (rz2<=0.05) return null;
            double fov=Math.toRadians(mc.options.getFov().getValue());
            double p=sw/(2.0*Math.tan(fov/2.0));
            double x=sw/2.0+(rx/rz2)*p, y=sh/2.0-(ry2/rz2)*p;
            if (x<-100||x>sw+100||y<-100||y>sh+100) return null;
            return new double[]{x,y};
        } catch (Exception e) { return null; }
    }

    private int col(float r) {
        int hR,hG;
        if (r>0.5f){float t=(r-0.5f)/0.5f;hR=(int)(80+175*t);hG=(int)(200+10*t);}
        else{float t=r/0.5f;hR=220;hG=(int)(30+170*t);}
        return 0xFF000000|(hR<<16)|(hG<<8)|0x44;
    }

    private float lerp(float a,float b,float t){return a+(b-a)*t;}
    private int safeInt(String s,int d){try{return Integer.parseInt(s.trim());}catch(Exception e){return d;}}
    private void btn(Screen sc,String t,int x,int y,int w,int h,ButtonWidget.PressAction a){Screens.getButtons(sc).add(ButtonWidget.builder(Text.literal(t),a).dimensions(x,y,w,h).build());}
    private boolean isTrash(ItemStack s){return s.isOf(Items.ROTTEN_FLESH)||s.isOf(Items.POISONOUS_POTATO)||s.isOf(Items.DIRT)||s.isOf(Items.COBBLESTONE)||s.isOf(Items.GRAVEL)||s.isOf(Items.SAND);}
    private boolean isArmor(ItemStack s){String n=s.getItem().toString().toLowerCase();return n.contains("helmet")||n.contains("chestplate")||n.contains("leggings")||n.contains("boots");}

    public static class Module {
        String name; boolean enabled;
        public Module(String name,boolean enabled){this.name=name;this.enabled=enabled;}
    }
}
