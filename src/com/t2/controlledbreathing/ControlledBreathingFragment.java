package com.t2.controlledbreathing;

import java.io.IOException;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Random;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnErrorListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnSeekCompleteListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.text.format.DateUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.widget.TextView;
import android.widget.Toast;

import com.t2.controlledbreathing.ControlledBreathingBackgroundView.OnBackgroundChangedListener;
import com.t2.controlledbreathing.ControlledBreathingBarView.OnControlledBreathingEventListener;

public class ControlledBreathingFragment extends Fragment implements OnControlledBreathingEventListener, OnClickListener,
		LoaderCallbacks<Bitmap>, OnBackgroundChangedListener, OnPreparedListener, OnSeekCompleteListener, OnCompletionListener {

	private static final int LOADER_BACKGROUND = 1;

	private final DecimalFormat mFormatter = new DecimalFormat("#0.0' s'");

	private Animation mInhaleMessageAnimation;
	private Animation mExhaleMessageAnimation;
	private Animation mHoldMessageAnimation;

	private boolean mStarted;
	private boolean mStarting;
	private boolean mInhaling;
	private boolean mFinished;
	private boolean mFinishStarted;

	private CountDownTimer mStartTimer;
	private MediaPlayer mPlayer;
	private MediaPlayer mMusicPlayer;

	private long mInhaleDuration;
	private long mExhaleDuration;
	private long mHoldDuration;

	private int mSoundPhase;
	private boolean mPromptsEnabled;
	private boolean mMusicEnabled;

	private Handler mSessionHandler;
	private Runnable mSessionTimer;
	// private long mSessionStartTime;
	private long mSessionDuration;

	private Toast mDurationToast;
	private long mToastShownTime;

	private Uri mMusicUri;
	private int mMusicPosition;

	private static final int[] INHALE_AUDIO = {
			R.raw.breathing_inhale_1, R.raw.breathing_inhale_2,
			R.raw.breathing_inhale_3, R.raw.breathing_inhale_4 };
	private static final int[] EXHALE_AUDIO = {
			R.raw.breathing_exhale_1, R.raw.breathing_exhale_2,
			R.raw.breathing_exhale_3, R.raw.breathing_exhale_4 };
	private static final int[] MISC_AUDIO = {
			R.raw.breathing_misc_relax_1, R.raw.breathing_misc_relax_2,
			R.raw.breathing_misc_relax_3, R.raw.breathing_misc_relax_4,
			R.raw.breathing_misc_focus, R.raw.breathing_misc_naturally,
			R.raw.breathing_misc_rythmic, R.raw.breathing_misc_smooth };

	public ControlledBreathingBarView getBarView() {
		return (ControlledBreathingBarView) getView().findViewById(R.id.bar);
	}

	public TextView getExhaleTextView() {
		return (TextView) getView().findViewById(R.id.lbl_exhale);
	}

	public TextView getHoldTextView() {
		return (TextView) getView().findViewById(R.id.lbl_hold);
	}

	public TextView getInhaleTextView() {
		return (TextView) getView().findViewById(R.id.lbl_inhale);
	}

	public TextView getMessageTextView() {
		return (TextView) getView().findViewById(R.id.lbl_message);
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		getView().findViewById(R.id.lay_breathing_text).setOnClickListener(this);
		getView().findViewById(R.id.btn_add_time).setOnClickListener(this);
		getView().findViewById(R.id.btn_remove_time).setOnClickListener(this);
		getBarView().setOnControlledBreathingEventListener(this);
		getBackgroundView().setOnBackgroundChangedListener(this);
		updateDurationDescriptions();

		if (mStarted) {
			getMessageTextView().setVisibility(View.INVISIBLE);
			getInhaleTextView().setVisibility(View.INVISIBLE);
			getExhaleTextView().setText("Exhale");
			getHoldTextView().setVisibility(View.INVISIBLE);
			getExhaleTextView().setVisibility(View.INVISIBLE);
		}

		if (mFinished) {
			getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
			getView().findViewById(R.id.lay_breathing).setVisibility(View.INVISIBLE);
			getBackgroundView().setVisibility(View.INVISIBLE);
			getView().findViewById(R.id.lbl_complete).setVisibility(View.VISIBLE);
		}
	}

	public void onBackgroundChanged() {
		getLoaderManager().restartLoader(LOADER_BACKGROUND, null, this);
		getLoaderManager().getLoader(LOADER_BACKGROUND).forceLoad();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.view.View.OnClickListener#onClick(android.view.View)
	 */
	public void onClick(View v) {
		if (v.getId() == R.id.lay_breathing_text) {
			if (mFinished) {
				stopMusic();
				stopPrompt();
				getActivity().finish();
				return;
			}

			if (!mStarting && !mStarted) {
				getExhaleTextView().setText("Alright.");
				getExhaleTextView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
				Animation anim = AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_out);
				anim.setDuration(4000);
				anim.setFillAfter(true);
				getMessageTextView().startAnimation(anim);
				mStartTimer.start();
				mStarting = true;
			}
		} else if (v.getId() == R.id.btn_add_time) {
			if (mInhaling) {
				mInhaleDuration += 200;
				PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
						.putLong(getString(R.string.pref_inhale_duration), mInhaleDuration).commit();
				showDurationToast("Inhale duration - " + mFormatter.format(mInhaleDuration / 1000.0));
			} else {
				mExhaleDuration += 200;
				PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
						.putLong(getString(R.string.pref_exhale_duration), mExhaleDuration).commit();
				showDurationToast("Exhale duration - " + mFormatter.format(mExhaleDuration / 1000.0));
			}
			setTextFadeDuration();
		} else if (v.getId() == R.id.btn_remove_time) {
			if (mInhaling && mInhaleDuration > 1200) {
				mInhaleDuration -= 200;
				PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
						.putLong(getString(R.string.pref_inhale_duration), mInhaleDuration).commit();
				showDurationToast("Inhale duration - " + mFormatter.format(mInhaleDuration / 1000.0));
			} else if (mExhaleDuration > 1200) {
				mExhaleDuration -= 200;
				PreferenceManager.getDefaultSharedPreferences(getActivity()).edit()
						.putLong(getString(R.string.pref_exhale_duration), mExhaleDuration).commit();
				showDurationToast("Exhale duration - " + mFormatter.format(mExhaleDuration / 1000.0));
			}
			setTextFadeDuration();
		}

		if (!mStarted) {
			getBarView().invalidate();
		}

	}

	private void showDurationToast(String text) {
		mDurationToast.setText(text);
		mDurationToast.show();
		// if (System.currentTimeMillis() - mToastShownTime > 2000) {
		// mDurationToast.show();
		// mToastShownTime = System.currentTimeMillis();
		// }
	}

	public void onCompletion(MediaPlayer mp) {
		mMusicUri = null;
		mp.reset();
		startMusic();
	}

	private void updateDurationDescriptions() {
		getView().findViewById(R.id.btn_add_time).setContentDescription(
				mInhaling ? "Increase inhale duration" : "Increase exhale duration");
		getView().findViewById(R.id.btn_remove_time).setContentDescription(
				mInhaling ? "Decrease inhale duration" : "Decrease exhale duration");
	}

	/**
	 * ControlledBreathingBarView sends events at various stages in its journey
	 * up and down. These events are used to sync up audio and visual elements
	 * of the activity with the state of the bar.
	 */
	public void OnControlledBreathingEvent(ControlledBreathingEvent event, long duration) {
		if (!mStarted) {
			mStarted = true;
			startMusic();
			getExhaleTextView().startAnimation(((AnimationSet) mExhaleMessageAnimation).getAnimations().get(1));
			return;
		}

		Random rand = new Random();
		long fadeOutDuration = duration - 400;
		switch (event) {
		case EXHALE_START:
			mInhaling = false;
			updateDurationDescriptions();
			getExhaleTextView().setVisibility(View.VISIBLE);
			if (fadeOutDuration > 0) {
				List<Animation> animations = ((AnimationSet) mExhaleMessageAnimation).getAnimations();
				animations.get(0).setDuration(400);
				animations.get(1).setDuration(fadeOutDuration);
				getExhaleTextView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
				getExhaleTextView().startAnimation(mExhaleMessageAnimation);
			}
			startPrompt(EXHALE_AUDIO[rand.nextInt(EXHALE_AUDIO.length)]);
			startMusic();
			break;
		case EXHALE_RESUME:
			mInhaling = false;
			updateDurationDescriptions();
			getExhaleTextView().setVisibility(View.VISIBLE);
			if (fadeOutDuration > 0) {
				List<Animation> animations = ((AnimationSet) mExhaleMessageAnimation).getAnimations();
				animations.get(0).setDuration(0);
				animations.get(1).setDuration(duration);
				getExhaleTextView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
				getExhaleTextView().startAnimation(mExhaleMessageAnimation);
			}
			startMusic();
			break;
		case INHALE_START:
			mInhaling = true;
			updateDurationDescriptions();
			getInhaleTextView().setVisibility(View.VISIBLE);
			if (fadeOutDuration > 0) {
				List<Animation> animations = ((AnimationSet) mInhaleMessageAnimation).getAnimations();
				animations.get(0).setDuration(400);
				animations.get(1).setDuration(fadeOutDuration);
				getInhaleTextView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
				getInhaleTextView().startAnimation(mInhaleMessageAnimation);
			}
			startPrompt(INHALE_AUDIO[rand.nextInt(INHALE_AUDIO.length)]);
			startMusic();
			break;
		case INHALE_RESUME:
			mInhaling = true;
			updateDurationDescriptions();
			getInhaleTextView().setVisibility(View.VISIBLE);
			if (fadeOutDuration > 0) {
				List<Animation> animations = ((AnimationSet) mInhaleMessageAnimation).getAnimations();
				animations.get(0).setDuration(0);
				animations.get(1).setDuration(duration);
				getInhaleTextView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
				getInhaleTextView().startAnimation(mInhaleMessageAnimation);
			}
			startMusic();
			break;
		case EXHALE_HALF:
		case INHALE_HALF:
			startPrompt(MISC_AUDIO[rand.nextInt(MISC_AUDIO.length)]);
			break;
		case EXHALE_END:
			getExhaleTextView().setVisibility(View.INVISIBLE);
			mExhaleMessageAnimation.reset();
			break;
		case INHALE_END:
			getInhaleTextView().setVisibility(View.INVISIBLE);
			mInhaleMessageAnimation.reset();
			break;
		case HOLD_END:
			mHoldMessageAnimation.reset();
			getHoldTextView().setVisibility(View.INVISIBLE);
			break;
		case HOLD_START:
			getHoldTextView().setVisibility(View.VISIBLE);
			if (fadeOutDuration > 0) {
				List<Animation> animations = ((AnimationSet) mHoldMessageAnimation).getAnimations();
				animations.get(0).setDuration(400);
				animations.get(1).setDuration(fadeOutDuration);
				getHoldTextView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
				getHoldTextView().startAnimation(mHoldMessageAnimation);
			}
			break;
		case HOLD_RESUME:
			getHoldTextView().setVisibility(View.VISIBLE);
			if (fadeOutDuration > 0) {
				List<Animation> animations = ((AnimationSet) mHoldMessageAnimation).getAnimations();
				animations.get(0).setDuration(0);
				animations.get(1).setDuration(duration);
				getHoldTextView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
				getHoldTextView().startAnimation(mHoldMessageAnimation);
			}
			break;

		}
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
		mInhaleDuration = prefs.getLong(getString(R.string.pref_inhale_duration), 7000);
		mExhaleDuration = prefs.getLong(getString(R.string.pref_exhale_duration), 7000);
		mHoldDuration = Long.valueOf(prefs.getString(getString(R.string.pref_hold_duration), "2000"));
		mSessionDuration = Long.valueOf(prefs.getString(getString(R.string.pref_breathing_session_duration), "0"));

		mInhaleMessageAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.inhale);
		mExhaleMessageAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.exhale);
		mHoldMessageAnimation = AnimationUtils.loadAnimation(getActivity(), R.anim.hold);

		mDurationToast = Toast.makeText(getActivity(), "", Toast.LENGTH_SHORT);

		setTextFadeDuration();

		Loader<Bitmap> loader = getLoaderManager().initLoader(LOADER_BACKGROUND, null, this);
		if (!loader.isStarted()) {
			loader.forceLoad();
		}
		// getLoaderManager().getLoader(LOADER_BACKGROUND).forceLoad();

		Random rand = new Random();
		mSoundPhase = rand.nextInt(6);
		mPromptsEnabled = prefs.getBoolean(getString(R.string.pref_breathing_prompts), true);
		mMusicEnabled = !MusicCategory.NONE.name().equals(
				prefs.getString(getString(R.string.pref_breathing_music), MusicCategory.NONE.name()));

		if (savedInstanceState != null) {
			mStarted = savedInstanceState.getBoolean("started");
			String uriString = savedInstanceState.getString("music_file");
			if (uriString != null) {
				mMusicUri = Uri.parse(uriString);
				mMusicPosition = savedInstanceState.getInt("music_position");
			}
			mSessionDuration = savedInstanceState.getLong("session");
			mFinished = savedInstanceState.getBoolean("finished");
		}

		if (mSessionDuration > 0) {
			mSessionHandler = new Handler();
			mSessionTimer = new Runnable() {
				public void run() {
					mSessionDuration = mSessionDuration - 1000;
					if (!mFinishStarted && mSessionDuration <= 4000) {
						mFinishStarted = true;
						Animation fadeOut = AnimationUtils.loadAnimation(getActivity(), android.R.anim.fade_out);
						fadeOut.setFillAfter(true);
						fadeOut.setDuration(mSessionDuration);
						getView().findViewById(R.id.lay_breathing).startAnimation(fadeOut);
						getBackgroundView().stop();
						getBackgroundView().startAnimation(fadeOut);
					}

					if (mSessionDuration <= 0) {
						mSessionDuration = 0;
						getBarView().stop();
						Animation fadeIn = AnimationUtils.loadAnimation(getActivity(),
								android.R.anim.fade_in);
						fadeIn.setFillAfter(true);
						fadeIn.setStartOffset(0);
						fadeIn.setDuration(2000);
						fadeIn.setAnimationListener(new AnimationListener() {
							public void onAnimationStart(Animation animation) {
							}

							public void onAnimationRepeat(Animation animation) {
							}

							public void onAnimationEnd(Animation animation) {
								getView().findViewById(R.id.lbl_complete).setVisibility(View.VISIBLE);
								getView().findViewById(R.id.lbl_complete).sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
							}
						});
						getView().findViewById(R.id.lbl_complete).startAnimation(fadeIn);
						mStarted = false;
						mFinished = true;
						getActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
						return;
					}

					((TextView) getView().findViewById(R.id.lbl_message)).setText(DateUtils.formatElapsedTime(mSessionDuration / 1000));
					mSessionHandler.postDelayed(mSessionTimer, 1000);
				}
			};
		}
	}

	public Loader<Bitmap> onCreateLoader(int id, Bundle args) {
		return new BackgroundTaskLoader(getActivity());
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.controlled_breathing_view, null);
	}

	public void onLoaderReset(Loader<Bitmap> loader) {
	}

	public void onLoadFinished(Loader<Bitmap> loader, Bitmap data) {
		if (getBackgroundView() == null) {
			return;
		}

		getBackgroundView().queueBackground(data);
	}

	@Override
	public void onPause() {
		super.onPause();

		if (mMusicEnabled && mMusicPlayer != null) {
			try {
				if (mMusicPlayer.isPlaying()) {
					mMusicPosition = mMusicPlayer.getCurrentPosition();
				}
			} catch (IllegalStateException e) {
			}
		}

		stopMusic();
		stopPrompt();

		if (mSessionHandler != null) {
			mSessionHandler.removeCallbacks(mSessionTimer);
		}

		if (mStarting) {
			mStartTimer.cancel();
		}
	}

	public void onPrepared(MediaPlayer mp) {
		if (mp == mMusicPlayer && mMusicPosition > 0) {
			mp.setOnSeekCompleteListener(this);
			mp.seekTo(mMusicPosition);
		} else {
			mp.start();
		}
	}

	@Override
	public void onResume() {
		super.onResume();
		if (!mStarted) {
			restart();
		} else {
			if (mSessionDuration > 0) {
				getMessageTextView().setVisibility(View.VISIBLE);
				getMessageTextView().setText(DateUtils.formatElapsedTime(mSessionDuration / 1000));
				mSessionHandler.postDelayed(mSessionTimer, 100);
			}
		}
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		outState.putBoolean("started", mStarted);

		if (mMusicUri != null) {
			outState.putString("music_file", mMusicUri.toString());
			if (mMusicPlayer != null) {
				try {
					if (mMusicPlayer.isPlaying()) {
						outState.putInt("music_position", mMusicPlayer.getCurrentPosition());
					}
				} catch (IllegalStateException e) {
				}
			}
		}

		outState.putLong("session", mSessionDuration);
		outState.putBoolean("finished", mFinished);
	}

	public void onSeekComplete(MediaPlayer mp) {
		mMusicPosition = 0;
		mp.start();
	}

	public void restart() {
		mStartTimer = new CountDownTimer(6000, 200) {

			private boolean mDeepBreathShown, mThreeShown, mTwoShown, mOneShown;

			@Override
			public void onFinish() {
				mDeepBreathShown = false;
				mThreeShown = false;
				mTwoShown = false;
				mOneShown = false;

				getExhaleTextView().setText("Exhale");
				getMessageTextView().setText("");
				getMessageTextView().clearAnimation();
				getBarView().start();
				getExhaleTextView().sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
				((ControlledBreathingBackgroundView) getView().findViewById(R.id.img_background)).start();
				if (mSessionDuration > 0) {
					getMessageTextView().setText(DateUtils.formatElapsedTime(mSessionDuration / 1000));
					Animation fadeIn = AnimationUtils.loadAnimation(getActivity(),
							android.R.anim.fade_in);
					fadeIn.setFillAfter(true);
					fadeIn.setDuration(500);
					getMessageTextView().startAnimation(fadeIn);
					mSessionHandler.postDelayed(mSessionTimer, 1000);
				}
				mStarting = false;
			}

			@Override
			public void onTick(long millisUntilFinished) {
				OutlineTextView view = (OutlineTextView) getExhaleTextView();
				if (view == null) {
					return;
				}

				if (millisUntilFinished <= 1000 && !mOneShown) {
					mOneShown = true;
					view.setText("1");
					view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
				} else if (millisUntilFinished <= 2200 && !mTwoShown) {
					mTwoShown = true;
					view.setText("2");
					view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
				} else if (millisUntilFinished <= 3400 && !mThreeShown) {
					mThreeShown = true;
					view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 60);
					view.setText("3");
					view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
				} else if (millisUntilFinished <= 4800 && !mDeepBreathShown) {
					mDeepBreathShown = true;
					view.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 35);
					view.setText("Take a deep breath!");
					view.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
				}
			}
		};

		mStarted = false;
		mStarting = false;
		getExhaleTextView().setTextSize(TypedValue.COMPLEX_UNIT_DIP, 60);
		getExhaleTextView().setText("Ready?");
		getMessageTextView().setText("Tap to start.");
		getMessageTextView().clearAnimation();
	}

	private ControlledBreathingBackgroundView getBackgroundView() {
		return (ControlledBreathingBackgroundView) getView().findViewById(R.id.img_background);
	}

	private void setTextFadeDuration() {
		((AnimationSet) mInhaleMessageAnimation).getAnimations().get(1).setDuration(mInhaleDuration - 400);
		((AnimationSet) mExhaleMessageAnimation).getAnimations().get(1).setDuration(mExhaleDuration - 400);
		if (mHoldDuration > 0) {
			((AnimationSet) mHoldMessageAnimation).getAnimations().get(1).setDuration(mHoldDuration - 400);
		}
	}

	private void startMusic() {
		if (!mMusicEnabled) {
			return;
		} else if (mMusicPlayer != null) {
			try {
				if (mMusicPlayer.isPlaying()) {
					return;
				}
			} catch (IllegalStateException e) {
			}
		}

		if (mMusicUri == null) {
			MusicCategory category = MusicCategory.valueOf(PreferenceManager.getDefaultSharedPreferences(getActivity()).getString(
					getActivity().getString(R.string.pref_breathing_music), MusicCategory.RANDOM.name()));
			Resources res = getResources();
			switch (category) {
			case AMBIENT_EVENINGS:
			case EVO_SOLUTION:
			case OCEAN_MIST:
			case WANING_MOMENTS:
			case WATERMARK:
				long rId = category.getResourceId();
				mMusicUri = Uri.parse(
						ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + res.getResourcePackageName((int) rId) + "/" + rId);
				break;
			case RANDOM:
				rId = MusicCategory.getRandomResource();
				mMusicUri = Uri.parse(
						ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + res.getResourcePackageName((int) rId) + "/" + rId);
				break;
			case PERSONAL_MUSIC:
				if (!(getActivity() instanceof MusicProvider)) {
					return;
				}
				mMusicUri = ((MusicProvider) getActivity()).getMusic();
				break;
			case NONE:
				return;
			}

		}

		if (mMusicUri == null) {
			return;
		}

		try {
			mMusicPlayer = new MediaPlayer();
			mMusicPlayer.setOnErrorListener(new OnErrorListener() {
				public boolean onError(MediaPlayer mp, int what, int extra) {
					mp.reset();
					return true;
				}
			});
			mMusicPlayer.setOnCompletionListener(this);
			mMusicPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			mMusicPlayer.setVolume(0.7f, 0.7f);
			mMusicPlayer.setDataSource(getActivity(), mMusicUri);
			mMusicPlayer.setOnPreparedListener(this);
			mMusicPlayer.prepareAsync();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void startPrompt(int id) {
		if (!mPromptsEnabled) {
			return;
		}

		mSoundPhase = (mSoundPhase + 1) % 5;
		if (mSoundPhase != 0) {
			return;
		}

		if (mPlayer != null) {
			try {
				if (mPlayer.isPlaying()) {
					return;
				}
			} catch (IllegalStateException e) {
			}

			mPlayer.release();
		}

		try {
			mPlayer = new MediaPlayer();
			mPlayer.setOnErrorListener(new OnErrorListener() {
				public boolean onError(MediaPlayer mp, int what, int extra) {
					mp.reset();
					return true;
				}
			});
			mPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
			AssetFileDescriptor afd = getResources().openRawResourceFd(id);
			mPlayer.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
			afd.close();
			mPlayer.setOnPreparedListener(this);
			mPlayer.prepareAsync();
		} catch (IOException e) {
			Log.e("bla", "blaaaaa", e);
		}

	}

	private void stopMusic() {
		// mMusicPosition = 0;
		// mMusicUri = null;
		if (mMusicPlayer != null) {
			try {
				mMusicPlayer.reset();
			} catch (IllegalStateException e) {
			}
			mMusicPlayer.release();
			mMusicPlayer = null;
		}
	}

	private void stopPrompt() {
		if (mPlayer != null) {
			try {
				mPlayer.reset();
			} catch (IllegalStateException e) {
			}
			mPlayer.release();
			mPlayer = null;
		}
	}

	public static interface BackgroundProvider {
		public Bitmap getBackground();
	}

	public static interface MusicProvider {
		public Uri getMusic();
	}

	private static final class BackgroundTaskLoader extends AsyncTaskLoader<Bitmap> {

		private Context mContext;

		public BackgroundTaskLoader(Context context) {
			super(context);
			mContext = context;
		}

		@Override
		public Bitmap loadInBackground() {
			BackgroundCategory category = BackgroundCategory.valueOf(PreferenceManager.getDefaultSharedPreferences(getContext()).getString(
					mContext.getString(R.string.pref_breathing_background), BackgroundCategory.RAINFORESTS.name()));

			if (category == BackgroundCategory.NONE) {
				return null;
			}

			Random rand = new Random();
			if (category == BackgroundCategory.PERSONAL_IMAGES) {
				if (mContext instanceof BackgroundProvider) {
					return ((BackgroundProvider) mContext).getBackground();
				}
				return null;
			} else {
				int[] ids = category.getResources();
				int index = rand.nextInt(ids.length);
				return BitmapFactory.decodeResource(mContext.getResources(), ids[index]);
			}
		}
	}
}
