package dev.gloam.client.mixin;
import dev.gloam.Gloam;
import dev.gloam.util.AtmosphereMath;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FogRenderer.class)
public abstract class FogMixin {
    @Inject(method="setupFog",at=@At("RETURN"))
    private void gloam$mist(Camera camera,int distance,DeltaTracker delta,float darkness,ClientLevel level,CallbackInfoReturnable<FogData> cir){
        if(!level.dimension().equals(Gloam.REALM)||camera.getFluidInCamera()!=FogType.NONE)return;
        var p=Minecraft.getInstance().player;
        if(p==null||p.hasEffect(MobEffects.BLINDNESS)||p.hasEffect(MobEffects.DARKNESS))return;
        float f=(float)AtmosphereMath.fog(level.getGameTime(),delta.getGameTimeDeltaPartialTick(false));
        
        FogData data=cir.getReturnValue();
        float baseline=Math.min(data.environmentalEnd,data.renderDistanceEnd);
        if(!Float.isFinite(baseline))baseline=Math.max(56,distance*16f);
        float end=Math.min(baseline,72-20*f);
        float oldStart=Math.min(data.environmentalStart,data.renderDistanceStart);
        if(!Float.isFinite(oldStart))oldStart=baseline*0.8f;
        float start=Math.min(Math.min(oldStart,10-6*f),Math.max(0,end-1));
        // Respect shorter vanilla/effect distances and never extend visibility.
        data.environmentalEnd=Math.min(data.environmentalEnd,end);
        data.environmentalStart=Math.min(data.environmentalStart,start);
        data.skyEnd=Math.min(data.skyEnd,end);
        data.cloudEnd=Math.min(data.cloudEnd,end);
        // Smoky neutral grey like the supplied forest reference; no old green tint.
        data.color.x=0.26f;data.color.y=0.25f;data.color.z=0.24f;
    }
}
