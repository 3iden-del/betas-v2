package com.ragemod;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

/** All keybinds (Options > Controls > RAGE Mod). */
public final class Keys {
	public static KeyMapping.Category category;
	public static KeyMapping menu;
	public static KeyMapping radial;
	public static KeyMapping hitboxes;
	public static KeyMapping afk;
	public static KeyMapping autoJoin;
	public static KeyMapping kill;
	public static KeyMapping help;
	public static KeyMapping stats;
	public static KeyMapping players;
	public static KeyMapping info;
	public static KeyMapping priority;
	public static KeyMapping network;
	public static KeyMapping config;

	private Keys() {}

	private static KeyMapping make(String id, InputConstants.Type type, int code) {
		return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.ragemod." + id, type, code, category));
	}

	public static void register() {
		category = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("ragemod", "main"));
		menu = make("menu", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT);
		radial = make("radial", InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_MIDDLE);
		hitboxes = make("hitboxes", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
		afk = make("afk", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
		autoJoin = make("autojoin", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
		kill = make("kill", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
		help = make("help", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
		stats = make("stats", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
		players = make("players", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
		info = make("info", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
		priority = make("priority", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
		network = make("network", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
		config = make("config", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN);
	}
}
