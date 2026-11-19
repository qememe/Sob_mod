package ru.qememe.sobmod.client;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.EnumFacing;

final class Mesh {
    private Mesh() {}

    static void face(BufferBuilder buffer, EnumFacing side, double x0, double y0, double z0,
                     double x1, double y1, double z1, TextureAtlasSprite sprite,
                     float red, float green, float blue, float alpha) {
        switch (side) {
            case DOWN: quad(buffer, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, sprite, red, green, blue, alpha); break;
            case UP: quad(buffer, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, sprite, red, green, blue, alpha); break;
            case NORTH: quad(buffer, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, sprite, red, green, blue, alpha); break;
            case SOUTH: quad(buffer, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, sprite, red, green, blue, alpha); break;
            case WEST: quad(buffer, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, sprite, red, green, blue, alpha); break;
            case EAST: quad(buffer, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, sprite, red, green, blue, alpha); break;
        }
    }

    private static void quad(BufferBuilder buffer, double ax, double ay, double az, double bx, double by, double bz,
                             double cx, double cy, double cz, double dx, double dy, double dz, TextureAtlasSprite sprite,
                             float red, float green, float blue, float alpha) {
        buffer.pos(ax, ay, az).tex(sprite.getMinU(), sprite.getMaxV()).color(red, green, blue, alpha).endVertex();
        buffer.pos(bx, by, bz).tex(sprite.getMaxU(), sprite.getMaxV()).color(red, green, blue, alpha).endVertex();
        buffer.pos(cx, cy, cz).tex(sprite.getMaxU(), sprite.getMinV()).color(red, green, blue, alpha).endVertex();
        buffer.pos(dx, dy, dz).tex(sprite.getMinU(), sprite.getMinV()).color(red, green, blue, alpha).endVertex();
    }
}
