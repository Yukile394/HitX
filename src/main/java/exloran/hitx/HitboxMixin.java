package exloran.hitx.mixin;

import exloran.hitx.HitX;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * HitboxMixin — Entity hitbox'larını HitX.hitBoxActive aktifken
 * HitX.hitboxSize kadar genişletir.
 *
 * fabric.mod.json → mixins → ["hitx.mixins.json"] ekle.
 * hitx.mixins.json içeriği:
 * {
 *   "required": true,
 *   "package": "exloran.hitx.mixin",
 *   "compatibilityLevel": "JAVA_17",
 *   "client": ["HitboxMixin"],
 *   "injectors": { "defaultRequire": 1 }
 * }
 */
@Mixin(Entity.class)
public class HitboxMixin {

    @Inject(
        method = "getDimensions",
        at = @At("RETURN"),
        cancellable = true
    )
    private void expandHitbox(EntityPose pose,
                               CallbackInfoReturnable<EntityDimensions> cir) {
        if (!HitX.hitBoxActive) return;

        Entity self = (Entity)(Object) this;

        // Kendi oyuncumuzu büyütme
        net.minecraft.client.MinecraftClient mc =
                net.minecraft.client.MinecraftClient.getInstance();
        if (mc.player != null && self == mc.player) return;

        EntityDimensions orig = cir.getReturnValue();
        float extra = HitX.hitboxSize;

        // Genişletilmiş boyut
        EntityDimensions expanded = EntityDimensions.changing(
                orig.width()  + extra,
                orig.height() + extra
        );
        cir.setReturnValue(expanded);
    }
}
