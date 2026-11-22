package com.egehew.emtra.sound;

import com.egehew.emtra.render.EmtraClientConfig;
import org.lwjgl.openal.AL10;
import org.lwjgl.openal.AL11;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALC10;
import org.lwjgl.openal.EXTEfx;
import org.lwjgl.openal.EXTThreadLocalContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Combines cabin muffle with SPR's last direct and reverb coefficients for each Minecraft source.
 * The sound and render threads share this state and the scratch filter. OpenAL copies the scratch filter's
 * coefficients onto the source at attachment time.
 */
public final class RemasteredSoundFilters {

	private static final Map<Integer, Source> SOURCES = new HashMap<>();
	/** Time constant of the glide toward SPR's latest values; SPR updates every 3 ticks (150 ms) by default. */
	private static final float SMOOTH_SECONDS = 0.12F;
	private static long context;
	private static int scratchFilter;

	private RemasteredSoundFilters() {
	}

	public static synchronized void register(int source) {
		final long current = currentContext();
		if (current == 0) {
			return;
		}
		if (context != current) {
			context = current;
			scratchFilter = 0;
			SOURCES.clear();
		}
		SOURCES.put(source, new Source());
	}

	public static synchronized void delete(int[] sources) {
		if (context != 0 && currentContext() == context) {
			for (final int source : sources) {
				SOURCES.remove(source);
			}
			if (SOURCES.isEmpty() && scratchFilter != 0) {
				EXTEfx.alDeleteFilters(scratchFilter);
				scratchFilter = 0;
			}
		}
		AL10.alDeleteSources(sources);
	}

	public static synchronized void attachDirect(int source, int parameter, int originalFilter, float gain, float gainHF) {
		final Source state = gameSource(source);
		if (state == null || parameter != EXTEfx.AL_DIRECT_FILTER || originalFilter == EXTEfx.AL_FILTER_NULL) {
			AL11.alSourcei(source, parameter, originalFilter);
			return;
		}
		final Filter target = new Filter(gain, gainHF);
		if (state.direct != null && EmtraClientConfig.SMOOTH_SPR_UPDATES) {
			// A moving sound's re-evaluation: glide there in smooth() instead of jumping (an audible click).
			state.directTarget = target;
			state.smoothing = true;
			return;
		}
		state.direct = target;
		state.directTarget = null;
		AL11.alSourcei(source, parameter, state.neutral() ? originalFilter : combined(state, state.direct));
	}

	public static synchronized void attachSend(int source, int parameter, int slot, int index, int originalFilter, float gain, float gainHF) {
		final Source state = gameSource(source);
		if (state == null || parameter != EXTEfx.AL_AUXILIARY_SEND_FILTER || index < 0 || index >= state.sends.length) {
			AL11.alSource3i(source, parameter, slot, index, originalFilter);
			return;
		}
		final Filter filter = new Filter(gain, gainHF);
		final Send previous = state.sends[index];
		if (previous != null && slot != 0 && previous.slot() == slot && EmtraClientConfig.SMOOTH_SPR_UPDATES) {
			state.sendTargets[index] = filter;
			state.smoothing = true;
			return;
		}
		state.sendTargets[index] = null;
		state.sends[index] = slot == 0 ? null : new Send(slot, filter);
		AL11.alSource3i(source, parameter, slot, index, state.neutral() || slot == 0 ? originalFilter : combined(state, filter));
	}

