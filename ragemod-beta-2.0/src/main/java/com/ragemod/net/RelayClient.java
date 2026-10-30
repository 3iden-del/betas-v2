package com.ragemod.net;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.ragemod.core.Config;
import com.ragemod.core.Msg;
import com.ragemod.core.Status;
import com.ragemod.core.StatusStore;
import com.ragemod.core.War;
import com.ragemod.core.WhitelistGate;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Talks to the RAGE relay server (relay/server.js): access check, mod users online, private messages,
 * help requests, war sync and the admin (Dev) tools. Messages travel through the relay, you never see a website.
 */
public final class RelayClient {
	/** Built-in relay address, so nobody has to type anything. */
	private static final String DEFAULT_RELAY = "wss://rage-relay.onrender.com";

	public record Peer(String name, String uuid, String server) {}
	public record AdminUser(String name, String uuid, String server, String ip, long since, boolean allowed) {}
	public record AdminPending(String name, String uuid, String ip, long at) {}
	public record AdminMember(String name, String role) {}
	public record AdminData(List<AdminUser> users, List<AdminPending> pending, List<AdminMember> allowed) {}

	private static final HttpClient HTTP = HttpClient.newHttpClient();
	private static final Map<String, List<String>> CONVOS = new ConcurrentHashMap<>();
	private static final Set<String> UNREAD = ConcurrentHashMap.newKeySet();
	private static final StringBuilder BUF = new StringBuilder();

