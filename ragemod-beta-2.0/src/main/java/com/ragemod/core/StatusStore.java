package com.ragemod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Saved player marks (target / friend / annoyance / enemy) plus ignore and show flags. */
public final class StatusStore {
	public static final class Entry {
		public String name;
		public Status status = Status.NONE;
		public boolean ignored;
		public boolean show;

		Entry(String name) {
			this.name = name;
		}

		boolean isEmpty() {
			return status == Status.NONE && !ignored && !show;
		}
	}

	private static final Map<String, Entry> MAP = new LinkedHashMap<>();

	private StatusStore() {}

	private static String key(String name) {
		return name.toLowerCase(Locale.ROOT);
	}

	public static synchronized Status status(String name) {
		Entry e = MAP.get(key(name));
		return e == null ? Status.NONE : e.status;
	}

	public static synchronized boolean ignored(String name) {
		Entry e = MAP.get(key(name));
		return e != null && e.ignored;
	}

	public static synchronized boolean show(String name) {
		Entry e = MAP.get(key(name));
		return e != null && e.show;
	}

	private static Entry entry(String name) {
		return MAP.computeIfAbsent(key(name), k -> new Entry(name));
	}

	private static void prune(String name) {
		Entry e = MAP.get(key(name));
		if (e != null && e.isEmpty()) MAP.remove(key(name));
	}

	/** Sets the status, or clears it if the player already has that status. */
	public static synchronized void toggle(String name, Status s) {
		Entry e = entry(name);
		e.name = name;
		e.status = (e.status == s) ? Status.NONE : s;
		prune(name);
		save();
	}

	public static synchronized void set(String name, Status s) {
		Entry e = entry(name);
		e.name = name;
		e.status = s;
		prune(name);
		save();
	}

	public static synchronized void toggleIgnore(String name) {
		Entry e = entry(name);
		e.name = name;
		e.ignored = !e.ignored;
		prune(name);
		save();
	}

	public static synchronized void toggleShow(String name) {
		Entry e = entry(name);
		e.name = name;
		e.show = !e.show;
		prune(name);
		save();
	}

	public static synchronized void clear(String name) {
		MAP.remove(key(name));
		save();
	}

	public static synchronized List<Entry> all() {
		return new ArrayList<>(MAP.values());
	}

	public static synchronized List<String> namesWith(Status s) {
		List<String> out = new ArrayList<>();
		for (Entry e : MAP.values()) {
			if (e.status == s) out.add(e.name);
		}
		return out;
	}

	public static synchronized int count(Status s) {
		int n = 0;
		for (Entry e : MAP.values()) {
			if (e.status == s) n++;
		}
		return n;
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("ragemod").resolve("statuses.json");
	}

	public static synchronized void load() {
		try {
			Path f = file();
			if (!Files.exists(f)) return;
			JsonArray arr = JsonParser.parseString(Files.readString(f)).getAsJsonArray();
			MAP.clear();
			for (JsonElement el : arr) {
				JsonObject o = el.getAsJsonObject();
				String name = o.get("name").getAsString();
				Entry e = new Entry(name);
				try {
					e.status = Status.valueOf(o.get("status").getAsString());
				} catch (Exception ignored) {
					e.status = Status.NONE;
				}
				e.ignored = o.has("ignored") && o.get("ignored").getAsBoolean();
				e.show = o.has("show") && o.get("show").getAsBoolean();
				if (!e.isEmpty()) MAP.put(key(name), e);
			}
		} catch (Exception ex) {
			System.err.println("[RAGE] Could not read statuses: " + ex);
		}
	}

	public static synchronized void save() {
		try {
			JsonArray arr = new JsonArray();
			for (Entry e : MAP.values()) {
				JsonObject o = new JsonObject();
				o.addProperty("name", e.name);
				o.addProperty("status", e.status.name());
				o.addProperty("ignored", e.ignored);
				o.addProperty("show", e.show);
				arr.add(o);
			}
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.writeString(f, new GsonBuilder().setPrettyPrinting().create().toJson(arr));
		} catch (Exception ex) {
			System.err.println("[RAGE] Could not save statuses: " + ex);
		}
	}
}
