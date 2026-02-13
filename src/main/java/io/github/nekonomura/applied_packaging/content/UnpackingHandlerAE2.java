package io.github.nekonomura.applied_packaging.content;

import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IInWorldGridNodeHost;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.me.helpers.MachineSource;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public enum UnpackingHandlerAE2 implements UnpackingHandler {
    INSTANCE;
    private UnpackingHandlerAE2() {
    }
    // level, pos(Node), state, side, items, orderContext, simulate
    @Override
    public boolean unpack(Level level, BlockPos pos, BlockState state, Direction side, List<ItemStack> items, @Nullable PackageOrderWithCrafts orderContext, boolean simulate) {
        BlockEntity targetBE = level.getBlockEntity(pos);
        if (!(targetBE instanceof MEPackagerBlockEntity)) {
            return false;
        }
        IActionSource actionSource = new MachineSource((MEPackagerBlockEntity)targetBE); // 操作主体を定義
        IManagedGridNode targetNode = ((IGridConnectedBlockEntity)targetBE).getMainNode();
        if (targetNode == null|| !targetNode.isReady()) {
            return false;
        }
        MEStorage storage = targetNode.getGrid().getStorageService().getInventory();
        // シュミレーションじゃない時の処理
        if (!simulate) {
            for(ItemStack itemStack : items) {
                storage.insert(AEItemKey.of(itemStack), itemStack.getCount(), Actionable.MODULATE, actionSource);
            }
            return true;
        }
        //  シュミレーションの時の処理
        // 入りきらなかったらfalseを返す．
        for(ItemStack itemStack : items) {
            long remainder = storage.insert(AEItemKey.of(itemStack), itemStack.getCount(), Actionable.SIMULATE, actionSource);
            if (remainder != 0) {
                return false;
            }
        }
        return true;
    }
}
