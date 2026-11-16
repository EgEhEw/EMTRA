package com.egehew.emtra.mixin;

import com.egehew.emtra.duck.IAltRoutePlatformData;
import com.egehew.emtra.compat.MagicMtrCompat;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = RoutePlatformData.class, remap = false)
public abstract class RoutePlatformDataMixin {

	@Inject(method = "writePlatformCache", at = @At("TAIL"))
	private void emtra$onWritePlatformCache(Route route, Long2ObjectOpenHashMap<Platform> platformIdMap, CallbackInfo ci) {
		if ((Object) this instanceof IAltRoutePlatformData) {
			final IAltRoutePlatformData duck = (IAltRoutePlatformData) (Object) this;
			duck.emtra$getAlternatePlatforms().clear();
			final LongArrayList altIds = MagicMtrCompat.isPresent() ? new LongArrayList() : new LongArrayList(duck.emtra$getAlternatePlatformIds());
			if (((RoutePlatformData) (Object) this).platform != null) {
				for (final long alternateId : MagicMtrCompat.getAlternatePlatformIds(route, ((RoutePlatformData) (Object) this).platform)) {
					if (alternateId != duck.emtra$getPlatformId() && !altIds.contains(alternateId)) {
						altIds.add(alternateId);
					}
				}
			}
			for (int i = 0; i < altIds.size(); i++) {
				final Platform altPlatform = platformIdMap.get(altIds.getLong(i));
				if (altPlatform != null) {
					duck.emtra$getAlternatePlatforms().add(altPlatform);
					altPlatform.routes.add(route);
					altPlatform.routeColors.add(route.getColor());
				}
			}
		}
	}
}
