package io.github.nekonomura.applied_packaging.content;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IManagedGridNode;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.me.ManagedGridNode;
import appeng.me.helpers.BlockEntityNodeListener;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.parts.AEBasePart;
import appeng.parts.misc.InterfacePart;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.PackagerBlock;
import com.simibubi.create.content.logistics.packager.PackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerItemHandler;
import com.simibubi.create.content.logistics.packager.PackagingRequest;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlock;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.advancement.AllAdvancements;
import io.github.nekonomura.applied_packaging.mixin.PackagerBlockEntityAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;


import java.util.List;

import static io.github.nekonomura.applied_packaging.AppliedPackaging.getLogger;

public class MEPackagerBlockEntity extends PackagerBlockEntity
        // AE2本体のクラスの場合，リスナーはコンポジットされているようですが一旦は直接実装する．
        implements IGridNodeListener<MEPackagerBlockEntity>,
        IGridConnectedBlockEntity
        //MEPackagerLogicHost
{
    private final ManagedGridNode mainNode;

    // 梱包用のバッファ
    private ItemStackHandler internalBuffer = new ItemStackHandler(9);
    // コンストラクタ．
    public MEPackagerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.mainNode = new ManagedGridNode(this, this);
        getLogger().info("MEPackagerBlockEntity created at {}", pos);
    }

    // メインノードのゲッター．
    @Override
    public IManagedGridNode getMainNode() {
        return this.mainNode;
    }

    // セーブしなきゃらなないときにセーブするって結構小泉進次郎だよね．
    @Override
    public void onSaveChanges(MEPackagerBlockEntity mePackagerBlockEntity, IGridNode iGridNode) {
        this.saveChanges();
    }

    @Override
    public void saveChanges() {
        this.setChanged();
    }


    // 送信
    @Override
    public void attemptToSend(List<PackagingRequest> queuedRequests) {
        if (queuedRequests != null || this.heldBox.isEmpty() && this.animationTicks == 0 && this.buttonCooldown <= 0) {
            IItemHandler targetInv = (IItemHandler)this.targetInventory.getInventory();
            if (targetInv != null && !(targetInv instanceof PackagerItemHandler)) {
                boolean anyItemPresent = false;
                ItemStackHandler extractedItems = new ItemStackHandler(9);
                ItemStack extractedPackageItem = ItemStack.EMPTY;
                PackagingRequest nextRequest = null;
                String fixedAddress = null;
                int fixedOrderId = 0;
                int linkIndexInOrder = 0;
                boolean finalLinkInOrder = false;
                int packageIndexAtLink = 0;
                boolean finalPackageAtLink = false;
                PackageOrderWithCrafts orderContext = null;
                boolean requestQueue = queuedRequests != null;
                if (requestQueue && !queuedRequests.isEmpty()) {
                    nextRequest = (PackagingRequest)queuedRequests.get(0);
                    fixedAddress = nextRequest.address();
                    fixedOrderId = nextRequest.orderId();
                    linkIndexInOrder = nextRequest.linkIndex();
                    finalLinkInOrder = nextRequest.finalLink().booleanValue();
                    packageIndexAtLink = nextRequest.packageCounter().getAndIncrement();
                    orderContext = nextRequest.context();
                }

                label151:
                for(int i = 0; i < 9; ++i) {
                    boolean continuePacking = true;

                    while(continuePacking) {
                        continuePacking = false;

                        for(int slot = 0; slot < targetInv.getSlots(); ++slot) {
                            int initialCount = requestQueue ? Math.min(64, nextRequest.getCount()) : 64;
                            ItemStack extracted = targetInv.extractItem(slot, initialCount, true);
                            if (!extracted.isEmpty() && (!requestQueue || ItemHandlerHelper.canItemStacksStack(extracted, nextRequest.item()))) {
                                boolean bulky = !extracted.getItem().canFitInsideContainerItems();
                                if (!bulky || !anyItemPresent) {
                                    anyItemPresent = true;
                                    int leftovers = ItemHandlerHelper.insertItemStacked(extractedItems, extracted.copy(), false).getCount();
                                    int transferred = extracted.getCount() - leftovers;
                                    targetInv.extractItem(slot, transferred, false);
                                    if (extracted.getItem() instanceof PackageItem) {
                                        extractedPackageItem = extracted;
                                    }

                                    if (!requestQueue) {
                                        if (bulky) {
                                            break label151;
                                        }
                                    } else {
                                        nextRequest.subtract(transferred);
                                        if (nextRequest.isEmpty()) {
                                            finalPackageAtLink = true;
                                            queuedRequests.remove(0);
                                            if (queuedRequests.isEmpty()) {
                                                break label151;
                                            }

                                            int previousCount = nextRequest.packageCounter().intValue();
                                            nextRequest = (PackagingRequest)queuedRequests.get(0);
                                            if (!fixedAddress.equals(nextRequest.address()) || fixedOrderId != nextRequest.orderId()) {
                                                break label151;
                                            }

                                            nextRequest.packageCounter().setValue(previousCount);
                                            finalPackageAtLink = false;
                                            continuePacking = true;
                                            if (nextRequest.context() != null) {
                                                orderContext = nextRequest.context();
                                            }

                                            if (bulky) {
                                                break label151;
                                            }
                                            break;
                                        }

                                        if (bulky) {
                                            break label151;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (!anyItemPresent) {
                    if (nextRequest != null) {
                        queuedRequests.remove(0);
                    }

                } else {
                    ItemStack createdBox = extractedPackageItem.isEmpty() ? PackageItem.containing(extractedItems) : extractedPackageItem.copy();
                    PackageItem.clearAddress(createdBox);
                    if (fixedAddress != null) {
                        PackageItem.addAddress(createdBox, fixedAddress);
                    }

                    if (requestQueue) {
                        PackageItem.setOrder(createdBox, fixedOrderId, linkIndexInOrder, finalLinkInOrder, packageIndexAtLink, finalPackageAtLink, orderContext);
                    }

                    if (!requestQueue && !this.signBasedAddress.isBlank()) {
                        PackageItem.addAddress(createdBox, this.signBasedAddress);
                    }

                    BlockPos linkPos = ((PackagerBlockEntityAccessor) this).callGetLinkPos();
                    if (extractedPackageItem.isEmpty() && linkPos != null) {
                        BlockEntity var27 = this.level.getBlockEntity(linkPos);
                        if (var27 instanceof PackagerLinkBlockEntity) {
                            PackagerLinkBlockEntity plbe = (PackagerLinkBlockEntity)var27;
                            plbe.behaviour.deductFromAccurateSummary(extractedItems);
                        }
                    }

                    if (this.heldBox.isEmpty() && this.animationTicks == 0) {
                        this.heldBox = createdBox;
                        this.animationInward = false;
                        this.animationTicks = 20;
                        this.triggerStockCheck();
                        this.notifyUpdate();
                    } else {
                        this.queuedExitingPackages.add(new BigItemStack(createdBox, 1));
                    }
                }
            }
        }
    }

}
