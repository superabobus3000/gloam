"""Generate 26.3 worldgen data from the exact vanilla common JAR.
Usage: python tools/make_worldgen.py /path/to/minecraft-common-deobf-26.3.jar
Requires only Python's standard library. Generated JSON is committed, no game JAR is shipped.
"""
import copy,json,sys,zipfile
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
BIOMES=['forest','flower_forest','birch_forest','old_growth_birch_forest',
        'dark_forest','pale_garden','dappled_forest','taiga','old_growth_pine_taiga',
        'old_growth_spruce_taiga','snowy_taiga','swamp','mangrove_swamp','cherry_grove']

def write(path,data):
 p=ROOT/path;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')
def binary(kind,a,b):return {'type':'minecraft:'+kind,'left':a,'right':b}
def clamp(x,low,high):return {'type':'minecraft:clamp','input':x,'min':low,'max':high}
def noise(name,scale=1):return {'type':'minecraft:noise','noise':'gloam:'+name,'xz_scale':scale,'y_scale':0.0}
def block(name):return {'type':'minecraft:block','result_state':'minecraft:'+name}
def condition(pred,then):return {'type':'minecraft:condition','if_true':pred,'then_run':then}
def sequence(*args):return {'type':'minecraft:sequence','sequence':list(args)}
def biome(names):return {'type':'minecraft:biome','biome_is':['gloam:'+n for n in names]}

