package io.github.nekonomura.applied_packaging;

import com.simibubi.create.foundation.data.BuilderTransformers;
import com.simibubi.create.foundation.data.CreateRegistrate;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.BlockEntry;
import io.github.nekonomura.applied_packaging.content.mepackager.MEPackagerBlock;
import io.github.nekonomura.applied_packaging.content.mepackager.MEPackagerBlockEntity;
import com.simibubi.create.content.logistics.packager.PackagerRenderer;
import com.simibubi.create.content.logistics.packager.PackagerVisual;

public class Registration {
    private static final CreateRegistrate REGISTRATE = AppliedPackaging.getRegistrate();
    public static final BlockEntry<MEPackagerBlock> MEPACKAGER = REGISTRATE.block("mepackager", MEPackagerBlock::new)
            .transform(BuilderTransformers.packager())
            .lang("MEPackager")
            .register();
    public static final BlockEntityEntry<MEPackagerBlockEntity> MEPACKAGER_BLOCK_ENTITY = REGISTRATE
            .blockEntity("mepackager", MEPackagerBlockEntity::new)
            .visual(() -> PackagerVisual::new, true)
            .validBlocks(MEPACKAGER)
            .renderer(() -> PackagerRenderer::new)
            .register();
    public static void register() {
    }
}
