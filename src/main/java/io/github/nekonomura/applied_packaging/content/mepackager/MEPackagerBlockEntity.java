package io.github.nekonomura.applied_packaging.content.mepackager;

import appeng.api.config.Actionable;
import appeng.api.networking.*;
import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.KeyCounter;
import appeng.api.storage.MEStorage;
import appeng.api.util.AECableType;
import appeng.me.helpers.IGridConnectedBlockEntity;
import appeng.me.helpers.MachineSource;
import com.simibubi.create.api.packager.unpacking.UnpackingHandler;
import com.simibubi.create.compat.computercraft.events.PackageEvent;
import com.simibubi.create.content.logistics.BigItemStack;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packager.*;
import com.simibubi.create.content.logistics.packagerLink.PackagerLinkBlockEntity;
import com.simibubi.create.content.logistics.stockTicker.PackageOrderWithCrafts;
import com.simibubi.create.foundation.item.ItemHelper;
import io.github.nekonomura.applied_packaging.Registration;
import io.github.nekonomura.applied_packaging.mixin.PackagerBlockEntityAccessor;
import io.github.nekonomura.applied_packaging.util.Annotations;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.ItemStackHandler;
import appeng.api.networking.GridFlags;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;

import static io.github.nekonomura.applied_packaging.AppliedPackaging.getLogger;

