package com.ragemod.core;

import net.minecraft.network.chat.Component;

/** Shared recolor logic for name tags and the tab list. */
public final class NickColors {
	private NickColors() {}

	public static Component nick(String name, Component original) {
		if (original == null || !Config.CHANGE_NICK.b() || !WhitelistGate.allowed()) return original;
		return recolor(name, original);
	}

	public static Component tab(String name, Component original) {
		if (original == null || !Config.CHANGE_TAB.b() || !WhitelistGate.allowed()) return original;
		return recolor(name, original);
	}

	private static Component recolor(String name, Component original) {
		Integer rgb = Marks.nickRgb(name);
		if (rgb == null) return original;
		final int color = rgb;
		return Component.literal(original.getString()).withStyle(s -> s.withColor(color));
	}
}
