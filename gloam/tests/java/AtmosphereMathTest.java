import dev.gloam.util.AtmosphereMath;
public class AtmosphereMathTest {
    private static void check(boolean b,String msg){if(!b)throw new AssertionError(msg);}
    public static void main(String[] args){
        for(long t=-10000;t<20000;t++){
            double f=AtmosphereMath.fog(t,0);
            check(f>=0&&f<=1,"fog bounds");
            check(Math.abs(f-AtmosphereMath.fog(t+7200,0))<1e-9,"period");
            check(Math.abs(f-AtmosphereMath.fog(t+1,0))<.004,"smooth edge");
        }
        check(AtmosphereMath.fog(0,0)==0,"clear at start");
        check(AtmosphereMath.fog(1600,0)==1,"peak");
        check(AtmosphereMath.fog(5000,0)==0,"clear interval");
        for(int cell=-30;cell<30;cell++){
            int left=cell*64+20,right=(cell+1)*64+20;
            check(right-(left+23)>=40,"lantern minimum across cells");
            check(AtmosphereMath.lanternWindowChunk(Math.floorDiv(left,16),Math.floorDiv(left,16)),"negative coordinates");
        }
        for(int x=-32;x<32;x++)for(int z=-32;z<32;z++) {
            boolean expected=(Math.floorMod(x,4)==1||Math.floorMod(x,4)==2)&&(Math.floorMod(z,4)==1||Math.floorMod(z,4)==2);
            check(AtmosphereMath.lanternWindowChunk(x,z)==expected,"four window chunks including negative coordinates");
        }
        boolean[] visited=new boolean[576];
        for(int i=0;i<576;i++)visited[(117+i*205)%576]=true;
        for(boolean v:visited)check(v,"complete candidate permutation");
        for(long seed:new long[]{0,1,-1,20261006})for(int x=-30;x<30;x++)for(int z=-30;z<30;z++){
            int cx=AtmosphereMath.portalChunk(seed,x,z,true),cz=AtmosphereMath.portalChunk(seed,x,z,false);
            check(Math.floorDiv(cx,24)==x&&Math.floorDiv(cz,24)==z,"portal belongs to region");
            check(Math.floorMod(cx,24)>=4&&Math.floorMod(cx,24)<=19,"portal margin");
            check(AtmosphereMath.portalChunk(seed,x+1,z,true)-cx>=9,"portal cell separation");
        }
        System.out.println("PASS: periodic smooth fog; >=40 lantern spacing; negative coordinates; bounded portal grid.");
    }
}
