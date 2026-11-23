package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.BogieArticulationSplit;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.resource.VehicleModel;
import org.mtr.mod.resource.VehicleResource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Jacobs articulation parts (articularinside, articulation_outer, ...) are often modeled inside the bogie. Split them
 * into their own bogie attachment so they sit between the two swaying car ends instead of staying on the rail.
 */
@Mixin(value = VehicleResource.class, remap = false)
public abstract class BogieArticulationSplitMixin {
	@Unique private ObjectArrayList<VehicleModel> emtra$originalBogie1Models;
	@Unique private ObjectArrayList<VehicleModel> emtra$originalBogie2Models;

	@Inject(method = "<init>*", at = @At("RETURN"))
	private void emtra$splitArticulation(CallbackInfo ci) {
		var schema = (VehicleResourceSchemaAccessor) (Object) this;
		var bogie1 = schema.emtra$bogie1Models();
		var bogie2 = schema.emtra$bogie2Models();
		var original1 = new ObjectArrayList<>(bogie1);
		var original2 = new ObjectArrayList<>(bogie2);
		if (BogieArticulationSplit.apply(schema.emtra$id(), bogie1) && emtra$originalBogie1Models == null) {
			emtra$originalBogie1Models = original1;
		}
		if (BogieArticulationSplit.apply(schema.emtra$id(), bogie2) && emtra$originalBogie2Models == null) {
			emtra$originalBogie2Models = original2;
		}
	}

	// The resource pack creator edits the pack's own bogie entries, not the split render models.
	@Redirect(method = "toVehicleResourceWrapper", at = @At(value = "FIELD", opcode = 180,
			target = "Lorg/mtr/mod/resource/VehicleResource;bogie1Models:Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;"))
	private ObjectArrayList<VehicleModel> emtra$creatorBogie1Models(VehicleResource resource) {
		return emtra$originalBogie1Models != null ? emtra$originalBogie1Models : ((VehicleResourceSchemaAccessor) (Object) resource).emtra$bogie1Models();
	}

	@Redirect(method = "toVehicleResourceWrapper", at = @At(value = "FIELD", opcode = 180,
			target = "Lorg/mtr/mod/resource/VehicleResource;bogie2Models:Lorg/mtr/libraries/it/unimi/dsi/fastutil/objects/ObjectArrayList;"))
	private ObjectArrayList<VehicleModel> emtra$creatorBogie2Models(VehicleResource resource) {
		return emtra$originalBogie2Models != null ? emtra$originalBogie2Models : ((VehicleResourceSchemaAccessor) (Object) resource).emtra$bogie2Models();
	}
}
