package com.ragemod.gui;

import com.ragemod.core.ChatLog;
import com.ragemod.core.Config;
import com.ragemod.core.KillTracker;
import com.ragemod.core.Lobby;
import com.ragemod.core.Msg;
import com.ragemod.core.NameStore;
import com.ragemod.core.PlayerLookup;
import com.ragemod.core.Status;
import com.ragemod.core.StatusStore;
import com.ragemod.core.War;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Player Info tab: search any player, see a card with the skin they are wearing, your K/D against them,
 * their name history, a button to save their skin file, and everything they said (with times).
 */
public class PlayerInfoScreen extends RageScreen {
	private static final int ROW_H = 18;
	private static final int M = 8;
	private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss");
	private static final Status[] GROUP_BUTTONS = {Status.TARGET, Status.FRIEND, Status.ALLIANCE, Status.MUTUAL, Status.ANNOYANCE, Status.ENEMY};
	private static final String[] GROUP_TEXT = {"Target", "Friend", "Alliance", "Mutual", "Annoy", "Enemy"};

	private record Row(String name, boolean lookup) {}

	private String selected;
	private EditBox search;
	private String filter = "";
	private int listOffset;
	private int histScroll;
	private int chatScroll;
	private List<Row> rows = new ArrayList<>();

	private int leftW;
	private int rightX;
	private int rightW;
	private int topH;
	private int cardW;
	private int infoX;
	private int infoW;
	private int botY;
	private int botH;
	private int histW;
	private int chatX;
	private int chatW;
	private int listTop;
	private int perPage;

	public PlayerInfoScreen(Screen parent, String initial) {
		super("Player Info", parent);
		this.selected = initial;
	}

	@Override
	protected void init() {
		resetDyn();
		int w = this.width;
		int h = this.height;
		leftW = clamp(w * 24 / 100, 140, 210);
		rightX = M + leftW + 8;
		rightW = w - rightX - M;
		topH = clamp((h - 2 * M) * 52 / 100, 150, 240);
		cardW = clamp(rightW * 32 / 100, 150, 210);
		infoX = rightX + cardW + 8;
		infoW = rightW - cardW - 8;
		botY = M + topH + 8;
		botH = h - M - botY;
		histW = clamp(rightW * 34 / 100, 120, 240);
		chatX = rightX + histW + 8;
		chatW = rightW - histW - 8;
		listTop = M + 46;
		perPage = Math.max(1, (h - M - 30 - listTop) / ROW_H);

		search = input(M + 6, M + 22, leftW - 12, 18, "Search a player...", s -> {
			filter = s;
			listOffset = 0;
			layout();
		});
		search.setValue(filter);
		vbtn(M + 6, h - M - 24, 56, 18, plain("Back"), b -> back());
		vbtn(M + leftW - 68, h - M - 24, 30, 18, plain("<"), b -> page(-perPage));
		vbtn(M + leftW - 36, h - M - 24, 30, 18, plain(">"), b -> page(perPage));
		if (selected != null) PlayerLookup.get(selected);
		layout();
	}

	private void select(String name) {
		selected = name;
		histScroll = 0;
		chatScroll = 0;
		PlayerLookup.get(name);
		layout();
	}

	private void page(int delta) {
		listOffset = clamp(listOffset + delta, 0, Math.max(0, rows.size() - perPage));
		layout();
	}

	private void buildRows() {
		Map<String, String> names = new LinkedHashMap<>();
		Set<String> online = new HashSet<>();
		for (String n : Lobby.names(true)) {
			names.put(n.toLowerCase(Locale.ROOT), n);
			online.add(n.toLowerCase(Locale.ROOT));
		}
		for (StatusStore.Entry e : StatusStore.all()) names.putIfAbsent(e.name.toLowerCase(Locale.ROOT), e.name);
		for (String n : ChatLog.names()) names.putIfAbsent(n.toLowerCase(Locale.ROOT), n);
		for (String n : NameStore.knownNames()) names.putIfAbsent(n.toLowerCase(Locale.ROOT), n);
		if (selected != null) names.putIfAbsent(selected.toLowerCase(Locale.ROOT), selected);

		String f = filter.toLowerCase(Locale.ROOT).trim();
		List<String> list = new ArrayList<>();
		for (Map.Entry<String, String> e : names.entrySet()) {
			if (f.isEmpty() || e.getKey().contains(f)) list.add(e.getValue());
		}
		list.sort((a, b) -> {
			boolean oa = online.contains(a.toLowerCase(Locale.ROOT));
			boolean ob = online.contains(b.toLowerCase(Locale.ROOT));
			if (oa != ob) return oa ? -1 : 1;
			return a.compareToIgnoreCase(b);
		});
		rows = new ArrayList<>();
		String typed = filter.trim();
		if (!typed.isEmpty() && typed.matches("[A-Za-z0-9_]{1,16}") && !names.containsKey(typed.toLowerCase(Locale.ROOT))) {
			rows.add(new Row(typed, true));
		}
		for (String n : list) rows.add(new Row(n, false));
	}

