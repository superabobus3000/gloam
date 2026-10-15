package dev.gloam.world;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.gloam.Gloam;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;

/** Owned invisible vanilla light nodes near ground, not a fake level above Minecraft's 15 limit. */
public final class HiddenLights {
    private HiddenLights() {}
    private static final class Index {int format=1;Map<String,Set<String>> lamps=new TreeMap<>();Set<String> dead=new TreeSet<>();}
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static Index index=new Index();
    private static final Set<String> owned=new HashSet<>();
    private static final Map<String,String> owner=new HashMap<>();
    private static MinecraftServer active;
    private static boolean failed,dirty;
    private static long ticks;
    private static final int LIGHT_LEVEL=11;
    private static final int LEGACY_LIGHT_LEVEL=14;
    private static final int[][] OFFSETS={{0,0},{8,0},{-8,0},{0,8},{0,-8}};
    private static String key(BlockPos p){return p.getX()+","+p.getY()+","+p.getZ();}
    private static BlockPos pos(String key){String[] v=key.split(",");if(v.length!=3)throw new IllegalArgumentException("Light position");return new BlockPos(Integer.parseInt(v[0]),Integer.parseInt(v[1]),Integer.parseInt(v[2]));}
    private static Path path(MinecraftServer s){return s.getWorldPath(LevelResource.ROOT).resolve("data/gloam-lights.json");}
    public static void init(){ServerLifecycleEvents.SERVER_STOPPING.register(s->{if(active==s&&!failed)save(s);});}
    public static void reset(){index=new Index();owned.clear();owner.clear();active=null;failed=dirty=false;ticks=0;}
    private static void load(MinecraftServer s){
        active=s;
        if(!Files.exists(path(s)))return;
        try{
            var raw=JSON.fromJson(Files.readString(path(s)),com.google.gson.JsonObject.class);
            if(raw==null||!raw.has("format")||!raw.has("lamps"))throw new IllegalArgumentException("Missing light index fields");
            Index read=JSON.fromJson(raw,Index.class);
            if(read==null||read.format!=1||read.lamps==null)throw new IllegalArgumentException("Invalid light index");
            for(var e:read.lamps.entrySet()){
                pos(e.getKey());if(e.getValue()==null||e.getValue().size()>5)throw new IllegalArgumentException("Invalid light halo");
                for(String p:e.getValue()){pos(p);if(!owned.add(p))throw new IllegalArgumentException("Shared light owner");owner.put(p,e.getKey());}
            }
            if(read.dead==null)read.dead=new TreeSet<>();
            index=read;
        }catch(Exception ex){owned.clear();owner.clear();failed=true;Gloam.LOG.error("Hidden light index damaged; not overwriting it",ex);}
    }
    private static boolean save(MinecraftServer s){
        if(failed)return false;if(!dirty)return true;
        try{
            Path p=path(s),tmp=p.resolveSibling("gloam-lights.json.tmp");Files.createDirectories(p.getParent());Files.writeString(tmp,JSON.toJson(index));
            try{Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException ex){Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}
            dirty=false;return true;
        }catch(Exception ex){failed=true;Gloam.LOG.error("Hidden light index write failed; stopped",ex);return false;}
    }
    private static boolean loaded(ServerLevel l,BlockPos p){return l.getChunkSource().getChunkNow(p.getX()>>4,p.getZ()>>4)!=null;}
    private static boolean node(ServerLevel l,BlockPos p){var s=l.getBlockState(p);return s.is(Blocks.LIGHT)&&(s.getValue(LightBlock.LEVEL)==LIGHT_LEVEL||s.getValue(LightBlock.LEVEL)==LEGACY_LIGHT_LEVEL)&&!s.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED);}
    private static void soften(ServerLevel l,Set<String> points){
        if(points==null)return;
        for(String point:new ArrayList<>(points)){
            BlockPos p=pos(point);
            if(!loaded(l,p)||Gloam.isProtected(l,p))continue;
            var state=l.getBlockState(p);
            if(node(l,p)&&state.getValue(LightBlock.LEVEL)==LEGACY_LIGHT_LEVEL)
                l.setBlock(p,state.setValue(LightBlock.LEVEL,LIGHT_LEVEL),2);
        }
    }
    private static int ground(ServerLevel l,int x,int z,int nearY){
        if(!loaded(l,new BlockPos(x,nearY,z)))return Integer.MIN_VALUE;
        for(int y=Math.min(l.getMaxY()-3,nearY+12);y>=Math.max(l.getMinY(),nearY-64);y--){
            var s=l.getBlockState(new BlockPos(x,y,z));
            if(s.is(BlockTags.DIRT)||s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.PODZOL)||s.is(Blocks.MOSS_BLOCK)||s.is(Blocks.PALE_MOSS_BLOCK)||s.is(Blocks.MUD)||s.is(Blocks.MUDDY_MANGROVE_ROOTS))return y;
        }
        return Integer.MIN_VALUE;
    }
    public static boolean owns(ServerLevel l,BlockPos p){return !failed&&active==l.getServer()&&owned.contains(key(p))&&node(l,p);}
    public static void ensure(ServerLevel l,BlockPos lamp){
        if(!l.dimension().equals(Gloam.REALM))return;
        if(active!=l.getServer())load(l.getServer());
        if(failed||!loaded(l,lamp)||!LanternLighting.lamp(l.getBlockState(lamp))||Gloam.isProtected(l,lamp)||NaturalVillages.reserved(l,lamp))return;
        soften(l,index.lamps.get(key(lamp)));
        int floor=ground(l,lamp.getX(),lamp.getZ(),lamp.getY()-1);if(floor==Integer.MIN_VALUE)return;
        String anchor=key(lamp);Set<String> points=index.lamps.computeIfAbsent(anchor,k->new TreeSet<>());
        List<BlockPos> planned=new ArrayList<>();
        for(int[] offset:OFFSETS){
            int x=lamp.getX()+offset[0],z=lamp.getZ()+offset[1];int y=ground(l,x,z,floor);
            if(y==Integer.MIN_VALUE||Math.abs(y-floor)>8)continue;
            BlockPos p=new BlockPos(x,y+2,z);String target=key(p);
            if(!l.getWorldBorder().isWithinBounds(p)||Gloam.isProtected(l,p)||NaturalVillages.reserved(l,p)||!l.getFluidState(p).isEmpty())continue;
            if(points.contains(target))continue;
            if(points.size()>=5||owned.contains(target)||!l.getBlockState(p).isAir())continue;
            points.add(target);owned.add(target);owner.put(target,anchor);planned.add(p);dirty=true;
        }
        // Persist the whole bounded intent once, rather than five full-file writes for each lamp.
        if(!planned.isEmpty()){
            if(!save(l.getServer()))return;
            for(BlockPos p:planned)if(!l.setBlock(p,Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL,LIGHT_LEVEL),2)){
                String target=key(p);points.remove(target);owned.remove(target);owner.remove(target);dirty=true;
            }
        }
        if(points.isEmpty()){index.lamps.remove(anchor);dirty=true;}
    }
    /** Called after successful setBlock as well as by loaded-chunk maintenance: commands/explosions too. */
    public static void changed(ServerLevel l,BlockPos lamp){
        if(!l.dimension().equals(Gloam.REALM)||failed||active!=l.getServer())return;
        String changed=key(lamp);
        if(owned.contains(changed)&&(!node(l,lamp)||l.getBlockState(lamp).getValue(LightBlock.LEVEL)!=LIGHT_LEVEL)){
            String source=owner.remove(changed);owned.remove(changed);
            Set<String> points=index.lamps.get(source);if(points!=null){points.remove(changed);if(points.isEmpty()){index.lamps.remove(source);index.dead.remove(source);}}dirty=true;
        }
        if(!index.lamps.containsKey(changed)||LanternLighting.lamp(l.getBlockState(lamp)))return;
        index.dead.add(key(lamp));dirty=true;remove(l,key(lamp));
    }
    private static void remove(ServerLevel l,String lamp){
        Set<String> points=index.lamps.get(lamp);if(points==null)return;
        for(String point:new ArrayList<>(points)){
            BlockPos p=pos(point);if(!loaded(l,p))continue;
            // Leave player replacements alone. Only delete this mod's recorded current/legacy light node.
            if(node(l,p))l.setBlock(p,Blocks.AIR.defaultBlockState(),2);
            points.remove(point);owned.remove(point);owner.remove(point);dirty=true;
        }
        if(points.isEmpty()){index.lamps.remove(lamp);index.dead.remove(lamp);dirty=true;}
    }
    public static void tick(MinecraftServer server){
        if(active!=server)load(server);
        if(failed)return;ticks++;
        if(ticks%100==0)save(server);
        if(ticks%20!=0)return;
        ServerLevel l=server.getLevel(Gloam.REALM);if(l==null)return;
        // Work only on nearby loaded records; no force load and no full-world block scans.
        int budget=16;
        for(String anchor:new ArrayList<>(index.lamps.keySet())){
            BlockPos p=pos(anchor);if(index.dead.contains(anchor)){remove(l,anchor);continue;}
            // Revisit real owned nodes on every load: no persistent migration-done flag.
            soften(l,index.lamps.get(anchor));if(!loaded(l,p))continue;
            if(!LanternLighting.lamp(l.getBlockState(p))||NaturalVillages.reserved(l,p)){index.dead.add(anchor);dirty=true;remove(l,anchor);continue;}
            if(budget-->0)ensure(l,p);
        }
    }
}
