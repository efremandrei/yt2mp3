package com.andre.yt2mp3;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.view.View;
import android.view.Window;
import android.view.WindowInsetsController;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLException;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import kotlin.Unit;

public class MainActivity extends Activity {
    private static final int REQUEST_WRITE_STORAGE = 27;
    private static final String PREFS_NAME = "yt2mp3_prefs";
    private static final String PREF_SKIN = "skin";
    private static final String SKIN_BRIGHT = "bright";
    private static final String SKIN_DARK = "dark";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ScrollView rootScroll;
    private LinearLayout topBar;
    private LinearLayout contentBody;
    private TextView titleText;
    private TextView outputLabel;
    private EditText urlInput;
    private Button infoButton;
    private Button lightSkinButton;
    private Button darkSkinButton;
    private Button pasteButton;
    private Button openDownloadsButton;
    private Button convertButton;
    private Button cancelButton;
    private Button updateEngineButton;
    private ProgressBar progressBar;
    private TextView statusText;
    private TextView footerText;
    private SkinPalette skin;
    private volatile String activeProcessId;
    private volatile boolean initialized;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUpdateChecker.checkDaily(this);
        setContentView(R.layout.activity_main);

        rootScroll = findViewById(R.id.root_scroll);
        topBar = findViewById(R.id.top_bar);
        contentBody = findViewById(R.id.content_body);
        titleText = findViewById(R.id.title_text);
        outputLabel = findViewById(R.id.output_label);
        urlInput = findViewById(R.id.url_input);
        infoButton = findViewById(R.id.info_button);
        lightSkinButton = findViewById(R.id.light_skin_button);
        darkSkinButton = findViewById(R.id.dark_skin_button);
        pasteButton = findViewById(R.id.paste_button);
        openDownloadsButton = findViewById(R.id.open_downloads_button);
        convertButton = findViewById(R.id.convert_button);
        cancelButton = findViewById(R.id.cancel_button);
        updateEngineButton = findViewById(R.id.update_engine_button);
        progressBar = findViewById(R.id.progress_bar);
        statusText = findViewById(R.id.status_text);
        footerText = findViewById(R.id.footer_text);

        applySavedSkin();

        infoButton.setOnClickListener(view -> showInfoDialog());
        lightSkinButton.setOnClickListener(view -> setSkin(SKIN_BRIGHT));
        darkSkinButton.setOnClickListener(view -> setSkin(SKIN_DARK));
        pasteButton.setOnClickListener(view -> pasteFromClipboard());
        openDownloadsButton.setOnClickListener(view -> openDownloads());
        convertButton.setOnClickListener(view -> startConversion());
        cancelButton.setOnClickListener(view -> cancelActiveConversion());
        updateEngineButton.setOnClickListener(view -> updateEngine());

