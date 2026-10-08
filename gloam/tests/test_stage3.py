import json,unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
class Stage3(unittest.TestCase):
 def test_client_environment_split(self):
  meta=json.loads((ROOT/'src/main/resources/fabric.mod.json').read_text())
  self.assertEqual(meta['entrypoints']['client'],['dev.gloam.client.GloamClient'])
  self.assertIn({'config':'gloam.client.mixins.json','environment':'client'},meta['mixins'])
  for p in (ROOT/'src/main/java').rglob('*.java'):self.assertNotIn('import net.minecraft.client',p.read_text())
 def test_charge_authority_and_cancellation(self):
  s=(ROOT/'src/main/java/dev/gloam/Gloam.java').read_text()
  self.assertIn('clientboundPlay().register',s)
  self.assertIn('effect(player,21)',s)
  self.assertIn('effect(player,0)',s)
  self.assertIn('cancelCharge(player)',s)
  self.assertNotIn('serverboundPlay()',s)
 def test_natural_generation_is_event_driven(self):
  s=(ROOT/'src/main/java/dev/gloam/world/NaturalPortals.java').read_text()
  self.assertIn('CHUNK_GENERATE.register',s)
  self.assertIn('Math.min(4,pending.size())',s)
  self.assertIn('j.tries<200',s)
  self.assertIn('getChunkNow',s)
 def test_accessibility_and_timeout(self):
  s=(ROOT/'src/client/java/dev/gloam/client/GloamClient.java').read_text()
  self.assertIn('screenEffectScale()',s)
  self.assertIn('DISCONNECT.register',s)
  self.assertIn('age>30',s)
 def test_fog_exclusions(self):
  s=(ROOT/'src/client/java/dev/gloam/client/mixin/FogMixin.java').read_text()
  for term in ['Gloam.REALM','FogType.NONE','MobEffects.BLINDNESS','MobEffects.DARKNESS','renderDistanceEnd']:self.assertIn(term,s)
if __name__=='__main__':unittest.main()
