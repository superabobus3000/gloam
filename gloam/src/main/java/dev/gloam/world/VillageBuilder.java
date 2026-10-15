package dev.gloam.world;

import dev.gloam.util.AtmosphereMath;
import dev.gloam.util.VillageLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.*;
import net.minecraft.world.level.levelgen.Heightmap;

/** Small, deterministic, weathered hamlet. Two habitable houses; remaining houses are ruins. */
public final class VillageBuilder {
    private VillageBuilder() {}
    private static BlockState light(boolean hanging){
        Block b=BuiltInRegistries.BLOCK.getValue(Identifier.parse("minecraft:waxed_weathered_copper_lantern"));
        if(b==null||b==Blocks.AIR)throw new IllegalStateException("Village lantern unavailable");
        return b.defaultBlockState().setValue(BlockStateProperties.HANGING,hanging);
    }
    private static void put(ServerLevel l,int x,int y,int z,BlockState state){
        int flags=Block.UPDATE_CLIENTS;
        // Place multipart beds/doors without intermediate shape updates before both halves exist.
        if(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock||state.getBlock() instanceof net.minecraft.world.level.block.DoorBlock)flags|=Block.UPDATE_KNOWN_SHAPE;
        if(!l.setBlock(new BlockPos(x,y,z),state,flags)&&!l.getBlockState(new BlockPos(x,y,z)).equals(state))
            throw new IllegalStateException("Village write refused at "+x+","+y+","+z);
    }
    private static void put(ServerLevel l,int x,int y,int z,Block b){put(l,x,y,z,b.defaultBlockState());}
    private static BlockPos local(int bx,int bz,int y,int home,int x,int dy,int z){
        int r=VillageLayout.rotation(home);
        return new BlockPos(bx+VillageLayout.homeX(home)+VillageLayout.rotatedX(x,z,r),y+dy,bz+VillageLayout.homeZ(home)+VillageLayout.rotatedZ(x,z,r));
    }
    private static void homeBlock(ServerLevel l,int bx,int bz,int y,int home,int x,int dy,int z,BlockState state){
        BlockPos p=local(bx,bz,y,home,x,dy,z);put(l,p.getX(),p.getY(),p.getZ(),state);
    }
    private static Direction direction(Direction d,int rotation){for(int i=0;i<rotation;i++)d=d.getClockWise();return d;}
    public static BlockPos spawn(int bx,int bz,int[][] heights,int home,int resident){
        int y=heights[VillageLayout.homeX(home)][VillageLayout.homeZ(home)];
        return local(bx,bz,y,home,resident==2?1:-1,0,-1);
    }
    public static void build(ServerLevel level,int bx,int bz,long seed,int[][] heights,int count){
        // Clear vegetation only within the village; keep the original gently rolling ground.
        for(int x=0;x<80;x++)for(int z=0;z<80;z++){
            int y=heights[x][z];if(y==0)continue;
            int top=Math.min(level.getMaxY()-1,level.getHeight(Heightmap.Types.WORLD_SURFACE,bx+x,bz+z));
            for(int yy=top;yy>=y;yy--)put(level,bx+x,yy,bz+z,Blocks.AIR);
            long h=AtmosphereMath.cellHash(seed,x,z);
            // Small patches, not an entirely flat platform or an empty plains biome.
            put(level,bx+x,y-1,bz+z,(h&31)==0?Blocks.COARSE_DIRT:Blocks.GRASS_BLOCK);
        }
        // Radial, slightly worn footpaths to each inhabited/ruined building.
        for(int i=0;i<count;i++){
            int r=VillageLayout.rotation(i);
            int endX=VillageLayout.homeX(i)+VillageLayout.rotatedX(0,-6,r);
            int endZ=VillageLayout.homeZ(i)+VillageLayout.rotatedZ(0,-6,r);
            int steps=Math.max(Math.abs(endX-40),Math.abs(endZ-40));
            for(int k=0;k<=steps;k++){
                int x=40+(endX-40)*k/Math.max(1,steps),z=40+(endZ-40)*k/Math.max(1,steps);
                for(int dx=-1;dx<=1;dx++)for(int dz=-1;dz<=1;dz++){
                    int xx=x+dx,zz=z+dz,y=heights[xx][zz];if(y==0)continue;
                    put(level,bx+xx,y-1,bz+zz,(AtmosphereMath.cellHash(seed,xx,zz)&7)==0?Blocks.COARSE_DIRT:Blocks.DIRT_PATH);
                }
            }
        }
        for(int i=0;i<count;i++)house(level,bx,bz,seed,heights,i);
        well(level,bx,bz,heights);
        farm(level,bx,bz,heights);
        for(int[] q:new int[][]{{32,28},{52,37},{36,53},{24,43}}){
            int x=q[0],z=q[1],y=heights[x][z];
            put(level,bx+x,y,bz+z,Blocks.COBBLESTONE_WALL);
            put(level,bx+x,y+1,bz+z,Blocks.SPRUCE_FENCE);
            put(level,bx+x,y+2,bz+z,light(false));
        }
    }
    private static void house(ServerLevel l,int bx,int bz,long seed,int[][] ground,int i){
        int cx=VillageLayout.homeX(i),cz=VillageLayout.homeZ(i),y=ground[cx][cz],r=VillageLayout.rotation(i);
        boolean lived=i<2;
        for(int x=-4;x<=4;x++)for(int z=-5;z<=5;z++){
            int wx=cx+VillageLayout.rotatedX(x,z,r),wz=cz+VillageLayout.rotatedZ(x,z,r);
            int original=ground[wx][wz];
            for(int yy=original;yy<y-1;yy++)put(l,bx+wx,yy,bz+wz,Blocks.COBBLESTONE);
            for(int yy=y;yy<original;yy++)put(l,bx+wx,yy,bz+wz,Blocks.AIR);
            if(Math.abs(x)<=3&&Math.abs(z)<=4){
                homeBlock(l,bx,bz,y,i,x,-1,z,(lived?Blocks.SPRUCE_PLANKS:((x+z+i)%3==0?Blocks.MOSSY_COBBLESTONE:Blocks.COBBLESTONE)).defaultBlockState());
            }
        }
        for(int x=-3;x<=3;x++)for(int z=-4;z<=4;z++){
            if(Math.abs(x)!=3&&Math.abs(z)!=4)continue;
            for(int h=0;h<=4;h++){
                if(z==-4&&x==0&&h<2)continue;
                if((Math.abs(x)==3&&z==0||Math.abs(z)==4&&Math.abs(x)==1)&&h>=1&&h<=2){
                    if(lived)homeBlock(l,bx,bz,y,i,x,h,z,Blocks.GLASS_PANE.defaultBlockState());
                    continue;
                }
                long hash=AtmosphereMath.cellHash(seed+i*997+x,h*71,z);
                if(!lived&&h>0&&Math.floorMod(hash,10)<5)continue;
                Block b=h==0?((hash&1)==0?Blocks.MOSSY_COBBLESTONE:Blocks.COBBLESTONE):
                    (Math.abs(x)==3&&Math.abs(z)==4?Blocks.SPRUCE_LOG:Blocks.SPRUCE_PLANKS);
                homeBlock(l,bx,bz,y,i,x,h,z,b.defaultBlockState());
            }
        }
        for(int x=-4;x<=4;x++)for(int z=-5;z<=5;z++){
            int roof=8-Math.abs(x);
            long hash=AtmosphereMath.cellHash(seed+i*113,x,z);
            if(!lived&&(Math.floorMod(hash,10)<6||x>1&&z>0))continue;
            BlockState b=x==0?Blocks.DARK_OAK_SLAB.defaultBlockState():Blocks.DARK_OAK_STAIRS.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING,direction(x<0?Direction.EAST:Direction.WEST,r));
            homeBlock(l,bx,bz,y,i,x,roof,z,b);
            if(Math.abs(z)==4&&Math.abs(x)<=3)for(int h=5;h<roof;h++)homeBlock(l,bx,bz,y,i,x,h,z,Blocks.SPRUCE_PLANKS.defaultBlockState());
        }
        if(lived){
            Direction front=direction(Direction.NORTH,r);
            var door=Blocks.SPRUCE_DOOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,front);
            homeBlock(l,bx,bz,y,i,0,0,-4,door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.LOWER));
            homeBlock(l,bx,bz,y,i,0,1,-4,door.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,DoubleBlockHalf.UPPER));
            // Full-block entrance apron prevents a three-block foundation from trapping the inhabitants.
            int ex=cx+VillageLayout.rotatedX(0,-5,r),ez=cz+VillageLayout.rotatedZ(0,-5,r);
            for(int step=0;step<4;step++){
                int xx=ex+front.getStepX()*step,zz=ez+front.getStepZ()*step;
                int target=y-1+Math.max(-step,Math.min(step,ground[xx][zz]-y));
                for(int fill=ground[xx][zz]-1;fill<=target;fill++)put(l,bx+xx,fill,bz+zz,Blocks.COBBLESTONE);
                for(int yy=target+1;yy<=Math.max(target+2,ground[xx][zz]+1);yy++)put(l,bx+xx,yy,bz+zz,Blocks.AIR);
            }
            int beds=i==0?2:1;
            for(int k=0;k<beds;k++){
                int x=k==0?-1:1;
                var bed=Blocks.BED.brown().defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING,direction(Direction.SOUTH,r));
                homeBlock(l,bx,bz,y,i,x,0,1,bed.setValue(BlockStateProperties.BED_PART,BedPart.FOOT));
                homeBlock(l,bx,bz,y,i,x,0,2,bed.setValue(BlockStateProperties.BED_PART,BedPart.HEAD));
            }
            homeBlock(l,bx,bz,y,i,-2,0,-2,Blocks.CRAFTING_TABLE.defaultBlockState());
            homeBlock(l,bx,bz,y,i,2,0,2,Blocks.BARREL.defaultBlockState());
            homeBlock(l,bx,bz,y,i,2,0,-2,Blocks.SPRUCE_PLANKS.defaultBlockState());
            homeBlock(l,bx,bz,y,i,2,1,-2,light(false));
        }else{
            homeBlock(l,bx,bz,y,i,-2,1,3,Blocks.COBWEB.defaultBlockState());
            homeBlock(l,bx,bz,y,i,2,0,2,Blocks.MOSS_BLOCK.defaultBlockState());
            homeBlock(l,bx,bz,y,i,-1,0,0,Blocks.SPRUCE_SLAB.defaultBlockState());
            homeBlock(l,bx,bz,y,i,2,0,-2,Blocks.MOSSY_COBBLESTONE.defaultBlockState());
        }
    }
    private static void well(ServerLevel l,int bx,int bz,int[][] ground){
        int y=ground[40][40];
        for(int x=38;x<=42;x++)for(int z=38;z<=42;z++){
            for(int yy=ground[x][z]-1;yy<=y-1;yy++)put(l,bx+x,yy,bz+z,Blocks.COBBLESTONE);
            put(l,bx+x,y,bz+z,x==40&&z==40?Blocks.WATER:Blocks.MOSSY_COBBLESTONE);
            put(l,bx+x,y+1,bz+z,Blocks.AIR);
        }
        for(int x:new int[]{38,42})for(int h=1;h<=3;h++)put(l,bx+x,y+h,bz+40,Blocks.SPRUCE_FENCE);
        for(int x=37;x<=43;x++)for(int z=39;z<=41;z++)put(l,bx+x,y+4,bz+z,Blocks.DARK_OAK_SLAB);
        put(l,bx+40,y+3,bz+40,LanternChains.copperChain().defaultBlockState());
        put(l,bx+40,y+2,bz+40,light(true));
    }
    private static void farm(ServerLevel l,int bx,int bz,int[][] ground){
        int y=ground[49][51];
        for(int x=46;x<=52;x++)for(int z=48;z<=54;z++){
            for(int yy=ground[x][z]-1;yy<y-1;yy++)put(l,bx+x,yy,bz+z,Blocks.DIRT);
            for(int yy=y;yy<=Math.max(y,ground[x][z]);yy++)put(l,bx+x,yy,bz+z,Blocks.AIR);
            if(x==46||x==52||z==48||z==54)put(l,bx+x,y-1,bz+z,Blocks.SPRUCE_LOG);
            else if(x==49&&z==51)put(l,bx+x,y-1,bz+z,Blocks.WATER);
            else {
                put(l,bx+x,y-1,bz+z,Blocks.FARMLAND.defaultBlockState().setValue(BlockStateProperties.MOISTURE,7));
                put(l,bx+x,y,bz+z,Blocks.WHEAT.defaultBlockState().setValue(BlockStateProperties.AGE_7,(x+z)%8));
            }
        }
        put(l,bx+46,y,bz+48,Blocks.COMPOSTER);
    }
}
