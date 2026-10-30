package com.ragemod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Records what players say, with the time, for the Player Info tab. */
public final class ChatLog {
	public record Line(long time, String text) {}

	private static final class Log {
		String display;
		final List<Line> lines = new ArrayList<>();
	}

	private static final Map<String, Log> LOGS = new LinkedHashMap<>();
	private static final Pattern NAME_TOKEN = Pattern.compile("[A-Za-z0-9_]{3,16}");
	private static String lastKey = "";
	private static long lastTime;

	private ChatLog() {}

	private static int firstSeparator(String s) {
		int best = -1;
		for (char c : new char[] {':', '\u00BB', '>'}) {
			int i = s.indexOf(c);
			if (i >= 0 && (best < 0 || i < best)) best = i;
		}
		return best;
	}

	private static boolean isKnown(String name) {
		return Lobby.contains(name) || NameStore.knows(name) || StatusStore.status(name) != Status.NONE;
	}

	/** @param signedSender the sender from a signed chat message, or null for plain server messages */
	public static void onMessage(String plain, String signedSender) {
		if (!Config.CHAT_LOG.b() || !WhitelistGate.allowed()) return;
		String speaker;
		String text;
		if (signedSender != null) {
			speaker = signedSender;
			text = plain;
		} else {
			int sep = firstSeparator(plain);
			if (sep <= 0 || sep > 80) return;
			if (plain.charAt(sep - 1) == '-') return; // "[Name -> me]" private message format
			String prefix = plain.substring(0, sep);
			Matcher m = NAME_TOKEN.matcher(prefix);
			String last = null;
			while (m.find()) last = m.group();
			if (last == null) return;
			speaker = last;
			text = plain.substring(sep + 1).trim();
			if (text.isEmpty() || !isKnown(speaker)) return;
		}
		long now = System.currentTimeMillis();
		String key = speaker.toLowerCase(Locale.ROOT) + "|" + text;
		if (key.equals(lastKey) && now - lastTime < 400) return;
		lastKey = key;
		lastTime = now;

		synchronized (LOGS) {
			String lower = speaker.toLowerCase(Locale.ROOT);
			Log log = LOGS.remove(lower); // re-insert at the end so the most recent speakers stay
			if (log == null) log = new Log();
			log.display = speaker;
			log.lines.add(new Line(now, text));
			int max = Config.CHAT_LINES.i();
			while (log.lines.size() > max) log.lines.remove(0);
			LOGS.put(lower, log);
			while (LOGS.size() > 400) {
				String oldest = LOGS.keySet().iterator().next();
				LOGS.remove(oldest);
			}
		}
	}

	public static List<Line> lines(String name) {
		synchronized (LOGS) {
			Log l = LOGS.get(name.toLowerCase(Locale.ROOT));
			return l == null ? new ArrayList<>() : new ArrayList<>(l.lines);
		}
	}

	/** Names that have something logged. */
	public static List<String> names() {
		List<String> out = new ArrayList<>();
		synchronized (LOGS) {
			for (Log l : LOGS.values()) out.add(l.display);
		}
		return out;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("ragemod").resolve("chatlogs.json");
	}

	public static void load() {
		try {
			Path f = file();
			if (!Files.exists(f)) return;
			JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
			synchronized (LOGS) {
				LOGS.clear();
				for (var entry : root.entrySet()) {
					JsonObject o = entry.getValue().getAsJsonObject();
					Log log = new Log();
					log.display = o.get("d").getAsString();
					for (JsonElement el : o.getAsJsonArray("l")) {
						JsonArray a = el.getAsJsonArray();
						log.lines.add(new Line(a.get(0).getAsLong(), a.get(1).getAsString()));
					}
					LOGS.put(entry.getKey(), log);
				}
			}
		} catch (Exception ex) {
			System.err.println("[RAGE] Could not read chat logs: " + ex);
		}
	}

	public static void save() {
		try {
			JsonObject root = new JsonObject();
			synchronized (LOGS) {
				for (var entry : LOGS.entrySet()) {
					JsonObject o = new JsonObject();
					o.addProperty("d", entry.getValue().display);
					JsonArray arr = new JsonArray();
					for (Line l : entry.getValue().lines) {
						JsonArray a = new JsonArray();
						a.add(l.time());
						a.add(l.text());
						arr.add(a);
					}
					o.add("l", arr);
					root.add(entry.getKey(), o);
				}
			}
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.writeString(f, root.toString());
		} catch (Exception ex) {
			System.err.println("[RAGE] Could not save chat logs: " + ex);
		}
	}
}
