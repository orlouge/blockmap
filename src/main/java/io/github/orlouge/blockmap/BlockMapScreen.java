package io.github.orlouge.blockmap;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.util.Iterator;
import java.util.List;

public class BlockMapScreen extends Screen {
    private final BlockMap averageBlockMap, dominantBlockMap;
    private int offsetX = 0, offsetY = 0, size = 16;
    private boolean resetSize = true;

    public BlockMapScreen(BlockMap averageBlockMap, BlockMap dominantBlockMap) {
        super(Text.of("BlockMap"));
        this.averageBlockMap = averageBlockMap;
        this.dominantBlockMap = dominantBlockMap;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        if (resetSize) {
            this.setSize(Math.min(width, height) / Math.max(averageBlockMap.height, averageBlockMap.width));
            resetSize = false;
        }
        this.renderBackground(matrices);
        this.renderBlockMap(matrices, averageBlockMap, mouseX, mouseY);
        // this.renderBlockMap(matrices, dominantBlockMap);
    }

    private void setSize(int size) {
        this.size = Math.max(1, Math.min(128, size));
    }

    private void renderBlockMap(MatrixStack matrices, BlockMap blockMap, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);

        BlockMapEntry selectedEntry = null;
        for (BlockMap.Cell cell : blockMap.cells) {
            int gx = cell.cellX, gy = cell.cellY;
            BlockMapEntry entry = cell.entry;
            if (entry != null) {
                int x = offsetX + gx * size, y = offsetY + gy * size;
                if (x > -size && y > -size && x < width && y < height) {
                    setShaderTexture(0, entry.getTexture());
                    this.drawTexture(matrices, x, y, 0, 0, size, size, size, size);
                    if (mouseX >= x && mouseY >= y && mouseX < x + size && mouseY < y + size) {
                        selectedEntry = entry;
                    }
                }
            }
        }

        if (selectedEntry != null) {
            OrderedText text = OrderedText.styledForwardsVisitedString(
                    selectedEntry.block.getName().getString() +
                    (selectedEntry.direction != null
                            ? (" (" + selectedEntry.direction + ")") : ""),
                    Style.EMPTY
            );
            this.renderOrderedTooltip(matrices, List.of(text), mouseX, mouseY);
        }
    }

    private static void setShaderTexture(int i, AbstractTexture texture) {
        int[] shaderTextures = RenderSystem.shaderTextures;
        if (i >= 0 && i < shaderTextures.length) {
            if (!RenderSystem.isOnRenderThread()) {
                RenderSystem.recordRenderCall(() -> {
                    shaderTextures[i] = texture.getGlId();
                });
            } else {
                shaderTextures[i] = texture.getGlId();
            }
        }
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        offsetX += (int) deltaX;
        offsetY += (int) deltaY;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        // return super.mouseScrolled(mouseX, mouseY, amount);
        int oldSize = this.size;
        if (amount > 0) {
            this.setSize(oldSize * 2);
        } else if (amount < 0) {
            this.setSize(oldSize / 2);
        } else {
            return true;
        }
        offsetX = (offsetX - (int) mouseX) * this.size / oldSize + (int) mouseX;
        offsetY = (offsetY - (int) mouseY) * this.size / oldSize + (int) mouseY;
        return true;
    }
}
