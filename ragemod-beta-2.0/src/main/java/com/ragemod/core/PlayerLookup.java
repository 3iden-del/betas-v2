package com.ragemod.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Online lookups for the Player Info tab: UUID, skin file and name history.
 * Uses Mojang's public API for UUID + skin and laby.net for name history (Mojang removed its own in 2022).
 * Your own locally recorded names (NameStore) are always merged in.
 */
public final class PlayerLookup {
	public static final class Profile {
		public final String name;
		public volatile String uuid = "";
		public volatile String skinUrl = "";
		public volatile boolean slim;
		public volatile boolean loading = true;
		public volatile String error = "";
		/** each entry: {name, date or note} */
		public volatile List<String[]> history = new ArrayList<>();

		Profile(String name) {
			this.name = name;
		}
	}

	private static final Map<String, Profile> CACHE = new ConcurrentHashMap<>();
	private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

	private PlayerLookup() {}

	/** Returns the cached profile, starting a background lookup the first time. Call from the main thread. */
	public static Profile get(String name) {
		String key = name.toLowerCase(Locale.ROOT);
		Profile existing = CACHE.get(key);
		if (existing != null) return existing;
		Profile p = new Profile(name);
		CACHE.put(key, p);
		UUID tab = Lobby.uuidOf(name);
		Thread t = new Thread(() -> fetch(p, tab), "rage-lookup");
		t.setDaemon(true);
		t.start();
		return p;
	}

	public static void refresh(String name) {
		CACHE.remove(name.toLowerCase(Locale.ROOT));
		get(name);
	}

	private static String strip(String uuid) {
		return uuid.replace("-", "");
	}

