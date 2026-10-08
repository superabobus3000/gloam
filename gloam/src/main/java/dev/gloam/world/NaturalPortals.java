package dev.gloam.world;

import dev.gloam.Gloam;
import dev.gloam.util.AtmosphereMath;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Post-generation decoration, not a per-tick world scan. All writes occur on the server thread. */
public final class NaturalPortals {
    private record Job(ServerLevel level,int x,int z,int tries) {}
    private static final ConcurrentLinkedQueue<Job> pending=new ConcurrentLinkedQueue<>();
    private NaturalPortals() {}
    public static void init() {
        ForestLanterns.init();
        ServerChunkEvents.CHUNK_GENERATE.register((level,chunk)->{
            var p=chunk.getPos();
            if(supported(level)&&candidate(level,p.x(),p.z())) pending.add(new Job(level,p.x(),p.z(),0));
        });
    }
    public static void reset(){pending.clear();ForestLanterns.reset();}
    public static boolean supported(ServerLevel l){return l.dimension().equals(Level.OVERWORLD)||l.dimension().equals(Gloam.REALM);}
    public static boolean allowedBiome(ServerLevel l,BlockPos p) {
        String key=l.getUncachedNoiseBiome(p.getX()>>2,p.getY()>>2,p.getZ()>>2).unwrapKey().orElseThrow().identifier().toString();
        String ns=l.dimension().equals(Gloam.REALM)?"gloam:":"minecraft:";
        return key.equals(ns+"old_growth_pine_taiga")||key.equals(ns+"old_growth_spruce_taiga")||key.equals(ns+"pale_garden")||
            (l.dimension().equals(Level.OVERWORLD)&&(key.equals("minecraft:jungle")||key.equals("minecraft:bamboo_jungle")||key.equals("minecraft:sparse_jungle")));
    }
    private static long seed(ServerLevel l){return l.getSeed()^(l.dimension().equals(Gloam.REALM)?0x37a104f2L:0x776f726c64L);}
    public static boolean candidate(ServerLevel l,int cx,int cz){
        int rx=Math.floorDiv(cx,24),rz=Math.floorDiv(cz,24);
        return cx==AtmosphereMath.portalChunk(seed(l),rx,rz,true)&&cz==AtmosphereMath.portalChunk(seed(l),rx,rz,false);
    }
    public static void tick(MinecraftServer server){
        ForestLanterns.tick(server);
        int budget=Math.min(4,pending.size());
        for(int n=0;n<budget;n++) {
            Job j=pending.poll();if(j==null)break;
            if(j.level.getServer()!=server)continue;
            // Generation just finished: the full chunk is already available. Do not load surrounding chunks.
            LevelChunk chunk=j.level.getChunkSource().getChunkNow(j.x,j.z);
            if(chunk==null){
                // CHUNK_GENERATE may fire before the full chunk is published in ServerChunkCache.
                // Retry on later ticks instead of silently losing the decoration; do not load neighbours.
                if(j.tries<200)pending.add(new Job(j.level,j.x,j.z,j.tries+1));
                continue;
            }
            try {
                if(candidate(j.level,j.x,j.z)) generate(j.level,chunk);
            } catch(Exception e){Gloam.LOG.error("Natural decoration failed at {},{}",j.x,j.z,e);}
        }
    }
    public static Gloam.End generate(ServerLevel level,LevelChunk chunk) throws java.io.IOException {
        var cp=chunk.getPos();
        if(!supported(level)||!candidate(level,cp.x(),cp.z()))return null;
        int x=cp.getMinBlockX()+2,z=cp.getMinBlockZ()+6;
        if(Gloam.retired(level,x,z))return null;
        Gloam.End existing=Gloam.knownAt(level,x,z);if(existing!=null)return existing;
        var gen=level.getChunkSource().getGenerator();var random=level.getChunkSource().randomState();
        int h=gen.getBaseHeight(x+1,z+2,Heightmap.Types.OCEAN_FLOOR_WG,level,random);
        if(h<level.getMinY()+8||h+4>=level.getMaxY()||!allowedBiome(level,new BlockPos(x+1,h,z+2)))return null;
        for(int dx:new int[]{0,6,12})for(int dz:new int[]{0,4}) {
            int y=gen.getBaseHeight(x+dx,z+dz,Heightmap.Types.OCEAN_FLOOR_WG,level,random);
            BlockPos surface=new BlockPos(x+dx,h-1,z+dz);
            if(Math.abs(y-h)>2||!level.getFluidState(surface).isEmpty()||!level.getFluidState(surface.above()).isEmpty())return null;
        }
        Gloam.End end=new Gloam.End(level.dimension().identifier().toString(),x,h-7,z);
        for(BlockPos p:BlockPos.betweenClosed(end.origin(),end.origin().offset(12,10,4)))
            if(Gloam.isProtected(level,p)||level.getBlockEntity(p)!=null)return null;
        Gloam.place(level,end);
        Gloam.registerNatural(level.getServer(),end);
        Gloam.LOG.info("Natural gate placed in {} at {}",end.dimension(),end.origin());
        return end;
    }
    /** Admin locator checks deterministic candidate cells, generating at most 8 eligible chunks. */
    public static Gloam.End locate(ServerLevel l,BlockPos near) throws java.io.IOException {
        if(!supported(l))throw new IllegalArgumentException("В этом измерении арки не появляются.");
        int rx=Math.floorDiv(near.getX(),384),rz=Math.floorDiv(near.getZ(),384),loaded=0;
        for(int r=0;r<=10;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++) {
            if(Math.max(Math.abs(dx),Math.abs(dz))!=r)continue;
            int cx=AtmosphereMath.portalChunk(seed(l),rx+dx,rz+dz,true),cz=AtmosphereMath.portalChunk(seed(l),rx+dx,rz+dz,false);
            BlockPos p=new BlockPos(cx*16+3,76,cz*16+8);
            if(!l.getWorldBorder().isWithinBounds(p)||!allowedBiome(l,p))continue;
            if(++loaded>8)throw new IllegalArgumentException("Подходящий участок не найден рядом. Попробуйте из другой точки.");
            Gloam.End e=generate(l,l.getChunk(cx,cz));if(e!=null)return e;
        }
        throw new IllegalArgumentException("Арка не найдена в радиусе поиска. Попробуйте ближе к разрешённым биомам.");
    }
}
