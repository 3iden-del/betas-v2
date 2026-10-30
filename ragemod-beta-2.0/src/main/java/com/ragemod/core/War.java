package com.ragemod.core;

import com.ragemod.net.RelayClient;

import java.util.List;

/** War mode state. Enemies are players with Status.ENEMY. One enemy can be the top priority target. */
public final class War {
	public static volatile boolean active;
	public static volatile boolean joined;
	public static volatile boolean leading;
	public static volatile String leader = "-";
	public static volatile String server = "-";
	public static volatile String note = "";
	private static volatile String priority = "";

	private War() {}

	public static int enemies() {
		return StatusStore.count(Status.ENEMY);
	}

	// ---- top priority target ----

	public static String priority() {
		return priority;
	}

	public static boolean isPriority(String name) {
		String p = priority;
		return !p.isEmpty() && p.equalsIgnoreCase(name);
	}

	/** Sets (or clears, if it is already the priority) the top priority target and tells the clan. */
	public static void togglePriority(String name) {
		if (isPriority(name)) {
			priority = "";
			note = "Top priority cleared.";
			RelayClient.sendPriority("");
		} else {
			priority = name;
			note = "Top priority: " + name;
			RelayClient.sendPriority(name);
		}
	}

	public static void clearPriority() {
		if (priority.isEmpty()) return;
		priority = "";
		note = "Top priority cleared.";
		RelayClient.sendPriority("");
	}

	public static void onRemotePriority(String from, String name) {
		priority = name == null ? "" : name;
		if (priority.isEmpty()) {
			Msg.say(from + " cleared the top priority target.");
		} else {
			Msg.say(from + " set the TOP PRIORITY target: " + priority, 0xFFFF55);
		}
	}

	// ---- war state ----

	public static void start() {
		active = true;
		joined = true;
		leading = true;
		leader = Msg.me();
		server = Msg.server();
		note = "War intent sent.";
		RelayClient.sendWar("start");
	}

	public static void end() {
		boolean wasLeading = leading;
		active = false;
		joined = false;
		leading = false;
		priority = "";
		note = "War ended.";
		if (wasLeading) RelayClient.sendWar("end");
	}

	public static void join() {
		if (!active) return;
		joined = true;
		note = "Joined the war.";
		RelayClient.sendWar("join");
	}

	/** Called when the leader announces a war over the relay. */
	public static void onRemoteStart(String from, String srv, List<String> enemyNames) {
		active = true;
		joined = false;
		leading = false;
		leader = from;
		server = srv;
		for (String n : enemyNames) StatusStore.set(n, Status.ENEMY);
		note = from + " started a war. Open the War menu to join.";
		Msg.say(from + " started a war on " + srv + " with " + enemyNames.size() + " enemies. Open the War menu to join.");
	}

	public static void onRemoteEnd(String from) {
		if (!leader.equalsIgnoreCase(from)) return;
		active = false;
		joined = false;
		leading = false;
		priority = "";
		note = "War ended by " + from + ".";
		Msg.say("War ended by " + from + ".");
	}
}
