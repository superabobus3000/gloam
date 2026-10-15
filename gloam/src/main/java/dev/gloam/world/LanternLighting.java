package dev.gloam.world;
import dev.gloam.Gloam;
import dev.gloam.net.FireflyAreasPayload;
import dev.gloam.util.AtmosphereMath;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import java.util.*;
import java.util.concurrent.*;

/** Loaded chunk relighting only; forest lamp census also drives the 2/3 firefly-site ratio. */
public final class LanternLighting {
    private LanternLighting() {}
    private record Key(ServerLevel level,int x,int z) {}
    private record Work(Key key,int tries) {}
    private static final Queue<Work> queue=new ConcurrentLinkedQueue<>();
    private static final Set<Key> queued=ConcurrentHashMap.newKeySet(),checked=ConcurrentHashMap.newKeySet();
    private static final Map<Key,List<BlockPos>> lights=new ConcurrentHashMap<>();
    private static long tick;
    public static boolean lamp(BlockState s){
        String id=BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
        return id.equals("minecraft:waxed_weathered_copper_lantern")||id.equals("minecraft:weathered_copper_lantern");
    }
    public static void init(){
        ServerChunkEvents.CHUNK_LOAD.register((l,c,g)->enqueue(l,c.getPos().x(),c.getPos().z()));
        ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register((l,c,o,n)->{
            var key=new Key(l,c.getPos().x(),c.getPos().z());
            if(n==FullChunkStatus.INACCESSIBLE){checked.remove(key);lights.remove(key);}
            else enqueue(l,key.x,key.z);
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((l,c)->{var k=new Key(l,c.getPos().x(),c.getPos().z());checked.remove(k);lights.remove(k);});
    }
    private static void enqueue(ServerLevel l,int x,int z){
        if(!l.dimension().equals(Gloam.REALM))return;
        var key=new Key(l,x,z);if(!checked.contains(key)&&queued.add(key))queue.add(new Work(key,0));
    }
    public static void rescan(ServerLevel l,int x,int z){checked.remove(new Key(l,x,z));enqueue(l,x,z);}
    public static String diagnostics(ServerLevel l){
        int n=0,selected=0;
        for(var item:lights.entrySet())if(item.getKey().level==l)for(BlockPos pos:item.getValue()){
            if(!lamp(l.getBlockState(pos))||NaturalVillages.reserved(l,pos)||Gloam.isProtected(l,pos))continue;
            n++;if(AtmosphereMath.fireflySite(AtmosphereMath.cellHash(l.getSeed(),Math.floorDiv(pos.getX(),64),Math.floorDiv(pos.getZ(),64))))selected++;
        }
        return "forest="+n+"; firefly_sites="+selected;
    }
    public static void reset(){queue.clear();queued.clear();checked.clear();lights.clear();tick=0;}
    public static void tick(MinecraftServer server){
        tick++;
        if(tick%4==0){
            Work w=queue.poll();if(w!=null){
                Key k=w.key;var chunk=k.level.getChunkSource().getChunkNow(k.x,k.z);
                if(chunk==null){if(w.tries<100)queue.add(new Work(k,w.tries+1));else queued.remove(k);}
                else{
                    List<BlockPos> found=new ArrayList<>();var sections=chunk.getSections();
                    for(int i=0;i<sections.length;i++){
                        var section=sections[i];if(!section.maybeHas(LanternLighting::lamp))continue;
                        int y0=chunk.getSectionYFromSectionIndex(i)*16;
                        for(int x=0;x<16;x++)for(int z=0;z<16;z++)for(int y=0;y<16;y++)if(lamp(section.getBlockState(x,y,z))){
                            BlockPos pos=new BlockPos(k.x*16+x,y0+y,k.z*16+z);
                            k.level.getChunkSource().getLightEngine().checkBlock(pos);
                            if(!Gloam.isProtected(k.level,pos)&&!NaturalVillages.reserved(k.level,pos))HiddenLights.ensure(k.level,pos);
                            // Only actual forest-window lights, not village/arch decor or underground fixtures.
                            if(Math.floorMod(pos.getX(),64)>=20&&Math.floorMod(pos.getX(),64)<=43&&Math.floorMod(pos.getZ(),64)>=20&&Math.floorMod(pos.getZ(),64)<=43&&
                                !Gloam.isProtected(k.level,pos)&&!NaturalVillages.reserved(k.level,pos)){found.add(pos);}
                        }
                    }
                    lights.put(k,List.copyOf(found));queued.remove(k);checked.add(k);
                }
            }
        }
        if(tick%100!=0)return;
        for(var player:server.getPlayerList().getPlayers()){
            if(!ServerPlayNetworking.canSend(player,FireflyAreasPayload.TYPE))continue;
            List<BlockPos> areas=new ArrayList<>();ServerLevel l=player.level();
            if(l.dimension().equals(Gloam.REALM)){
                int cx=Math.floorDiv(player.blockPosition().getX(),16),cz=Math.floorDiv(player.blockPosition().getZ(),16);
                for(int dx=-4;dx<=4;dx++)for(int dz=-4;dz<=4;dz++){
                    var chunk=l.getChunkSource().getChunkNow(cx+dx,cz+dz);if(chunk==null)continue;
                    for(BlockPos pos:lights.getOrDefault(new Key(l,cx+dx,cz+dz),List.of())){
                        if(areas.size()>=32||!lamp(l.getBlockState(pos))||NaturalVillages.reserved(l,pos)||Gloam.isProtected(l,pos))continue;
                        long h=AtmosphereMath.cellHash(l.getSeed(),Math.floorDiv(pos.getX(),64),Math.floorDiv(pos.getZ(),64));
                        if(!AtmosphereMath.fireflySite(h))continue;
                        int y=l.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ());
                        // Under leaves, the top height can be the log/lantern: use soil below the lamp, not treetops.
                        for(int yy=pos.getY()-1;yy>=Math.max(l.getMinY(),pos.getY()-64);yy--){
                            var soil=l.getBlockState(new BlockPos(pos.getX(),yy,pos.getZ()));
                            if(soil.is(net.minecraft.tags.BlockTags.DIRT)||soil.is(net.minecraft.world.level.block.Blocks.GRASS_BLOCK)||soil.is(net.minecraft.world.level.block.Blocks.PODZOL)||soil.is(net.minecraft.world.level.block.Blocks.MOSS_BLOCK)||soil.is(net.minecraft.world.level.block.Blocks.MUD)||soil.is(net.minecraft.world.level.block.Blocks.PALE_MOSS_BLOCK)){y=yy+1;break;}
                        }
                        areas.add(new BlockPos(pos.getX(),y,pos.getZ()));
                    }
                }
            }
            ServerPlayNetworking.send(player,new FireflyAreasPayload(areas));
        }
    }
}
