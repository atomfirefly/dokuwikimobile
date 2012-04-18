package org.dokuwikimobile.manager;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.dokuwikimobile.Dokuwiki;
import org.dokuwikimobile.DokuwikiApplication;
import org.dokuwikimobile.cache.Cache;
import org.dokuwikimobile.listener.*;
import org.dokuwikimobile.model.LoginData;
import org.dokuwikimobile.model.Page;
import org.dokuwikimobile.model.SearchResult;
import org.dokuwikimobile.xmlrpc.DokuwikiXMLRPCClient;
import org.dokuwikimobile.xmlrpc.DokuwikiXMLRPCClient.Canceler;
import org.dokuwikimobile.xmlrpc.ErrorCode;


/**
 * The DokuwikiManager handles one complete Dokuwiki as a facade. 
 * It manages the PasswordManager, Caches and the DokuwikiXMLRPCClient to this 
 * Dokuwiki. This class can only exist once for each Dokuwiki.
 * 
 * It has a static factory method to receive or create the instance
 * for a specific Dokuwiki. The single instances are singleton classes, saved
 * in a singleton catalog.
 * 
 * @author Tim Roes <mail@timroes.de>
 */
public class DokuwikiManager {

	/// ==================
	/// STATIC METHODS
	/// ==================

	private static Map<Dokuwiki, DokuwikiManager> instances 
			= new HashMap<Dokuwiki, DokuwikiManager>();
	
	/**
	 * This will return the instance of DokuwikiManager for a specific Dokuwiki.
	 * If an instance of the specific DokuwikiManager already exist, it will be 
	 * returned. If not, it will be generated, stored and returned.
	 * 
	 * @param dokuwiki The Dokuwiki a manager should be returned for.
	 * @return The DokuwikiManager for the requested Dokuwiki.
	 */
	public static DokuwikiManager get(Dokuwiki dokuwiki) {

		// If this dokuwiki has already a DokuwikiManager, return it.
		if(instances.containsKey(dokuwiki)) {
			return instances.get(dokuwiki);
		}

		// Retrieve application context
		Context context = DokuwikiApplication.getAppContext();
		
		// Generate new DokuwikiManager
		DokuwikiManager manager = new DokuwikiManager();

		// Store dokuwiki information
		manager.dokuwiki = dokuwiki;
		// Initialize password manager for this dokuwiki
		manager.passwordManager = new PasswordManager(
				context.getSharedPreferences(dokuwiki.getMd5hash(), Context.MODE_PRIVATE));
		// Initialize the xmlrpc client
		manager.xmlrpcClient = new DokuwikiXMLRPCClient(dokuwiki.getUrl());
		
		// Pass login data to XMLRPC client
		if(manager.passwordManager.hasLoginData()) {
			manager.xmlrpcClient.setLoginData(manager.passwordManager.getLoginData());
		}

		// Create cache within cachedir/wikimd5hash/
		manager.cache = new Cache(new File(context.getCacheDir(), dokuwiki.getMd5hash()));

		// Save instance in singleton map
		instances.put(dokuwiki, manager);
		
		return manager;
		
	}

	/**
	 * Returns a collection of all currently instantiated dokuwikis.
	 * This is NOT necessarily a collection of all dokuwikis, saved in this app.
	 * Therefor use the Dokuwiki.getAll() method.
	 * 
	 * @see Dokuwiki
	 * 
	 * @return A collection of all instantiated dokuwikis.
	 */
	public static Collection<DokuwikiManager> getAll() {
		return instances.values();
	}


	/// ====================
	/// INSTANCE
	/// ====================
	
	private Dokuwiki dokuwiki;
	private PasswordManager passwordManager;
	private DokuwikiXMLRPCClient xmlrpcClient;
	private Cache cache;

	private Listener listener = new Listener();

	private DokuwikiManager() { }


	public Dokuwiki getDokuwiki() {
		return dokuwiki;
	}

	/// ====================
	/// CACHE FUNCTIONS
	/// ====================

	/**
	 * Returns the size of the cache in byte.
	 * 
	 * @return Size of cache used in bytes.
	 */
	public int getCacheSize() {
		return cache.getCacheSize();
	}

	/**
	 * Clear the whole cache of this dokuwiki. 
	 * This will delete all cached pages and media. Saved preferences will not 
	 * be touched.
	 */
	public void clearCache() {
		cache.clear();
	}

	/// ====================
	/// PAGE FUNCTIONS
	/// ====================

	public void getPage(PageListener listener, String pageName) {

		Canceler canceler = xmlrpcClient.getPageHTML(this.listener, pageName);
		this.listener.put(canceler.getId(), listener);
		listener.onStartLoading(canceler, canceler.getId());
		
	}

	/// ====================
	/// SEARCHING
	/// ====================

	/**
	 * Do a search in this dokuwiki. This will search for any pages containing
	 * the query string. The listener will be informed about the results.
	 * For a more detailed description of the dokuwiki search queries, have a 
	 * look at http://www.dokuwiki.org/search.
	 * 
	 * @param listener The listener, to be informed about search results.
	 * @param query The query search string.
	 */
	public void search(SearchListener listener, String query) {
		// TODO: Search in cache
		Canceler canceler = xmlrpcClient.search(this.listener, "*" + query + "*");
		this.listener.put(canceler.getId(), listener);
		listener.onStartLoading(canceler, canceler.getId());
		
	}

	/// ====================
	/// AUTHENTICATION
	/// ====================
	
