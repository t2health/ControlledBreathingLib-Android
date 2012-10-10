/*
 * 
 * ControlledBreathingLib
 * 
 * Copyright © 2009-2012 United States Government as represented by 
 * the Chief Information Officer of the National Center for Telehealth 
 * and Technology. All Rights Reserved.
 * 
 * Copyright © 2009-2012 Contributors. All Rights Reserved. 
 * 
 * THIS OPEN SOURCE AGREEMENT ("AGREEMENT") DEFINES THE RIGHTS OF USE, 
 * REPRODUCTION, DISTRIBUTION, MODIFICATION AND REDISTRIBUTION OF CERTAIN 
 * COMPUTER SOFTWARE ORIGINALLY RELEASED BY THE UNITED STATES GOVERNMENT 
 * AS REPRESENTED BY THE GOVERNMENT AGENCY LISTED BELOW ("GOVERNMENT AGENCY"). 
 * THE UNITED STATES GOVERNMENT, AS REPRESENTED BY GOVERNMENT AGENCY, IS AN 
 * INTENDED THIRD-PARTY BENEFICIARY OF ALL SUBSEQUENT DISTRIBUTIONS OR 
 * REDISTRIBUTIONS OF THE SUBJECT SOFTWARE. ANYONE WHO USES, REPRODUCES, 
 * DISTRIBUTES, MODIFIES OR REDISTRIBUTES THE SUBJECT SOFTWARE, AS DEFINED 
 * HEREIN, OR ANY PART THEREOF, IS, BY THAT ACTION, ACCEPTING IN FULL THE 
 * RESPONSIBILITIES AND OBLIGATIONS CONTAINED IN THIS AGREEMENT.
 * 
 * Government Agency: The National Center for Telehealth and Technology
 * Government Agency Original Software Designation: ControlledBreathingLib001
 * Government Agency Original Software Title: ControlledBreathingLib
 * User Registration Requested. Please send email 
 * with your contact information to: robert.kayl2@us.army.mil
 * Government Agency Point of Contact for Original Software: robert.kayl2@us.army.mil
 * 
 */
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