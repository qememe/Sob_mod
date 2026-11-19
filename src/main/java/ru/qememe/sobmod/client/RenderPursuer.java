package ru.qememe.sobmod.client;

import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.util.ResourceLocation;
import ru.qememe.sobmod.EntityPursuer;
import ru.qememe.sobmod.SobMod;

final class RenderPursuer extends RenderBiped<EntityPursuer> {
    private static final ResourceLocation TEXTURE = new ResourceLocation(SobMod.MOD_ID, "textures/entity/pursuer.png");

    RenderPursuer(RenderManager manager) {
        super(manager, new Uniform(), 0.47F);
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityPursuer pursuer) {
        return TEXTURE;
    }

    private static final class Uniform extends ModelBiped {
        Uniform() {
            super(0, 0, 64, 64);
            bipedHeadwear.showModel = false;
            ModelRenderer crown = new ModelRenderer(this, 0, 36);
            crown.addBox(-4.5F, -9, -4.5F, 9, 3, 9);
            bipedHead.addChild(crown);
            ModelRenderer visor = new ModelRenderer(this, 0, 49);
            visor.addBox(-4.0F, -6.25F, -6.5F, 8, 1, 3);
            bipedHead.addChild(visor);
            ModelRenderer pouch = new ModelRenderer(this, 28, 49);
            pouch.addBox(-3.75F, 8, -3, 3, 3, 1);
            bipedBody.addChild(pouch);
            ModelRenderer pack = new ModelRenderer(this, 39, 35);
            pack.addBox(-2.75F, 2, 2, 5, 7, 2);
            bipedBody.addChild(pack);
        }

        @Override
        public void setVisible(boolean visible) {
            super.setVisible(visible);
            bipedHeadwear.showModel = false;
        }
    }
}