	private void layout() {
		clearDyn();
		buildRows();
		listOffset = clamp(listOffset, 0, Math.max(0, rows.size() - perPage));

		int end = Math.min(rows.size(), listOffset + perPage);
		for (int i = listOffset; i < end; i++) {
			final Row r = rows.get(i);
			int y = listTop + (i - listOffset) * ROW_H;
			hit(M + 4, y, leftW - 8, ROW_H, b -> select(r.name()));
		}

		if (selected == null) return;
		final String name = selected;
		int bx = infoX + 10;
		int bw = infoW - 20;
		int gw = (bw - 5 * 2) / 6;
		int y1 = M + topH - 50;
		Status cur = StatusStore.status(name);
		for (int k = 0; k < GROUP_BUTTONS.length; k++) {
			final Status s = GROUP_BUTTONS[k];
			dbtn(bx + k * (gw + 2), y1, gw, 20, groupLabel(s, GROUP_TEXT[k], cur == s), b -> {
				StatusStore.toggle(name, s);
				layout();
			});
		}
		int y2 = M + topH - 26;
		int fw = (bw - 4 * 2) / 5;
		dbtn(bx, y2, fw, 20, War.isPriority(name) ? bold("Top target", 0xFFFF55) : col("Top target", 0xAAAAAA), b -> {
			War.togglePriority(name);
			layout();
		});
		dbtn(bx + (fw + 2), y2, fw, 20, StatusStore.show(name) ? bold("Show", 0xFFFFFF) : col("Show", 0xAAAAAA), b -> {
			StatusStore.toggleShow(name);
			layout();
		});
		dbtn(bx + 2 * (fw + 2), y2, fw, 20, StatusStore.ignored(name) ? bold("Ignore", 0xFF5555) : col("Ignore", 0xAAAAAA), b -> {
			StatusStore.toggleIgnore(name);
			layout();
		});
		dbtn(bx + 3 * (fw + 2), y2, fw, 20, col("Save skin", 0x55FFFF), b -> {
			PlayerLookup.saveSkin(name);
			Msg.say("Saving skin for " + name + "...");
		});
		dbtn(bx + 4 * (fw + 2), y2, fw, 20, col("Refresh", 0x55FF55), b -> PlayerLookup.refresh(name));
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (sy == 0) return super.mouseScrolled(mx, my, sx, sy);
		int dir = sy > 0 ? 1 : -1;
		if (over(chatX, botY, chatW, botH, (int) mx, (int) my)) {
			chatScroll = Math.max(0, chatScroll + dir * 3);
		} else if (over(rightX, botY, histW, botH, (int) mx, (int) my)) {
			histScroll = Math.max(0, histScroll - dir * 2);
		} else {
			listOffset = clamp(listOffset - dir * 2, 0, Math.max(0, rows.size() - perPage));
			layout();
		}
		return true;
	}

