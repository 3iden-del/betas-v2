package com.ragemod.gui;

import com.ragemod.core.Lobby;
import com.ragemod.core.WhitelistGate;
import com.ragemod.net.RelayClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;

import java.util.ArrayList;
import java.util.List;

/** Clan members (everyone on the allowlist) with an online dot. */
public class ClanScreen extends RageScreen {
	private static final int ROW_H = 22;
	private static final int GAP = 3;

	private int offset;
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int listY;
	private int perPage;
	private List<WhitelistGate.Member> members = new ArrayList<>();

	public ClanScreen(Screen parent) {
		super("Clan", parent);
	}

	@Override
	protected void init() {
		resetDyn();
		px = 10;
		py = 8;
		pw = this.width - 20;
		ph = this.height - 16;
		listY = py + 36;
		perPage = Math.max(1, (ph - 36 - 32) / (ROW_H + GAP));
		vbtn(px + pw - 62, py + 8, 52, 18, plain("Back"), b -> back());
		vbtn(px + pw / 2 - 40, py + ph - 26, 30, 18, plain("<"), b -> page(-perPage));
		vbtn(px + pw / 2 + 10, py + ph - 26, 30, 18, plain(">"), b -> page(perPage));
		layout();
	}

	private boolean online(String name) {
		return RelayClient.isOnline(name) || Lobby.contains(name) || name.equalsIgnoreCase(com.ragemod.core.Msg.me());
	}

	private void layout() {
		clearDyn();
		members = new ArrayList<>(WhitelistGate.members());
		members.sort((a, b) -> {
			boolean oa = online(a.name());
			boolean ob = online(b.name());
			if (oa != ob) return oa ? -1 : 1;
			return a.name().compareToIgnoreCase(b.name());
		});
		offset = clamp(offset, 0, Math.max(0, members.size() - perPage));

		int end = Math.min(members.size(), offset + perPage);
		for (int i = offset; i < end; i++) {
			final String name = members.get(i).name();
			int y = listY + (i - offset) * (ROW_H + GAP);
			if (RelayClient.isOnline(name)) {
				dbtn(px + pw - 10 - 80, y + 1, 80, ROW_H - 2, col("Message", 0x55FFFF),
					b -> Minecraft.getInstance().setScreen(new NetworkScreen(this, name)));
			}
		}
	}

	private void page(int delta) {
		offset = clamp(offset + delta, 0, Math.max(0, members.size() - perPage));
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
		int on = 0;
		for (WhitelistGate.Member m : members) {
			if (online(m.name())) on++;
		}
		Theme.text(g, WhitelistGate.clan() + " clan members  (" + on + " online / " + members.size() + ")", px + 10, py + 14, Theme.GOLD);

		int end = Math.min(members.size(), offset + perPage);
		for (int i = offset; i < end; i++) {
			WhitelistGate.Member m = members.get(i);
			int y = listY + (i - offset) * (ROW_H + GAP);
			boolean hover = over(px + 8, y, pw - 16, ROW_H, mx, my);
			Theme.rect(g, px + 8, y, pw - 16, ROW_H, hover ? Theme.ROW_HOVER : ((i & 1) == 0 ? Theme.ROW : Theme.ROW_ALT));
			boolean on2 = online(m.name());
			boolean owner = m.role().equalsIgnoreCase("owner");
			Theme.rect(g, px + 16, y + (ROW_H - 6) / 2, 6, 6, on2 ? Theme.GOOD : 0xFF5A3A3C);
			if (owner) Emblems.drawNamed(g, "alliance", px + 28, y + 3, 16);
			int nameX = owner ? px + 48 : px + 30;
			Theme.text(g, m.name(), nameX, y + (ROW_H - 8) / 2, on2 ? Theme.TEXT : Theme.TEXT_DIM);
			Theme.text(g, m.role(), nameX + Theme.width(m.name()) + 10, y + (ROW_H - 8) / 2, owner ? Theme.GOLD : Theme.TEXT_DIM);
		}
		if (members.isEmpty()) Theme.text(g, "No members loaded yet (waiting for the relay).", px + 16, listY + 6, Theme.TEXT_DIM);
		String foot = members.isEmpty() ? "0 / 0" : (offset + 1) + "-" + end + " / " + members.size();
		Theme.textRight(g, foot, px + pw - 12, py + ph - 22, Theme.TEXT_DIM);
		super.render(g, mx, my, pt);
	}
}
