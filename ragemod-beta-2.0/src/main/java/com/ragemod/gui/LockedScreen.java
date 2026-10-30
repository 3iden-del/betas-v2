package com.ragemod.gui;

import com.ragemod.core.Msg;
import com.ragemod.core.WhitelistGate;
import com.ragemod.net.RelayClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/** Shown when you are not on the allowlist. Admins can log in from here. */
public class LockedScreen extends RageScreen {
	private int px;
	private int py;
	private int pw;
	private int ph;

	public LockedScreen() {
		super("Locked", null);
	}

	@Override
	protected void init() {
		resetDyn();
		pw = 300;
		ph = 150;
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		vbtn(px + 20, py + ph - 52, pw - 40, 20, bold("Admin login (Dev)", 0xFF5555),
			b -> Minecraft.getInstance().setScreen(new DevLoginScreen(this)));
		vbtn(px + 20, py + ph - 28, pw - 40, 20, plain("Close"), b -> onClose());
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		dim(g);
		Theme.colorPanel(g, px, py, pw, ph);
		Emblems.drawNamed(g, "alliance", px + pw / 2 - 16, py + 10, 32);
		Theme.textCentered(g, "RAGE Mod is locked", px + pw / 2, py + 46, Theme.BAD);
		String why = WhitelistGate.reason().isEmpty() ? "Waiting for the relay (" + RelayClient.status() + ")..." : WhitelistGate.reason();
		Theme.textCentered(g, Theme.fit(why, pw - 20), px + pw / 2, py + 60, Theme.TEXT_DIM);
		Theme.textCentered(g, "Your name: " + Msg.me(), px + pw / 2, py + 76, Theme.TEXT);
		Theme.textCentered(g, "Give it to a RAGE admin to get access.", px + pw / 2, py + 88, Theme.TEXT_DIM);
		super.render(g, mx, my, pt);
	}
}
