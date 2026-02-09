package io.github.nekonomura.applied_packaging.content;


import appeng.api.config.Actionable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IManagedGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingRequester;
import appeng.api.stacks.AEKey;
import appeng.api.storage.MEStorage;
import appeng.api.upgrades.IUpgradeableObject;
import appeng.api.util.IConfigManager;
import appeng.api.util.IConfigurableObject;
import appeng.helpers.InterfaceLogicHost;
import com.google.common.collect.ImmutableSet;
import org.jetbrains.annotations.Nullable;

public class MEPackagerLogic/* implements
        ICraftingRequester,
        IUpgradeableObject,
        IConfigurableObject
        */
{
    /*
    protected final MEPackagerLogicHost host;
    protected final IManagedGridNode mainNode;
    private @Nullable MEStorage networkStorage;

    MEPackagerLogic(MEPackagerLogicHost host, IManagedGridNode mainNode) {
        this.host = host;
        this.mainNode = mainNode;
    }
    public void gridChanged() {
        this.networkStorage = this.mainNode.getGrid().getStorageService().getInventory();
        this.notifyNeighbors();
    }

    private void notifyNeighbors() {

    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return null;
    }

    @Override
    public long insertCraftedItems(ICraftingLink iCraftingLink, AEKey aeKey, long l, Actionable actionable) {
        return 0;
    }

    @Override
    public void jobStateChange(ICraftingLink iCraftingLink) {

    }

    @Override
    public @Nullable IGridNode getActionableNode() {
        return null;
    }

    @Override
    public IConfigManager getConfigManager() {
        return null;
    }
    */
}
