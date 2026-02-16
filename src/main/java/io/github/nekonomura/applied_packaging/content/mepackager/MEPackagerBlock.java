package io.github.nekonomura.applied_packaging.content.mepackager;

import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.parts.networking.CablePart;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import io.github.nekonomura.applied_packaging.Registration;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

public class MEPackagerBlock extends PackagerBlock{

    public MEPackagerBlock(Properties properties) {
        super(properties);
        BlockState defaultBlockState = this.defaultBlockState();
        if (defaultBlockState.hasProperty(LINKED)) {
            defaultBlockState = defaultBlockState.setValue(LINKED, false);
        }
        this.registerDefaultState(defaultBlockState);
    }

    public BlockEntityType<? extends PackagerBlockEntity> getBlockEntityType() {
        return Registration.MEPACKAGER_BLOCK_ENTITY.get();
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Capability<IItemHandler> itemCap = ForgeCapabilities.ITEM_HANDLER;
        Direction preferredFacing = null;

        // ケーブルに優先的に向ける処理．
        for(Direction face : context.getNearestLookingDirections()) {
            BlockEntity be = context.getLevel().getBlockEntity(context.getClickedPos().relative(face));
            if (be instanceof IPartHost partHost) {
                IPart centerPart = partHost.getPart(null);
                if (centerPart instanceof CablePart) {
                    preferredFacing = face.getOpposite();
                    break;
                }
            }
        }

        // 普通の方向決め処理．
        Player player = context.getPlayer();
        if (preferredFacing == null) {
            Direction facing = context.getNearestLookingDirection();
            preferredFacing = player != null && player.isShiftKeyDown() ? facing : facing.getOpposite();
        }

        return super.getStateForPlacement(context).setValue(FACING, preferredFacing);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(new Property[]{LINKED}));
    }
}
