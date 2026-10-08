package dev.gloam.client;

import dev.gloam.Gloam;
import dev.gloam.net.PortalEffectPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;

/** Server-authoritative charge display, interpolated on the client. No C2S teleport requests. */
public final class GloamClient implements ClientModInitializer {
    private static float previous, strength, target;
    private static int age, arrival, phase, animationTicks;
    public static void reset(){previous=strength=target=0;age=arrival=phase=0;}
    @Override public void onInitializeClient(){
        ClientPlayNetworking.registerGlobalReceiver(PortalEffectPayload.TYPE,(payload,context)->
            context.client().execute(()->{
                int p=payload.phase();if(p<0||p>21)return;
                age=0;phase=p;arrival=p==21?16:0;target=p==21?1:p/20f;
            }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->reset());
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(client.player==null||client.level==null||!client.player.isAlive()){reset();return;}
            if(client.isPaused())return;
            animationTicks++;previous=strength;age++;
            if(arrival>0){arrival--;target=arrival/16f;}
            else if(phase==21||age>30)target=0;
            strength+=Math.clamp(target-strength,-0.16f,0.16f);
            if(strength<0.001f)strength=0;
        });
        HudElementRegistry.addLast(Gloam.id("transition"),GloamClient::draw);
    }
    public static float strength(float partial){
        var mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||!mc.player.isAlive())return 0;
        return previous+(strength-previous)*Math.clamp(partial,0,1);
    }
    public static float motion(float partial){return strength(partial)*Minecraft.getInstance().options.screenEffectScale().get().floatValue();}
    public static float clock(float partial){return animationTicks+partial;}
    private static void draw(GuiGraphicsExtractor g,DeltaTracker delta){
        var mc=Minecraft.getInstance();float partial=delta.getGameTimeDeltaPartialTick(false),p=strength(partial);
        if(p<=0.002f)return;
        int w=g.guiWidth(),h=g.guiHeight();
        int frame=(animationTicks/2)%32;
        int alpha=(int)(p*65);
        g.blit(RenderPipelines.GUI_TEXTURED,Gloam.id("textures/block/portal.png"),0,0,0f,frame*32f,w,h,32,32,32,1024,(alpha<<24)|0xffffff);
        int edge=(int)(p*115)<<24|0x063c37;
        g.fillGradient(0,0,w,Math.max(1,h/3),edge,0x00063c37);
        g.fillGradient(0,h*2/3,w,h,0x00063c37,edge);
        int barWidth=100,left=(w-barWidth)/2,top=h*3/4;
        g.fill(left,top,left+barWidth,top+2,0x90213e3b);
        g.fill(left,top,left+(int)(barWidth*p),top+2,0xd057cab3);
        g.centeredText(mc.font,Component.translatable(phase==21?"gloam.portal.arrived":"gloam.portal.entering"),w/2,top-14,0xffa8e5d7);
    }
}
