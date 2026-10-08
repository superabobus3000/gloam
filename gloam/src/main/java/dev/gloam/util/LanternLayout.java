package dev.gloam.util;

/** Pure layout rules shared by placement and exhaustive geometry tests. */
public final class LanternLayout {
    private LanternLayout() {}
    public static final int MAX_SUPPORT_HEIGHT=32;
    public static int chainLength(int supportHeight,long seed){
        if(supportHeight<4||supportHeight>MAX_SUPPORT_HEIGHT)throw new IllegalArgumentException("Unsupported tree height");
        int max=Math.min(6,supportHeight-3);
        return 1+(int)Math.floorMod(seed,max);
    }
}
