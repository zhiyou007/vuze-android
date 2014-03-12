package com.vuze.android.remote.activity;

import java.util.List;
import java.util.Map;

import android.annotation.TargetApi;
import android.app.ActionBar;
import android.content.Intent;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.*;

import com.vuze.android.remote.*;
import com.vuze.android.remote.fragment.*;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;

/**
 * Activity to hold {@link TorrentListFragment}.  Used for narrow screens.
 */
public class TorrentDetailsActivity
	extends FragmentActivity
	implements TorrentListReceivedListener, SessionInfoGetter
{
	private static final String TAG = "TorrentDetailsView";

	private long torrentID;

	private SessionInfo sessionInfo;

	private TorrentListRowFiller torrentListRowFiller;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		Intent intent = getIntent();

		final Bundle extras = intent.getExtras();
		if (extras == null) {
			Log.e(TAG, "No extras!");
			finish();
			return;
		}
		
		Resources res = getResources();
		if (!res.getBoolean(R.bool.showTorrentDetailsActivity)) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "Don't show TorrentDetailsActivity");
			}
			finish();
			return;
		}

		torrentID = extras.getLong("TorrentID");
		String remoteProfileID = extras.getString(SessionInfoManager.BUNDLE_KEY);
		sessionInfo = SessionInfoManager.getSessionInfo(remoteProfileID, this, true);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
			setupHoneyComb();
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
			setupIceCream();
		}

		setContentView(R.layout.activity_torrent_detail);

		View viewMain = findViewById(R.id.activity_torrent_detail_view);
		torrentListRowFiller = new TorrentListRowFiller(this, viewMain);

		TorrentDetailsFragment detailsFrag = (TorrentDetailsFragment) getSupportFragmentManager().findFragmentById(
				R.id.fragment2);

		if (detailsFrag != null) {
			detailsFrag.setTorrentIDs(new long[] {
				torrentID
			});
		}
	}

	@Override
	protected void onPause() {
		super.onPause();
		if (sessionInfo != null) {
  		sessionInfo.activityPaused();
  		sessionInfo.removeTorrentListReceivedListener(this);
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		if (sessionInfo != null) {
			sessionInfo.activityResumed();
			sessionInfo.addTorrentListReceivedListener(TAG, this);
		}
	}

	@Override
	public void rpcTorrentListReceived(String callID, List<?> listTorrents) {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				Map<?, ?> mapTorrent = sessionInfo.getTorrent(torrentID);
				torrentListRowFiller.fillHolder(mapTorrent, sessionInfo);
			}
		});
	}

	@TargetApi(Build.VERSION_CODES.ICE_CREAM_SANDWICH)
	private void setupIceCream() {
		getActionBar().setHomeButtonEnabled(true);
	}

	@TargetApi(Build.VERSION_CODES.HONEYCOMB)
	private void setupHoneyComb() {
		// needed because one of our test machines won't listen to <item name="android:windowActionBar">true</item>
		requestWindowFeature(Window.FEATURE_ACTION_BAR);

		// enable ActionBar app icon to behave as action to toggle nav drawer
		ActionBar actionBar = getActionBar();
		if (actionBar == null) {
			System.err.println("actionBar is null");
			return;
		}

		RemoteProfile remoteProfile = sessionInfo.getRemoteProfile();
		if (Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB) {
			actionBar.setTitle(remoteProfile.getNick());
		} else {
			actionBar.setSubtitle(remoteProfile.getNick());
		}

		// enable ActionBar app icon to behave as action to toggle nav drawer
		actionBar.setDisplayHomeAsUpEnabled(true);
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case android.R.id.home:
				finish();
				return true;
		}
		if (TorrentListFragment.handleTorrentMenuActions(sessionInfo, new long[] {
			torrentID
		}, getSupportFragmentManager(), item.getItemId())) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		if (AndroidUtils.DEBUG_MENU) {
			Log.d(TAG, "onCreateOptionsMenu");
		}

		super.onCreateOptionsMenu(menu);

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.menu_context_torrent_details, menu);

		return true;
	}

	@Override
	public SessionInfo getSessionInfo() {
		return sessionInfo;
	}
}
