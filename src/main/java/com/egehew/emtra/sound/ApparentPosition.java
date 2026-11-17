package com.egehew.emtra.sound;

/**
 * A vehicle sound that can be heard from somewhere other than where the vehicle is: when it reaches the listener
 * around obstacles (through an opening, around a tunnel bend), Minecraft is given a position in the direction of the
 * opening, at the distance the sound travelled. Implemented on MTR's looping vehicle sounds by a mixin.
 */
public interface ApparentPosition {

	/** Where the sound should seem to come from; NaN to use the vehicle's own position again. */
	void emtra$setApparentTarget(double x, double y, double z);

	double emtra$trueX();

	double emtra$trueY();

	double emtra$trueZ();
}
