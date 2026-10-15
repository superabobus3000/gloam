package dev.gloam.client;
import dev.gloam.Gloam;
import dev.gloam.util.AtmosphereMath;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import java.util.List;

/** Small intermittent clusters of vanilla full-bright firefly particles, not persistent mob entities. */
public final class Fireflies {
    private Fireflies() {}
    private static List<BlockPos> areas=List.of();
    private static int age=200,clock;
    public static void receive(List<BlockPos> positions){areas=List.copyOf(positions);age=0;}
    public static void reset(){areas=List.of();age=200;clock=0;}
    public static void tick(Minecraft mc){
        if(mc.level==null||mc.player==null||!mc.level.dimension().equals(Gloam.REALM)){reset();return;}
        if(mc.isPaused())return;
        age++;clock++;
        if(age>150||clock%8!=0||!mc.level.getFluidState(mc.player.blockPosition()).isEmpty())return;
        int budget=8;long time=mc.level.getGameTime();
        for(BlockPos p:areas){
            if(budget<=0||p.distSqr(mc.player.blockPosition())>56*56||!mc.level.hasChunk(p.getX()>>4,p.getZ()>>4))continue;
            long seed=AtmosphereMath.cellHash(0,p.getX(),p.getZ());
            if(!AtmosphereMath.firefliesActive(time,seed))continue;
            var random=mc.level.getRandom();
            double x=p.getX()+.5+(random.nextDouble()-.5)*8,z=p.getZ()+.5+(random.nextDouble()-.5)*8,y=p.getY()+.6+random.nextDouble()*2.4;
            BlockPos at=BlockPos.containing(x,y,z);
            if(!mc.level.hasChunk(at.getX()>>4,at.getZ()>>4)||!mc.level.getBlockState(at).isAir())continue;
            mc.level.addParticle(ParticleTypes.FIREFLY,x,y,z,(random.nextDouble()-.5)*.035,random.nextDouble()*.014,(random.nextDouble()-.5)*.035);
            budget--;
        }
    }
}
