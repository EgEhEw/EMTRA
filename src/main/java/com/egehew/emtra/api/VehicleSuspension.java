package com.egehew.emtra.api;

import com.egehew.emtra.render.VehicleMotion;

/** Publishes a client-side pose for the carriages assigned to a JCM vehicle script. */
public final class VehicleSuspension {

	private VehicleSuspension() {
	}

	/**
	 * Offsets are world-space XYZ triples in blocks, in the order returned by ctx.getMyCars().
	 * Each car has one body triple and two optional triples for its scripted bogie attachments.
	 * A null array supplies zero offsets. Submit again on each script render.
	 */
	public static boolean submit(Object ctx, double[] bodies, double[] bogieAttachments, boolean replaceStockOscillation) {
		return ctx instanceof VehicleMotion.ScriptContext context &&
				context.emtra$submitSuspension(bodies, bogieAttachments, replaceStockOscillation);
	}
}
