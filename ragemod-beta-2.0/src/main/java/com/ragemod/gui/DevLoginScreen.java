package com.ragemod.gui;

import com.ragemod.net.RelayClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/** Password prompt for the Dev tab. The password is checked by the relay server, it is not stored in the mod. */
public class DevLoginScreen extends RageScreen {
	private int px;
	private int py;
	private int pw;
	private int ph;
	private EditBox box;
	private int fieldX;
	private int fieldY;

	public DevLoginScreen(Screen parent) {
		super("Dev login", parent);
	}

	@Override
	protected void init() {
		resetDyn();
		pw = 280;
		ph = 140;
		px = (this.width - pw) / 2;
		py = (this.height - ph) / 2;
		fieldX = px + 20;
		fieldY = py + 62;
		box = input(fieldX, fieldY, pw - 40, 20, "Password", s -> { });
		// invisible text (black on black): we draw stars ourselves
		box.setTextColor(0xFF000000);
		box.setMaxLength(64);
		vbtn(px + 20, py + ph - 30, 120, 20, bold("Login", 0x55FF55), b -> login());
		vbtn(px + pw - 140, py + ph - 30, 120, 20, plain("Back"), b -> back());
	}

	private void login() {
		RelayClient.auth(box.getValue());
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && box != null && box.isFocused()) {
			login();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		if (RelayClient.adminOk()) {
			Minecraft.getInstance().setScreen(new DevScreen(parent));
			return;
		}
		dim(g);
		Theme.colorPanel(g, px, py, pw, ph);
		Theme.textCentered(g, "Dev tab", px + pw / 2, py + 14, 0xFFFF5555);
		Theme.textCentered(g, "Enter the admin password", px + pw / 2, py + 32, Theme.TEXT_DIM);
		super.render(g, mx, my, pt);
		if (box != null && !box.getValue().isEmpty()) {
			Theme.text(g, "*".repeat(box.getValue().length()), fieldX + 4, fieldY + 6, Theme.TEXT);
		}
		String st = RelayClient.adminStatus();
		if (!st.isEmpty()) Theme.textCentered(g, Theme.fit(st, pw - 20), px + pw / 2, py + 90, st.startsWith("Logged") ? Theme.GOOD : Theme.GOLD);
	}
}
