package com.ragemod;

import com.ragemod.core.Config;
import com.ragemod.core.LobbyWatcher;
import com.ragemod.core.War;
import com.ragemod.core.Msg;
import com.ragemod.core.Opt;
import com.ragemod.core.StatusStore;
import com.ragemod.core.WhitelistGate;
import com.ragemod.gui.ConfigScreen;
import com.ragemod.gui.LockedScreen;
import com.ragemod.gui.PlayerInfoScreen;
import com.ragemod.gui.MainMenuScreen;
import com.ragemod.gui.NetworkScreen;
import com.ragemod.gui.PlayersScreen;
import com.ragemod.gui.RadialScreen;
import com.ragemod.gui.StatsScreen;
import com.ragemod.net.RelayClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

/** Per-tick logic: keybinds, anti-AFK, auto join, kill key, ignore filter. */
public final class Features {
	// anti AFK
	public static boolean afk;
	private static int afkTicks;
	private static boolean afkFlip;
	private static boolean jumpRelease;

	// auto join
	public static boolean autoJoin;
	public static int autoJoinAttempts;
	private static int ajTicks;
	private static Object ajPlayer;
	private static Object ajLevel;
	private static String ajCommand = "";

	// last command the user typed (for auto join)
	private static String lastCommand = "";
	private static boolean ownCommand;

	private Features() {}

	public static void rememberCommand(String cmd) {
		if (ownCommand) return;
		String c = cmd.trim();
		if (!c.isEmpty()) lastCommand = c;
	}

	private static void sendCommand(Minecraft mc, String cmd) {
		if (mc.player == null) return;
		String c = cmd.trim();
		if (c.startsWith("/")) c = c.substring(1);
		if (c.isEmpty()) return;
		ownCommand = true;
		try {
			mc.player.connection.sendCommand(c);
		} finally {
			ownCommand = false;
		}
	}

	private static void toggleOpt(Opt o, String label) {
		o.cycle(1);
		Config.save();
		Msg.say(label + ": " + (o.b() ? "ON" : "OFF"));
	}

	public static void tick(Minecraft mc) {
		RelayClient.tick();
		LobbyWatcher.tick(mc);
		if (mc.player == null) {
			if (autoJoin) stopAutoJoin("Disconnected, auto join stopped.");
			return;
		}

		while (Keys.menu.consumeClick()) {
			if (WhitelistGate.allowed()) mc.setScreen(new MainMenuScreen());
			else mc.setScreen(new LockedScreen());
		}
		while (Keys.config.consumeClick()) {
			if (WhitelistGate.ensureAllowed()) mc.setScreen(new ConfigScreen(null));
		}
		while (Keys.stats.consumeClick()) {
			if (WhitelistGate.ensureAllowed()) mc.setScreen(new StatsScreen(null, null));
		}
		while (Keys.players.consumeClick()) {
			if (WhitelistGate.ensureAllowed()) mc.setScreen(new PlayersScreen(null));
		}
		while (Keys.network.consumeClick()) {
			if (WhitelistGate.ensureAllowed()) mc.setScreen(new NetworkScreen(null, null));
		}
		while (Keys.radial.consumeClick()) {
			if (!Config.RADIAL.b() || !WhitelistGate.ensureAllowed()) continue;
			Entity e = mc.crosshairPickEntity;
			if (e instanceof AbstractClientPlayer p && p != mc.player) mc.setScreen(new RadialScreen(p));
		}
		while (Keys.hitboxes.consumeClick()) {
			if (WhitelistGate.ensureAllowed()) toggleOpt(Config.HITBOXES, "Hitboxes");
		}
		while (Keys.info.consumeClick()) {
			if (WhitelistGate.ensureAllowed()) mc.setScreen(new PlayerInfoScreen(null, null));
		}
		while (Keys.priority.consumeClick()) {
			if (!WhitelistGate.ensureAllowed()) continue;
			Entity e = mc.crosshairPickEntity;
			if (e instanceof AbstractClientPlayer p && p != mc.player) {
				War.togglePriority(p.getName().getString());
				Msg.say(War.isPriority(p.getName().getString()) ? "Top priority target: " + p.getName().getString() : "Top priority cleared.");
			} else {
				Msg.say("Look at a player to set them as the top priority target.");
			}
		}
		while (Keys.afk.consumeClick()) {
			if (!WhitelistGate.ensureAllowed()) continue;
			afk = !afk;
			afkTicks = 0;
			Msg.say("Anti-AFK: " + (afk ? "ON" : "OFF"));
		}
		while (Keys.autoJoin.consumeClick()) {
			if (!WhitelistGate.ensureAllowed()) continue;
			if (autoJoin) stopAutoJoin("Auto join stopped.");
			else startAutoJoin(mc);
		}
		while (Keys.kill.consumeClick()) {
			if (WhitelistGate.ensureAllowed()) sendCommand(mc, Config.KILL_COMMAND.s());
		}
		while (Keys.help.consumeClick()) {
			if (!WhitelistGate.ensureAllowed()) continue;
			if (RelayClient.connected()) {
				RelayClient.sendHelp("");
				Msg.say("Help request sent to " + RelayClient.peers().size() + " mod user(s).");
			} else {
				Msg.say("Not connected to the relay, can't ask for help.");
			}
		}

		if (!WhitelistGate.allowed()) {
			afk = false;
			if (autoJoin) stopAutoJoin("Auto join stopped.");
			return;
		}

		tickAfk(mc);
		tickAutoJoin(mc);
	}

