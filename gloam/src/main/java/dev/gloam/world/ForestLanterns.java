package dev.gloam.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.gloam.Gloam;
import dev.gloam.util.AtmosphereMath;
import dev.gloam.util.LanternLayout;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Independent, idempotent forest decoration on BOTH generation and loading of old chunks. */
public final class ForestLanterns {
    private ForestLanterns() {}
    private record Cell(ServerLevel level,int x,int z) {String key(){return x+","+z;}}
    private record Work(Cell cell,int retries) {}
    private static final ConcurrentLinkedQueue<Work> pending=new ConcurrentLinkedQueue<>();
    private static final Set<Cell> queued=ConcurrentHashMap.newKeySet();
    private static final Set<String> checked=new HashSet<>();
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> FORESTS=Set.of("forest","flower_forest","birch_forest","old_growth_birch_forest",
        "dark_forest","pale_garden","dappled_forest","taiga","old_growth_pine_taiga","old_growth_spruce_taiga",
        "snowy_taiga","swamp","mangrove_swamp","cherry_grove");
    private static final class Index {int format=1;Set<String> filledCells=new TreeSet<>();}
    private static Index index=new Index();
    private static MinecraftServer active;
    private static boolean failed,dirty;
    private static long ticks;
    public static int placedThisSession;
    public static String status(){return "занято ячеек="+index.filledCells.size()+", проверено="+checked.size()+", в очереди="+pending.size();}

