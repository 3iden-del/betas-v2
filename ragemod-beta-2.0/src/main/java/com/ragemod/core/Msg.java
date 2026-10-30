package com.ragemod.core;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/** Small helpers for chat output and session info. */
public final class Msg {
	private Msg() {}

	public static void say(String text) {
		say(text, 0xFF5555);
	}

	public static void say(String text, int prefixRgb) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Component prefix = Component.literal("[RAGE] ").withStyle(s -> s.withColor(prefixRgb));
		Component body = Component.literal(text).withStyle(s -> s.withColor(0xEEDDDD));
		mc.player.displayClientMessage(Component.empty().append(prefix).append(body), false);
	}

	/** Chat alert with a colored group label, e.g. [RAGE] [Target] Name joined the lobby. */
	public static void alert(String label, int labelRgb, String text) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		Component prefix = Component.literal("[RAGE] ").withStyle(s -> s.withColor(0xFF5555));
		Component tag = Component.literal("[" + label + "] ").withStyle(s -> s.withColor(labelRgb));
		Component body = Component.literal(text).withStyle(s -> s.withColor(0xEEDDDD));
		mc.player.displayClientMessage(Component.empty().append(prefix).append(tag).append(body), false);
	}

	/** Your Minecraft username. */
	public static String me() {
		Minecraft mc = Minecraft.getInstance();
		return mc.getUser().getName();
	}

	/** Address of the server you're on, or "singleplayer". */
	public static String server() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getCurrentServer() != null && mc.getCurrentServer().ip != null) return mc.getCurrentServer().ip;
		return "singleplayer";
	}
}
