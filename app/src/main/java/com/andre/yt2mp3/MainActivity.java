package com.andre.yt2mp3;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlin.Unit;

public class MainActivity extends Activity {
    private static final int REQUEST_WRITE_STORAGE = 27;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private EditText urlInput;
    private Button convertButton;
    private Button cancelButton;
    private Button updateEngineButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private volatile String activeProcessId;
    private volatile boolean initialized;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        urlInput = findViewById(R.id.url_input);
        convertButton = findViewById(R.id.convert_button);
        cancelButton = findViewById(R.id.cancel_button);
        updateEngineButton = findViewById(R.id.update_engine_button);
        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);

        findViewById(R.id.paste_button).setOnClickListener(view -> pasteFromClipboard());
        findViewById(R.id.open_downloads_button).setOnClickListener(view -> openDownloads());
        convertButton.setOnClickListener(view -> startConversion());
        cancelButton.setOnClickListener(view -> cancelActiveConversion());
        updateEngineButton.setOnClickListener(view -> updateEngine());

        readSharedUrl(getIntent());
        maybeRequestLegacyStoragePermission();
        initializeEngine();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        readSharedUrl(intent);
    }

    @Override
    protected void onDestroy() {
        cancelActiveConversion();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void initializeEngine() {
        setStatus("Preparing converter engine...");
        setBusy(true);
        executor.execute(() -> {
            try {
                YoutubeDL.getInstance().init(getApplicationContext());
                FFmpeg.getInstance().init(getApplicationContext());
                initialized = true;
                runOnUiThread(() -> {
                    setBusy(false);
                    setStatus("Ready. MP3 files save to Download/yt2mp3.");
                });
            } catch (YoutubeDLException error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    setStatus("Engine initialization failed: " + compactError(error));
                });
            }
        });
    }

    private void startConversion() {
        String url = urlInput.getText().toString().trim();
        if (!YoutubeUrl.isSupported(url)) {
            setStatus("Enter a valid youtube.com or youtu.be URL.");
            return;
        }
        if (!initialized) {
            setStatus("Converter engine is still preparing.");
            return;
        }

        hideKeyboard();
        File outputDir = OutputTemplate.outputDirectory(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            setStatus("Could not create " + outputDir.getAbsolutePath());
            return;
        }

        String processId = "yt2mp3-" + UUID.randomUUID();
        activeProcessId = processId;
        progressBar.setProgress(0);
        setBusy(true);
        setStatus("Starting download...");

        executor.execute(() -> {
            try {
                YoutubeDLRequest request = new YoutubeDLRequest(url);
                request.addOption("--no-playlist");
                request.addOption("--extract-audio");
                request.addOption("--audio-format", "mp3");
                request.addOption("--audio-quality", "0");
                request.addOption("--newline");
                request.addOption("--restrict-filenames");
                request.addOption("--paths", outputDir.getAbsolutePath());
                request.addOption("-o", OutputTemplate.mp3Template(outputDir));

                YoutubeDL.getInstance().execute(request, processId, true, (progress, etaInSeconds, line) -> {
                    runOnUiThread(() -> showProgress(progress, etaInSeconds, line));
                    return Unit.INSTANCE;
                });

                runOnUiThread(() -> {
                    activeProcessId = null;
                    progressBar.setProgress(100);
                    setBusy(false);
                    setStatus("Done. Saved MP3 in " + outputDir.getAbsolutePath());
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    activeProcessId = null;
                    setBusy(false);
                    setStatus("Conversion failed: " + compactError(error));
                });
            }
        });
    }

    private void updateEngine() {
        if (!initialized) {
            setStatus("Converter engine is still preparing.");
            return;
        }
        setBusy(true);
        setStatus("Checking for yt-dlp updates...");
        executor.execute(() -> {
            try {
                YoutubeDL.UpdateStatus status = YoutubeDL.getInstance().updateYoutubeDL(
                        getApplicationContext(), YoutubeDL.UpdateChannel._STABLE);
                runOnUiThread(() -> {
                    setBusy(false);
                    setStatus(status == YoutubeDL.UpdateStatus.DONE
                            ? "Engine updated."
                            : "Engine is already up to date.");
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    setBusy(false);
                    setStatus("Engine update failed: " + compactError(error));
                });
            }
        });
    }

    private void cancelActiveConversion() {
        String processId = activeProcessId;
        if (processId != null) {
            YoutubeDL.getInstance().destroyProcessById(processId);
            activeProcessId = null;
            setBusy(false);
            setStatus("Canceled.");
        }
    }

    private void showProgress(float progress, long etaInSeconds, String line) {
        int roundedProgress = Math.max(0, Math.min(100, Math.round(progress)));
        progressBar.setProgress(roundedProgress);
        if (progress > 0f) {
            String eta = etaInSeconds > 0 ? " ETA " + etaInSeconds + "s" : "";
            setStatus(String.format(Locale.US, "%.1f%%%s", progress, eta));
        } else if (line != null && !line.trim().isEmpty()) {
            setStatus(line.trim());
        }
    }

    private void pasteFromClipboard() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(this);
        if (text != null) {
            urlInput.setText(text.toString().trim());
            urlInput.setSelection(urlInput.length());
        }
    }

    private void readSharedUrl(Intent intent) {
        if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
            return;
        }
        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        if (text != null) {
            urlInput.setText(text.toString().trim());
            urlInput.setSelection(urlInput.length());
        }
    }

    private void openDownloads() {
        Intent intent = new Intent(DownloadManagerCompat.DOWNLOADS_ACTION);
        if (intent.resolveActivity(getPackageManager()) != null) {
            startActivity(intent);
            return;
        }
        Intent settingsIntent = new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS);
        startActivity(settingsIntent);
    }

    private void maybeRequestLegacyStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
        }
    }

    private void setBusy(boolean busy) {
        convertButton.setEnabled(!busy);
        updateEngineButton.setEnabled(!busy);
        cancelButton.setEnabled(activeProcessId != null);
    }

    private void setStatus(String message) {
        statusText.setText(message);
    }

    private void hideKeyboard() {
        View currentFocus = getCurrentFocus();
        if (currentFocus == null) {
            return;
        }
        InputMethodManager manager =
                (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (manager != null) {
            manager.hideSoftInputFromWindow(currentFocus.getWindowToken(), 0);
        }
    }

    private static String compactError(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            message = error.getClass().getSimpleName();
        }
        message = message.replace('\n', ' ').replace('\r', ' ').trim();
        return message.length() > 240 ? message.substring(0, 237) + "..." : message;
    }

    private static final class DownloadManagerCompat {
        static final String DOWNLOADS_ACTION = "android.intent.action.VIEW_DOWNLOADS";
    }
}
