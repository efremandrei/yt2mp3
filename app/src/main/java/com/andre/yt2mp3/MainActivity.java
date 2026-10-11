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
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ViewGroup;
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

import com.yausername.youtubedl_android.YoutubeDL;
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
    private static final String PREF_ENGINE_CHECK = "engine_check";
    private static final long ENGINE_CHECK_INTERVAL = 24L * 60 * 60 * 1000;
    private static final String SKIN_BRIGHT = "bright";
    private static final String SKIN_DARK = "dark";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private ScrollView rootScroll;
    private LinearLayout topBar;
    private LinearLayout contentBody;
    private TextView titleText;
    private TextView outputLabel;
    private EditText urlInput;
    private EditText filenameInput;
    private Button clearButton;
    private Button editorButton;
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
    private volatile boolean cancelRequested;
    private volatile boolean busy;
    private String verifiedEngineVersion;
    private TextView aboutEngineText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AppUpdateChecker.checkDaily(this);
        setContentView(R.layout.activity_main);

        rootScroll = findViewById(R.id.root_scroll);
        rootScroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = insets.getInsets(
                        android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            }
            return insets;
        });
        rootScroll.requestApplyInsets();
        topBar = findViewById(R.id.top_bar);
        contentBody = findViewById(R.id.content_body);
        titleText = findViewById(R.id.title_text);
        outputLabel = findViewById(R.id.output_label);
        urlInput = findViewById(R.id.url_input);
        filenameInput = findViewById(R.id.filename_input);
        clearButton = findViewById(R.id.clear_button);
        editorButton = findViewById(R.id.editor_button);
        editorButton.setOnClickListener(view -> startActivity(new Intent(this, EditorActivity.class)));
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

        statusText.setTextIsSelectable(true);
        applySavedSkin();
        filenameInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            public void onTextChanged(CharSequence s, int start, int before, int count) { updateOutputPreview(); }
            public void afterTextChanged(Editable text) { }
        });
        updateOutputPreview();

        infoButton.setOnClickListener(view -> showInfoDialog());
        lightSkinButton.setOnClickListener(view -> setSkin(SKIN_BRIGHT));
        darkSkinButton.setOnClickListener(view -> setSkin(SKIN_DARK));
        pasteButton.setOnClickListener(view -> pasteFromClipboard());
        clearButton.setOnClickListener(view -> {
            urlInput.setText("");
            urlInput.setError(null);
            urlInput.requestFocus();
        });
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
        rootScroll.setBackgroundColor(skin.background);
        topBar.setBackgroundColor(skin.background);
        contentBody.setBackgroundColor(skin.background);
        titleText.setTextColor(skin.textPrimary);
        tintLabels(contentBody);
        for (int id : new int[]{R.id.link_card, R.id.output_card, R.id.status_card}) {
            findViewById(id).setBackground(rounded(skin.surface, skin.border, 18));
        }
        outputLabel.setTextColor(skin.textSecondary);
        statusText.setTextColor(skin.textPrimary);
        footerText.setTextColor(skin.textSecondary);

        for (EditText input : new EditText[]{urlInput, filenameInput}) {
            input.setTextColor(skin.textPrimary);
            input.setHintTextColor(skin.textSecondary);
            input.setBackground(rounded(skin.background, skin.border, 12));
        }

        styleTopBarButton(infoButton, false);
        styleSecondaryButton(pasteButton);
        styleSecondaryButton(clearButton);
        styleSecondaryButton(editorButton);
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
            progressBar.setIndeterminateTintList(ColorStateList.valueOf(skin.accent));
        }

        Window window = getWindow();
        window.setStatusBarColor(skin.background);
        window.setNavigationBarColor(skin.background);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.setStatusBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && window.getInsetsController() != null) {
            window.getInsetsController().setSystemBarsAppearance(
                    skin.dark ? 0 : WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.getDecorView().setSystemUiVisibility(skin.dark ? 0 : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    private void tintLabels(View view) {
        if (view instanceof TextView && view.getTag() != null) {
            ((TextView) view).setTextColor("heading".equals(view.getTag()) ? skin.textPrimary : skin.textSecondary);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) tintLabels(group.getChildAt(i));
        }
    }

    private void updateOutputPreview() {
        try {
            String name = OutputName.custom(filenameInput.getText().toString());
            outputLabel.setText("Download / yt2mp3 /\n" + (name.isEmpty() ? "Video title [video ID].mp3" : name));
            filenameInput.setError(null);
        } catch (IllegalArgumentException error) {
            outputLabel.setText("Download / yt2mp3");
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
        button.setBackground(rounded(selected ? skin.accent : skin.surface,
                selected ? skin.accentPressed : skin.border, 12));
        button.setTextColor(selected ? Color.WHITE : skin.textPrimary);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
    }

    private GradientDrawable rounded(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(fill);
        drawable.setStroke(Math.max(1, Math.round(getResources().getDisplayMetrics().density)), stroke);
        drawable.setCornerRadius(radiusDp * getResources().getDisplayMetrics().density);
        return drawable;
    }

    private void showInfoDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = Math.round(24 * getResources().getDisplayMetrics().density);
        content.setPadding(padding, 0, padding, padding / 2);
        scroll.addView(content);
        addInfoText(content, getString(R.string.info_description), false, false);
        addInfoSection(content, R.string.info_release,
                getString(R.string.info_version, appVersionName(), appVersionCode()), false);
        addInfoSection(content, R.string.info_developer,
                getString(R.string.info_developer_name), false);
        addInfoText(content, getString(R.string.info_email), false, true);
        addInfoSection(content, R.string.info_project,
                getString(R.string.info_repository), true);
        addInfoText(content, getString(R.string.info_audio_engine), true, false);
        aboutEngineText = addInfoText(content, aboutEngineDetails(), false, false);
        AlertDialog dialog = themedDialog()
                .setTitle(R.string.info_title)
                .setView(scroll)
                .setNeutralButton(R.string.info_check_updates, (dialogInterface, which) -> AppUpdateChecker.checkNow(this))
                .setPositiveButton(R.string.info_close, null)
                .show();
        int linkColor = skin.dark ? Color.rgb(184, 160, 255) : skin.accent;
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(linkColor);
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setTextColor(linkColor);
        dialog.setOnDismissListener(ignored -> aboutEngineText = null);
    }

    private String aboutEngineDetails() {
        String version = YoutubeDL.getInstance().versionName(getApplicationContext());
        if (version == null || version.trim().isEmpty()) version = verifiedEngineVersion;
        return getString(R.string.info_engine_details, version == null
                ? getString(initialized ? R.string.info_engine_unknown : R.string.info_engine_preparing) : version);
    }

    private void addInfoSection(LinearLayout content, int heading, String value, boolean linked) {
        addInfoText(content, getString(heading), true, false);
        addInfoText(content, value, false, linked);
    }

    private TextView addInfoText(LinearLayout content, String value, boolean heading, boolean linked) {
        TextView label = new TextView(this);
        label.setTextSize(heading ? 12 : 16);
        label.setTextColor(heading ? skin.textSecondary : skin.textPrimary);
        label.setTypeface(Typeface.DEFAULT, heading ? Typeface.BOLD : Typeface.NORMAL);
        label.setLineSpacing(3 * getResources().getDisplayMetrics().density, 1f);
        int top = Math.round((heading ? 20 : 5) * getResources().getDisplayMetrics().density);
        label.setPadding(0, top, 0, 0);
        if (linked) {
            SpannableString link = new SpannableString(value);
            Linkify.addLinks(link, Linkify.EMAIL_ADDRESSES | Linkify.WEB_URLS);
            label.setText(link);
            label.setLinkTextColor(skin.dark ? Color.rgb(184, 160, 255) : skin.accent);
            label.setMovementMethod(LinkMovementMethod.getInstance());
            label.setLinksClickable(true);
        } else {
            label.setText(value);
            label.setTextIsSelectable(!heading);
        }
        content.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return label;
    }

    private String appVersionName() {
        try {
            PackageInfo info = getPackageManager().getPackageInfo(getPackageName(), 0);
            return info.versionName == null ? "unknown" : info.versionName;
        } catch (PackageManager.NameNotFoundException error) {
            return "unknown";
        }
    }

    private AlertDialog.Builder themedDialog() {
        return new AlertDialog.Builder(this, skin.dark
                ? android.R.style.Theme_Material_Dialog_Alert
                : android.R.style.Theme_Material_Light_Dialog_Alert);
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
                String engineVersion = EngineRuntime.initialize(getApplicationContext());
                appendLog(outputDir, "Converter engine ready. Runtime verified: yt-dlp " + engineVersion);
                initialized = true;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    verifiedEngineVersion = engineVersion;
                    if (aboutEngineText != null) aboutEngineText.setText(aboutEngineDetails());
                    setBusy(false);
                    setStatus("Ready. MP3 files save to Download/yt2mp3.");
                });
            } catch (Exception error) {
                appendLog(outputDir, "Engine initialization failed: " + fullError(error));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    setBusy(false);
                    setStatus("Engine initialization failed: " + fullError(error));
                    showConversionError(fullError(error));
                });
            }
        });
    }

    private void startConversion() {
        final String customName;
        try {
            customName = OutputName.custom(filenameInput.getText().toString());
        } catch (IllegalArgumentException error) {
            filenameInput.setError(error.getMessage());
            filenameInput.requestFocus();
            return;
        }
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
        if (busy) return;
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
            maybeRequestLegacyStoragePermission();
            setStatus("Allow storage access, then tap Convert again.");
            return;
        }
        File outputDir = outputDirectory();

        String processId = "yt2mp3-" + UUID.randomUUID();
        activeProcessId = processId;
        cancelRequested = false;
        progressBar.setProgress(0);
        setBusy(true);
        setStatus("Starting download...");
        appendLog(outputDir, "Starting conversion: " + url);

        executor.execute(() -> {
            File jobDirectory = new File(getCacheDir(), processId);
            try {
                if (!jobDirectory.mkdirs()) throw new IOException("Could not create conversion workspace.");
                refreshEngineIfDue(outputDir);
                if (cancelRequested) throw new YoutubeDL.CanceledException();
                YoutubeDLRequest request = new YoutubeDLRequest(url);
                request.addOption("--no-playlist");
                request.addOption("--format", "bestaudio/best");
                request.addOption("--socket-timeout", "30");
                request.addOption("--retries", "3");
                request.addOption("--fragment-retries", "3");
                request.addOption("--remote-components", "ejs:github");
                request.addOption("--extract-audio");
                request.addOption("--audio-format", "mp3");
                request.addOption("--audio-quality", "0");
                request.addOption("--newline");
                request.addOption("--restrict-filenames");
                request.addOption("--paths", jobDirectory.getAbsolutePath());
                request.addOption("-o", OutputTemplate.mp3Template(jobDirectory));

                // YoutubeDLException is built from stderr. Merging it into stdout
                // discards the failure details and leaves an empty exception.
                YoutubeDL.getInstance().execute(request, processId, false, (progress, etaInSeconds, line) -> {
                    appendProgressLog(outputDir, progress, etaInSeconds, line);
                    if (cancelRequested) {
                        new Thread(() -> YoutubeDL.getInstance().destroyProcessById(processId)).start();
                    }
                    runOnUiThread(() -> {
                        if (!cancelRequested) showProgress(progress, etaInSeconds, line);
                    });
                    return Unit.INSTANCE;
                });

                if (cancelRequested) throw new YoutubeDL.CanceledException();
                File completed = ConversionFiles.completedMp3(jobDirectory);
                // Saving is atomic from the user's perspective; cancellation ends here.
                runOnUiThread(() -> {
                    cancelButton.setEnabled(false);
                    setStatus("Saving MP3...");
                });
                String savedPath = Mp3Publisher.publish(getApplicationContext(), completed, customName);
                appendLog(outputDir, "Conversion done. Saved " + savedPath);
                runOnUiThread(() -> {
                    activeProcessId = null;
                    progressBar.setProgress(100);
                    setBusy(false);
                    setStatus("Done. Saved " + savedPath);
                });
            } catch (Exception error) {
                boolean canceled = cancelRequested || error instanceof YoutubeDL.CanceledException
                        || error instanceof InterruptedException;
                String details = fullError(error);
                appendLog(outputDir, canceled ? "Conversion canceled." : "Conversion failed: " + details);
                runOnUiThread(() -> {
                    activeProcessId = null;
                    setBusy(false);
                    setStatus(canceled ? "Canceled." : "Conversion failed: " + compactError(error));
                    if (!canceled && !isFinishing() && !isDestroyed()) showConversionError(details);
                });
            } finally {
                ConversionFiles.cleanUp(jobDirectory);
            }
        });
    }

    private void refreshEngineIfDue(File logDir) {
        long lastCheck = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getLong(PREF_ENGINE_CHECK, 0L);
        if (System.currentTimeMillis() - lastCheck < ENGINE_CHECK_INTERVAL) return;
        runOnUiThread(() -> setStatus("Updating YouTube converter..."));
        try {
            YoutubeDL.getInstance().updateYoutubeDL(getApplicationContext(), YoutubeDL.UpdateChannel._STABLE);
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                    .putLong(PREF_ENGINE_CHECK, System.currentTimeMillis()).apply();
            appendLog(logDir, "Engine version: " + YoutubeDL.getInstance().versionName(getApplicationContext()));
        } catch (Exception error) {
            appendLog(logDir, "Engine update unavailable; using installed version: " + fullError(error));
        }
    }

    private void showConversionError(String details) {
        themedDialog().setTitle("Conversion failed")
                .setMessage(details)
                .setNeutralButton("Copy error", (dialog, which) -> {
                    ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    if (clipboard != null) clipboard.setPrimaryClip(ClipData.newPlainText("yt2mp3 error",
                            "yt2mp3 " + appVersionName() + "/" + appVersionCode()
                            + "\nAndroid " + Build.VERSION.RELEASE
                            + "\nEngine " + YoutubeDL.getInstance().versionName(getApplicationContext())
                            + "\n" + details));
                }).setPositiveButton(android.R.string.ok, null).show();
    }

    private static String fullError(Throwable error) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? error.getClass().getSimpleName() : message.trim();
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
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit()
                        .putLong(PREF_ENGINE_CHECK, System.currentTimeMillis()).apply();
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
            cancelRequested = true;
            YoutubeDL.getInstance().destroyProcessById(processId);
            cancelButton.setEnabled(false);
            setStatus("Canceling...");
            appendLog(outputDirectory(), "Canceled active conversion.");
        }
    }

    private void showProgress(float progress, long etaInSeconds, String line) {
        int roundedProgress = Math.max(0, Math.min(100, Math.round(progress)));
        progressBar.setProgress(roundedProgress);
        progressBar.setIndeterminate(false);
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
        this.busy = busy;
        convertButton.setEnabled(!busy);
        updateEngineButton.setEnabled(!busy);
        editorButton.setEnabled(!busy);
        cancelButton.setEnabled(activeProcessId != null);
        cancelButton.setVisibility(activeProcessId != null ? View.VISIBLE : View.GONE);
        progressBar.setVisibility(busy || progressBar.getProgress() > 0 ? View.VISIBLE : View.GONE);
        progressBar.setIndeterminate(busy);
        boolean editable = activeProcessId == null;
        urlInput.setEnabled(editable);
        filenameInput.setEnabled(editable);
        pasteButton.setEnabled(editable);
        clearButton.setEnabled(editable);
        for (Button button : new Button[]{convertButton, updateEngineButton, pasteButton, clearButton, editorButton}) {
            button.setAlpha(button.isEnabled() ? 1f : 0.45f);
        }
    }

    private void setStatus(String message) {
        statusText.setText(message);
    }

    private File outputDirectory() {
        return new File(getFilesDir(), "logs");
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
                    Color.rgb(104, 65, 203),
                    Color.rgb(80, 45, 166),
                    Color.rgb(180, 35, 24),
                    Color.rgb(216, 222, 232));
        }

        static SkinPalette dark() {
            return new SkinPalette(
                    true,
                    Color.rgb(15, 17, 26),
                    Color.rgb(25, 28, 41),
                    Color.rgb(239, 246, 243),
                    Color.rgb(164, 178, 177),
                    Color.rgb(112, 73, 216),
                    Color.rgb(87, 51, 180),
                    Color.rgb(255, 138, 128),
                    Color.rgb(49, 54, 73));
        }
    }
}
