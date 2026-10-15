package dev.gloam.world;

import dev.gloam.Gloam;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.FullChunkStatus;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Repair actual loaded blocks, independently of forest placement/migration indexes. */
public final class LanternChains {
    private LanternChains() {}
    private record Key(ServerLevel level,int x,int z) {}
    private record Work(Key key,int tries) {}
    private static final ConcurrentLinkedQueue<Work> jobs=new ConcurrentLinkedQueue<>();
    private static final Set<Key> queued=ConcurrentHashMap.newKeySet(), checked=ConcurrentHashMap.newKeySet();
    private static long ticks;
    public static int upgradedThisSession;
    private record Permit(ServerLevel level,BlockPos pos) {}
    private static final ThreadLocal<Permit> permit=new ThreadLocal<>();
    public static boolean authorized(ServerLevel level,BlockPos pos,net.minecraft.world.level.block.state.BlockState state){
        Permit p=permit.get();
        if(p==null||p.level!=level||!p.pos.equals(pos))return false;
        var old=level.getBlockState(pos);
        return old.is(Blocks.IRON_CHAIN)&&state.is(copperChain())
            &&old.getValue(BlockStateProperties.AXIS)==state.getValue(BlockStateProperties.AXIS)
            &&old.getValue(BlockStateProperties.WATERLOGGED)==state.getValue(BlockStateProperties.WATERLOGGED);
    }
    public static Block copperChain(){
        Block b=BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:waxed_weathered_copper_chain"));
        if(b==null||b==Blocks.AIR)throw new IllegalStateException("Waxed weathered copper chain is unavailable");
        return b;
    }
    public static void init(){
        ServerChunkEvents.CHUNK_LOAD.register((level,chunk,generated)->enqueue(level,chunk.getPos().x(),chunk.getPos().z()));
        ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register((level,chunk,oldStatus,newStatus)->{
            Key key=new Key(level,chunk.getPos().x(),chunk.getPos().z());
            if(newStatus==FullChunkStatus.INACCESSIBLE)checked.remove(key);
            else enqueue(level,key.x,key.z);
        });
        ServerChunkEvents.CHUNK_UNLOAD.register((level,chunk)->checked.remove(new Key(level,chunk.getPos().x(),chunk.getPos().z())));
    }
    public static void reset(){jobs.clear();queued.clear();checked.clear();ticks=0;upgradedThisSession=0;}
    private static void enqueue(ServerLevel level,int x,int z){
        if(!level.dimension().equals(Gloam.REALM)&&!level.dimension().equals(Level.OVERWORLD))return;
        Key key=new Key(level,x,z);
        if(!checked.contains(key)&&queued.add(key))jobs.add(new Work(key,0));
    }
    public static void tick(MinecraftServer server){
        if(++ticks%2!=0)return;
        Work work=jobs.poll();if(work==null)return;
        Key key=work.key;ServerLevel level=key.level;
        if(level.getServer()!=server){queued.remove(key);return;}
        var chunk=level.getChunkSource().getChunkNow(key.x,key.z);
        if(chunk==null){
            if(work.tries<100)jobs.add(new Work(key,work.tries+1));else queued.remove(key);
            return;
        }
        try{
            Block copper=copperChain();var sections=chunk.getSections();
            for(int i=0;i<sections.length;i++){
                var section=sections[i];if(!section.maybeHas(s->s.is(Blocks.IRON_CHAIN)))continue;
                int y0=chunk.getSectionYFromSectionIndex(i)*16;
                for(int y=0;y<16;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++){
                    var state=section.getBlockState(x,y,z);if(!state.is(Blocks.IRON_CHAIN))continue;
                    BlockPos pos=new BlockPos(key.x*16+x,y0+y,key.z*16+z);
                    if(level.getBlockEntity(pos)!=null)continue;
                    boolean portal=Gloam.isProtected(level,pos);
                    if(!portal&&(!level.dimension().equals(Gloam.REALM)||!aboveLantern(level,pos,copper)))continue;
                    var replacement=copper.defaultBlockState()
                        .setValue(BlockStateProperties.AXIS,state.getValue(BlockStateProperties.AXIS))
                        .setValue(BlockStateProperties.WATERLOGGED,state.getValue(BlockStateProperties.WATERLOGGED));
                    try{
                        if(portal)permit.set(new Permit(level,pos));
                        if(level.setBlock(pos,replacement,Block.UPDATE_ALL))upgradedThisSession++;
                    }finally{permit.remove();}
                }
            }
            checked.add(key);
        }catch(Exception e){Gloam.LOG.error("Chain material upgrade failed at {},{}",key.x,key.z,e);}
        queued.remove(key);
    }
    private static boolean aboveLantern(ServerLevel level,BlockPos pos,Block copper){
        // Same XZ column stays inside this already-loaded chunk. No anchor/window/filled-cell dependency.
        for(int d=1;d<=32&&pos.getY()-d>=level.getMinY();d++){
            var state=level.getBlockState(pos.below(d));
            if(state.is(Blocks.IRON_CHAIN)||state.is(copper))continue;
            String id=BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            return (id.equals("minecraft:waxed_weathered_copper_lantern")||id.equals("minecraft:weathered_copper_lantern"))
                &&state.getValue(BlockStateProperties.HANGING);
        }
        return false;
    }
}
