package org.mtr.altplatforms.mixin;

import org.mtr.altplatforms.data.AlternatePath;
import org.mtr.altplatforms.data.PlatformStop;
import org.mtr.altplatforms.duck.IAltRoutePlatformData;
import org.mtr.altplatforms.duck.IDepotAlternatePaths;
import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.generated.data.DepotSchema;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.serializer.WriterBase;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongAVLTreeSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Depot.class, remap = false)
public abstract class DepotMixin extends DepotSchema {

	protected DepotMixin(TransportMode transportMode, Data data) {
		super(transportMode, data);
	}

	protected DepotMixin(ReaderBase readerBase, Data data) {
		super(readerBase, data);
	}

	@Shadow
	public ObjectArrayList<Route> routes;

	@Shadow
	@Final
	private LongAVLTreeSet generatingSidingIds;

	@Shadow
	@Final
	private ObjectArrayList<PathData> path;

	@Inject(method = "<init>(Lorg/mtr/core/serializer/ReaderBase;Lorg/mtr/core/data/Data;)V", at = @At("TAIL"))
	private void altplatforms$onInit(ReaderBase readerBase, Data data, CallbackInfo ci) {
		if ((Object) this instanceof IDepotAlternatePaths) {
			final ObjectArrayList<AlternatePath> list = ((IDepotAlternatePaths) (Object) this).altplatforms$getAlternatePaths();
			readerBase.iterateReaderArray("alternatePaths", list::clear, readerBaseChild -> list.add(new AlternatePath(readerBaseChild)));
		}
	}

	@Inject(method = "finishGeneratingPath", at = @At("TAIL"))
	private void altplatforms$onFinishGeneratingPath(long sidingId, CallbackInfo ci) {
		if (generatingSidingIds.isEmpty()) {
			altplatforms$generateAlternatePaths();
		}
	}

	@Inject(method = "updateData", at = @At("TAIL"))
	private void altplatforms$onUpdateData(ReaderBase readerBase, CallbackInfo ci) {
		if ((Object) this instanceof IDepotAlternatePaths) {
			final ObjectArrayList<AlternatePath> list = ((IDepotAlternatePaths) (Object) this).altplatforms$getAlternatePaths();
			readerBase.iterateReaderArray("alternatePaths", list::clear, readerBaseChild -> list.add(new AlternatePath(readerBaseChild)));
		}
	}

	@Inject(method = "serializeFullData", at = @At("TAIL"))
	private void altplatforms$onSerializeFullData(WriterBase writerBase, CallbackInfo ci) {
		if ((Object) this instanceof IDepotAlternatePaths) {
			writerBase.writeDataset(((IDepotAlternatePaths) (Object) this).altplatforms$getAlternatePaths(), "alternatePaths");
		}
	}

	@Inject(method = "writePathCache", at = @At("HEAD"))
	private void altplatforms$onWritePathCacheHead(CallbackInfo ci) {
		if ((Object) this instanceof IDepotAlternatePaths) {
			if (!path.isEmpty()) {
				altplatforms$generateAlternatePaths();
			}
		}
	}

	@Inject(method = "writePathCache", at = @At("TAIL"))
	private void altplatforms$onWritePathCache(CallbackInfo ci) {
		if ((Object) this instanceof IDepotAlternatePaths) {
			for (final AlternatePath altPath : ((IDepotAlternatePaths) (Object) this).altplatforms$getAlternatePaths()) {
				altPath.writePathCache(data, transportMode);
			}
		}
	}

