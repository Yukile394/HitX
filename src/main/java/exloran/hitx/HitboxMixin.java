package exloran.hitx.mixin;

import exloran.hitx.HitX;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HitboxMixin — hitBoxActive=true iken düşman entity hitbox'larını
 * HitX.hitboxXZ (genişlik) ve HitX.hitboxY (yükseklik) kadar büyütür.
 *
 * KURULUM:
 *   1. Bu dosyayı  src/main/java/exloran/hitx/mixin/HitboxMixin.java  koy
 *   2. src/main/resources/hitx.mixins.json  içeriğini aşağıdaki gibi ayarla
 *   3. fabric.mod.json  içine  "mixins": ["hitx.mixins.json"]  ekle
 */
@Mixin(Entity.class)
public class HitboxMixin {

    @Inject(
        method  = "getDimensions",
        at      = @At("RETURN"),
        cancellable = true
    )
    private void hitx_expandHitbox(EntityPose pose,
                                    CallbackInfoReturnable<EntityDimensions> cir) {
        if (!HitX.hitBoxActive) return;

        // Kendi oyuncumuzu büyütme
        MinecraftClient mc = MinecraftClient.getInstance();
        Entity self = (Entity)(Object) this;
        if (mc.player != null && self == mc.player) return;

        EntityDimensions orig = cir.getReturnValue();
        cir.setReturnValue(EntityDimensions.changing(
                orig.width()  + HitX.hitboxXZ,
                orig.height() + HitX.hitboxY
        ));
    }
}
