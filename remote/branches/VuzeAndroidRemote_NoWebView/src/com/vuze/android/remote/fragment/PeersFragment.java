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

package com.vuze.android.remote.fragment;

import java.util.List;

import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.ListView;

import com.vuze.android.remote.AndroidUtils;
import com.vuze.android.remote.R;
import com.vuze.android.remote.SessionInfo.RpcExecuter;
import com.vuze.android.remote.rpc.TorrentListReceivedListener;
import com.vuze.android.remote.rpc.TransmissionRPC;

public class PeersFragment
	extends TorrentDetailPage
{
	private static final String TAG = "PeersFragment";

	private ListView listview;

	private PeersAdapter adapter;

	public PeersFragment() {
		super();
	}

	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);

		adapter = new PeersAdapter(this.getActivity());
		if (sessionInfo != null) {
			adapter.setSessionInfo(sessionInfo);
		}
		listview.setItemsCanFocus(true);
		listview.setAdapter(adapter);
	}

	public View onCreateView(android.view.LayoutInflater inflater,
			android.view.ViewGroup container, Bundle savedInstanceState) {

		View view = inflater.inflate(R.layout.frag_torrent_peers, container, false);

		listview = (ListView) view.findViewById(R.id.peers_list);

		listview.setItemsCanFocus(false);
		listview.setClickable(true);
		listview.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

		return view;
	}

	/* (non-Javadoc)
	 * @see com.vuze.android.remote.fragment.TorrentDetailPage#updateTorrentID(long, boolean, boolean, boolean)
	 */
	@Override
	public void updateTorrentID(final long torrentID, boolean isTorrent,
			boolean wasTorrent, boolean torrentIdChanged) {
		if (torrentIdChanged) {
			adapter.clearList();
		}

		//System.out.println("torrent is " + torrent);
		adapter.setSessionInfo(sessionInfo);
		if (isTorrent) {
			sessionInfo.executeRpc(new RpcExecuter() {
				@Override
				public void executeRpc(TransmissionRPC rpc) {
					rpc.getTorrentPeerInfo(TAG, torrentID,
							new TorrentListReceivedListener() {
						@Override
						public void rpcTorrentListReceived(String callID,
								List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
							updateAdapterTorrentID(torrentID);
						}
					});
				}
			});
		}

		if (torrentIdChanged) {
			AndroidUtils.clearChecked(listview);
		}
	}

	private void updateAdapterTorrentID(long id) {
		if (adapter == null) {
			return;
		}
		FragmentActivity activity = getActivity();
		if (activity == null) {
			return;
		}
		activity.runOnUiThread(new Runnable() {
			@Override
			public void run() {
				adapter.setTorrentID(torrentID);
			}
		});
	}

	@Override
	public void triggerRefresh() {
		sessionInfo.executeRpc(new RpcExecuter() {
			@Override
			public void executeRpc(TransmissionRPC rpc) {
				rpc.getTorrentPeerInfo(TAG, torrentID,
						new TorrentListReceivedListener() {
					@Override
					public void rpcTorrentListReceived(String callID,
							List<?> addedTorrentMaps, List<?> removedTorrentIDs) {
						updateAdapterTorrentID(torrentID);
					}
				});
			}
		});
	}

	@Override
	public void rpcTorrentListReceived(String callID, List<?> addedTorrentMaps,
			List<?> removedTorrentIDs) {
	}
}