	private static void tickAfk(Minecraft mc) {
		if (jumpRelease) {
			mc.options.keyJump.setDown(false);
			jumpRelease = false;
		}
		if (!afk) return;
		if (++afkTicks < Config.AFK_INTERVAL.i() * 20) return;
		afkTicks = 0;
		float d = afkFlip ? 1.5f : -1.5f;
		afkFlip = !afkFlip;
		mc.player.setYRot(mc.player.getYRot() + d);
		mc.player.swing(InteractionHand.MAIN_HAND);
		if (Config.AFK_JUMP.b()) {
			mc.options.keyJump.setDown(true);
			jumpRelease = true;
		}
	}

	private static void startAutoJoin(Minecraft mc) {
		String cmd = Config.AJ_COMMAND.s().isBlank() ? lastCommand : Config.AJ_COMMAND.s();
		if (cmd.isBlank()) {
			Msg.say("No command to repeat. Type your join command once in chat, or set autoJoinCommand in the config.");
			return;
		}
		ajCommand = cmd;
		autoJoin = true;
		autoJoinAttempts = 0;
		ajTicks = Config.AJ_INTERVAL.i() * 20; // fire right away
		ajPlayer = mc.player;
		ajLevel = mc.level;
		Msg.say("Auto join ON, repeating: /" + cmd.replaceFirst("^/", ""));
	}

	public static void stopAutoJoin(String message) {
		if (!autoJoin) return;
		autoJoin = false;
		if (message != null) Msg.say(message);
	}

	private static void tickAutoJoin(Minecraft mc) {
		if (!autoJoin) return;
		if (mc.player != ajPlayer || mc.level != ajLevel) {
			stopAutoJoin("You joined a new world, auto join stopped.");
			return;
		}
		if (++ajTicks < Config.AJ_INTERVAL.i() * 20) return;
		ajTicks = 0;
		if (++autoJoinAttempts > Config.AJ_MAX.i()) {
			stopAutoJoin("Auto join gave up after " + Config.AJ_MAX.i() + " attempts.");
			return;
		}
		sendCommand(mc, ajCommand);
	}

	/** Hide chat lines sent by ignored players. Used by the chat/game message filters. */
	public static boolean allowText(String plain) {
		if (!Config.IGNORE_CHAT.b() || !WhitelistGate.allowed()) return true;
		String lower = plain.toLowerCase(Locale.ROOT);
		int sep = firstSeparator(lower);
		if (sep < 0) return true;
		for (StatusStore.Entry e : StatusStore.all()) {
			if (!e.ignored) continue;
			int idx = lower.indexOf(e.name.toLowerCase(Locale.ROOT));
			if (idx >= 0 && idx < sep) return false;
		}
		return true;
	}

	private static int firstSeparator(String s) {
		int best = -1;
		for (char c : new char[] {':', '\u00BB', '>'}) {
			int i = s.indexOf(c);
			if (i >= 0 && (best < 0 || i < best)) best = i;
		}
		return best;
	}
}
