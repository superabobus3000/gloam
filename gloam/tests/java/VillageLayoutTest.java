import dev.gloam.util.VillageLayout;
public class VillageLayoutTest {
 public static void main(String[] args){
  for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++){
   long seed=VillageLayout.seed(20261006,x,z);
   int a=VillageLayout.originChunk(20261006,x,z,true)-x*32;
   int b=VillageLayout.originChunk(20261006,x,z,false)-z*32;
   if(a<6||a>21||b<6||b>21)throw new AssertionError("bounds");
   int adjacent=VillageLayout.originChunk(20261006,x+1,z,true)-VillageLayout.originChunk(20261006,x,z,true);
   if(adjacent<17)throw new AssertionError("overlapping sites");
   if(VillageLayout.houses(seed)<5||VillageLayout.houses(seed)>7)throw new AssertionError("homes");
   if(VillageLayout.residents(seed)<2||VillageLayout.residents(seed)>3)throw new AssertionError("population");
   for(int i=0;i<7;i++)for(int dx=-5;dx<=5;dx++)for(int dz=-5;dz<=5;dz++)if(!VillageLayout.clearing(VillageLayout.homeX(i)+dx,VillageLayout.homeZ(i)+dz,seed))throw new AssertionError("home outside");
  }
  for(int r=0;r<4;r++)for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++){
   int xx=VillageLayout.rotatedX(x,z,r),zz=VillageLayout.rotatedZ(x,z,r);
   if(VillageLayout.rotatedX(xx,zz,4-r)!=x||VillageLayout.rotatedZ(xx,zz,4-r)!=z)throw new AssertionError("rotation");
  }
  System.out.println("VillageLayoutTest PASS: 1681 sites and rotations");
 }
}
