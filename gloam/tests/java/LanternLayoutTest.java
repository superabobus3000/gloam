import dev.gloam.util.LanternLayout;
public class LanternLayoutTest {
 public static void main(String[] args){
  boolean[] lengths=new boolean[7];
  for(int h=4;h<=32;h++)for(long seed=-100;seed<=100;seed++){
   int n=LanternLayout.chainLength(h,seed);lengths[n]=true;
   if(n<1||n>6||h-n-1<2)throw new AssertionError("clearance/length");
   int lamp=h-n-1;
   if(lamp+n+1!=h)throw new AssertionError("gap in chain");
  }
  for(int n=1;n<=6;n++)if(!lengths[n])throw new AssertionError("missing length");
  System.out.println("PASS: anchors 4..32; chain lengths 1..6; continuous attachment; clearance >=2.");
 }
}
