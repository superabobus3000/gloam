from pathlib import Path
import struct,zlib,io,nbtlib,json,math
ROOT=None
def chunks():
 for p in ROOT.glob('*.mca'):
  raw=p.read_bytes()
  for i in range(1024):
   off=int.from_bytes(raw[i*4:i*4+3],'big')*4096
   if not off:continue
   size=int.from_bytes(raw[off:off+4],'big');typ=raw[off+4];data=raw[off+5:off+4+size]
   if typ!=2:continue
   yield nbtlib.File.parse(io.BytesIO(zlib.decompress(data)))
def name(v):return str(v.get('Name',v.get('id',v.get('','')))) if isinstance(v,dict) else str(v)
def vals(sec,key,total):
 a=sec.get(key,{});pal=a.get('palette',[])
 if not pal:return []
 if len(pal)==1:return [pal[0]]*total
 bits=max(4 if total==4096 else 1,(len(pal)-1).bit_length());per=64//bits;mask=(1<<bits)-1;data=a['data']
 return [pal[(int(data[i//per])>>((i%per)*bits))&mask] for i in range(total)]
if __name__=='__main__':
 import argparse
 parser=argparse.ArgumentParser(description="Inspect saved Gloam forest lanterns (stop/save the test server first).")
 parser.add_argument('world',type=Path);parser.add_argument('report',type=Path);args=parser.parse_args()
 ROOT=args.world/'dimensions/gloam/realm/region'
 lamps=[]
 for c in chunks():
  cx,cz=int(c['xPos']),int(c['zPos'])
  for s in c.get('sections',[]):
   if not any(name(v)=='minecraft:waxed_weathered_copper_lantern' for v in s.get('block_states',{}).get('palette',[])):continue
   states=vals(s,'block_states',4096);biomes=vals(s,'biomes',64)
   for i,v in enumerate(states):
    if name(v)!='minecraft:waxed_weathered_copper_lantern':continue
    x,z,y=i%16,i//16%16,i//256
    lamps.append(dict(x=cx*16+x,y=int(s['Y'])*16+y,z=cz*16+z,biome=name(biomes[(y//4)*16+(z//4)*4+x//4]),state=str(v)))
 cells=[(p['x']//64,p['z']//64) for p in lamps]
 minimum=min((math.hypot(a['x']-b['x'],a['z']-b['z']) for i,a in enumerate(lamps) for b in lamps[i+1:]),default=None)
 result=dict(count=len(lamps),minimum_xz=minimum,one_per_cell=len(set(cells))==len(cells),biomes=sorted(set(p['biome'] for p in lamps)),positions=lamps)
 args.report.write_text(json.dumps(result,indent=2));print(json.dumps({k:v for k,v in result.items() if k!='positions'},indent=2))
 assert result['one_per_cell'] and (minimum is None or minimum>=40)
