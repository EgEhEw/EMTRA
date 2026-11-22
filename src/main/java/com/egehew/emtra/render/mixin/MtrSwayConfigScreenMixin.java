package com.egehew.emtra.render.mixin;

import com.egehew.emtra.render.EmtraClientConfig;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.mapper.GraphicsHolder;
import org.mtr.mapping.mapper.GuiDrawing;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.config.Client;
import org.mtr.mod.screen.ConfigScreen;
import org.mtr.mod.screen.WidgetShorterSlider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(value = ConfigScreen.class, remap = false)
public abstract class MtrSwayConfigScreenMixin {
	@Shadow @Final private WidgetShorterSlider sliderTrainOscillationMultiplier;

	@Inject(method = "init2", at = @At("TAIL"))
	private void emtra$setStockSwaySliderActive(CallbackInfo ci) {
		new ClickableWidget(sliderTrainOscillationMultiplier).setActiveMapped(!EmtraClientConfig.ADVANCED_SWAY);
	}

	@ModifyArgs(method = "render", at = @At(value = "INVOKE", ordinal = 5, target =
			"Lorg/mtr/mapping/mapper/GraphicsHolder;drawText(Lorg/mtr/mapping/holder/MutableText;IIIZI)V"))
	private void emtra$labelLockedSway(Args args) {
		if (EmtraClientConfig.ADVANCED_SWAY) {
			args.set(0, TextHelper.translatable("options.emtra.mtr_sway_locked"));
			args.set(3, 0xFFA0A0A0);
		}
	}

	@Inject(method = "render", at = @At("TAIL"))
	private void emtra$dimStockSwaySlider(GraphicsHolder graphics, int mouseX, int mouseY, float delta, CallbackInfo ci) {
		if (!EmtraClientConfig.ADVANCED_SWAY) return;

		// MTR draws its own slider texture and does not tint it when the widget is inactive.
		ClickableWidget widget = new ClickableWidget(sliderTrainOscillationMultiplier);
		int x = sliderTrainOscillationMultiplier.getX2();
		int y = sliderTrainOscillationMultiplier.getY2();
		int labelWidth = GraphicsHolder.getTextWidth(sliderTrainOscillationMultiplier.getMessage2().getString());
		GuiDrawing drawing = new GuiDrawing(graphics);
		drawing.beginDrawingRectangle();
		drawing.drawRectangle(x, y, x + widget.getWidth() + 4 + labelWidth, y + widget.getHeight(), 0x80000000);
		drawing.finishDrawingRectangle();
	}

	@Redirect(method = "onClose2", at = @At(value = "INVOKE",
			target = "Lorg/mtr/mod/config/Client;setVehicleOscillationMultiplier(D)V"))
	private void emtra$preserveStockSway(Client client, double value) {
		if (!EmtraClientConfig.ADVANCED_SWAY) client.setVehicleOscillationMultiplier(value);
	}
}
