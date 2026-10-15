"""Saved-world village checks. Run after save/stop; fixtures and reports stay temporary.
Usage: python inspect_village_world.py WORLD OUTPUT
"""
import sys, json
from pathlib import Path
from collections import Counter
import inspect_lantern_world as mca
HOMES=[(19,18),(43,12),(66,25),(64,51),(44,67),(18,61),(11,39)]
def properties(v):
 # 26.3 MCA palettes omit DEFAULT states/properties; string-only beds are foot/north.
 defaults={'minecraft:brown_bed':{'part':'foot','facing':'north','occupied':'false'},'minecraft:spruce_door':{'half':'lower','facing':'north','hinge':'left','open':'false','powered':'false'}}
 out=dict(defaults.get(mca.name(v),{}))
 if isinstance(v,dict):out.update({str(k):str(a) for k,a in v.get('properties',v.get('Properties',{})).items()})
 return out
def inspect(world):
 index=json.loads((world/'data/gloam-villages.json').read_text())
 villages=index['villages']; reports={}
 # Keep a thin height band of each village (not all terrain/entire world).
 states={k:{} for k in villages}
 mca.ROOT=world/'dimensions/gloam/realm/region'
 for c in mca.chunks():
  cx,cz=int(c['xPos']),int(c['zPos'])
  matches=[k for k,e in villages.items() if e['x']//16<=cx<e['x']//16+5 and e['z']//16<=cz<e['z']//16+5]
  if not matches:continue
  k=matches[0];e=villages[k]
  for s in c.get('sections',[]):
   sy=int(s['Y'])*16
   if sy<48 or sy>112:continue
   for i,v in enumerate(mca.vals(s,'block_states',4096)):
    name=mca.name(v)
    if name in ['minecraft:air','minecraft:cave_air']:continue
    x=cx*16+i%16-e['x'];z=cz*16+i//16%16-e['z'];y=sy+i//256
    states[k][x,y,z]=(name,properties(v))
 for k,e in villages.items():
  assert e['stage']=='done' and 5<=e['houses']<=7 and 2<=e['residents']<=3 and e['spawned']==e['residents']
  s=states[k];counts=Counter(v[0] for v in s.values());homes=[]
  beds=[(p,v) for p,v in s.items() if v[0]=='minecraft:brown_bed'];heads=[p for p,v in beds if v[1].get('part')=='head'];feet=[p for p,v in beds if v[1].get('part')=='foot']
  assert len(heads)==len(feet)==3,(k,'beds',heads,feet)
  offsets={'north':(0,-1),'south':(0,1),'west':(-1,0),'east':(1,0)}
  for p,v in beds:
   if v[1].get('part')!='foot':continue
   dx,dz=offsets[v[1]['facing']];partner=(p[0]+dx,p[1],p[2]+dz)
   assert partner in heads,(k,'mismatched bed',p)
  doors=[(p,v) for p,v in s.items() if v[0]=='minecraft:spruce_door']
  assert len(doors)==4,(k,'doors',doors)
  for p,v in doors:
   if v[1]['half']!='lower':continue
   assert s[p[0],p[1]+1,p[2]][1]['half']=='upper'
  assert counts['minecraft:composter']==1 and counts['minecraft:barrel']==2
  decor=[(40,40),(32,28),(52,37),(36,53),(24,43),(17,20),(41,14)]
  for x,z in decor:
   assert sum(1 for (xx,y,zz),v in s.items() if xx==x and zz==z and v[0]=='minecraft:waxed_weathered_copper_lantern')==1,(k,'missing decor',x,z)
  assert sum(1 for (x,y,z),v in s.items() if x==z==40 and v[0]=='minecraft:waxed_weathered_copper_chain')==1
  assert counts['minecraft:iron_chain']==0
  for i,(x,z) in enumerate(HOMES):
   floors=[y for (xx,y,zz),v in s.items() if xx==x and zz==z and v[0] in ['minecraft:spruce_planks','minecraft:cobblestone','minecraft:mossy_cobblestone']]
   if i>=e['houses']:
    assert not floors,(k,'extra house',i);continue
   assert floors,(k,'missing house',i)
   y=max(floors)+1
   assert (x,y,z) not in s or i>=2,(k,'blocked home center',i)
   roof=sum(1 for (xx,yy,zz),v in s.items() if abs(xx-x)<=5 and abs(zz-z)<=5 and yy>=y+4 and v[0] in ['minecraft:dark_oak_stairs','minecraft:dark_oak_slab'])
   assert roof>5,(k,'missing roof/ruins',i,roof)
   homes.append({'index':i,'x':e['x']+x,'z':e['z']+z,'floor':y-1,'roof_blocks':roof,'inhabited':i<2})
  # Ground and two blocks above must permit walking through each inhabited entrance.
  for p,v in doors:
   if v[1]['half']!='lower':continue
   dx,dz=offsets[v[1]['facing']]
   prev=p[1]-1
   for step in range(1,5):
    x,z=p[0]+dx*step,p[2]+dz*step
    solid=[yy for (xx,yy,zz),q in s.items() if xx==x and zz==z and yy<=p[1]+3 and q[0] in ['minecraft:cobblestone','minecraft:grass_block','minecraft:dirt_path','minecraft:coarse_dirt','minecraft:dirt']]
    assert solid,(k,'missing entrance support',x,z)
    y=max(solid);assert abs(y-prev)<=1,(k,'unwalkable entrance',prev,y)
    assert (x,y+1,z) not in s and (x,y+2,z) not in s,(k,'blocked entrance',x,z,y)
    prev=y
  reports[k]={'houses':e['houses'],'residents':e['residents'],'beds':3,'doors':2,'lanterns':7,'homes':homes,'roof_blocks':sum(h['roof_blocks'] for h in homes)}
 return {'villages':reports,'skipped':index['skipped']}
if __name__=='__main__':
 report=inspect(Path(sys.argv[1]));Path(sys.argv[2]).write_text(json.dumps(report,indent=2));print(json.dumps(report,indent=2))
