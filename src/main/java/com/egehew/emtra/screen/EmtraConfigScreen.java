package com.egehew.emtra.screen;

import com.egehew.emtra.render.EmtraClientConfig;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.screen.MTRScreenBase;
import org.mtr.mod.screen.WidgetShorterSlider;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * EMTRA client options. Opened from the mod list (Forge "Config" button, Mod Menu on Fabric).
 * Built from MTR's loader-independent widgets, like MTR's own config screen. Changes apply immediately and are
 * saved when the screen closes.
 */
public class EmtraConfigScreen extends MTRScreenBase {

	private static final int ROW_HEIGHT = 16;
	private static final int CONTROL_WIDTH = 90;
	private static final int TOP = 22;
	private static final int BOTTOM_MARGIN = 30;

	private int scroll;
	private int maxScroll;

	private final List<Row> rows = new ArrayList<>();
	private final ButtonWidgetExtension buttonDone;

	public EmtraConfigScreen(@Nullable Screen previousScreen) {
		super(previousScreen);
		section("options.emtra.section_render");
		toggle("options.emtra.render_instancing", () -> EmtraClientConfig.RENDER_INSTANCING, value -> EmtraClientConfig.RENDER_INSTANCING = value);
		toggle("options.emtra.render_rail_cache", () -> EmtraClientConfig.RENDER_RAIL_CACHE, value -> EmtraClientConfig.RENDER_RAIL_CACHE = value);
		toggle("options.emtra.render_catenary_cache", () -> EmtraClientConfig.RENDER_CATENARY_CACHE, value -> EmtraClientConfig.RENDER_CATENARY_CACHE = value);
		toggle("options.emtra.render_stats", () -> EmtraClientConfig.RENDER_STATS, value -> EmtraClientConfig.RENDER_STATS = value);
		section("options.emtra.section_sound");
		toggle("options.emtra.train_muffle", () -> EmtraClientConfig.TRAIN_MUFFLE, value -> EmtraClientConfig.TRAIN_MUFFLE = value);
		slider("options.emtra.train_muffle_volume", 20, 5, "%", EmtraClientConfig.TRAIN_MUFFLE_VOLUME, value -> EmtraClientConfig.TRAIN_MUFFLE_VOLUME = value);
		slider("options.emtra.train_muffle_treble", 20, 5, "%", EmtraClientConfig.TRAIN_MUFFLE_TREBLE, value -> EmtraClientConfig.TRAIN_MUFFLE_TREBLE = value);
		slider("options.emtra.train_muffle_door_open", 20, 5, "%", EmtraClientConfig.TRAIN_MUFFLE_DOOR_OPEN, value -> EmtraClientConfig.TRAIN_MUFFLE_DOOR_OPEN = value);
		slider("options.emtra.train_muffle_fade", 25, 100, " ms", EmtraClientConfig.TRAIN_MUFFLE_FADE_MS, value -> EmtraClientConfig.TRAIN_MUFFLE_FADE_MS = value);
		slider("options.emtra.train_muffle_door_fade", 20, 50, " ms", EmtraClientConfig.TRAIN_MUFFLE_DOOR_FADE_MS, value -> EmtraClientConfig.TRAIN_MUFFLE_DOOR_FADE_MS = value);
		toggle("options.emtra.sound_debug", () -> EmtraClientConfig.SOUND_DEBUG, value -> EmtraClientConfig.SOUND_DEBUG = value);
		buttonDone = new ButtonWidgetExtension(0, 0, 0, 20, TextHelper.translatable("gui.done"), button -> onClose2());
	}

	@Override
	protected void init2() {
		super.init2();
		final int controlX = getWidthMapped() - 20 - CONTROL_WIDTH;
		for (final Row row : rows) {
			if (row.button != null) {
				IDrawing.setPositionAndWidth(row.button, controlX, 0, CONTROL_WIDTH);
				row.refresh();
				addChild(new ClickableWidget(row.button));
			} else if (row.slider != null) {
				row.slider.setHeight(16);
				IDrawing.setPositionAndWidth(row.slider, controlX, 0, CONTROL_WIDTH - 6 - GraphicsHolder.getTextWidth("2500 ms"));
				row.slider.setValue(row.initialSliderValue);
				addChild(new ClickableWidget(row.slider));
			}
		}
		IDrawing.setPositionAndWidth(buttonDone, getWidthMapped() / 2 - 75, getHeightMapped() - 24, 150);
		addChild(new ClickableWidget(buttonDone));
		layout();
	}

