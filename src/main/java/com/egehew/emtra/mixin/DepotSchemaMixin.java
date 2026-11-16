package com.egehew.emtra.mixin;

import com.egehew.emtra.data.AlternatePath;
import com.egehew.emtra.duck.IDepotAlternatePaths;
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
	private final ObjectArrayList<AlternatePath> emtra$alternatePaths = new ObjectArrayList<>();

	@Override
	public ObjectArrayList<AlternatePath> emtra$getAlternatePaths() {
		return emtra$alternatePaths;
	}

	@Override
	public ObjectArrayList<AlternatePath> emtra$getAlternatePathsForPlatform(long platformId) {
		final ObjectArrayList<AlternatePath> result = new ObjectArrayList<>();
		for (final AlternatePath alt : emtra$alternatePaths) {
			if (alt.primaryPlatformId == platformId) {
				result.add(alt);
			}
		}
		return result;
	}

	@Override
	public ObjectArrayList<AlternatePath> emtra$getAlternatePathsInvolvingPlatform(long platformId) {
		final ObjectArrayList<AlternatePath> result = new ObjectArrayList<>();
		for (final AlternatePath alt : emtra$alternatePaths) {
			if (alt.primaryPlatformId == platformId || alt.alternatePlatformId == platformId) {
				result.add(alt);
			}
		}
		return result;
	}

	@Inject(method = "serializeData", at = @At("TAIL"))
	private void emtra$onSerializeData(WriterBase writerBase, CallbackInfo ci) {
		writerBase.writeDataset(emtra$alternatePaths, "alternatePaths");
	}
}
