package com.ragemod.core;

/** Decides which color (if any) a player gets for nick tags, tab list and hitboxes. Returns RGB or null. */
public final class Marks {
	private Marks() {}

	private static boolean killer(String name) {
		return Config.HIGHLIGHT_KILLER.b() && KillTracker.isKiller(name);
	}

	/** Nick tag / tab list color. */
	public static Integer nickRgb(String name) {
		if (War.isPriority(name)) return Config.PRIORITY_COLOR.rgb();
		if (killer(name)) return Config.KILLER_COLOR.rgb();
		Status s = StatusStore.status(name);
		if (s != Status.NONE) return s.rgb();
		if (StatusStore.show(name)) return Config.HIGHLIGHT_COLOR.rgb();
		return null;
	}

	/** Hitbox color, honoring the per-group toggles. */
	public static Integer boxRgb(String name) {
		if (!Config.HITBOXES.b()) return null;
		Integer c = null;
		Status s = StatusStore.status(name);
		if (War.isPriority(name) && Config.HB_PRIORITY.b()) {
			c = Config.PRIORITY_COLOR.rgb();
		} else if (killer(name) && Config.HB_KILLER.b()) {
			c = Config.KILLER_COLOR.rgb();
		} else if (s != Status.NONE && boxAllowed(s)) {
			c = s.rgb();
		} else if (StatusStore.show(name) && Config.HB_HIGHLIGHTED.b()) {
			c = Config.HIGHLIGHT_COLOR.rgb();
		}
		if (c != null && !Config.CHANGE_HITBOX.b()) c = Config.HIGHLIGHT_COLOR.rgb();
		return c;
	}

	private static boolean boxAllowed(Status s) {
		return switch (s) {
			case TARGET -> Config.HB_TARGETS.b();
			case FRIEND -> Config.HB_FRIENDS.b();
			case ALLIANCE -> Config.HB_ALLIANCES.b();
			case MUTUAL -> Config.HB_MUTUALS.b();
			case ANNOYANCE -> Config.HB_ANNOYANCES.b();
			case ENEMY -> Config.HB_ENEMIES.b();
			default -> false;
		};
	}
}