	private static volatile WebSocket socket;
	private static volatile boolean connecting;
	private static volatile boolean connected;
	private static volatile List<Peer> peers = new ArrayList<>();
	private static volatile String status = "Offline";
	private static volatile boolean adminOk;
	private static volatile String adminStatus = "";
	private static volatile AdminData adminData = new AdminData(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
	private static long nextTry;
	private static long lastPing;

	private RelayClient() {}

	public static boolean connected() { return connected; }
	public static List<Peer> peers() { return peers; }
	public static String status() { return status; }
	public static boolean adminOk() { return adminOk; }
	public static String adminStatus() { return adminStatus; }
	public static AdminData adminData() { return adminData; }

	public static List<String> convo(String name) {
		return CONVOS.computeIfAbsent(name.toLowerCase(Locale.ROOT), k -> new CopyOnWriteArrayList<>());
	}

	public static boolean unread(String name) { return UNREAD.contains(name.toLowerCase(Locale.ROOT)); }
	public static void markRead(String name) { UNREAD.remove(name.toLowerCase(Locale.ROOT)); }
	public static boolean anyUnread() { return !UNREAD.isEmpty(); }

	public static boolean isOnline(String name) {
		for (Peer p : peers) {
			if (p.name().equalsIgnoreCase(name)) return true;
		}
		return false;
	}

	private static String url() {
		String u = Config.RELAY_URL.s();
		if (u == null || u.isBlank()) u = DEFAULT_RELAY;
		return u.trim();
	}

	/** Called every client tick on the main thread. Handles connect / reconnect / keep-alive. */
	public static void tick() {
		long now = System.currentTimeMillis();
		if (connected && now - lastPing > 30_000) {
			lastPing = now;
			JsonObject o = new JsonObject();
			o.addProperty("t", "ping");
			send(o);
		}
		if (!connected && !connecting && now >= nextTry) connect();
	}

	private static void connect() {
		String u = url();
		connecting = true;
		status = "Connecting...";
		try {
			HTTP.newWebSocketBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.buildAsync(URI.create(u), new Listener())
				.whenComplete((ws, ex) -> {
					connecting = false;
					if (ex != null) {
						connected = false;
						status = "Offline (relay not reachable yet)";
						nextTry = System.currentTimeMillis() + 15_000;
					} else {
						socket = ws;
						connected = true;
						status = "Online";
						sendHello();
					}
				});
		} catch (Exception ex) {
			connecting = false;
			status = "Offline (bad relay URL)";
			nextTry = System.currentTimeMillis() + 60_000;
		}
	}

	public static void disconnect() {
		WebSocket w = socket;
		socket = null;
		connected = false;
		connecting = false;
		peers = new ArrayList<>();
		adminOk = false;
		if (w != null) {
			try {
				w.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
			} catch (Exception ignored) {
				// already closed
			}
		}
	}

	private static void sendHello() {
		UUID id = null;
		try {
			id = Minecraft.getInstance().getUser().getProfileId();
		} catch (Throwable ignored) {
			// no profile yet
		}
		JsonObject o = new JsonObject();
		o.addProperty("t", "hello");
		o.addProperty("uuid", id == null ? "" : id.toString());
		o.addProperty("name", Msg.me());
		o.addProperty("server", Msg.server());
		o.addProperty("v", "2.0");
		send(o);
	}

	/** Tell the relay which server you're on now. */
	public static void updateServer() {
		if (connected) sendHello();
	}

	private static void send(JsonObject o) {
		WebSocket w = socket;
		if (w == null || !connected) return;
		try {
			w.sendText(o.toString(), true);
		} catch (Exception ignored) {
			// connection dropped, tick() reconnects
		}
	}

	// ---- chat / help / war ----

	public static void sendPm(String to, String text) {
		if (text.isBlank() || !WhitelistGate.allowed()) return;
		JsonObject o = new JsonObject();
		o.addProperty("t", "pm");
		o.addProperty("to", to);
		o.addProperty("msg", text);
		send(o);
		convo(to).add("You: " + text);
	}

	public static void sendHelp(String text) {
		if (!WhitelistGate.allowed()) return;
		JsonObject o = new JsonObject();
		o.addProperty("t", "help");
		o.addProperty("msg", text);
		o.addProperty("server", Msg.server());
		send(o);
	}

	public static void sendWar(String action) {
		if (!WhitelistGate.allowed()) return;
		JsonObject o = new JsonObject();
		o.addProperty("t", "war");
		o.addProperty("action", action);
		o.addProperty("server", Msg.server());
		JsonArray arr = new JsonArray();
		for (String n : StatusStore.namesWith(Status.ENEMY)) arr.add(n);
		o.add("enemies", arr);
		send(o);
	}

	public static void sendPriority(String name) {
		if (!WhitelistGate.allowed()) return;
		JsonObject o = new JsonObject();
		o.addProperty("t", "war");
		o.addProperty("action", "priority");
		o.addProperty("name", name == null ? "" : name);
		o.addProperty("server", Msg.server());
		send(o);
	}

	// ---- admin (Dev tab) ----

	public static void auth(String password) {
		adminStatus = "Checking...";
		JsonObject o = new JsonObject();
		o.addProperty("t", "auth");
		o.addProperty("password", password);
		send(o);
		if (!connected) adminStatus = "Not connected to the relay yet, wait a moment and try again.";
	}

	public static void adminList() {
		if (!adminOk) return;
		JsonObject o = new JsonObject();
		o.addProperty("t", "a_list");
		send(o);
	}

	private static void adminName(String type, String name) {
		if (!adminOk) return;
		JsonObject o = new JsonObject();
		o.addProperty("t", type);
		o.addProperty("name", name);
		send(o);
	}

	public static void adminAllow(String name) { adminName("a_allow", name); }
	public static void adminRemove(String name) { adminName("a_remove", name); }
	public static void adminKick(String name) { adminName("a_kick", name); }

	// ---- incoming ----

	private static void ping(boolean urgent) {
		if (!Config.SOUNDS.b()) return;
		Minecraft mc = Minecraft.getInstance();
		mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), urgent ? 2.0f : 1.5f));
	}

	private static String str(JsonObject o, String key, String def) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : def;
	}

	/** One-time notice that the relay can see name, server and IP. */
	private static void showNoticeOnce() {
		try {
			Path f = FabricLoader.getInstance().getConfigDir().resolve("ragemod").resolve("notice_ack");
			if (Files.exists(f)) return;
			Files.createDirectories(f.getParent());
			Files.writeString(f, "1");
			Msg.say("Heads up: the RAGE relay can see your Minecraft name, the server you're on and your IP address (admins only).");
		} catch (Exception ignored) {
			// not critical
		}
	}

	private static void handle(JsonObject o) {
		String t = str(o, "t", "");
		switch (t) {
			case "welcome" -> {
				boolean ok = o.has("allowed") && o.get("allowed").getAsBoolean();
				WhitelistGate.setFromRelay(ok, str(o, "reason", ""));
				List<WhitelistGate.Member> roster = new ArrayList<>();
				if (o.has("roster")) {
					for (JsonElement e : o.getAsJsonArray("roster")) {
						JsonObject m = e.getAsJsonObject();
						roster.add(new WhitelistGate.Member(str(m, "name", "?"), str(m, "role", "member")));
					}
				}
				WhitelistGate.setRoster(str(o, "clan", "RAGE"), roster);
				if (ok) showNoticeOnce();
			}
			case "users" -> {
				List<Peer> list = new ArrayList<>();
				UUID me = null;
				try {
					me = Minecraft.getInstance().getUser().getProfileId();
				} catch (Throwable ignored) {
					// no profile
				}
				Set<String> before = new HashSet<>();
				for (Peer p : peers) before.add(p.name().toLowerCase(Locale.ROOT));
				for (JsonElement e : o.getAsJsonArray("list")) {
					JsonObject u = e.getAsJsonObject();
					String uuid = str(u, "uuid", "");
					if (me != null && uuid.equalsIgnoreCase(me.toString())) continue;
					if (str(u, "name", "").equalsIgnoreCase(Msg.me())) continue;
					list.add(new Peer(str(u, "name", "?"), uuid, str(u, "server", "?")));
				}
				boolean first = before.isEmpty();
				peers = list;
				if (Config.JOIN_NOTICES.b() && !first) {
					for (Peer p : list) {
						if (!before.contains(p.name().toLowerCase(Locale.ROOT))) Msg.say(p.name() + " is now online with the mod.");
					}
				}
			}
			case "pm" -> {
				String from = str(o, "from", "?");
				String msg = str(o, "msg", "");
				convo(from).add(from + ": " + msg);
				UNREAD.add(from.toLowerCase(Locale.ROOT));
				if (Config.PM_IN_CHAT.b()) Msg.say("[PM] " + from + ": " + msg, 0xFF55FF);
				ping(false);
			}
			case "help" -> {
				String from = str(o, "from", "?");
				String msg = str(o, "msg", "");
				String srv = str(o, "server", "?");
				Msg.say(from + " NEEDS HELP on " + srv + (msg.isBlank() ? "" : ": " + msg), 0xFF2A2A);
				ping(true);
			}
			case "war" -> {
				String action = str(o, "action", "");
				String from = str(o, "from", "?");
				switch (action) {
					case "start" -> {
						List<String> names = new ArrayList<>();
						if (o.has("enemies")) {
							for (JsonElement e : o.getAsJsonArray("enemies")) names.add(e.getAsString());
						}
						War.onRemoteStart(from, str(o, "server", "?"), names);
						ping(true);
					}
					case "end" -> War.onRemoteEnd(from);
					case "join" -> Msg.say(from + " joined the war.");
					case "priority" -> {
						War.onRemotePriority(from, str(o, "name", ""));
						ping(true);
					}
					default -> { }
				}
			}
			case "admin" -> {
				adminOk = o.has("ok") && o.get("ok").getAsBoolean();
				adminStatus = adminOk ? "Logged in." : str(o, "reason", "Wrong password.");
				if (adminOk) adminList();
			}
			case "a_data" -> {
				List<AdminUser> users = new ArrayList<>();
				List<AdminPending> pending = new ArrayList<>();
				List<AdminMember> allowed = new ArrayList<>();
				if (o.has("users")) {
					for (JsonElement e : o.getAsJsonArray("users")) {
						JsonObject u = e.getAsJsonObject();
						users.add(new AdminUser(str(u, "name", "?"), str(u, "uuid", ""), str(u, "server", "?"), str(u, "ip", "?"),
							u.has("since") ? u.get("since").getAsLong() : 0L, u.has("allowed") && u.get("allowed").getAsBoolean()));
					}
				}
				if (o.has("pending")) {
					for (JsonElement e : o.getAsJsonArray("pending")) {
						JsonObject u = e.getAsJsonObject();
						pending.add(new AdminPending(str(u, "name", "?"), str(u, "uuid", ""), str(u, "ip", "?"), u.has("at") ? u.get("at").getAsLong() : 0L));
					}
				}
				if (o.has("allowed")) {
					for (JsonElement e : o.getAsJsonArray("allowed")) {
						JsonObject u = e.getAsJsonObject();
						allowed.add(new AdminMember(str(u, "name", "?"), str(u, "role", "member")));
					}
				}
				adminData = new AdminData(users, pending, allowed);
			}
			case "deny" -> {
				status = "Denied: " + str(o, "reason", "not allowed");
				nextTry = System.currentTimeMillis() + 120_000;
				disconnect();
			}
			default -> { }
		}
	}

	private static final class Listener implements WebSocket.Listener {
		@Override
		public void onOpen(WebSocket webSocket) {
			webSocket.request(1);
		}

		@Override
		public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
			BUF.append(data);
			if (last) {
				String text = BUF.toString();
				BUF.setLength(0);
				try {
					JsonObject o = JsonParser.parseString(text).getAsJsonObject();
					Minecraft.getInstance().execute(() -> {
						try {
							handle(o);
						} catch (Exception ex) {
							System.err.println("[RAGE] relay message error: " + ex);
						}
					});
				} catch (Exception ex) {
					System.err.println("[RAGE] bad relay message: " + ex);
				}
			}
			webSocket.request(1);
			return null;
		}

		@Override
		public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
			connected = false;
			socket = null;
			peers = new ArrayList<>();
			adminOk = false;
			status = "Offline";
			nextTry = System.currentTimeMillis() + 10_000;
			return null;
		}

		@Override
		public void onError(WebSocket webSocket, Throwable error) {
			connected = false;
			socket = null;
			peers = new ArrayList<>();
			adminOk = false;
			status = "Offline (error)";
			nextTry = System.currentTimeMillis() + 15_000;
		}
	}
}
