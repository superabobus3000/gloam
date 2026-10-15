package dev.gloam.util;

/** Deterministic village sites and geometry; no world access or mutable randomness. */
public final class VillageLayout {
    private VillageLayout() {}
    public static final int REGION_CHUNKS=32, SIZE=80, CHUNKS=5;
    private static final int[][] HOMES={{19,18},{43,12},{66,25},{64,51},{44,67},{18,61},{11,39}};
    public static long seed(long seed,int rx,int rz){return AtmosphereMath.cellHash(seed^0x76696c6c616765L,rx,rz);}
    public static int originChunk(long seed,int rx,int rz,boolean x){
        long h=seed(seed,rx,rz);
        return (x?rx:rz)*REGION_CHUNKS+6+(int)Math.floorMod(x?h:AtmosphereMath.mix(h),16);
    }
    public static int houses(long seed){return 5+(int)Math.floorMod(seed,3);}
    public static int residents(long seed){return 2+(int)Math.floorMod(AtmosphereMath.mix(seed),2);}
    public static int homeX(int i){return HOMES[i][0];}
    public static int homeZ(int i){return HOMES[i][1];}
    public static int rotation(int i){
        int dx=40-homeX(i),dz=40-homeZ(i);
        // Local front faces north. Clockwise rotations: north/east/south/west.
        return Math.abs(dx)>Math.abs(dz)?(dx>0?1:3):(dz>0?2:0);
    }
    public static int rotatedX(int x,int z,int rotation){return switch(rotation&3){case 0->x;case 1->-z;case 2->-x;default->z;};}
    public static int rotatedZ(int x,int z,int rotation){return switch(rotation&3){case 0->z;case 1->x;case 2->-z;default->-x;};}
    public static boolean clearing(int x,int z,long seed){
        int dx=x-40,dz=z-40;
        // Softly asymmetric boundary, but houses and roofs always fit inside the clearing.
        double radius=39+Math.sin(Math.atan2(dz,dx)*3+(seed&7))*.3;
        return dx*dx+dz*dz<=radius*radius;
    }
}
