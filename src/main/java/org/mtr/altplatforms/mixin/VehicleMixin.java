package org.mtr.altplatforms.mixin;

import org.mtr.altplatforms.data.AlternatePath;
import org.mtr.altplatforms.duck.IDepotAlternatePaths;
import org.mtr.altplatforms.duck.IVehicleExtraData;
import org.mtr.core.Main;
import org.mtr.core.data.*;
import org.mtr.core.generated.data.VehicleSchema;
import org.mtr.core.serializer.JsonReader;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.core.tool.Utilities;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectAVLTreeMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

@Mixin(value = Vehicle.class, remap = false)
public abstract class VehicleMixin extends VehicleSchema {

	protected VehicleMixin(TransportMode transportMode, Data data) {
		super(transportMode, data);
	}

	protected VehicleMixin(ReaderBase readerBase, Data data) {
		super(readerBase, data);
	}

	@Shadow
	public VehicleExtraData vehicleExtraData;

	@Shadow
	public Siding siding;

	@Shadow
	public boolean isClientside;

	@Shadow
	private long stoppingCooldown;

	@Shadow
	abstract void setNextStoppingIndex();

	@Shadow
	abstract double getPathStoppingPoint();

	@Shadow
	abstract double railBlockedDistance(int currentIndex, double railProgress, double checkDistance, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, boolean checkSignals, boolean isOccupied);

	@Unique
	private long altplatforms$lastDiversionTime = 0;

	@Unique
	private double altplatforms$getPhysicalBlockedDistance(
			int currentIndex,
			int stopIndex,
			double pathStoppingPoint,
			@Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions
	) {
		if (vehiclePositions == null || vehicleExtraData == null) {
			return -1;
		}

		final int maxIndex = Math.min(stopIndex, vehicleExtraData.immutablePath.size() - 1);
		for (int i = currentIndex; i <= maxIndex; i++) {
			final PathData pd = vehicleExtraData.immutablePath.get(i);
			if (pd.getStartDistance() >= pathStoppingPoint) {
				break;
			}
			for (int p = 0; p < 2; p++) {
				final VehiclePosition vp = Data.tryGet(vehiclePositions.get(p), pd.getOrderedPosition1(), pd.getOrderedPosition2());
				if (vp != null) {
					final double overlap = vp.getClosestOverlap(0, pd.getRailLength(), pd.reversePositions, id);
					if (overlap >= 0) {
						final double startDist = Math.max(0, pd.getStartDistance() - railProgress);
						return Math.max(0, startDist + overlap - transportMode.stoppingSpace);
					}
				}
			}
		}
		return -1;
	}

	@Redirect(method = "simulateMoving", at = @At(value = "INVOKE", target = "Lorg/mtr/core/data/Vehicle;railBlockedDistance(IDDLorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;ZZ)D"))
	private double altplatforms$onRailBlockedDistance(
			Vehicle vehicle,
			int currentIndex,
			double railProgress,
			double checkDistance,
			@Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions,
			boolean checkSignals,
			boolean isOccupied
	) {
		final double pathStoppingPoint = getPathStoppingPoint();
		final int stopIndex = (int) nextStoppingIndexAto;
		final PathData stoppingPathData = (vehicleExtraData != null && stopIndex >= 0 && stopIndex < vehicleExtraData.immutablePath.size())
				? vehicleExtraData.immutablePath.get(stopIndex) : null;
		final boolean isPlatformStop = stoppingPathData != null && stoppingPathData.getDwellTime() > 0;

		final double effectiveCheckDistance = (isPlatformStop && pathStoppingPoint > railProgress)
				? Math.min(checkDistance, Math.max(0, pathStoppingPoint - railProgress))
				: checkDistance;

		final double mtrBlockedDistance = railBlockedDistance(currentIndex, railProgress, effectiveCheckDistance, vehiclePositions, checkSignals, isOccupied);

		if (isPlatformStop && pathStoppingPoint > railProgress) {
			if (mtrBlockedDistance < 0) {
				stoppingCooldown = 0;
				return -1.0;
			}

			final double obstructionProgress = railProgress + mtrBlockedDistance;
			if (obstructionProgress >= pathStoppingPoint - 1.0) {
				stoppingCooldown = 0;
				return -1.0;
			}

			final double physicalBlockedDistance = altplatforms$getPhysicalBlockedDistance(currentIndex, stopIndex, pathStoppingPoint, vehiclePositions);
			if (physicalBlockedDistance >= 0) {
				return physicalBlockedDistance;
			} else {
				stoppingCooldown = 0;
				return -1.0;
			}
		}

		return mtrBlockedDistance;
	}

