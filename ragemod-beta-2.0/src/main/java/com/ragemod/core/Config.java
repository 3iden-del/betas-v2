package com.ragemod.core;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** All configurable options. Saved to config/ragemod/config.json. */
public final class Config {
	public static final List<Opt> ALL = new ArrayList<>();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private static Opt bool(String g, String k, boolean d, String desc) {
		Opt o = new Opt(g, k, desc, Opt.Kind.BOOL, d, 0, 1, 1);
		ALL.add(o);
		return o;
	}

	private static Opt color(String g, String k, String d, String desc) {
		Opt o = new Opt(g, k, desc, Opt.Kind.COLOR, d, 0, 0, 0);
		ALL.add(o);
		return o;
	}

	private static Opt num(String g, String k, int d, int min, int max, int step, String desc) {
		Opt o = new Opt(g, k, desc, Opt.Kind.INT, d, min, max, step);
		ALL.add(o);
		return o;
	}

	private static Opt text(String g, String k, String d, String desc) {
		Opt o = new Opt(g, k, desc, Opt.Kind.STRING, d, 0, 0, 0);
		ALL.add(o);
		return o;
	}

	// ---- Group colors ----
	public static final Opt TARGET_COLOR = color("Colors", "targetColor", "red", "Color used for players marked as targets");
	public static final Opt FRIEND_COLOR = color("Colors", "friendColor", "green", "Color used for players marked as friends");
	public static final Opt ALLIANCE_COLOR = color("Colors", "allianceColor", "light_purple", "Color used for players marked as alliances");
	public static final Opt MUTUAL_COLOR = color("Colors", "mutualColor", "cyan", "Color used for players marked as mutuals");
	public static final Opt ANNOYANCE_COLOR = color("Colors", "annoyanceColor", "gold", "Color used for players marked as annoyances");
	public static final Opt ENEMY_COLOR = color("Colors", "enemyColor", "dark_red", "Color used for players marked as war enemies");
	public static final Opt PRIORITY_COLOR = color("Colors", "priorityColor", "yellow", "Color used for the war top priority target");
	public static final Opt KILLER_COLOR = color("Colors", "killerColor", "orange", "Color used for the player who last killed you");
	public static final Opt HIGHLIGHT_COLOR = color("Colors", "highlightColor", "white", "Color used for highlighted (Show) players");

	// ---- Coloring ----
	public static final Opt CHANGE_NICK = bool("Coloring", "changeNickColor", true, "Change nickname color in game");
	public static final Opt CHANGE_TAB = bool("Coloring", "changeTabColor", true, "Change color of nicks in tablist");
	public static final Opt CHANGE_HITBOX = bool("Coloring", "changeHitboxColor", true, "Color hitboxes by group (off = all use highlightColor)");

	// ---- Hitboxes ----
	public static final Opt HITBOXES = bool("Hitboxes", "hitboxesEnabled", true, "Master switch for colored hitboxes");
	public static final Opt HB_TARGETS = bool("Hitboxes", "hitboxTargets", true, "Draw hitboxes on targets");
	public static final Opt HB_FRIENDS = bool("Hitboxes", "hitboxFriends", true, "Draw hitboxes on friends");
	public static final Opt HB_ALLIANCES = bool("Hitboxes", "hitboxAlliances", true, "Draw hitboxes on alliances");
	public static final Opt HB_MUTUALS = bool("Hitboxes", "hitboxMutuals", true, "Draw hitboxes on mutuals");
	public static final Opt HB_ANNOYANCES = bool("Hitboxes", "hitboxAnnoyances", true, "Draw hitboxes on annoyances");
	public static final Opt HB_ENEMIES = bool("Hitboxes", "hitboxEnemies", true, "Draw hitboxes on war enemies");
	public static final Opt HB_PRIORITY = bool("Hitboxes", "hitboxPriority", true, "Draw a hitbox on the top priority target");
	public static final Opt HB_HIGHLIGHTED = bool("Hitboxes", "hitboxHighlighted", true, "Draw hitboxes on highlighted players");
	public static final Opt HB_KILLER = bool("Hitboxes", "hitboxKiller", true, "Draw a hitbox on whoever killed you");
	public static final Opt HB_THICK = num("Hitboxes", "hitboxThickness", 2, 1, 4, 1, "Hitbox line thickness");
	public static final Opt ESP_DISTANCE = num("Hitboxes", "espMaxDistance", 128, 16, 256, 16, "Max distance (blocks) for hitboxes");

	// ---- Join / leave alerts ----
	public static final Opt ALERT_TARGETS = bool("Alerts", "alertTargets", true, "Chat alert when a target joins or leaves the lobby");
	public static final Opt ALERT_ENEMIES = bool("Alerts", "alertEnemies", true, "Chat alert when a war enemy joins or leaves");
	public static final Opt ALERT_FRIENDS = bool("Alerts", "alertFriends", false, "Chat alert when a friend joins or leaves");
	public static final Opt ALERT_ALLIANCES = bool("Alerts", "alertAlliances", false, "Chat alert when an alliance joins or leaves");
	public static final Opt ALERT_MUTUALS = bool("Alerts", "alertMutuals", false, "Chat alert when a mutual joins or leaves");
	public static final Opt ALERT_PRIORITY = bool("Alerts", "alertPriority", true, "Chat alert when the top priority target joins or leaves");
	public static final Opt ALERT_SOUND = bool("Alerts", "alertSounds", true, "Play a sound with join/leave alerts");