    public static void init(){
        ServerChunkEvents.CHUNK_LOAD.register((level,chunk,newChunk)->enqueue(level,chunk));
        ServerChunkEvents.CHUNK_GENERATE.register(ForestLanterns::enqueue);
        // Cached chunks can become accessible again without a CHUNK_LOAD event in 26.3.
        ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register((level,chunk,oldStatus,newStatus)->{
            if(newStatus.ordinal()>=net.minecraft.server.level.FullChunkStatus.FULL.ordinal())enqueue(level,chunk);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server->{if(active==server&&!failed)flush(server);});
    }
    public static void reset(){
        pending.clear();queued.clear();checked.clear();index=new Index();active=null;failed=dirty=false;ticks=0;placedThisSession=0;
    }
    private static void enqueue(ServerLevel level,LevelChunk chunk){
        if(!level.dimension().equals(Gloam.REALM))return;
        var p=chunk.getPos();
        if(!AtmosphereMath.lanternWindowChunk(p.x(),p.z()))return;
        Cell cell=new Cell(level,Math.floorDiv(p.x(),4),Math.floorDiv(p.z(),4));
        if(queued.add(cell))pending.add(new Work(cell,0));
    }
    private static Path file(MinecraftServer server){return server.getWorldPath(LevelResource.ROOT).resolve("data/gloam-lanterns.json");}
    private static void load(MinecraftServer server){
        active=server;
        Path p=file(server);
        if(!Files.exists(p))return;
        try{
            Index read=JSON.fromJson(Files.readString(p),Index.class);
            if(read==null||read.format!=1||read.filledCells==null)throw new IOException("Invalid lantern index");
            index=read;
        }catch(Exception e){failed=true;Gloam.LOG.error("Lantern index cannot be read; decoration paused, file left untouched",e);}
    }
    private static void flush(MinecraftServer server){
        if(!dirty)return;
        try{
            Path p=file(server);Files.createDirectories(p.getParent());Path temp=p.resolveSibling(p.getFileName()+".tmp");
            Files.writeString(temp,JSON.toJson(index));
            try{Files.move(temp,p,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,p,StandardCopyOption.REPLACE_EXISTING);}
            dirty=false;
        }catch(IOException e){Gloam.LOG.error("Cannot save lantern index; will retry",e);}
    }
    public static void tick(MinecraftServer server){
        if(active!=server)load(server);
        ticks++;
        if(failed||Boolean.getBoolean("gloam.test.skipLanterns"))return;
        if(ticks%100==0)flush(server);
        if(ticks%2!=0)return; // At most ten small cells per second, never scan an entire world.
        Work work=pending.poll();if(work==null)return;
        Cell c=work.cell;
        if(c.level.getServer()!=server||index.filledCells.contains(c.key())||checked.contains(c.key())){queued.remove(c);return;}
        LevelChunk[] chunks=new LevelChunk[4];int n=0;
        for(int dx=1;dx<=2;dx++)for(int dz=1;dz<=2;dz++)chunks[n++]=c.level.getChunkSource().getChunkNow(c.x*4+dx,c.z*4+dz);
        if(Arrays.stream(chunks).anyMatch(Objects::isNull)){
            if(work.retries<100)pending.add(new Work(c,work.retries+1));else queued.remove(c);
            return; // Each subsequent CHUNK_LOAD can enqueue the cell again. Never force neighbours to load.
        }
        try{
            // A pre-index (0.3) light must be discovered BEFORE any candidate is placed in its cell.
            boolean existing=hasExisting(c,chunks);
            boolean placed=!existing&&place(c);
            checked.add(c.key());
            if(existing||placed){index.filledCells.add(c.key());dirty=true;}
            if(placed)placedThisSession++;
        }catch(Exception e){Gloam.LOG.error("Forest lighting failed in cell {},{}",c.x,c.z,e);}
        queued.remove(c);
    }
    private static boolean lamp(BlockState state){
        if(state.is(Blocks.LANTERN)||state.is(Blocks.SOUL_LANTERN))return true;
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString().endsWith("copper_lantern");
    }
    private static boolean hasExisting(Cell c,LevelChunk[] chunks){
        int bx=c.x*64+20,bz=c.z*64+20;
        for(LevelChunk chunk:chunks){
            var sections=chunk.getSections();
            for(int si=0;si<sections.length;si++){
                var section=sections[si];if(!section.maybeHas(ForestLanterns::lamp))continue;
                int y0=chunk.getSectionYFromSectionIndex(si)*16;
                for(int x=Math.max(bx,chunk.getPos().getMinBlockX());x<=Math.min(bx+23,chunk.getPos().getMinBlockX()+15);x++)
                    for(int z=Math.max(bz,chunk.getPos().getMinBlockZ());z<=Math.min(bz+23,chunk.getPos().getMinBlockZ()+15);z++)
                        for(int y=0;y<16;y++)if(lamp(section.getBlockState(x&15,y,z&15))&&!Gloam.isProtected(c.level,new BlockPos(x,y0+y,z)))return true;
            }
        }
        return false;
    }
    private static boolean forest(ServerLevel level,BlockPos pos){
        String biome=level.getBiome(pos).unwrapKey().orElseThrow().identifier().toString();
        return FORESTS.contains(biome.substring(biome.indexOf(':')+1));
    }
    private static boolean replaceable(ServerLevel level,BlockPos p){
        var state=level.getBlockState(p);
        return !Gloam.isProtected(level,p)&&level.getBlockEntity(p)==null&&level.getFluidState(p).isEmpty()&&
            (state.canBeReplaced()||state.is(Blocks.SNOW));
    }
    private static boolean place(Cell c){
        ServerLevel level=c.level;int bx=c.x*64+20,bz=c.z*64+20;
        long seed=AtmosphereMath.cellHash(level.getSeed(),c.x,c.z);int start=(int)Math.floorMod(seed,576);
        Block lantern=BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:waxed_weathered_copper_lantern"));
        if(lantern==null||lantern==Blocks.AIR)throw new IllegalStateException("Weathered copper lantern is unavailable");
        BlockPos groundFallback=null;
        for(int n=0;n<576;n++){
            int i=(start+n*205)%576; // coprime permutation over the complete 24x24 window
            int x=bx+i%24,z=bz+i/24;
            int top=Math.min(level.getMaxY()-2,level.getHeight(Heightmap.Types.WORLD_SURFACE,x,z));
            for(int y=top;y>level.getMinY();y--){
                BlockPos floor=new BlockPos(x,y,z);var ground=level.getBlockState(floor);
                if(!(ground.is(BlockTags.DIRT)||ground.is(Blocks.GRASS_BLOCK)||ground.is(Blocks.PODZOL)||ground.is(Blocks.MUD)||ground.is(Blocks.MUDDY_MANGROVE_ROOTS)||ground.is(Blocks.MOSS_BLOCK)||ground.is(Blocks.PALE_MOSS_BLOCK)))continue;
                BlockPos pos=floor.above();
                if(!replaceable(level,pos)||!forest(level,pos))break;
                boolean trunk=false;
                for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++){
                    if(Math.abs(dx)+Math.abs(dz)>2)continue;
                    // The four already-loaded chunks cover this entire halo (18..45 within the cell).
                    var foot=level.getBlockState(pos.offset(dx,0,dz));
                    if(foot.is(BlockTags.LOGS)||foot.is(Blocks.MANGROVE_ROOTS)||foot.is(Blocks.MUDDY_MANGROVE_ROOTS)||level.getBlockState(pos.offset(dx,1,dz)).is(BlockTags.LOGS))trunk=true;
                }
                if(!trunk)break;
                boolean canopy=false;
                for(int h=3;h<=64&&y+h<level.getMaxY();h++)if(level.getBlockState(floor.above(h)).is(BlockTags.LEAVES)){canopy=true;break;}
                if(!canopy)break; // Do not decorate isolated log posts without a tree canopy.
                if((seed&1)==0){
                    if(hang(level,floor,lantern,seed+i))return true;
                    // Keep looking throughout the window before falling back to the ground.
                    if(groundFallback==null&&lantern.defaultBlockState().canSurvive(level,pos))groundFallback=pos;
                }else if(lantern.defaultBlockState().canSurvive(level,pos)){
                    return level.setBlock(pos,lantern.defaultBlockState().setValue(BlockStateProperties.HANGING,false),Block.UPDATE_ALL);
                }
                break;
            }
        }
        return groundFallback!=null&&level.setBlock(groundFallback,lantern.defaultBlockState().setValue(BlockStateProperties.HANGING,false),Block.UPDATE_ALL);
    }
    private static boolean hang(ServerLevel level,BlockPos floor,Block lantern,long seed){
        for(int h=4;h<=LanternLayout.MAX_SUPPORT_HEIGHT&&floor.getY()+h<level.getMaxY();h++){
            BlockPos anchor=floor.above(h);var support=level.getBlockState(anchor);
            if(!(support.is(BlockTags.LOGS)||support.is(BlockTags.LEAVES)))continue;
            if(Gloam.isProtected(level,anchor)||!level.getFluidState(anchor).isEmpty())continue;
            int length=LanternLayout.chainLength(h,seed);
            BlockPos light=anchor.below(length+1);
            boolean clear=true;
            for(int k=0;k<=length;k++){
                BlockPos p=light.above(k);
                if(!level.getBlockState(p).isAir()||!replaceable(level,p)){clear=false;break;}
            }
            if(!clear)continue;
            // Build down from the real anchor, with no gaps, then attach the lantern.
            for(int k=1;k<=length;k++)level.setBlock(anchor.below(k),Blocks.IRON_CHAIN.defaultBlockState(),Block.UPDATE_ALL);
            if(level.setBlock(light,lantern.defaultBlockState().setValue(BlockStateProperties.HANGING,true),Block.UPDATE_ALL))return true;
            for(int k=1;k<=length;k++)level.setBlock(anchor.below(k),Blocks.AIR.defaultBlockState(),Block.UPDATE_ALL);
        }
        return false;
    }
}
