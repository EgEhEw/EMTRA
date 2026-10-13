package org.mtr.altplatforms.mixin;

import org.mtr.altplatforms.duck.IAltRoutePlatformData;
import org.mtr.core.data.Platform;
import org.mtr.core.generated.data.RoutePlatformDataSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.WriterBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RoutePlatformDataSchema.class, remap = false)
public abstract class RoutePlatformDataSchemaMixin implements IAltRoutePlatformData {

	@Shadow
	protected long platformId;

	@Unique
	private final LongArrayList altplatforms$alternatePlatformIds = new LongArrayList();
	@Unique
	private final ObjectArrayList<Platform> altplatforms$alternatePlatforms = new ObjectArrayList<>();

	@Override
	public long altplatforms$getPlatformId() {
		return platformId;
	}

	@Override
	public LongArrayList altplatforms$getAlternatePlatformIds() {
		return altplatforms$alternatePlatformIds;
	}

	@Override
	public ObjectArrayList<Platform> altplatforms$getAlternatePlatforms() {
		return altplatforms$alternatePlatforms;
	}

	@Inject(method = "updateData", at = @At("TAIL"))
	private void altplatforms$onUpdateData(ReaderBase readerBase, CallbackInfo ci) {
		readerBase.iterateLongArray("alternatePlatformIds", altplatforms$alternatePlatformIds::clear, altplatforms$alternatePlatformIds::add);
	}

	@Inject(method = "serializeData", at = @At("TAIL"))
	private void altplatforms$onSerializeData(WriterBase writerBase, CallbackInfo ci) {
		final WriterBase.Array array = writerBase.writeArray("alternatePlatformIds");
		altplatforms$alternatePlatformIds.forEach(array::writeLong);
	}
}
