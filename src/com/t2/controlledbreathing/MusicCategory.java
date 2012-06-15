package com.t2.controlledbreathing;

import java.util.Random;

public enum MusicCategory {
	NONE("No Music"),
	PERSONAL_MUSIC("My Music"),
	RANDOM("Random"),
	AMBIENT_EVENINGS("Ambient Evenings", R.raw.ambientevenings),
	EVO_SOLUTION("Evo Solution", R.raw.evosolution),
	OCEAN_MIST("Ocean Mist", R.raw.oceanmist),
	WANING_MOMENTS("Waning Moments", R.raw.waningmoments),
	WATERMARK("Watermark", R.raw.watermark);

	private String mName;
	private long mResourceId;

	public static long getRandomResource() {
		Random rand = new Random();
		MusicCategory[] cats = values();
		MusicCategory cat = null;
		do {
			cat = cats[rand.nextInt(cats.length)];
		} while (cat.getResourceId() == 0);
		return cat.getResourceId();
	}

	private MusicCategory(String name) {
		mName = name;
	}

	private MusicCategory(String name, int resourceId) {
		mName = name;
		mResourceId = resourceId;
	}

	public String getName() {
		return mName;
	}

	public long getResourceId() {
		return mResourceId;
	}

	@Override
	public String toString() {
		return mName;
	}
}