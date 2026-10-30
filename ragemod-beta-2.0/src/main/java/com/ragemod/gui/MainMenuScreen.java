package com.ragemod.gui;

import com.ragemod.core.WhitelistGate;
import com.ragemod.net.RelayClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** "Open a module" menu, with the Dev tab on the side. */
public class MainMenuScreen extends RageScreen {
	private static final String[] NAMES = {"Players", "Player Info", "Stats", "War", "Mod Users", "Clan", "Config"};
	private static final int[] COLORS = {0x55FF55, 0x55FFFF, 0xFFFF55, 0xFF5555, 0xFF55FF, 0xFFAA00, 0xDDDDDD};

	private int px;
	private int py;
	private int pw;
	private int ph;

	public MainMenuScreen() {
		super("RAGE Mod", null);
	}

	@Override
	protected void init() {
		resetDyn();
		pw = 260;
		int rowH = 20;
		int gap = 4;
		ph = 74 + NAMES.length * (rowH + gap) + 8;
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		int y = py + 68;
		for (int i = 0; i < NAMES.length; i++) {
			final String name = NAMES[i];
			vbtn(px + 14, y, pw - 28, rowH, bold(name, COLORS[i]), b -> open(name));
			y += rowH + gap;
		}
		// the Dev tab sits off to the side
		vbtn(px + pw + 6, py + ph / 2 - 12, 46, 24, bold("DEV", 0xFF5555), b -> {
			Minecraft mc = Minecraft.getInstance();
			mc.setScreen(RelayClient.adminOk() ? new DevScreen(this) : new DevLoginScreen(this));
		});
	}

	private void open(String name) {
		Minecraft mc = Minecraft.getInstance();
		switch (name) {
			case "Players" -> mc.setScreen(new PlayersScreen(this));
			case "Player Info" -> mc.setScreen(new PlayerInfoScreen(this, null));
			case "Stats" -> mc.setScreen(new StatsScreen(this, null));
			case "War" -> mc.setScreen(new WarScreen(this));
			case "Mod Users" -> mc.setScreen(new NetworkScreen(this, null));
			case "Clan" -> mc.setScreen(new ClanScreen(this));
			case "Config" -> mc.setScreen(new ConfigScreen(this));
			default -> { }
		}
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		dim(g);
		Theme.colorPanel(g, px, py, pw, ph);
		Emblems.drawNamed(g, "alliance", px + pw / 2 - 16, py + 10, 32);
		Theme.textCentered(g, WhitelistGate.clan().toUpperCase() + " MOD  \u2022  beta 2.0", px + pw / 2, py + 46, Theme.GOLD);
		Theme.textCentered(g, "Open a module", px + pw / 2, py + 57, Theme.TEXT_DIM);
		Theme.text(g, "Dev", px + pw + 18, py + ph / 2 + 16, Theme.TEXT_DIM);
		super.render(g, mx, my, pt);
	}
}
