package org.mtr.altplatforms.mixin;

import org.mtr.core.data.PathData;
import org.mtr.core.path.SidingPathFinder;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = SidingPathFinder.class, remap = false)
public interface SidingPathFinderAccessor {
	@Invoker("tick")
	ObjectArrayList<PathData> altplatforms$callTick(long cruisingAltitude);
}
