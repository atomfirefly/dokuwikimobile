package org.dokuwikimobile.ui.activity;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import com.actionbarsherlock.app.ActionBar;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.view.Window;
import org.dokuwikimobile.DokuwikiApplication;
import org.dokuwikimobile.R;
import org.dokuwikimobile.listener.PageListener;
import org.dokuwikimobile.model.Page;
import org.dokuwikimobile.ui.view.DokuwikiWebView;
import org.dokuwikimobile.xmlrpc.DokuwikiXMLRPCClient.Canceler;
import org.dokuwikimobile.xmlrpc.ErrorCode;

/**
 *
 * @author Tim Roes <mail@timroes.de>
 */
public class BrowserActivity extends DokuwikiActivity implements PageListener {

	private static final String PAGE_ID = "pageid";

	private Handler handler;
	
	private DokuwikiWebView browser;
	private Canceler canceler;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		
		handler = new Handler();

		browser = new DokuwikiWebView(this);
		setContentView(browser);

		setupActionBar();

		startLoadingPage();
		
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);

		startLoadingPage();
	}

	

	/**
	 * Create the option menu for this activity.
	 * 
	 * @param menu The menu to inflate.
	 * @return Whether the menu has been changed.
	 */
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getSupportMenuInflater();
		inflater.inflate(R.menu.browser, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.pageinfo).setEnabled(true);
		if(canceler != null) {
			menu.findItem(R.id.reload_abort).setTitle(R.string.cancel);
			menu.findItem(R.id.reload_abort).setIcon(R.drawable.ic_bar_cancel);
		} else {
			menu.findItem(R.id.reload_abort).setTitle(R.string.refresh);
			menu.findItem(R.id.reload_abort).setIcon(R.drawable.ic_bar_refresh);
		}
		return true;
	}

	

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		
		switch(item.getItemId()) {
			case android.R.id.home:
				Intent intent = new Intent(this, ChooserActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(intent);
				return true;
			case R.id.reload_abort:
				if(canceler != null) {
					Log.d(DokuwikiApplication.LOGGER_NAME, "Abort loading");
					canceler.cancel();
					endLoading();
				} else {
					startLoadingPage();
				}
				return true;
			case R.id.search:
				onSearchRequested();
				return true;
			case R.id.preferences:
				startActivity(new Intent(this, Preferences.class));
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}

	}
	
	/**
	 * Setup the action bar. This will set the titles and enable the up navigation.
	 */
	private void setupActionBar() {

		ActionBar bar = getSupportActionBar();
		// Set subtitel to application name
		bar.setSubtitle(R.string.app_name);
		// Set titel to titel of dokuwiki
		bar.setTitle(manager.getDokuwiki().getTitle());
		// Enable up navigation over icon
		bar.setHomeButtonEnabled(true);
		bar.setDisplayHomeAsUpEnabled(true);
		
	}

	private void startLoadingPage() {
		
		if(getIntent().getStringExtra(PAGE_ID) != null) {
			manager.getPage(this, getIntent().getStringExtra(PAGE_ID));
		} else {
			manager.getPage(this, "start");
		}

	}

	private void showLoading(Canceler canceler) {
		this.canceler = canceler;
		handler.post(new Runnable() {

			public void run() {
				setSupportProgressBarIndeterminateVisibility(true);
				invalidateOptionsMenu();
			}
			
		});

	}

	private void endLoading() {
		canceler = null;
		handler.post(new Runnable() {

			public void run() {
				setSupportProgressBarIndeterminateVisibility(false);
				invalidateOptionsMenu();
			}
			
		});
	}

	public void onPageLoaded(Page page) {
		showPage(page);
	}

	private void showPage(Page page) {
		browser.loadPage(page);
	}
	
	public void onStartLoading(Canceler cancel, long id) {
		showLoading(cancel);
	}

	public void onEndLoading(long id) {
		endLoading();
	}

	public void onError(ErrorCode error, long id) {
		endLoading();
	}

	
}