def build(jar):
 z=zipfile.ZipFile(jar)
 def vanilla(path):return json.loads(z.read('data/minecraft/'+path+'.json'))
 # No ocean continentalness and no 3-D cave/ravine density in this stage.
 # Terrain zero crossing is within Y=66..86 by construction, for every seed.
 for name,octave in [('hills',-7),('detail',-5),('temperature',-8),('humidity',-8)]:
  write('data/gloam/worldgen/noise/'+name+'.json',{'base_amplitude':1.0,'base_octave':octave,'octave_count':3})
 relief=binary('add',binary('mul',clamp(noise('hills'),-1,1),0.5),binary('mul',clamp(noise('detail'),-1,1),0.125))
 density=binary('add',{'type':'minecraft:gradient','axis':'y','from_coordinate':60,'from_value':1.0,'to_coordinate':92,'to_value':-1.0},relief)
 write('data/gloam/worldgen/density_function/terrain.json',density)
 write('data/gloam/worldgen/density_function/surface.json',{'type':'minecraft:find_top_surface','density':'gloam:terrain','lower_bound':-64,'upper_bound':96.0,'cell_height':1})
 settings={'default_block':'minecraft:stone','default_fluid':'minecraft:water','disable_mob_generation':False,
  'legacy_random_source':False,'noise':{'height':384,'min_y':-64},'sea_level':32,'spawn_target':[],
  'material_rule':'gloam:surface','noise_router':{
   'temperature':noise('temperature',0.25),'vegetation':noise('humidity',0.25),'continents':0.0,'erosion':0.0,'depth':0.0,'ridges':0.0,
   'chunk_surface_level':'gloam:surface','final_density':'gloam:terrain'},'debug_functions':[]}
 write('data/gloam/worldgen/noise_settings/realm.json',settings)
 # Retain vanilla bedrock, grass/dirt, and deep stone; provide matching custom-biome surfaces.
 top=sequence(
  condition(biome(['old_growth_pine_taiga','old_growth_spruce_taiga']),'minecraft:overworld/biome_surface/old_growth_pine_taiga'),
  condition(biome(['swamp','mangrove_swamp']),condition({'type':'minecraft:noise_threshold','noise':'minecraft:surface','min_threshold':-0.2,'max_threshold':2.0},block('mud'))),
  'minecraft:overworld/biome_surface/default')
 surface=sequence('minecraft:bedrock_floor',
  condition({'type':'minecraft:above_preliminary_surface'},sequence(
   condition('minecraft:on_floor',top),condition('minecraft:under_floor',block('dirt')))),
  'minecraft:overworld/underground')
 write('data/gloam/worldgen/material_rule/surface.json',surface)
 # Small surface ponds only; the vanilla bounded lake feature cannot carve an ocean.
 lake=vanilla('worldgen/feature/lake_lava')
 lake['fluid']={'id':'minecraft:water','properties':{'level':'0'}}
 lake['barrier']={'id':'minecraft:clay'}
 lake['can_replace_with_barrier']={'type':'minecraft:not','predicate':{'type':'minecraft:matching_block_tag','tag':'minecraft:features_cannot_replace'}}
 write('data/gloam/worldgen/feature/pond.json',lake)
 for name,chance in [('pond',32),('swamp_pond',6)]:
  write('data/gloam/worldgen/placed_feature/'+name+'.json',{'feature':'gloam:pond','placement':[
   {'type':'minecraft:rarity_filter','chance':chance},{'type':'minecraft:in_square'},
   {'type':'minecraft:heightmap','heightmap':'WORLD_SURFACE_WG'},{'type':'minecraft:biome'}]})
 # Unique biome keys: vanilla structure biome tags never match; overworld is untouched.
 # Remove dungeon/fossil features as well (they are features, not structure-set entries).
 removed={'lake_lava_underground','lake_lava_surface','monster_room','monster_room_deep',
          'spring_water','spring_lava','spring_lava_frozen','underwater_magma','fossil_upper','fossil_lower',
          'fossil','amethyst_geode'}
 for name in BIOMES:
  d=vanilla('worldgen/biome/'+name);d['carvers']=[]
  d['features']=[[f for f in step if f.split(':')[-1] not in removed] for step in d['features']]
  d['features'][1]=['gloam:swamp_pond' if name in ['swamp','mangrove_swamp'] else 'gloam:pond']
  d['attributes']['minecraft:visual/sky_color']='#02080d'
  d['attributes']['minecraft:visual/fog_color']='#172b2d'
  write('data/gloam/worldgen/biome/'+name+'.json',d)
 # Compact, seed-dependent patches; no plains, rivers, oceans or disallowed biomes.
 matrix=[['snowy_taiga']*4,
         ['taiga','old_growth_pine_taiga','old_growth_spruce_taiga','pale_garden'],
         ['birch_forest','old_growth_birch_forest','forest','dark_forest'],
         ['cherry_grove','flower_forest','dappled_forest','swamp'],
         ['forest','dark_forest','swamp','mangrove_swamp']]
 entries=[]
 for row,t in zip(matrix,[-0.6,-0.3,0.0,0.3,0.6]):
  for name,h in zip(row,[-0.45,-0.15,0.15,0.45]):
   entries.append({'biome':'gloam:'+name,'parameters':{'temperature':t,'humidity':h,'continentalness':0.0,'erosion':0.0,'depth':0.0,'weirdness':0.0,'offset':0.0}})
 write('data/gloam/dimension/realm.json',{'type':'gloam:realm','generator':{'type':'minecraft:noise','settings':'gloam:realm','biome_source':{'type':'minecraft:multi_noise','biomes':entries}}})
 write('data/gloam/tags/worldgen/biome/portal_destinations.json',{'replace':False,'values':['gloam:old_growth_pine_taiga','gloam:old_growth_spruce_taiga','gloam:pale_garden']})
 # Custom biome translation entries; no override of vanilla language keys.
 russian=['Лес сумрака','Цветочный лес сумрака','Берёзовый лес сумрака','Старовозрастный берёзовый лес сумрака','Тёмный лес сумрака','Бледный лес сумрака','Пятнистый лес сумрака','Тайга сумрака','Старовозрастная сосновая тайга сумрака','Старовозрастная еловая тайга сумрака','Заснеженная тайга сумрака','Болото сумрака','Мангровое болото сумрака','Вишнёвый лес сумрака']
 for lang in ['en_us','ru_ru']:
  p=ROOT/f'assets/gloam/lang/{lang}.json';d=json.loads(p.read_text())
  for name,ru in zip(BIOMES,russian):d['biome.gloam.'+name]=ru if lang=='ru_ru' else 'Gloam '+name.replace('_',' ').title()
  write(f'assets/gloam/lang/{lang}.json',d)
 print('Generated worldgen for',len(BIOMES),'biomes. Terrain base bounds: Y 66..86. Sea level: 32.')
if __name__=='__main__':build(sys.argv[1])
