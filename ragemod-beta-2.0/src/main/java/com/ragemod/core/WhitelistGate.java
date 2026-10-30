package com.ragemod.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Access gate. The RAGE relay decides by your Minecraft name whether you are allowed
 * (admins manage the list from the Dev tab). The last "allowed" answer is remembered for a few
 * days so the mod still works if the relay is asleep or offline.
 */
public final class WhitelistGate {
	/** Set to false to skip the allowlist completely (testing only). */
	private static final boolean ENFORCE = true;
	private static final long GRACE_MS = TimeUnit.DAYS.toMillis(3);

	public record Member(String name, String role) {}

	private static volatile boolean relayAllowed;
	private static volatile boolean denied;
	private static volatile String reason = "Connecting to the RAGE relay...";
	private static volatile String clanName = "RAGE";
	private static volatile List<Member> members = Collections.emptyList();
	private static String graceName = "";
	private static long graceAt;
	private static long lastNoticeMs;

	private WhitelistGate() {}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("ragemod").resolve("access.json");
	}

	/** Loads the remembered access (call once at startup). */
	public static void start() {
		try {
			Path f = file();
			if (!Files.exists(f)) return;
			JsonObject o = JsonParser.parseString(Files.readString(f)).getAsJsonObject();
			graceName = o.get("name").getAsString();
			graceAt = o.get("at").getAsLong();
		} catch (Exception ignored) {
			// no remembered access yet
		}
	}

	private static boolean graceOk() {
		try {
			return !graceName.isEmpty() && graceName.equalsIgnoreCase(Msg.me()) && System.currentTimeMillis() - graceAt < GRACE_MS;
		} catch (Throwable t) {
			return false;
		}
	}

	/** True if this player may use the mod right now. Safe to call every frame. */
	public static boolean allowed() {
		if (!ENFORCE) return true;
		return relayAllowed || (!denied && graceOk());
	}

	public static String reason() { return reason; }
	public static String clan() { return clanName; }
	public static List<Member> members() { return members; }

	/** Called by the relay client when the relay answers the hello. */
	public static void setFromRelay(boolean allowed, String why) {
		relayAllowed = allowed;
		denied = !allowed;
		reason = allowed ? "" : (why == null || why.isEmpty() ? "You are not on the RAGE allowlist." : why);
		if (allowed) {
			try {
				graceName = Msg.me();
				graceAt = System.currentTimeMillis();
				JsonObject o = new JsonObject();
				o.addProperty("name", graceName);
				o.addProperty("at", graceAt);
				Path f = file();
				Files.createDirectories(f.getParent());
				Files.writeString(f, o.toString());
			} catch (Exception ignored) {
				// not critical
			}
		}
	}

	public static void setRoster(String clan, List<Member> roster) {
		if (clan != null && !clan.isEmpty()) clanName = clan;
		members = new ArrayList<>(roster);
	}

	/** Use in keybind handlers. Returns true if allowed, otherwise tells the player how to get access. */
	public static boolean ensureAllowed() {
		if (allowed()) return true;
		long now = System.currentTimeMillis();
		if (now - lastNoticeMs > 3000) {
			lastNoticeMs = now;
			Minecraft mc = Minecraft.getInstance();
			if (mc.player != null) {
				mc.player.displayClientMessage(Component.literal("[RAGE] Locked: " + (reason.isEmpty() ? "waiting for the relay..." : reason)), false);
				mc.player.displayClientMessage(Component.literal("[RAGE] Your name: " + Msg.me() + " (give it to a RAGE admin)"), false);
			}
		}
		return false;
	}
}
