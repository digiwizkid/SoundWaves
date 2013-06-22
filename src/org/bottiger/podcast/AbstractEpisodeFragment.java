package org.bottiger.podcast;

import java.util.HashMap;

import org.bottiger.podcast.provider.FeedItem;
import org.bottiger.podcast.provider.ItemColumns;
import org.bottiger.podcast.provider.PodcastOpenHelper;
import org.bottiger.podcast.provider.PodcastProvider;
import org.bottiger.podcast.provider.Subscription;
import org.bottiger.podcast.service.PlayerService;
import org.bottiger.podcast.service.PodcastDownloadManager;
import org.bottiger.podcast.utils.OPMLImportExport;
import org.bottiger.podcast.utils.ThemeHelper;

import android.accounts.Account;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.CursorLoader;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ListView;
import android.widget.Toast;

import com.handmark.pulltorefresh.library.PullToRefreshBase;
import com.handmark.pulltorefresh.library.PullToRefreshBase.OnRefreshListener;
import com.handmark.pulltorefresh.library.PullToRefreshListView;

public abstract class AbstractEpisodeFragment extends PodcastBaseFragment {
	
	protected OnPlaylistRefreshListener mActivityCallback;

	protected static HashMap<Integer, Integer> mIconMap;

	protected long pref_order;
	protected long pref_where;
	protected long pref_select;

	protected ListView actualListView = null;
	
	private SharedPreferences prefs;

	String showListenedKey = "sowListened";
	Boolean showListenedVal = true;

	protected static final String episodesToShowKey = "episodesToShow";
	protected static final int episodesToShowVal = 20;
	
