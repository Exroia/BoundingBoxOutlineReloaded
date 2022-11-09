package com.irtimaled.bbor.common.interop;

import com.irtimaled.bbor.client.ClientRenderer;
import com.irtimaled.bbor.client.config.BoundingBoxTypeHelper;
import com.irtimaled.bbor.client.config.ConfigManager;
import com.irtimaled.bbor.client.renderers.AbstractRenderer;
import com.irtimaled.bbor.common.BoundingBoxType;
import com.irtimaled.bbor.common.CommonProxy;
import com.irtimaled.bbor.common.EventBus;
import com.irtimaled.bbor.common.StructureProcessor;
import com.irtimaled.bbor.common.events.PlayerLoggedIn;
import com.irtimaled.bbor.common.events.PlayerLoggedOut;
import com.irtimaled.bbor.common.events.PlayerSubscribed;
import com.irtimaled.bbor.common.events.ServerTick;
import com.irtimaled.bbor.common.events.StructuresLoaded;
import com.irtimaled.bbor.common.events.WorldLoaded;
import com.irtimaled.bbor.common.models.AbstractBoundingBox;
import com.irtimaled.bbor.common.models.DimensionId;
import com.irtimaled.bbor.common.models.ServerPlayer;
import net.minecraft.network.ClientConnection;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.gen.feature.ConfiguredStructureFeature;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public class CommonInterop {
    public static void chunkLoaded(WorldChunk chunk) {
        DimensionId dimensionId = DimensionId.from(chunk.getWorld().getRegistryKey());
        Map<String, StructureStart> structures = new HashMap<>();
        final Registry<ConfiguredStructureFeature<?, ?>> structureFeatureRegistry = chunk.getWorld().getRegistryManager().get(Registry.CONFIGURED_STRUCTURE_FEATURE_KEY);
        for (var es : chunk.getStructureStarts().entrySet()) {
            final Optional<RegistryKey<ConfiguredStructureFeature<?, ?>>> optional = structureFeatureRegistry.getKey(es.getKey());
            optional.ifPresent(key -> structures.put(key.getValue().toString(), es.getValue()));
        }
        if (structures.size() > 0) EventBus.publish(new StructuresLoaded(structures, dimensionId));
    }

    public static void loadWorlds(Collection<ServerWorld> worlds) {
        for (ServerWorld world : worlds) {
            loadWorld(world);
            loadWorldStructures(world);
        }
    }

    public static void loadWorldStructures(World world) {
        try {
            final Registry<ConfiguredStructureFeature<?, ?>> structureFeatureRegistry = world.getRegistryManager().get(Registry.CONFIGURED_STRUCTURE_FEATURE_KEY);
            loadStructuresFromRegistry(structureFeatureRegistry);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static void loadStructuresFromRegistry(Registry<ConfiguredStructureFeature<?, ?>> structureFeatureRegistry) {
        System.out.println("Registring structures: " + Arrays.toString(structureFeatureRegistry.getEntrySet().stream().map(entry -> entry.getKey().getValue().toString()).distinct().toArray(String[]::new)));
        for (var entry : structureFeatureRegistry.getEntrySet()) {
            final Identifier value = entry.getKey().getValue();
            final BoundingBoxType boundingBoxType = BoundingBoxType.register("structure:" + value);
            StructureProcessor.registerSupportedStructure(boundingBoxType);
            StructureProcessor.supportedStructureIds.add(value.toString());
            if (!CommonProxy.isServer) {
                BoundingBoxTypeHelper.registerType(boundingBoxType, ConfigManager.structureShouldRender(value.toString()), ConfigManager.structureColor(value.toString()));
            }
        }
    }

    public static void loadWorld(ServerWorld world) {
        EventBus.publish(new WorldLoaded(world));
    }

    public static void tick() {
        EventBus.publish(new ServerTick());
    }

    public static void playerLoggedIn(ServerPlayerEntity player) {
        ServerPlayNetworkHandler connection = player.networkHandler;
        if (connection == null) return;

        ClientConnection networkManager = connection.connection;
        if (networkManager.isLocal()) return;

        EventBus.publish(new PlayerLoggedIn(new ServerPlayer(player)));
    }

    public static void playerLoggedOut(ServerPlayerEntity player) {
        EventBus.publish(new PlayerLoggedOut(player.getId()));
    }

    public static void playerSubscribed(ServerPlayerEntity player) {
        EventBus.publish(new PlayerSubscribed(player.getId(), new ServerPlayer(player)));
    }

    public static <T extends AbstractBoundingBox> AbstractRenderer<T> registerRenderer(Class<? extends T> type, Supplier<AbstractRenderer<T>> renderer) {
        return DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> () -> ClientRenderer.registerRenderer(type, renderer));
    }
}
