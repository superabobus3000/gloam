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
        // Permanent haze; only a very gentle change in density, never a clear interval.
        return .9-.1*Math.cos(t*(Math.PI*2/7200));
    }
    public static boolean fireflySite(long hash){return Math.floorMod(hash,4)!=0;}
    public static boolean firefliesActive(long tick,long hash){return Math.floorMod(tick+Math.floorMod(hash,1000),1000)<400;}
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
