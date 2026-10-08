package dev.gloam.mixin;

import dev.gloam.Gloam;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Protects the complete saved volume, including the air in the passage. */
@Mixin(Level.class)
public abstract class LevelProtectionMixin {
    @Inject(method="destroyBlock(Lnet/minecraft/core/BlockPos;ZLnet/minecraft/world/entity/Entity;I)Z", at=@At("HEAD"), cancellable=true)
    private void gloam$preventDrops(BlockPos pos, boolean drops, net.minecraft.world.entity.Entity entity, int recursion, CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof ServerLevel level && Gloam.isProtected(level,pos)) cir.setReturnValue(false);
    }
    @Inject(method="setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z", at=@At("HEAD"), cancellable=true)
    private void gloam$protect(BlockPos pos, BlockState state, int flags, int recursion, CallbackInfoReturnable<Boolean> cir) {
        if ((Object)this instanceof ServerLevel level && Gloam.isProtected(level, pos)) cir.setReturnValue(false);
    }
}
