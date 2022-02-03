package io.github.orlouge.blockmap;

import io.github.orlouge.blockmap.mixin.SpriteAccessor;
import net.minecraft.block.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.model.BakedModel;
import net.minecraft.client.render.model.BakedModelManager;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.Sprite;
import net.minecraft.util.math.Direction;
import net.minecraft.util.registry.Registry;
import net.minecraft.util.registry.RegistryKey;

import java.util.*;
import java.util.stream.Collectors;

public class BlockMapManager {
    private static List<BlockMapEntry> entries = null;
    private static BlockMap dominantBlockMap = null, averageBlockMap = null;

    public static BlockMap getBlockMap(boolean dominant) {
        if (dominant) {
            if (dominantBlockMap == null) {
                dominantBlockMap = new BlockMap(getEntries(), true);
            }
            return dominantBlockMap;
        } else {
            if (averageBlockMap == null) {
                averageBlockMap = new BlockMap(getEntries(), false);
            }
            return averageBlockMap;
        }
    }

    public static List<BlockMapEntry> getEntries() {
        if (entries == null) {
            Set<Map.Entry<RegistryKey<Block>, Block>> blockRegistry = Registry.BLOCK.getEntries();
            entries = blockRegistry.stream().flatMap(entry -> getBlockColor(entry.getValue()).stream()).collect(Collectors.toList());
        }

        return entries;
    }

    public static List<BlockMapEntry> getBlockColor(Block block) {
        BlockState state = block.getDefaultState();
        if (
                state.getRenderType() != BlockRenderType.MODEL ||
                state.hasSidedTransparency() ||
                !state.isOpaque() ||
                block instanceof CarpetBlock ||
                block instanceof FenceBlock ||
                block instanceof FenceGateBlock ||
                block instanceof InfestedBlock ||
                block instanceof WallBlock ||
                block instanceof AnvilBlock ||
                block instanceof BigDripleafBlock ||
                block instanceof AbstractRedstoneGateBlock ||
                block instanceof OperatorBlock ||
                block instanceof GrindstoneBlock
        ) {
            return List.of();
        }
        BlockMapClientMod.LOGGER.info(block.toString());

        MinecraftClient client = MinecraftClient.getInstance();
        BakedModelManager modelManager = client.getBakedModelManager();
        HashMap<Sprite, Direction> sprites = new HashMap<>();
        ArrayList<BlockMapEntry> blockColors = new ArrayList<>();

        for (Direction direction : Direction.values()) {
            if (state.isSideInvisible(state, direction)) {
                continue;
            }
            BakedModel model = modelManager.getBlockModels().getModel(state);
            List<BakedQuad> quads = model.getQuads(state, direction, client.world.getRandom());
            if (quads.size() == 1) {
                for (BakedQuad quad : quads) {
                    Sprite sprite = quad.getSprite();
                    if (sprite.getWidth() < 16 || sprite.getHeight() < 16) continue;
                    if (sprites.containsKey(sprite)) {
                        sprites.put(sprite, direction);
                    } else {
                        sprites.put(sprite, null);
                    }
                }
            }
        }

        for (Map.Entry<Sprite, Direction> spriteDir : sprites.entrySet()) {
            NativeImage image = ((SpriteAccessor) spriteDir.getKey()).getImages()[0];
            if (image.getFormat() == NativeImage.Format.RGBA) {
                blockColors.add(new BlockMapEntry(block, image, sprites.size() == 1 ? null : spriteDir.getValue()));
            }
        }

        return blockColors;
    }
}
