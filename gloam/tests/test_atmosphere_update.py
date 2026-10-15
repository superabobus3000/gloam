from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]/'src'
def main(p):return (ROOT/'main/java/dev/gloam'/p).read_text()
class Update(unittest.TestCase):
 def test_permanent_smoky_fog(self):
  s=(ROOT/'client/java/dev/gloam/client/mixin/FogMixin.java').read_text()
  self.assertIn('data.color.x=0.26f',s)
  self.assertIn('FogType.NONE',s)
  self.assertIn('MobEffects.BLINDNESS',s)
  self.assertIn('MobEffects.DARKNESS',s)
  self.assertIn('72-20*f',s)
 def test_real_halo_not_noop_emission(self):
  s=main('world/HiddenLights.java')
  for p in ['Blocks.LIGHT','LIGHT_LEVEL=11','ATOMIC_MOVE','owned.contains','isAir()','getChunkNow','index.dead','points.add(target)']:
   self.assertIn(p,s)
  self.assertNotIn('.getChunk(',s)
  self.assertLess(s.index('if(!save(l.getServer()))return;'),s.index('for(BlockPos p:planned)if(!l.setBlock(p,'))
  self.assertIn('HiddenLights.changed',main('mixin/LevelProtectionMixin.java'))
  self.assertFalse((ROOT/'main/java/dev/gloam/mixin/LanternLightMixin.java').exists())
 def test_legacy_halos_revisited_without_done_flags(self):
  s=main('world/HiddenLights.java')
  self.assertIn('LEGACY_LIGHT_LEVEL=14',s)
  self.assertIn('soften(l,index.lamps.get(anchor))',s)
  self.assertIn('state.setValue(LightBlock.LEVEL,LIGHT_LEVEL)',s)
  self.assertIn('if(!loaded(l,p)||Gloam.isProtected(l,p))continue',s)
  self.assertNotIn('migrationDone',s)
 def test_arrival_hook_and_legacy_reservation(self):
  s=main('Gloam.java')
  self.assertLess(s.index('NaturalVillages.ensureArrival(destination,feet,uuid)'),s.index('player.teleport(new TeleportTransition'))
  s=main('world/NaturalVillages.java')
  for p in ['index.arrivals','"dense:"','"arrival:"','<=10000','dx*dx+dz*dz>10000','for(Entry e:index.villages.values())','Blocks.BROWN_MUSHROOM','Blocks.MOSSY_COBBLESTONE']:
   self.assertIn(p,s)
 def test_bounded_server_authoritative_fireflies(self):
  s=main('net/FireflyAreasPayload.java')
  self.assertIn('n>32',s)
  self.assertIn('List.copyOf(out)',s)
  s=main('world/LanternLighting.java')
  self.assertIn('AtmosphereMath.fireflySite',s)
  self.assertIn('NaturalVillages.reserved',s)
  self.assertNotIn('.getChunk(',s)
  s=(ROOT/'client/java/dev/gloam/client/Fireflies.java').read_text()
  for p in ['ParticleTypes.FIREFLY','int budget=8','age>150','hasChunk','Gloam.REALM','firefliesActive']:
   self.assertIn(p,s)
if __name__=='__main__':unittest.main()
