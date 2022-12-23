package com.irtimaled.bbor.client.providers;

import com.irtimaled.bbor.client.Player;
import com.irtimaled.bbor.client.models.BoundingBoxSphere;
import com.irtimaled.bbor.client.models.Point;
import com.irtimaled.bbor.common.BoundingBoxType;
import com.irtimaled.bbor.common.models.DimensionId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CustomSphereProvider implements IBoundingBoxProvider<BoundingBoxSphere>, ICachingProvider {
    private static final Map<DimensionId, Map<Integer, BoundingBoxSphere>> dimensionCache = new ConcurrentHashMap<>();

    private static Map<Integer, BoundingBoxSphere> getCache(DimensionId dimensionId) {
        return dimensionCache.computeIfAbsent(dimensionId, i -> new ConcurrentHashMap<>());
    }

    public static void add(Point center, double radius) {
        DimensionId dimensionId = Player.getDimensionId();
        int cacheKey = center.hashCode();
        BoundingBoxSphere sphere = new BoundingBoxSphere(center, radius, BoundingBoxType.Custom);
        getCache(dimensionId).put(cacheKey, sphere);
    }

    public static boolean remove(Point center) {
        DimensionId dimensionId = Player.getDimensionId();
        int cacheKey = center.hashCode();
        return getCache(dimensionId).remove(cacheKey) != null;
    }

    public static void clear() {
        dimensionCache.values().forEach(Map::clear);
    }

    public void clearCache() {
        clear();
    }

    @Override
    public Iterable<BoundingBoxSphere> get(DimensionId dimensionId) {
        return getCache(dimensionId).values();
    }
}
