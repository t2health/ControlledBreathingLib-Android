package com.t2.controlledbreathing;

public enum BackgroundCategory {
	NONE("No Background"),
	PERSONAL_IMAGES("My Images"),
	RAINFORESTS("Rainforests", R.drawable.rainforest1, R.drawable.rainforest2, R.drawable.rainforest3, R.drawable.rainforest4,
			R.drawable.rainforest5, R.drawable.rainforest6, R.drawable.rainforest7, R.drawable.rainforest8),
	BEACHES("Beaches", R.drawable.beach1, R.drawable.beach2, R.drawable.beach3, R.drawable.beach4, R.drawable.beach5,
			R.drawable.beach6, R.drawable.beach7, R.drawable.beach8, R.drawable.beach9, R.drawable.beach10, R.drawable.beach11,
			R.drawable.beach12);

	private String mName;
	private int[] mResources;

	private BackgroundCategory(String name, int... resources) {
		mName = name;
		mResources = resources;
	}

	public int[] getResources() {
		return mResources;
	}

	@Override
	public String toString() {
		return mName;
	}
}