	/**
	 * Login to this dokuwiki with the given login data. The listener will be 
	 * notified about the success or fail of the login.
	 * 
	 * @param listener Listener to be informed about result of login.
	 * @param login Login data to use.
	 * @param saveLogin Whether the login data should be saved for this dokuwiki.
	 */
	public void login(LoginListener listener, LoginData login, boolean saveLogin) {

		if(saveLogin) {
			// Save login data in password manager
			passwordManager.saveLoginData(login);
		}

		// Set login data in xmlrpc interface
		xmlrpcClient.setLoginData(login);

		Canceler canceler = xmlrpcClient.login(this.listener, login);
		this.listener.put(canceler.getId(), listener);
		listener.onStartLoading(canceler, canceler.getId());

	}

	/**
	 * Logs out the user from the dokuwiki.
	 */
	public void logout() {
		passwordManager.clearLoginData();
		xmlrpcClient.clearLoginData();
	}


	/// ====================
	/// LISTENER
	/// ====================
	
	/**
	 * The Listener class handles all callbacks from the XMLRPC interface.
	 * It stores a map of all call ids and their listeners. In the context of
	 * this subclass we call of the 'original listener' whenever we mean the listener, 
	 * that was passed to a call on the DokuwikiManager, and will be saved here
	 * for later callback.
	 */
	private class Listener implements LoginListener, SearchListener, PageHtmlListener {
		
		private Map<Long, CancelableListener> listeners = new HashMap<Long, CancelableListener>();

		/**
		 * Stores a listener with its corresponding id. This must be called after
		 * a call has been made, so this subclass can match the response to the listener.
		 * 
		 * @param id The id of the call.
		 * @param listener The listener, that wants to be noticed about the answer.
		 */
		public void put(long id, CancelableListener listener) {
			listeners.put(id, listener);
		}

		/**
		 * Gets the listener for a specific callback. This will take the callback id,
		 * the type of the listener expected to be returned.
		 * 
		 * @param id The id of the callback.
		 * @param listenerType The type of listener expected for that id.
		 * @return The listener with the given type or null if none has been found,
		 * 		or had the wrong type.
		 */
		private <T> T getListener(long id, Class<T> listenerType) {
			return getOrRemoveListener(id, listenerType, false);
		}

		/**
		 * Remove the Listener for a specific callback. This will take the callback id,
		 * the type of the listener expected to be returned. The listener will be removed
		 * from the list and returned.
		 * 
		 * @param id The id of the callback.
		 * @param listenerType The type of listener expected for that id.
		 * @return The listener with the given type or null if none has been found,
		 * 		or had the wrong type.
		 */
		private <T> T removeListener(long id, Class<T> listenerType) {
			return getOrRemoveListener(id, listenerType, true);
		}

		/**
		 * Gets or removes the listener for a specific callback. This will take the callback id,
		 * the type of the listener expected to be returned and whether the listener 
		 * should be removed from listener map.
		 * 
		 * @param id The id of the callback.
		 * @param listenerType The type of listener expected for that id.
		 * @param removeListener Whether the listener should be removed from listener map.
		 * @return The listener with the given type or null if none has been found,
		 * 		or had the wrong type.
		 */
		private <T> T getOrRemoveListener(long id, Class<T> listenerType, boolean removeListener) {
			
			CancelableListener listener = listeners.get(id);

			// If no listener existed, return null
			if(listener == null) {
				return null;
			}

			// If listener has wrong type, return null
			if(!listenerType.isInstance(listener)) {
				return null;
			}

			if(removeListener) {
				return (T)listeners.remove(id);
			}
			
			return (T)listener;
			
		}

		/**
		 * This method will be called, when the server finished with a login
		 * attempt.
		 * 
		 * @param succeeded Whether the login was successful or not.
		 * @param id The call id.
		 */
		public void onLogin(boolean succeeded, long id) {

			// If login data was wrong, delete it from password manager and xmlrpc
			// client
			if(!succeeded) {
				passwordManager.clearLoginData();
				xmlrpcClient.clearLoginData();
			}

			// Notify original listener
			LoginListener l;
			if((l = getListener(id, LoginListener.class)) != null) {
				l.onLogin(succeeded, id);
			}

			l.onEndLoading(id);

		}

		/**
		 * This method will be called, when the server returned some search results.
		 * 
		 * @param pages The list of search results found.
		 * @param id The call id.
		 */
		public void onSearchResults(List<SearchResult> pages, long id) {

			SearchListener l;
			if((l = getListener(id, SearchListener.class)) != null) {
				l.onSearchResults(pages, id);
			}

			l.onEndLoading(id);
			
		}
		
		public void onPageHtml(String html, long id) {

			PageListener l;
			if((l = getListener(id, PageListener.class)) != null) {
				Page p = new Page(html, null);
				l.onPageLoaded(p);
			}
			
			l.onEndLoading(id);

		}

		/**
		 * This method will never be called here, since the DokuwikiManager is
		 * responsible for generating the calls to onStartLoading.
		 * Check the interface documentation for more details.
		 * 
		 * @param cancel The canceler to cancel the call.
		 * @param id The id of the call.
		 */
		public void onStartLoading(Canceler cancel, long id) { }

		/**
		 * This method will never be called here, since the DokuwikiManager is 
		 * responsible for generating the calls to onEndLoading.
		 * Check the interface documentation for more details. 
		 * 
		 * @param id The id of the call.
		 */
		public void onEndLoading(long id) { }

		/**
		 * This method will be called if an error occurred during a call.
		 * The DokuwikiManager will just remove the listener for this call and
		 * forward the error message to it.
		 * 
		 * @param error The error returned from XMLRPC client.
		 * @param id The id of the call.
		 */
		public void onError(ErrorCode error, long id) {
			Log.e(DokuwikiApplication.LOGGER_NAME, "Error returned in DokuwikiManager '" + error.name() + "'");
			removeListener(id, CancelableListener.class).onError(error, id);
		}

	}

}
