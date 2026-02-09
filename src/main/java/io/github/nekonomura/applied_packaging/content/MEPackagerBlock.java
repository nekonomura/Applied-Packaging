package io.github.nekonomura.applied_packaging.content;

import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import io.github.nekonomura.applied_packaging.Registration;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class MEPackagerBlock extends PackagerBlock{

    public MEPackagerBlock(Properties properties) {
        super(properties);
        BlockState defaultBlockState = this.defaultBlockState();
        if (defaultBlockState.hasProperty(LINKED)) {
            defaultBlockState = (BlockState)defaultBlockState.setValue(LINKED, false);
        }

        this.registerDefaultState((BlockState)defaultBlockState.setValue(POWERED, false));
    }

    public BlockEntityType<? extends PackagerBlockEntity> getBlockEntityType() {
        return (BlockEntityType) Registration.MEPACKAGER_BLOCK_ENTITY.get();
    }
}
