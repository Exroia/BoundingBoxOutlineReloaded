package com.irtimaled.bbor.client.providers;

import com.irtimaled.bbor.client.Player;
import com.irtimaled.bbor.client.models.BoundingBoxLine;
import com.irtimaled.bbor.client.models.Point;
import com.irtimaled.bbor.common.BoundingBoxType;
import com.irtimaled.bbor.common.models.DimensionId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class CustomLineProvider implements IBoundingBoxProvider<BoundingBoxLine>, ICachingProvider {
    private static final Map<DimensionId, Map<Integer, BoundingBoxLine>> dimensionCache = new ConcurrentHashMap<>();

    private static int getHashKey(Point minPoint, Point maxPoint) {
        return (31 + minPoint.hashCode()) * 31 + maxPoint.hashCode();
    }

    private static Map<Integer, BoundingBoxLine> getCache(DimensionId dimensionId) {
        return dimensionCache.computeIfAbsent(dimensionId, i -> new ConcurrentHashMap<>());
    }

    public static void add(Point minPoint, Point maxPoint) {
        DimensionId dimensionId = Player.getDimensionId();
        int cacheKey = getHashKey(minPoint, maxPoint);
        BoundingBoxLine line = BoundingBoxLine.from(minPoint, maxPoint, BoundingBoxType.Custom);
        getCache(dimensionId).put(cacheKey, line);
    }

    public static boolean remove(Point min, Point max) {
        DimensionId dimensionId = Player.getDimensionId();
        int cacheKey = getHashKey(min, max);
        return getCache(dimensionId).remove(cacheKey) != null;
    }

    public static void clear() {
        dimensionCache.values().forEach(Map::clear);
    }

    public void clearCache() {
        clear();
    }

    @Override
    public Iterable<BoundingBoxLine> get(DimensionId dimensionId) {
        return getCache(dimensionId).values();
    }
}
