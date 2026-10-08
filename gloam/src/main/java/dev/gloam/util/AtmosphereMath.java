package dev.gloam.util;

/** Pure deterministic math shared with tests; no Minecraft dependencies. */
public final class AtmosphereMath {
    private AtmosphereMath() {}
    public static long mix(long v) {
        v=(v^(v>>>30))*0xbf58476d1ce4e5b9L;
        v=(v^(v>>>27))*0x94d049bb133111ebL;
        return v^(v>>>31);
    }
    public static long cellHash(long seed,int x,int z) { return mix(seed ^ (x*341873128712L) ^ (z*132897987541L)); }
    public static double fog(long tick, float partial) {
        double t=Math.floorMod(tick,7200)+partial;
        if(t>=3000) return 0;
        double a=Math.min(1,Math.min(t/400.0,(3000-t)/600.0));
        return a*a*(3-2*a);
    }
    // One forest lantern per 64-block cell, candidate window [20..43] on both axes.
    // Different cells have at least 64-23=41 blocks separation, even across negative coordinates.
    public static boolean lanternWindowChunk(int cx,int cz) {
        int x=Math.floorMod(cx,4),z=Math.floorMod(cz,4);
        return (x==1||x==2)&&(z==1||z==2);
    }
    public static int portalChunk(long seed,int regionX,int regionZ,boolean xAxis) {
        long h=cellHash(seed,regionX,regionZ);
        return (xAxis?regionX:regionZ)*24+4+(int)Math.floorMod(xAxis?h:mix(h),16);
    }
}
