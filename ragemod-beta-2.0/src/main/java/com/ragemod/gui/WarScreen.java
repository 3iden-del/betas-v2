package com.ragemod.gui;

import com.ragemod.core.Config;
import com.ragemod.core.KillTracker;
import com.ragemod.core.Lobby;
import com.ragemod.core.Msg;
import com.ragemod.core.Status;
import com.ragemod.core.StatusStore;
import com.ragemod.core.War;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** War module: status and top priority target on the left, pick enemies from the server players on the right. */
public class WarScreen extends RageScreen {
	private static final int ROW_H = 22;
	private static final int GAP = 3;

	private EditBox search;
	private String filter = "";
	private int offset;
	private int lx;
	private int ly;
	private int lw;
	private int lh;
	private int rx;
	private int rw;
	private int listY;
	private int perPage;
	private List<String> shown = new ArrayList<>();

	public WarScreen(Screen parent) {
		super("War", parent);
	}

	@Override
	protected void init() {
		resetDyn();
		lx = 10;
		ly = 8;
		lw = Math.min(290, this.width * 38 / 100);
		lh = this.height - 16;
		rx = lx + lw + 8;
		rw = this.width - rx - 10;
		listY = ly + 58;
		perPage = Math.max(1, (lh - 58 - 32) / (ROW_H + GAP));
		vbtn(rx + rw - 62, ly + 8, 52, 18, plain("Back"), b -> back());
		search = input(rx + 10, ly + 30, rw - 20, 18, "Search current server nicks...", s -> {
			filter = s;
			offset = 0;
			layout();
		});
		search.setValue(filter);
		vbtn(rx + rw / 2 - 40, ly + lh - 26, 30, 18, plain("<"), b -> page(-perPage));
		vbtn(rx + rw / 2 + 10, ly + lh - 26, 30, 18, plain(">"), b -> page(perPage));
		layout();
	}

	private void layout() {
		clearDyn();
		String f = filter.toLowerCase(Locale.ROOT).trim();
		shown = new ArrayList<>();
		for (String n : Lobby.names(false)) {
			if (f.isEmpty() || n.toLowerCase(Locale.ROOT).contains(f)) shown.add(n);
		}
		// priority first, then enemies, then the rest
		shown.sort((a, b) -> {
			int ra = rank(a);
			int rb = rank(b);
			if (ra != rb) return ra - rb;
			return a.compareToIgnoreCase(b);
		});
		offset = clamp(offset, 0, Math.max(0, shown.size() - perPage));

		int by = ly + 38 + 9 * 12 + 30;
		if (!War.active) {
			dbtn(lx + 10, by, lw - 20, 20, bold("Start War", 0x55FF55), b -> {
				War.start();
				layout();
			});
		} else {
			if (!War.joined) {
				dbtn(lx + 10, by, lw - 20, 20, bold("Join War", 0x55FF55), b -> {
					War.join();
					layout();
				});
				by += 24;
			}
			dbtn(lx + 10, by, lw - 20, 20, bold("End War", 0xFF5555), b -> {
				War.end();
				layout();
			});
		}
		if (!War.priority().isEmpty()) {
			dbtn(lx + 10, ly + 38 + 9 * 12 + 4, lw - 20, 20, col("Clear top priority", 0xFFFF55), b -> {
				War.clearPriority();
				layout();
			});
		}

		int end = Math.min(shown.size(), offset + perPage);
		for (int i = offset; i < end; i++) {
			final String name = shown.get(i);
			int y = listY + (i - offset) * (ROW_H + GAP);
			boolean enemy = StatusStore.status(name) == Status.ENEMY;
			boolean prio = War.isPriority(name);
			dbtn(rx + rw - 10 - 142, y + 1, 70, ROW_H - 2, prio ? bold("Top target", 0xFFFF55) : col("Top target", 0xAAAAAA), b -> {
				if (StatusStore.status(name) != Status.ENEMY) StatusStore.set(name, Status.ENEMY);
				War.togglePriority(name);
				layout();
			});
			dbtn(rx + rw - 10 - 70, y + 1, 70, ROW_H - 2, enemy ? col("Deselect", 0xFF5555) : col("Enemy", 0xFFFFFF), b -> {
				StatusStore.toggle(name, Status.ENEMY);
				if (War.isPriority(name) && StatusStore.status(name) != Status.ENEMY) War.clearPriority();
				layout();
			});
		}
	}

