package com.ragemod.gui;

import com.ragemod.core.Status;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Group emblems: crossed swords (target), heart (friend), the RAGE skull (alliance), linked rings (mutual),
 * warning sign (annoyance), war cross (enemy) and a star (top priority). Textures come in 16 and 32 px.
 */
public final class Emblems {
	private static final Map<String, Identifier> CACHE = new HashMap<>();

	private Emblems() {}

	private static Identifier id(String name, int size) {
		String key = name + "_" + size;
		Identifier existing = CACHE.get(key);
		if (existing != null) return existing;
		Identifier created = Identifier.fromNamespaceAndPath("ragemod", "textures/gui/emblems/" + key + ".png");
		CACHE.put(key, created);
		return created;
	}

	/** size must be 16 or 32 (alliance also has 64). */
	public static void draw(GuiGraphics g, Status s, int x, int y, int size) {
		if (s == null || s == Status.NONE) return;
		drawNamed(g, s.emblem, x, y, size);
	}

	public static void drawPriority(GuiGraphics g, int x, int y, int size) {
		drawNamed(g, "priority", x, y, size);
	}

	public static void drawNamed(GuiGraphics g, String name, int x, int y, int size) {
		g.blit(RenderPipelines.GUI_TEXTURED, id(name, size), x, y, 0.0F, 0.0F, size, size, size, size);
	}
}
