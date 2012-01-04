package de.timroes.dokuapp.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.os.Bundle;
import android.text.Html;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;
import de.timroes.dokuapp.R;
import de.timroes.dokuapp.Settings;
import de.timroes.dokuapp.content.Page;
import de.timroes.dokuapp.services.DokuwikiService;
import de.timroes.dokuapp.content.DokuwikiUrl;
import de.timroes.dokuapp.services.LoadingListener;
import de.timroes.dokuapp.views.DokuwikiWebView;
import de.timroes.dokuapp.views.DokuwikiWebView.ScrollListener;
import de.timroes.dokuapp.views.MessageView;
import de.timroes.dokuapp.xmlrpc.DokuwikiXMLRPCClient.Canceler;

public class BrowserActivity extends DokuwikiActivity implements ScrollListener, 
		DokuwikiWebView.LinkLoadListener, LoadingListener {

	public final static String PAGEID = "pageid";

	private final static int DIALOG_PAGEINFO = 1;
	
	private DokuwikiWebView browser;
	private MessageView message;

	private Canceler canceler;

	/**
	 * This contains the link to the currently requested page. The page
	 * must not be loaded yet, but at least the user clicked a link to it.
	 * This is saved here to make sure, we only load pages into the webview,
	 * that the user still want to see.
	 */
	private DokuwikiUrl currentRequested;

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.browser);
		browser = (DokuwikiWebView)findViewById(R.id.mainbrowser);
		message = (MessageView)findViewById(R.id.message);
		
		browser.setLinkLoadListener(this);
		browser.setScrollListener(this);
		message.setWebView(browser);
	}

	@Override
	public void onServiceBound(DokuwikiService service) {
		super.onServiceBound(service);
		// If no page has been requested; first start!
		if(getIntent().getStringExtra(PAGEID) != null) {
			// If page id was in intent, use it to open page and delete it afterwards
			displayPage(DokuwikiUrl.parseUrl(getIntent().getStringExtra(PAGEID)));
			getIntent().removeExtra(PAGEID);
		} else if(currentRequested == null) {
			displayPage(new DokuwikiUrl(Settings.HOME));
		} 
	}

	private void reload() {
		displayPage(currentRequested);
	}

	private synchronized void displayPage(DokuwikiUrl url) {
		if(url == null)
			return;

		message.showLoading();

		currentRequested = url;
		connector.getService().getPage(this, this, url.id);
		Toast.makeText(this, "Loading page...", Toast.LENGTH_SHORT).show();
	}

	private String getPageInfoMessage(Page page) {
		if(page == null)
			return "";

		return getResources().getString(R.string.pageinfo_dialog, 
				page.getPageInfo().getName(),
				page.getPageInfo().getAuthor(),
				page.getPageInfo().getLastModified().toLocaleString());
	}

	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		switch(id) {
			case DIALOG_PAGEINFO:
				((AlertDialog)dialog).setMessage(
						Html.fromHtml(getPageInfoMessage(browser.getPage())));
				break;
		}
		super.onPrepareDialog(id, dialog);
	}

	@Override
	protected Dialog onCreateDialog(int id) {
		switch(id) {
			case DIALOG_PAGEINFO:
				// Create Pageinfo dialog
				AlertDialog.Builder builder = new AlertDialog.Builder(this);
				builder.setTitle(R.string.pageinfo);
				builder.setMessage(Html.fromHtml(getPageInfoMessage(browser.getPage())));
				builder.setPositiveButton(R.string.ok, new OnClickListener() {
					public void onClick(DialogInterface dialog, int id) {
						dialog.dismiss();
					}
				});
				return builder.create();
			default:
				return super.onCreateDialog(id);
		}
	}

	@Override
	protected void onPageLoadedCallback(Page page) {
		super.onPageLoadedCallback(page);

		if(page == null)
			return;

		// TODO Improve
		if(currentRequested != null
				&& page.getPageName().equals(currentRequested.id)) {
			browser.loadPage(page);
			// TODO: scroll to anchor
			browser.scrollTo(0, 0);
		}
		message.setMessage(MessageView.Type.SUCCESS, page.getPageInfo().toString());
		Toast.makeText(this, "Page loaded.", Toast.LENGTH_SHORT).show();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.browser, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public void onBackPressed() {
		if(browser.canGoBack()) {
			browser.goBack();
		} else {
			finish();
		}
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		menu.findItem(R.id.pageinfo).setEnabled(browser.getPage() != null);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if(item.getItemId() == R.id.realod) {
			reload();
		} else if(item.getItemId() == R.id.preferences) {
			startActivity(new Intent(this, Preferences.class));
		} else if(item.getItemId() == R.id.pageinfo) {
			showDialog(DIALOG_PAGEINFO);
		} else if(item.getItemId() == R.id.search) {
			onSearchRequested();
		} else {
			return super.onOptionsItemSelected(item);
		}

		return false;
	}

	@Override
	public void loginDialogFinished(FinishState state) {
		super.loginDialogFinished(state);
		reload();
	}

	public void onScroll(int l, int t, int oldl, int oldt) {
	}

	public void onInternalLinkLoad(DokuwikiWebView webview, DokuwikiUrl link) {
		displayPage(link);
	}

	public boolean onExternalLinkLoad(DokuwikiWebView webview, String link) {
		// We don't need to handle any external links. Let android handle them for us.
		return false;
	}

	public void onHistoryLoaded(DokuwikiWebView webview, DokuwikiUrl link) {
		// If page has been loaded from history, change currentRequest to that
		// page. So a page that is currently loaded wont show up in webview
		// since user has allready decided otherwise.
		currentRequested = link;
	}

	public void startLoading(Canceler cancel) {
		this.canceler = cancel;
	}

	public void endLoading() {
		message.hideLoading();
	}

}