	/**
	 * SPR re-evaluates moving sounds every few ticks and sets the new filter coefficients at once. OpenAL does not
	 * interpolate a changed low-pass, so each step is an audible click where the result varies (walls, tunnels).
	 * Glides every source toward SPR's latest values; called every frame.
	 */
	public static synchronized void smooth(float seconds) {
		if (context == 0 || currentContext() != context || seconds <= 0) {
			return;
		}
		final float step = 1 - (float) Math.exp(-seconds / SMOOTH_SECONDS);
		for (final Map.Entry<Integer, Source> entry : SOURCES.entrySet()) {
			final Source state = entry.getValue();
			if (!state.smoothing) {
				continue;
			}
			final int source = entry.getKey();
			if (!AL10.alIsSource(source)) {
				state.smoothing = false;
				continue;
			}
			boolean moving = false;
			if (state.directTarget != null && state.direct != null) {
				state.direct = approach(state.direct, state.directTarget, step);
				if (state.direct == state.directTarget) {
					state.directTarget = null;
				} else {
					moving = true;
				}
				AL11.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, combined(state, state.direct));
			}
			for (int i = 0; i < state.sends.length; i++) {
				final Filter target = state.sendTargets[i];
				final Send send = state.sends[i];
				if (target == null || send == null) {
					state.sendTargets[i] = null;
					continue;
				}
				final Filter next = approach(send.filter(), target, step);
				state.sends[i] = new Send(send.slot(), next);
				if (next == target) {
					state.sendTargets[i] = null;
				} else {
					moving = true;
				}
				AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, send.slot(), i, combined(state, next));
			}
			state.smoothing = moving;
		}
	}

	private static Filter approach(Filter current, Filter target, float step) {
		final float gain = current.gain() + (target.gain() - current.gain()) * step;
		final float gainHF = current.gainHF() + (target.gainHF() - current.gainHF()) * step;
		return Math.abs(target.gain() - gain) < 0.002F && Math.abs(target.gainHF() - gainHF) < 0.002F
				? target : new Filter(gain, gainHF);
	}

	/** Updates a registered source. Returns true once SPR owns its direct filter. */
	static synchronized boolean update(int source, float cabinGain, float cabinHF) {
		final Source state = SOURCES.get(source);
		if (state == null || context == 0 || currentContext() != context) {
			return false;
		}
		if (state.cabinGain != cabinGain || state.cabinHF != cabinHF) {
			state.cabinGain = cabinGain;
			state.cabinHF = cabinHF;
			if (state.direct != null) {
				AL11.alSourcei(source, EXTEfx.AL_DIRECT_FILTER, combined(state, state.direct));
			}
			for (int i = 0; i < state.sends.length; i++) {
				final Send send = state.sends[i];
				if (send != null) {
					AL11.alSource3i(source, EXTEfx.AL_AUXILIARY_SEND_FILTER, send.slot(), i, combined(state, send.filter()));
				}
			}
		}
		return state.direct != null;
	}

	private static Source gameSource(int source) {
		final Source state = SOURCES.get(source);
		// Simple Voice Chat can use the same numeric source IDs in a separate thread-local context.
		return state != null && context != 0 && currentContext() == context ? state : null;
	}

	private static long currentContext() {
		if (ALC.getCapabilities().ALC_EXT_thread_local_context) {
			final long local = EXTThreadLocalContext.alcGetThreadContext();
			if (local != 0) {
				return local;
			}
		}
		return ALC10.alcGetCurrentContext();
	}

	private static int combined(Source state, Filter filter) {
		if (scratchFilter == 0) {
			scratchFilter = EXTEfx.alGenFilters();
			EXTEfx.alFilteri(scratchFilter, EXTEfx.AL_FILTER_TYPE, EXTEfx.AL_FILTER_LOWPASS);
		}
		EXTEfx.alFilterf(scratchFilter, EXTEfx.AL_LOWPASS_GAIN, clamp(filter.gain() * state.cabinGain));
		EXTEfx.alFilterf(scratchFilter, EXTEfx.AL_LOWPASS_GAINHF, clamp(filter.gainHF() * state.cabinHF));
		return scratchFilter;
	}

	private static float clamp(float value) {
		return Math.max(0, Math.min(1, value));
	}

	private record Filter(float gain, float gainHF) {
	}

	private record Send(int slot, Filter filter) {
	}

	private static final class Source {
		private Filter direct;
		private Filter directTarget;
		private final Send[] sends = new Send[4];
		private final Filter[] sendTargets = new Filter[4];
		private boolean smoothing;
		private float cabinGain = 1;
		private float cabinHF = 1;

		private boolean neutral() {
			return cabinGain == 1 && cabinHF == 1;
		}
	}
}