	// ---- Killer ----
	public static final Opt HIGHLIGHT_KILLER = bool("Killer", "highlightKiller", true, "Highlight the player who killed you");
	public static final Opt KILLER_SECONDS = num("Killer", "killerHighlightSeconds", 30, 5, 120, 5, "How long the killer stays highlighted (seconds)");

	// ---- Stats ----
	public static final Opt TRACK_KILLS = bool("Stats", "trackKills", true, "Track kills and deaths from chat messages");
	public static final Opt MAX_EVENTS = num("Stats", "maxStoredEvents", 5000, 500, 20000, 500, "Max kill events stored on disk");
	public static final Opt DEATH_NOTICE = bool("Stats", "deathNotice", true, "Show your K/D vs the killer in chat when you die");

	// ---- Player info ----
	public static final Opt CHAT_LOG = bool("PlayerInfo", "chatLogEnabled", true, "Record what players say (with time) for the Player Info tab");
	public static final Opt CHAT_LINES = num("PlayerInfo", "maxChatLinesPerPlayer", 300, 50, 1000, 50, "Max chat lines kept per player");
	public static final Opt LOOKUPS = bool("PlayerInfo", "lookupNameHistory", true, "Look up name history / skin online (Mojang + laby.net) in Player Info");

	// ---- Radial ----
	public static final Opt RADIAL = bool("Radial", "radialMenuEnabled", true, "Enable the radial menu on middle click");
	public static final Opt RADIAL_SIZE = num("Radial", "radialSize", 190, 140, 260, 10, "Radial menu radius");

	// ---- Mod chat ----
	public static final Opt RELAY_URL = text("Network", "relayUrl", "", "Relay URL override (empty = built-in RAGE relay)");
	public static final Opt SOUNDS = bool("Network", "notifySounds", true, "Play a sound for help requests and private messages");
	public static final Opt PM_IN_CHAT = bool("Network", "privateMessagesInChat", true, "Also print private messages in the chat");
	public static final Opt JOIN_NOTICES = bool("Network", "showUserJoinNotices", false, "Notify when another mod user comes online");

	// ---- Anti AFK ----
	public static final Opt AFK_INTERVAL = num("AntiAFK", "antiAfkInterval", 25, 5, 120, 5, "Seconds between anti-AFK nudges");
	public static final Opt AFK_JUMP = bool("AntiAFK", "antiAfkJump", false, "Also jump when nudging");

	// ---- Auto join ----
	public static final Opt AJ_COMMAND = text("AutoJoin", "autoJoinCommand", "", "Command to repeat (empty = your last typed command)");
	public static final Opt AJ_INTERVAL = num("AutoJoin", "autoJoinInterval", 3, 1, 30, 1, "Seconds between join attempts");
	public static final Opt AJ_MAX = num("AutoJoin", "autoJoinMaxAttempts", 40, 5, 200, 5, "Stop after this many attempts");

	// ---- Misc ----
	public static final Opt KILL_COMMAND = text("Misc", "killCommand", "kill", "Command sent by the kill keybind (no slash)");
	public static final Opt IGNORE_CHAT = bool("Misc", "ignoreHidesChat", true, "Hide chat lines from ignored players");
	public static final Opt HUD = bool("Misc", "showHudIndicators", true, "Show small on-screen indicators (anti-AFK, auto join, war, top target)");

	private Config() {}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("ragemod").resolve("config.json");
	}

	public static void load() {
		try {
			Path f = file();
			if (!Files.exists(f)) {
				save();
				return;
			}
			JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
			for (Opt opt : ALL) {
				if (!o.has(opt.key)) continue;
				JsonElement e = o.get(opt.key);
				if (e.isJsonPrimitive()) {
					if (e.getAsJsonPrimitive().isBoolean()) opt.set(e.getAsBoolean());
					else if (e.getAsJsonPrimitive().isNumber()) opt.set(e.getAsInt());
					else opt.set(e.getAsString());
				}
			}
		} catch (Exception ex) {
			System.err.println("[RAGE] Could not read config: " + ex);
		}
	}

	public static void save() {
		try {
			JsonObject o = new JsonObject();
			for (Opt opt : ALL) {
				Object v = opt.get();
				if (v instanceof Boolean bo) o.addProperty(opt.key, bo);
				else if (v instanceof Number n) o.addProperty(opt.key, n);
				else o.addProperty(opt.key, String.valueOf(v));
			}
			Path f = file();
			Files.createDirectories(f.getParent());
			Files.writeString(f, GSON.toJson(o));
		} catch (Exception ex) {
			System.err.println("[RAGE] Could not save config: " + ex);
		}
	}

	public static void resetAll() {
		for (Opt o : ALL) o.reset();
		save();
	}
}
