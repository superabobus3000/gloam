"""Deterministic original portal texture. Requires Pillow; not used by Gradle."""
import json,math,random,shutil,zipfile
from pathlib import Path
from PIL import Image
ROOT=Path(__file__).resolve().parents[1]/'src/main/resources'
def write(name,data):
 p=ROOT/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(json.dumps(data,indent=2,ensure_ascii=False)+'\n')
write('assets/gloam/blockstates/portal.json',{'variants':{'':{'model':'gloam:block/portal'}}})
write('assets/gloam/models/block/portal.json',{'ambientocclusion':False,'textures':{'particle':'gloam:block/portal','portal':'gloam:block/portal'},'elements':[{'from':[6,0,0],'to':[10,16,16],'shade':False,'faces':{side:{'texture':'#portal','uv':[0,0,16,16]} for side in ['west','east','north','south','up','down']}}]})
for lang, label in [('ru_ru','Переход в сумрак'),('en_us','Gloam Gateway')]:
 lang_path=ROOT/f'assets/gloam/lang/{lang}.json'
 translations=json.loads(lang_path.read_text()) if lang_path.exists() else {}
 translations['block.gloam.portal']=label
 write(f'assets/gloam/lang/{lang}.json',translations)
p=ROOT/'assets/gloam/textures/block/portal.png';p.parent.mkdir(parents=True,exist_ok=True)
im=Image.new('RGBA',(32,32*32));rand=random.Random(23);noise=[[rand.uniform(-1,1) for x in range(32)] for y in range(32)]
for t in range(32):
 phase=2*math.pi*t/32
 for y in range(32):
  for x in range(32):
   u=x/32*math.tau;v=y/32*math.tau
   f=(math.sin(2*u+math.sin(v+phase))+math.cos(3*v+math.sin(u-phase))+math.sin(4*u-2*v+phase))/3
   b=max(0,min(1,.5+.35*f+.08*noise[y][x]))
   im.putpixel((x,y+32*t),(int(3+10*b),int(47+127*b),int(46+116*b),255))
im.save(p)
write('assets/gloam/textures/block/portal.png.mcmeta',{'animation':{'frametime':2,'interpolate':True}})
# Static environmental attributes replace time-driven overworld tracks; never change overworld's clock.
write('data/gloam/dimension_type/realm.json',{
 'ambient_light':0.02,'attributes':{
 'minecraft:visual/sky_color':'#02080d','minecraft:visual/fog_color':'#172b2d',
 'minecraft:visual/sky_light_color':'#7a7aff','minecraft:visual/sky_light_factor':0.24,
 'minecraft:visual/sun_angle':180.0,'minecraft:visual/moon_angle':0.0,
 'minecraft:visual/star_brightness':0.5,'minecraft:gameplay/sky_light_level':0.26666668,
 'minecraft:gameplay/monsters_burn':False,
 'minecraft:gameplay/bed_rule':{'can_set_spawn':'never','can_sleep':'never','error_message':{'text':'В сумраке нельзя спать.'}},
 'minecraft:gameplay/respawn_anchor_works':False},
 'coordinate_scale':1.0,'has_ceiling':False,'has_ender_dragon_fight':False,'has_skylight':True,
 'height':384,'infiniburn':'#minecraft:infiniburn_overworld','logical_height':384,'min_y':-64,
 'monster_spawn_block_light_limit':0,'monster_spawn_light_level':{'type':'minecraft:uniform','min_inclusive':0,'max_inclusive':7},'timelines':[]})
# World generation is maintained separately by make_worldgen.py.
# Do not overwrite realm.json with a flat generator here.
