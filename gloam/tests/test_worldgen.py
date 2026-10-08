"""26.3 data invariants. Integration results require a real dedicated server."""
import json, unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
def read(p):return json.loads((ROOT/p).read_text())
WORLD='data/gloam/worldgen/'
ALLOWED={'forest','flower_forest','birch_forest','old_growth_birch_forest','dark_forest','pale_garden','dappled_forest','taiga','old_growth_pine_taiga','old_growth_spruce_taiga','snowy_taiga','swamp','mangrove_swamp','cherry_grove'}
class Worldgen(unittest.TestCase):
 def test_biome_whitelist(self):
  d=read('data/gloam/dimension/realm.json')['generator']
  self.assertEqual(d['type'],'minecraft:noise')
  self.assertEqual(d['settings'],'gloam:realm')
  self.assertEqual({b['biome'] for b in d['biome_source']['biomes']},{'gloam:'+b for b in ALLOWED})
  self.assertEqual({p.stem for p in (ROOT/WORLD/'biome').glob('*.json')},ALLOWED)
 def test_no_canyons_or_dungeon_features(self):
  forbidden={'minecraft:monster_room','minecraft:monster_room_deep','minecraft:fossil_upper','minecraft:fossil_lower','minecraft:lake_lava_surface','minecraft:lake_lava_underground','minecraft:spring_lava','minecraft:spring_water'}
  for b in ALLOWED:
   d=read(WORLD+'biome/'+b+'.json')
   self.assertEqual(d['carvers'],[])
   self.assertFalse(forbidden.intersection(f for step in d['features'] for f in step))
 def test_no_vanilla_structure_tags_or_overrides(self):
  self.assertFalse((ROOT/'data/minecraft').exists())
  self.assertFalse((ROOT/WORLD/'structure').exists())
  self.assertFalse((ROOT/WORLD/'structure_set').exists())
  for p in (ROOT/'data/gloam/tags').rglob('*.json'):self.assertNotIn('has_structure',str(p))
 def test_climate_has_no_continent_ocean_switch(self):
  s=read(WORLD+'noise_settings/realm.json')
  self.assertEqual(s['sea_level'],32)
  self.assertNotIn('aquifers',s)
  self.assertEqual(s['noise_router']['continents'],0.0)
  self.assertEqual(s['noise_router']['depth'],0.0)
 def test_height_formula_bounds(self):
  d=read(WORLD+'density_function/terrain.json')
  self.assertEqual(d['type'],'minecraft:add')
  g=d['left'];self.assertEqual((g['from_coordinate'],g['to_coordinate']),(60,92))
  self.assertEqual((g['from_value'],g['to_value']),(1.0,-1.0))
  r=d['right'];self.assertEqual(r['type'],'minecraft:add')
  total=0
  for node in (r['left'],r['right']):
   self.assertEqual(node['type'],'minecraft:mul')
   c=node['left'];self.assertEqual((c['type'],c['min'],c['max']),('minecraft:clamp',-1,1))
   self.assertEqual(c['input']['y_scale'],0.0);total+=node['right']
  self.assertEqual(total,0.625)
  self.assertEqual((76-16*total,76+16*total),(66,86))
 def test_lakes_water_only(self):
  d=read(WORLD+'feature/pond.json')
  self.assertEqual(d['type'],'minecraft:lake')
  self.assertEqual(d['fluid']['id'],'minecraft:water')
  for b in ALLOWED:
   d=read(WORLD+'biome/'+b+'.json');self.assertEqual(len(d['features'][1]),1)
   self.assertIn(d['features'][1][0],['gloam:pond','gloam:swamp_pond'])
 def test_night_not_overridden_by_biomes(self):
  for b in ALLOWED:
   a=read(WORLD+'biome/'+b+'.json')['attributes']
   self.assertEqual(a['minecraft:visual/sky_color'],'#02080d')
   self.assertEqual(a['minecraft:visual/fog_color'],'#172b2d')
  d=read('data/gloam/dimension_type/realm.json')
  self.assertEqual(d['timelines'],[])
  self.assertEqual(d['attributes']['minecraft:visual/sky_light_factor'],0.24)
 def test_destinations_are_allowed(self):
  d=read('data/gloam/tags/worldgen/biome/portal_destinations.json')
  self.assertEqual(set(d['values']),{'gloam:old_growth_pine_taiga','gloam:old_growth_spruce_taiga','gloam:pale_garden'})
if __name__=='__main__':unittest.main()
