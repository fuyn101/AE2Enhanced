package com.github.aeddddd.ae2enhanced.client.model;

import com.github.aeddddd.ae2enhanced.AE2Enhanced;
import com.github.aeddddd.ae2enhanced.item.ItemGasDrop;
import com.github.aeddddd.ae2enhanced.util.FakeItemRegister;
import mekanism.api.gas.GasStack;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ItemCameraTransforms;
import net.minecraft.client.renderer.block.model.ItemOverrideList;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.vertex.VertexFormat;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraftforge.client.model.ICustomModelLoader;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ItemLayerModel;
import net.minecraftforge.common.model.IModelState;
import net.minecraftforge.common.model.TRSRTransformation;
import org.apache.commons.lang3.tuple.Pair;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.vecmath.Matrix4f;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * 气体假物品的 IModel / ICustomModelLoader 实现。
 * 通过 ItemOverrideList 根据 ItemStack 的 NBT 动态返回带正确气体纹理的 BakedModel。
 *
 * 设计参考 ae2fc 的 GasPacketModel。
 */
public class GasDropModel extends FluidDropModel {

    public static final ResourceLocation MODEL_LOCATION = new ResourceLocation(AE2Enhanced.MOD_ID, "gas_drop");

    @Override
    public IBakedModel bake(IModelState state, VertexFormat format, Function<ResourceLocation, TextureAtlasSprite> bakedTextureGetter) {
        return new BakedGasDropModel(state, format);
    }

    public static class Loader implements ICustomModelLoader {
        @Override
        public boolean accepts(ResourceLocation modelLocation) {
            String loc = modelLocation.toString();
            String expected = MODEL_LOCATION.toString();
            return loc.equals(expected) || loc.startsWith(expected + "#");
        }

        @Override
        public IModel loadModel(ResourceLocation modelLocation) {
            return new GasDropModel();
        }

        @Override
        public void onResourceManagerReload(IResourceManager resourceManager) {
        }
    }

    protected static class BakedGasDropModel extends BakedFluidDropModel {
        public BakedGasDropModel(IModelState modelState, VertexFormat vertexFormat) {
            super(modelState, vertexFormat);
        }

        @Override
        protected ItemOverrideList genOverrides() {
            return new OverrideCache(this, vertexFormat);
        }

        @Override
        protected IBakedModel genDefaultOverrides() {
            return ((OverrideCache) overrides).resolve(new GasStack(mekanism.common.MekanismFluids.Hydrogen, 1000));
        }
    }

    protected static class OverrideCache extends ItemOverrideList {
        private final BakedGasDropModel parent;
        private final VertexFormat vertexFormat;
        private final Map<String, OverrideModel> cache = new HashMap<>();

        public OverrideCache(BakedGasDropModel parent, VertexFormat vertexFormat) {
            super(Collections.emptyList());
            this.parent = parent;
            this.vertexFormat = vertexFormat;
        }

        @Nonnull
        @Override
        public IBakedModel handleItemState(@Nonnull IBakedModel originalModel, ItemStack stack, @Nullable World world, @Nullable EntityLivingBase entity) {
            if (!ItemGasDrop.isGasDrop(stack)) {
                return originalModel;
            }
            GasStack gas = FakeItemRegister.getStack(stack);
            return gas != null ? resolve(gas) : originalModel;
        }

        public OverrideModel resolve(GasStack gas) {
            String key = gas.getGas().getName();
            return cache.computeIfAbsent(key, k -> new OverrideModel(gas, parent.modelTransform, vertexFormat));
        }
    }

    protected static class OverrideModel implements IBakedModel {
        private final TextureAtlasSprite texture;
        private final List<BakedQuad> quads;

        public OverrideModel(GasStack gasStack, Optional<TRSRTransformation> modelTransform, VertexFormat vertexFormat) {
            // 使用 TextureMap atlas sprite（与 ae2fc GasPacketModel.getSprite() 等效，
            // 但在我们的环境中 Mekanism 的 sprite 缓存对大多数气体未初始化，getSprite() 返回 null/白色）
            ResourceLocation icon = gasStack.getGas().getIcon();
            if (icon != null) {
                this.texture = Minecraft.getMinecraft().getTextureMapBlocks().getAtlasSprite(icon.toString());
            } else {
                this.texture = Minecraft.getMinecraft().getTextureMapBlocks().getMissingSprite();
            }
            this.quads = ItemLayerModel.getQuadsForSprite(1, this.texture, vertexFormat, modelTransform);
        }

        @Nonnull
        @Override
        public List<BakedQuad> getQuads(@Nullable IBlockState state, @Nullable EnumFacing side, long rand) {
            return quads;
        }

        @Override
        public boolean isAmbientOcclusion() {
            return false;
        }

        @Override
        public boolean isGui3d() {
            return false;
        }

        @Override
        public boolean isBuiltInRenderer() {
            return false;
        }

        @Override
        public TextureAtlasSprite getParticleTexture() {
            return texture;
        }

        @Nonnull
        @Override
        public ItemCameraTransforms getItemCameraTransforms() {
            return FluidDropModel.CAMERA_TRANSFORMS;
        }

        @Nonnull
        @Override
        public ItemOverrideList getOverrides() {
            return ItemOverrideList.NONE;
        }
    }
}
