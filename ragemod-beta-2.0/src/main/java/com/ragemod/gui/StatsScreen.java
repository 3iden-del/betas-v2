package com.ragemod.gui;

import com.ragemod.core.KillTracker;
import com.ragemod.core.Lobby;
import com.ragemod.core.Msg;
import com.ragemod.core.Status;
import com.ragemod.core.StatusStore;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Stats dashboard: pick a player (left), optional opponents (right), see kills / deaths / K/D and a graph. */
public class StatsScreen extends RageScreen {
	private static final int ROW_H = 18;

	private EditBox search;
	private Button scopeButton;
	private String filter = "";
	private String left;
	private final Map<String, String> right = new LinkedHashMap<>(); // lower -> display
	private boolean sessionOnly = true;
	private int offL;
	private int offR;

	private int lx;
	private int cx;
	private int rx;
	private int colW;
	private int cenW;
	private int top;
	private int bottom;
	private int perPage;
	private List<String> names = new ArrayList<>();
	private KillTracker.Stats stats = new KillTracker.Stats(0, 0, new double[0]);

	public StatsScreen(Screen parent, String initial) {
		super("Stats", parent);
		this.left = initial != null ? initial : Msg.me();
	}

	@Override
	protected void init() {
		resetDyn();
		int w = this.width;
		colW = Math.max(110, w * 27 / 100);
		cenW = w - 2 * colW - 4 * 8 - 8;
		lx = 8;
		cx = lx + colW + 8;
		rx = cx + cenW + 8;
		top = 54;
		bottom = this.height - 8;
		perPage = Math.max(1, (bottom - top - 30) / (ROW_H + 2));

		int scopeW = Math.max(150, w * 40 / 100);
		scopeButton = vbtn(lx, 26, scopeW, 20, plain(""), b -> {
			sessionOnly = !sessionOnly;
			layout();
		});
		search = input(lx + scopeW + 8, 26, w - lx - scopeW - 16, 20, "Search players...", s -> {
			filter = s;
			offL = 0;
			offR = 0;
			layout();
		});
		search.setValue(filter);
		vbtn(w - 60, 4, 52, 18, plain("Back"), b -> back());
		layout();
	}

	private void collect() {
		Map<String, String> all = new LinkedHashMap<>();
		for (String n : Lobby.names(true)) all.put(n.toLowerCase(Locale.ROOT), n);
		all.putIfAbsent(Msg.me().toLowerCase(Locale.ROOT), Msg.me());
		for (StatusStore.Entry e : StatusStore.all()) all.putIfAbsent(e.name.toLowerCase(Locale.ROOT), e.name);
		all.putIfAbsent(left.toLowerCase(Locale.ROOT), left);
		String f = filter.toLowerCase(Locale.ROOT).trim();
		names = new ArrayList<>();
		for (Map.Entry<String, String> e : all.entrySet()) {
			if (f.isEmpty() || e.getKey().contains(f)) names.add(e.getValue());
		}
		names.sort(String.CASE_INSENSITIVE_ORDER);
	}

	private void layout() {
		clearDyn();
		collect();
		offL = clamp(offL, 0, Math.max(0, names.size() - perPage));
		offR = clamp(offR, 0, Math.max(0, names.size() - perPage));
		stats = KillTracker.stats(left, right.values(), sessionOnly);
		scopeButton.setMessage(col("Scope: " + (sessionOnly ? "This session" : "All time"), 0xFFFF55));

		int endL = Math.min(names.size(), offL + perPage);
		for (int i = offL; i < endL; i++) {
			final String n = names.get(i);
			int y = top + 24 + (i - offL) * (ROW_H + 2);
			hit(lx + 4, y, colW - 8, ROW_H, b -> {
				left = n;
				layout();
			});
		}
		int endR = Math.min(names.size(), offR + perPage);
		for (int i = offR; i < endR; i++) {
			final String n = names.get(i);
			int y = top + 24 + (i - offR) * (ROW_H + 2);
			hit(rx + 4, y, colW - 8, ROW_H, b -> {
				String k = n.toLowerCase(Locale.ROOT);
				if (right.containsKey(k)) right.remove(k);
				else right.put(k, n);
				layout();
			});
		}
	}

	private int colorOf(String n) {
		Status s = StatusStore.status(n);
		return s == Status.NONE ? 0xFF8CCBFF : s.argb();
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (sy == 0) return super.mouseScrolled(mx, my, sx, sy);
		int d = sy > 0 ? -2 : 2;
		if (mx >= rx) offR = clamp(offR + d, 0, Math.max(0, names.size() - perPage));
		else offL = clamp(offL + d, 0, Math.max(0, names.size() - perPage));
		layout();
		return true;
	}

