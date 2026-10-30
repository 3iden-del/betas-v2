package com.ragemod.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/** Base screen: vanilla buttons and text boxes on dark panels, plus simple clickable list rows. */
public abstract class RageScreen extends Screen {
	/** A clickable rectangle (used for list rows). The action receives the mouse button. */
	private record Hit(int x, int y, int w, int h, IntConsumer action) {
		boolean contains(double mx, double my) {
			return mx >= x && mx < x + w && my >= y && my < y + h;
		}
	}

	protected final Screen parent;
	private final List<AbstractWidget> dyn = new ArrayList<>();
	private final List<Hit> hits = new ArrayList<>();

	protected RageScreen(String title, Screen parent) {
		super(Component.literal(title));
		this.parent = parent;
	}

	// ---- widgets ----

	/** A vanilla button that stays until the screen is rebuilt. */
	protected Button vbtn(int x, int y, int w, int h, Component label, Button.OnPress press) {
		return this.addRenderableWidget(Button.builder(label, press).pos(x, y).size(w, h).build());
	}

	/** A vanilla button that is removed by clearDyn() (use for list rows). */
	protected Button dbtn(int x, int y, int w, int h, Component label, Button.OnPress press) {
		Button b = vbtn(x, y, w, h, label, press);
		dyn.add(b);
		return b;
	}

	protected EditBox input(int x, int y, int w, int h, String hint, Consumer<String> onChange) {
		EditBox eb = new EditBox(this.font, x, y, w, h, Component.literal(hint));
		eb.setHint(Component.literal(hint));
		eb.setMaxLength(64);
		eb.setResponder(onChange);
		this.addRenderableWidget(eb);
		return eb;
	}

	/** A text box that is removed by clearDyn(). */
	protected EditBox dinput(int x, int y, int w, int h, String hint, Consumer<String> onChange) {
		EditBox eb = input(x, y, w, h, hint, onChange);
		dyn.add(eb);
		return eb;
	}

	/** Removes all dynamic widgets and clickable rows. */
	protected void clearDyn() {
		for (AbstractWidget w : dyn) this.removeWidget(w);
		dyn.clear();
		hits.clear();
	}

	/** Call at the start of init(): the screen already dropped its widgets, so just forget them. */
	protected void resetDyn() {
		dyn.clear();
		hits.clear();
	}

	protected void hit(int x, int y, int w, int h, IntConsumer action) {
		hits.add(new Hit(x, y, w, h, action));
	}

	// ---- text helpers ----

	protected static Component col(String text, int rgb) {
		return Component.literal(text).withStyle(s -> s.withColor(rgb));
	}

	protected static Component bold(String text, int rgb) {
		return Component.literal(text).withStyle(s -> s.withColor(rgb).withBold(true));
	}

	/** Group button label: bold in the group color when it is the player's group, gray otherwise. */
	protected static Component groupLabel(com.ragemod.core.Status s, String text, boolean active) {
		return active ? bold(text, s.rgb()) : col(text, 0xAAAAAA);
	}

	protected static Component plain(String text) {
		return Component.literal(text);
	}

	protected boolean over(int x, int y, int w, int h, int mx, int my) {
		return mx >= x && mx < x + w && my >= y && my < y + h;
	}

	protected static int clamp(int v, int lo, int hi) {
		return Math.max(lo, Math.min(hi, v));
	}

	protected void dim(GuiGraphics g) {
		g.fill(0, 0, this.width, this.height, Theme.DIM);
	}

	protected void back() {
		Minecraft.getInstance().setScreen(parent);
	}

	// ---- Screen overrides ----

	@Override
	public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
		// we draw our own dim, no vanilla blur
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(parent);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) return true;
		for (int i = hits.size() - 1; i >= 0; i--) {
			Hit h = hits.get(i);
			if (h.contains(event.x(), event.y())) {
				h.action().accept(event.button());
				return true;
			}
		}
		return false;
	}
}
