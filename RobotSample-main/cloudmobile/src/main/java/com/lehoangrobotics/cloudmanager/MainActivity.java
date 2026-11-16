package com.lehoangrobotics.cloudmanager;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_NAME = "LeHoangCloudPrefs";
    private static final String KEY_SERVER_URL = "server_url";
    private static final String DEFAULT_URL = "http://10.0.2.2:5050"; // default for emulator / intranet

    private WebView mWebView;
    private SwipeRefreshLayout mSwipeRefresh;
    private ProgressBar mProgressBar;
    private LinearLayout mLayoutError;
    private TextView mTvCurrentServer;
    private TextView mTvErrorDetail;
    private Button mBtnRetry;
    private Button mBtnConfigServer;
    private ImageButton mBtnSettings;

    private SharedPreferences mPrefs;
    private String mCurrentUrl;
    private boolean mHasError = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mPrefs = getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mCurrentUrl = mPrefs.getString(KEY_SERVER_URL, "http://192.168.0.125:5050");

        initViews();
        setupWebView();
        loadCloudPortal();
    }

    private void initViews() {
        mWebView = findViewById(R.id.webView);
        mSwipeRefresh = findViewById(R.id.swipeRefresh);
        mProgressBar = findViewById(R.id.progressBar);
        mLayoutError = findViewById(R.id.layoutError);
        mTvCurrentServer = findViewById(R.id.tvCurrentServer);
        mTvErrorDetail = findViewById(R.id.tvErrorDetail);
        mBtnRetry = findViewById(R.id.btnRetry);
        mBtnConfigServer = findViewById(R.id.btnConfigServer);
        mBtnSettings = findViewById(R.id.btnSettings);

        mTvCurrentServer.setText("Cloud: " + mCurrentUrl);

        mSwipeRefresh.setColorSchemeColors(0xFF00F2FE, 0xFF06B6D4);
        mSwipeRefresh.setProgressBackgroundColorSchemeColor(0xFF0F172A);
        mSwipeRefresh.setOnRefreshListener(() -> {
            mHasError = false;
            mWebView.reload();
        });

        mBtnRetry.setOnClickListener(v -> loadCloudPortal());
        mBtnConfigServer.setOnClickListener(v -> showServerConfigDialog());
        mBtnSettings.setOnClickListener(v -> showServerConfigDialog());
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAppCacheEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        }

        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress < 100) {
                    mProgressBar.setVisibility(View.VISIBLE);
                    mProgressBar.setProgress(newProgress);
                } else {
                    mProgressBar.setVisibility(View.GONE);
                    mSwipeRefresh.setRefreshing(false);
                }
            }
        });

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                mHasError = false;
                mLayoutError.setVisibility(View.GONE);
                mWebView.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                mSwipeRefresh.setRefreshing(false);
                if (!mHasError) {
                    mLayoutError.setVisibility(View.GONE);
                    mWebView.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    if (request.isForMainFrame()) {
                        showError("Lỗi kết nối: " + error.getDescription());
                    }
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // Allow self-signed certs in intranet testing
                handler.proceed();
            }
        });
    }

    private void loadCloudPortal() {
        mHasError = false;
        mLayoutError.setVisibility(View.GONE);
        mWebView.setVisibility(View.VISIBLE);
        mTvCurrentServer.setText("Cloud: " + mCurrentUrl);
        mWebView.loadUrl(mCurrentUrl);
    }

    private void showError(String msg) {
        mHasError = true;
        mSwipeRefresh.setRefreshing(false);
        mWebView.setVisibility(View.GONE);
        mLayoutError.setVisibility(View.VISIBLE);
        mTvErrorDetail.setText(msg + "\nMáy chủ: " + mCurrentUrl);
    }

    private void showServerConfigDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Cài đặt Máy chủ Cloud");
        builder.setMessage("Nhập địa chỉ máy chủ Cloud trung tâm (IP hoặc Domain):");

        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setText(mCurrentUrl);
        input.setSelection(mCurrentUrl.length());
        builder.setView(input);

        builder.setPositiveButton("Lưu & Kết nối", (dialog, which) -> {
            String newUrl = input.getText().toString().trim();
            if (!newUrl.startsWith("http://") && !newUrl.startsWith("https://")) {
                newUrl = "http://" + newUrl;
            }
            if (!newUrl.contains(":") && !newUrl.endsWith(".com") && !newUrl.endsWith(".vn")) {
                newUrl += ":5050";
            }
            mCurrentUrl = newUrl;
            mPrefs.edit().putString(KEY_SERVER_URL, mCurrentUrl).apply();
            Toast.makeText(MainActivity.this, "Đã lưu máy chủ: " + mCurrentUrl, Toast.LENGTH_SHORT).show();
            loadCloudPortal();
        });

        builder.setNegativeButton("Hủy", (dialog, which) -> dialog.cancel());
        builder.show();
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}

