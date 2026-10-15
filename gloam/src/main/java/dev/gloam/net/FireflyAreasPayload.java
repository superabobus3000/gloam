package dev.gloam.net;
import dev.gloam.Gloam;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import java.util.List;
import java.util.ArrayList;
/** Bounded, server-selected firefly locations; purely visual, no new entities or world blocks. */
public record FireflyAreasPayload(List<BlockPos> areas) implements CustomPacketPayload {
    public static final Type<FireflyAreasPayload> TYPE=new Type<>(Gloam.id("firefly_areas"));
    public static final StreamCodec<RegistryFriendlyByteBuf,FireflyAreasPayload> CODEC=StreamCodec.of(
        (buf,p)->{if(p.areas.size()>32)throw new IllegalArgumentException("Too many firefly areas");buf.writeVarInt(p.areas.size());for(BlockPos pos:p.areas)buf.writeBlockPos(pos);},
        buf->{int n=buf.readVarInt();if(n<0||n>32)throw new IllegalArgumentException("Invalid firefly area count");List<BlockPos> out=new ArrayList<>();for(int i=0;i<n;i++)out.add(buf.readBlockPos());return new FireflyAreasPayload(List.copyOf(out));});
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
