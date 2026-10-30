package com.ragemod.core;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/** Watches the tab list: chat alerts when marked players join or leave, and records seen names. */
public final class LobbyWatcher {
	private static Map<String, String> previous = new LinkedHashMap<>();
	private static Object lastLevel;
	private static long readyAt;
	private static int tick;

	private LobbyWatcher() {}

	public static void tick(Minecraft mc) {
		ClientPacketListener conn = mc.getConnection();
		if (mc.level == null || conn == null || mc.player == null) {
			previous = new LinkedHashMap<>();
			lastLevel = null;
			return;
		}
		if (!WhitelistGate.allowed()) return;

		if (mc.level != lastLevel) {
			lastLevel = mc.level;
			previous = snapshot(conn, false);
			readyAt = System.currentTimeMillis() + 4000; // ignore the join burst
			return;
		}
		if (++tick % 10 != 0) return;

		Map<String, String> current = snapshot(conn, true);
		if (System.currentTimeMillis() >= readyAt) {
			for (Map.Entry<String, String> e : current.entrySet()) {
				if (!previous.containsKey(e.getKey())) alert(e.getValue(), true);
			}
			for (Map.Entry<String, String> e : previous.entrySet()) {
				if (!current.containsKey(e.getKey())) alert(e.getValue(), false);
			}
		}
		previous = current;
	}

	private static Map<String, String> snapshot(ClientPacketListener conn, boolean observe) {
		Map<String, String> out = new LinkedHashMap<>();
		String me = Msg.me().toLowerCase(Locale.ROOT);
		for (PlayerInfo info : conn.getOnlinePlayers()) {
			String name = info.getProfile().name();
			if (observe) NameStore.observe(info.getProfile().id().toString(), name);
			String k = name.toLowerCase(Locale.ROOT);
			if (!k.equals(me)) out.put(k, name);
		}
		return out;
	}

	private static void alert(String name, boolean joined) {
		boolean prio = War.isPriority(name);
		Status s = StatusStore.status(name);
		boolean on = false;
		if (prio && Config.ALERT_PRIORITY.b()) on = true;
		switch (s) {
			case TARGET -> on |= Config.ALERT_TARGETS.b();
			case ENEMY -> on |= Config.ALERT_ENEMIES.b();
			case FRIEND -> on |= Config.ALERT_FRIENDS.b();
			case ALLIANCE -> on |= Config.ALERT_ALLIANCES.b();
			case MUTUAL -> on |= Config.ALERT_MUTUALS.b();
			default -> { }
		}
		if (!on) return;

		String label = prio ? "TOP TARGET" : s.label;
		int rgb = prio ? Config.PRIORITY_COLOR.rgb() : s.rgb();
		Msg.alert(label, rgb, name + (joined ? " joined the lobby." : " left the lobby."));
		if (Config.ALERT_SOUND.b()) {
			Minecraft mc = Minecraft.getInstance();
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), joined ? 1.9f : 0.8f));
		}
	}
}
