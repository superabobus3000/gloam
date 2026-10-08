package dev.gloam.mixin;

import dev.gloam.Gloam;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PistonBaseBlock.class)
public abstract class PistonProtectionMixin {
    @Inject(method="isPushable",at=@At("HEAD"),cancellable=true)
    private static void gloam$immovable(BlockState state, Level world, BlockPos pos, Direction direction, boolean destroy, Direction piston, CallbackInfoReturnable<Boolean> cir) {
        if(world instanceof ServerLevel level && (Gloam.isProtected(level,pos) || Gloam.isProtected(level,pos.relative(direction)))) cir.setReturnValue(false);
    }
}
