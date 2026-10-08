package dev.gloam.net;
import dev.gloam.Gloam;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
/** 0=cancel; 1..20=authoritative charge; 21=arrived. Server -> client only. */
public record PortalEffectPayload(int phase) implements CustomPacketPayload {
    public static final Type<PortalEffectPayload> TYPE=new Type<>(Gloam.id("portal_effect"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PortalEffectPayload> CODEC=StreamCodec.of(
        (buf,p)->buf.writeVarInt(p.phase),buf->new PortalEffectPayload(buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type(){ return TYPE; }
}
