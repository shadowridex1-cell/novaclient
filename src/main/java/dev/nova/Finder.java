package dev.nova;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;

import java.util.*;

/** Scanează chunk-urile încărcate: ore, bază (blocuri construite), stash (containere). */
public final class Finder {
    public static boolean ore, base, stash;
    public static int STASH_MIN = 8;   // containere / chunk
    public static int BASE_MIN = 40;   // blocuri construite / chunk
    public static int MAX_RADIUS = 10; // chunk-uri

    public record OreHit(BlockPos pos, String name) {}

    public static final class ChunkData {
        final List<OreHit> ores = new ArrayList<>();
        int built, containers;
        long tick;
        boolean alerted;
        BlockPos center;
    }

    static final Map<Long, ChunkData> DATA = new HashMap<>();
    static long ticks;

    public static void tick(Minecraft mc) {
        ticks++;
        if (mc.level == null || mc.player == null) { DATA.clear(); return; }
        if (!(ore || base || stash)) return;

        int r = Math.min(mc.options.getEffectiveRenderDistance(), MAX_RADIUS);
        ChunkPos pc = mc.player.chunkPosition();
        int budget = 2; // chunk-uri scanate / tick, ca să nu dea lag

        for (int dx = -r; dx <= r && budget > 0; dx++) {
            for (int dz = -r; dz <= r && budget > 0; dz++) {
                int cx = pc.x + dx, cz = pc.z + dz;
                long key = ChunkPos.asLong(cx, cz);
                ChunkData old = DATA.get(key);
                if (old != null && ticks - old.tick < 600) continue; // rescan la 30s
                if (!(mc.level.getChunk(cx, cz, ChunkStatus.FULL, false) instanceof LevelChunk lc)) continue;
                ChunkData nd = scan(lc);
                if (old != null) nd.alerted = old.alerted;
                DATA.put(key, nd);
                alert(mc, nd);
                budget--;
            }
        }
        if (ticks % 200 == 0) {
            DATA.keySet().removeIf(k -> Math.abs(ChunkPos.getX(k) - pc.x) > r + 2
                    || Math.abs(ChunkPos.getZ(k) - pc.z) > r + 2);
        }
    }

    static ChunkData scan(LevelChunk c) {
        ChunkData d = new ChunkData();
        d.tick = ticks;
        ChunkPos cp = c.getPos();
        int minX = cp.getMinBlockX(), minZ = cp.getMinBlockZ();
        d.center = new BlockPos(minX + 8, 64, minZ + 8);

        for (int i = 0; i < c.getSectionsCount(); i++) {
            LevelChunkSection s = c.getSection(i);
            if (s.hasOnlyAir() || !s.maybeHas(Finder::interesting)) continue;
            int y0 = SectionPos.sectionToBlockCoord(c.getSectionYFromSectionIndex(i));
            for (int x = 0; x < 16; x++)
                for (int y = 0; y < 16; y++)
                    for (int z = 0; z < 16; z++) {
                        BlockState st = s.getBlockState(x, y, z);
                        if (ore) {
                            String n = oreName(st);
                            if (n != null) d.ores.add(new OreHit(new BlockPos(minX + x, y0 + y, minZ + z), n));
                        }
                        if (base && isBuilt(st)) d.built++;
                    }
        }
        if (stash) {
            for (BlockEntity be : c.getBlockEntities().values()) {
                if (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity
                        || be instanceof ShulkerBoxBlockEntity) d.containers++;
            }
        }
        return d;
    }

    static boolean interesting(BlockState s) { return oreName(s) != null || isBuilt(s); }

    static String oreName(BlockState s) {
        if (s.is(Blocks.ANCIENT_DEBRIS)) return "Ancient Debris";
        if (s.is(BlockTags.DIAMOND_ORES)) return "Diamond";
        if (s.is(BlockTags.EMERALD_ORES)) return "Emerald";
        if (s.is(BlockTags.GOLD_ORES)) return "Gold";
        if (s.is(BlockTags.IRON_ORES)) return "Iron";
        if (s.is(BlockTags.LAPIS_ORES)) return "Lapis";
        if (s.is(BlockTags.REDSTONE_ORES)) return "Redstone";
        return null;
    }

