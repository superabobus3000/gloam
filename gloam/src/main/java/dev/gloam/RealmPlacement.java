package dev.gloam;

import net.minecraft.core.BlockPos;
import dev.gloam.world.NaturalPortals;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/** Bounded biome search without loading thousands of candidate chunks. */
public final class RealmPlacement {
    private static final TagKey<Biome> DESTINATIONS = TagKey.create(Registries.BIOME, Gloam.id("portal_destinations"));
    private RealmPlacement() {}

    public static BlockPos find(ServerLevel level, int pairId) {
        int centerX = Math.multiplyExact(pairId, 256);
        int loadedCandidates = 0;
        // Search a 4096-block square, sampling only its growing perimeter.
        for (int ring=0; ring<=32; ring++) {
            for (int dx=-ring; dx<=ring; dx++) for (int dz=-ring; dz<=ring; dz++) {
                if (Math.max(Math.abs(dx),Math.abs(dz))!=ring) continue;
                int x=centerX+dx*64, z=dz*64;
                if (!level.getWorldBorder().isWithinBounds(new BlockPos(x,80,z)) ||
                    !level.getWorldBorder().isWithinBounds(new BlockPos(x+12,80,z+4))) continue;
                if (!NaturalPortals.allowedBiome(level,new BlockPos(x+1,76,z+2))) continue;
                var chunkSource=level.getChunkSource();
                var generator=chunkSource.getGenerator();
                var random=chunkSource.randomState();
                int ground=generator.getBaseHeight(x+1,z+2,Heightmap.Types.OCEAN_FLOOR_WG,level,random);
                // Reject steep locations before expensive chunk generation.
                int farGround=generator.getBaseHeight(x+11,z+2,Heightmap.Types.OCEAN_FLOOR_WG,level,random);
                if (Math.abs(ground-farGround)>2) continue;
                BlockPos origin=new BlockPos(x,ground-7,z);
                boolean overlaps=false;
                for (BlockPos p:BlockPos.betweenClosed(origin,origin.offset(12,10,4))) {
                    if(Gloam.isProtected(level,p)) { overlaps=true; break; }
                }
                if(overlaps) continue;
                if(++loadedCandidates>12) throw new IllegalStateException("Не найден сухой участок для выхода. Попробуйте создать пару позже или в новом мире.");
                // Recheck the actual decorated surface: lakes may replace the underlying terrain.
                BlockPos surface=new BlockPos(x+1,ground-1,z+2);
                level.getChunkAt(surface);
                if(!level.getFluidState(surface).isEmpty() || level.getBlockState(surface).isAir() ||
                   !level.getFluidState(surface.above()).isEmpty()) continue;
                if(!NaturalPortals.allowedBiome(level,surface)) continue;
                return origin;
            }
        }
        throw new IllegalStateException("Подходящий биом выхода не найден в радиусе 2048 блоков. Нужен новый тестовый мир с генерацией 0.2.");
    }
}
