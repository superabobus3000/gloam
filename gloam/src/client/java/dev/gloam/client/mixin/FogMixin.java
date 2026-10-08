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
        if(f<=0)return;
        FogData data=cir.getReturnValue();
        float baseline=Math.min(data.environmentalEnd,data.renderDistanceEnd);
        if(!Float.isFinite(baseline))baseline=Math.max(56,distance*16f);
        float end=baseline+(Math.min(baseline,56)-baseline)*f;
        float oldStart=Math.min(data.environmentalStart,data.renderDistanceStart);
        if(!Float.isFinite(oldStart))oldStart=baseline*0.8f;
        float start=oldStart+(Math.min(oldStart,8)-oldStart)*f;
        // Respect shorter vanilla/effect distances and never extend visibility.
        data.environmentalEnd=Math.min(data.environmentalEnd,end);
        data.environmentalStart=Math.min(data.environmentalStart,start);
        data.skyEnd=Math.min(data.skyEnd,end);
        data.cloudEnd=Math.min(data.cloudEnd,end);
        data.color.x+=(0.065f-data.color.x)*f*0.6f;
        data.color.y+=(0.115f-data.color.y)*f*0.6f;
        data.color.z+=(0.105f-data.color.z)*f*0.6f;
    }
}
