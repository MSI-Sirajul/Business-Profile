package com.business.profile;

import android.Manifest;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
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
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SharedPreferences sharedPreferences;
    private static final String PREF_NAME = "BusinessProfilePrefs";
    private static final String KEY_URL = "saved_url";
    private static final String KEY_FIRST_RUN = "first_run";
    private Handler handler;

    // UI Elements
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private LinearLayout errorLayout;
    private boolean isErrorState = false;

    // Upload Variables
    private ValueCallback<Uri[]> uploadMessage;
    private static final int REQUEST_SELECT_FILE = 100;
    private static final int PERMISSION_REQUEST_CODE = 200;

    // Lists (Loaded from Resources)
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
        checkFirstRun();
    }

    private void loadResources() {
        // strings.xml থেকে ডাটা লোড করা হচ্ছে
        externalAppPackages = Arrays.asList(getResources().getStringArray(R.array.external_app_packages));
        externalUrlPatterns = Arrays.asList(getResources().getStringArray(R.array.external_url_patterns));
        externalDomains = Arrays.asList(getResources().getStringArray(R.array.external_domains));
    }

    private void initializeViews() {
        webView = findViewById(R.id.webView);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        errorLayout = findViewById(R.id.errorLayout);
        Button btnRetry = findViewById(R.id.btnRetry);
        sharedPreferences = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

        btnRetry.setOnClickListener(v -> {
            if (isNetworkAvailable()) {
                hideErrorPage();
                webView.reload();
            } else {
                showToast(getString(R.string.msg_internet_unavailable));
            }
        });
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            webSettings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }

        // --- DOWNLOAD LISTENER ---
        webView.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                // ১. পারমিশন চেক করুন এবং প্রয়োজন হলে চান
                if (checkDownloadPermission()) {
                    // ২. পারমিশন থাকলে বা Android 10+ হলে ডাউনলোড শুরু
                    startSystemDownload(url, userAgent, contentDisposition, mimetype);
                }
            }
        });

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                progressBar.setVisibility(View.VISIBLE);
                isErrorState = false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
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
                     // Main frame only logic to prevent ad blocks triggering error page
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
                // Upload এর জন্য কোনো স্পেশাল পারমিশন Android 5.0+ এ লাগে না (Intent ব্যবহার করে)
                if (uploadMessage != null) {
                    uploadMessage.onReceiveValue(null);
                    uploadMessage = null;
                }
                uploadMessage = filePathCallback;

                Intent intent;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    intent = fileChooserParams.createIntent();
                } else {
                    intent = new Intent(Intent.ACTION_GET_CONTENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.setType("*/*");
                }
                
                try {
                    startActivityForResult(intent, REQUEST_SELECT_FILE);
                } catch (ActivityNotFoundException e) {
                    uploadMessage = null;
                    showToast(getString(R.string.msg_file_chooser_error));
                    return false;
                }
                return true;
            }
        });
    }

    // --- PERMISSION LOGIC ---
    private boolean checkDownloadPermission() {
        // Android 10 (Q) বা তার বেশি হলে পারমিশন দরকার নেই (Scoped Storage)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return true;
        }
        
        // Android 6 (M) থেকে Android 9 (Pie) পর্যন্ত পারমিশন লাগবে
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                
                // পারমিশন নেই, পপ-আপ দেখান
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        PERMISSION_REQUEST_CODE);
                return false;
            }
        }
        return true;
    }

    // পারমিশন রেজাল্ট হ্যান্ডেল করা
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                showToast(getString(R.string.msg_permission_granted));
            } else {
                showToast(getString(R.string.msg_permission_denied));
            }
        }
    }

    // --- SYSTEM DOWNLOAD MANAGER ---
    private void startSystemDownload(String url, String userAgent, String contentDisposition, String mimetype) {
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setMimeType(mimetype);
            
            // কুকিজ যোগ করা (গুরুত্বপূর্ণ)
            String cookies = CookieManager.getInstance().getCookie(url);
            request.addRequestHeader("cookie", cookies);
            request.addRequestHeader("User-Agent", userAgent);
            
            String fileName = URLUtil.guessFileName(url, contentDisposition, mimetype);
            request.setDescription("Downloading file...");
            request.setTitle(fileName);
            request.allowScanningByMediaScanner();
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            dm.enqueue(request);
            
            showToast(String.format(getString(R.string.msg_download_started), fileName));
            
        } catch (Exception e) {
            // যদি DownloadManager ফেইল করে, ব্রাউজারে ওপেন করার চেষ্টা
            try {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                startActivity(intent);
                showToast(getString(R.string.msg_opening_browser));
            } catch (Exception ex) {
                showToast("Error: " + ex.getMessage());
            }
        }
    }

    // --- UPLOAD HANDLING ---
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SELECT_FILE) {
            if (uploadMessage == null) return;
            uploadMessage.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            uploadMessage = null;
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    // --- HELPER METHODS ---
    private boolean handleExternalLinks(String url) {
        for (String pattern : externalUrlPatterns) {
            if (url.startsWith(pattern)) { openExternalApp(url); return true; }
        }
        if (shouldOpenExternally(url)) { openExternalApp(url); return true; }
        
        // Deep Links check from strings.xml logic can be done via shouldOpenExternally logic mainly
        // But for specific packages:
        for(String pkg : externalAppPackages) {
             // Basic implementation: if url contains distinctive part of pkg or known domain
             // Since we simplified, reliance on `shouldOpenExternally` is better
        }

        // Restoring specific checks as requested
        if ((url.contains("facebook.com") || url.contains("fb.com") || url.contains("web.facebook.com")) && isAppInstalled("com.facebook.katana")) {
            openExternalApp(url); return true;
        } else if ((url.contains("whatsapp.com") || url.contains("wa.me")) && isAppInstalled("com.whatsapp")) {
            openExternalApp(url); return true;
        } else if ((url.contains("telegram.com") || url.contains("t.me")) && isAppInstalled("org.telegram.messenger")) {
            openExternalApp(url); return true;
        } else if ((url.contains("tiktok.com") || url.contains("tiktok.me")) && isAppInstalled("com.zhiliaoapp.musically")) {
            openExternalApp(url); return true;
        } else if ((url.contains("messenger.com") || url.contains("m.me") || url.contains("fb.me")) && isAppInstalled("com.facebook.orca")) {
            openExternalApp(url); return true;
        } 
        

        if (url.startsWith("intent://")) {
             try {
                Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                if (intent.resolveActivity(getPackageManager()) != null) {
                    startActivity(intent);
                    return true;
                }
             } catch (Exception e) {}
        }

        webView.loadUrl(url);
        return true;
    }

    private boolean shouldOpenExternally(String url) {
        for (String domain : externalDomains) { if (url.contains(domain)) return true; }
        return false;
    }

    private void openExternalApp(String url) {
        try {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (intent.resolveActivity(getPackageManager()) != null) startActivity(intent);
            else {
                if(!url.startsWith("http")) showToast("App not installed");
                else webView.loadUrl(url);
            }
        } catch (Exception e) { webView.loadUrl(url); }
    }

    private boolean isAppInstalled(String packageName) {
        try { getPackageManager().getPackageInfo(packageName, 0); return true; }
        catch (PackageManager.NameNotFoundException e) { return false; }
    }

    private void checkFirstRun() {
        boolean isFirstRun = sharedPreferences.getBoolean(KEY_FIRST_RUN, true);
        String savedUrl = sharedPreferences.getString(KEY_URL, null);
        if (isFirstRun || savedUrl == null || savedUrl.isEmpty()) handler.postDelayed(this::showUrlConfigPopup, 300);
        else {
            if (isNetworkAvailable()) loadUrl(savedUrl);
            else showErrorPage();
        }
    }

    private void showUrlConfigPopup() {
        if (isFinishing()) return;
        View popupView = LayoutInflater.from(this).inflate(R.layout.popup_url_config, null);
        android.support.v7.app.AlertDialog.Builder builder = new android.support.v7.app.AlertDialog.Builder(this);
        builder.setView(popupView);
        builder.setCancelable(false);
        android.support.v7.app.AlertDialog dialog = builder.create();
        if (dialog.getWindow() != null) dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        EditText etUrl = popupView.findViewById(R.id.etUrl);
        Button btnSave = popupView.findViewById(R.id.btnSave);
        etUrl.requestFocus();
        btnSave.setOnClickListener(v -> {
            String url = etUrl.getText().toString().trim();
            if (!TextUtils.isEmpty(url)) {
                saveAndLoadUrl(url);
                if (dialog.isShowing()) dialog.dismiss();
            } else {
                etUrl.setError(getString(R.string.msg_enter_url_error));
                etUrl.requestFocus();
            }
        });
        try { dialog.show(); } catch (Exception e) { showToast(getString(R.string.msg_init_error)); }
    }

    private void saveAndLoadUrl(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "https://" + url;
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(KEY_URL, url);
        editor.putBoolean(KEY_FIRST_RUN, false);
        editor.apply();
        if (isNetworkAvailable()) { loadUrl(url); showToast(getString(R.string.msg_website_loaded)); }
        else showErrorPage();
    }

    private void loadUrl(String url) { webView.loadUrl(url); }
    private void showToast(String message) { Toast.makeText(this, message, Toast.LENGTH_SHORT).show(); }
    private void showErrorPage() { webView.setVisibility(View.GONE); errorLayout.setVisibility(View.VISIBLE); progressBar.setVisibility(View.GONE); swipeRefreshLayout.setRefreshing(false); }
    private void hideErrorPage() { webView.setVisibility(View.VISIBLE); errorLayout.setVisibility(View.GONE); }
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    
    @Override
    public void onBackPressed() {
        if (errorLayout.getVisibility() == View.VISIBLE) {
            String savedUrl = sharedPreferences.getString(KEY_URL, null);
            if (savedUrl != null && isNetworkAvailable()) { hideErrorPage(); webView.loadUrl(savedUrl); }
            else super.onBackPressed();
        } else if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
    
    @Override
    protected void onDestroy() {
        if (handler != null) handler.removeCallbacksAndMessages(null);
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}