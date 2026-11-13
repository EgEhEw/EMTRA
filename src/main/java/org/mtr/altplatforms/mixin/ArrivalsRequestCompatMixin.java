package org.mtr.altplatforms.mixin;

import org.mtr.core.operation.ArrivalsRequest;
import org.spongepowered.asm.mixin.Mixin;

/** Ensures the compatibility plugin also visits Magic's PIDS request hooks. */
@Mixin(value = ArrivalsRequest.class, remap = false)
public abstract class ArrivalsRequestCompatMixin {
}
