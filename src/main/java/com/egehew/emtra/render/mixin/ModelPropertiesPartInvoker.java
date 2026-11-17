package com.egehew.emtra.render.mixin;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mapping.holder.Box;
import org.mtr.mod.resource.ModelPropertiesPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = ModelPropertiesPart.class, remap = false)
public interface ModelPropertiesPartInvoker {
	@Invoker(value = "mapDoors", remap = false)
	void emtra$mapDoors(ObjectArrayList<Box> doorways);
}
