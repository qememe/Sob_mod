package ru.qememe.sobmod.client;

import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ResourceLocation;
import ru.qememe.sobmod.EntityFab;
import ru.qememe.sobmod.SobMod;

final class RenderFab extends Render<EntityFab> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(SobMod.MOD_ID, "textures/entity/fab.png");
    private static final int SIDES = 12;
    private static final double[] RADIUS = {0, 0.12, 0.23, 0.35, 0.42, 0.44, 0.44, 0.33, 0.19, 0.16, 0};
    private static final double[] HEIGHT = {0, 0.035, 0.09, 0.18, 0.30, 0.45, 1.14, 1.38, 1.55, 1.92, 1.94};
    private final Fins fins = new Fins();

    RenderFab(RenderManager manager) {
        super(manager);
        shadowSize = 0.5F;
    }

    @Override
    public void doRender(EntityFab bomb, double x, double y, double z, float yaw, float partialTicks) {
        bindEntityTexture(bomb);
        GlStateManager.pushMatrix();
        GlStateManager.translate(x, y, z);
        GlStateManager.rotate((bomb.ticksExisted + partialTicks) * 8.0F, 0, 1, 0);
        GlStateManager.disableLighting();
        drawHull();
        GlStateManager.enableLighting();
        fins.render();
        GlStateManager.popMatrix();
        super.doRender(bomb, x, y, z, yaw, partialTicks);
    }

    private static void drawHull() {
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
        for (int ring = 0; ring < HEIGHT.length - 1; ring++) {
            for (int side = 0; side < SIDES; side++) {
                float lighting = 0.82F + 0.18F * (float) Math.cos(side * Math.PI * 2 / SIDES);
                vertex(buffer, ring, side, lighting);
                vertex(buffer, ring + 1, side, lighting);
                vertex(buffer, ring + 1, side + 1, lighting);
                vertex(buffer, ring, side + 1, lighting);
            }
        }
        tessellator.draw();
    }

    private static void vertex(BufferBuilder buffer, int ring, int side, float shade) {
        double angle = side * Math.PI * 2 / SIDES;
        double height = HEIGHT[ring];
        buffer.pos(RADIUS[ring] * Math.cos(angle), height, RADIUS[ring] * Math.sin(angle))
            .tex((0.5 + 63 * side / (double) SIDES) / 128, (0.5 + (1 - height / 1.94) * 59) / 128)
            .color(shade, shade, shade, 1.0F).endVertex();
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityFab entity) {
        return TEXTURE;
    }

    private static final class Fins extends ModelBase {
        private final ModelRenderer transverse;
        private final ModelRenderer longitudinal;
        private final ModelRenderer tailRing;

        Fins() {
            textureWidth = 128;
            textureHeight = 128;

            transverse = new ModelRenderer(this, 0, 64);
            transverse.addBox(-9, 25, -0.75F, 18, 6, 1);
            longitudinal = new ModelRenderer(this, 40, 64);
            longitudinal.addBox(-9, 25, -0.75F, 18, 6, 1);
            longitudinal.rotateAngleY = (float) Math.PI / 2;
            tailRing = new ModelRenderer(this, 80, 64);
            tailRing.addBox(-4.5F, 28, -4.5F, 9, 2, 9);
        }

        void render() {
            GlStateManager.color(1, 1, 1, 1);
            transverse.render(1 / 16.0F);
            longitudinal.render(1 / 16.0F);
            tailRing.render(1 / 16.0F);
        }
    }
}