	private static AbstractClientPlayer findEntity(String name) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return null;
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p.getName().getString().equalsIgnoreCase(name)) return p;
		}
		return null;
	}

	private int groupColor(String name) {
		if (War.isPriority(name)) return Config.PRIORITY_COLOR.argb();
		Status s = StatusStore.status(name);
		return s == Status.NONE ? 0xFF8C8CB0 : s.argb();
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		dim(g);
		int h = this.height;

		// ---- left: search list ----
		Theme.colorPanel(g, M, M, leftW, h - 2 * M);
		Theme.text(g, "Player Info", M + 8, M + 8, Theme.GOLD);
		int end = Math.min(rows.size(), listOffset + perPage);
		for (int i = listOffset; i < end; i++) {
			Row r = rows.get(i);
			int y = listTop + (i - listOffset) * ROW_H;
			boolean sel = selected != null && r.name().equalsIgnoreCase(selected);
			boolean hover = over(M + 4, y, leftW - 8, ROW_H, mx, my);
			Theme.rect(g, M + 4, y, leftW - 8, ROW_H - 1, sel ? Theme.ROW_SEL : (hover ? Theme.ROW_HOVER : ((i & 1) == 0 ? Theme.ROW : Theme.ROW_ALT)));
			Status s = StatusStore.status(r.name());
			if (s != Status.NONE) Emblems.draw(g, s, M + 6, y + 1, 16);
			boolean on = Lobby.contains(r.name());
			int color = s == Status.NONE ? (on ? Theme.TEXT : Theme.TEXT_DIM) : s.argb();
			String label = r.lookup() ? "Look up: " + r.name() : r.name();
			Theme.text(g, Theme.fit(label, leftW - 52), M + 26, y + 5, color);
			if (on) Theme.rect(g, M + leftW - 14, y + 7, 4, 4, Theme.GOOD);
		}
		if (rows.isEmpty()) Theme.text(g, "No players found.", M + 10, listTop + 4, Theme.TEXT_DIM);
		String foot = rows.isEmpty() ? "0" : (listOffset + 1) + "-" + end + " / " + rows.size();
		Theme.textCentered(g, foot, M + leftW / 2, h - M - 40, Theme.TEXT_DIM);

		renderCard(g, mx, my);
		renderInfo(g);
		renderHistory(g);
		renderChat(g);
		super.render(g, mx, my, pt);
	}

	private void renderCard(GuiGraphics g, int mx, int my) {
		int x = rightX;
		Theme.panel(g, x, M, cardW, topH);
		if (selected == null) {
			Theme.textCentered(g, "Select a player", x + cardW / 2, M + topH / 2 - 4, Theme.TEXT_DIM);
			return;
		}
		int gc = groupColor(selected);
		Theme.border(g, x, M, cardW, topH, gc);
		Theme.border(g, x + 1, M + 1, cardW - 2, topH - 2, gc);
		Status st = StatusStore.status(selected);
		if (st != Status.NONE) Emblems.draw(g, st, x + 8, M + 8, 32);
		if (War.isPriority(selected)) Emblems.drawPriority(g, x + cardW - 40, M + 8, 32);

		int x1 = x + 14;
		int y1 = M + 38;
		int x2 = x + cardW - 14;
		int y2 = M + topH - 46;
		AbstractClientPlayer ent = findEntity(selected);
		if (ent != null) {
			int scale = Math.max(20, (y2 - y1) * 3 / 7);
			InventoryScreen.renderEntityInInventoryFollowsMouse(g, x1, y1, x2, y2, scale, 0.0625F, mx, my, ent);
		} else {
			Minecraft mc = Minecraft.getInstance();
			UUID id = Lobby.uuidOf(selected);
			PlayerInfo info = (id != null && mc.getConnection() != null) ? mc.getConnection().getPlayerInfo(id) : null;
			int cx = x + cardW / 2;
			if (info != null) {
				PlayerFaceRenderer.draw(g, info.getSkin(), cx - 32, y1 + 14, 64);
				Theme.textCentered(g, "Not nearby", cx, y1 + 86, Theme.TEXT_DIM);
			} else {
				Theme.rect(g, cx - 32, y1 + 14, 64, 64, 0xFF2E2E3A);
				Theme.textCentered(g, "?", cx, y1 + 42, Theme.TEXT_DIM);
				Theme.textCentered(g, "Not in the lobby", cx, y1 + 86, Theme.TEXT_DIM);
			}
		}
		Theme.textCentered(g, Theme.fit(selected, cardW - 12), x + cardW / 2, M + topH - 38, gc);
		String groupText = War.isPriority(selected) ? "TOP PRIORITY TARGET" : (st == Status.NONE ? "No group" : st.label);
		Theme.textCentered(g, groupText, x + cardW / 2, M + topH - 26, gc);
		Theme.textCentered(g, Lobby.contains(selected) ? "In your lobby" : "Not in your lobby", x + cardW / 2, M + topH - 14, Theme.TEXT_DIM);
	}

	private void renderInfo(GuiGraphics g) {
		Theme.panel(g, infoX, M, infoW, topH);
		Theme.groupBar(g, infoX + 1, M + 1, infoW - 2, 3);
		if (selected == null) {
			Theme.text(g, "Search or click a player on the left.", infoX + 10, M + 14, Theme.TEXT_DIM);
			return;
		}
		PlayerLookup.Profile prof = PlayerLookup.get(selected);
		int x = infoX + 10;
		int y = M + 10;
		Theme.text(g, Theme.fit(selected, infoW - 20), x, y, groupColor(selected));
		y += 14;
		String uuid = prof.uuid.isEmpty() ? "unknown" : prof.uuid;
		Theme.text(g, Theme.fit("UUID: " + uuid, infoW - 20), x, y, Theme.TEXT_DIM);
		y += 12;
		KillTracker.Stats vs = KillTracker.stats(Msg.me(), List.of(selected), false);
		String kd = (vs.kills() + vs.deaths() == 0)
			? "no fights yet"
			: vs.kills() + " kills, " + vs.deaths() + " deaths  (K/D " + String.format("%.2f", vs.kd()) + ")";
		Theme.text(g, Theme.fit("You vs them: " + kd, infoW - 20), x, y, Theme.GOOD);
		y += 12;
		if (!prof.skinUrl.isEmpty()) {
			Theme.text(g, "Skin model: " + (prof.slim ? "slim" : "classic"), x, y, Theme.TEXT_DIM);
			y += 12;
		}
		if (prof.loading) {
			Theme.text(g, "Looking up online info...", x, y, Theme.GOLD);
		} else if (!prof.error.isEmpty()) {
			for (String line : Theme.wrap(prof.error, infoW - 20)) {
				Theme.text(g, line, x, y, 0xFFFFAA55);
				y += 11;
			}
		} else {
			Theme.text(g, "Online info loaded.", x, y, Theme.TEXT_DIM);
		}
	}

	private void renderHistory(GuiGraphics g) {
		Theme.panel(g, rightX, botY, histW, botH);
		Theme.text(g, "Name history", rightX + 8, botY + 7, Theme.GOLD);
		if (selected == null) return;
		PlayerLookup.Profile prof = PlayerLookup.get(selected);
		List<String[]> hist = prof.history;
		int maxRows = Math.max(1, (botH - 26) / 11);
		histScroll = clamp(histScroll, 0, Math.max(0, hist.size() - maxRows));
		int y = botY + 22;
		if (hist.isEmpty()) {
			Theme.text(g, prof.loading ? "Loading..." : "Nothing found.", rightX + 8, y, Theme.TEXT_DIM);
			return;
		}
		for (int i = histScroll; i < Math.min(hist.size(), histScroll + maxRows); i++) {
			String[] e = hist.get(i);
			String left = Theme.fit(e[0], histW - 100);
			Theme.text(g, left, rightX + 8, y, Theme.TEXT);
			Theme.textRight(g, e[1], rightX + histW - 8, y, Theme.TEXT_DIM);
			y += 11;
		}
	}

	private void renderChat(GuiGraphics g) {
		Theme.panel(g, chatX, botY, chatW, botH);
		if (selected == null) {
			Theme.text(g, "Chat log", chatX + 8, botY + 7, Theme.GOLD);
			return;
		}
		List<ChatLog.Line> lines = ChatLog.lines(selected);
		Theme.text(g, "Chat log (" + lines.size() + ")", chatX + 8, botY + 7, Theme.GOLD);
		if (lines.isEmpty()) {
			Theme.text(g, Config.CHAT_LOG.b() ? "Nothing recorded yet." : "Chat logging is off in the config.", chatX + 8, botY + 22, Theme.TEXT_DIM);
			return;
		}
		int stampW = Theme.width("00-00 00:00:00") + 6;
		int textW = chatW - 16 - stampW;
		List<String[]> flat = new ArrayList<>();
		for (ChatLog.Line l : lines) {
			String stamp = STAMP.format(Instant.ofEpochMilli(l.time()).atZone(ZoneId.systemDefault()));
			List<String> wrapped = Theme.wrap(l.text(), Math.max(40, textW));
			for (int i = 0; i < wrapped.size(); i++) flat.add(new String[] {i == 0 ? stamp : "", wrapped.get(i)});
		}
		int maxLines = Math.max(1, (botH - 26) / 11);
		chatScroll = clamp(chatScroll, 0, Math.max(0, flat.size() - maxLines));
		int start = Math.max(0, flat.size() - maxLines - chatScroll);
		int y = botY + 22;
		for (int i = start; i < Math.min(flat.size(), start + maxLines); i++) {
			String[] f = flat.get(i);
			if (!f[0].isEmpty()) Theme.text(g, f[0], chatX + 8, y, Theme.TEXT_DIM);
			Theme.text(g, f[1], chatX + 8 + stampW, y, Theme.TEXT);
			y += 11;
		}
		if (chatScroll > 0) Theme.textRight(g, "scrolled up", chatX + chatW - 8, botY + 7, Theme.TEXT_DIM);
	}
}
