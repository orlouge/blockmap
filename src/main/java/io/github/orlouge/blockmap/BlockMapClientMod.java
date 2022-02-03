package io.github.orlouge.blockmap;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Environment(EnvType.CLIENT)
public class BlockMapClientMod implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("blockmap");
    public static KeyBinding openBlockMapKeyBinding;
    private static BlockMap averageBlockMap, dominantBlockMap;

    @Override
    public void onInitializeClient() {
        openBlockMapKeyBinding = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.blockmap.openblockmap",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_B,
                "category.blockmap.blockmap"
        ));

        ClientTickEvents.END_WORLD_TICK.register(client -> {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (openBlockMapKeyBinding.wasPressed()) {
                if (averageBlockMap == null) {
                    averageBlockMap = BlockMapManager.getBlockMap(false);
                }
                if (dominantBlockMap == null) {
                    dominantBlockMap = BlockMapManager.getBlockMap(true);
                }
                while (openBlockMapKeyBinding.wasPressed());
                if (mc.player != null && mc.currentScreen == null) {
                    mc.setScreen(new BlockMapScreen(averageBlockMap, dominantBlockMap));
                }
            }
        });
    }
}
