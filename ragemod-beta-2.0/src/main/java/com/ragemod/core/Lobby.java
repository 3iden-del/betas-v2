package com.ragemod.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Everyone currently in the tab list. */
public final class Lobby {
	private Lobby() {}

	public static List<String> names(boolean includeSelf) {
		List<String> out = new ArrayList<>();
		Set<String> seen = new HashSet<>();
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener conn = mc.getConnection();
		if (conn == null) return out;
		String me = Msg.me().toLowerCase(Locale.ROOT);
		for (PlayerInfo info : conn.getOnlinePlayers()) {
			String n = info.getProfile().name();
			String k = n.toLowerCase(Locale.ROOT);
			if (!includeSelf && k.equals(me)) continue;
			if (seen.add(k)) out.add(n);
		}
		out.sort(String.CASE_INSENSITIVE_ORDER);
		return out;
	}

	/** UUID from the tab list for this name, or null if the player isn't in the lobby. */
	public static java.util.UUID uuidOf(String name) {
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener conn = mc.getConnection();
		if (conn == null) return null;
		for (PlayerInfo info : conn.getOnlinePlayers()) {
			if (info.getProfile().name().equalsIgnoreCase(name)) return info.getProfile().id();
		}
		return null;
	}

	public static boolean contains(String name) {
		for (String n : names(true)) {
			if (n.equalsIgnoreCase(name)) return true;
		}
		return false;
	}
}
