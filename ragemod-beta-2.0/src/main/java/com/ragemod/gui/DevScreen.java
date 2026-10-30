package com.ragemod.gui;

import com.ragemod.net.RelayClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;

import java.util.List;

/** Admin tools: who is on the mod (with IP), who is waiting for access, and the allowlist. */
public class DevScreen extends RageScreen {
	private static final int ROW_H = 22;
	private static final int GAP = 2;
	private static final String[] TABS = {"Users", "Pending", "Allowlist"};

	private int tab;
	private int offset;
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int listY;
	private int perPage;
	private long lastRequest;
	private RelayClient.AdminData lastData;
	private EditBox addBox;

	public DevScreen(Screen parent) {
		super("Dev", parent);
	}

	@Override
	protected void init() {
		resetDyn();
		px = 10;
		py = 8;
		pw = this.width - 20;
		ph = this.height - 16;
		listY = py + 76;
		perPage = Math.max(1, (ph - 76 - 32) / (ROW_H + GAP));

		vbtn(px + pw - 62, py + 8, 52, 18, plain("Back"), b -> back());
		int tw = 90;
		for (int i = 0; i < TABS.length; i++) {
			final int idx = i;
			vbtn(px + 10 + i * (tw + 4), py + 30, tw, 18, tab == i ? bold(TABS[i], 0xFFFF55) : col(TABS[i], 0xAAAAAA), b -> {
				tab = idx;
				offset = 0;
				rebuildTabs();
			});
		}
		vbtn(px + pw / 2 - 40, py + ph - 26, 30, 18, plain("<"), b -> page(-perPage));
		vbtn(px + pw / 2 + 10, py + ph - 26, 30, 18, plain(">"), b -> page(perPage));
		RelayClient.adminList();
		lastRequest = System.currentTimeMillis();
		layout();
	}

	/** Re-run init so the tab buttons show which tab is open. */
	private void rebuildTabs() {
		this.clearWidgets();
		this.init();
	}

	private int count() {
		RelayClient.AdminData d = RelayClient.adminData();
		return switch (tab) {
			case 0 -> d.users().size();
			case 1 -> d.pending().size();
			default -> d.allowed().size();
		};
	}

	private void layout() {
		clearDyn();
		addBox = null;
		RelayClient.AdminData d = RelayClient.adminData();
		lastData = d;
		offset = clamp(offset, 0, Math.max(0, count() - perPage));
		int btnX = px + pw - 10 - 80;

		if (tab == 2) {
			addBox = dinput(px + 10, py + 54, 200, 18, "Minecraft name to allow...", s -> { });
			addBox.setMaxLength(16);
			dbtn(px + 214, py + 53, 80, 20, bold("Allow", 0x55FF55), b -> {
				String n = addBox.getValue().trim();
				if (!n.isEmpty()) {
					RelayClient.adminAllow(n);
					addBox.setValue("");
				}
			});
		}

		int end = Math.min(count(), offset + perPage);
		for (int i = offset; i < end; i++) {
			int y = listY + (i - offset) * (ROW_H + GAP);
			switch (tab) {
				case 0 -> {
					final String n = d.users().get(i).name();
					dbtn(btnX, y + 1, 80, ROW_H - 2, col("Kick", 0xFF5555), b -> RelayClient.adminKick(n));
				}
				case 1 -> {
					final String n = d.pending().get(i).name();
					dbtn(btnX, y + 1, 80, ROW_H - 2, bold("Allow", 0x55FF55), b -> RelayClient.adminAllow(n));
				}
				default -> {
					final String n = d.allowed().get(i).name();
					boolean owner = d.allowed().get(i).role().equalsIgnoreCase("owner");
					var b = dbtn(btnX, y + 1, 80, ROW_H - 2, col("Remove", 0xFF5555), bb -> RelayClient.adminRemove(n));
					b.active = !owner;
				}
			}
		}
	}

	private void page(int delta) {
		offset = clamp(offset + delta, 0, Math.max(0, count() - perPage));
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

	private static String ago(long ms) {
		long s = Math.max(0, (System.currentTimeMillis() - ms) / 1000);
		if (s < 60) return s + "s";
		if (s < 3600) return (s / 60) + "m";
		if (s < 86400) return (s / 3600) + "h";
		return (s / 86400) + "d";
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		long now = System.currentTimeMillis();
		if (now - lastRequest > 4000) {
			lastRequest = now;
			RelayClient.adminList();
		}
		if (lastData != RelayClient.adminData()) layout();

		dim(g);
		Theme.colorPanel(g, px, py, pw, ph);
		Theme.text(g, "Dev tab", px + 10, py + 14, 0xFFFF5555);
		if (!RelayClient.adminOk()) {
			Theme.text(g, "Session expired. Go back and log in again.", px + 10, py + 60, Theme.GOLD);
			super.render(g, mx, my, pt);
			return;
		}
		Theme.text(g, "IP addresses are private. Don't share screenshots of this screen.", px + 80, py + 14, Theme.GOLD);

		RelayClient.AdminData d = RelayClient.adminData();
		int end = Math.min(count(), offset + perPage);
		int right = px + pw - 100;
		for (int i = offset; i < end; i++) {
			int y = listY + (i - offset) * (ROW_H + GAP);
			boolean hover = over(px + 8, y, pw - 16, ROW_H, mx, my);
			Theme.rect(g, px + 8, y, pw - 16, ROW_H, hover ? Theme.ROW_HOVER : ((i & 1) == 0 ? Theme.ROW : Theme.ROW_ALT));
			int ty = y + (ROW_H - 8) / 2;
			switch (tab) {
				case 0 -> {
					RelayClient.AdminUser u = d.users().get(i);
					Theme.rect(g, px + 14, y + 8, 5, 5, u.allowed() ? Theme.GOOD : Theme.BAD);
					Theme.text(g, Theme.fit(u.name(), 120), px + 26, ty, u.allowed() ? Theme.TEXT : Theme.BAD);
					Theme.text(g, Theme.fit(u.server(), 150), px + 160, ty, Theme.TEXT_DIM);
					Theme.textRight(g, u.ip() + "   online " + ago(u.since()), right, ty, 0xFF8CCBFF);
				}
				case 1 -> {
					RelayClient.AdminPending u = d.pending().get(i);
					Theme.text(g, Theme.fit(u.name(), 140), px + 16, ty, Theme.GOLD);
					Theme.textRight(g, u.ip() + "   waiting " + ago(u.at()), right, ty, 0xFF8CCBFF);
				}
				default -> {
					RelayClient.AdminMember u = d.allowed().get(i);
					boolean owner = u.role().equalsIgnoreCase("owner");
					Theme.text(g, Theme.fit(u.name(), 180), px + 16, ty, owner ? Theme.GOLD : Theme.TEXT);
					Theme.textRight(g, u.role(), right, ty, owner ? Theme.GOLD : Theme.TEXT_DIM);
				}
			}
		}
		if (count() == 0) {
			String empty = switch (tab) {
				case 0 -> "Nobody connected.";
				case 1 -> "Nobody is waiting for access.";
				default -> "The allowlist is empty.";
			};
			Theme.text(g, empty, px + 16, listY + 6, Theme.TEXT_DIM);
		}
		String foot = count() == 0 ? "0 / 0" : (offset + 1) + "-" + end + " / " + count();
		Theme.textRight(g, foot, px + pw - 12, py + ph - 22, Theme.TEXT_DIM);
		super.render(g, mx, my, pt);
	}
}