	@Inject(method = "simulateMoving", at = @At("HEAD"))
	private void altplatforms$onSimulateMoving(long millisElapsed, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, int currentIndex, CallbackInfo ci) {
		altplatforms$checkAlternatePlatformDiversion(currentIndex, vehiclePositions);

		if (stoppingCooldown > 0) {
			final double pathStoppingPoint = getPathStoppingPoint();
			final int stopIndex = (int) nextStoppingIndexAto;
			final PathData stoppingPathData = (vehicleExtraData != null && stopIndex >= 0 && stopIndex < vehicleExtraData.immutablePath.size())
					? vehicleExtraData.immutablePath.get(stopIndex) : null;
			if (stoppingPathData != null && stoppingPathData.getDwellTime() > 0 && pathStoppingPoint > railProgress) {
				final double physicalBlockedDistance = altplatforms$getPhysicalBlockedDistance(currentIndex, stopIndex, pathStoppingPoint, vehiclePositions);
				if (physicalBlockedDistance < 0) {
					stoppingCooldown = 0;
				}
			}
		}
	}

	@Inject(method = "simulateStopped", at = @At("HEAD"))
	private void altplatforms$onSimulateStopped(long millisElapsed, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions, int currentIndex, CallbackInfo ci) {
		altplatforms$checkAlternatePlatformDiversion(currentIndex, vehiclePositions);
	}