    /** Blocuri care aproape niciodată nu apar natural. */
    static boolean isBuilt(BlockState s) {
        return s.is(BlockTags.PLANKS) || s.is(BlockTags.WOOL) || s.is(BlockTags.BEDS)
                || s.is(BlockTags.WOODEN_DOORS) || s.is(BlockTags.STAIRS) || s.is(BlockTags.SLABS)
                || s.is(BlockTags.FENCES) || s.is(Blocks.GLASS) || s.is(Blocks.GLASS_PANE)
                || s.is(Blocks.TORCH) || s.is(Blocks.WALL_TORCH) || s.is(Blocks.CRAFTING_TABLE)
                || s.is(Blocks.FURNACE) || s.is(Blocks.BRICKS) || s.is(Blocks.STONE_BRICKS)
                || s.is(Blocks.COBBLESTONE) || s.is(Blocks.CHEST) || s.is(Blocks.BARREL)
                || s.is(Blocks.HOPPER) || s.is(Blocks.OBSERVER) || s.is(Blocks.PISTON);
    }

    static void alert(Minecraft mc, ChunkData d) {
        if (d.alerted) return;
        boolean s = stash && d.containers >= STASH_MIN;
        boolean b = base && d.built >= BASE_MIN;
        if (!s && !b) return;
        d.alerted = true;
        BlockPos p = d.center;
        String what = (s ? "STASH (" + d.containers + " containere)" : "") + (s && b ? " + " : "")
                + (b ? "BAZĂ (" + d.built + " blocuri)" : "");
        mc.player.displayClientMessage(Component.literal("§a[Nova] §f" + what + " la §e" + p.getX() + ", " + p.getZ()), false);
    }

    /** Afișează în chat cele mai apropiate rezultate. */
    public static void report(Minecraft mc) {
        if (mc.player == null) return;
        BlockPos me = mc.player.blockPosition();
        var out = mc.player;

        if (ore) {
            List<OreHit> hits = new ArrayList<>();
            DATA.values().forEach(d -> hits.addAll(d.ores));
            hits.sort(Comparator.comparingDouble(h -> h.pos().distSqr(me)));
            out.displayClientMessage(Component.literal("§b[Nova] Ore (" + hits.size() + "):"), false);
            for (int i = 0; i < Math.min(8, hits.size()); i++) {
                OreHit h = hits.get(i);
                out.displayClientMessage(Component.literal(" §7" + h.name() + " §f" + h.pos().getX() + " " + h.pos().getY()
                        + " " + h.pos().getZ() + " §8(" + (int) Math.sqrt(h.pos().distSqr(me)) + "m)"), false);
            }
        }
        if (base) list(mc, me, "Baze", d -> d.built >= BASE_MIN, d -> d.built + " blocuri");
        if (stash) list(mc, me, "Stash-uri", d -> d.containers >= STASH_MIN, d -> d.containers + " containere");
    }

    private static void list(Minecraft mc, BlockPos me, String title,
                             java.util.function.Predicate<ChunkData> f,
                             java.util.function.Function<ChunkData, String> info) {
        List<ChunkData> l = new ArrayList<>(DATA.values().stream().filter(f).toList());
        l.sort(Comparator.comparingDouble(d -> d.center.distSqr(me)));
        mc.player.displayClientMessage(Component.literal("§b[Nova] " + title + " (" + l.size() + "):"), false);
        for (int i = 0; i < Math.min(5, l.size()); i++) {
            ChunkData d = l.get(i);
            mc.player.displayClientMessage(Component.literal(" §f" + d.center.getX() + ", " + d.center.getZ()
                    + " §7" + info.apply(d)), false);
        }
    }
}
