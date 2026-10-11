package com.andre.yt2mp3;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.OpenableColumns;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EditorActivity extends Activity {
    private static final int PICK_AUDIO = 70;
    private final ArrayList<AudioChunk> chunks = new ArrayList<>();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AudioEditEngine engine = new AudioEditEngine();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LinearLayout body, chunkList;
    private TextView fileText, positionText, summary, status;
    private EditText fromInput, toInput, chunkName, outputName;
    private Button addButton, exportButton, cancelButton, playButton, cancelEdit;
    private SeekBar seek;
    private ProgressBar progress;
    private File source;
    private String sourceName = "Audio";
    private long durationMs;
    private AudioChunk editing;
    private MediaPlayer player;
    private boolean playerReady, busy;
    private long previewEnd = Long.MAX_VALUE;
    private boolean dark;
    private int background, surface, text, muted, border, accent;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        dark = !"bright".equals(getSharedPreferences("yt2mp3_prefs", MODE_PRIVATE).getString("skin", "dark"));
        background = dark ? Color.rgb(15,17,26) : Color.rgb(247,248,250);
        surface = dark ? Color.rgb(25,28,41) : Color.WHITE;
        text = dark ? Color.rgb(239,246,243) : Color.rgb(23,26,31);
        muted = dark ? Color.rgb(164,178,177) : Color.rgb(86,94,108);
        border = dark ? Color.rgb(49,54,73) : Color.rgb(216,222,232);
        accent = Color.rgb(104,65,203);
        buildScreen();
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
        if (state != null) {
            String path = state.getString("source");
            if (path != null && new File(path).isFile()) {
                setSource(new File(path), state.getString("sourceName", "Audio"), state.getLong("duration"));
                ArrayList<String> names = state.getStringArrayList("names");
                long[] starts = state.getLongArray("starts"), ends = state.getLongArray("ends");
                boolean[] selected = state.getBooleanArray("selected");
                if (names != null && starts != null && ends != null && selected != null) {
                    for (int i = 0; i < names.size(); i++) {
                        AudioChunk chunk = new AudioChunk(names.get(i), starts[i], ends[i], durationMs);
                        chunk.selected = selected[i]; chunks.add(chunk);
                    }
                    renderChunks();
                    int editingIndex = state.getInt("editingIndex", -1);
                    if (editingIndex >= 0 && editingIndex < chunks.size()) {
                        editing = chunks.get(editingIndex); addButton.setText("Save chunk"); cancelEdit.setVisibility(View.VISIBLE);
                    }
                }
            }
        }
    }
    private void buildScreen() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true); scroll.setBackgroundColor(background);
        scroll.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                    insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets;
        });
        body = column(); body.setPadding(dp(20), dp(16), dp(20), dp(32)); scroll.addView(body);
        LinearLayout toolbar = row(body);
        Button back = smallButton(toolbar, "‹", this::handleBack); back.setContentDescription("Back to converter");
        TextView title = label(toolbar, "MP3 editor", 23, false); title.setTypeface(null, Typeface.BOLD);
        label(body, "Cut. Arrange. Make a new mix.", 28, false);
        label(body, "Choose an audio file, add time ranges, then select and order the chunks you want to keep.", 14, true);
        button(body, "Choose audio file", this::chooseFile, true);
        LinearLayout sourceCard = card(body);
        fileText = label(sourceCard, "No file selected", 16, false);
        positionText = label(sourceCard, "0:00.000 / 0:00.000", 13, true);
        seek = new SeekBar(this); sourceCard.addView(seek); seek.setEnabled(false);
        seek.setProgressTintList(android.content.res.ColorStateList.valueOf(accent));
        seek.setThumbTintList(android.content.res.ColorStateList.valueOf(accent));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int value, boolean user) {
                if (user && playerReady) { previewEnd = Long.MAX_VALUE; player.seekTo(value); }
            }
            public void onStartTrackingTouch(SeekBar bar) { }
            public void onStopTrackingTouch(SeekBar bar) { }
        });
        LinearLayout playback = row(sourceCard);
        playButton = button(playback, "Play / pause", this::togglePlayback, false);
        playButton.setEnabled(false);
        button(playback, "Stop", this::stopPlayback, false);
        LinearLayout rangeCard = card(body);
        label(rangeCard, "New chunk", 17, false).setTypeface(null, Typeface.BOLD);
        label(rangeCard, "Times: seconds, mm:ss or hh:mm:ss. Decimals are supported.", 12, true);
        LinearLayout times = row(rangeCard);
        LinearLayout fromCol = weightedColumn(times), toCol = weightedColumn(times);
        TextView fromLabel = label(fromCol, "From", 13, true); fromInput = input(fromCol, "0:00.000", R.id.editor_from); fromLabel.setLabelFor(R.id.editor_from);
        TextView toLabel = label(toCol, "To", 13, true); toInput = input(toCol, "0:30.000", R.id.editor_to); toLabel.setLabelFor(R.id.editor_to);
        fromInput.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        toInput.setInputType(fromInput.getInputType());
        LinearLayout marks = row(rangeCard);
        button(marks, "Set from playhead", () -> mark(fromInput), false);
        button(marks, "Set to playhead", () -> mark(toInput), false);
        chunkName = input(rangeCard, "Chunk name (optional)", R.id.editor_chunk_name);
        addButton = button(rangeCard, "Add chunk", this::addChunk, true); addButton.setId(R.id.editor_add);
        addButton.setEnabled(false);
        cancelEdit = button(rangeCard, "Cancel edit", () -> { editing = null; chunkName.setText(""); addButton.setText("Add chunk"); cancelEdit.setVisibility(View.GONE); }, false);
        cancelEdit.setVisibility(View.GONE);
        label(body, "Chunks & merge order", 20, false).setTypeface(null, Typeface.BOLD);
        label(body, "Checked chunks export from top to bottom. Use ↑ / ↓ to change the order. Overlapping ranges are allowed.", 13, true);
        chunkList = column(); body.addView(chunkList);
        LinearLayout exportCard = card(body);
        summary = label(exportCard, "Add your first chunk above.", 14, true); summary.setId(R.id.editor_summary);
        outputName = input(exportCard, "New MP3 filename", R.id.editor_output_name);
        exportButton = button(exportCard, "Export selected chunks", () -> export(selectedChunks(), outputName.getText().toString()), true);
        exportButton.setId(R.id.editor_export); exportButton.setEnabled(false);
        label(exportCard, "Saves to Download/yt2mp3. The source stays unchanged. Audio is re-encoded to MP3 for precise cuts.", 12, true);
        progress = new ProgressBar(this); progress.setVisibility(View.GONE); body.addView(progress);
        status = label(body, "Choose a file to begin.", 14, false); status.setTextIsSelectable(true);
        status.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        cancelButton = button(body, "Cancel operation", () -> { engine.cancel(); status.setText("Canceling…"); cancelButton.setEnabled(false); }, false);
        cancelButton.setVisibility(View.GONE);
        setContentView(scroll); scroll.requestApplyInsets();
        getWindow().setStatusBarColor(background); getWindow().setNavigationBarColor(background);
        if (Build.VERSION.SDK_INT >= 30 && getWindow().getInsetsController() != null) {
            int lightBars = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            getWindow().getInsetsController().setSystemBarsAppearance(dark ? 0 : lightBars, lightBars);
        }
    }
    private void chooseFile() {
        if (busy) return;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("audio/*").addCategory(Intent.CATEGORY_OPENABLE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); startActivityForResult(intent, PICK_AUDIO);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_AUDIO && result == RESULT_OK && data != null && data.getData() != null) importSource(data.getData());
    }
    private void importSource(Uri uri) {
        stopPlayback(); engine.reset(); setBusy(true, "Loading audio…");
        worker.execute(() -> {
            File copy = null;
            try {
                String name = "Audio";
                try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) name = cursor.getString(0);
                }
                copy = File.createTempFile("editor-source-", ".audio", getCacheDir());
                try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(copy)) {
                    if (in == null) throw new IOException("Could not read that file.");
                    byte[] buffer = new byte[65536]; int count;
                    while ((count = in.read(buffer)) != -1) {
                        if (engine.isCanceled() || Thread.currentThread().isInterrupted()) throw new InterruptedException();
                        out.write(buffer, 0, count);
                    }
                }
                long duration = readDuration(copy);
                if (duration <= 0 || duration > Integer.MAX_VALUE) throw new IOException("This file has no supported audio duration.");
                File loaded = copy; String displayName = name;
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) { loaded.delete(); return; }
                    setSource(loaded, displayName, duration); setBusy(false, "Add the ranges you want to keep.");
                });
                copy = null;
            } catch (Exception error) { finishError(error); }
            finally { if (copy != null) copy.delete(); }
        });
    }
    static long readDuration(File file) throws Exception {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        try {
            retriever.setDataSource(file.getAbsolutePath());
            String duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
            return duration == null ? 0 : Long.parseLong(duration);
        } finally { retriever.release(); }
    }
    void setSource(File file, String name, long duration) {
        releasePlayer(); if (source != null && !source.equals(file)) source.delete();
        source = file; sourceName = name; durationMs = duration;
        addButton.setEnabled(true); status.setText("Add the ranges you want to keep.");
        chunks.clear(); editing = null; cancelEdit.setVisibility(View.GONE); addButton.setText("Add chunk");
        fileText.setText(name + "\n" + AudioEditPlan.time(duration));
        fromInput.setText("0:00.000"); toInput.setText(AudioEditPlan.time(duration)); chunkName.setText("");
        String base = name.replaceFirst("(?i)\\.[a-z0-9]{1,5}$", "").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        outputName.setText(base.length() > 60 ? "Edited audio" : base + "-edited");
        seek.setMax((int) duration); renderChunks(); preparePlayer();
    }
    private void preparePlayer() {
        try {
            player = new MediaPlayer(); player.setDataSource(source.getAbsolutePath());
            player.setOnPreparedListener(ready -> { playerReady = true; seek.setEnabled(!busy); playButton.setEnabled(!busy); tick.run(); });
            player.setOnCompletionListener(done -> previewEnd = Long.MAX_VALUE);
            player.setOnErrorListener((failed, what, extra) -> { playerReady = false; status.setText("Audio preview unavailable. You can still export chunks."); return true; });
            player.prepareAsync();
        } catch (Exception error) { status.setText("Audio preview unavailable: " + error.getMessage()); }
    }
    private final Runnable tick = new Runnable() {
        public void run() {
            if (!playerReady || player == null) return;
            int position = player.getCurrentPosition();
            if (player.isPlaying() && position >= previewEnd) { player.pause(); previewEnd = Long.MAX_VALUE; }
            seek.setProgress(position); positionText.setText(AudioEditPlan.time(position) + " / " + AudioEditPlan.time(durationMs));
            handler.postDelayed(this, 100);
        }
    };
    private void togglePlayback() {
        if (!playerReady || busy) return;
        previewEnd = Long.MAX_VALUE; if (player.isPlaying()) player.pause(); else player.start();
    }
    private void stopPlayback() {
        previewEnd = Long.MAX_VALUE; if (playerReady && player != null) { player.pause(); player.seekTo(0); }
    }
    private void preview(AudioChunk chunk) {
        if (!playerReady || busy) return;
        player.pause(); previewEnd = chunk.toMs;
        player.setOnSeekCompleteListener(ready -> { ready.setOnSeekCompleteListener(null); if (!busy) ready.start(); });
        player.seekTo((int) chunk.fromMs);
    }
    private void mark(EditText input) { if (playerReady) input.setText(AudioEditPlan.time(player.getCurrentPosition())); }
    private void releasePlayer() {
        handler.removeCallbacks(tick); playerReady = false;
        if (player != null) { player.release(); player = null; }
    }
    private void addChunk() {
        if (source == null) { status.setText("Choose an audio file first."); return; }
        try {
            String name = chunkName.getText().toString().trim(); if (name.isEmpty()) name = "Chunk " + (chunks.size() + 1);
            OutputName.custom(name);
            AudioChunk chunk = new AudioChunk(name, AudioEditPlan.parseTime(fromInput.getText().toString()),
                    AudioEditPlan.parseTime(toInput.getText().toString()), durationMs);
            if (editing != null) { chunk.selected = editing.selected; chunks.set(chunks.indexOf(editing), chunk); editing = null; }
            else { if (chunks.size() >= 100) throw new IllegalArgumentException("Use up to 100 chunks in one edit."); chunks.add(chunk); }
            chunkName.setText(""); addButton.setText("Add chunk"); cancelEdit.setVisibility(View.GONE);
            renderChunks(); status.setText("Chunk added. Check the chunks to export and arrange their order.");
        } catch (IllegalArgumentException error) { status.setText(error.getMessage()); }
    }
    List<AudioChunk> selectedChunks() {
        ArrayList<AudioChunk> selected = new ArrayList<>(); for (AudioChunk chunk : chunks) if (chunk.selected) selected.add(chunk); return selected;
    }
    private void renderChunks() {
        chunkList.removeAllViews();
        for (AudioChunk chunk : chunks) {
            LinearLayout container = card(chunkList), header = row(container);
            CheckBox checked = new CheckBox(this); checked.setText(chunk.name); checked.setTextColor(text); checked.setChecked(chunk.selected);
            checked.setButtonTintList(android.content.res.ColorStateList.valueOf(accent));
            checked.setMaxLines(1); checked.setEllipsize(android.text.TextUtils.TruncateAt.END);
            header.addView(checked, new LinearLayout.LayoutParams(0, dp(48), 1));
            checked.setOnCheckedChangeListener((button, selected) -> { chunk.selected = selected; updateSummary(); });
            Button up = smallButton(header, "↑", () -> move(chunk, -1)); up.setContentDescription("Move " + chunk.name + " up"); up.setEnabled(chunks.indexOf(chunk) > 0);
            Button down = smallButton(header, "↓", () -> move(chunk, 1)); down.setContentDescription("Move " + chunk.name + " down"); down.setEnabled(chunks.indexOf(chunk) < chunks.size() - 1);
            smallButton(header, "×", () -> {
                chunks.remove(chunk); if (editing == chunk) { editing = null; addButton.setText("Add chunk"); cancelEdit.setVisibility(View.GONE); }
                renderChunks();
            }).setContentDescription("Remove " + chunk.name);
            label(container, AudioEditPlan.time(chunk.fromMs) + " → " + AudioEditPlan.time(chunk.toMs)
                    + "  ·  " + AudioEditPlan.time(chunk.toMs - chunk.fromMs), 13, true);
            LinearLayout actions = row(container);
            button(actions, "Preview", () -> preview(chunk), false);
            button(actions, "Edit", () -> {
                editing = chunk; fromInput.setText(AudioEditPlan.time(chunk.fromMs)); toInput.setText(AudioEditPlan.time(chunk.toMs));
                chunkName.setText(chunk.name); addButton.setText("Save chunk"); cancelEdit.setVisibility(View.VISIBLE); fromInput.requestFocus();
            }, false);
            button(actions, "Save MP3", () -> export(Collections.singletonList(chunk), chunk.name), false);
        }
        updateSummary();
    }
    private void move(AudioChunk chunk, int direction) {
        int from = chunks.indexOf(chunk), to = from + direction;
        if (to >= 0 && to < chunks.size()) { Collections.swap(chunks, from, to); renderChunks(); }
    }
    private void updateSummary() {
        List<AudioChunk> selected = selectedChunks();
        summary.setText(selected.size() + " of " + chunks.size() + " selected · output " + AudioEditPlan.time(AudioEditPlan.duration(selected)));
        exportButton.setEnabled(!busy && source != null && !selected.isEmpty()); exportButton.setAlpha(exportButton.isEnabled() ? 1f : .45f);
    }
    private void export(List<AudioChunk> selected, String requestedName) {
        if (busy || source == null || selected.isEmpty()) { status.setText("Choose a file and select at least one chunk."); return; }
        final String name;
        try { name = OutputName.custom(requestedName.trim().isEmpty() ? "Edited audio" : requestedName); }
        catch (IllegalArgumentException error) { outputName.setError(error.getMessage()); return; }
        if (Build.VERSION.SDK_INT <= 28 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 71);
            status.setText("Allow storage access, then tap export again."); return;
        }
        stopPlayback(); engine.reset(); File input = source; ArrayList<AudioChunk> plan = new ArrayList<>(selected);
        setBusy(true, "Exporting " + plan.size() + " chunk(s) in this order… Keep this screen open.");
        body.post(() -> ((ScrollView)body.getParent()).smoothScrollTo(0,body.getHeight()));
        worker.execute(() -> {
            File output = null;
            try {
                EngineRuntime.initialize(getApplicationContext());
                output = File.createTempFile("editor-export-", ".mp3", getCacheDir());
                engine.render(getApplicationContext(), input, plan, output);
                if (engine.isCanceled() || Thread.currentThread().isInterrupted()) throw new InterruptedException();
                runOnUiThread(() -> { status.setText("Saving new MP3…"); cancelButton.setEnabled(false); });
                String saved = Mp3Publisher.publish(getApplicationContext(), output, name);
                runOnUiThread(() -> { if (!isDestroyed()) setBusy(false, "Saved " + saved); });
            } catch (Exception error) { finishError(error); }
            finally { if (output != null) output.delete(); }
        });
    }
    private void finishError(Exception error) {
        runOnUiThread(() -> {
            if (isDestroyed() || isFinishing()) return;
            boolean canceled = engine.isCanceled() || error instanceof InterruptedException;
            String message = canceled ? "Canceled. The source file is unchanged." : "Could not process audio: " + error.getMessage();
            setBusy(false, message);
            if (!canceled) new AlertDialog.Builder(this, dark ? android.R.style.Theme_Material_Dialog_Alert
                    : android.R.style.Theme_Material_Light_Dialog_Alert).setTitle("Audio editor").setMessage(message).setPositiveButton("OK", null).show();
        });
    }
    private void setBusy(boolean active, String message) {
        busy = active; enableControls(body, !active);
        cancelButton.setVisibility(active ? View.VISIBLE : View.GONE); cancelButton.setEnabled(active);
        progress.setVisibility(active ? View.VISIBLE : View.GONE); status.setText(message);
        playButton.setEnabled(!active && playerReady); seek.setEnabled(!active && playerReady);
        if (active) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        addButton.setEnabled(!active && source != null);
        if (!active) renderChunks(); else updateSummary();
    }
    private void enableControls(View view, boolean enabled) {
        if (view instanceof Button || view instanceof EditText || view instanceof SeekBar) view.setEnabled(enabled);
        if (view instanceof ViewGroup) for (int i = 0; i < ((ViewGroup)view).getChildCount(); i++) enableControls(((ViewGroup)view).getChildAt(i), enabled);
    }
    // Modern Android back gestures use the callback registered above; retain the API 24–32 path.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { handleBack(); }
    private void handleBack() {
        if (busy) { status.setText("Cancel the current operation before leaving."); return; } finish();
    }
    @Override protected void onPause() { super.onPause(); if (playerReady && player != null) player.pause(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); if (source == null) return;
        state.putString("source", source.getAbsolutePath()); state.putString("sourceName", sourceName); state.putLong("duration", durationMs);
        ArrayList<String> names = new ArrayList<>(); long[] starts = new long[chunks.size()], ends = new long[chunks.size()]; boolean[] selected = new boolean[chunks.size()];
        for (int i = 0; i < chunks.size(); i++) { AudioChunk c = chunks.get(i); names.add(c.name); starts[i] = c.fromMs; ends[i] = c.toMs; selected[i] = c.selected; }
        state.putStringArrayList("names", names); state.putLongArray("starts", starts); state.putLongArray("ends", ends); state.putBooleanArray("selected", selected);
        state.putInt("editingIndex", chunks.indexOf(editing));
    }
    @Override protected void onDestroy() {
        engine.cancel(); worker.shutdownNow(); releasePlayer();
        if (isFinishing() && source != null) source.delete(); super.onDestroy();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout weightedColumn(LinearLayout parent) { LinearLayout view = column(); parent.addView(view, new LinearLayout.LayoutParams(0, -2, 1)); return view; }
    private LinearLayout row(LinearLayout parent) { LinearLayout view = new LinearLayout(this); view.setGravity(android.view.Gravity.CENTER_VERTICAL); parent.addView(view, new LinearLayout.LayoutParams(-1,-2)); return view; }
    private LinearLayout card(LinearLayout parent) { LinearLayout view = column(); view.setPadding(dp(16),dp(16),dp(16),dp(16)); view.setBackground(shape(surface)); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,-2); params.setMargins(0,dp(16),0,dp(16)); parent.addView(view,params); return view; }
    private GradientDrawable shape(int fill) { GradientDrawable shape = new GradientDrawable(); shape.setColor(fill); shape.setCornerRadius(dp(14)); shape.setStroke(dp(1),border); return shape; }
    private TextView label(LinearLayout parent, String value, int size, boolean secondary) { TextView label = new TextView(this); label.setText(value); label.setTextSize(size); label.setTextColor(secondary ? muted : text); label.setPadding(dp(4),dp(5),0,dp(7)); parent.addView(label); return label; }
    private EditText input(LinearLayout parent, String hint, int id) { EditText input = new EditText(this); input.setId(id); input.setSingleLine(true); input.setHint(hint); input.setTextSize(15); input.setTextColor(text); input.setHintTextColor(muted); input.setPadding(dp(12),dp(12),dp(12),dp(12)); input.setBackground(shape(background)); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1,dp(54)); params.setMargins(0,dp(6),dp(4),dp(6)); parent.addView(input,params); return input; }
    private Button button(LinearLayout parent, String title, Runnable action, boolean primary) { Button button = new Button(this); button.setText(title); button.setTextSize(13); button.setAllCaps(false); button.setTextColor(primary ? Color.WHITE : text); button.setBackground(shape(primary ? accent : surface)); button.setPadding(dp(5),0,dp(5),0); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(parent.getOrientation() == LinearLayout.HORIZONTAL ? 0 : -1,dp(48),parent.getOrientation() == LinearLayout.HORIZONTAL ? 1 : 0); params.setMargins(0,dp(6),dp(4),dp(6)); parent.addView(button,params); button.setOnClickListener(view -> action.run()); return button; }
    private Button smallButton(LinearLayout parent, String title, Runnable action) { Button button = button(parent,title,action,false); button.setMinWidth(0); LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48),dp(48)); params.setMargins(dp(4),0,0,0); button.setLayoutParams(params); return button; }
}