	@Unique
	private void altplatforms$checkAlternatePlatformDiversion(int currentIndex, @Nullable ObjectArrayList<Object2ObjectAVLTreeMap<Position, Object2ObjectAVLTreeMap<Position, VehiclePosition>>> vehiclePositions) {
		if (siding == null || siding.area == null || isClientside || transportMode.continuousMovement || vehiclePositions == null) {
			return;
		}

		if (System.currentTimeMillis() - altplatforms$lastDiversionTime < 2000L) {
			return;
		}

		PathData upcomingPlatformPathData = null;
		int upcomingPlatformIndex = -1;
		for (int i = currentIndex; i < vehicleExtraData.immutablePath.size(); i++) {
			final PathData pd = vehicleExtraData.immutablePath.get(i);
			if (pd.getSavedRailBaseId() != 0 && pd.getDwellTime() > 0) {
				upcomingPlatformPathData = pd;
				upcomingPlatformIndex = i;
				break;
			}
		}

		if (upcomingPlatformPathData == null || upcomingPlatformIndex < 0) {
			return;
		}

		final long upcomingPlatformId = upcomingPlatformPathData.getSavedRailBaseId();
		if (!((Object) siding.area instanceof IDepotAlternatePaths)) {
			return;
		}

		final ObjectArrayList<AlternatePath> alternatePaths = ((IDepotAlternatePaths) (Object) siding.area).altplatforms$getAlternatePathsForPlatform(upcomingPlatformId);
		if (alternatePaths.isEmpty()) {
			return;
		}

		for (final AlternatePath altPath : alternatePaths) {
			if (altPath.alternatePath.isEmpty() || altPath.alternatePlatformId == upcomingPlatformId) {
				continue;
			}

			int divCurrentIndex = -1;
			int divAltIndex = -1;
			final int searchStart = Math.max(0, currentIndex - 2);
			for (int i = searchStart; i <= upcomingPlatformIndex; i++) {
				final PathData curPd = vehicleExtraData.immutablePath.get(i);
				for (int j = 0; j < altPath.alternatePath.size(); j++) {
					final PathData altPd = altPath.alternatePath.get(j);
					if (curPd.isSameRail(altPd)) {
						if (i + 1 < vehicleExtraData.immutablePath.size() && j + 1 < altPath.alternatePath.size()) {
							final PathData nextCur = vehicleExtraData.immutablePath.get(i + 1);
							final PathData nextAlt = altPath.alternatePath.get(j + 1);
							if (!nextCur.isSameRail(nextAlt)) {
								divCurrentIndex = i + 1;
								divAltIndex = j + 1;
								break;
							}
						}
					}
				}
				if (divCurrentIndex >= 0) {
					break;
				}
			}

			if (divCurrentIndex < 0 || divAltIndex < 0 || divCurrentIndex > upcomingPlatformIndex) {
				continue;
			}

			final double divergenceDistance = vehicleExtraData.immutablePath.get(divCurrentIndex).getStartDistance();
			final double maxAllowedProgress = (speed != 0) ? (divergenceDistance + 0.01) : (divergenceDistance + 2.0);
			if (railProgress > maxAllowedProgress) {
				continue;
			}

			int upcomingPlatformEndIndex = upcomingPlatformIndex;
			for (int i = upcomingPlatformIndex + 1; i < vehicleExtraData.immutablePath.size(); i++) {
				final PathData pd = vehicleExtraData.immutablePath.get(i);
				if (pd.getSavedRailBaseId() == upcomingPlatformId || pd.getDwellTime() > 0) {
					upcomingPlatformEndIndex = i;
				} else {
					break;
				}
			}

			boolean currentBranchBlocked = false;
			for (int i = divCurrentIndex; i <= upcomingPlatformEndIndex; i++) {
				final PathData curPd = vehicleExtraData.immutablePath.get(i);
				if (curPd.isSignalBlocked(id, Rail.BlockReservation.DO_NOT_RESERVE)) {
					currentBranchBlocked = true;
					break;
				}
				for (int p = 0; p < 2; p++) {
					final VehiclePosition vp = Data.tryGet(vehiclePositions.get(p), curPd.getOrderedPosition1(), curPd.getOrderedPosition2());
					if (vp != null && vp.getClosestOverlap(0, curPd.getRailLength(), curPd.reversePositions, id) >= 0) {
						currentBranchBlocked = true;
						break;
					}
				}
				if (currentBranchBlocked) {
					break;
				}
			}

			if (!currentBranchBlocked) {
				continue;
			}

			int altPlatformStartIndex = -1;
			int altPlatformEndIndex = -1;
			for (int j = divAltIndex; j < altPath.alternatePath.size(); j++) {
				final PathData altPd = altPath.alternatePath.get(j);
				if (altPd.getSavedRailBaseId() == altPath.alternatePlatformId || altPd.getDwellTime() > 0) {
					if (altPlatformStartIndex < 0) {
						altPlatformStartIndex = j;
					}
					altPlatformEndIndex = j;
				} else if (altPlatformStartIndex >= 0) {
					break;
				}
			}

			int mergeCurrentIndex = -1;
			int mergeAltIndex = -1;
			boolean isWrapAroundMerge = false;
			final int searchAltStart = Math.max(divAltIndex, (altPlatformEndIndex >= 0 ? altPlatformEndIndex : altPlatformStartIndex) + 1);

			final IVehicleExtraData duckExtraData = (IVehicleExtraData) (Object) vehicleExtraData;

			for (int i = upcomingPlatformEndIndex + 1; i < vehicleExtraData.immutablePath.size(); i++) {
				final PathData curPd = vehicleExtraData.immutablePath.get(i);
				for (int j = searchAltStart; j < altPath.alternatePath.size(); j++) {
					final PathData altPd = altPath.alternatePath.get(j);
					if (curPd.isSameRail(altPd)) {
						mergeCurrentIndex = i;
						mergeAltIndex = j;
						break;
					}
				}
				if (mergeCurrentIndex >= 0) {
					break;
				}
			}

			if (mergeCurrentIndex < 0 && duckExtraData.altplatforms$getRepeatIndex2() > 0) {
				for (int i = (int) duckExtraData.altplatforms$getRepeatIndex1(); i <= upcomingPlatformIndex; i++) {
					final PathData curPd = vehicleExtraData.immutablePath.get(i);
					for (int j = searchAltStart; j < altPath.alternatePath.size(); j++) {
						final PathData altPd = altPath.alternatePath.get(j);
						if (curPd.isSameRail(altPd)) {
							mergeCurrentIndex = i;
							mergeAltIndex = j;
							isWrapAroundMerge = true;
							break;
						}
					}
					if (mergeCurrentIndex >= 0) {
						break;
					}
				}
			}

			if (mergeCurrentIndex < 0 || mergeAltIndex < 0) {
				continue;
			}

			final int checkAltEnd = altPlatformEndIndex >= 0 ? altPlatformEndIndex : mergeAltIndex;
			boolean altBlocked = false;
			for (int j = divAltIndex; j <= checkAltEnd; j++) {
				final PathData altPd = altPath.alternatePath.get(j);
				if (altPd.isSignalBlocked(id, Rail.BlockReservation.DO_NOT_RESERVE)) {
					altBlocked = true;
					break;
				}
				for (int p = 0; p < 2; p++) {
					final VehiclePosition vp = Data.tryGet(vehiclePositions.get(p), altPd.getOrderedPosition1(), altPd.getOrderedPosition2());
					if (vp != null && vp.getClosestOverlap(0, altPd.getRailLength(), altPd.reversePositions, id) >= 0) {
						altBlocked = true;
						break;
					}
				}
				if (altBlocked) {
					break;
				}
			}

			if (altBlocked) {
				continue;
			}

			final ObjectArrayList<PathData> newPath = new ObjectArrayList<>();
			for (int i = 0; i < divCurrentIndex; i++) {
				newPath.add(vehicleExtraData.immutablePath.get(i));
			}

			double currentDist = newPath.isEmpty() ? 0 : Utilities.getElement(newPath, -1).getEndDistance();
			for (int j = divAltIndex; j < mergeAltIndex; j++) {
				final PathData orig = altPath.alternatePath.get(j);
				final double start = currentDist;
				final double end = start + orig.getRailLength();
				newPath.add(new PathData(orig, start, end));
				currentDist = end;
			}

			final long oldRepeat1 = duckExtraData.altplatforms$getRepeatIndex1();
			final long oldRepeat2 = duckExtraData.altplatforms$getRepeatIndex2();
			long newRepeatIndex1 = oldRepeat1;
			long newRepeatIndex2 = 0;
			double newTotalDistance;

			if (isWrapAroundMerge) {
				final int loopEnd = oldRepeat2 > 0 ? (int) oldRepeat2 : vehicleExtraData.immutablePath.size();
				for (int i = mergeCurrentIndex; i < loopEnd; i++) {
					final PathData orig = vehicleExtraData.immutablePath.get(i);
					final double start = currentDist;
					final double end = start + orig.getRailLength();
					newPath.add(new PathData(orig, start, end));
					currentDist = end;
				}

				newRepeatIndex1 = divCurrentIndex;
				newRepeatIndex2 = newPath.size();
				newTotalDistance = currentDist;

				if (oldRepeat2 > 0) {
					newPath.add(new PathData(new PathData(new JsonReader(new JsonObject())), currentDist, Double.MAX_VALUE));
				}
			} else {
				final int delta = (mergeAltIndex - divAltIndex) - (mergeCurrentIndex - divCurrentIndex);
				for (int i = mergeCurrentIndex; i < vehicleExtraData.immutablePath.size(); i++) {
					final PathData orig = vehicleExtraData.immutablePath.get(i);
					final double start = currentDist;
					final double end = start + orig.getRailLength();
					newPath.add(new PathData(orig, start, end));
					currentDist = end;
				}

				if (oldRepeat1 <= divCurrentIndex) {
					newRepeatIndex1 = oldRepeat1;
				} else if (divCurrentIndex < oldRepeat1 && mergeCurrentIndex <= oldRepeat1) {
					newRepeatIndex1 = oldRepeat1 + delta;
				} else {
					newRepeatIndex1 = altPlatformStartIndex >= 0 ? divCurrentIndex + (altPlatformStartIndex - divAltIndex) : divCurrentIndex;
				}

				if (oldRepeat2 > 0) {
					newRepeatIndex2 = oldRepeat2 + delta;
					newTotalDistance = newRepeatIndex2 < newPath.size() ? Utilities.getElement(newPath, (int) newRepeatIndex2).getStartDistance() : Utilities.getElement(newPath, -1).getEndDistance();
				} else {
					newRepeatIndex2 = 0;
					newTotalDistance = Utilities.getElement(newPath, -1).getEndDistance();
				}
			}

			PathData.writePathCache(newPath, data, transportMode);
			duckExtraData.altplatforms$setPath(newPath, newRepeatIndex1, newRepeatIndex2, newTotalDistance);
			setNextStoppingIndex();
			stoppingCooldown = 0;
			altplatforms$lastDiversionTime = System.currentTimeMillis();
			Main.LOGGER.info("Vehicle {} diverted to alternate platform {} at stop {}", id, altPath.alternatePlatformId, altPath.stopIndex);
			break;
		}
	}
}
