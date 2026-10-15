"""Source/architecture checks complement (do not replace) dedicated runtime tests."""
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[1]/'src/main/java/dev/gloam'
def source(name):return (ROOT/name).read_text()
class Villages(unittest.TestCase):
 def test_new_terrain_only_and_no_forced_neighbours(self):
  s=source('world/NaturalVillages.java')
  self.assertIn('if(generated)fresh',s)
  self.assertIn('cardinality()!=25',s)
  self.assertIn('getChunkNow',s)
  self.assertNotIn('.getChunk(',s[s.index('public static void tick('):s.index('private static void build(')])
  self.assertIn('Bounded starter exception',s)
  self.assertIn('level.dimension().equals(Gloam.REALM)',s)
 def test_save_before_build_and_no_respawn(self):
  s=source('world/NaturalVillages.java')
  self.assertLess(s.index('if(!save(server))return;'),s.index('VillageBuilder.build('))
  self.assertIn('index.villages.containsKey(site.key())',s)
  self.assertIn('ATOMIC_MOVE',s)
  self.assertIn('file untouched',s)
  self.assertNotIn('EntityRemove',s)
 def test_preflight_preserves_arches_and_old_sites(self):
  s=source('world/NaturalVillages.java')
  self.assertIn('Gloam.hasProtectedFootprint',s)
  self.assertIn('getBlockEntitiesPos()',s)
  self.assertIn('getInhabitedTime()',s)
  self.assertIn('index.skipped.add',s)
  self.assertIn('"water"',s)
  self.assertIn('":swamp"',s)
 def test_population_and_house_rules(self):
  s=source('util/VillageLayout.java')
  self.assertIn('return 5+',s)
  self.assertIn('return 2+',s)
  self.assertIn('REGION_CHUNKS=32',s)
  s=source('world/VillageBuilder.java')
  self.assertIn('boolean lived=i<2',s)
  self.assertIn('int beds=i==0?2:1',s)
  self.assertIn('BedPart.FOOT',s)
  self.assertIn('BedPart.HEAD',s)
  self.assertIn('Blocks.COMPOSTER',s)
 def test_decor_excluded_and_no_old_worldgen_changes(self):
  s=source('world/ForestLanterns.java')
  self.assertGreaterEqual(s.count('NaturalVillages.reserved'),2)
  self.assertIn('NaturalVillages.reserved',source('world/NaturalPortals.java'))
  self.assertNotIn('WORLD_SURFACE',source('util/VillageLayout.java'))
if __name__=='__main__':unittest.main()
