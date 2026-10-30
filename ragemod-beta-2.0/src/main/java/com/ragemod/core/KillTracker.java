package com.ragemod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads kill messages from chat, stores them, and builds stats for the dashboard. */
public final class KillTracker {
	public record Kill(long time, String killer, String victim) {}

	public record Stats(int kills, int deaths, double[] series) {
		public double kd() {
			return deaths == 0 ? kills : (double) kills / deaths;
		}
	}

	private static final List<Kill> EVENTS = new ArrayList<>();
	public static final long SESSION_START = System.currentTimeMillis();

	private static volatile String killerName;
	private static volatile long killerUntil;
	private static String lastKey = "";
	private static long lastKeyTime;

	// "<victim> was slain by <killer>"
	private static final Pattern WAS_BY = Pattern.compile("([A-Za-z0-9_]{3,16}) was ([A-Za-z' ]{3,30}?) by ([A-Za-z0-9_]{3,16})");
	// "<killer> killed <victim>"
	private static final Pattern KILLED = Pattern.compile("([A-Za-z0-9_]{3,16}) (?:killed|slew|eliminated|murdered|assassinated) ([A-Za-z0-9_]{3,16})");

	private KillTracker() {}

	public static boolean isKiller(String name) {
		String k = killerName;
		return k != null && k.equalsIgnoreCase(name) && System.currentTimeMillis() < killerUntil;
	}

	public static String currentKiller() {
		return isKiller(killerName == null ? "" : killerName) ? killerName : null;
	}

	private static boolean isMe(String n) {
		Minecraft mc = Minecraft.getInstance();
		if (n.equalsIgnoreCase(mc.getUser().getName())) return true;
		return mc.player != null && n.equalsIgnoreCase(mc.player.getName().getString());
	}

	public static void onMessage(String plain) {
		if (!Config.TRACK_KILLS.b() || !WhitelistGate.allowed()) return;
		String killer = null;
		String victim = null;
		Matcher m = WAS_BY.matcher(plain);
		if (m.find()) {
			victim = m.group(1);
			killer = m.group(3);
		} else {
			m = KILLED.matcher(plain);
			if (m.find()) {
				killer = m.group(1);
				victim = m.group(2);
			}
		}
		if (killer == null || victim == null || killer.equalsIgnoreCase(victim)) return;

		long now = System.currentTimeMillis();
		String key = (killer + ">" + victim).toLowerCase(Locale.ROOT);
		if (key.equals(lastKey) && now - lastKeyTime < 500) return;
		lastKey = key;
		lastKeyTime = now;

		synchronized (EVENTS) {
			EVENTS.add(new Kill(now, killer, victim));
			int max = Config.MAX_EVENTS.i();
			while (EVENTS.size() > max) EVENTS.remove(0);
		}

		if (isMe(victim)) {
			killerName = killer;
			killerUntil = now + Config.KILLER_SECONDS.i() * 1000L;
			if (Config.DEATH_NOTICE.b()) {
				Stats vs = stats(Msg.me(), List.of(killer), false);
				Msg.say("Killed by " + killer + "  (you vs them: " + vs.kills() + "-" + vs.deaths() + ", K/D " + String.format("%.2f", vs.kd()) + ")");
			}
		}
	}

	/**
	 * Kills/deaths of {@code player}. If {@code opponents} is empty it counts against everyone,
	 * otherwise only fights with those players.
	 */
	public static Stats stats(String player, Collection<String> opponents, boolean sessionOnly) {
		List<String> opp = new ArrayList<>();
		for (String o : opponents) opp.add(o.toLowerCase(Locale.ROOT));
		List<Double> series = new ArrayList<>();
		int kills = 0;
		int deaths = 0;
		List<Kill> copy;
		synchronized (EVENTS) {
			copy = new ArrayList<>(EVENTS);
		}
		for (Kill k : copy) {
			if (sessionOnly && k.time() < SESSION_START) continue;
			boolean hit = false;
			if (k.killer().equalsIgnoreCase(player) && (opp.isEmpty() || opp.contains(k.victim().toLowerCase(Locale.ROOT)))) {
				kills++;
				hit = true;
			} else if (k.victim().equalsIgnoreCase(player) && (opp.isEmpty() || opp.contains(k.killer().toLowerCase(Locale.ROOT)))) {
				deaths++;
				hit = true;
			}
			if (hit) series.add(deaths == 0 ? kills : (double) kills / deaths);
		}
		double[] arr = new double[series.size()];
		for (int i = 0; i < arr.length; i++) arr[i] = series.get(i);
		return new Stats(kills, deaths, arr);
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("ragemod").resolve("kills.json");
	}

	public static void load() {
		try {
			Path f = file();
			if (!Files.exists(f)) return;
			JsonArray arr = JsonParser.parseString(Files.readString(f)).getAsJsonArray();
			synchronized (EVENTS) {
				EVENTS.clear();
				for (JsonElement el : arr) {
					JsonObject o = el.getAsJsonObject();
					EVENTS.add(new Kill(o.get("t").getAsLong(), o.get("k").getAsString(), o.get("v").getAsString()));
				}
			}
		} catch (Exception ex) {
			System.err.println("[RAGE] Could not read kills: " + ex);
		}
	}

	public static void save() {
		try {
			JsonArray arr = new JsonArray();
			synchronized (EVENTS) {
				for (Kill k : EVENTS) {
					JsonObject o = new JsonObject();
					o.addProperty("t", k.time());
					o.addProperty("k", k.killer());
					o.addProperty("v", k.victim());
					arr.add(o);
				}
			}
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.writeString(f, arr.toString());
		} catch (Exception ex) {
			System.err.println("[RAGE] Could not save kills: " + ex);
		}
	}
}
