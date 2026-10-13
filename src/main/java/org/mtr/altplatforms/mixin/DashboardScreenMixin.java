package org.mtr.altplatforms.mixin;

import org.mtr.altplatforms.duck.IAltRoutePlatformData;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.core.operation.UpdateDataRequest;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mapping.holder.ClickableWidget;
import org.mtr.mapping.holder.MinecraftClient;
import org.mtr.mapping.holder.MutableText;
import org.mtr.mapping.holder.Screen;
import org.mtr.mapping.holder.Text;
import org.mtr.mapping.mapper.ButtonWidgetExtension;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mapping.mapper.TextHelper;
import org.mtr.mod.InitClient;
import org.mtr.mod.client.IDrawing;
import org.mtr.mod.client.MinecraftClientData;
import org.mtr.mod.data.IGui;
import org.mtr.mod.packet.PacketUpdateData;
import org.mtr.mod.screen.DashboardListItem;
import org.mtr.mod.screen.DashboardListSelectorScreen;
import org.mtr.mod.screen.DashboardScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = DashboardScreen.class, remap = false)
public abstract class DashboardScreenMixin extends ScreenExtension implements IGui {

	@Shadow
	private Route editingRoute;

	@Shadow
	private int editingRoutePlatformIndex;

	@Shadow
	private ButtonWidgetExtension buttonDoneEditingRouteDestination;

	@Shadow
	abstract void toggleButtons();

	@Shadow
	abstract boolean isValidRoutePlatformIndex();

	@Unique
	private ButtonWidgetExtension altplatforms$buttonSelectAlternatePlatforms;

	@Inject(method = "init2", at = @At("TAIL"))
	private void altplatforms$onInit2(CallbackInfo ci) {
		altplatforms$buttonSelectAlternatePlatforms = new ButtonWidgetExtension(0, 0, 0, SQUARE_SIZE, TextHelper.translatable("gui.mtr.alt_platforms"), button -> altplatforms$onClickSelectAlternatePlatforms());
		final int bottomRowY = height - SQUARE_SIZE;
		IDrawing.setPositionAndWidth(altplatforms$buttonSelectAlternatePlatforms, 0, bottomRowY, PANEL_WIDTH / 2);
		IDrawing.setPositionAndWidth(buttonDoneEditingRouteDestination, PANEL_WIDTH / 2, bottomRowY, PANEL_WIDTH / 2);
		altplatforms$buttonSelectAlternatePlatforms.visible = false;
		addChild(new ClickableWidget(altplatforms$buttonSelectAlternatePlatforms));
	}

	@Inject(method = "toggleButtons", at = @At("TAIL"))
	private void altplatforms$onToggleButtons(CallbackInfo ci) {
		if (altplatforms$buttonSelectAlternatePlatforms != null) {
			final boolean visible = buttonDoneEditingRouteDestination != null && buttonDoneEditingRouteDestination.visible && editingRoute != null && isValidRoutePlatformIndex();
			altplatforms$buttonSelectAlternatePlatforms.visible = visible;
			if (visible) {
				final int bottomRowY = height - SQUARE_SIZE;
				IDrawing.setPositionAndWidth(altplatforms$buttonSelectAlternatePlatforms, 0, bottomRowY, PANEL_WIDTH / 2);
				IDrawing.setPositionAndWidth(buttonDoneEditingRouteDestination, PANEL_WIDTH / 2, bottomRowY, PANEL_WIDTH / 2);
				final RoutePlatformData rpd = editingRoute.getRoutePlatforms().get(editingRoutePlatformIndex);
				final int count = ((IAltRoutePlatformData) (Object) rpd).altplatforms$getAlternatePlatformIds().size();
				final MutableText text = TextHelper.translatable("gui.mtr.alt_platforms");
				altplatforms$buttonSelectAlternatePlatforms.setMessage2(count > 0 ? new Text(TextHelper.literal(String.format("%s (%d)", text.getString(), count)).data) : new Text(text.data));
			}
		}
	}

	@Unique
	private void altplatforms$onClickSelectAlternatePlatforms() {
		if (isValidRoutePlatformIndex()) {
			final RoutePlatformData routePlatformData = editingRoute.getRoutePlatforms().get(editingRoutePlatformIndex);
			final Platform currentPlatform = routePlatformData.platform;
			final ObjectArrayList<DashboardListItem> availablePlatforms = new ObjectArrayList<>();
			if (currentPlatform != null && currentPlatform.area != null) {
				currentPlatform.area.savedRails.forEach(platform -> {
					if (platform.getId() != currentPlatform.getId()) {
						availablePlatforms.add(new DashboardListItem(platform.getId(), platform.getName(), currentPlatform.area.getColor()));
					}
				});
			} else {
				MinecraftClientData.getDashboardInstance().platforms.forEach(platform -> {
					if (currentPlatform == null || platform.getId() != currentPlatform.getId()) {
						final String name = platform.area == null ? platform.getName() : String.format("%s (%s)", platform.area.getName(), platform.getName());
						final int color = platform.area == null ? 0 : platform.area.getColor();
						availablePlatforms.add(new DashboardListItem(platform.getId(), name, color));
					}
				});
			}

			final LongArrayList altIds = ((IAltRoutePlatformData) (Object) routePlatformData).altplatforms$getAlternatePlatformIds();

			MinecraftClient.getInstance().openScreen(new Screen(new DashboardListSelectorScreen(
					() -> {
						final LongArrayList copiedIds = new LongArrayList(altIds);
						if (currentPlatform != null) {
							editingRoute.getRoutePlatforms().forEach(rpd -> {
								if (rpd.platform != null && rpd.platform.getId() == currentPlatform.getId()) {
									if (rpd != routePlatformData) {
										final LongArrayList otherAltIds = ((IAltRoutePlatformData) (Object) rpd).altplatforms$getAlternatePlatformIds();
										otherAltIds.clear();
										otherAltIds.addAll(copiedIds);
									}
									rpd.writePlatformCache(editingRoute, MinecraftClientData.getDashboardInstance().platformIdMap);
								}
							});
						}
						routePlatformData.writePlatformCache(editingRoute, MinecraftClientData.getDashboardInstance().platformIdMap);
						InitClient.REGISTRY_CLIENT.sendPacketToServer(new PacketUpdateData(new UpdateDataRequest(MinecraftClientData.getDashboardInstance()).addRoute(editingRoute)));
						toggleButtons();
					},
					new ObjectImmutableList<>(availablePlatforms),
					altIds,
					false,
					false,
					this
			)));
		}
	}
}