	/** Places the rows for the current scroll position; rows outside the list area are moved off screen. */
	private void layout() {
		final int bottom = getHeightMapped() - BOTTOM_MARGIN;
		int contentHeight = 0;
		for (final Row row : rows) {
			contentHeight += row.button == null && row.slider == null ? ROW_HEIGHT - 3 : ROW_HEIGHT;
		}
		maxScroll = Math.max(0, TOP + contentHeight - bottom);
		scroll = Math.max(0, Math.min(scroll, maxScroll));
		int y = TOP - scroll;
		for (final Row row : rows) {
			row.y = y;
			row.visible = y >= TOP - 2 && y + 16 <= bottom;
			final int widgetY = row.visible ? y : -1000;
			if (row.button != null) {
				row.button.setY2(widgetY);
			} else if (row.slider != null) {
				row.slider.setY2(widgetY);
			}
			y += row.button == null && row.slider == null ? ROW_HEIGHT - 3 : ROW_HEIGHT;
		}
	}

	@Override
	public boolean mouseScrolled2(double mouseX, double mouseY, double amount) {
		if (maxScroll > 0) {
			scroll -= (int) Math.round(amount * ROW_HEIGHT);
			layout();
			return true;
		}
		return super.mouseScrolled2(mouseX, mouseY, amount);
	}

	@Override
	public void render(GraphicsHolder graphicsHolder, int mouseX, int mouseY, float delta) {
		renderBackground(graphicsHolder);
		graphicsHolder.drawCenteredText(TextHelper.translatable("options.emtra.title"), getWidthMapped() / 2, 8, 0xFFFFFFFF);
		for (final Row row : rows) {
			if (!row.visible) {
				continue;
			}
			if (row.button == null && row.slider == null) {
				graphicsHolder.drawText(TextHelper.translatable(row.key), 14, row.y + 4, 0xFFFFAA00, false, GraphicsHolder.getDefaultLight());
			} else {
				graphicsHolder.drawText(TextHelper.translatable(row.key), 20, row.y + 4, 0xFFFFFFFF, false, GraphicsHolder.getDefaultLight());
			}
		}
		if (maxScroll > 0) {
			// Scroll position hint on the right edge.
			final int bottom = getHeightMapped() - BOTTOM_MARGIN;
			final int barHeight = Math.max(10, (bottom - TOP) * (bottom - TOP) / (bottom - TOP + maxScroll));
			final int barY = TOP + (bottom - TOP - barHeight) * scroll / maxScroll;
			graphicsHolder.drawText("|", getWidthMapped() - 8, barY, 0xFF888888, false, GraphicsHolder.getDefaultLight());
			graphicsHolder.drawText("|", getWidthMapped() - 8, barY + barHeight - 8, 0xFF888888, false, GraphicsHolder.getDefaultLight());
		}
		super.render(graphicsHolder, mouseX, mouseY, delta);
	}

	@Override
	public void onClose2() {
		EmtraClientConfig.save();
		super.onClose2();
	}

	private void section(String key) {
		rows.add(new Row(key, null, null, 0));
	}

	private void toggle(String key, BooleanSupplier getter, Consumer<Boolean> setter) {
		final Row[] holder = new Row[1];
		final ButtonWidgetExtension button = new ButtonWidgetExtension(0, 0, 0, 16, TextHelper.literal(""), pressed -> {
			setter.accept(!getter.getAsBoolean());
			holder[0].refresh();
		});
		holder[0] = new Row(key, button, null, 0);
		holder[0].getter = getter;
		rows.add(holder[0]);
	}

	private void slider(String key, int steps, int stepSize, String unit, int currentValue, java.util.function.IntConsumer setter) {
		final WidgetShorterSlider slider = new WidgetShorterSlider(0, 0, steps, value -> value * stepSize + unit, value -> setter.accept(value * stepSize));
		rows.add(new Row(key, null, slider, Math.round((float) currentValue / stepSize)));
	}

	private static Text text(MutableText mutableText) {
		return new Text(mutableText.data);
	}

	private static final class Row {
		private final String key;
		@Nullable
		private final ButtonWidgetExtension button;
		@Nullable
		private final WidgetShorterSlider slider;
		private final int initialSliderValue;
		private BooleanSupplier getter;
		private int y;
		private boolean visible = true;

		private Row(String key, @Nullable ButtonWidgetExtension button, @Nullable WidgetShorterSlider slider, int initialSliderValue) {
			this.key = key;
			this.button = button;
			this.slider = slider;
			this.initialSliderValue = initialSliderValue;
		}

		private void refresh() {
			if (button != null && getter != null) {
				button.setMessage2(text(TextHelper.translatable(getter.getAsBoolean() ? "options.emtra.on" : "options.emtra.off")));
			}
		}
	}
}
