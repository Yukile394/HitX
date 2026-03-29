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

public class HitX implements ClientModInitializer {

    // HUD
    private boolean hudOn = true, tagOn = true;
    private PlayerEntity target = null;
    private float alpha = 0f;
    private boolean rLast = false, nLast = false;
    private static final double RANGE = 6.5, DOT = 0.97;
    private static final float FADE = 0.12f;

    // Dupe sistemi
    // Mod: 0=AH Sell, 1=Drop Dupe, 2=Shop Dupe
    private boolean dupeOn    = false;
    private int dupeMode      = 0;   // secili dupe turu
    private int dupeStep      = 0;
    private int dupeTimer     = 0;
    private int dupeCount     = 0;
    private int dupeMax       = 0;   // 0 = sonsuz
    private String dupePrice  = "1000";
    private String shopCmd    = "shop"; // shop komutu (sunucuya gore degisir)

    @Override
    public void onInitializeClient() {
        AutoConfig.register(HitXConfig.class, GsonConfigSerializer::new);

        // ===== SCREEN EVENTS =====
        ScreenEvents.AFTER_INIT.register((client, screen, W, H) -> {

            // Sandik
            if (screen instanceof GenericContainerScreen chest) {
                int sx=W/2+92, sy=H/2-80, id=chest.getScreenHandler().syncId;
                btn(screen,"Herseyi Al", sx,sy,   85,20,b->{int s=chest.getScreenHandler().getInventory().size();for(int i=0;i<s;i++)client.interactionManager.clickSlot(id,i,0,SlotActionType.QUICK_MOVE,client.player);});
                btn(screen,"Herseyi Koy",sx,sy+24,85,20,b->{int s=chest.getScreenHandler().getInventory().size();for(int i=s;i<s+36;i++)client.interactionManager.clickSlot(id,i,0,SlotActionType.QUICK_MOVE,client.player);});
                btn(screen,"Herseyi At", sx,sy+48,85,20,b->{for(int i=0;i<chest.getScreenHandler().slots.size();i++)client.interactionManager.clickSlot(id,i,1,SlotActionType.THROW,client.player);});
                btn(screen,"Cop At",     sx,sy+72,85,20,b->{for(int i=0;i<chest.getScreenHandler().slots.size();i++){ItemStack st=chest.getScreenHandler().getSlot(i).getStack();if(isTrash(st))client.interactionManager.clickSlot(id,i,1,SlotActionType.THROW,client.player);}});
            }

            // Envanter
            if (screen instanceof InventoryScreen inv) {
                int x=W/2-88, y=H/2-83, id=inv.getScreenHandler().syncId;

                // Sag taraf: mevcut butonlar
                btn(screen,"Zirhi Giy",x+228,y,   50,18,b->{for(int i=9;i<45;i++){ItemStack st=inv.getScreenHandler().getSlot(i).getStack();if(isArmor(st))client.interactionManager.clickSlot(id,i,0,SlotActionType.QUICK_MOVE,client.player);}});
                btn(screen,"Temizle",  x+228,y+20,50,18,b->{for(int i=9;i<45;i++)client.interactionManager.clickSlot(id,i,1,SlotActionType.THROW,client.player);});

                // Sol taraf: Dupe paneli
                // Sol panel X = envanter solundan 110px sol
                int px = x - 112;
                int py = y;
                int pw = 108; // panel genisligi

                // --- DUPE TUR SECIMI ---
                btn(screen,"AH Sell",  px,   py,   34,14,b->dupeMode=0);
                btn(screen,"Drop",     px+36,py,   34,14,b->dupeMode=1);
                btn(screen,"Shop",     px+72,py,   34,14,b->dupeMode=2);

                // --- FIYAT (AH Sell icin) ---
                btn(screen,"-1k",  px,   py+16,24,13,b->dupePrice=String.valueOf(Math.max(1,safeInt(dupePrice,1000)-1000)));
                btn(screen,"+1k",  px+26,py+16,24,13,b->dupePrice=String.valueOf(safeInt(dupePrice,1000)+1000));
                btn(screen,"-100", px+52,py+16,24,13,b->dupePrice=String.valueOf(Math.max(1,safeInt(dupePrice,1000)-100)));
                btn(screen,"+100", px+78,py+16,24,13,b->dupePrice=String.valueOf(safeInt(dupePrice,1000)+100));

                // --- LOOP SAYISI ---
                btn(screen,"x1",  px,   py+31,24,13,b->dupeMax=1);
                btn(screen,"x5",  px+26,py+31,24,13,b->dupeMax=5);
                btn(screen,"x10", px+52,py+31,24,13,b->dupeMax=10);
                btn(screen,"INF", px+78,py+31,24,13,b->dupeMax=0);

                // --- SHOP KOMUTU degistir ---
                btn(screen,"shop+",px,   py+46,52,13,b->{shopCmd=shopCmd.equals("shop")?"market":shopCmd.equals("market")?"bazar":"shop";});
                btn(screen,"Sifirla",px+54,py+46,52,13,b->{dupeCount=0;dupeStep=0;dupeTimer=0;});

                // --- BASLAT / DURDUR ---
                btn(screen, dupeOn ? ">> DUR <<" : ">> DUPE <<", px, py+61, pw, 20, b -> {
                    if (!dupeOn) {
                        if (client.player.getMainHandStack().isEmpty()) {
                            client.player.sendMessage(Text.literal("§c[HitX] Elde item olmali!"), true);
                            return;
                        }
                        dupeOn=true; dupeStep=0; dupeTimer=5; dupeCount=0;
                        client.setScreen(null);
                        String modeStr = dupeMode==0?"AH Sell":dupeMode==1?"Drop":"Shop";
                        client.player.sendMessage(Text.literal("§a[DUPE] Basladi! Mod: §e"+modeStr+" §7Fiyat: §e"+dupePrice+" §7Loop: §e"+(dupeMax==0?"INF":dupeMax)), true);
                    } else {
                        dupeOn=false; dupeStep=0; dupeTimer=0;
                        client.player.sendMessage(Text.literal("§c[DUPE] Durduruldu. Toplam: §e"+dupeCount), true);
                    }
                });

                // Sol panel yazilari afterRender ile ciz
                ScreenEvents.afterRender(screen).register((sc, ctx, mx, my, d) -> {
                    // Arka plan kutu
                    ctx.fill(px-4, py-14, px+pw+4, py+86, 0xBB000000);
                    ctx.fill(px-4, py-14, px+pw+4, py-13, 0xFF222244);

                    // Baslik
                    ctx.drawText(client.textRenderer, "§b== DUPE ==", px+8, py-11, 0xAADDFF, true);

                    // Tur gostergesi
                    String[] modes={"§eAH Sell","§aDrop","§dShop"};
                    ctx.drawText(client.textRenderer, "Mod: "+modes[dupeMode], px, py+1, 0xFFFFFF, false);

                    // Fiyat
                    ctx.drawText(client.textRenderer, "§7Fiyat: §f"+dupePrice, px, py+16, 0xFFFFFF, false);

                    // Loop
                    ctx.drawText(client.textRenderer, "§7Loop: §f"+(dupeMax==0?"INF":dupeMax), px+54, py+32, 0xFFFFFF, false);

                    // Shop komutu
                    ctx.drawText(client.textRenderer, "§7Cmd: §f/"+shopCmd, px, py+47, 0xFFFFFF, false);

                    // Durum
                    if (dupeOn) {
                        ctx.drawText(client.textRenderer, "§a● "+dupeCount+"/"+(dupeMax==0?"INF":dupeMax), px, py+83, 0xFFFFFF, true);
                    } else {
                        ctx.drawText(client.textRenderer, "§c● DURDU", px, py+83, 0xFFFFFF, false);
                    }
                });
            }
        });

        // ===== TICK =====
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

            // ---- DUPE TICK ----
            if (dupeOn && client.currentScreen==null) {
                dupeTimer--;
                if (dupeTimer<=0) {
                    switch (dupeMode) {
                        case 0 -> tickAhSell(client);  // AH Sell dupe
                        case 1 -> tickDrop(client);    // Drop dupe
                        case 2 -> tickShop(client);    // Shop dupe
                    }
                }
            }

            // Hedef tespiti
            boolean show=false;
            if (client.crosshairTarget instanceof EntityHitResult e&&e.getEntity() instanceof PlayerEntity p&&p.isAlive()){target=p;show=true;}
            if (!show) {
                Vec3d eye=client.player.getCameraPosVec(1f), look=client.player.getRotationVec(1f).normalize();
                List<PlayerEntity> near=client.world.getEntitiesByClass(PlayerEntity.class,client.player.getBoundingBox().expand(RANGE),e->e!=client.player&&e.isAlive());
                PlayerEntity best=null; double bd=DOT;
                for (PlayerEntity c:near){double d=look.dotProduct(c.getCameraPosVec(1f).subtract(eye).normalize());if(d>bd){bd=d;best=c;}}
                if (best!=null){target=best;show=true;}
            }
            if (!show) target=null;
            alpha=show&&hudOn?Math.min(1f,alpha+FADE):Math.max(0f,alpha-FADE);
        });

        // ===== HUD =====
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            MinecraftClient mc=MinecraftClient.getInstance();
            if (mc.player==null||mc.options.hudHidden) return;
            HitXConfig config=AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            int sw=mc.getWindow().getScaledWidth(), sh=mc.getWindow().getScaledHeight();
            float delta=tickCounter.getTickDelta(true);

            ctx.drawText(mc.textRenderer,"§aFPS §f"+mc.getCurrentFps(),5,5,0xFFFFFF,true);
            ctx.drawText(mc.textRenderer,"§7HUD "+(hudOn?"§a":"§c")+"[R]",5,14,0xFFFFFF,false);
            ctx.drawText(mc.textRenderer,"§7Bar "+(tagOn?"§a":"§c")+"[N]",5,23,0xFFFFFF,false);
            if (dupeOn) {
                String[] mn={"§eAH","§aDrop","§dShop"};
                ctx.drawText(mc.textRenderer,"§7DUPE "+mn[dupeMode]+" §f"+dupePrice+" §7("+dupeCount+"/"+(dupeMax==0?"INF":dupeMax)+")",5,32,0xFFFFFF,true);
            }

            // Oyuncu ustu can bari
            if (tagOn&&mc.world!=null) {
                for (PlayerEntity pl:mc.world.getPlayers()) {
                    if (pl==mc.player||!pl.isAlive()) continue;
                    double dist=mc.player.distanceTo(pl);
                    if (dist>RANGE+0.5) continue;
                    double wx=config.visuals.sabitBar?pl.getX():lerp(pl.lastRenderX,pl.getX(),delta);
                    double wy=config.visuals.sabitBar?pl.getY():lerp(pl.lastRenderY,pl.getY(),delta);
                    double wz=config.visuals.sabitBar?pl.getZ():lerp(pl.lastRenderZ,pl.getZ(),delta);
                    double[] sc=proj(mc,new Vec3d(wx,wy+pl.getHeight()+0.4,wz),sw,sh);
                    if (sc==null) continue;
                    float r=Math.max(0f,pl.getHealth()/pl.getMaxHealth());
                    int px=(int)sc[0],py=(int)sc[1],bw=(int)(50*(1.0-dist/(RANGE+2)*0.3)),bh=4;
                    int bx=px-bw/2,fill=Math.max(1,(int)(r*bw)),col=col(r);
                    ctx.fill(bx-1,py-1,bx+bw+1,py+bh+1,0xAA000000);
                    ctx.fill(bx,py,bx+fill,py+bh,col);
                    if (dist<RANGE-1){String nm=pl.getName().getString();ctx.drawText(mc.textRenderer,nm,px-mc.textRenderer.getWidth(nm)/2,py-10,0xFFFFFF,true);}
                }
            }

            if (alpha<=0.01f||!hudOn) return;
            float hp=target!=null?target.getHealth():0f, mhp=target!=null?target.getMaxHealth():20f, r=Math.max(0f,hp/mhp);
            int a=(int)(alpha*255), c=col(r), hpA=(a<<24)|(c&0xFFFFFF);
            int bW=155, bH=46;
            int bX=(sw*config.hudX)/100-(bW/2), bY=(sh*config.hudY)/100-(bH/2);
            float scale=config.hudScale/100f;

            ctx.getMatrices().push();
            ctx.getMatrices().translate(bX+bW/2f,bY+bH/2f,200);
            ctx.getMatrices().scale(scale,scale,1);
            ctx.getMatrices().translate(-bW/2f,-bH/2f,0);
            int bg=(Math.min(a,230)<<24)|0x0A0A0A;
            ctx.fill(5,0,bW-5,bH,bg);ctx.fill(0,5,bW,bH-5,bg);
            ctx.fill(5,0,bW-5,2,hpA);
            if (target!=null){try{Identifier sk=mc.getSkinProvider().getSkinTextures(target.getGameProfile()).texture();int hx=6,hy=(bH-20)/2;ctx.fill(hx-1,hy-1,hx+21,hy+21,(Math.min(a,100)<<24)|0x000000);ctx.drawTexture(sk,hx,hy,20,20,8,8,8,8,64,64);ctx.drawTexture(sk,hx,hy,20,20,40,8,8,8,64,64);}catch(Exception ignored){}}
            ctx.drawText(mc.textRenderer,"TARGET",32,4,(Math.min(a,180)<<24)|0x88BBFF,false);
            ctx.drawText(mc.textRenderer,target!=null?target.getName().getString():"---",32,13,(a<<24)|0xFFFFFF,true);
            String hs=(int)Math.ceil(hp)+" HP"; int hw=mc.textRenderer.getWidth(hs);
            ctx.drawText(mc.textRenderer,hs,bW-hw-6,13,hpA,true);
            int barX=32,barY=29,barW=bW-38,barH=7,fill=Math.max(1,(int)(r*barW));
            ctx.fill(barX,barY,barX+barW,barY+barH,(Math.min(a,200)<<24)|0x1A1A1A);
            ctx.fill(barX,barY,barX+fill,barY+barH,hpA);
            ctx.getMatrices().pop();
        });
    }

    // ===== DUPE MOD 0: AH SELL =====
    // Adim 0: /ah sell <fiyat> gonder
    // Adim 1: /ah cancel ile geri al (bazi sunucularda item dupe olur)
    private void tickAhSell(MinecraftClient client) {
        if (dupeStep==0) {
            if (client.player.getMainHandStack().isEmpty()){stopDupe(client,"Elde item yok");return;}
            client.player.networkHandler.sendChatCommand("ah sell "+dupePrice);
            client.player.sendMessage(Text.literal("§7>> /ah sell "+dupePrice),true);
            dupeTimer=25; dupeStep=1;
        } else if (dupeStep==1) {
            client.player.networkHandler.sendChatCommand("ah cancel");
            client.player.sendMessage(Text.literal("§7>> /ah cancel"),true);
            dupeTimer=20; dupeStep=2;
        } else {
            finishLoop(client);
        }
    }

    // ===== DUPE MOD 1: DROP DUPE =====
    // Adim 0: Elde item at (Q)
    // Adim 1: /back veya /undo ile geri al (plugin destekli)
    private void tickDrop(MinecraftClient client) {
        if (dupeStep==0) {
            if (client.player.getMainHandStack().isEmpty()){stopDupe(client,"Elde item yok");return;}
            // Q tusuna bas (drop action)
            client.player.dropSelectedItem(false);
            client.player.sendMessage(Text.literal("§7>> Item atildi"),true);
            dupeTimer=15; dupeStep=1;
        } else if (dupeStep==1) {
            // Plugin: /undo veya /back komutu
            client.player.networkHandler.sendChatCommand("undo");
            client.player.sendMessage(Text.literal("§7>> /undo"),true);
            dupeTimer=20; dupeStep=2;
        } else {
            finishLoop(client);
        }
    }

    // ===== DUPE MOD 2: SHOP DUPE =====
    // Adim 0: /shop ile dukkan ac
    // Adim 1: Sat (sunucu plugin'ine gore)
    // Adim 2: Kapat ve tekrarla
    private void tickShop(MinecraftClient client) {
        if (dupeStep==0) {
            if (client.player.getMainHandStack().isEmpty()){stopDupe(client,"Elde item yok");return;}
            client.player.networkHandler.sendChatCommand(shopCmd+" sell");
            client.player.sendMessage(Text.literal("§7>> /"+shopCmd+" sell"),true);
            dupeTimer=20; dupeStep=1;
        } else if (dupeStep==1) {
            client.player.networkHandler.sendChatCommand(shopCmd+" cancel");
            client.player.sendMessage(Text.literal("§7>> /"+shopCmd+" cancel"),true);
            dupeTimer=20; dupeStep=2;
        } else {
            finishLoop(client);
        }
    }

    private void finishLoop(MinecraftClient client) {
        dupeCount++;
        if (dupeMax>0&&dupeCount>=dupeMax) {
            stopDupe(client,"Tamamlandi! Toplam: "+dupeCount+" loop");
        } else {
            dupeStep=0; dupeTimer=8;
        }
    }

    private void stopDupe(MinecraftClient client, String msg) {
        dupeOn=false; dupeStep=0; dupeTimer=0;
        client.player.sendMessage(Text.literal("§c[DUPE] "+msg),true);
    }

    // ===== PROJEKSIYON =====
    private double[] proj(MinecraftClient mc, Vec3d world, int sw, int sh) {
        try {
            var cam=mc.gameRenderer.getCamera();
            Vec3d rel=world.subtract(cam.getPos());
            if (mc.player.getRotationVec(1f).dotProduct(rel.normalize())<0) return null;
            double yr=Math.toRadians(cam.getYaw()), pr=Math.toRadians(cam.getPitch());
            double rx=rel.x*Math.cos(yr)-rel.z*Math.sin(yr), ry=rel.y, rz=rel.x*Math.sin(yr)+rel.z*Math.cos(yr);
            double ry2=ry*Math.cos(pr)-rz*Math.sin(pr), rz2=ry*Math.sin(pr)+rz*Math.cos(pr);
            if (rz2<=0.1) return null;
            double fov=Math.toRadians(mc.options.getFov().getValue()), p=sw/(2.0*Math.tan(fov/2.0));
            return new double[]{sw/2.0+(rx/rz2)*p, sh/2.0-(ry2/rz2)*p};
        } catch (Exception e) { return null; }
    }

    private int col(float r) {
        int hR,hG;
        if (r>0.5f){float t=(r-0.5f)/0.5f;hR=(int)(80+175*t);hG=(int)(200+10*t);}
        else{float t=r/0.5f;hR=220;hG=(int)(30+170*t);}
        return 0xFF000000|(hR<<16)|(hG<<8)|0x44;
    }

    private float lerp(float a,float b,float t){return a+(b-a)*t;}
    private double lerp(double a,double b,float t){return a+(b-a)*t;}
    private int safeInt(String s,int d){try{return Integer.parseInt(s.trim());}catch(Exception e){return d;}}
    private void btn(Screen sc,String t,int x,int y,int w,int h,ButtonWidget.PressAction a){Screens.getButtons(sc).add(ButtonWidget.builder(Text.literal(t),a).dimensions(x,y,w,h).build());}
    private boolean isTrash(ItemStack s){return s.isOf(Items.ROTTEN_FLESH)||s.isOf(Items.POISONOUS_POTATO)||s.isOf(Items.DIRT)||s.isOf(Items.COBBLESTONE)||s.isOf(Items.GRAVEL)||s.isOf(Items.SAND);}
    private boolean isArmor(ItemStack s){String n=s.getItem().toString().toLowerCase();return n.contains("helmet")||n.contains("chestplate")||n.contains("leggings")||n.contains("boots");}
            }                               
