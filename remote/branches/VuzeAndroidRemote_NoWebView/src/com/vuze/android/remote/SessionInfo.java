/**
 * Copyright (C) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 * 
 */

package com.vuze.android.remote;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.aelitis.azureus.util.MapUtils;
import com.vuze.android.remote.NetworkState.NetworkStateListener;
import com.vuze.android.remote.rpc.*;

/**
 * Access to all the information for a session, such as:<P>
 * - RemoteProfile<BR>
 * - SessionSettings<BR>
 * - RPC<BR>
 * - torrents<BR>
 */
public class SessionInfo
	implements SessionSettingsReceivedListener, NetworkStateListener
{
	private static final String TAG = "SessionInfo";

	private static String[] SESSION_STATS_FIELDS = {
		TransmissionVars.TR_SESSION_STATS_DOWNLOAD_SPEED,
		TransmissionVars.TR_SESSION_STATS_UPLOAD_SPEED
	};

	private SessionSettings sessionSettings;

	private TransmissionRPC rpc;

	private RemoteProfile remoteProfile;

	/** <Key, TorrentMap> */
	private LinkedHashMap<Object, Map<?, ?>> mapOriginal;

	private Object mLock = new Object();

	private List<TorrentListReceivedListener> torrentListReceivedListeners = new CopyOnWriteArrayList<TorrentListReceivedListener>();

	private List<SessionSettingsChangedListener> sessionSettingsChangedListeners = new CopyOnWriteArrayList<SessionSettingsChangedListener>();

	private List<RefreshTriggerListener> refreshTriggerListeners = new CopyOnWriteArrayList<RefreshTriggerListener>();

	private List<SessionInfoListener> availabilityListeners = new CopyOnWriteArrayList<SessionInfoListener>();

	private boolean rememberSettingChanges;

	private Handler handler;

	private boolean uiReady = false;

	private Map<?, ?> mapSessionStats;

	private Map<Long, Map<?, ?>> mapTags;

	private boolean refreshing;

	private String rpcRoot;

	protected SessionInfo(TransmissionRPC rpc, RemoteProfile _remoteProfile,
			boolean rememberSettingChanges) {
		this.remoteProfile = _remoteProfile;
		this.rememberSettingChanges = rememberSettingChanges;
		this.mapOriginal = new LinkedHashMap<Object, Map<?, ?>>();

		setRpc(rpc);

		VuzeRemoteApp.getNetworkState().addListener(this);
	}

	public SessionInfo(final Activity activity, RemoteProfile _remoteProfile,
			boolean rememberSettingChanges) {
		this((TransmissionRPC) null, _remoteProfile, rememberSettingChanges);

		// Bind and Open take a while, do it on the non-UI thread
		Thread thread = new Thread("bindAndOpen") {
			public void run() {
				String host = remoteProfile.getHost();
				if (host != null && host.length() > 0
						&& remoteProfile.getRemoteType() == RemoteProfile.TYPE_NORMAL) {
					open(activity, remoteProfile.getUser(), remoteProfile.getAC(),
							"http", host, remoteProfile.getPort());
				} else {
					bindAndOpen(activity, remoteProfile.getAC(), remoteProfile.getUser());
				}
			}
		};
		thread.setDaemon(true);
		thread.start();

	}

	@SuppressWarnings("null")
	protected void bindAndOpen(Activity activity, final String ac,
			final String user) {

		RPC rpc = new RPC();
		try {
			Map<?, ?> bindingInfo = rpc.getBindingInfo(ac);

			Map<?, ?> error = MapUtils.getMapMap(bindingInfo, "error", null);
			if (error != null) {
				String errMsg = MapUtils.getMapString(error, "msg", "Unknown Error");
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Error from getBindingInfo " + errMsg);
				}

				AndroidUtils.showConnectionError(activity, errMsg, false);
				return;
			}

			String host = MapUtils.getMapString(bindingInfo, "ip", null);
			String protocol = MapUtils.getMapString(bindingInfo, "protocol", null);
			int port = Integer.valueOf(MapUtils.getMapString(bindingInfo, "port", "0"));

			if (AndroidUtils.DEBUG) {
				if (host == null) {
					//ip = "192.168.2.59";
					host = "192.168.1.2";
					protocol = "http";
					port = 9092;
				}
			}

			if (host != null && protocol != null) {
				remoteProfile.setHost(host);
				remoteProfile.setPort(port);
				open(activity, "vuze", ac, protocol, host, port);
			}
		} catch (final RPCException e) {
			VuzeEasyTracker.getInstance(activity).logError(activity, e);
			AndroidUtils.showConnectionError(activity, e.getMessage(), false);
			if (AndroidUtils.DEBUG) {
				e.printStackTrace();
			}
		}
	}

	private void open(Activity activity, String user, final String ac,
			String protocol, String host, int port) {
		try {

			rpcRoot = protocol + "://" + host + ":" + port + "/";
			String rpcUrl = rpcRoot + "transmission/rpc";

			if (!AndroidUtils.isURLAlive(rpcUrl)) {
				AndroidUtils.showConnectionError(activity,
						R.string.error_remote_not_found, false);
				return;
			}

			AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
			remoteProfile.setLastUsedOn(System.currentTimeMillis());
			if (rememberSettingChanges) {
				appPreferences.setLastRemote(ac);
				appPreferences.addRemoteProfile(remoteProfile);
			}

			if (AndroidUtils.DEBUG) {
				Log.d(null, "rpc root = " + rpcRoot);
			}

			setRpc(new TransmissionRPC(this, rpcUrl, user, ac));
		} catch (Exception e) {
			VuzeEasyTracker.getInstance(activity).logError(activity, e);
			if (AndroidUtils.DEBUG) {
				e.printStackTrace();
			}
		}
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.rpc.SessionSettingsReceivedListener#sessionPropertiesUpdated(java.util.Map)
	 */
	@Override
	public void sessionPropertiesUpdated(Map<?, ?> map) {
		SessionSettings settings = new SessionSettings();
		settings.setDLIsAuto(MapUtils.getMapBoolean(map,
				"speed-limit-down-enabled", true));
		settings.setULIsAuto(MapUtils.getMapBoolean(map, "speed-limit-up-enabled",
				true));
		settings.setDownloadDir(MapUtils.getMapString(map, "download-dir", null));

		settings.setDlSpeed(MapUtils.getMapLong(map, "speed-limit-down", 0));
		settings.setUlSpeed(MapUtils.getMapLong(map, "speed-limit-up", 0));

		sessionSettings = settings;

		for (SessionSettingsChangedListener l : sessionSettingsChangedListeners) {
			l.sessionSettingsChanged(settings);
		}

		if (!uiReady) {
			rpc.simpleRpcCall("tags-get-list", new ReplyMapReceivedListener() {

				@Override
				public void rpcSuccess(String id, Map<?, ?> optionalMap) {
					List<?> tagList = MapUtils.getMapList(optionalMap, "tags", null);
					if (tagList == null) {
						mapTags = null;
						setUIReady();
						return;
					}
					mapTags = new HashMap<Long, Map<?, ?>>();
					for (Object tag : tagList) {
						if (tag instanceof Map) {
							Map<?, ?> mapTag = (Map<?, ?>) tag;
							mapTags.put(MapUtils.getMapLong(mapTag, "uid", 0), mapTag);
						}
					}
					setUIReady();
				}

				@Override
				public void rpcFailure(String id, String message) {
					setUIReady();
				}

				@Override
				public void rpcError(String id, Exception e) {
					setUIReady();
				}
			});

		}
	}

	private void setUIReady() {
		uiReady = true;
		initRefreshHandler();
		for (SessionInfoListener l : availabilityListeners) {
			l.uiReady();
		}
		availabilityListeners.clear();
	}

	public Map<?, ?> getTag(Long uid) {
		if (mapTags == null) {
			return null;
		}
		// TODO: if .get is null, maybe repull tag list?
		return mapTags.get(uid);
	}

	public List<Map<?, ?>> getTags() {
		if (mapTags == null) {
			return null;
		}
		return new ArrayList<Map<?, ?>>(mapTags.values());
	}

	/**
	 * @return the sessionSettings
	 */
	public SessionSettings getSessionSettings() {
		return sessionSettings;
	}

	/**
	 * @return the rpc
	 */
	public TransmissionRPC getRpc() {
		return rpc;
	}

	/**
	 * @return the remoteProfile
	 */
	public RemoteProfile getRemoteProfile() {
		return remoteProfile;
	}

	/**
	 * @param rpc the rpc to set
	 */
	public void setRpc(TransmissionRPC rpc) {
		if (this.rpc == rpc) {
			return;
		}

		if (this.rpc != null) {
			this.rpc.removeSessionSettingsReceivedListener(this);
		}

		this.rpc = rpc;

		if (rpc != null) {
			for (SessionInfoListener l : availabilityListeners) {
				l.transmissionRpcAvailable(this);
			}

			rpc.addTorrentListReceivedListener(new TorrentListReceivedListener() {
				@Override
				public void rpcTorrentListReceived(List<?> listTorrents) {
					addTorrents(listTorrents);
				}
			});

			rpc.addSessionSettingsReceivedListener(this);
		}
	}

	/**
	 * @param remoteProfile the remoteProfile to set
	 */
	public void setRemoteProfile(RemoteProfile remoteProfile) {
		this.remoteProfile = remoteProfile;
	}

	/*
	public LinkedHashMap<Object, Map<?, ?>> getTorrentList() {
		synchronized (mLock) {
			return new LinkedHashMap<Object, Map<?, ?>>(mapOriginal);
		}
	}
	*/

	public List<Map<?, ?>> getTorrentList() {
		synchronized (mLock) {
			return new LinkedList<Map<?, ?>>(mapOriginal.values());
		}
	}

	public Object[] getTorrentListKeys() {
		synchronized (mLock) {
			return mapOriginal.keySet().toArray();
		}
	}

	public Map<?, ?> getTorrent(Object key) {
		synchronized (mLock) {
			return mapOriginal.get(key);
		}
	}

	@SuppressWarnings({
		"rawtypes",
		"unchecked"
	})
	public void addTorrents(List<?> collection) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "adding torrents " + collection.size());
		}
		synchronized (mLock) {
			for (Object item : collection) {
				if (!(item instanceof Map)) {
					continue;
				}
				Map mapTorrent = (Map) item;
				Object key = mapTorrent.get("id");
				if (key instanceof Integer) {
					key = Long.valueOf((Integer) key);
				}
				Map old = mapOriginal.put(key, mapTorrent);
				if (old != null) {
					// merge anything missing in new map with old
					for (Iterator iterator = old.keySet().iterator(); iterator.hasNext();) {
						Object torrentKey = iterator.next();
						if (!mapTorrent.containsKey(torrentKey)) {
							//System.out.println(key + " missing " + torrentKey);
							mapTorrent.put(torrentKey, old.get(torrentKey));
						}
					}

					mergeList("files", mapTorrent, old);
					mergeList("fileStats", mapTorrent, old);
				}
			}
		}

		for (TorrentListReceivedListener l : torrentListReceivedListeners) {
			l.rpcTorrentListReceived(collection);
		}
	}

	@SuppressWarnings({
		"rawtypes",
		"unchecked"
	})
	private void mergeList(String key, Map mapTorrent, Map old) {
		List listOldFiles = MapUtils.getMapList(old, key, null);
		if (listOldFiles != null) {
			// files: merge special case
			List listUpdatedFiles = MapUtils.getMapList(mapTorrent, key, null);
			if (listUpdatedFiles != null) {
				List listNewFiles = new ArrayList(listOldFiles);
				for (Object oUpdatedFile : listUpdatedFiles) {
					if (!(oUpdatedFile instanceof Map)) {
						continue;
					}
					Map mapUpdatedFile = (Map) oUpdatedFile;
					int index = MapUtils.getMapInt(mapUpdatedFile, "index", -1);
					if (index < 0 || index >= listNewFiles.size()) {
						continue;
					}
					Map mapNewFile = (Map) listNewFiles.get(index);
					for (Object fileKey : mapUpdatedFile.keySet()) {
						mapNewFile.put(fileKey, mapUpdatedFile.get(fileKey));
					}
					mapTorrent.put(key, listNewFiles);
				}
			}
		}
	}

	public boolean addTorrentListReceivedListener(TorrentListReceivedListener l) {
		return addTorrentListReceivedListener(l, true);
	}

	public boolean addTorrentListReceivedListener(TorrentListReceivedListener l,
			boolean fire) {
		synchronized (torrentListReceivedListeners) {
			if (torrentListReceivedListeners.contains(l)) {
				return false;
			}
			torrentListReceivedListeners.add(l);
			List<Map<?, ?>> torrentList = getTorrentList();
			if (torrentList.size() > 0 && fire) {
				l.rpcTorrentListReceived(torrentList);
			}
		}
		return true;
	}

	public void removeTorrentListReceivedListener(TorrentListReceivedListener l) {
		synchronized (torrentListReceivedListeners) {
			torrentListReceivedListeners.remove(l);
		}
	}

	public void saveProfileIfRemember() {
		if (rememberSettingChanges) {
			AppPreferences appPreferences = VuzeRemoteApp.getAppPreferences();
			appPreferences.addRemoteProfile(remoteProfile);
		}
	}

	public void updateSessionSettings(SessionSettings newSettings) {
		SessionSettings originalSettings = getSessionSettings();

		saveProfileIfRemember();

		// if already init/cancelled, methods will handle check
		if (!remoteProfile.isUpdateIntervalEnabled()) {
			cancelRefreshHandler();
		} else {
			if (handler == null) {
				initRefreshHandler();
			}
		}

		Map<String, Object> changes = new HashMap<String, Object>();
		if (newSettings.isDLAuto() != originalSettings.isDLAuto()) {
			changes.put("speed-limit-down-enabled", newSettings.isDLAuto());
		}
		if (newSettings.isULAuto() != originalSettings.isULAuto()) {
			changes.put("speed-limit-up-enabled", newSettings.isULAuto());
		}
		if (newSettings.getUlSpeed() != originalSettings.getUlSpeed()) {
			changes.put("speed-limit-up", newSettings.getUlSpeed());
		}
		if (newSettings.getDlSpeed() != originalSettings.getDlSpeed()) {
			changes.put("speed-limit-down", newSettings.getDlSpeed());
		}
		if (changes.size() > 0) {
			rpc.updateSettings(changes);
		}

		sessionSettings = newSettings;

		for (SessionSettingsChangedListener l : sessionSettingsChangedListeners) {
			l.sessionSettingsChanged(sessionSettings);
		}
	}

	private void cancelRefreshHandler() {
		if (handler == null) {
			return;
		}
		handler.removeCallbacksAndMessages(null);
		handler = null;
	}

	public void initRefreshHandler() {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "initRefreshHandler");
		}
		if (handler != null) {
			return;
		}
		long interval = remoteProfile.isUpdateIntervalEnabled()
				? remoteProfile.getUpdateInterval() : 0;
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Handler fires every " + interval);
		}
		if (interval <= 0) {
			return;
		}
		handler = new Handler(Looper.getMainLooper());
		handler.postDelayed(new Runnable() {
			public void run() {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Fire Handler");
				}
				triggerRefresh(true);

				for (RefreshTriggerListener l : refreshTriggerListeners) {
					l.triggerRefresh();
				}

				long interval = remoteProfile.isUpdateIntervalEnabled()
						? remoteProfile.getUpdateInterval() : 0;
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Handler fires in " + interval);
				}
				if (interval > 0) {
					handler.postDelayed(this, interval * 1000);
				}
			}
		}, interval * 1000);
	}

	public void triggerRefresh(final boolean recentOnly) {
		synchronized (mLock) {
			if (refreshing) {
				if (AndroidUtils.DEBUG) {
					Log.d(TAG, "Refresh skipped. Already refreshing");
				}
				return;
			}
			refreshing = true;
		}
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Refresh Triggered");
		}

		// XXX Don't do this when we have no focus!

		rpc.getSessionStats(SESSION_STATS_FIELDS, new ReplyMapReceivedListener() {
			@Override
			public void rpcSuccess(String id, Map<?, ?> optionalMap) {
				updateSessionStats(optionalMap);

				TorrentListReceivedListener listener = new TorrentListReceivedListener() {
					@Override
					public void rpcTorrentListReceived(List<?> listTorrents) {
						synchronized (mLock) {
							refreshing = false;
						}
					}
				};
				if (recentOnly) {
					rpc.getRecentTorrents(listener);
				} else {
					rpc.getAllTorrents(listener);
				}
			}

			@Override
			public void rpcError(String id, Exception e) {
			}

			@Override
			public void rpcFailure(String id, String message) {
			}
		});
	}

	protected void updateSessionStats(Map<?, ?> map) {
		Map<?, ?> oldSessionStats = mapSessionStats;
		mapSessionStats = map;

/*
   string                     | value type
   ---------------------------+-------------------------------------------------
   "activeTorrentCount"       | number
   "downloadSpeed"            | number
   "pausedTorrentCount"       | number
   "torrentCount"             | number
   "uploadSpeed"              | number
   ---------------------------+-------------------------------+
   "cumulative-stats"         | object, containing:           |
                              +------------------+------------+
                              | uploadedBytes    | number     | tr_session_stats
                              | downloadedBytes  | number     | tr_session_stats
                              | filesAdded       | number     | tr_session_stats
                              | sessionCount     | number     | tr_session_stats
                              | secondsActive    | number     | tr_session_stats
   ---------------------------+-------------------------------+
   "current-stats"            | object, containing:           |
                              +------------------+------------+
                              | uploadedBytes    | number     | tr_session_stats
                              | downloadedBytes  | number     | tr_session_stats
                              | filesAdded       | number     | tr_session_stats
                              | sessionCount     | number     | tr_session_stats
                              | secondsActive    | number     | tr_session_stats
*/
		long oldDownloadSpeed = MapUtils.getMapLong(oldSessionStats,
				TransmissionVars.TR_SESSION_STATS_DOWNLOAD_SPEED, 0);
		long newDownloadSpeed = MapUtils.getMapLong(map,
				TransmissionVars.TR_SESSION_STATS_DOWNLOAD_SPEED, 0);
		long oldUploadSpeed = MapUtils.getMapLong(oldSessionStats,
				TransmissionVars.TR_SESSION_STATS_UPLOAD_SPEED, 0);
		long newUploadSpeed = MapUtils.getMapLong(map,
				TransmissionVars.TR_SESSION_STATS_UPLOAD_SPEED, 0);

		if (oldDownloadSpeed != newDownloadSpeed
				|| oldUploadSpeed != newUploadSpeed) {
			for (SessionSettingsChangedListener l : sessionSettingsChangedListeners) {
				l.speedChanged(newDownloadSpeed, newUploadSpeed);
			}
		}
	}

	public void addSessionSettingsChangedListeners(
			SessionSettingsChangedListener l) {
		synchronized (sessionSettingsChangedListeners) {
			if (!sessionSettingsChangedListeners.contains(l)) {
				sessionSettingsChangedListeners.add(l);
				if (sessionSettings != null) {
					l.sessionSettingsChanged(sessionSettings);
				}
			}
		}
	}

	public void removeSessionSettingsChangedListeners(
			SessionSettingsChangedListener l) {
		synchronized (sessionSettingsChangedListeners) {
			sessionSettingsChangedListeners.remove(l);
		}
	}

	/**
	 * add SessionInfoListener.  listener is only triggered once for each method,
	 * and then removed
	 */
	public void addRpcAvailableListener(SessionInfoListener l) {
		if (uiReady && rpc != null) {
			if (rpc != null) {
				l.transmissionRpcAvailable(this);
			}
			if (uiReady) {
				l.uiReady();
			}
		} else {
			synchronized (availabilityListeners) {
				if (availabilityListeners.contains(l)) {
					return;
				}
				availabilityListeners.add(l);
			}
			if (rpc != null) {
				l.transmissionRpcAvailable(this);
			}
		}
	}

	public void addRefreshTriggerListener(RefreshTriggerListener l) {
		if (refreshTriggerListeners.contains(l)) {
			return;
		}
		refreshTriggerListeners.add(l);
	}

	public void removeRefreshTriggerListener(RefreshTriggerListener l) {
		refreshTriggerListeners.remove(l);
	}

	@Override
	public void onlineStateChanged(boolean isOnline) {
		if (isOnline || getRemoteProfile().isLocalHost()) {
			initRefreshHandler();
		} else {
			cancelRefreshHandler();
		}
	}

	@Override
	public void wifiConnectionChanged(boolean isWifiConnected) {
	}

	public String getRpcRoot() {
		return rpcRoot;
	}
}
