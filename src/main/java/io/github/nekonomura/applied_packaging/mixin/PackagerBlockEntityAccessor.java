package io.github.nekonomura.applied_packaging.mixin;

import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = PackagerBlockEntity.class,remap = false)
public interface PackagerBlockEntityAccessor {
    @Invoker("getLinkPos")
    public BlockPos callGetLinkPos();
}
