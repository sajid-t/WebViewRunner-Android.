package live.pwthor.webviewrunner;

import android.annotation.SuppressLint;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

public class MainActivity extends AppCompatActivity {
    private static final String HOME_URL = "https://pwthor.live/study/batches";
    private static final long SCRIPT_TIMEOUT_MS = 10_000L;

    /* ================================================================
       AUTHORIZED SCRIPT CONFIGURATION
       Replace only this constant with an authorized script body.
       It runs inside the currently loaded WebView page. Do not add
       third-party click automation, access-control bypasses, or an
       external bypass script here.
       ================================================================ */
    private static final String AUTHORIZED_SCRIPT =
            "var banner = document.getElementById('webviewrunner-test-banner');"
          + "if (!banner) { banner = document.createElement('div'); banner.id = 'webviewrunner-test-banner'; }"
          + "banner.textContent = 'WebViewRunner test script executed';"
          + "banner.style.cssText = 'position:fixed;z-index:2147483647;top:16px;left:16px;right:16px;padding:12px 14px;border-radius:10px;background:#3885ff;color:#ffffff;font:600 14px sans-serif;box-shadow:0 8px 28px rgba(0,0,0,.25);';"
          + "document.body.insertBefore(banner, document.body.firstChild);"
          + "return { ok: true, message: banner.textContent, source: 'configured-test-script' };";
    /* ================================================================ */

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private WebView webView;
    private ProgressBar pageProgress;
    private ProgressBar scriptSpinner;
    private Button runScriptButton;
    private TextView emptyState;
    private TextView statusTitle;
    private TextView statusDetail;
    private TextView statusDot;
    private boolean pageReady = false;
    private boolean scriptRunning = false;
    private long currentRunToken = 0L;
    private Runnable scriptTimeout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.web_view);
        pageProgress = findViewById(R.id.page_progress);
        scriptSpinner = findViewById(R.id.script_spinner);
        runScriptButton = findViewById(R.id.run_script_button);
        emptyState = findViewById(R.id.empty_state);
        statusTitle = findViewById(R.id.status_title);
        statusDetail = findViewById(R.id.status_detail);
        statusDot = findViewById(R.id.status_dot);

        runScriptButton.setEnabled(false);
        scriptSpinner.setVisibility(View.GONE);
        findViewById(R.id.back_button).setOnClickListener(view -> navigateBack());
        findViewById(R.id.reload_button).setOnClickListener(view -> webView.reload());
        findViewById(R.id.home_button).setOnClickListener(view -> webView.loadUrl(HOME_URL));
        runScriptButton.setOnClickListener(view -> startAuthorizedScript());

        configureWebView();
        setStatus("Loading target page...", "The runner will be available after the native WebView finishes loading.", StatusTone.IDLE);
        webView.loadUrl(HOME_URL);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void configureWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new RunnerWebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                pageProgress.setProgress(newProgress);
                pageProgress.setVisibility(newProgress >= 100 ? View.GONE : View.VISIBLE);
            }
        });
    }

    private void navigateBack() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            setStatus("At start of history", "There is no earlier page in the native WebView history.", StatusTone.IDLE);
        }
    }

    private void startAuthorizedScript() {
        if (scriptRunning) return;
        if (!pageReady) {
            setStatus("Script Failed", "Wait for the target page to finish loading before running the configured script.", StatusTone.ERROR);
            return;
        }

        scriptRunning = true;
        final long runToken = ++currentRunToken;
        scriptSpinner.setVisibility(View.VISIBLE);
        runScriptButton.setEnabled(false);
        runScriptButton.setText(R.string.running_script);
        setStatus("Running Script...", "Evaluating the configured authorized script on the current page.", StatusTone.RUNNING);

        // The configured body is wrapped only to capture a structured result and errors.
        String wrappedScript = "(function(){try{var result=(function(){"
                + AUTHORIZED_SCRIPT
                + "})();return JSON.stringify({ok:true,result:result===undefined?null:result});}catch(error){return JSON.stringify({ok:false,error:String(error&&error.message?error.message:error)});}})();";

        scriptTimeout = () -> {
            if (scriptRunning && runToken == currentRunToken) {
                finishScriptFailure("The script timed out after 10 seconds.");
            }
        };
        mainHandler.postDelayed(scriptTimeout, SCRIPT_TIMEOUT_MS);

        webView.evaluateJavascript(wrappedScript, value -> {
            if (!scriptRunning || runToken != currentRunToken) return;
            mainHandler.removeCallbacks(scriptTimeout);
            handleEvaluationResult(value);
        });
    }

    private void handleEvaluationResult(String rawValue) {
        try {
            Object decoded = new JSONTokener(rawValue == null ? "null" : rawValue).nextValue();
            if (!(decoded instanceof String)) {
                finishScriptFailure("The page returned an unreadable execution result.");
                return;
            }
            JSONObject payload = new JSONObject((String) decoded);
            if (!payload.optBoolean("ok", false)) {
                finishScriptFailure(payload.optString("error", "The configured script reported an error."));
                return;
            }
            JSONObject result = payload.optJSONObject("result");
            String message = result == null ? "Authorized script returned a result." : result.optString("message", "Authorized script returned a result.");
            finishScriptSuccess(message);
        } catch (JSONException | NullPointerException exception) {
            finishScriptFailure("Could not parse the JavaScript execution result.");
        }
    }

    private void finishScriptSuccess(String resultMessage) {
        scriptRunning = false;
        scriptSpinner.setVisibility(View.GONE);
        runScriptButton.setEnabled(pageReady);
        runScriptButton.setText(R.string.run_script);
        setStatus("Script Completed", resultMessage, StatusTone.SUCCESS);
    }

    private void finishScriptFailure(String reason) {
        if (scriptTimeout != null) mainHandler.removeCallbacks(scriptTimeout);
        scriptRunning = false;
        currentRunToken++;
        scriptSpinner.setVisibility(View.GONE);
        runScriptButton.setEnabled(pageReady);
        runScriptButton.setText(R.string.run_script);
        setStatus("Script Failed", reason, StatusTone.ERROR);
    }

    private void setStatus(String title, String detail, StatusTone tone) {
        statusTitle.setText(title);
        statusDetail.setText(detail);
        int color = tone == StatusTone.SUCCESS ? getColor(R.color.runner_green)
                : tone == StatusTone.ERROR ? getColor(R.color.runner_red)
                : tone == StatusTone.RUNNING ? getColor(R.color.runner_blue)
                : getColor(R.color.runner_muted);
        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(color);
        statusDot.setBackground(dot);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (scriptTimeout != null) mainHandler.removeCallbacks(scriptTimeout);
        if (webView != null) {
            webView.stopLoading();
            webView.destroy();
        }
        super.onDestroy();
    }

    private enum StatusTone { IDLE, RUNNING, SUCCESS, ERROR }

    private final class RunnerWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            Uri uri = request.getUrl();
            String scheme = uri.getScheme();
            // Keep ordinary HTTP(S) navigation in this WebView. Do not auto-click or inject links.
            return !("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme));
        }

        @Override
        public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
            super.onPageStarted(view, url, favicon);
            if (scriptRunning) {
                finishScriptFailure("Navigation changed before the script completed.");
            }
            pageReady = false;
            runScriptButton.setEnabled(false);
            emptyState.setVisibility(View.GONE);
            pageProgress.setVisibility(View.VISIBLE);
            setStatus("Loading target page...", "Waiting for the native WebView to finish navigation.", StatusTone.RUNNING);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            super.onPageFinished(view, url);
            pageReady = true;
            runScriptButton.setEnabled(!scriptRunning);
            pageProgress.setVisibility(View.GONE);
            setStatus("Ready to run authorized script", "The current page is loaded. The configured script can now be evaluated.", StatusTone.IDLE);
        }

        @Override
        public void onReceivedError(@NonNull WebView view, @NonNull WebResourceRequest request, @NonNull WebResourceError error) {
            super.onReceivedError(view, request, error);
            if (request.isForMainFrame()) {
                pageReady = false;
                runScriptButton.setEnabled(false);
                emptyState.setText(R.string.page_error);
                emptyState.setVisibility(View.VISIBLE);
                setStatus("Page Load Failed", getString(R.string.page_error), StatusTone.ERROR);
            }
        }

        @SuppressWarnings("deprecation")
        @Override
        public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
            super.onReceivedError(view, errorCode, description, failingUrl);
            pageReady = false;
            runScriptButton.setEnabled(false);
            emptyState.setText(R.string.page_error);
            emptyState.setVisibility(View.VISIBLE);
            setStatus("Page Load Failed", getString(R.string.page_error), StatusTone.ERROR);
        }
    }
}
