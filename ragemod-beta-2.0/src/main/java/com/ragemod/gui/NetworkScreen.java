package com.ragemod.gui;

import com.ragemod.core.Msg;
import com.ragemod.core.WhitelistGate;
import com.ragemod.net.RelayClient;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Online mod users, private chat inside the mod, and the "ask everyone for help" button. */
public class NetworkScreen extends RageScreen {
	private static final int ROW_H = 20;

	private String selected;
	private EditBox input;
	private int lx;
	private int lw;
	private int rx;
	private int rw;
	private int top;
	private int bottom;
	private int perPage;
	private int offset;
	private List<RelayClient.Peer> lastPeers;

	public NetworkScreen(Screen parent, String select) {
		super("Mod Users", parent);
		this.selected = select;
	}

	@Override
	protected void init() {
		resetDyn();
		lx = 10;
		lw = Math.max(150, this.width * 30 / 100);
		rx = lx + lw + 8;
		rw = this.width - rx - 10;
		top = 34;
		bottom = this.height - 10;
		perPage = Math.max(1, (bottom - top - 58) / (ROW_H + 2));

		vbtn(this.width - 64, 8, 54, 20, plain("Back"), b -> back());
		vbtn(lx, 8, 140, 20, bold("ASK FOR HELP", 0xFF5555), b -> askHelp());
		input = input(rx + 8, bottom - 28, rw - 16 - 62, 20, "Message...", s -> { });
		input.setMaxLength(200);
		vbtn(rx + rw - 8 - 56, bottom - 28, 56, 20, bold("Send", 0x55FF55), b -> send());
		layout();
	}

	private void layout() {
		clearDyn();
		lastPeers = RelayClient.peers();
		offset = clamp(offset, 0, Math.max(0, lastPeers.size() - perPage));
		int end = Math.min(lastPeers.size(), offset + perPage);
		for (int i = offset; i < end; i++) {
			final RelayClient.Peer p = lastPeers.get(i);
			int y = top + 44 + (i - offset) * (ROW_H + 2);
			hit(lx + 4, y, lw - 8, ROW_H, b -> {
				selected = p.name();
				RelayClient.markRead(p.name());
				layout();
			});
		}
	}

	private void askHelp() {
		if (!RelayClient.connected()) {
			Msg.say("Not connected to the relay, can't ask for help.");
			return;
		}
		RelayClient.sendHelp("");
		Msg.say("Help request sent to " + RelayClient.peers().size() + " mod user(s).");
	}

	private void send() {
		if (selected == null || input == null) return;
		String t = input.getValue().trim();
		if (t.isEmpty()) return;
		if (!RelayClient.connected()) {
			Msg.say("Not connected to the relay.");
			return;
		}
		RelayClient.sendPm(selected, t);
		input.setValue("");
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if ((event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) && input != null && input.isFocused()) {
			send();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean mouseScrolled(double mx, double my, double sx, double sy) {
		if (sy != 0 && mx < rx) {
			offset = clamp(offset + (sy > 0 ? -2 : 2), 0, Math.max(0, RelayClient.peers().size() - perPage));
			layout();
			return true;
		}
		return super.mouseScrolled(mx, my, sx, sy);
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		if (lastPeers != RelayClient.peers()) layout();

		dim(g);
		Theme.colorPanel(g, lx, top, lw, bottom - top);
		Theme.text(g, "Mod users online (" + RelayClient.peers().size() + ")", lx + 8, top + 10, Theme.GOLD);
		boolean on = RelayClient.connected();
		boolean ok = WhitelistGate.allowed();
		Theme.text(g, "Relay: " + Theme.fit(RelayClient.status(), lw - 60), lx + 8, top + 22, on ? Theme.GOOD : Theme.BAD);
		Theme.text(g, ok ? "Access: allowed" : "Access: locked", lx + 8, top + 33, ok ? Theme.GOOD : Theme.BAD);

		int end = Math.min(lastPeers.size(), offset + perPage);
		for (int i = offset; i < end; i++) {
			RelayClient.Peer p = lastPeers.get(i);
			int y = top + 44 + (i - offset) * (ROW_H + 2);
			boolean sel = p.name().equalsIgnoreCase(selected);
			boolean hover = over(lx + 4, y, lw - 8, ROW_H, mx, my);
			Theme.rect(g, lx + 4, y, lw - 8, ROW_H, sel ? Theme.ROW_SEL : (hover ? Theme.ROW_HOVER : Theme.ROW));
			boolean unread = RelayClient.unread(p.name());
			Theme.rect(g, lx + 9, y + 8, 4, 4, unread ? 0xFFFF5555 : Theme.GOOD);
			Theme.text(g, Theme.fit(p.name() + (unread ? "  *new*" : ""), lw - 40), lx + 18, y + 6, unread ? Theme.GOLD : 0xFF8CCBFF);
		}
		if (lastPeers.isEmpty()) Theme.text(g, "Nobody else online.", lx + 8, top + 50, Theme.TEXT_DIM);

		Theme.colorPanel(g, rx, top, rw, bottom - top);
		if (selected == null) {
			Theme.text(g, "Select a user to chat privately.", rx + 10, top + 12, Theme.TEXT_DIM);
		} else {
			Theme.text(g, "Chat with " + selected + (RelayClient.isOnline(selected) ? "" : " (offline)"), rx + 10, top + 12, Theme.GOLD);
			List<String> wrapped = new ArrayList<>();
			for (String l : new ArrayList<>(RelayClient.convo(selected))) wrapped.addAll(Theme.wrap(l, rw - 24));
			int maxLines = Math.max(1, (bottom - top - 24 - 40) / 11);
			int start = Math.max(0, wrapped.size() - maxLines);
			int y = top + 28;
			for (int i = start; i < wrapped.size(); i++) {
				String l = wrapped.get(i);
				Theme.text(g, l, rx + 10, y, l.startsWith("You:") ? 0xFF8CCBFF : Theme.TEXT);
				y += 11;
			}
			RelayClient.markRead(selected);
		}
		super.render(g, mx, my, pt);
	}
}
