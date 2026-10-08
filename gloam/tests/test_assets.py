"""Offline asset integrity checks; not a substitute for Minecraft playtesting."""
import json, unittest
from pathlib import Path
import nbtlib
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
class Assets(unittest.TestCase):
 def test_all_json_parse(self):
  for path in list(ROOT.rglob('*.json'))+list(ROOT.rglob('*.mcmeta')):
   with self.subTest(path=str(path)): json.loads(path.read_text())
 def test_original_template_dimensions(self):
  p=nbtlib.load(ROOT/'data/gloam/structure/stone_portal.nbt')
  self.assertEqual(list(p['size']),[13,11,5]);self.assertEqual(len(p['blocks']),715)
 def test_portal_wall_and_landing(self):
  p=nbtlib.load(ROOT/'data/gloam/structure/stone_portal.nbt')
  a={tuple(map(int,b['pos'])):str(p['palette'][int(b['state'])]['id']) for b in p['blocks']}
  for y in (2,3,4):
   self.assertEqual(a[11,y,2],'minecraft:chiseled_stone_bricks')
   self.assertEqual(a[10,y,2],'minecraft:air')
   self.assertEqual(a[10,y,1],'minecraft:mossy_stone_brick_wall')
   self.assertEqual(a[10,y,3],'minecraft:mossy_stone_brick_wall')
  for y in (2,3): self.assertEqual(a[9,y,2],'minecraft:air')
  self.assertEqual(a[9,1,2],'minecraft:stone_slab')
 def test_portal_geometry_and_dynamic_height(self):
  source=(ROOT.parents[2]/'src/main/java/dev/gloam/Gloam.java').read_text()
  self.assertIn('offset(4,-7,-2)',source)
  self.assertIn('RealmPlacement.find(target,id)',source)
  self.assertIn('offset(10,y,2)',source)
  self.assertNotIn('offset(11,y,2)',source)
 def test_texture_frames(self):
  with Image.open(ROOT/'assets/gloam/textures/block/portal.png') as im:
   self.assertEqual(im.size,(32,1024));self.assertEqual(im.height//im.width,32)
 def test_no_overworld_override(self):
  self.assertFalse((ROOT/'data/minecraft').exists())
  d=json.loads((ROOT/'data/gloam/dimension_type/realm.json').read_text())
  self.assertEqual(d['timelines'],[])
if __name__=='__main__': unittest.main()
