package com.egehew.emtra.render;

import com.egehew.emtra.EmtraMod;
import com.egehew.emtra.render.mixin.JsonReaderAccessor;
import com.egehew.emtra.render.mixin.VehicleModelAccessor;
import com.egehew.emtra.render.mixin.VehicleModelSchemaAccessor;
import org.mtr.core.serializer.JsonReader;
import org.mtr.libraries.com.google.gson.JsonArray;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.com.google.gson.JsonObject;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.resource.VehicleModel;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Finds Jacobs articulation parts inside bogie models by name and moves them to a separate bogie attachment model.
 * The wheels keep following the rail, while the attachment follows the shared joint of the two car bodies
 * ({@link VehicleMotion.Frame#shiftArticulation}). Runs once per vehicle resource when packs load; JSON only.
 */
public final class BogieArticulationSplit {
	public static final String FLAG = "emtraBogieAttachment";
	// articularinside, Articulation_Outer, jacobs_artic... but not "particle".
	private static final Pattern ARTICULATION = Pattern.compile("(^|[^a-z])artic");

	private BogieArticulationSplit() {
	}

	/** Replaces bogie models that contain articulation parts. Returns true if the list changed. */
	public static boolean apply(String vehicleId, ObjectArrayList<VehicleModel> models) {
		boolean changed = false;
		try {
			for (int i = 0; i < models.size(); i++) {
				ObjectArrayList<VehicleModel> replacement = split(models.get(i));
				if (replacement == null) continue;
				models.remove(i);
				models.addAll(i, replacement);
				i += replacement.size() - 1;
				changed = true;
				EmtraMod.LOGGER.info("[EMTRA] Jacobs articulation parts follow the car bodies: vehicle {}, bogie model {}",
						vehicleId, ((VehicleModelSchemaAccessor) (Object) replacement.get(0)).emtra$modelPropertiesResource());
			}
		} catch (Throwable t) {
			EmtraMod.LOGGER.warn("[EMTRA] Jacobs articulation detection failed for vehicle {}", vehicleId, t);
		}
		return changed;
	}

	static boolean isArticulationName(String name) {
		return name != null && ARTICULATION.matcher(name.toLowerCase(Locale.ROOT)).find();
	}

	private static ObjectArrayList<VehicleModel> split(VehicleModel model) {
		if (model == null) return null;
		var access = (VehicleModelAccessor) (Object) model;
		JsonReader propertiesReader = access.emtra$modelPropertiesJsonReader();
		if (propertiesReader == null) return null;
		var properties = ((JsonReaderAccessor) (Object) propertiesReader).emtra$map();
		JsonElement flag = properties.get(FLAG);
		if (flag != null && flag.isJsonPrimitive() && flag.getAsBoolean()) return null;
		JsonElement partsElement = properties.get("parts");
		if (partsElement == null || !partsElement.isJsonArray()) return null;

		JsonArray wheelParts = new JsonArray();
		JsonArray articulationParts = new JsonArray();
		for (JsonElement partElement : partsElement.getAsJsonArray()) {
			if (!partElement.isJsonObject()) {
				wheelParts.add(partElement);
				continue;
			}
			JsonObject part = partElement.getAsJsonObject();
			JsonElement namesElement = part.get("names");
			if (namesElement == null || !namesElement.isJsonArray()) {
				wheelParts.add(part);
				continue;
			}
			JsonArray wheelNames = new JsonArray();
			JsonArray articulationNames = new JsonArray();
			for (JsonElement name : namesElement.getAsJsonArray()) {
				boolean articulation = name.isJsonPrimitive() && isArticulationName(name.getAsString());
				(articulation ? articulationNames : wheelNames).add(name);
			}
			if (articulationNames.size() == 0) {
				wheelParts.add(part);
			} else if (wheelNames.size() == 0) {
				articulationParts.add(part);
			} else {
				wheelParts.add(withNames(part, wheelNames));
				articulationParts.add(withNames(part, articulationNames));
			}
		}
		if (articulationParts.size() == 0) return null;

		var schema = (VehicleModelSchemaAccessor) (Object) model;
		JsonObject entry = new JsonObject();
		entry.addProperty("modelResource", schema.emtra$modelResource());
		entry.addProperty("textureResource", schema.emtra$textureResource());
		entry.addProperty("modelPropertiesResource", schema.emtra$modelPropertiesResource());
		entry.addProperty("positionDefinitionsResource", schema.emtra$positionDefinitionsResource());
		entry.addProperty("flipTextureV", schema.emtra$flipTextureV());
		JsonReader positions = access.emtra$positionDefinitionsJsonReader();
		String id = schema.emtra$modelPropertiesResource();

		ObjectArrayList<VehicleModel> result = new ObjectArrayList<>(2);
		if (wheelParts.size() > 0) {
			result.add(new VehicleModel(new JsonReader(entry), new JsonReader(withParts(properties, wheelParts, false)),
					positions, id, schema.emtra$resourceProvider()));
		}
		// Keeps any emtraBogieAttachmentHalfLength/Height the pack defines; otherwise the defaults apply.
		result.add(new VehicleModel(new JsonReader(entry), new JsonReader(withParts(properties, articulationParts, true)),
				positions, id + " (articulation)", schema.emtra$resourceProvider()));
		return result;
	}

	private static JsonObject withNames(JsonObject part, JsonArray names) {
		JsonObject copy = part.deepCopy();
		copy.add("names", names);
		return copy;
	}

	private static JsonObject withParts(java.util.Map<String, JsonElement> properties, JsonArray parts, boolean attachment) {
		JsonObject copy = new JsonObject();
		properties.forEach(copy::add);
		copy.add("parts", parts);
		if (attachment) copy.addProperty(FLAG, true);
		return copy;
	}
}
