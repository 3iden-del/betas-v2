package com.ragemod.core;

/** The group a player can be put in. */
public enum Status {
	NONE("None", "none"),
	TARGET("Target", "target"),
	FRIEND("Friend", "friend"),
	ALLIANCE("Alliance", "alliance"),
	MUTUAL("Mutual", "mutual"),
	ANNOYANCE("Annoyance", "annoyance"),
	ENEMY("Enemy", "enemy");

	public final String label;
	/** File name of the emblem texture (without size suffix). */
	public final String emblem;

	Status(String label, String emblem) {
		this.label = label;
		this.emblem = emblem;
	}

	/** The groups shown as buttons, in display order. */
	public static final Status[] GROUPS = {TARGET, FRIEND, ALLIANCE, MUTUAL, ANNOYANCE, ENEMY};

	/** RGB color from the config for this group (white for NONE). */
	public int rgb() {
		return switch (this) {
			case TARGET -> Config.TARGET_COLOR.rgb();
			case FRIEND -> Config.FRIEND_COLOR.rgb();
			case ALLIANCE -> Config.ALLIANCE_COLOR.rgb();
			case MUTUAL -> Config.MUTUAL_COLOR.rgb();
			case ANNOYANCE -> Config.ANNOYANCE_COLOR.rgb();
			case ENEMY -> Config.ENEMY_COLOR.rgb();
			default -> 0xFFFFFF;
		};
	}

	public int argb() {
		return 0xFF000000 | rgb();
	}
}
