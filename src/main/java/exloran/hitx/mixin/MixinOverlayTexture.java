package exloran.hitx.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import exloran.hitx.HitXConfig;
import exloran.hitx.listener.OverlayReloadListener;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(OverlayTexture.class)
public abstract class MixinOverlayTexture implements OverlayReloadListener {
    @Shadow
    @Final
    private NativeImageBackedTexture texture;

    @Inject(method = "<init>", at = @At("TAIL"))
    public void modifyHitColor(CallbackInfo ci) {
        this.reloadOverlay();
        OverlayReloadListener.register(this);
    }

    @Override
    public void onOverlayReload() {
        this.reloadOverlay();
    }

    private static int getColorInt(int red, int green, int blue, int alpha) {
        alpha = 255 - alpha;
        return (alpha << 24) + (blue << 16) + (green << 8) + red;
    }

    public void reloadOverlay() {
        NativeImage nativeImage = this.texture.getImage();
        if (nativeImage == null) return;

        // Config'i çağırıyoruz
        HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();

        for (int i = 0; i < 16; ++i) {
            for (int j = 0; j < 16; ++j) {
                if (i < 8) {
                    if (cfg.hitColorActive) {
                        nativeImage.setColor(j, i, getColorInt(cfg.hcRed, cfg.hcGreen, cfg.hcBlue, cfg.hcAlpha));
                    } else {
                        // Varsayılan Minecraft Kırmızı Efekti
                        nativeImage.setColor(j, i, -1308622593);
                    }
                }
            }
        }

        RenderSystem.activeTexture(33985);
        this.texture.bindTexture();
        nativeImage.upload(0, 0, 0, 0, 0, nativeImage.getWidth(), nativeImage.getHeight(), false, true, false, false);
        RenderSystem.activeTexture(33984);
    }
}