	private void drawList(GuiGraphics g, int x, int off, boolean multi, int mx, int my) {
		int end = Math.min(names.size(), off + perPage);
		for (int i = off; i < end; i++) {
			String n = names.get(i);
			int y = top + 24 + (i - off) * (ROW_H + 2);
			boolean sel = multi ? right.containsKey(n.toLowerCase(Locale.ROOT)) : n.equalsIgnoreCase(left);
			boolean hover = over(x + 4, y, colW - 8, ROW_H, mx, my);
			Theme.rect(g, x + 4, y, colW - 8, ROW_H, sel ? Theme.ROW_SEL : (hover ? Theme.ROW_HOVER : Theme.ROW));
			Status s = StatusStore.status(n);
			if (s != Status.NONE) Emblems.draw(g, s, x + 6, y + 1, 16);
			else Theme.rect(g, x + 8, y + 7, 4, 4, colorOf(n));
			Theme.text(g, Theme.fit(n, colW - 40), x + 26, y + 5, sel ? Theme.TEXT : colorOf(n));
		}
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		dim(g);
		Theme.textCentered(g, "RAGE Stats Dashboard", this.width / 2, 10, Theme.GOLD);

		Theme.colorPanel(g, lx, top, colW, bottom - top);
		Theme.textCentered(g, "LEFT PLAYERS", lx + colW / 2, top + 10, Theme.GOOD);
		drawList(g, lx, offL, false, mx, my);
		Theme.textCentered(g, names.size() + " players", lx + colW / 2, bottom - 12, Theme.TEXT_DIM);

		Theme.colorPanel(g, rx, top, colW, bottom - top);
		Theme.textCentered(g, "RIGHT (OPTIONAL)", rx + colW / 2, top + 10, Theme.BAD);
		drawList(g, rx, offR, true, mx, my);
		Theme.textCentered(g, right.size() + " selected", rx + colW / 2, bottom - 12, Theme.TEXT_DIM);

		Theme.colorPanel(g, cx, top, cenW, bottom - top);
		int pad = 10;
		int ty = top + 12;
		String rightText = right.isEmpty() ? "All opponents" : String.join(", ", right.values());
		int half = (cenW - 2 * pad - 24) / 2;
		Theme.text(g, Theme.fit(left, half), cx + pad, ty, colorOf(left));
		Theme.textCentered(g, "VS", cx + cenW / 2, ty, Theme.TEXT_DIM);
		Theme.textRight(g, Theme.fit(rightText, half), cx + cenW - pad, ty, Theme.BAD);

		Theme.text(g, "Kills: " + stats.kills(), cx + pad, ty + 20, Theme.GOOD);
		Theme.text(g, "Deaths: " + stats.deaths(), cx + pad, ty + 34, Theme.BAD);
		Theme.text(g, "K/D: " + String.format("%.2f", stats.kd()), cx + pad, ty + 48, Theme.GOLD);

		int gx = cx + pad;
		int gy = ty + 70;
		int gw = cenW - 2 * pad;
		int gh = bottom - gy - pad;
		if (gh > 40) drawGraph(g, gx, gy, gw, gh);
		super.render(g, mx, my, pt);
	}

	/** K/D over time as a step line. */
	private void drawGraph(GuiGraphics g, int gx, int gy, int gw, int gh) {
		Theme.rect(g, gx, gy, gw, gh, 0xFF0E0E14);
		Theme.border(g, gx, gy, gw, gh, Theme.PANEL_EDGE);
		Theme.text(g, "K/D over time", gx + 6, gy + 5, Theme.TEXT_DIM);

		double[] src = stats.series();
		if (src.length == 0) {
			Theme.textCentered(g, "No fights yet", gx + gw / 2, gy + gh / 2, Theme.TEXT_DIM);
			return;
		}
		double[] v = new double[src.length + 1];
		v[0] = 0.0;
		System.arraycopy(src, 0, v, 1, src.length);
		double max = 1.0;
		for (double d : v) max = Math.max(max, d);
		max *= 1.1;

		int ax = gx + 8;
		int ay = gy + 20;
		int aw = gw - 16;
		int ah = gh - 28;
		int n = v.length;
		int color = 0xFF55FF77;
		for (int i = 0; i < n; i++) {
			int x1 = ax + (n == 1 ? 0 : i * aw / (n - 1));
			int y1 = ay + ah - (int) Math.round(v[i] / max * ah);
			int x2 = (i + 1 < n) ? ax + (i + 1) * aw / (n - 1) : ax + aw;
			g.fill(x1, y1 - 1, x2 + 1, y1 + 1, color);
			if (i + 1 < n) {
				int y2 = ay + ah - (int) Math.round(v[i + 1] / max * ah);
				g.fill(x2 - 1, Math.min(y1, y2) - 1, x2 + 1, Math.max(y1, y2) + 1, color);
			}
		}
	}
}
