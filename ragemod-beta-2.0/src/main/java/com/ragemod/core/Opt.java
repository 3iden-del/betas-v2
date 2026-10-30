package com.ragemod.core;

/** One configurable option. */
public final class Opt {
	public enum Kind { BOOL, COLOR, INT, STRING }

	public final String group;
	public final String key;
	public final String desc;
	public final Kind kind;
	public final Object def;
	public final int min;
	public final int max;
	public final int step;
	private Object val;

	Opt(String group, String key, String desc, Kind kind, Object def, int min, int max, int step) {
		this.group = group;
		this.key = key;
		this.desc = desc;
		this.kind = kind;
		this.def = def;
		this.min = min;
		this.max = max;
		this.step = step;
		this.val = def;
	}

	public boolean b() { return (Boolean) val; }
	public int i() { return (Integer) val; }
	public String s() { return (String) val; }
	public int rgb() { return Colors.rgb((String) val); }
	public int argb() { return 0xFF000000 | rgb(); }
	public Object get() { return val; }
	public void reset() { val = def; }

	public void set(Object v) {
		switch (kind) {
			case BOOL -> val = (v instanceof Boolean bo) ? bo : Boolean.parseBoolean(String.valueOf(v));
			case INT -> {
				int n = (v instanceof Number num) ? num.intValue() : parseInt(String.valueOf(v), (Integer) def);
				val = Math.max(min, Math.min(max, n));
			}
			case COLOR -> val = Colors.NAMES[Colors.index(String.valueOf(v))];
			case STRING -> val = String.valueOf(v);
		}
	}

	/** Click action in the config screen. dir = +1 (left click) or -1 (right click). */
	public void cycle(int dir) {
		switch (kind) {
			case BOOL -> val = !b();
			case COLOR -> val = Colors.cycle((String) val, dir);
			case INT -> {
				int n = i() + (dir >= 0 ? step : -step);
				if (n > max) n = min;
				if (n < min) n = max;
				val = n;
			}
			default -> { }
		}
	}

	public String display() {
		return switch (kind) {
			case BOOL -> b() ? "true" : "false";
			case INT -> String.valueOf(i());
			case COLOR, STRING -> String.valueOf(val);
		};
	}

	private static int parseInt(String s, int fallback) {
		try {
			return Integer.parseInt(s.trim());
		} catch (Exception e) {
			return fallback;
		}
	}
}
