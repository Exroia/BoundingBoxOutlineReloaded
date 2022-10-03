package com.irtimaled.bbor.client.renderers;

import com.irtimaled.bbor.client.Player;
import com.irtimaled.bbor.client.config.BoundingBoxTypeHelper;
import com.irtimaled.bbor.client.config.ConfigManager;
import com.irtimaled.bbor.client.models.BoundingBoxWorldSpawn;
import com.irtimaled.bbor.common.models.Coords;

public class WorldSpawnRenderer extends AbstractRenderer<BoundingBoxWorldSpawn> {
    @Override
    public void render(RenderingContext ctx, BoundingBoxWorldSpawn boundingBox) {
        Coords minCoords = boundingBox.getMinCoords();
        Coords maxCoords = boundingBox.getMaxCoords();

        double y = Player.getMaxY(ConfigManager.worldSpawnMaxY.get());

        OffsetBox offsetBox = new OffsetBox(minCoords.getX(), y, minCoords.getZ(), maxCoords.getX(), y, maxCoords.getZ());
        renderCuboid(ctx, offsetBox.nudge(), BoundingBoxTypeHelper.getColor(boundingBox.getType()), false, 30);
    }
}
