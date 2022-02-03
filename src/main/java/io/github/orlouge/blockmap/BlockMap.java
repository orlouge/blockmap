package io.github.orlouge.blockmap;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BlockMap {
    public final List<Cell> cells;
    public final int width, height;

    public BlockMap(List<BlockMapEntry> blockMapEntries, boolean dominant) {
        BlockMapBuilder builder = new BlockMapBuilder(blockMapEntries, dominant);
        cells = new ArrayList<>(blockMapEntries.size());
        width = builder.width();
        height = builder.height();
        Iterator<Iterator<BlockMapEntry>> rows = builder.grid();
        for (int x = 0; x < width; x++) {
            Iterator<BlockMapEntry> row = rows.next();
            for (int y = 0; y < height; y++) {
                BlockMapEntry entry = row.next();
                if (entry != null) {
                    cells.add(new Cell(entry, x, y));
                }
            }
        }
    }

    public class Cell {
        public final BlockMapEntry entry;
        public final int cellX, cellY;

        public Cell(BlockMapEntry entry, int cellX, int cellY) {
            this.entry = entry;
            this.cellX = cellX;
            this.cellY = cellY;
        }
    }
}