        readSharedUrl(getIntent());
        maybeRequestLegacyStoragePermission();
        initializeEngine();
    }

    private void applySavedSkin() {
        SharedPreferences preferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String selectedSkin = preferences.getString(PREF_SKIN, SKIN_DARK);
        skin = SKIN_DARK.equals(selectedSkin) ? SkinPalette.dark() : SkinPalette.light();
        applySkin();
    }

    private void setSkin(String selectedSkin) {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(PREF_SKIN, selectedSkin)
                .apply();
        skin = SKIN_DARK.equals(selectedSkin) ? SkinPalette.dark() : SkinPalette.light();
        applySkin();
    }

    private void applySkin() {
        rootScroll.setBackgroundColor(Color.BLACK);
        topBar.setBackgroundColor(Color.BLACK);
        contentBody.setBackgroundColor(skin.background);
        titleText.setTextColor(Color.WHITE);
        outputLabel.setTextColor(skin.textSecondary);
        statusText.setTextColor(skin.textPrimary);
        footerText.setTextColor(skin.textSecondary);

        urlInput.setTextColor(Color.rgb(23, 26, 31));
        urlInput.setHintTextColor(Color.rgb(86, 94, 108));
        urlInput.setBackground(rounded(Color.WHITE, Color.rgb(216, 222, 232), 8));

        styleTopBarButton(infoButton, false);
        styleSecondaryButton(pasteButton);
        styleSecondaryButton(openDownloadsButton);
        styleSecondaryButton(cancelButton);
        cancelButton.setTextColor(skin.danger);
        styleSecondaryButton(updateEngineButton);
        stylePrimaryButton(convertButton);

        styleSkinButton(lightSkinButton, !skin.dark);
        styleSkinButton(darkSkinButton, skin.dark);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            progressBar.setProgressTintList(ColorStateList.valueOf(skin.accent));
            progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(skin.border));
        }

        Window window = getWindow();
        window.setStatusBarColor(Color.BLACK);
        window.setNavigationBarColor(skin.background);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && window.getInsetsController() != null) {
            window.getInsetsController().setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(0);
        }
    }

    private void stylePrimaryButton(Button button) {
        button.setBackground(rounded(skin.accent, skin.accentPressed, 8));
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
    }

    private void styleSecondaryButton(Button button) {
        button.setBackground(rounded(skin.surface, skin.border, 8));
        button.setTextColor(skin.textPrimary);
        button.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
    }

    private void styleSkinButton(Button button, boolean selected) {
        if (selected) {
            button.setBackground(rounded(skin.accent, skin.accentPressed, 8));
            button.setTextColor(Color.WHITE);
            button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        } else {
            styleTopBarButton(button, false);
        }
    }

    private void styleTopBarButton(Button button, boolean selected) {
        button.setBackground(rounded(selected ? skin.accent : Color.BLACK,
                selected ? skin.accentPressed : Color.rgb(104, 112, 122), 8));
        button.setTextColor(Color.WHITE);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private static GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setStroke(2, stroke);
        drawable.setCornerRadius(radiusDp * 3f);
        return drawable;
    }

    private void showInfoDialog() {
        String message = getString(R.string.info_message, appVersionName(), appVersionCode());
        SpannableString linkedMessage = new SpannableString(message);
        Linkify.addLinks(linkedMessage, Linkify.EMAIL_ADDRESSES | Linkify.WEB_URLS);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.info_title)
                .setMessage(linkedMessage)
                .setNeutralButton("Check for updates", (dialogInterface, which) -> AppUpdateChecker.checkNow(this))
                .setPositiveButton(android.R.string.ok, null)
                .show();
        TextView messageView = dialog.findViewById(android.R.id.message);
        if (messageView != null) {
            messageView.setMovementMethod(LinkMovementMethod.getInstance());
            messageView.setLinksClickable(true);
        }
    }

    private String appVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "unknown" : info.versionName;
        } catch (PackageManager.NameNotFoundException error) {
            return "unknown";
        }
    }

    private long appVersionCode() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                return info.getLongVersionCode();
            }
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException error) {
            return 0L;
        }
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
        File outputDir = outputDirectory();
        appendLog(outputDir, "App started. Version " + appVersionName() + "/" + appVersionCode());
        setStatus("Preparing converter engine...");
        appendLog(outputDir, "Preparing converter engine.");
        setBusy(true);
        executor.execute(() -> {
            try {
                YoutubeDL.getInstance().init(getApplicationContext());
                FFmpeg.getInstance().init(getApplicationContext());
                appendLog(outputDir, "Converter engine ready.");
                initialized = true;
                runOnUiThread(() -> {
                    setBusy(false);
                    setStatus("Ready. MP3 files save to Download/yt2mp3.");
                });
            } catch (YoutubeDLException error) {
                appendLog(outputDir, "Engine initialization failed: " + compactError(error));
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
            appendLog(outputDirectory(), "Rejected unsupported URL.");
            setStatus("Enter a valid youtube.com or youtu.be URL.");
            return;
        }
        if (!initialized) {
            appendLog(outputDirectory(), "Conversion requested while engine was still preparing.");
            setStatus("Converter engine is still preparing.");
            return;
        }

        hideKeyboard();
        File outputDir = outputDirectory();
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            setStatus("Could not create " + outputDir.getAbsolutePath());
            return;
        }

        String processId = "yt2mp3-" + UUID.randomUUID();
        activeProcessId = processId;
        progressBar.setProgress(0);
        setBusy(true);
        setStatus("Starting download...");
        appendLog(outputDir, "Starting conversion: " + url);

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
                    appendProgressLog(outputDir, progress, etaInSeconds, line);
                    runOnUiThread(() -> showProgress(progress, etaInSeconds, line));
                    return Unit.INSTANCE;
                });

                appendLog(outputDir, "Conversion done. Saved MP3 in " + outputDir.getAbsolutePath());
                runOnUiThread(() -> {
                    activeProcessId = null;
                    progressBar.setProgress(100);
                    setBusy(false);
                    setStatus("Done. Saved MP3 in " + outputDir.getAbsolutePath());
                });
            } catch (Exception error) {
                appendLog(outputDir, "Conversion failed: " + compactError(error));
                runOnUiThread(() -> {
                    activeProcessId = null;
                    setBusy(false);
                    setStatus("Conversion failed: " + compactError(error));
                });
            }
        });
    }

    private void updateEngine() {
        File outputDir = outputDirectory();
        if (!initialized) {
            appendLog(outputDir, "Engine update requested while engine was still preparing.");
            setStatus("Converter engine is still preparing.");
            return;
        }
        setBusy(true);
        setStatus("Checking for yt-dlp updates...");
        appendLog(outputDir, "Checking for yt-dlp updates.");
        executor.execute(() -> {
            try {
                YoutubeDL.UpdateStatus status = YoutubeDL.getInstance().updateYoutubeDL(
                        getApplicationContext(), YoutubeDL.UpdateChannel._STABLE);
                appendLog(outputDir, status == YoutubeDL.UpdateStatus.DONE
                        ? "Engine updated."
                        : "Engine is already up to date.");
                runOnUiThread(() -> {
                    setBusy(false);
                    setStatus(status == YoutubeDL.UpdateStatus.DONE
                            ? "Engine updated."
                            : "Engine is already up to date.");
                });
            } catch (Exception error) {
                appendLog(outputDir, "Engine update failed: " + compactError(error));
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
            appendLog(outputDirectory(), "Canceled active conversion.");
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

    private File outputDirectory() {
        return OutputTemplate.outputDirectory(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS));
    }

    private void appendProgressLog(File outputDir, float progress, long etaInSeconds, String line) {
        if (line != null && !line.trim().isEmpty()) {
            appendLog(outputDir, line.trim());
            return;
        }
        if (progress > 0f) {
            String eta = etaInSeconds > 0 ? " ETA " + etaInSeconds + "s" : "";
            appendLog(outputDir, String.format(Locale.US, "%.1f%%%s", progress, eta));
        }
    }

    private void appendLog(File outputDir, String message) {
        try {
            RollingLog.append(outputDir, message);
        } catch (IOException ignored) {
            // Logging must not block conversion or UI actions.
        }
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

    private static final class SkinPalette {
        final boolean dark;
        final int background;
        final int surface;
        final int textPrimary;
        final int textSecondary;
        final int accent;
        final int accentPressed;
        final int danger;
        final int border;

        private SkinPalette(
                boolean dark,
                int background,
                int surface,
                int textPrimary,
                int textSecondary,
                int accent,
                int accentPressed,
                int danger,
                int border) {
            this.dark = dark;
            this.background = background;
            this.surface = surface;
            this.textPrimary = textPrimary;
            this.textSecondary = textSecondary;
            this.accent = accent;
            this.accentPressed = accentPressed;
            this.danger = danger;
            this.border = border;
        }

        static SkinPalette light() {
            return new SkinPalette(
                    false,
                    Color.rgb(247, 248, 250),
                    Color.WHITE,
                    Color.rgb(23, 26, 31),
                    Color.rgb(86, 94, 108),
                    Color.rgb(14, 124, 102),
                    Color.rgb(10, 94, 77),
                    Color.rgb(180, 35, 24),
                    Color.rgb(216, 222, 232));
        }

        static SkinPalette dark() {
            return new SkinPalette(
                    true,
                    Color.rgb(12, 18, 22),
                    Color.rgb(25, 34, 39),
                    Color.rgb(239, 246, 243),
                    Color.rgb(164, 178, 177),
                    Color.rgb(34, 199, 169),
                    Color.rgb(24, 161, 138),
                    Color.rgb(255, 138, 128),
                    Color.rgb(64, 78, 84));
        }
    }
}