	@Unique
	private void altplatforms$generateAlternatePaths() {
		if (!((Object) this instanceof IDepotAlternatePaths)) {
			return;
		}

		final ObjectArrayList<AlternatePath> altPaths = ((IDepotAlternatePaths) (Object) this).altplatforms$getAlternatePaths();
		altPaths.clear();

		final ObjectArrayList<PlatformStop> stops = new ObjectArrayList<>();
		long previousPlatformId = 0;
		for (final Route route : routes) {
			if (route == null) continue;
			for (int i = 0; i < route.getRoutePlatforms().size(); i++) {
				final RoutePlatformData rpd = route.getRoutePlatforms().get(i);
				final long platformId = ((Object) rpd instanceof IAltRoutePlatformData) ? ((IAltRoutePlatformData) (Object) rpd).altplatforms$getPlatformId() : 0;
				final Platform platform = rpd.platform != null ? rpd.platform : (platformId != 0 ? data.platformIdMap.get(platformId) : null);
				if (platform != null) {
					final ObjectArrayList<Platform> altList = new ObjectArrayList<>();
					if ((Object) rpd instanceof IAltRoutePlatformData) {
						final LongArrayList altIds = ((IAltRoutePlatformData) (Object) rpd).altplatforms$getAlternatePlatformIds();
						for (int j = 0; j < altIds.size(); j++) {
							final Platform altPlatform = data.platformIdMap.get(altIds.getLong(j));
							if (altPlatform != null && !altList.contains(altPlatform)) {
								altList.add(altPlatform);
							}
						}
					}
					if (platform.getId() == previousPlatformId && !stops.isEmpty()) {
						for (final Platform p : altList) {
							if (!stops.get(stops.size() - 1).alternatePlatforms.contains(p)) {
								stops.get(stops.size() - 1).alternatePlatforms.add(p);
							}
						}
					} else {
						stops.add(new PlatformStop(platform, altList));
						previousPlatformId = platform.getId();
					}
				}
			}
		}

		if (stops.isEmpty()) {
			return;
		}

		final boolean isCircular = stops.size() >= 2 && stops.get(0).platform.getId() == Utilities.getElement(stops, -1).platform.getId();

		for (int i = 0; i < stops.size(); i++) {
			final PlatformStop details = stops.get(i);
			if (details.alternatePlatforms.isEmpty()) {
				continue;
			}

			final Platform prevPlatform;
			if (i > 0) {
				prevPlatform = stops.get(i - 1).platform;
			} else if (isCircular) {
				prevPlatform = stops.size() >= 3 ? stops.get(stops.size() - 2).platform : null;
			} else if (repeatInfinitely && !stops.isEmpty()) {
				prevPlatform = Utilities.getElement(stops, -1).platform;
			} else {
				prevPlatform = null;
			}

			final Platform nextPlatform;
			if (i < stops.size() - 1) {
				nextPlatform = stops.get(i + 1).platform;
			} else if (isCircular) {
				nextPlatform = stops.size() >= 2 ? stops.get(1).platform : null;
			} else if (repeatInfinitely && !stops.isEmpty()) {
				nextPlatform = Utilities.getElement(stops, 0).platform;
			} else {
				nextPlatform = null;
			}

			for (final Platform altPlatform : details.alternatePlatforms) {
				altplatforms$generateSingleAlternatePath(altPaths, i, prevPlatform, details.platform, altPlatform, nextPlatform);
				altplatforms$generateSingleAlternatePath(altPaths, i, prevPlatform, altPlatform, details.platform, nextPlatform);
			}

			if (i == 0) {
				for (final Siding siding : savedRails) {
					for (final Platform altPlatform : details.alternatePlatforms) {
						final ObjectArrayList<PathData> pathFromSiding = altplatforms$findDirectPath(data, siding, altPlatform, -1, cruisingAltitude, 5000);
						if (pathFromSiding != null && pathFromSiding.size() >= 2) {
							final ObjectArrayList<PathData> altFromSiding = new ObjectArrayList<>(pathFromSiding);
							final Platform nextForSiding = stops.size() > 1 ? stops.get(1).platform : null;
							if (nextForSiding != null) {
								final ObjectArrayList<PathData> pathNext = altplatforms$findDirectPath(data, altPlatform, nextForSiding, 0, cruisingAltitude, 5000);
								if (pathNext != null && pathNext.size() >= 2) {
									if (SidingPathFinder.overlappingPaths(altFromSiding, pathNext)) {
										pathNext.remove(0);
									}
									altFromSiding.addAll(pathNext);
								}
							}
							PathData.writePathCache(altFromSiding, data, transportMode);
							altPaths.add(new AlternatePath(0, details.platform.getId(), altPlatform.getId(), altFromSiding));
							Main.LOGGER.info("Generated alternate path from siding {} for stop 0 (platform {}) via alternate platform {}", siding.getName(), details.platform.getId(), altPlatform.getId());
						}
					}
				}
			}
		}
	}

	@Unique
	private void altplatforms$generateSingleAlternatePath(ObjectArrayList<AlternatePath> altPaths, int stopIndex, Platform prevPlatform, Platform fromPlatform, Platform toPlatform, Platform nextPlatform) {
		if (prevPlatform == null) {
			return;
		}

		final ObjectArrayList<PathData> altPathSegment = new ObjectArrayList<>();
		boolean success = true;

		final ObjectArrayList<PathData> path1 = altplatforms$findDirectPath(data, prevPlatform, toPlatform, Math.max(0, stopIndex - 1), cruisingAltitude, 5000);
		if (path1 != null && path1.size() >= 2) {
			altPathSegment.addAll(path1);
		} else {
			success = false;
		}

		if (success && nextPlatform != null) {
			final ObjectArrayList<PathData> path2 = altplatforms$findDirectPath(data, toPlatform, nextPlatform, stopIndex, cruisingAltitude, 5000);
			if (path2 != null && path2.size() >= 2) {
				if (!altPathSegment.isEmpty() && SidingPathFinder.overlappingPaths(altPathSegment, path2)) {
					path2.remove(0);
				}
				altPathSegment.addAll(path2);
			} else {
				success = false;
			}
		}

		if (success && !altPathSegment.isEmpty()) {
			PathData.writePathCache(altPathSegment, data, transportMode);
			altPaths.add(new AlternatePath(stopIndex, fromPlatform.getId(), toPlatform.getId(), altPathSegment));
			Main.LOGGER.info("Generated alternate path for stop {} (from platform {} to platform {})", stopIndex, fromPlatform.getId(), toPlatform.getId());
		}
	}

	@Unique
	@SuppressWarnings({"rawtypes", "unchecked"})
	private ObjectArrayList<PathData> altplatforms$findDirectPath(Data data, SavedRailBase<?, ?> startSavedRail, SavedRailBase<?, ?> endSavedRail, int stopIndex, long cruisingAltitude, long maxMillis) {
		final SidingPathFinder sidingPathFinder = new SidingPathFinder(data, startSavedRail, endSavedRail, stopIndex);
		return Utilities.loopUntilTimeout(() -> ((SidingPathFinderAccessor) (Object) sidingPathFinder).altplatforms$callTick(cruisingAltitude), maxMillis);
	}
}
