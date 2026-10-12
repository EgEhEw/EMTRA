package org.mtr.altplatforms.mixin;

import org.mtr.altplatforms.duck.IAltRoutePlatformData;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.generated.data.RoutePlatformDataSchema;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.WriterBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RoutePlatformData.class, remap = false)
public abstract class RoutePlatformDataMixin extends RoutePlatformDataSchema implements IAltRoutePlatformData {

	protected RoutePlatformDataMixin(long platformId) {
		super(platformId);
	}

	protected RoutePlatformDataMixin(ReaderBase readerBase) {
		super(readerBase);
	}

	@Unique
	private final LongArrayList altplatforms$alternatePlatformIds = new LongArrayList();
	@Unique
	private final ObjectArrayList<Platform> altplatforms$alternatePlatforms = new ObjectArrayList<>();

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

	@Inject(method = "writePlatformCache", at = @At("TAIL"))
	private void altplatforms$onWritePlatformCache(Route route, Long2ObjectOpenHashMap<Platform> platformIdMap, CallbackInfo ci) {
		altplatforms$alternatePlatforms.clear();
		for (int i = 0; i < altplatforms$alternatePlatformIds.size(); i++) {
			final Platform altPlatform = platformIdMap.get(altplatforms$alternatePlatformIds.getLong(i));
			if (altPlatform != null) {
				altplatforms$alternatePlatforms.add(altPlatform);
				altPlatform.routes.add(route);
				altPlatform.routeColors.add(route.getColor());
			}
		}
	}
}
