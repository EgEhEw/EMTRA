package com.egehew.emtra.mixin;

import com.egehew.emtra.duck.IAltRoutePlatformData;
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
	private final LongArrayList emtra$alternatePlatformIds = new LongArrayList();
	@Unique
	private final ObjectArrayList<Platform> emtra$alternatePlatforms = new ObjectArrayList<>();

	@Override
	public long emtra$getPlatformId() {
		return platformId;
	}

	@Override
	public LongArrayList emtra$getAlternatePlatformIds() {
		return emtra$alternatePlatformIds;
	}

	@Override
	public ObjectArrayList<Platform> emtra$getAlternatePlatforms() {
		return emtra$alternatePlatforms;
	}

	@Inject(method = "updateData", at = @At("TAIL"))
	private void emtra$onUpdateData(ReaderBase readerBase, CallbackInfo ci) {
		readerBase.iterateLongArray("alternatePlatformIds", emtra$alternatePlatformIds::clear, emtra$alternatePlatformIds::add);
	}

	@Inject(method = "serializeData", at = @At("TAIL"))
	private void emtra$onSerializeData(WriterBase writerBase, CallbackInfo ci) {
		final WriterBase.Array array = writerBase.writeArray("alternatePlatformIds");
		emtra$alternatePlatformIds.forEach(array::writeLong);
	}
}
