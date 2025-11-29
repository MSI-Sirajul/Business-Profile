package com.business.profile;

import android.Manifest;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.ListPopupWindow;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.MimeTypeMap;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebStorage;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {
	
	private WebView webView;
	private SharedPreferences sharedPreferences;
	private static final String PREF_NAME = "BusinessProfilePrefs";
	private static final String KEY_URL = "saved_url";
	private static final String KEY_APP_NAME = "saved_app_name";
	
	private Handler handler;
	private Runnable connectionChecker;
	private Runnable reloadRunnable; // রিলোড ডিলে হ্যান্ডেল করার জন্য
	
	// UI Elements
	private SwipeRefreshLayout swipeRefreshLayout;
	private ProgressBar progressBar;
	private RelativeLayout loadingOverlay;
	private LinearLayout errorLayout;
	private ImageButton fabMenu;
	private boolean isErrorState = false;
	private boolean doubleBackToExitPressedOnce = false;
	private boolean isInitialLoad = true;
	
	// Colorful Spinner Variables
	private int[] colorArray = {Color.BLUE, Color.RED, Color.GREEN, Color.MAGENTA};
	private int colorIndex = 0;
	private Runnable colorRunnable;
	
	// Upload & Download Variables
	private ValueCallback<Uri[]> uploadMessage;
	private static final int REQUEST_SELECT_FILE = 100;
	private static final int PERMISSION_REQUEST_STORAGE = 200;
	private static final int PERMISSION_REQUEST_NOTIFICATION = 201;
	
	private String pendingUrl, pendingUserAgent, pendingContentDisposition, pendingMimetype;
	private List<String> externalAppPackages;
	private List<String> externalUrlPatterns;
	private List<String> externalDomains;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		handler = new Handler();
		loadResources();
		initializeViews();
		setupWebView();
		setupRefreshLayout();
		setupFabMenu();
		
		loadSavedUrl();
	}
	
	private void loadResources() {
		externalAppPackages = Arrays.asList(getResources().getStringArray(R.array.external_app_packages));
		externalUrlPatterns = Arrays.asList(getResources().getStringArray(R.array.external_url_patterns));
		externalDomains = Arrays.asList(getResources().getStringArray(R.array.external_domains));
	}
	
	private void initializeViews() {
		webView = findViewById(R.id.webView);
		swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
		progressBar = findViewById(R.id.progressBar);
		loadingOverlay = findViewById(R.id.loadingOverlay);
		errorLayout = findViewById(R.id.errorLayout);
		fabMenu = findViewById(R.id.fabMenu);
		Button btnRetry = findViewById(R.id.btnRetry);
		
		sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
		
		// ম্যানুয়াল রিট্রাই (সাথে সাথে লোড হবে)
		btnRetry.setOnClickListener(v -> {
			if (isNetworkAvailable()) {
				hideErrorPage();
				webView.reload();
				} else {
				showToast(getString(R.string.msg_internet_unavailable));
			}
		});
	}
	
	private void loadSavedUrl() {
		String url = sharedPreferences.getString(KEY_URL, null);
		if (url != null) {
			if (isNetworkAvailable()) {
				webView.loadUrl(url);
				} else {
				showErrorPage();
			}
			} else {
			// ডাটা না থাকলে সেটআপ পেজে পাঠান
			Intent intent = new Intent(MainActivity.this, SetupActivity.class);
			startActivity(intent);
			finish();
		}
	}
	
	// --- DYNAMIC COLORFUL SPINNER ---
	private void startColorfulSpinner() {
		final ProgressBar spinner = loadingOverlay.findViewById(R.id.loadingSpinner);
		if (spinner == null) return;
		
		colorRunnable = new Runnable() {
			@Override
			public void run() {
				if (loadingOverlay.getVisibility() == View.VISIBLE) {
					spinner.getIndeterminateDrawable().setColorFilter(
					colorArray[colorIndex % colorArray.length],
					PorterDuff.Mode.SRC_IN
					);
					colorIndex++;
					handler.postDelayed(this, 500);
				}
			}
		};
		handler.post(colorRunnable);
	}
	
	private void stopColorfulSpinner() {
		if (colorRunnable != null) {
			handler.removeCallbacks(colorRunnable);
			colorRunnable = null;
		}
	}
	
	// --- CUSTOM POPUP MENU (ListPopupWindow) ---
	private void setupFabMenu() {
		fabMenu.setOnClickListener(v -> showCustomPopupMenu(v));
	}
	
	private void showCustomPopupMenu(View anchorView) {
		final ListPopupWindow popupWindow = new ListPopupWindow(this);
		
		List<CustomMenuItem> menuItems = new ArrayList<>();
		menuItems.add(new CustomMenuItem(0, getString(R.string.action_info), R.drawable.info));
		menuItems.add(new CustomMenuItem(1, getString(R.string.action_share), R.drawable.share));
		menuItems.add(new CustomMenuItem(2, getString(R.string.action_clear_cache), R.drawable.clear));
		menuItems.add(new CustomMenuItem(3, getString(R.string.action_reset), R.drawable.reset));
		
		CustomMenuAdapter adapter = new CustomMenuAdapter(this, menuItems);
		popupWindow.setAdapter(adapter);
		popupWindow.setAnchorView(anchorView);
		
		float density = getResources().getDisplayMetrics().density;
		popupWindow.setWidth((int)(160 * density));
		popupWindow.setBackgroundDrawable(ContextCompat.getDrawable(this, R.drawable.bg_popup_rounded));
		popupWindow.setHorizontalOffset((int)(-10 * density));
		popupWindow.setVerticalOffset((int)(5 * density));
		popupWindow.setModal(true);
		
		popupWindow.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				CustomMenuItem item = menuItems.get(position);
				switch (item.id) {
					case 0: startActivity(new Intent(MainActivity.this, InfoActivity.class)); break;
					case 1: shareAppUrl(); break;
					case 2: clearAppCache(); break;
					case 3: showResetConfirmation(); break;
				}
				popupWindow.dismiss();
			}
		});
		popupWindow.show();
	}
	
	// Helper Classes
	private static class CustomMenuItem {
		int id; String title; int iconRes;
		CustomMenuItem(int id, String title, int iconRes) { this.id = id; this.title = title; this.iconRes = iconRes; }
	}
	
	private class CustomMenuAdapter extends BaseAdapter {
		private Context context; private List<CustomMenuItem> items;
		CustomMenuAdapter(Context context, List<CustomMenuItem> items) { this.context = context; this.items = items; }
		@Override public int getCount() { return items.size(); }
		@Override public Object getItem(int position) { return items.get(position); }
		@Override public long getItemId(int position) { return position; }
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {
			if (convertView == null) convertView = LayoutInflater.from(context).inflate(R.layout.item_popup_menu, parent, false);
			ImageView icon = convertView.findViewById(R.id.menuIcon);
			TextView title = convertView.findViewById(R.id.menuTitle);
			CustomMenuItem item = items.get(position);
			icon.setImageResource(item.iconRes);
			title.setText(item.title);
			return convertView;
		}
	}
	
	private void shareAppUrl() {
		String currentUrl = webView.getUrl();
		if (currentUrl == null) currentUrl = sharedPreferences.getString(KEY_URL, "");
		String appName = sharedPreferences.getString(KEY_APP_NAME, getString(R.string.app_name));
		Intent shareIntent = new Intent(Intent.ACTION_SEND);
		shareIntent.setType("text/plain");
		shareIntent.putExtra(Intent.EXTRA_SUBJECT, appName);
		shareIntent.putExtra(Intent.EXTRA_TEXT, "Check out " + appName + ": " + currentUrl);
		startActivity(Intent.createChooser(shareIntent, "Share via"));
	}
	
	private void clearAppCache() {
		webView.clearCache(true);
		webView.clearHistory();
		showToast(getString(R.string.msg_cache_cleared));
		webView.reload();
	}
	
	// --- CUSTOM RESET DIALOG ---
	private void showResetConfirmation() {
		View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_reset, null);
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setView(dialogView);
		builder.setCancelable(false);
		AlertDialog dialog = builder.create();
		
		if (dialog.getWindow() != null) {
			dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
		}
		
		Button btnCancel = dialogView.findViewById(R.id.btnCancel);
		Button btnConfirm = dialogView.findViewById(R.id.btnConfirmReset);
		
		btnCancel.setOnClickListener(v -> dialog.dismiss());
		btnConfirm.setOnClickListener(v -> {
			dialog.dismiss();
			resetApplication();
		});
		dialog.show();
	}
	
	private void resetApplication() {
		SharedPreferences.Editor editor = sharedPreferences.edit();
		editor.clear();
		boolean success = editor.commit();
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			CookieManager.getInstance().removeAllCookies(null);
			CookieManager.getInstance().flush();
			} else {
			CookieManager.getInstance().removeAllCookie();
		}
		
		webView.clearCache(true);
		webView.clearHistory();
		WebStorage.getInstance().deleteAllData();
		
		if (success) {
			showToast("Resetting app...");
			handler.postDelayed(() -> {
				Intent intent = new Intent(MainActivity.this, SetupActivity.class);
				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
				startActivity(intent);
				finish();
			}, 500);
		}
	}
	
	private void setupRefreshLayout() {
		swipeRefreshLayout.setOnRefreshListener(() -> {
			if (isNetworkAvailable()) {
				hideErrorPage();
				webView.reload();
				} else {
				swipeRefreshLayout.setRefreshing(false);
				showErrorPage();
			}
		});
		swipeRefreshLayout.setColorSchemeColors(Color.BLUE, Color.RED, Color.GREEN);
	}
	
	private void setupWebView() {
		WebSettings webSettings = webView.getSettings();
		webSettings.setJavaScriptEnabled(true);
		webSettings.setDomStorageEnabled(true);
		webSettings.setAllowFileAccess(true);
		webSettings.setAllowContentAccess(true);
		webSettings.setAllowFileAccessFromFileURLs(true);
		webSettings.setAllowUniversalAccessFromFileURLs(true);
		webSettings.setUseWideViewPort(true);
		webSettings.setLoadWithOverviewMode(true);
		webSettings.setBuiltInZoomControls(true);
		webSettings.setDisplayZoomControls(false);
		
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			webSettings.setSafeBrowsingEnabled(true);
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
			webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
			CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
		}
		
		webView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
			pendingUrl = url;
			pendingUserAgent = userAgent;
			pendingContentDisposition = contentDisposition;
			pendingMimetype = mimetype;
			if (checkDownloadRequirements()) {
				startSystemDownload(url, userAgent, contentDisposition, mimetype);
			}
		});
		
		webView.setWebViewClient(new WebViewClient() {
			@Override
			public void onPageStarted(WebView view, String url, Bitmap favicon) {
				if (isInitialLoad) {
					loadingOverlay.setVisibility(View.VISIBLE);
					startColorfulSpinner();
					} else {
					progressBar.setVisibility(View.VISIBLE);
				}
				isErrorState = false;
				stopConnectionChecker(); // লোডিং শুরু হলে চেকার বন্ধ করুন
			}
			
			@Override
			public void onPageFinished(WebView view, String url) {
				progressBar.setVisibility(View.GONE);
				loadingOverlay.setVisibility(View.GONE);
				stopColorfulSpinner();
				swipeRefreshLayout.setRefreshing(false);
				isInitialLoad = false;
				if (!isErrorState) hideErrorPage();
			}
			
			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				return handleExternalLinks(url);
			}
			
			@Override
			public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
				if (failingUrl != null && !failingUrl.startsWith("http")) return;
				isErrorState = true;
				showErrorPage();
			}
			
			@Override
			public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
					if (request.isForMainFrame()) {
						isErrorState = true;
						showErrorPage();
					}
				}
			}
		});
		
		webView.setWebChromeClient(new WebChromeClient() {
			@Override
			public void onProgressChanged(WebView view, int newProgress) {
				progressBar.setProgress(newProgress);
				if (newProgress == 100) progressBar.setVisibility(View.GONE);
			}
			@Override
			public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
				if (uploadMessage != null) { uploadMessage.onReceiveValue(null); uploadMessage = null; }
				uploadMessage = filePathCallback;
				Intent intent;
				if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) { intent = fileChooserParams.createIntent(); }
				else { intent = new Intent(Intent.ACTION_GET_CONTENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*"); }
				try { startActivityForResult(intent, REQUEST_SELECT_FILE); }
				catch (ActivityNotFoundException e) { uploadMessage = null; showToast(getString(R.string.msg_file_chooser_error)); return false; }
				return true;
			}
		});
	}
	
	// --- AUTO RECONNECT LOGIC WITH 3 SECOND DELAY ---
	private void startConnectionChecker() {
		if (connectionChecker == null) {
			connectionChecker = new Runnable() {
				@Override
				public void run() {
					if (isNetworkAvailable()) {
						// কানেকশন পাওয়া গেছে, কিন্তু ৩ সেকেন্ড অপেক্ষা করব
						if (reloadRunnable == null) {
							reloadRunnable = new Runnable() {
								@Override
								public void run() {
									// ৩ সেকেন্ড পরও যদি নেট থাকে, তবেই রিলোড
									if (isNetworkAvailable()) {
										hideErrorPage();
										webView.reload();
										stopConnectionChecker();
										} else {
										// নেট চলে গেছে, আবার চেকার চালু রাখা হবে
										reloadRunnable = null;
									}
								}
							};
							handler.postDelayed(reloadRunnable, 3000); // ৩ সেকেন্ড ডিলে
						}
						} else {
						// নেট নেই, তাই পেন্ডিং রিলোড থাকলে বাতিল করুন
						if (reloadRunnable != null) {
							handler.removeCallbacks(reloadRunnable);
							reloadRunnable = null;
						}
						// আবার ১ সেকেন্ড পর চেক করুন
						handler.postDelayed(this, 1000);
					}
				}
			};
			handler.post(connectionChecker);
		}
	}
	
	private void stopConnectionChecker() {
		if (connectionChecker != null) {
			handler.removeCallbacks(connectionChecker);
			connectionChecker = null;
		}
		if (reloadRunnable != null) {
			handler.removeCallbacks(reloadRunnable);
			reloadRunnable = null;
		}
	}
	
	private boolean checkDownloadRequirements() {
		if (Build.VERSION.SDK_INT >= 33) {
			String permissionPostNotifications = "android.permission.POST_NOTIFICATIONS";
			if (ContextCompat.checkSelfPermission(this, permissionPostNotifications) != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this, new String[]{permissionPostNotifications}, PERMISSION_REQUEST_NOTIFICATION);
				return false;
			}
			return true;
		}
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return true;
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_STORAGE);
				return false;
			}
		}
		return true;
	}
	
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		if (requestCode == PERMISSION_REQUEST_STORAGE || requestCode == PERMISSION_REQUEST_NOTIFICATION) {
			if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
				if (pendingUrl != null) startSystemDownload(pendingUrl, pendingUserAgent, pendingContentDisposition, pendingMimetype);
				} else {
				if (requestCode == PERMISSION_REQUEST_NOTIFICATION) {
					if (pendingUrl != null) startSystemDownload(pendingUrl, pendingUserAgent, pendingContentDisposition, pendingMimetype);
				} else showToast(getString(R.string.msg_permission_denied));
			}
		}
	}
	
	private void startSystemDownload(String url, String userAgent, String contentDisposition, String mimetype) {
		if (url.startsWith("blob:")) { showToast("Blob downloads not supported."); return; }
		try {
			DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
			if (mimetype == null || mimetype.trim().isEmpty()) mimetype = "*/*";
			String fileExtension = MimeTypeMap.getFileExtensionFromUrl(url);
			if (fileExtension != null && !fileExtension.isEmpty()) {
				String type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension);
				if (type != null) mimetype = type;
			}
			request.setMimeType(mimetype);
			String cookies = CookieManager.getInstance().getCookie(url);
			if (cookies != null && !cookies.isEmpty()) request.addRequestHeader("cookie", cookies);
			if (userAgent == null || userAgent.isEmpty()) userAgent = webView.getSettings().getUserAgentString();
			request.addRequestHeader("User-Agent", userAgent);
			String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
			request.setDescription("Downloading file...");
			request.setTitle(fileName);
			request.allowScanningByMediaScanner();
			request.setAllowedNetworkTypes(DownloadManager.Request.NETWORK_WIFI | DownloadManager.Request.NETWORK_MOBILE);
			request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
			request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
			DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
			if (dm != null) { dm.enqueue(request); showToast(String.format(getString(R.string.msg_download_started), fileName)); }
			else { throw new Exception("Download Manager not found"); }
			} catch (Exception e) {
			try { Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url)); startActivity(intent); }
			catch (Exception ex) { showToast("Download Error: " + e.getMessage()); }
		}
		pendingUrl = null;
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (requestCode == REQUEST_SELECT_FILE) {
			if (uploadMessage == null) return;
			uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
			uploadMessage = null;
		} else { super.onActivityResult(requestCode, resultCode, data); }
	}
	
	private boolean handleExternalLinks(String url) {
		for (String pattern : externalUrlPatterns) { if (url.startsWith(pattern)) { openExternalApp(url); return true; } }
		if (url.startsWith("intent://")) {
			try {
				Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
				if (intent.resolveActivity(getPackageManager()) != null) { startActivity(intent); return true; }
				String fallbackUrl = intent.getStringExtra("browser_fallback_url");
				if (fallbackUrl != null) { webView.loadUrl(fallbackUrl); return true; }
			} catch (Exception e) {} return true;
		}
		if (shouldOpenExternally(url)) { openExternalApp(url); return true; }
		if ((url.contains("facebook.com") || url.contains("fb.com")) && isAppInstalled("com.facebook.katana")) { openExternalApp(url); return true; }
		else if ((url.contains("whatsapp.com") || url.contains("wa.me")) && isAppInstalled("com.whatsapp")) { openExternalApp(url); return true; }
		return false;
	}
	
	private boolean shouldOpenExternally(String url) { for (String domain : externalDomains) { if (url.contains(domain)) return true; } return false; }
	private void openExternalApp(String url) {
		try { Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url)); intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(intent); }
		catch (Exception e) { if(url.startsWith("http")) webView.loadUrl(url); else showToast("App not installed"); }
	}
	private boolean isAppInstalled(String packageName) {
		try { getPackageManager().getPackageInfo(packageName, 0); return true; } catch (PackageManager.NameNotFoundException e) { return false; }
	}
	private void showErrorPage() {
		webView.setVisibility(View.GONE);
		errorLayout.setVisibility(View.VISIBLE);
		loadingOverlay.setVisibility(View.GONE);
		progressBar.setVisibility(View.GONE);
		swipeRefreshLayout.setRefreshing(false);
		startConnectionChecker();
	}
	private void hideErrorPage() { webView.setVisibility(View.VISIBLE); errorLayout.setVisibility(View.GONE); stopConnectionChecker(); }
	private boolean isNetworkAvailable() { ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE); NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo(); return activeNetworkInfo != null && activeNetworkInfo.isConnected(); }
	private void showToast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
	@Override
	public void onBackPressed() {
		if (errorLayout.getVisibility() == View.VISIBLE) {
			String savedUrl = sharedPreferences.getString(KEY_URL, null);
			if (savedUrl != null && isNetworkAvailable()) { hideErrorPage(); webView.loadUrl(savedUrl); } else handleExit();
		} else if (webView.canGoBack()) { webView.goBack(); } else { handleExit(); }
	}
	private void handleExit() {
		if (doubleBackToExitPressedOnce) { super.onBackPressed(); return; }
		this.doubleBackToExitPressedOnce = true;
		showToast(getString(R.string.msg_exit_toast));
		new Handler().postDelayed(() -> doubleBackToExitPressedOnce = false, 2000);
	}
	@Override
	protected void onDestroy() {
		if (handler != null) handler.removeCallbacksAndMessages(null);
		if (webView != null) webView.destroy();
		stopConnectionChecker();
		stopColorfulSpinner();
		super.onDestroy();
	}
}