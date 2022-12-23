package com.irtimaled.bbor.client.providers;

import com.irtimaled.bbor.client.Player;
import com.irtimaled.bbor.client.config.BoundingBoxTypeHelper;
import com.irtimaled.bbor.client.config.ConfigManager;
import com.irtimaled.bbor.client.interop.ClientWorldUpdateTracker;
import com.irtimaled.bbor.client.interop.SpawnableBlocksHelper;
import com.irtimaled.bbor.client.models.BoundingBoxSpawnableBlocks;
import com.irtimaled.bbor.common.BoundingBoxType;
import com.irtimaled.bbor.common.EventBus;
import com.irtimaled.bbor.common.models.DimensionId;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectLinkedOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import it.unimi.dsi.fastutil.objects.ObjectSets;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Comparator;

public class SpawnableBlocksProvider implements IBoundingBoxProvider<BoundingBoxSpawnableBlocks>, ICachingProvider {

    private static final ObjectLinkedOpenHashSet<ChunkPos> queuedUpdateChunks = new ObjectLinkedOpenHashSet<>();
    private static final ObjectSet<ChunkPos> dirtyPoses = ObjectSets.synchronize(new ObjectLinkedOpenHashSet<>());

    public static void runQueuedTasks() {
        if (!BoundingBoxTypeHelper.shouldRender(BoundingBoxType.SpawnableBlocks)) return;
        ChunkPos pos;
        synchronized (queuedUpdateChunks) {
            if (queuedUpdateChunks.isEmpty()) return;
            pos = queuedUpdateChunks.removeFirst();
        }
        final SpawnableBlocksChunk chunk = chunks.get(pos.toLong());
        if (chunk != null) {
            chunk.findBoxesFromBlockState(pos.getStartX(), pos.getStartZ());
        } else {
            chunks.put(pos.toLong(), new SpawnableBlocksChunk(pos.x, pos.z));
        }
    }

    private static final Long2ObjectMap<SpawnableBlocksChunk> chunks = Long2ObjectMaps.synchronize(new Long2ObjectLinkedOpenHashMap<>(), SpawnableBlocksProvider.class);

    private static class SpawnableBlocksChunk {

        private static int getIndex(int x, int z) {
            return (z & 0b1111) << 4 | (x & 0b1111);
        }

        private final BoundingBoxSpawnableBlocks[] boxes = new BoundingBoxSpawnableBlocks[16 * 16];

        public SpawnableBlocksChunk(int chunkX, int chunkZ) {
            int chunkStartX = chunkX << 4;
            int chunkStartZ = chunkZ << 4;

            findBoxesFromBlockState(chunkStartX, chunkStartZ);
        }