	private static JsonObject getJson(String url) throws Exception {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(10))
			.header("User-Agent", "RageMod/2.0")
			.GET()
			.build();
		HttpResponse<String> res = HTTP.send(req, HttpResponse.BodyHandlers.ofString());
		if (res.statusCode() == 429) throw new RuntimeException("rate limited, try again in a minute");
		if (res.statusCode() != 200 || res.body() == null || res.body().isBlank()) return null;
		JsonElement e = JsonParser.parseString(res.body());
		return e.isJsonObject() ? e.getAsJsonObject() : null;
	}

	private static JsonObject sessionProfile(String uuidNoDashes) throws Exception {
		return getJson("https://sessionserver.mojang.com/session/minecraft/profile/" + uuidNoDashes);
	}

	private static void fetch(Profile p, UUID tabUuid) {
		try {
			if (!Config.LOOKUPS.b()) {
				if (tabUuid != null) p.uuid = tabUuid.toString();
				mergeLocal(p, new ArrayList<>());
				p.error = "Online lookups are turned off in the config (lookupNameHistory).";
				return;
			}
			JsonObject prof = null;
			if (tabUuid != null) prof = sessionProfile(strip(tabUuid.toString()));
			if (prof == null) {
				JsonObject m = getJson("https://api.mojang.com/users/profiles/minecraft/" + URLEncoder.encode(p.name, StandardCharsets.UTF_8));
				if (m != null && m.has("id")) prof = sessionProfile(m.get("id").getAsString());
			}
			List<String[]> hist = new ArrayList<>();
			if (prof == null) {
				if (tabUuid != null) p.uuid = tabUuid.toString();
				p.error = "No Mojang account found for this name (it may be a nick).";
				mergeLocal(p, hist);
				return;
			}
			String id = prof.get("id").getAsString();
			p.uuid = dashed(id);
			parseSkin(p, prof);

			try {
				JsonObject laby = getJson("https://laby.net/api/v3/user/" + id + "/profile");
				if (laby != null && laby.has("name_history") && laby.get("name_history").isJsonArray()) {
					JsonArray arr = laby.getAsJsonArray("name_history");
					for (JsonElement el : arr) {
						if (!el.isJsonObject()) continue;
						JsonObject o = el.getAsJsonObject();
						if (!o.has("name")) continue;
						String date = "original";
						if (o.has("changed_at") && !o.get("changed_at").isJsonNull()) {
							String s = o.get("changed_at").getAsString();
							date = s.length() >= 10 ? s.substring(0, 10) : s;
						}
						hist.add(new String[] {o.get("name").getAsString(), date});
					}
					// newest first
					hist.sort((a, b) -> {
						if (a[1].equals("original")) return 1;
						if (b[1].equals("original")) return -1;
						return b[1].compareTo(a[1]);
					});
				} else {
					p.error = "Name history service unavailable (showing names you've seen).";
				}
			} catch (Exception ex) {
				p.error = "Name history service unavailable (showing names you've seen).";
			}
			mergeLocal(p, hist);
		} catch (Exception ex) {
			p.error = "Lookup failed: " + ex.getMessage();
			mergeLocal(p, new ArrayList<>());
		} finally {
			p.loading = false;
		}
	}

	private static void mergeLocal(Profile p, List<String[]> hist) {
		if (!p.uuid.isEmpty()) {
			for (NameStore.Seen s : NameStore.history(p.uuid)) {
				boolean have = false;
				for (String[] h : hist) {
					if (h[0].equalsIgnoreCase(s.name())) {
						have = true;
						break;
					}
				}
				if (!have) hist.add(new String[] {s.name(), "seen by you"});
			}
		}
		p.history = hist;
		p.loading = false;
	}

	private static void parseSkin(Profile p, JsonObject prof) {
		try {
			if (!prof.has("properties")) return;
			for (JsonElement el : prof.getAsJsonArray("properties")) {
				JsonObject o = el.getAsJsonObject();
				if (!o.has("name") || !o.get("name").getAsString().equals("textures")) continue;
				String json = new String(Base64.getDecoder().decode(o.get("value").getAsString()), StandardCharsets.UTF_8);
				JsonObject tex = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
				if (tex != null && tex.has("SKIN")) {
					JsonObject skin = tex.getAsJsonObject("SKIN");
					p.skinUrl = skin.get("url").getAsString();
					p.slim = skin.has("metadata") && skin.getAsJsonObject("metadata").has("model")
						&& skin.getAsJsonObject("metadata").get("model").getAsString().equals("slim");
				}
			}
		} catch (Exception ignored) {
			// no skin info, the card still works from the in-game skin
		}
	}

	private static String dashed(String id) {
		if (id.length() != 32) return id;
		return id.substring(0, 8) + "-" + id.substring(8, 12) + "-" + id.substring(12, 16) + "-" + id.substring(16, 20) + "-" + id.substring(20);
	}

	/** Downloads the player's skin PNG into config/ragemod/skins and reports back in chat. */
	public static void saveSkin(String name) {
		Profile p = get(name);
		Thread t = new Thread(() -> {
			String msg;
			try {
				if (p.loading) {
					msg = "Still looking up " + name + ", try again in a second.";
				} else if (p.skinUrl.isEmpty()) {
					msg = "No skin found for " + name + (p.error.isEmpty() ? "." : " (" + p.error + ")");
				} else {
					HttpRequest req = HttpRequest.newBuilder(URI.create(p.skinUrl))
						.timeout(Duration.ofSeconds(10))
						.header("User-Agent", "RageMod/2.0")
						.GET()
						.build();
					HttpResponse<byte[]> res = HTTP.send(req, HttpResponse.BodyHandlers.ofByteArray());
					if (res.statusCode() != 200) {
						msg = "Could not download the skin (HTTP " + res.statusCode() + ").";
					} else {
						String safe = name.replaceAll("[^A-Za-z0-9_\\-]", "_");
						Path dir = FabricLoader.getInstance().getConfigDir().resolve("ragemod").resolve("skins");
						Files.createDirectories(dir);
						Path out = dir.resolve(safe + ".png");
						Files.write(out, res.body());
						msg = "Saved skin: " + out.toAbsolutePath();
					}
				}
			} catch (Exception ex) {
				msg = "Skin download failed: " + ex.getMessage();
			}
			final String finalMsg = msg;
			Minecraft.getInstance().execute(() -> Msg.say(finalMsg));
		}, "rage-skin");
		t.setDaemon(true);
		t.start();
	}
}
