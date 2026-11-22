package com.egehew.emtra.render;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.data.VehicleExtension;
import org.mtr.mod.render.DynamicVehicleModel;
import org.mtr.mod.render.StoredMatrixTransformations;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.mtr.mod.resource.PartCondition;

import java.util.function.Function;

/** Native bogie trim follows the shared suspension pivot while the wheel mesh follows the track. */
public interface BogieArticulation {
	void emtra$queueBogieAttachment(int bogie, Function<Seams, StoredMatrixTransformations> matrices, VehicleExtension vehicle, int light);

	static boolean isAttachment(DynamicVehicleModel model) {
		return seams(model) != null;
	}

	static Seams seams(DynamicVehicleModel model) {
		return (Object) model.modelProperties instanceof ModelProperties properties ? properties.emtra$bogieAttachmentSeams() : null;
	}

	interface ModelProperties {
		Seams emtra$bogieAttachmentSeams();
	}

	/** Attachment contact planes in model space, measured in blocks from the bogie anchor. */
	record Seams(double halfLength, double height) {
		public static final Seams DEFAULT = new Seams(0.5, 1);
	}

	final class MaterialGroups extends Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper>> {
		public Seams seams;
		public final Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.MaterialGroupWrapper>> attachments = new Object2ObjectOpenHashMap<>();
		public final Object2ObjectOpenHashMap<PartCondition, ObjectArrayList<OptimizedModelWrapper.ObjModelWrapper>> attachmentObjects = new Object2ObjectOpenHashMap<>();
	}

	final class OptimizedGroups extends Object2ObjectOpenHashMap<PartCondition, OptimizedModelWrapper> {
		public final Object2ObjectOpenHashMap<PartCondition, OptimizedModelWrapper> attachments;
		public final Seams seams;

		public OptimizedGroups(Object2ObjectOpenHashMap<PartCondition, OptimizedModelWrapper> wheels,
				Object2ObjectOpenHashMap<PartCondition, OptimizedModelWrapper> attachments, Seams seams) {
			super(wheels);
			this.attachments = attachments;
			this.seams = seams;
		}
	}
}
