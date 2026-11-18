package com.egehew.emtra.sound;

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
		state.direct = new Filter(gain, gainHF);
		AL11.alSourcei(source, parameter, state.neutral() ? originalFilter : combined(state, state.direct));
	}

	public static synchronized void attachSend(int source, int parameter, int slot, int index, int originalFilter, float gain, float gainHF) {
		final Source state = gameSource(source);
		if (state == null || parameter != EXTEfx.AL_AUXILIARY_SEND_FILTER || index < 0 || index >= state.sends.length) {
			AL11.alSource3i(source, parameter, slot, index, originalFilter);
			return;
		}
		final Filter filter = new Filter(gain, gainHF);
		state.sends[index] = slot == 0 ? null : new Send(slot, filter);
		AL11.alSource3i(source, parameter, slot, index, state.neutral() || slot == 0 ? originalFilter : combined(state, filter));
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
		private final Send[] sends = new Send[4];
		private float cabinGain = 1;
		private float cabinHF = 1;

		private boolean neutral() {
			return cabinGain == 1 && cabinHF == 1;
		}
	}
}
