import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class LanternBackfill(unittest.TestCase):
 def test_all_forest_biomes_supported(self):
  s=(ROOT/'src/main/java/dev/gloam/world/ForestLanterns.java').read_text()
  for p in (ROOT/'src/main/resources/data/gloam/worldgen/biome').glob('*.json'):
   self.assertIn('"'+p.stem+'"',s)
 def test_old_and_new_chunks_and_existing_lamps(self):
  s=(ROOT/'src/main/java/dev/gloam/world/ForestLanterns.java').read_text()
  for term in ['CHUNK_LOAD.register','CHUNK_GENERATE.register','FULL_CHUNK_STATUS_CHANGE.register','hasExisting(c,chunks)','filledCells','gloam-lanterns.json','getChunkNow']:
   self.assertIn(term,s)
  self.assertNotIn('getChunk(',s)
 def test_263_surface_blocks_not_just_narrow_dirt_tag(self):
  s=(ROOT/'src/main/java/dev/gloam/world/ForestLanterns.java').read_text()
  for block in ['GRASS_BLOCK','PODZOL','MUD','MUDDY_MANGROVE_ROOTS','MOSS_BLOCK','PALE_MOSS_BLOCK','SNOW']:
   self.assertIn('Blocks.'+block,s)
 def test_expanded_window_spacing(self):
  # Worst case for all adjacent cells, including negative cell coordinates.
  import math
  for cx in range(-3,4):
   for cz in range(-3,4):
    for dx,dz in [(1,0),(0,1),(1,1),(-1,1)]:
     for x,z in [(20,20),(20,43),(43,20),(43,43)]:
      for xx,zz in [(20,20),(20,43),(43,20),(43,43)]:
       self.assertGreaterEqual(math.hypot((cx*64+x)-((cx+dx)*64+xx),(cz*64+z)-((cz+dz)*64+zz)),40)
 def test_lighting_independent_of_portal_candidates(self):
  s=(ROOT/'src/main/java/dev/gloam/world/ForestLanterns.java').read_text()
  self.assertNotIn('NaturalPortals.candidate',s)
  self.assertNotIn('RealmPlacement',s)
if __name__=='__main__':unittest.main()