        private void findBoxesFromBlockState(int chunkStartX, int chunkStartZ) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    findBoxFromBlockState(chunkStartX + x, chunkStartZ + z);
                }
            }
        }

        private void findBoxFromBlockState(int x, int z) {
            BoundingBoxSpawnableBlocks box = boxes[getIndex(x, z)];
            if (box == null) {
                boxes[getIndex(x, z)] = box = new BoundingBoxSpawnableBlocks(x, z);
            }
            final ClientWorld world = MinecraftClient.getInstance().world;
            if (world == null) return;
            final WorldChunk chunk = world.getChunk(ChunkSectionPos.getSectionCoord(x), ChunkSectionPos.getSectionCoord(z));
            if (chunk == null) return;

            final int maxY = chunk.sampleHeightmap(Heightmap.Type.WORLD_SURFACE, x, z) + 1;
            box.getBlockYs().clear();

            // I have no idea how to properly use isSpawnable so i copied this
            BlockState upperBlockState = world.getBlockState(new BlockPos(x, world.getBottomY(), z));
            for (int y = world.getBottomY() + 1; y < maxY; y++) {
                BlockState spawnBlockState = upperBlockState;
                BlockPos pos = new BlockPos(x, y, z);
                upperBlockState = world.getBlockState(pos);
                if (SpawnableBlocksHelper.isSpawnable(world, pos, spawnBlockState, upperBlockState)) {
                    box.getBlockYs().add(y);
                }
            }
        }

        public BoundingBoxSpawnableBlocks[] getBlocks() {
            return boxes;
        }
    }

    {
        EventBus.subscribe(ClientWorldUpdateTracker.ChunkLoadEvent.class, event -> {
            final ChunkPos pos = new ChunkPos(event.x(), event.z());
            dirtyPoses.add(pos);
        });
        EventBus.subscribe(ClientWorldUpdateTracker.LightingUpdateEvent.class, event -> {
            final ChunkPos pos = new ChunkPos(event.x(), event.z());
            dirtyPoses.add(pos);
        });
        EventBus.subscribe(ClientWorldUpdateTracker.ChunkUnloadEvent.class, event -> {
            synchronized (queuedUpdateChunks) {
                queuedUpdateChunks.remove(new ChunkPos(event.x(), event.z()));
            }
            dirtyPoses.remove(new ChunkPos(event.x(), event.z()));
            chunks.remove(ChunkPos.toLong(event.x(), event.z()));
        });
        EventBus.subscribe(ClientWorldUpdateTracker.BlockChangeEvent.class, event -> {
            for (int x = -1; x <= 1; x ++) {
                for (int z = -1; z <= 1; z++) {
                    dirtyPoses.add(new ChunkPos(ChunkSectionPos.getSectionCoord(event.x()) + x, ChunkSectionPos.getSectionCoord(event.z()) + z));
                }
            }
        });
        EventBus.subscribe(ClientWorldUpdateTracker.WorldResetEvent.class, event -> {
            synchronized (queuedUpdateChunks) {
                queuedUpdateChunks.clear();
            }
            dirtyPoses.clear();
            chunks.clear();
        });
    }

    public static void scheduleRecalculation() {
        synchronized (SpawnableBlocksProvider.class) {
            final LongIterator iterator = chunks.keySet().iterator();
            while (iterator.hasNext()) {
                final long key = iterator.nextLong();
                final ChunkPos pos = new ChunkPos(key);
                dirtyPoses.add(pos);
            }
        }
    }

    public static int pendingUpdates() {
        return queuedUpdateChunks.size();
    }

    public void clearCache() {
        chunks.clear();
        synchronized (queuedUpdateChunks) {
            queuedUpdateChunks.clear();
        }
    }

    @Override
    public boolean canProvide(DimensionId dimensionId) {
        return BoundingBoxTypeHelper.shouldRender(BoundingBoxType.SpawnableBlocks);
    }

    @Override
    public Iterable<BoundingBoxSpawnableBlocks> get(DimensionId dimensionId) {
        int renderDistanceChunks = ConfigManager.spawnableBlocksRenderDistance.get();
        int playerChunkX = ChunkSectionPos.getSectionCoord(Player.getX());
        int playerChunkZ = ChunkSectionPos.getSectionCoord(Player.getZ());

        ArrayList<BoundingBoxSpawnableBlocks> boxes = new ArrayList<>();

        ObjectArrayList<ChunkPos> pendingPoses = new ObjectArrayList<>();
        for (int chunkX = playerChunkX - renderDistanceChunks; chunkX <= playerChunkX + renderDistanceChunks; chunkX++) {
            for (int chunkZ = playerChunkZ - renderDistanceChunks; chunkZ <= playerChunkZ + renderDistanceChunks; chunkZ++) {
                long key = ChunkPos.toLong(chunkX, chunkZ);
                SpawnableBlocksChunk chunk = chunks.get(key);
                final ChunkPos pos = new ChunkPos(chunkX, chunkZ);
                if (chunk == null || dirtyPoses.remove(pos)) {
                    boolean alreadyQueued;
                    synchronized (queuedUpdateChunks) {
                        alreadyQueued = queuedUpdateChunks.contains(pos);
                    }
                    if (!alreadyQueued) pendingPoses.add(pos);
                    if (chunk == null) continue;
                }
                for (BoundingBoxSpawnableBlocks box : chunk.getBlocks()) {
                    if (box != null) {
                        boxes.add(box);
                    }
                }
            }
        }
        if (!pendingPoses.isEmpty()) {
            ChunkPos currentSectionPosObj = new ChunkPos(playerChunkX, playerChunkZ);
            pendingPoses.unstableSort(Comparator.comparingInt(value -> value.getChebyshevDistance(currentSectionPosObj)));
            synchronized (queuedUpdateChunks) {
                queuedUpdateChunks.addAll(pendingPoses);
            }
        }
        return boxes;
    }

}
