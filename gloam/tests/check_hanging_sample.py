import sys,json,collections,subprocess,argparse
from pathlib import Path
sys.path.insert(0,'gloam/tests');import inspect_lantern_world as scan
parser=argparse.ArgumentParser(description="Check mixed lantern sample (requires nbtlib and a saved forest test area).")
parser.add_argument('world',type=Path);parser.add_argument('report',type=Path);args=parser.parse_args()
subprocess.run([sys.executable,'gloam/tests/inspect_lantern_world.py',str(args.world),str(args.report)],check=True)
r=json.loads(args.report.read_text());wanted={(p['x']//16,p['z']//16) for p in r['positions']}
scan.ROOT=args.world/'dimensions/gloam/realm/region';data={}
for c in scan.chunks():
 key=(int(c['xPos']),int(c['zPos']))
 if key in wanted:data[key]={int(s['Y']):s for s in c['sections']}
def get(x,y,z):
 s=data[(x//16,z//16)][y//16]['block_states'];pal=s['palette'];i=(y%16)*256+(z%16)*16+x%16
 if len(pal)==1:return pal[0]
 bits=max(4,(len(pal)-1).bit_length());per=64//bits
 return pal[(int(s['data'][i//per])>>((i%per)*bits))&((1<<bits)-1)]
checks=[]
for p in r['positions']:
 x,y,z=p['x'],p['y'],p['z'];v=get(x,y,z)
 if str(v.get('properties',{}).get('hanging','false'))!='true':continue
 n=0
 while scan.name(get(x,y+n+1,z))=='minecraft:iron_chain':n+=1
 anchor=scan.name(get(x,y+n+1,z));assert 1<=n<=6 and ('leaves' in anchor or 'log' in anchor)
 floor=y-1
 while scan.name(get(x,floor,z)) in ['minecraft:air','minecraft:short_grass','minecraft:fern','minecraft:tall_grass','minecraft:large_fern','minecraft:leaf_litter']:floor-=1
 assert y-floor>=2
 checks.append(dict(x=x,y=y,z=z,chains=n,anchor=anchor,anchor_height=y+n+1-floor,ground_clearance=y-floor))
r.update(version='0.3.2-alpha.1',seed=20261006,hanging=len(checks),ground=r['count']-len(checks),attachments=checks)
assert r['hanging']>0 and r['ground']>0
assert max(p['anchor_height'] for p in checks)>7
assert len(set(p['chains'] for p in checks))>1
args.report.write_text(json.dumps(r,indent=2));print('Hanging',len(checks),'Ground',r['ground'],'Lengths',collections.Counter(p['chains'] for p in checks),'Max anchor height',max(p['anchor_height'] for p in checks))