    // Container Activity must implement this interface
	// http://developer.android.com/training/basics/fragments/communicating.html
    public interface OnPlaylistRefreshListener {
        public void onRefreshPlaylist();
    }
    
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        
        // This makes sure that the container activity has implemented
        // the callback interface. If not, it throws an exception
        try {
        	mActivityCallback = (OnPlaylistRefreshListener) activity;
        } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString()
                    + " must implement OnHeadlineSelectedListener");
        }
    }


	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setHasOptionsMenu(true);
		prefs = PreferenceManager.getDefaultSharedPreferences(getActivity());
	}

	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
		inflater.inflate(R.menu.episode_list, menu);
		super.onCreateOptionsMenu(menu, inflater);
		
		ThemeHelper themeHelper = new ThemeHelper(getActivity());
		MenuItem menuItemSync = menu.findItem(R.id.menu_sync);
		menuItemSync.setIcon(themeHelper.getAttr(R.attr.sync_icon));
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_download_all: {
			Cursor cursor = createCursor(getWhere(), getOrder());
			cursor.moveToFirst();
			while (cursor.isAfterLast() == false) {
				FeedItem feedItem = FeedItem.getByCursor(cursor);
				if (!feedItem.isDownloaded())
					PodcastDownloadManager.addItemToQueue(feedItem);

				cursor.moveToNext();
			}
			PodcastDownloadManager.startDownload(getActivity());
			return true;
		}
		case R.id.menu_clear_playlist: {
			resetPlaylist(getActivity());
			refreshView();
		}
		case R.id.menu_sync:
			if (prefs.getBoolean(SettingsActivity.CLOUD_SUPPORT, true)) {
				//Account account = mCredential.getSelectedAccount();
				Account account = MainActivity.getCredentials().getSelectedAccount();
				Bundle bundle = new Bundle();
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_EXPEDITED, true);
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_FORCE, true);
				bundle.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
				String auth = PodcastProvider.AUTHORITY;
				auth = "org.bottiger.podcast.provider.PodcastProvider"; //
				ContentResolver
						.requestSync(
								account,
								"org.bottiger.podcast.provider.podcastprovider",
								bundle);
				ContentResolver.requestSync(account, auth, bundle);
			} else {
				CharSequence text = "Please enabled cloud support in the settings menu before attempting to sync";
				int duration = Toast.LENGTH_LONG;

				Toast toast = Toast.makeText(getActivity(), text, duration);
				toast.show();
			}

			return true;
		case R.id.menu_import: {
			OPMLImportExport importExport = new OPMLImportExport(getActivity());
			importExport.importSubscriptions();
			refreshView();
		}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onResume() {
		super.onResume();
	}

	public void getPref() {
		SharedPreferences pref = getActivity().getSharedPreferences(
				SettingsActivity.HAPI_PREFS_FILE_NAME, Context.MODE_PRIVATE);
		pref_order = pref.getLong("pref_order", 2);
		pref_where = pref.getLong("pref_where", 0);
		pref_select = pref.getLong("pref_select", 0);
	}

	@Deprecated
	protected Cursor createCursor(String condition, String order) {
		// return new CursorLoader(getActivity(), ItemColumns.URI, PROJECTION,
		// condition, null, getOrder()).loadInBackground();
		return new CursorLoader(getActivity(), ItemColumns.URI,
				ItemColumns.ALL_COLUMNS, condition, null, order)
				.loadInBackground();
	}

	public String getWhere() {
		Boolean showListened = sharedPreferences.getBoolean(showListenedKey,
				showListenedVal);
		String where = (showListened) ? "1" : ItemColumns.LISTENED + "== 0";
		return where;
	}

	public String getOrder() {
		return getOrder("DESC", 100);
	}

	public static String getOrder(String inputOrder, Integer amount) {
		assert inputOrder != null;

		PlayerService playerService = PodcastBaseFragment.mPlayerServiceBinder;

		String playingFirst = "";
		if (playerService != null && playerService.getCurrentItem() != null) {
			playingFirst = "case " + ItemColumns._ID + " when "
					+ playerService.getCurrentItem().getId()
					+ " then 1 else 2 end, ";
		}
		String prioritiesSecond = "case " + ItemColumns.PRIORITY
				+ " when 0 then 2 else 1 end, " + ItemColumns.PRIORITY + ", ";
		String order = playingFirst + prioritiesSecond + ItemColumns.DATE + " "
				+ inputOrder + " LIMIT " + amount; // before:
		return order;
	}

	public String getOrder(String inputOrder) {
		// assert inputOrder != null;
		//
		// String playingFirst = "";
		// if (mPlayerServiceBinder != null &&
		// mPlayerServiceBinder.getCurrentItem() != null) {
		// playingFirst = "case " + ItemColumns._ID + " when " +
		// mPlayerServiceBinder.getCurrentItem().getId() +
		// " then 1 else 2 end, ";
		// }
		// String prioritiesSecond = "case " + ItemColumns.PRIORITY +
		// " when 0 then 2 else 1 end, " + ItemColumns.PRIORITY + ", ";
		// String order = playingFirst + prioritiesSecond + ItemColumns.DATE +
		// " " + inputOrder + " LIMIT 20"; // before:
		// return order;
		int amount = sharedPreferences.getInt(episodesToShowKey,
				episodesToShowVal);
		return getOrder(inputOrder, amount);
	}

	protected void enablePullToRefresh() {
		enablePullToRefresh(null);
	}

	protected void enablePullToRefresh(final Subscription subscription) {

		final PullToRefreshListView pullToRefreshView = (PullToRefreshListView) fragmentView
				.findViewById(R.id.episode_list);

		OnRefreshListener<ListView> pullToRefreshListener = new OnRefreshListener<ListView>() {

			@Override
			public void onRefresh(PullToRefreshBase<ListView> refreshView) {
				PodcastDownloadManager.start_update(getActivity(),
						pullToRefreshView, subscription);
			}
		};

		actualListView = pullToRefreshView.getRefreshableView();
		pullToRefreshView.getLoadingLayoutProxy().setRefreshingLabel(
				"Refreshing feeds");
		pullToRefreshView.setOnRefreshListener(pullToRefreshListener);
	}

	protected static void resetPlaylist(Context context) {
		// Update the database
		PodcastOpenHelper helper = new PodcastOpenHelper(context);
		SQLiteDatabase db = helper.getWritableDatabase();
		String currentTime = String.valueOf(System.currentTimeMillis());
		String updateLastUpdate = ", " + ItemColumns.LAST_UPDATE + "=" + currentTime + " ";
		
		String action = "UPDATE " + ItemColumns.TABLE_NAME + " SET ";
		String value = ItemColumns.PRIORITY + "=0" + updateLastUpdate;
		String where = "WHERE " + ItemColumns.PRIORITY + "<> 0";
		String sql = action + value + where;
		db.execSQL(sql);
	}

}
