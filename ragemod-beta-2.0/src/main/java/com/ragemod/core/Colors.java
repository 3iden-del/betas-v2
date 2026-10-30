package com.ragemod.core;

/** Named color palette used by the config (matches the "green / red / gold" style dropdown). */
public final class Colors {
	public static final String[] NAMES = {
		"red", "dark_red", "orange", "gold", "yellow", "green", "dark_green", "aqua", "cyan",
		"dark_aqua", "blue", "dark_blue", "light_purple", "dark_purple", "pink", "white", "gray", "dark_gray"
	};
	public static final int[] RGB = {
		0xFF5555, 0xAA0000, 0xFF8C1A, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x00AA00, 0x55FFFF, 0x00E5FF,
		0x00AAAA, 0x5555FF, 0x0000AA, 0xFF55FF, 0xAA00AA, 0xFF7AB8, 0xFFFFFF, 0xAAAAAA, 0x555555
	};

	private Colors() {}

	public static int index(String name) {
		for (int i = 0; i < NAMES.length; i++) {
			if (NAMES[i].equalsIgnoreCase(name)) return i;
		}
		return 0;
	}

	public static int rgb(String name) {
		return RGB[index(name)];
	}

	public static String cycle(String name, int dir) {
		int i = index(name) + (dir >= 0 ? 1 : -1);
		if (i < 0) i = NAMES.length - 1;
		if (i >= NAMES.length) i = 0;
		return NAMES[i];
	}
}
