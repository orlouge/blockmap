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
import net.minecraft.util.math.Direction;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;

public class BlockMapScreen extends Screen {
    private final BlockMap averageBlockMap, dominantBlockMap;
    private int offsetX = 0, offsetY = 0, previousOffsetX = 0, previousOffsetY = 0, size = 16, previousSize = -1;
    private boolean resetSize = true, renderAverage = true;

    public BlockMapScreen(BlockMap averageBlockMap, BlockMap dominantBlockMap) {
        super(Text.of("BlockMap"));
        this.averageBlockMap = averageBlockMap;
        this.dominantBlockMap = dominantBlockMap;
    }

    @Override
    public void render(MatrixStack matrices, int mouseX, int mouseY, float delta) {
        super.render(matrices, mouseX, mouseY, delta);
        if (this.resetSize) {
            this.setSize(Math.min(width, height) / Math.max(averageBlockMap.height, averageBlockMap.width));
            this.resetSize = false;
        }
        this.renderBackground(matrices);
        this.renderBlockMap(matrices, this.renderAverage ? averageBlockMap : dominantBlockMap, mouseX, mouseY);
    }

    public void switchBlockMap() {
        int prevX = previousOffsetX, prevY = previousOffsetY, prevSize = this.previousSize;
        this.previousOffsetX = offsetX;
        this.previousOffsetY = offsetY;
        this.offsetX = prevX;
        this.offsetY = prevY;
        this.previousSize = size;
        this.size = prevSize;
        this.resetSize = this.resetSize || (prevSize == -1);
        this.renderAverage = !this.renderAverage;
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
            List<OrderedText> text = selectedEntry.getBlocks().entrySet().stream()
                            .map(entry -> {
                                String dirString = "";
                                if (entry.getValue() != null) {
                                    dirString = " ( ";
                                    for (Direction direction : entry.getValue()) {
                                        dirString += direction + " ";
                                    }
                                    dirString += ")";
                                }
                                return OrderedText.styledForwardsVisitedString(
                                        entry.getKey().getName().getString() + dirString,
                                        Style.EMPTY
                                );
                            }).collect(Collectors.toList());
            this.renderOrderedTooltip(matrices, text, mouseX, mouseY);
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
            this.setSize(oldSize + 1 + oldSize / 8);
        } else if (amount < 0) {
            this.setSize(oldSize - 1 - oldSize / 8);
        } else {
            return true;
        }
        offsetX = (offsetX - (int) mouseX) * this.size / oldSize + (int) mouseX;
        offsetY = (offsetY - (int) mouseY) * this.size / oldSize + (int) mouseY;
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (BlockMapClientMod.openBlockMapKeyBinding.matchesKey(keyCode, scanCode)) {
            this.switchBlockMap();
            return true;
        } else {
            return false;
        }
    }
}
