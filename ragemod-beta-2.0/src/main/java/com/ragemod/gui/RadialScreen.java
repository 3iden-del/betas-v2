package com.ragemod.gui;

import com.ragemod.core.Config;
import com.ragemod.core.Status;
import com.ragemod.core.StatusStore;
import com.ragemod.core.War;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.AbstractClientPlayer;

/** Middle-click menu on a player: Target, Friend, Alliance, Mutual, Annoyance, Info, Stats, Show, Ignore, Remove. */
public class RadialScreen extends RageScreen {
	private static final String[] LABELS = {"Target", "Friend", "Alliance", "Mutual", "Annoyance", "Info", "Stats", "Show", "Ignore", "Remove"};

	private final AbstractClientPlayer target;
	private final String name;
	private int cx;
	private int cy;
	private int radius;

	public RadialScreen(AbstractClientPlayer target) {
		super("Radial", null);
		this.target = target;
		this.name = target.getName().getString();
	}

	@Override
	protected void init() {
		resetDyn();
		radius = Config.RADIAL_SIZE.i();
		radius = Math.min(radius, Math.min(this.width, this.height) / 2 - 6);
		cx = this.width / 2;
		cy = this.height / 2;
		double ring = radius * 0.80;
		int bw = (int) Math.max(48, Math.min(72, ring * 0.62));
		int bh = 20;
		for (int i = 0; i < LABELS.length; i++) {
			final String label = LABELS[i];
			double ang = Math.toRadians(i * 360.0 / LABELS.length);
			int bx = cx + (int) Math.round(Math.sin(ang) * ring) - bw / 2;
			int by = cy - (int) Math.round(Math.cos(ang) * ring) - bh / 2;
			vbtn(bx, by, bw, bh, labelFor(label), b -> act(label));
		}
	}

	private net.minecraft.network.chat.Component labelFor(String label) {
		Status s = switch (label) {
			case "Target" -> Status.TARGET;
			case "Friend" -> Status.FRIEND;
			case "Alliance" -> Status.ALLIANCE;
			case "Mutual" -> Status.MUTUAL;
			case "Annoyance" -> Status.ANNOYANCE;
			default -> Status.NONE;
		};
		if (s != Status.NONE) return groupLabel(s, label, StatusStore.status(name) == s);
		return switch (label) {
			case "Info" -> col(label, 0x55FFFF);
			case "Stats" -> col(label, 0xFFFF55);
			case "Show" -> StatusStore.show(name) ? bold(label, 0xFFFFFF) : col(label, 0xAAAAAA);
			case "Ignore" -> StatusStore.ignored(name) ? bold(label, 0xFF5555) : col(label, 0xAAAAAA);
			default -> col(label, 0xFF8888);
		};
	}

	private void act(String label) {
		Minecraft mc = Minecraft.getInstance();
		switch (label) {
			case "Target" -> StatusStore.toggle(name, Status.TARGET);
			case "Friend" -> StatusStore.toggle(name, Status.FRIEND);
			case "Alliance" -> StatusStore.toggle(name, Status.ALLIANCE);
			case "Mutual" -> StatusStore.toggle(name, Status.MUTUAL);
			case "Annoyance" -> StatusStore.toggle(name, Status.ANNOYANCE);
			case "Show" -> StatusStore.toggleShow(name);
			case "Ignore" -> StatusStore.toggleIgnore(name);
			case "Remove" -> StatusStore.clear(name);
			case "Info" -> {
				mc.setScreen(new PlayerInfoScreen(null, name));
				return;
			}
			case "Stats" -> {
				mc.setScreen(new StatsScreen(null, name));
				return;
			}
			default -> { }
		}
		mc.setScreen(null);
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		boolean handled = super.mouseClicked(event, doubleClick);
		if (handled) return true;
		// click outside the circle closes the menu
		double dx = event.x() - cx;
		double dy = event.y() - cy;
		if (dx * dx + dy * dy > (double) radius * radius) {
			Minecraft.getInstance().setScreen(null);
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		Minecraft.getInstance().setScreen(null);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		g.fill(0, 0, this.width, this.height, 0x60000000);
		Status s = StatusStore.status(name);
		int ringColor = War.isPriority(name) ? Config.PRIORITY_COLOR.argb() : (s == Status.NONE ? 0xFF6A6A7A : s.argb());
		Theme.circle(g, cx, cy, radius, 0xC0101014);
		Theme.circle(g, cx, cy, radius - 2, 0xC01A1A22);
		int inner = (int) (radius * 0.34);
		Theme.circle(g, cx, cy, inner + 2, ringColor);
		Theme.circle(g, cx, cy, inner, 0xFF22222C);

		int head = 32;
		drawHead(g, cx - head / 2, cy - head - 2, head);
		Theme.textCentered(g, name, cx, cy + 8, Theme.TEXT);
		if (s != Status.NONE) {
			Emblems.draw(g, s, cx - 8, cy + 18, 16);
		} else {
			Theme.textCentered(g, "No group", cx, cy + 20, Theme.TEXT_DIM);
		}
		super.render(g, mx, my, pt);
	}

	private void drawHead(GuiGraphics g, int x, int y, int size) {
		Minecraft mc = Minecraft.getInstance();
		PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(target.getUUID());
		if (info != null) {
			PlayerFaceRenderer.draw(g, info.getSkin(), x, y, size);
		} else {
			g.fill(x, y, x + size, y + size, 0xFF3A3A48);
		}
	}
}
