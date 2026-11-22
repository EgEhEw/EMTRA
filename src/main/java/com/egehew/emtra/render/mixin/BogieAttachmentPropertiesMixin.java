package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.BogieArticulation;
import org.mtr.core.serializer.ReaderBase;
import org.mtr.mod.resource.ModelProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ModelProperties.class, remap = false)
public abstract class BogieAttachmentPropertiesMixin implements BogieArticulation.ModelProperties {
	@Unique private BogieArticulation.Seams emtra$seams;

	@Inject(method = "<init>(Lorg/mtr/core/serializer/ReaderBase;)V", at = @At("RETURN"))
	private void emtra$readBogieAttachment(ReaderBase reader, CallbackInfo ci) {
		if (reader.getBoolean("emtraBogieAttachment", false)) {
			var defaults = BogieArticulation.Seams.DEFAULT;
			double halfLength = reader.getDouble("emtraBogieAttachmentHalfLength", defaults.halfLength());
			double height = reader.getDouble("emtraBogieAttachmentHeight", defaults.height());
			emtra$seams = new BogieArticulation.Seams(
					Double.isFinite(halfLength) && halfLength > 0 ? halfLength : defaults.halfLength(),
					Double.isFinite(height) ? height : defaults.height());
		}
	}

	@Override
	public BogieArticulation.Seams emtra$bogieAttachmentSeams() {
		return emtra$seams;
	}
}