	private int rank(String name) {
		if (War.isPriority(name)) return 0;
		if (StatusStore.status(name) == Status.ENEMY) return 1;
		return 2;
	}

	private void page(int delta) {
		offset = clamp(offset + delta, 0, Math.max(0, shown.size() - perPage));
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

		// ---- left: war status ----
		Theme.colorPanel(g, lx, ly, lw, lh);
		Emblems.draw(g, Status.ENEMY, lx + 10, ly + 10, 16);
		Theme.text(g, "War", lx + 32, ly + 15, Theme.GOLD);
		int y = ly + 38;
		Theme.text(g, "Active: " + War.active, lx + 10, y, War.active ? Theme.GOOD : Theme.TEXT);
		Theme.text(g, "Joined: " + War.joined, lx + 10, y + 12, Theme.TEXT);
		Theme.text(g, "Leading: " + War.leading, lx + 10, y + 24, Theme.TEXT);
		Theme.text(g, "Server: " + Theme.fit(War.active ? War.server : Msg.server(), lw - 80), lx + 10, y + 36, Theme.TEXT);
		Theme.text(g, "Leader: " + Theme.fit(War.leader, lw - 80), lx + 10, y + 48, Theme.TEXT);
		Theme.text(g, "Enemies: " + War.enemies(), lx + 10, y + 60, Theme.TEXT_DIM);
		KillTracker.Stats st = KillTracker.stats(Msg.me(), StatusStore.namesWith(Status.ENEMY), false);
		String vs = (st.kills() + st.deaths() == 0)
			? "no fights"
			: st.kills() + "-" + st.deaths() + " (" + String.format("%.2f", st.kd()) + ")";
		Theme.text(g, Theme.fit("Your K/D vs enemies: " + vs, lw - 20), lx + 10, y + 72, Theme.TEXT_DIM);

		// top priority block
		Emblems.drawPriority(g, lx + 10, y + 86, 16);
		String prio = War.priority();
		if (prio.isEmpty()) {
			Theme.text(g, "Top priority: none", lx + 32, y + 90, Theme.TEXT_DIM);
		} else {
			Theme.text(g, Theme.fit("Top priority: " + prio, lw - 50), lx + 32, y + 90, Config.PRIORITY_COLOR.argb());
		}
		if (!War.note.isEmpty()) Theme.text(g, Theme.fit(War.note, lw - 20), lx + 10, ly + lh - 18, Theme.GOOD);

		// ---- right: server players ----
		Theme.colorPanel(g, rx, ly, rw, lh);
		Theme.text(g, "Current Server Players", rx + 10, ly + 14, Theme.GOLD);

		int end = Math.min(shown.size(), offset + perPage);
		for (int i = offset; i < end; i++) {
			String name = shown.get(i);
			int ry = listY + (i - offset) * (ROW_H + GAP);
			boolean enemy = StatusStore.status(name) == Status.ENEMY;
			boolean prioRow = War.isPriority(name);
			boolean hover = over(rx + 8, ry, rw - 16, ROW_H, mx, my);
			Theme.rect(g, rx + 8, ry, rw - 16, ROW_H, hover ? Theme.ROW_HOVER : Theme.ROW);
			int nameX = rx + 16;
			if (prioRow) {
				Emblems.drawPriority(g, nameX, ry + 3, 16);
				nameX += 20;
			} else if (enemy) {
				Emblems.draw(g, Status.ENEMY, nameX, ry + 3, 16);
				nameX += 20;
			}
			int color = prioRow ? Config.PRIORITY_COLOR.argb() : (enemy ? Status.ENEMY.argb() : Theme.TEXT);
			Theme.text(g, Theme.fit(name, rw - 190), nameX, ry + (ROW_H - 8) / 2, color);
		}
		if (shown.isEmpty()) Theme.text(g, "No players found.", rx + 16, listY + 6, Theme.TEXT_DIM);
		String foot = shown.isEmpty() ? "0 / 0" : (offset + 1) + "-" + end + " / " + shown.size();
		Theme.textRight(g, foot, rx + rw - 12, ly + lh - 22, Theme.TEXT_DIM);
		super.render(g, mx, my, pt);
	}
}
