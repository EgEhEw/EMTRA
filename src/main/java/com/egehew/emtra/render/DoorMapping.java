package com.egehew.emtra.render;

/**
 * Remembers which doorway list a DynamicVehicleModel's door parts were last mapped to.
 * Implemented on DynamicVehicleModel by a mixin.
 */
public interface DoorMapping {
	Object emtra$getMappedDoorways();

	void emtra$setMappedDoorways(Object doorways);
}
