package com.egehew.emtra.render.mixin;

import org.mtr.core.serializer.JsonReader;
import org.mtr.libraries.com.google.gson.JsonElement;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = JsonReader.class, remap = false)
public interface JsonReaderAccessor {
	@Accessor(value = "map", remap = false)
	Object2ObjectArrayMap<String, JsonElement> emtra$map();
}
