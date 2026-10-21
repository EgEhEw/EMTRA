package org.mtr.altplatforms.mixin;

import org.mtr.altplatforms.data.AlternatePath;
import org.mtr.altplatforms.duck.IDepotAlternatePaths;
import org.mtr.core.generated.data.DepotSchema;
import org.mtr.core.serializer.WriterBase;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DepotSchema.class, remap = false)
public abstract class DepotSchemaMixin implements IDepotAlternatePaths {

	@Unique
	private final ObjectArrayList<AlternatePath> altplatforms$alternatePaths = new ObjectArrayList<>();

	@Override
	public ObjectArrayList<AlternatePath> altplatforms$getAlternatePaths() {
		return altplatforms$alternatePaths;
	}

	@Override
	public ObjectArrayList<AlternatePath> altplatforms$getAlternatePathsForPlatform(long platformId) {
		final ObjectArrayList<AlternatePath> result = new ObjectArrayList<>();
		for (final AlternatePath alt : altplatforms$alternatePaths) {
			if (alt.primaryPlatformId == platformId) {
				result.add(alt);
			}
		}
		return result;
	}

	@Override
	public ObjectArrayList<AlternatePath> altplatforms$getAlternatePathsInvolvingPlatform(long platformId) {
		final ObjectArrayList<AlternatePath> result = new ObjectArrayList<>();
		for (final AlternatePath alt : altplatforms$alternatePaths) {
			if (alt.primaryPlatformId == platformId || alt.alternatePlatformId == platformId) {
				result.add(alt);
			}
		}
		return result;
	}

	@Inject(method = "serializeData", at = @At("TAIL"))
	private void altplatforms$onSerializeData(WriterBase writerBase, CallbackInfo ci) {
		writerBase.writeDataset(altplatforms$alternatePaths, "alternatePaths");
	}
}
