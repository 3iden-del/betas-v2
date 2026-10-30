package com.ragemod.gui;

import com.ragemod.core.Lobby;
import com.ragemod.core.Status;
import com.ragemod.core.StatusStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Everyone in the lobby (plus saved marks) with group buttons and an emblem for their group. */
public class PlayersScreen extends RageScreen {
	private static final int ROW_H = 22;
	private static final int GAP = 3;
	private static final Status[] BUTTON_GROUPS = {Status.TARGET, Status.FRIEND, Status.ALLIANCE, Status.MUTUAL, Status.ANNOYANCE};
	private static final String[] BUTTON_TEXT = {"Target", "Friend", "Alliance", "Mutual", "Annoy"};

	private EditBox search;
	private String filter = "";
	private int offset;
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int listY;
	private int perPage;
	private List<String> all = new ArrayList<>();

	public PlayersScreen(Screen parent) {
		super("Players", parent);
	}

	@Override
	protected void init() {
		resetDyn();
		px = 10;
		py = 8;
		pw = this.width - 20;
		ph = this.height - 16;
		listY = py + 72;
		perPage = Math.max(1, (ph - 72 - 32) / (ROW_H + GAP));

		vbtn(px + pw - 62, py + 8, 52, 18, plain("Back"), b -> back());
		search = input(px + 10, py + 46, pw - 20, 18, "Search current map or saved players...", s -> {
			filter = s;
			offset = 0;
			layout();
		});
		search.setValue(filter);
		vbtn(px + pw / 2 - 40, py + ph - 26, 30, 18, plain("<"), b -> page(-perPage));
		vbtn(px + pw / 2 + 10, py + ph - 26, 30, 18, plain(">"), b -> page(perPage));
		layout();
	}

	private void collect() {
		Map<String, String> names = new LinkedHashMap<>();
		for (String n : Lobby.names(false)) names.put(n.toLowerCase(Locale.ROOT), n);
		for (StatusStore.Entry e : StatusStore.all()) names.putIfAbsent(e.name.toLowerCase(Locale.ROOT), e.name);
		String f = filter.toLowerCase(Locale.ROOT).trim();
		all = new ArrayList<>();
		for (Map.Entry<String, String> e : names.entrySet()) {
			if (f.isEmpty() || e.getKey().contains(f)) all.add(e.getValue());
		}
		// marked players first, then alphabetical
		all.sort((a, b) -> {
			boolean ma = StatusStore.status(a) != Status.NONE;
			boolean mb = StatusStore.status(b) != Status.NONE;
			if (ma != mb) return ma ? -1 : 1;
			return a.compareToIgnoreCase(b);
		});
	}

	private void layout() {
		clearDyn();
		collect();
		offset = clamp(offset, 0, Math.max(0, all.size() - perPage));

		int bw = clamp((pw - 230) / 6 - 2, 38, 58);
		int totalW = (bw + 2) * 6;
		int x0 = px + pw - 10 - totalW;
		int end = Math.min(all.size(), offset + perPage);
		for (int i = offset; i < end; i++) {
			final String name = all.get(i);
			int y = listY + (i - offset) * (ROW_H + GAP);
			Status cur = StatusStore.status(name);
			for (int k = 0; k < BUTTON_GROUPS.length; k++) {
				final Status s = BUTTON_GROUPS[k];
				dbtn(x0 + k * (bw + 2), y + 1, bw, ROW_H - 2, groupLabel(s, BUTTON_TEXT[k], cur == s), b -> {
					StatusStore.toggle(name, s);
					layout();
				});
			}
			dbtn(x0 + 5 * (bw + 2), y + 1, bw, ROW_H - 2, col("Info", 0x55FFFF),
				b -> Minecraft.getInstance().setScreen(new PlayerInfoScreen(this, name)));
		}
	}

	private void page(int delta) {
		offset = clamp(offset + delta, 0, Math.max(0, all.size() - perPage));
		layout();
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (sy != 0) {
			page(sy > 0 ? -2 : 2);
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		dim(g);
		Theme.colorPanel(g, px, py, pw, ph);
		Theme.text(g, "Players", px + 10, py + 12, Theme.GOLD);

		// legend: emblem + group name
		int lx = px + 10;
		int ly = py + 26;
		for (Status s : Status.GROUPS) {
			Emblems.draw(g, s, lx, ly, 16);
			Theme.text(g, s.label, lx + 19, ly + 4, s.argb());
			lx += 19 + Theme.width(s.label) + 14;
		}

		int end = Math.min(all.size(), offset + perPage);
		for (int i = offset; i < end; i++) {
			String name = all.get(i);
			int y = listY + (i - offset) * (ROW_H + GAP);
			boolean hover = over(px + 8, y, pw - 16, ROW_H, mx, my);
			Theme.rect(g, px + 8, y, pw - 16, ROW_H, hover ? Theme.ROW_HOVER : ((i & 1) == 0 ? Theme.ROW : Theme.ROW_ALT));
			Status s = StatusStore.status(name);
			if (s != Status.NONE) {
				Theme.rect(g, px + 8, y, 3, ROW_H, s.argb());
				Emblems.draw(g, s, px + 14, y + 3, 16);
			}
			int color = s == Status.NONE ? Theme.TEXT : s.argb();
			int nameX = px + 36;
			Theme.text(g, name, nameX, y + (ROW_H - 8) / 2, color);
			if (!Lobby.contains(name)) Theme.text(g, "(offline)", nameX + Theme.width(name) + 8, y + (ROW_H - 8) / 2, Theme.TEXT_DIM);
		}
		if (all.isEmpty()) Theme.text(g, "No players found.", px + 16, listY + 6, Theme.TEXT_DIM);

		String foot = all.isEmpty() ? "0 / 0" : (offset + 1) + "-" + end + " / " + all.size();
		Theme.textRight(g, foot, px + pw - 12, py + ph - 22, Theme.TEXT_DIM);
		super.render(g, mx, my, pt);
	}
}
