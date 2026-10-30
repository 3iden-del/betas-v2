package com.ragemod.gui;

import com.ragemod.core.Config;
import com.ragemod.core.Opt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Settings panel: search, color pickers, toggles, numbers and text options. */
public class ConfigScreen extends RageScreen {
	private static final int ROW_H = 32;
	private static final int CTRL_W = 150;
	private static final int[] GROUP_COLORS = {0xFFFF5555, 0xFF55FF55, 0xFF55FFFF, 0xFFFFFF55, 0xFFFF55FF, 0xFFFFAA00, 0xFF8CCBFF, 0xFFFF8888, 0xFFAAFFAA, 0xFFFFD84D};

	private EditBox search;
	private String filter = "";
	private int offset;
	private int px;
	private int py;
	private int pw;
	private int ph;
	private int listY;
	private int perPage;
	private List<Opt> shown = new ArrayList<>();
	private final List<int[]> swatches = new ArrayList<>(); // x, y, argb
	private final Map<String, Integer> groupColor = new LinkedHashMap<>();

	public ConfigScreen(Screen parent) {
		super("Config", parent);
		for (Opt o : Config.ALL) {
			if (!groupColor.containsKey(o.group)) groupColor.put(o.group, GROUP_COLORS[groupColor.size() % GROUP_COLORS.length]);
		}
	}

	@Override
	protected void init() {
		resetDyn();
		px = 10;
		py = 8;
		pw = this.width - 20;
		ph = this.height - 16;
		listY = py + 54;
		perPage = Math.max(1, (ph - 54 - 36) / ROW_H);
		int sw = Math.min(pw - 40, 360);
		search = input(px + (pw - sw) / 2, py + 28, sw, 18, "Search options...", s -> {
			filter = s;
			offset = 0;
			layout();
		});
		search.setValue(filter);

		vbtn(px + pw / 2 - 150, py + ph - 26, 40, 20, plain("<"), b -> page(-perPage));
		vbtn(px + pw / 2 - 106, py + ph - 26, 120, 20, col("Reset to Defaults", 0xFF5555), b -> {
			Config.resetAll();
			layout();
		});
		vbtn(px + pw / 2 + 18, py + ph - 26, 60, 20, bold("Done", 0x55FF55), b -> onClose());
		vbtn(px + pw / 2 + 82, py + ph - 26, 40, 20, plain(">"), b -> page(perPage));
		layout();
	}

	private void layout() {
		clearDyn();
		swatches.clear();
		String f = filter.toLowerCase(Locale.ROOT).trim();
		shown = new ArrayList<>();
		for (Opt o : Config.ALL) {
			if (f.isEmpty() || o.key.toLowerCase(Locale.ROOT).contains(f) || o.desc.toLowerCase(Locale.ROOT).contains(f)
				|| o.group.toLowerCase(Locale.ROOT).contains(f)) {
				shown.add(o);
			}
		}
		offset = clamp(offset, 0, Math.max(0, shown.size() - perPage));

		int end = Math.min(shown.size(), offset + perPage);
		for (int i = offset; i < end; i++) {
			final Opt o = shown.get(i);
			int y = listY + (i - offset) * ROW_H;
			int x = px + pw - 12 - CTRL_W;
			switch (o.kind) {
				case STRING -> {
					EditBox eb = dinput(x, y + 6, CTRL_W, 20, o.key, o::set);
					eb.setMaxLength(120);
					eb.setValue(o.s());
				}
				case BOOL -> dbtn(x, y + 6, CTRL_W, 20, col(o.display(), o.b() ? 0x55FF55 : 0xFF5555), b -> {
					o.cycle(1);
					Config.save();
					layout();
				});
				case COLOR -> {
					dbtn(x, y + 6, 22, 20, plain("<"), b -> {
						o.cycle(-1);
						Config.save();
						layout();
					});
					dbtn(x + 24, y + 6, CTRL_W - 24, 20, Component.literal("    " + o.display()).withStyle(s -> s.withColor(o.rgb())), b -> {
						o.cycle(1);
						Config.save();
						layout();
					});
					swatches.add(new int[] {x + 30, y + 12, o.argb()});
				}
				case INT -> {
					dbtn(x, y + 6, 22, 20, plain("<"), b -> {
						o.cycle(-1);
						Config.save();
						layout();
					});
					dbtn(x + 24, y + 6, CTRL_W - 24, 20, col(o.display(), 0xFFD84D), b -> {
						o.cycle(1);
						Config.save();
						layout();
					});
				}
			}
		}
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
	public void onClose() {
		Config.save();
		super.onClose();
	}

	@Override
	public void render(GuiGraphics g, int mx, int my, float pt) {
		dim(g);
		Theme.colorPanel(g, px, py, pw, ph);
		Theme.textCentered(g, "RAGEMod Config", px + pw / 2, py + 12, Theme.GOLD);

		int end = Math.min(shown.size(), offset + perPage);
		for (int i = offset; i < end; i++) {
			Opt o = shown.get(i);
			int y = listY + (i - offset) * ROW_H;
			int gc = groupColor.getOrDefault(o.group, 0xFFFFFFFF);
			Theme.rect(g, px + 8, y + 1, pw - 16, ROW_H - 3, (i & 1) == 0 ? Theme.ROW : Theme.ROW_ALT);
			Theme.rect(g, px + 8, y + 1, 3, ROW_H - 3, gc);
			Theme.text(g, o.key, px + 16, y + 6, Theme.TEXT);
			Theme.text(g, o.group, px + 16 + Theme.width(o.key) + 8, y + 6, gc);
			Theme.text(g, Theme.fit(o.desc, pw - CTRL_W - 60), px + 16, y + 18, Theme.TEXT_DIM);
		}
		if (shown.isEmpty()) Theme.text(g, "No options match.", px + 16, listY + 6, Theme.TEXT_DIM);

		String foot = shown.isEmpty() ? "0 / 0" : (offset + 1) + "-" + end + " / " + shown.size();
		Theme.textRight(g, foot, px + pw - 12, py + ph - 20, Theme.TEXT_DIM);
		super.render(g, mx, my, pt);
		for (int[] s : swatches) {
			Theme.rect(g, s[0], s[1], 8, 8, s[2]);
			Theme.border(g, s[0], s[1], 8, 8, 0xFF000000);
		}
	}
}
