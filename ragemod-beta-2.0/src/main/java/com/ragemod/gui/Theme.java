package com.ragemod.gui;

import com.ragemod.core.Status;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/** Dark vanilla-style panels with colorful group accents. All colors are ARGB. */
public final class Theme {
	public static final int DIM = 0x98000000;
	public static final int PANEL = 0xE0141418;
	public static final int PANEL_EDGE = 0xFF5C5C68;
	public static final int ROW = 0xFF26262E;
	public static final int ROW_ALT = 0xFF1F1F27;
	public static final int ROW_HOVER = 0xFF3A3A48;
	public static final int ROW_SEL = 0xFF48486E;
	public static final int TEXT = 0xFFFFFFFF;
	public static final int TEXT_DIM = 0xFFA8A8B0;
	public static final int GOOD = 0xFF55FF55;
	public static final int BAD = 0xFFFF5555;
	public static final int GOLD = 0xFFFFD84D;

	private Theme() {}

	public static void rect(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + h, color);
	}

	public static void border(GuiGraphics g, int x, int y, int w, int h, int color) {
		g.fill(x, y, x + w, y + 1, color);
		g.fill(x, y + h - 1, x + w, y + h, color);
		g.fill(x, y, x + 1, y + h, color);
		g.fill(x + w - 1, y, x + w, y + h, color);
	}

	public static void panel(GuiGraphics g, int x, int y, int w, int h) {
		rect(g, x, y, w, h, PANEL);
		border(g, x, y, w, h, PANEL_EDGE);
	}

	/** Panel with a colored header strip made of all group colors. */
	public static void colorPanel(GuiGraphics g, int x, int y, int w, int h) {
		panel(g, x, y, w, h);
		groupBar(g, x + 1, y + 1, w - 2, 3);
	}

	/** A thin bar split into the six group colors. */
	public static void groupBar(GuiGraphics g, int x, int y, int w, int h) {
		Status[] gs = Status.GROUPS;
		int seg = Math.max(1, w / gs.length);
		for (int i = 0; i < gs.length; i++) {
			int sx = x + i * seg;
			int sw = (i == gs.length - 1) ? (x + w - sx) : seg;
			g.fill(sx, y, sx + sw, y + h, gs[i].argb());
		}
	}

	public static void circle(GuiGraphics g, int cx, int cy, int r, int color) {
		for (int dy = -r; dy <= r; dy++) {
			int dx = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
			g.fill(cx - dx, cy + dy, cx + dx, cy + dy + 1, color);
		}
	}

	public static void text(GuiGraphics g, String s, int x, int y, int color) {
		g.drawString(Minecraft.getInstance().font, s, x, y, color);
	}

	public static void textCentered(GuiGraphics g, String s, int cx, int y, int color) {
		g.drawCenteredString(Minecraft.getInstance().font, s, cx, y, color);
	}

	public static void textRight(GuiGraphics g, String s, int rightX, int y, int color) {
		Minecraft mc = Minecraft.getInstance();
		g.drawString(mc.font, s, rightX - mc.font.width(s), y, color);
	}

	public static int width(String s) {
		return Minecraft.getInstance().font.width(s);
	}

	/** Cuts a string with "..." so it fits in maxW pixels. */
	public static String fit(String s, int maxW) {
		if (width(s) <= maxW) return s;
		String t = s;
		while (t.length() > 1 && width(t + "...") > maxW) t = t.substring(0, t.length() - 1);
		return t + "...";
	}

	/** Word-wraps text to maxW pixels (long words are cut). */
	public static List<String> wrap(String text, int maxW) {
		List<String> out = new ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			while (width(word) > maxW && word.length() > 1) {
				int cut = word.length();
				while (cut > 1 && width(word.substring(0, cut)) > maxW) cut--;
				if (line.length() > 0) {
					out.add(line.toString());
					line = new StringBuilder();
				}
				out.add(word.substring(0, cut));
				word = word.substring(cut);
			}
			String test = line.length() == 0 ? word : line + " " + word;
			if (width(test) > maxW && line.length() > 0) {
				out.add(line.toString());
				line = new StringBuilder(word);
			} else {
				line = new StringBuilder(test);
			}
		}
		if (line.length() > 0) out.add(line.toString());
		return out;
	}

	/** Small red cross icon (used for war). */
	public static void warIcon(GuiGraphics g, int x, int y, int size, int color) {
		int t = Math.max(2, size / 3);
		int o = (size - t) / 2;
		g.fill(x + o, y, x + o + t, y + size, color);
		g.fill(x, y + o, x + size, y + o + t, color);
	}
}
