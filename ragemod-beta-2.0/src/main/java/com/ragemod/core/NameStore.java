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

/** Remembers which names each UUID has used while you were around them (your own local name history). */
public final class NameStore {
	public record Seen(String name, long firstSeen) {}

	private static final Map<String, List<Seen>> HISTORY = new LinkedHashMap<>(); // uuid -> names
	private static final Map<String, String> BY_NAME = new LinkedHashMap<>();    // lower name -> display name
	private static boolean dirty;

	private NameStore() {}

	public static synchronized void observe(String uuid, String name) {
		if (uuid == null || name == null || name.isEmpty()) return;
		String lower = name.toLowerCase(Locale.ROOT);
		if (!BY_NAME.containsKey(lower)) {
			BY_NAME.put(lower, name);
			dirty = true;
			while (BY_NAME.size() > 3000) {
				String first = BY_NAME.keySet().iterator().next();
				BY_NAME.remove(first);
			}
		}
		List<Seen> list = HISTORY.computeIfAbsent(uuid.toLowerCase(Locale.ROOT), k -> new ArrayList<>());
		for (Seen s : list) {
			if (s.name().equalsIgnoreCase(name)) return;
		}
		list.add(new Seen(name, System.currentTimeMillis()));
		dirty = true;
	}

	public static synchronized List<Seen> history(String uuid) {
		List<Seen> l = HISTORY.get(uuid.toLowerCase(Locale.ROOT));
		return l == null ? new ArrayList<>() : new ArrayList<>(l);
	}

	public static synchronized boolean knows(String name) {
		return BY_NAME.containsKey(name.toLowerCase(Locale.ROOT));
	}

	public static synchronized List<String> knownNames() {
		return new ArrayList<>(BY_NAME.values());
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("ragemod").resolve("names.json");
	}

	public static synchronized void load() {
		try {
			Path f = file();
			if (!Files.exists(f)) return;
			JsonObject root = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
			HISTORY.clear();
			BY_NAME.clear();
			for (var entry : root.entrySet()) {
				List<Seen> list = new ArrayList<>();
				for (JsonElement el : entry.getValue().getAsJsonArray()) {
					JsonObject o = el.getAsJsonObject();
					Seen s = new Seen(o.get("n").getAsString(), o.get("t").getAsLong());
					list.add(s);
					BY_NAME.put(s.name().toLowerCase(Locale.ROOT), s.name());
				}
				HISTORY.put(entry.getKey(), list);
			}
		} catch (Exception ex) {
			System.err.println("[RAGE] Could not read names: " + ex);
		}
	}

	public static synchronized void save() {
		if (!dirty) return;
		try {
			JsonObject root = new JsonObject();
			for (var entry : HISTORY.entrySet()) {
				JsonArray arr = new JsonArray();
				for (Seen s : entry.getValue()) {
					JsonObject o = new JsonObject();
					o.addProperty("n", s.name());
					o.addProperty("t", s.firstSeen());
					arr.add(o);
				}
				root.add(entry.getKey(), arr);
			}
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.writeString(f, root.toString());
			dirty = false;
		} catch (Exception ex) {
			System.err.println("[RAGE] Could not save names: " + ex);
		}
	}
}
