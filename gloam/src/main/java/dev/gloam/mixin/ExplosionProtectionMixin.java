package dev.gloam.mixin;

import dev.gloam.Gloam;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Remove protected positions before loot and destructive explosion callbacks. */
@Mixin(ServerExplosion.class)
public abstract class ExplosionProtectionMixin {
    @Inject(method="calculateExplodedPositions",at=@At("RETURN"),cancellable=true)
    private void gloam$filter(CallbackInfoReturnable<List<BlockPos>> cir) {
        var level=((ServerExplosion)(Object)this).level();
        var filtered=new ArrayList<>(cir.getReturnValue());
        filtered.removeIf(pos -> Gloam.isProtected(level,pos));
        cir.setReturnValue(filtered);
    }
}
