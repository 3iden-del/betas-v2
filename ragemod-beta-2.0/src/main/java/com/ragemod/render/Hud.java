package com.ragemod.render;

import com.ragemod.Features;
import com.ragemod.core.Config;
import com.ragemod.core.Lobby;
import com.ragemod.core.War;
import com.ragemod.core.WhitelistGate;
import com.ragemod.net.RelayClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;

/** Tiny on-screen indicators in the top-left corner. */
public final class Hud {
	private Hud() {}

	public static void render(GuiGraphics g, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.options.hideGui || !Config.HUD.b() || !WhitelistGate.allowed()) return;
		int y = 4;
		String prio = War.priority();
		if (!prio.isEmpty()) y = line(g, mc, "TOP TARGET: " + prio + distance(mc, prio), Config.PRIORITY_COLOR.argb(), y);
		if (Features.afk) y = line(g, mc, "RAGE: Anti-AFK ON", 0xFFFF5555, y);
		if (Features.autoJoin) y = line(g, mc, "RAGE: Auto join " + Features.autoJoinAttempts + "/" + Config.AJ_MAX.i(), 0xFFFF5555, y);
		if (War.active) y = line(g, mc, "RAGE: WAR" + (War.joined ? "" : " (not joined)"), 0xFFFF5555, y);
		if (RelayClient.anyUnread()) line(g, mc, "RAGE: new private message", 0xFFFF55FF, y);
	}

	private static String distance(Minecraft mc, String name) {
		if (mc.level != null) {
			for (AbstractClientPlayer p : mc.level.players()) {
				if (p.getName().getString().equalsIgnoreCase(name)) {
					return "  " + Math.round(p.distanceTo(mc.player)) + "m";
				}
			}
		}
		return Lobby.contains(name) ? "  (in lobby, not nearby)" : "  (not in lobby)";
	}

	private static int line(GuiGraphics g, Minecraft mc, String text, int color, int y) {
		g.drawString(mc.font, text, 5, y, color);
		return y + 11;
	}
}
