package com.ragemod;

import com.ragemod.core.ChatLog;
import com.ragemod.core.Config;
import com.ragemod.core.KillTracker;
import com.ragemod.core.NameStore;
import com.ragemod.core.StatusStore;
import com.ragemod.core.WhitelistGate;
import com.ragemod.net.RelayClient;
import com.ragemod.render.Esp;
import com.ragemod.render.Hud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

public class RageMod implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		Config.load();
		StatusStore.load();
		KillTracker.load();
		NameStore.load();
		ChatLog.load();
		WhitelistGate.start();
		Keys.register();

		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("ragemod", "esp"), Esp::render);
		HudElementRegistry.addLast(Identifier.fromNamespaceAndPath("ragemod", "indicators"), Hud::render);

		ClientTickEvents.END_CLIENT_TICK.register(Features::tick);

		// kill tracking + ignore filter
		ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> overlay || Features.allowText(message.getString()));
		ClientReceiveMessageEvents.ALLOW_CHAT.register((message, signed, sender, params, time) -> {
			if (sender != null && StatusStore.ignored(sender.name()) && Config.IGNORE_CHAT.b() && WhitelistGate.allowed()) return false;
			return Features.allowText(message.getString());
		});
		ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
			if (overlay) return;
			String text = message.getString();
			KillTracker.onMessage(text);
			ChatLog.onMessage(text, null);
		});
		ClientReceiveMessageEvents.CHAT.register((message, signed, sender, params, time) -> {
			String text = message.getString();
			KillTracker.onMessage(text);
			ChatLog.onMessage(text, sender == null ? null : sender.name());
		});

		// remember the last typed command (for auto join)
		ClientSendMessageEvents.COMMAND.register(Features::rememberCommand);

		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> RelayClient.updateServer());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			KillTracker.save();
			StatusStore.save();
			ChatLog.save();
			NameStore.save();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			KillTracker.save();
			StatusStore.save();
			ChatLog.save();
			NameStore.save();
			Config.save();
			RelayClient.disconnect();
		});
	}
}
