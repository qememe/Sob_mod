package ru.qememe.sobmod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import ru.qememe.sobmod.EntityDisasterDrop;
import ru.qememe.sobmod.SobMod;

final class RenderDisasterDrop extends Render<EntityDisasterDrop> {
    RenderDisasterDrop(RenderManager manager) {
        super(manager);
    }

    @Override
    public void doRender(EntityDisasterDrop drop, double x, double y, double z, float yaw, float partialTicks) {
        bindEntityTexture(drop);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.disableLighting();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        switch (drop.type()) {
            case METEOR:
                drawMeteor(buffer, drop);
                break;
            case TNT:
                drawTnt(buffer, drop);
                break;
            case LAVA:
                cube(buffer, -0.175, 0, -0.175, 0.175, 0.35, 0.175,
                    "minecraft:blocks/lava_still", 1, 1, 1);
                break;
            case AIRCRAFT:
                GlStateManager.rotate(yaw, 0, 1, 0);
                drawAircraft(buffer);
                break;
            default:
                break;
        }
        tessellator.draw();
        if (drop.type() == EntityDisasterDrop.Type.TNT && (drop.ticksExisted / 5) % 2 == 0) {
            drawFlash(tessellator);
        }
        GlStateManager.enableLighting();
        GlStateManager.popMatrix();
        super.doRender(drop, x, y, z, yaw, partialTicks);
    }

    private static void drawMeteor(BufferBuilder buffer, EntityDisasterDrop drop) {
        int size = drop.level() + 1;
        double origin = -size / 2.0;
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                for (int z = 0; z < size; z++) {
                    TextureAtlasSprite sprite = Minecraft.getMinecraft().getBlockRendererDispatcher().getBlockModelShapes()
                        .getTexture(drop.meteorMaterial(x, y, z));
                    for (EnumFacing side : EnumFacing.values()) {
                        int adjacentX = x + side.getFrontOffsetX();
                        int adjacentY = y + side.getFrontOffsetY();
                        int adjacentZ = z + side.getFrontOffsetZ();
                        if (adjacentX < 0 || adjacentY < 0 || adjacentZ < 0 || adjacentX >= size
                            || adjacentY >= size || adjacentZ >= size) {
                            drawFace(buffer, side, origin + x, y, origin + z, origin + x + 1, y + 1, origin + z + 1,
                                sprite, 1.0F, 0.88F, 0.81F);
                        }
                    }
                }
            }
        }
    }

    private static void drawTnt(BufferBuilder buffer, EntityDisasterDrop drop) {
        String prefix = drop.level() == 3 ? SobMod.MOD_ID + ":blocks/nuclear_tnt_" : "minecraft:blocks/tnt_";
        for (EnumFacing side : EnumFacing.values()) {
            String suffix = side == EnumFacing.UP ? "top" : side == EnumFacing.DOWN ? "bottom" : "side";
            drawFace(buffer, side, -0.49, 0, -0.49, 0.49, 0.98, 0.49, sprite(prefix + suffix), 1, 1, 1);
        }
    }

    private static void drawFlash(Tessellator tessellator) {
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA,
            GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO);
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_COLOR);
        double min = -0.491;
        double max = 0.491;
        double base = -0.001;
        double top = 0.981;
        double[][] points = {{min, base, min}, {max, base, min}, {max, base, max}, {min, base, max},
            {min, top, min}, {max, top, min}, {max, top, max}, {min, top, max}};
        int[][] faces = {{0, 1, 2, 3}, {7, 6, 5, 4}, {1, 0, 4, 5}, {3, 2, 6, 7}, {0, 3, 7, 4}, {2, 1, 5, 6}};
        for (int[] face : faces) {
            for (int index : face) {
                double[] point = points[index];
                buffer.pos(point[0], point[1], point[2]).color(1.0F, 1.0F, 1.0F, 0.44F).endVertex();
            }
        }
        tessellator.draw();
        GlStateManager.disableBlend();
        GlStateManager.enableTexture2D();
    }

    private static void drawAircraft(BufferBuilder buffer) {
        String metal = "minecraft:blocks/iron_block";
        cube(buffer, -0.58, 0, -3.45, 0.58, 0.83, 3.45, metal, 0.53F, 0.58F, 0.63F);
        cube(buffer, -0.41, 0.13, 3.45, 0.41, 0.66, 4.04, metal, 0.59F, 0.63F, 0.68F);
        cube(buffer, -0.39, 0.83, 1.57, 0.39, 1.18, 3.09, "minecraft:blocks/coal_block", 0.43F, 0.69F, 0.98F);
        cube(buffer, -4.66, 0.20, -0.68, 4.66, 0.38, 1.12, metal, 0.56F, 0.58F, 0.60F);
        cube(buffer, -3.83, 0.20, -1.24, 3.83, 0.38, -0.68, metal, 0.48F, 0.50F, 0.53F);
        cube(buffer, -2.08, 0.18, -3.5, 2.08, 0.35, -2.43, metal, 0.57F, 0.60F, 0.64F);
        cube(buffer, -0.12, 0.65, -3.50, 0.12, 1.91, -2.65, metal, 0.70F, 0.72F, 0.75F);
        for (int side : new int[] {-1, 1}) {
            double engine = side * 2.11;
            cube(buffer, engine - 0.33, -0.30, -0.85, engine + 0.33, 0.30, 0.91, metal, 0.37F, 0.41F, 0.46F);
            double light = side * 4.61;
            cube(buffer, light - 0.09, 0.36, 0.95, light + 0.09, 0.52, 1.16,
                "minecraft:blocks/concrete_white", side < 0 ? 0.95F : 0.12F, side < 0 ? 0.13F : 0.88F, 0.14F);
        }
    }

    private static void cube(BufferBuilder buffer, double x0, double y0, double z0, double x1, double y1, double z1,
                             String texture, float red, float green, float blue) {
        TextureAtlasSprite sprite = sprite(texture);
        for (EnumFacing side : EnumFacing.values()) {
            drawFace(buffer, side, x0, y0, z0, x1, y1, z1, sprite, red, green, blue);
        }
    }

    private static void drawFace(BufferBuilder buffer, EnumFacing side, double x0, double y0, double z0,
                                 double x1, double y1, double z1, TextureAtlasSprite sprite,
                                 float red, float green, float blue) {
        float shade = side == EnumFacing.UP ? 1 : side == EnumFacing.DOWN ? 0.62F
            : side.getAxis() == EnumFacing.Axis.X ? 0.83F : 0.92F;
        Mesh.face(buffer, side, x0, y0, z0, x1, y1, z1, sprite, red * shade, green * shade, blue * shade, 1);
    }

    private static TextureAtlasSprite sprite(String name) {
        return Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(name);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityDisasterDrop drop) {
        return TextureMap.LOCATION_BLOCKS_TEXTURE;
    }
}
