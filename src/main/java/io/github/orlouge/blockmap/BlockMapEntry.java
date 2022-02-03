package io.github.orlouge.blockmap;

import net.minecraft.block.Block;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class BlockMapEntry {
    private double dominantR, dominantG, dominantB;
    private double averageR, averageG, averageB;
    public final boolean hasDominant;
    public final Block block;
    public final Direction direction;
    private final AbstractTexture texture;
    private static final int DOMINANT_PERCENTAGE = 85, DOMINANT_MAXDIFF = 7000;

    public BlockMapEntry(Block block, NativeImage image, Direction direction) {
        this.block = block;
        this.direction = direction;
        this.texture = new NativeImageBackedTexture(image);
        int width = image.getWidth(), height = image.getHeight(), dominantCount = 0;

        int avgR = 0, avgG = 0, avgB = 0, domR = 0, domG = 0, domB = 0;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = image.getColor(x, y);
                int r = NativeImage.getRed(color), g = NativeImage.getGreen(color), b = NativeImage.getBlue(color);
                avgR += r;
                avgG += g;
                avgB += b;
            }
        }

        averageR = (double) avgR / ((double) (width * height) * 255d);
        averageG = (double) avgG / ((double) (width * height) * 255d);
        averageB = (double) avgB / ((double) (width * height) * 255d);
        avgR /= 255;
        avgG /= 255;
        avgB /= 255;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int color = image.getColor(x, y);
                int r = NativeImage.getRed(color), g = NativeImage.getGreen(color), b = NativeImage.getBlue(color);

                if (Math.pow(r - avgR, 2) + Math.pow(g - avgG, 2) + Math.pow(b - avgB, 2) > DOMINANT_MAXDIFF) {
                    continue;
                }

                domR += r;
                domG += g;
                domB += b;
                dominantCount++;
            }
        }

        dominantR = (double) domR / ((double) dominantCount * 255);
        dominantG = (double) domG / ((double) dominantCount * 255);
        dominantB = (double) domB / ((double) dominantCount * 255);

        hasDominant = dominantCount > width * height * DOMINANT_PERCENTAGE / 100;
    }

    public Vec3d averageColor() {
        return new Vec3d(averageR, averageG, averageB);
    }

    public Vec3d dominantColor() {
        return new Vec3d(dominantR, dominantG, dominantB);
    }

    public Vec3d dominantFeatures() {
        return features(dominantColor());
    }

    public Vec3d averageFeatures() {
        return features(averageColor()); }

    private static Vec3d features(Vec3d color) {
        double y = (color.x + color.y + color.z) / 3d;
        double a = (color.z - y) / 2d;
        double c = (color.x - y) / 2d;
        double b = (y - 0.5d) / 3d;
        return new Vec3d(a, b, c);
    }

    public AbstractTexture getTexture() {
        return texture;
    }
}
