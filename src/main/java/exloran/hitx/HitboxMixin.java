package exloran.hitx.mixin;

import exloran.hitx.HitX;
import exloran.hitx.HitXConfig;
import me.shedaniel.autoconfig.AutoConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HitboxMixin — Entity hitbox boyutlarını hitBoxActive=true iken büyütür.
 *
 * KURULUM (bir kez yapılır):
 *   1) Bu dosya: src/main/java/exloran/hitx/mixin/HitboxMixin.java
 *   2) src/main/resources/hitx.mixins.json içeriği aşağıda
 *   3) fabric.mod.json içine: "mixins": ["hitx.mixins.json"]
 */
@Mixin(Entity.class)
public class HitboxMixin {

    @Inject(
        method      = "getDimensions",
        at          = @At("RETURN"),
        cancellable = true
    )
    private void hitx_expandDimensions(EntityPose pose,
                                        CallbackInfoReturnable<EntityDimensions> cir) {
        if (!HitX.hitBoxActive) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        Entity self = (Entity)(Object) this;

        // Kendi oyuncuyu büyütme
        if (mc.player != null && self == mc.player) return;

        HitXConfig cfg = AutoConfig.getConfigHolder(HitXConfig.class).getConfig();
        EntityDimensions orig = cir.getReturnValue();

        cir.setReturnValue(EntityDimensions.changing(
                orig.width()  * cfg.xzExpand,
                orig.height() * cfg.yExpand
        ));
    }
}