public class MEPackagerBlockEntity extends PackagerBlockEntity
        // AE2本体のクラスの場合，リスナーはコンポジットされているようですが一旦は直接実装する．
        implements IGridNodeListener<MEPackagerBlockEntity>,
        IGridConnectedBlockEntity,
        IInWorldGridNodeHost,
        IActionHost
        //MEPackagerLogicHost
{
    private final IManagedGridNode mainNode;

    // コンストラクタ．
    public MEPackagerBlockEntity(BlockEntityType<?> typeIn, BlockPos pos, BlockState state) {
        super(typeIn, pos, state);
        this.mainNode = this.createMainNode()
                .setVisualRepresentation(this.getItem())
                .setInWorldNode(true)
                .setTagName("Packager")
                .setFlags(GridFlags.REQUIRE_CHANNEL);
        getLogger().info("MEPackagerBlockEntity created at {}", pos);
    }

    private ItemStack getItem() {
        return Registration.MEPACKAGER.asStack();
    }

    @Override
    public @Nullable IGridNode getGridNode(Direction dir) {
        BlockState state = this.getBlockState();
        if (state.hasProperty(PackagerBlock.FACING)) {
            if (dir == state.getValue(PackagerBlock.FACING).getOpposite()) {
                return this.mainNode.getNode();
            }
        }
        return null;
    }

    @Override
    public AECableType getCableConnectionType(Direction dir) {
        BlockState state = this.getBlockState();
        if (state.hasProperty(MEPackagerBlock.FACING)) {
            if (dir == state.getValue(MEPackagerBlock.FACING).getOpposite()) {
                return AECableType.SMART;
            }
        }
        return AECableType.NONE;
    }

    protected IManagedGridNode createMainNode() {
        return GridHelper.createManagedNode(this, this);
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

    //ここからcreate要素強め．

    // 送信
    @Annotations.AICode(checked = false)
    @Override
    public void attemptToSend(List<PackagingRequest> queuedRequests) {
        // 1. 基本チェック (箱を持っている、アニメーション中、ボタンクールダウン中は処理しない)
        if (queuedRequests == null && (!this.heldBox.isEmpty() || this.animationTicks != 0 || this.buttonCooldown > 0)) {
            return;
        }

        // AE2ネットワークが利用可能かチェック
        if (this.mainNode == null || !this.mainNode.isReady()) {
            return;
        }
        IGrid grid = this.mainNode.getGrid();
        MEStorage meStorage = grid.getStorageService().getInventory();
        IActionSource actionSource = new MachineSource(this); // 操作主体を定義
        boolean anyItemPresent = false;
        ItemStackHandler extractedItems = new ItemStackHandler(PackageItem.SLOTS); // 箱の中身 (9スロット)
        ItemStack extractedPackageItem = ItemStack.EMPTY; // Bulky Item等の既存パッケージ
        PackagingRequest nextRequest = null;
        String fixedAddress = null;
        int fixedOrderId = 0;
        int linkIndexInOrder = 0;
        boolean finalLinkInOrder = false;
        int packageIndexAtLink = 0;
        boolean finalPackageAtLink = false;
        PackageOrderWithCrafts orderContext = null;
        // リクエストキューモードの初期化
        boolean requestQueue = queuedRequests != null;
        if (requestQueue && !queuedRequests.isEmpty()) {
            nextRequest = queuedRequests.get(0);
            fixedAddress = nextRequest.address();
            fixedOrderId = nextRequest.orderId();
            linkIndexInOrder = nextRequest.linkIndex();
            finalLinkInOrder = nextRequest.finalLink().booleanValue();
            packageIndexAtLink = nextRequest.packageCounter().getAndIncrement();
            orderContext = nextRequest.context();
        } else {
            // 手動発送モード (AE2連携版では一旦未実装とするか、特定のインポートバス挙動にするか要検討)
            // ここではリクエストがない場合は何もしない実装とします
            return;
        }
        Outer:
        for (int i = 0; i < PackageItem.SLOTS; i++) {
            boolean continuePacking = true;

            while (continuePacking) {
                continuePacking = false;
                // AE2から抽出を試みる
                // リクエストされたアイテムのキーを取得
                AEItemKey key = AEItemKey.of(nextRequest.item());
                if (key == null) break; // 無効なアイテム

                // 抽出可能数を計算 (最大64個 または リクエスト残り数)
                long initialCount = Math.min(64, nextRequest.getCount());

                // AE2から実際に抽出 (MODULATE = 実行)
                long extractedCount = meStorage.extract(key, initialCount, Actionable.MODULATE, actionSource);

                if (extractedCount <= 0) {
                    // 在庫がない場合
                    // TODO: ここで自動クラフト(Auto-Crafting)を発行するロジックを追加可能
                    // 今回は「在庫切れ」としてスキップ
                    break;
                }

                ItemStack extracted = key.toStack((int) extractedCount);
                // Bulky Item (箱に入らないもの) の判定
                boolean bulky = !extracted.getItem().canFitInsideContainerItems();
                if (bulky && anyItemPresent) {
                    // すでに箱に物が入っているなら、このBulky Itemは次の箱にするため戻す
                    meStorage.insert(key, extractedCount, Actionable.MODULATE, actionSource);
                    continue;
                }
                anyItemPresent = true;

                // 一時インベントリ(extractedItems)に詰める
                int leftovers = ItemHandlerHelper.insertItemStacked(extractedItems, extracted.copy(), false).getCount();
                int transferred = extracted.getCount() - leftovers;

                // 余りが出た場合 (理論上起こりにくいが念のため)、AE2に戻す
                if (leftovers > 0) {
                    meStorage.insert(key, leftovers, Actionable.MODULATE, actionSource);
                }

                if (extracted.getItem() instanceof PackageItem) {
                    extractedPackageItem = extracted;
                }

                // リクエストの更新
                nextRequest.subtract(transferred);

                // まだこのリクエストが完了していないなら、ループ継続 (同じアイテムをさらに詰める)
                if (!nextRequest.isEmpty()) {
                    if (bulky) break Outer;
                    continue;
                }
                // --- リクエスト完了 & 次のリクエストへ (Defragmentation) ---
                finalPackageAtLink = true;
                queuedRequests.remove(0);

                if (queuedRequests.isEmpty()) {
                    break Outer;
                }

                int previousCount = nextRequest.packageCounter().intValue();
                nextRequest = queuedRequests.get(0);

                // 次の注文が別の宛先なら、ここで箱を閉じる
                if (!fixedAddress.equals(nextRequest.address()) || fixedOrderId != nextRequest.orderId()) {
                    break Outer;
                }

                // 同じ宛先なら、同じ箱に詰め続ける
                nextRequest.packageCounter().setValue(previousCount);
                finalPackageAtLink = false;
                continuePacking = true;
                if (nextRequest.context() != null) {
                    orderContext = nextRequest.context();
                }

                if (bulky) break Outer;
                break;
            }
        }
        // --- 梱包ループ終了 ---

        // 何も詰められなかった場合
        if (!anyItemPresent) {
            // リクエストが進まなかった場合、キューから削除すべきか？
            // 元コードでは remove(0) しているが、AE2在庫切れの場合は「待機」させるべきかもしれない。
            // ここでは元コードの挙動に合わせて削除しておく (無限ループ防止)
            if (nextRequest != null && nextRequest.isEmpty()) {
                queuedRequests.remove(0);
            }
            return;
        }

        // --- 箱(PackageItem)の生成 ---
        ItemStack createdBox = extractedPackageItem.isEmpty() ?
                PackageItem.containing(extractedItems) : extractedPackageItem.copy();

        // イベント発火 (ComputerCraft等用)
        computerBehaviour.prepareComputerEvent(new PackageEvent(createdBox, "package_created"));

        PackageItem.clearAddress(createdBox);
        if (fixedAddress != null) {
            PackageItem.addAddress(createdBox, fixedAddress);
        }

        // 注文情報の書き込み
        PackageItem.setOrder(createdBox, fixedOrderId, linkIndexInOrder, finalLinkInOrder, packageIndexAtLink, finalPackageAtLink, orderContext);

        // --- 重要: Create論理在庫の引き落とし ---
        // Mixinを使ってリンク位置を取得し、論理在庫を減らす
        BlockPos linkPos = ((PackagerBlockEntityAccessor) this).callGetLinkPos();
        if (extractedPackageItem.isEmpty() && linkPos != null) {
            BlockEntity be = this.level.getBlockEntity(linkPos);
            if (be instanceof PackagerLinkBlockEntity plbe) {
                plbe.behaviour.deductFromAccurateSummary(extractedItems);
            }
        }
        // --- 搬出処理 ---
        if (this.heldBox.isEmpty() && this.animationTicks == 0) {
            this.heldBox = createdBox;
            this.animationInward = false; // 搬出アニメーション
            this.animationTicks = CYCLE;
            this.triggerStockCheck(); // 在庫再チェック
            this.notifyUpdate();
        } else {
            this.queuedExitingPackages.add(new BigItemStack(createdBox, 1));
        }
    }

    @Annotations.AICode(checked = true)
    @Override
    public InventorySummary getAvailableItems() {
        // 1. ノードとグリッドの安全性チェック
        if (this.mainNode == null || !this.mainNode.isReady()) {
            return InventorySummary.EMPTY;
        }

        IGrid grid = this.mainNode.getGrid();
        MEStorage meStorage = grid.getStorageService().getInventory();

        // 2. 新しいサマリを作成
        InventorySummary summary = new InventorySummary();

        // 3. AE2の全在庫を取得 (重い処理なので注意)
        KeyCounter counter = meStorage.getAvailableStacks();

        // 4. 全アイテムをループしてCreate形式に変換
        for (Object2LongMap.Entry<AEKey> entry : counter) {
            AEKey key = entry.getKey();
            long amount = entry.getLongValue();

            // アイテムのみを対象とする（流体などは除外）
            if (key instanceof AEItemKey itemKey) {
                // AE2の量は long だが、CreateのInventorySummaryは int を要求する
                // 21億個を超える場合は、Integer.MAX_VALUE に丸める
                int createAmount = (int) Math.min(amount, Integer.MAX_VALUE);

                // アイテムスタックの生成 (個数は1で作成し、addメソッドで総数を指定)
                ItemStack stack = itemKey.toStack(1);

                // サマリに追加
                summary.add(stack, createAmount);
            }
        }

        // 5. 結果を返す
        // 親クラスのキャッシュ機構 (availableItems) を更新
        // (PackagerBlockEntityのフィールドはprivateなどで直接アクセスしづらいため、
        //  ここで返すだけでStockTickerが受け取ってくれる設計になっています)
        return summary;
    }
    @SuppressWarnings("UnstableApiUsage")
    @Override
    public boolean unwrapBox(ItemStack box, boolean simulate) {
        // 動作中だったら開封失敗
        if (this.animationTicks > 0)return false;
        Objects.requireNonNull(this.level);
        ItemStackHandler contents = PackageItem.getContents(box);
        List<ItemStack> items = ItemHelper.getNonEmptyStacks(contents);
        // 小包を開封して中身が空だったら開封済みを通知
        if (items.isEmpty()) return true;
        PackageOrderWithCrafts orderContext = PackageItem.getOrderContext(box);
        Direction facing = (Direction)this.getBlockState().getOptionalValue(PackagerBlock.FACING).orElse(Direction.UP);
        BlockPos target = this.worldPosition;
        BlockState targetState = this.level.getBlockState(target);
        UnpackingHandler toUse = UnpackingHandlerAE2.INSTANCE;
        boolean unpacked = toUse.unpack(this.level, target, targetState, facing, items, orderContext, simulate);
        // アニメーション
        if (unpacked && !simulate) {
            this.computerBehaviour.prepareComputerEvent(new PackageEvent(box, "package_received"));
            this.previouslyUnwrapped = box;
            this.animationInward = true;
            this.animationTicks = 20;
            this.notifyUpdate();
        }

        return unpacked;
    }

    @Override
    public void read(CompoundTag tag, boolean clientPacket) {
        super.read(tag, clientPacket);
        if (this.mainNode != null) {
            this.mainNode.loadFromNBT(tag);
        }
    }
    @Override
    public void initialize() {
        super.initialize();
        if (this.mainNode != null) {
            this.mainNode.create(this.level, this.worldPosition);
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (this.mainNode != null) {
            this.mainNode.destroy();
        }
    }
}
