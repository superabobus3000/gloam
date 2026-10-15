package dev.gloam.world;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.gloam.Gloam;
import dev.gloam.util.VillageLayout;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.LevelResource;
import java.nio.file.*;
import java.util.*;

/** Natural towns use only new terrain; the first portal arrival has a bounded nearby starter exception. */
public final class NaturalVillages {
    private NaturalVillages() {}
    private record Site(ServerLevel level,int rx,int rz,Integer explicitX,Integer explicitZ,boolean starter) {
        Site(ServerLevel level,int rx,int rz){this(level,rx,rz,null,null,false);}
        String key(){return starter?"arrival:"+cx()+","+cz():"dense:"+rx+","+rz;}
        int cx(){return explicitX!=null?explicitX:VillageLayout.originChunk(level.getSeed(),rx,rz,true);}
        int cz(){return explicitZ!=null?explicitZ:VillageLayout.originChunk(level.getSeed(),rx,rz,false);}
        long seed(){return VillageLayout.seed(level.getSeed(),rx,rz);}
    }
    public static final class Entry {
        public int x,z,houses,residents,spawned;
        public String stage="building";
        public List<String> villagers=new ArrayList<>();
    }
    private static final class Index {int format=1;Map<String,Entry> villages=new TreeMap<>();Set<String> skipped=new TreeSet<>();Map<String,String> arrivals=new TreeMap<>();Map<String,List<Integer>> firstExits=new TreeMap<>();}
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static Index index=new Index();
    private static final Map<Site,BitSet> fresh=new HashMap<>();
    private static final ArrayDeque<Site> pending=new ArrayDeque<>();
    private static final Set<Site> queued=new HashSet<>();
    private static final Map<Site,Integer> waits=new HashMap<>();
    private static MinecraftServer active;
    private static boolean failed,dirty;
    private static long ticks;
    private static final Map<UUID,Long> failedArrivalAt=new HashMap<>();
    public static int placedThisSession;
    public static void init(){
        ServerChunkEvents.CHUNK_LOAD.register((level,chunk,generated)->enqueue(level,chunk,generated));
        ServerChunkEvents.FULL_CHUNK_STATUS_CHANGE.register((level,chunk,oldStatus,newStatus)->{
            if(newStatus!=net.minecraft.server.level.FullChunkStatus.INACCESSIBLE)enqueue(level,chunk,false);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(s->{if(active==s&&!failed)save(s);});
    }
    private static void enqueue(ServerLevel level,LevelChunk chunk,boolean generated){
        if(!level.dimension().equals(Gloam.REALM))return;
        int x=chunk.getPos().x(),z=chunk.getPos().z();
        Site site=new Site(level,Math.floorDiv(x,VillageLayout.REGION_CHUNKS),Math.floorDiv(z,VillageLayout.REGION_CHUNKS));
        int dx=x-site.cx(),dz=z-site.cz();
        if(dx<0||dx>=5||dz<0||dz>=5)return;
        if(index.villages.containsKey(site.key())||index.skipped.contains(site.key()))return;
        if(generated)fresh.computeIfAbsent(site,k->new BitSet(25)).set(dx*5+dz);
        if(fresh.containsKey(site)){waits.remove(site);if(queued.add(site))pending.add(site);}
    }
    public static void reset(){index=new Index();fresh.clear();pending.clear();queued.clear();waits.clear();active=null;failed=dirty=false;ticks=0;placedThisSession=0;failedArrivalAt.clear();}
    private static Path path(MinecraftServer server){return server.getWorldPath(LevelResource.ROOT).resolve("data/gloam-villages.json");}
    private static void load(MinecraftServer server){
        active=server;
        if(!Files.exists(path(server)))return;
        try{
            var raw=JSON.fromJson(Files.readString(path(server)),com.google.gson.JsonObject.class);
            if(raw==null||!raw.has("format")||!raw.has("villages")||!raw.has("skipped"))throw new IllegalStateException("Missing village index fields");
            Index read=JSON.fromJson(raw,Index.class);
            if(read==null||read.format!=1||read.villages==null||read.skipped==null)throw new IllegalStateException("Invalid village index");
            for(var e:read.villages.values())if(e==null||e.stage==null||e.villagers==null||e.houses<5||e.houses>7||e.residents<2||e.residents>3||e.spawned<0||e.spawned>e.residents)throw new IllegalStateException("Invalid village entry");
            if(read.arrivals==null)read.arrivals=new TreeMap<>();
            if(read.firstExits==null)read.firstExits=new TreeMap<>();
            for(var point:read.firstExits.values())if(point==null||point.size()!=3||point.contains(null))throw new IllegalStateException("Invalid first exit");
            index=read;
        }catch(Exception e){failed=true;Gloam.LOG.error("Cannot read village index: generation disabled, file untouched",e);}
    }
    private static boolean save(MinecraftServer server){
        if(!dirty)return true;
        try{
            Path p=path(server);Files.createDirectories(p.getParent());Path temp=p.resolveSibling(p.getFileName()+".tmp");
            Files.writeString(temp,JSON.toJson(index));
            try{Files.move(temp,p,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(temp,p,StandardCopyOption.REPLACE_EXISTING);}
            dirty=false;return true;
        }catch(Exception e){failed=true;Gloam.LOG.error("Village index write failed: no further generation",e);return false;}
    }
    public static boolean reserved(ServerLevel level,BlockPos pos){
        if(!level.dimension().equals(Gloam.REALM)||failed)return false;
        // Coordinates, not old grid keys: legacy64-region villages remain reserved after grid change.
        for(Entry e:index.villages.values()){
            int dx=pos.getX()-e.x-40,dz=pos.getZ()-e.z-40;
            if(dx*dx+dz*dz<=1600&&pos.getX()>=e.x&&pos.getX()<e.x+80&&pos.getZ()>=e.z&&pos.getZ()<e.z+80)return true;
        }
        int rx=Math.floorDiv(pos.getX(),VillageLayout.REGION_CHUNKS*16),rz=Math.floorDiv(pos.getZ(),VillageLayout.REGION_CHUNKS*16);
        Site site=new Site(level,rx,rz);
        if(index.skipped.contains(site.key())||!fresh.containsKey(site))return false;
        int x=pos.getX()-site.cx()*16,z=pos.getZ()-site.cz()*16;
        return x>=0&&x<80&&z>=0&&z<80&&VillageLayout.clearing(x,z,site.seed());
    }
    public static long count(){return index.villages.values().stream().filter(e->e.stage.equals("done")).count();}
    public static String listing(){return JSON.toJson(index.villages);}
    public static String candidates(ServerLevel level,BlockPos near){
        List<Map<String,Object>> out=new ArrayList<>();
        int rx=Math.floorDiv(near.getX(),VillageLayout.REGION_CHUNKS*16),rz=Math.floorDiv(near.getZ(),VillageLayout.REGION_CHUNKS*16);
        for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
            int x=VillageLayout.originChunk(level.getSeed(),rx+dx,rz+dz,true)*16;
            int z=VillageLayout.originChunk(level.getSeed(),rx+dx,rz+dz,false)*16;
            out.add(Map.of("x",x,"z",z,"centerX",x+40,"centerZ",z+40,"region",(rx+dx)+","+(rz+dz),"status",index.villages.containsKey("dense:"+(rx+dx)+","+(rz+dz))?index.villages.get("dense:"+(rx+dx)+","+(rz+dz)).stage:index.skipped.contains("dense:"+(rx+dx)+","+(rz+dz))?"skipped":"candidate"));
        }
        return JSON.toJson(out);
    }
    public static void tick(MinecraftServer server){
        if(active!=server)load(server);
        if(failed)return;
        ticks++;
        if(ticks%100==0)save(server);
        if(ticks%5!=0||pending.isEmpty())return;
        Site site=pending.poll();queued.remove(site);
        if(index.villages.containsKey(site.key())||index.skipped.contains(site.key())){fresh.remove(site);return;}
        if(fresh.getOrDefault(site,new BitSet()).cardinality()!=25)return;
        LevelChunk[] chunks=new LevelChunk[25];int n=0;
        for(int dx=0;dx<5;dx++)for(int dz=0;dz<5;dz++){
            var c=site.level.getChunkSource().getChunkNow(site.cx()+dx,site.cz()+dz);
            if(c==null){if(waits.merge(site,1,Integer::sum)<=100&&queued.add(site))pending.add(site);return;}
            chunks[n++]=c;
        }
        int bx=site.cx()*16,bz=site.cz()*16;
        try{
            int[][] heights=preflight(site,chunks);
            if(heights==null){index.skipped.add(site.key());dirty=true;fresh.remove(site);return;}
            build(site,heights);
        }catch(Exception ex){
            Entry e=index.villages.get(site.key());if(e!=null){e.stage="interrupted";dirty=true;save(server);}
            Gloam.LOG.error("Village generation failed at {},{}; no automatic duplicate retry",bx,bz,ex);
        }
        fresh.remove(site);
    }
    private static void build(Site site,int[][] heights){
        MinecraftServer server=site.level.getServer();int bx=site.cx()*16,bz=site.cz()*16;
            Entry e=new Entry();e.x=bx;e.z=bz;e.houses=VillageLayout.houses(site.seed());e.residents=VillageLayout.residents(site.seed());
            index.villages.put(site.key(),e);dirty=true;
            // Persist intent before block writes; restart never generates a second settlement/population.
            if(!save(server))return;
            if(site.starter)flattenStarter(site,heights);
            VillageBuilder.build(site.level,bx,bz,site.seed(),heights,e.houses);
            e.stage="spawning";dirty=true;if(!save(server))return;
            for(int i=0;i<e.residents;i++){
                int home=i==1?1:0;
                BlockPos pos=VillageBuilder.spawn(bx,bz,heights,home,i);
                var villager=EntityTypes.VILLAGER.spawn(site.level,pos,EntitySpawnReason.STRUCTURE);
                if(villager==null)throw new IllegalStateException("Villager spawn returned null");
                villager.setPersistenceRequired();villager.addTag("gloam_village");villager.addTag("gloam_village_"+site.rx+"_"+site.rz);
                e.villagers.add(villager.getUUID().toString());e.spawned++;
            }
            e.stage="done";dirty=true;save(server);placedThisSession++;
            Gloam.LOG.info("Gloam village at {},{}: {} houses, {} villagers",bx+40,bz+40,e.houses,e.spawned);
    }
    /** Ensures a nearby village before the player's first portal arrival. Reuses an existing settlement. */
    public static boolean ensureArrival(ServerLevel level,BlockPos exit,UUID player){
        if(!level.dimension().equals(Gloam.REALM))return true;
        if(active!=level.getServer())load(level.getServer());
        if(failed)return false;
        String uuid=player.toString();
        if(index.arrivals.containsKey(uuid))return true;
        if(!index.firstExits.containsKey(uuid)){
            index.firstExits.put(uuid,List.of(exit.getX(),exit.getY(),exit.getZ()));dirty=true;if(!save(level.getServer()))return false;
        }
        var remembered=index.firstExits.get(uuid);exit=new BlockPos(remembered.get(0),remembered.get(1),remembered.get(2));
        Long last=failedArrivalAt.get(player);if(last!=null&&level.getGameTime()-last<600)return false;
        for(var item:index.villages.entrySet()){
            Entry e=item.getValue();long dx=(long)e.x+40-exit.getX(),dz=(long)e.z+40-exit.getZ();
            if(e.stage.equals("done")&&dx*dx+dz*dz<=10000){index.arrivals.put(uuid,item.getKey());dirty=true;return save(level.getServer());}
        }
        int[][] offsets={{80,0},{0,80},{-80,0},{0,-80},{56,56},{-56,56},{56,-56},{-56,-56}};
        for(int[] offset:offsets){
            int cx=(int)Math.round((exit.getX()+offset[0]-40)/16.0),cz=(int)Math.round((exit.getZ()+offset[1]-40)/16.0);
            Site site=new Site(level,cx,cz,cx,cz,true);
            long dx=(long)cx*16+40-exit.getX(),dz=(long)cz*16+40-exit.getZ();
            if(dx*dx+dz*dz>10000||index.villages.containsKey(site.key()))continue;
            if(overlaps(cx*16,cz*16)||Gloam.hasProtectedFootprint(level,cx*16,cz*16,cx*16+79,cz*16+79))continue;
            // Bounded starter exception: up to eight nearby footprints, only on the first portal entry.
            LevelChunk[] chunks=new LevelChunk[25];int n=0;
            for(int x=0;x<5;x++)for(int z=0;z<5;z++)chunks[n++]=level.getChunk(cx+x,cz+z);
            int[][] heights=preflight(site,chunks);if(heights==null)continue;
            try{
                build(site,heights);
                Entry e=index.villages.get(site.key());
                if(e!=null&&e.stage.equals("done")){index.arrivals.put(uuid,site.key());dirty=true;return save(level.getServer());}
            }catch(Exception ex){
                Entry e=index.villages.get(site.key());if(e!=null)e.stage="interrupted";
                dirty=true;save(level.getServer());Gloam.LOG.error("Starter village failed",ex);return false;
            }
        }
        failedArrivalAt.put(player,level.getGameTime());
        Gloam.LOG.warn("No safe starter village within100blocks of {}: first exit remembered, village not marked built",exit);
        return false;
    }
    private static boolean overlaps(int x,int z){
        for(Entry e:index.villages.values())if(e.x-16<x+80&&e.x+96>x&&e.z-16<z+80&&e.z+96>z)return true;
        return false;
    }
    private static void flattenStarter(Site site,int[][] heights){
        int y=heights[40][40];
        for(int x=0;x<80;x++)for(int z=0;z<80;z++)if(heights[x][z]!=0&&site.level.getFluidState(new BlockPos(site.cx()*16+x,heights[x][z]-1,site.cz()*16+z)).is(net.minecraft.tags.FluidTags.WATER))y=Math.max(y,heights[x][z]);
        for(int x=0;x<80;x++)for(int z=0;z<80;z++)if(heights[x][z]!=0){
            double radius=Math.hypot(x-40,z-40),blend=Math.clamp((radius-33)/6,0,1);
            boolean house=false;
            for(int i=0;i<VillageLayout.houses(site.seed());i++)if(Math.abs(x-VillageLayout.homeX(i))<=5&&Math.abs(z-VillageLayout.homeZ(i))<=5)house=true;
            int target=house?y:(int)Math.round(y+(heights[x][z]-y)*blend);
            for(int h=heights[x][z]-1;h<target-1;h++)site.level.setBlock(new BlockPos(site.cx()*16+x,h,site.cz()*16+z),Blocks.DIRT.defaultBlockState(),2);
            heights[x][z]=target;
        }
    }
    private static boolean soil(net.minecraft.world.level.block.state.BlockState s){
        return s.is(BlockTags.DIRT)||s.is(Blocks.GRASS_BLOCK)||s.is(Blocks.PODZOL)||s.is(Blocks.MOSS_BLOCK)||s.is(Blocks.PALE_MOSS_BLOCK);
    }
    private static int[][] reject(Site site,String reason){Gloam.LOG.info("Village site {},{} skipped: {}",site.rx,site.rz,reason);return null;}
    private static int[][] preflight(Site site,LevelChunk[] chunks){
        ServerLevel level=site.level;int bx=site.cx()*16,bz=site.cz()*16;
        if(overlaps(bx,bz))return reject(site,"existing village");
        if(!level.getWorldBorder().isWithinBounds(new BlockPos(bx,80,bz))||!level.getWorldBorder().isWithinBounds(new BlockPos(bx+79,80,bz+79)))return reject(site,"world border");
        if(Gloam.hasProtectedFootprint(level,bx,bz,bx+79,bz+79))return reject(site,"protected arch");
        for(var c:chunks)if(!c.getBlockEntitiesPos().isEmpty()||(!site.starter&&c.getInhabitedTime()>1200))return reject(site,"inhabited or block entity");
        int[][] heights=new int[80][80];int low=10000,high=-10000;
        for(int x=0;x<80;x++)for(int z=0;z<80;z++){
            if(!VillageLayout.clearing(x,z,site.seed()))continue;
            int top=level.getHeight(Heightmap.Types.WORLD_SURFACE,bx+x,bz+z),floor=Integer.MIN_VALUE;
            for(int y=top;y>=Math.max(level.getMinY(),top-128);y--){
                var p=new BlockPos(bx+x,y,bz+z);var state=level.getBlockState(p);
                if(soil(state)||(site.starter&&state.is(Blocks.STONE)&&y<=level.getChunkSource().getGenerator().getBaseHeight(bx+x,bz+z,Heightmap.Types.OCEAN_FLOOR_WG,level,level.getChunkSource().randomState()))){floor=y;break;}
                if(site.starter&&!state.isAir()&&!state.is(BlockTags.LOGS)&&!state.is(BlockTags.LEAVES)&&!state.canBeReplaced()&&!LanternLighting.lamp(state)&&!state.is(Blocks.SWEET_BERRY_BUSH)&&!state.is(BlockTags.FLOWERS)&&!state.is(Blocks.BROWN_MUSHROOM)&&!state.is(Blocks.RED_MUSHROOM)&&!state.is(Blocks.MOSSY_COBBLESTONE)&&!state.is(Blocks.LEAF_LITTER)&&!state.is(Blocks.MOSS_CARPET)&&!state.is(Blocks.PALE_MOSS_CARPET)&&!HiddenLights.owns(level,p))return reject(site,"non-natural surface block "+net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock())+" at "+p);
            }
            if(floor==Integer.MIN_VALUE||floor<50||floor>96)return reject(site,"soil or height");
            var p=new BlockPos(bx+x,floor+1,bz+z);
            if(!level.getFluidState(p).isEmpty()){
                if(!site.starter||!level.getFluidState(p).is(net.minecraft.tags.FluidTags.WATER))return reject(site,"water");
                // Starter exception: turn small natural pond cells into dry land, never leave a water-filled town.
                int water=floor+1;
                while(water<floor+24&&level.getFluidState(new BlockPos(bx+x,water,bz+z)).is(net.minecraft.tags.FluidTags.WATER))water++;
                floor=water-1;
            }
            String biome=level.getBiome(p).unwrapKey().orElseThrow().identifier().toString();
            if(biome.endsWith(":swamp")||biome.endsWith(":mangrove_swamp"))return reject(site,"swamp");
            heights[x][z]=floor+1;low=Math.min(low,floor);high=Math.max(high,floor);
        }
        if(high-low>(site.starter?24:12))return reject(site,"overall slope");
        if(!site.starter)for(int i=0;i<VillageLayout.houses(site.seed());i++){
            int x=VillageLayout.homeX(i),z=VillageLayout.homeZ(i),y=heights[x][z];
            for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++)if(heights[x+dx][z+dz]==0||Math.abs(heights[x+dx][z+dz]-y)>3)return reject(site,"house slope or clearing edge");
        }
        return heights;
    }
}
