package dev.gloam;

import dev.gloam.net.PortalEffectPayload;
import dev.gloam.world.NaturalPortals;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.*;
import java.io.IOException;
import java.util.*;

public final class Gloam implements ModInitializer {
    public static final Logger LOG = LoggerFactory.getLogger("gloam");
    public static final ResourceKey<Level> REALM = ResourceKey.create(Registries.DIMENSION, id("realm"));
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public static Block PORTAL;
    private static State state = new State();
    private static MinecraftServer activeServer;
    private static final Map<UUID, Charge> charges = new HashMap<>();
    private static final Map<UUID, Long> cooldowns = new HashMap<>();
    private static long ticks;

    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath("gloam", path); }
    public record End(String dimension, int x, int y, int z) {
        public BlockPos origin() { return new BlockPos(x,y,z); }
        boolean contains(BlockPos p) { return p.getX()>=x && p.getX()<x+13 && p.getY()>=y && p.getY()<y+11 && p.getZ()>=z && p.getZ()<z+5; }
        boolean touches(ServerPlayer p) {
            // Accept the new plane (x+10) and legacy alpha.1 plane (x+11).
            // tick() additionally requires the actual portal block at the player position.
            return p.getX()>=x+10.05 && p.getX()<=x+11.95 && p.getZ()>=z+1.75 && p.getZ()<=z+3.25 && p.getY()>=y+1.5 && p.getY()<y+5;
        }
        ServerLevel level(MinecraftServer server) { return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(dimension))); }
    }
    public record Pair(int id, End a, End b) {}
    private record Charge(End end, int count) {}
    private static final class State {
        int format = 1;
        int nextId = 1;
        List<Pair> pairs = new ArrayList<>();
        List<End> natural = new ArrayList<>();
        List<String> retired = new ArrayList<>();
    }
    public static boolean isProtected(ServerLevel level, BlockPos pos) {
        if (activeServer != level.getServer()) return false;
        String dim = level.dimension().identifier().toString();
        for (Pair pair : state.pairs) {
            if (pair.a.dimension.equals(dim) && pair.a.contains(pos) || pair.b.dimension.equals(dim) && pair.b.contains(pos)) return true;
        }
        for (End end:state.natural) if(end.dimension.equals(dim)&&end.contains(pos)) return true;
        return false;
    }
    public static End knownAt(ServerLevel level,int x,int z) {
        String dim=level.dimension().identifier().toString();
        for(Pair p:state.pairs) for(End e:List.of(p.a,p.b)) if(e.dimension.equals(dim)&&e.x==x&&e.z==z)return e;
        for(End e:state.natural) if(e.dimension.equals(dim)&&e.x==x&&e.z==z)return e;
        return null;
    }
    private static String endKey(End e){return e.dimension+":"+e.x+":"+e.z;}
    public static boolean retired(ServerLevel l,int x,int z){return state.retired.contains(l.dimension().identifier()+":"+x+":"+z);}
    public static void registerNatural(MinecraftServer server,End e) throws IOException {
        if(!state.natural.contains(e)) { state.natural.add(e);save(server); }
    }
    private static Pair pairFor(End e){
        for(Pair p:state.pairs)if(p.a.equals(e)||p.b.equals(e))return p;
        return null;
    }
    private static Pair linkNatural(MinecraftServer server,End source) throws IOException {
        Pair old=pairFor(source);if(old!=null)return old;
        if(!state.natural.contains(source))throw new IllegalArgumentException("Неизвестный вход");
        var targetKey=source.dimension.equals(REALM.identifier().toString())?Level.OVERWORLD:REALM;
        ServerLevel target=server.getLevel(targetKey);
        if(target==null)throw new IllegalStateException("Мир выхода недоступен");
        End dest=null;
        for(End e:state.natural)if(e.dimension.equals(targetKey.identifier().toString())){dest=e;break;}
        if(dest==null){
            BlockPos p=RealmPlacement.find(target,state.nextId);
            dest=new End(targetKey.identifier().toString(),p.getX(),p.getY(),p.getZ());
            place(target,dest);
        }
        Pair pair=targetKey.equals(REALM)?new Pair(state.nextId,source,dest):new Pair(state.nextId,dest,source);
        List<End> oldNatural=new ArrayList<>(state.natural);
        state.natural.remove(source);state.natural.remove(dest);state.pairs.add(pair);state.nextId++;
        try{save(server);}catch(IOException e){state.pairs.remove(pair);state.nextId--;state.natural=oldNatural;throw e;}
        return pair;
    }
    private static void effect(ServerPlayer p,int phase){
        if(ServerPlayNetworking.canSend(p,PortalEffectPayload.TYPE))ServerPlayNetworking.send(p,new PortalEffectPayload(phase));
    }
    private static void cancelCharge(ServerPlayer p){ if(charges.remove(p.getUUID())!=null)effect(p,0); }

    @Override public void onInitialize() {
        PayloadTypeRegistry.clientboundPlay().register(PortalEffectPayload.TYPE,PortalEffectPayload.CODEC);
        NaturalPortals.init();
        var key = ResourceKey.create(Registries.BLOCK, id("portal"));
        PORTAL = Registry.register(BuiltInRegistries.BLOCK, key,
            new PortalBlock(BlockBehaviour.Properties.of().setId(key).noCollision().noOcclusion().strength(-1, 3600000).lightLevel(s -> 9).noLootTable()));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            activeServer = server;
            NaturalPortals.reset();
            charges.clear(); cooldowns.clear(); ticks = 0;
            state = read(server);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            activeServer = null; NaturalPortals.reset(); state = new State(); charges.clear(); cooldowns.clear();
        });
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, block, entity) -> !(world instanceof ServerLevel sl) || !isProtected(sl,pos));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            if (world instanceof ServerLevel sl && (isProtected(sl, hit.getBlockPos()) || isProtected(sl,hit.getBlockPos().relative(hit.getDirection())))) return InteractionResult.FAIL;
            return InteractionResult.PASS;
        });
        ServerTickEvents.END_SERVER_TICK.register(this::tick);
        CommandRegistrationCallback.EVENT.register((dispatcher, access, environment) -> dispatcher.register(
            Commands.literal("gloam").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("create").executes(ctx -> {
                    var source = ctx.getSource();
                    if (source.getLevel().dimension() != Level.OVERWORLD) {
                        source.sendFailure(Component.literal("Создайте тестовую пару из обычного мира.")); return 0;
                    }
                    try {
                        BlockPos origin = BlockPos.containing(source.getPosition()).offset(4,-7,-2);
                        int id = create(source.getServer(),source.getLevel(),origin);
                        source.sendSuccess(() -> Component.literal("Gloam: пара #"+id+" создана. Вход к востоку от вас. /gloam unprotect "+id+" — отключить пару и снять защиту."),true);
                        return id;
                    } catch (Exception e) {
                        LOG.error("Cannot create portal pair",e);
                        source.sendFailure(Component.literal("Ошибка создания: "+e.getMessage())); return 0;
                    }
                }))
                .then(Commands.literal("locate").executes(ctx -> {
                    try {
                        var e=NaturalPortals.locate(ctx.getSource().getLevel(),BlockPos.containing(ctx.getSource().getPosition()));
                        ctx.getSource().sendSuccess(() -> Component.literal("Арка: "+e.x+" "+(e.y+7)+" "+(e.z+2)+" в "+e.dimension+". Вход с запада."),false);
                        return 1;
                    }catch(Exception e){ctx.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}
                }))
                .then(Commands.literal("link_here").executes(ctx -> {
                    try {
                        BlockPos pos=BlockPos.containing(ctx.getSource().getPosition());
                        String dim=ctx.getSource().getLevel().dimension().identifier().toString();
                        End found=null;
                        for(End e:state.natural)if(e.dimension.equals(dim)&&e.contains(pos)){found=e;break;}
                        if(found==null)throw new IllegalArgumentException("Встаньте внутри несвязанной естественной арки.");
                        Pair pair=linkNatural(ctx.getSource().getServer(),found);
                        ctx.getSource().sendSuccess(()->Component.literal("Связана пара #"+pair.id),true);return pair.id;
                    }catch(Exception e){ctx.getSource().sendFailure(Component.literal(e.getMessage()));return 0;}
                }))
                .then(Commands.literal("natural").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(JSON.toJson(state.natural)),false);return state.natural.size();
                }))
                .then(Commands.literal("stats").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("Пары: "+state.pairs.size()+", свободные арки: "+state.natural.size()+", лесные фонари за сессию: "+dev.gloam.world.ForestLanterns.placedThisSession+"; "+dev.gloam.world.ForestLanterns.status()),false);return 1;
                }))
                .then(Commands.literal("list").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(JSON.toJson(state.pairs)),false); return state.pairs.size();
                }))
                .then(Commands.literal("unprotect").then(Commands.argument("id",IntegerArgumentType.integer(1)).executes(ctx -> {
                    int id = IntegerArgumentType.getInteger(ctx,"id");
                    List<Pair> previous = new ArrayList<>(state.pairs);
                    List<String> oldRetired=new ArrayList<>(state.retired);
                    for(Pair p:state.pairs)if(p.id==id){state.retired.add(endKey(p.a));state.retired.add(endKey(p.b));}
                    boolean removed = state.pairs.removeIf(p -> p.id==id);
                    try { save(ctx.getSource().getServer()); }
                    catch (IOException e) { state.pairs = previous; state.retired=oldRetired; ctx.getSource().sendFailure(Component.literal("Не удалось сохранить изменение.")); return 0; }
                    ctx.getSource().sendSuccess(() -> Component.literal(removed ? "Связь отключена, защита снята. Блоки оставлены на месте." : "Пара не найдена."),true);
                    return removed ? 1 : 0;
                })))
        ));
        LOG.info("Gloam initialized: natural gateways, sparse lanterns, fog and transition effects enabled. Villages and custom caves are pending.");
    }

    public static void place(ServerLevel level, End end) {
        var template = level.getStructureTemplateManager().get(id("stone_portal")).orElseThrow(() -> new IllegalStateException("Шаблон stone_portal отсутствует"));
        if (!template.placeInWorld(level,end.origin(),end.origin(),new StructurePlaceSettings().setIgnoreEntities(true),level.getRandom(),Block.UPDATE_ALL)) {
            throw new IllegalStateException("Не удалось разместить шаблон");
        }
        for(int y=2;y<=4;y++) level.setBlock(end.origin().offset(10,y,2), PORTAL.defaultBlockState(), Block.UPDATE_ALL);
    }
    private static int create(MinecraftServer server, ServerLevel source, BlockPos origin) throws IOException {
        ServerLevel target = server.getLevel(REALM);
        if (target==null) throw new IllegalStateException("Измерение gloam:realm не загружено");
        if (origin.getY()<source.getMinY() || origin.getY()+11>source.getMaxY()) throw new IllegalArgumentException("Недостаточно места по высоте");
        int id = state.nextId;
        End a = new End(source.dimension().identifier().toString(),origin.getX(),origin.getY(),origin.getZ());
        BlockPos arrivalOrigin = RealmPlacement.find(target,id);
        End b = new End(REALM.identifier().toString(),arrivalOrigin.getX(),arrivalOrigin.getY(),arrivalOrigin.getZ());
        for(End end : List.of(a,b)) {
            ServerLevel level = end.level(server);
            for(BlockPos pos : BlockPos.betweenClosed(end.origin(),end.origin().offset(12,10,4))) {
                if (isProtected(level,pos)) throw new IllegalArgumentException("Пересечение с существующим порталом");
            }
        }
        // The administrative prototype command deliberately replaces terrain. Use only a test world.
        place(source,a); place(target,b);
        Pair pair = new Pair(id,a,b);
        state.pairs.add(pair); state.nextId++;
        try { save(server); }
        catch(IOException e) { state.pairs.remove(pair); state.nextId--; throw e; }
        return id;
    }
    private void tick(MinecraftServer server) {
        ticks++;
        NaturalPortals.tick(server);
        Set<UUID> online = new HashSet<>();
        for(ServerPlayer player : server.getPlayerList().getPlayers()) {
            UUID uuid=player.getUUID();online.add(uuid);
            if(!player.isAlive()||player.isPassenger()||ticks<cooldowns.getOrDefault(uuid,0L)) {cancelCharge(player);continue;}
            if(!player.level().getBlockState(player.blockPosition()).is(PORTAL)){cancelCharge(player);continue;}
            String dim=player.level().dimension().identifier().toString();
            End source=null;
            for(Pair pair:state.pairs)for(End e:List.of(pair.a,pair.b))if(e.dimension.equals(dim)&&e.touches(player))source=e;
            if(source==null)for(End e:state.natural)if(e.dimension.equals(dim)&&e.touches(player)){source=e;break;}
            if(source==null){cancelCharge(player);continue;}
            Charge old=charges.get(uuid);
            int count=old!=null&&old.end.equals(source)?old.count+1:1;
            effect(player,Math.min(count,20));
            if(count<20){charges.put(uuid,new Charge(source,count));continue;}
            charges.remove(uuid);cooldowns.put(uuid,ticks+80);
            try {
                Pair pair=pairFor(source);
                if(pair==null)pair=linkNatural(server,source);
                End end=pair.a.equals(source)?pair.b:pair.a;
                ServerLevel destination=end.level(server);
                if(destination==null)throw new IllegalStateException("Выход недоступен");
                BlockPos feet=end.origin().offset(9,2,2);
                destination.getChunkAt(feet);
                if(!destination.getBlockState(feet).getCollisionShape(destination,feet).isEmpty()||
                   !destination.getBlockState(feet.above()).getCollisionShape(destination,feet.above()).isEmpty()||
                   destination.getBlockState(feet.below()).getCollisionShape(destination,feet.below()).isEmpty()||
                   !destination.getFluidState(feet).isEmpty()||!destination.getFluidState(feet.above()).isEmpty())
                    throw new IllegalStateException("Выход заблокирован");
                player.teleport(new TeleportTransition(destination,new Vec3(feet.getX()+0.5,feet.getY(),feet.getZ()+0.5),Vec3.ZERO,90,0,TeleportTransition.PLACE_PORTAL_TICKET));
                player.resetFallDistance();effect(player,21);
            }catch(Exception e){
                effect(player,0);player.sendSystemMessage(Component.literal("Gloam: "+e.getMessage()+". Переход отменён."));
                LOG.warn("Portal transition failed",e);
            }
        }
        charges.keySet().retainAll(online);cooldowns.keySet().retainAll(online);
    }
    private static Path file(MinecraftServer server) { return server.getWorldPath(LevelResource.ROOT).resolve("data/gloam-portals.json"); }
    private static State read(MinecraftServer server) {
        Path file=file(server);
        if(!Files.exists(file)) return new State();
        try {
            State loaded=JSON.fromJson(Files.readString(file),State.class);
            if(loaded==null || loaded.format!=1 || loaded.pairs==null || loaded.nextId<1) throw new IOException("Invalid portal state");
            if(loaded.natural==null)loaded.natural=new ArrayList<>();
            if(loaded.retired==null)loaded.retired=new ArrayList<>();
            for(End e:loaded.natural)Identifier.parse(e.dimension);
            for(Pair p:loaded.pairs) {
                if(p.a==null || p.b==null || p.id<1) throw new IOException("Invalid portal pair");
                Identifier.parse(p.a.dimension); Identifier.parse(p.b.dimension);
            }
            return loaded;
        } catch(Exception e) { throw new IllegalStateException("Gloam portal state is damaged; restore data/gloam-portals.json from backup. Not overwriting it.",e); }
    }
    private static void save(MinecraftServer server) throws IOException {
        Path file=file(server); Files.createDirectories(file.getParent());
        Path tmp=file.resolveSibling(file.getFileName()+".tmp");
        Files.writeString(tmp,JSON.toJson(state));
        try { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
        catch(AtomicMoveNotSupportedException e) { Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING); }
    }
}
