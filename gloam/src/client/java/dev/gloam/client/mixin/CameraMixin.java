package dev.gloam.client.mixin;
import dev.gloam.client.GloamClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Inject(method="extractRenderState",at=@At("TAIL"))
    private void gloam$swim(CameraRenderState state,DeltaTracker delta,CallbackInfo ci){
        float partial=delta.getGameTimeDeltaPartialTick(false),p=GloamClient.motion(partial);
        if(p<=0||state.isPanoramicMode)return;
        float wave=(float)Math.sin(GloamClient.clock(partial)*0.24)*0.018f*p;
        // Small smooth anisotropic zoom, no alteration of aim/player rotation or game physics.
        // Both scales >=1: keeps the rendered view inside the original culling frustum.
        state.projectionMatrix.scale(1+0.035f*p+wave,1+0.035f*p-wave,1);
    }
}
