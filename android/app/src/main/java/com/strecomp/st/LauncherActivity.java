package com.strecomp.st;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * The start screen: bring in your own Spirit Tracks ROM (and, if you like, a save from another emulator), then
 * play. The ROM and the save are copied into the app's own storage (files/rom/bkie.nds, files/saves/bkie.sav); the
 * originals are only read. A save is never overwritten without a dated backup next to it.
 */
public final class LauncherActivity extends Activity {
    private static final int PICK_ROM = 1, PICK_SAVE = 2, EXPORT_SAVE = 3;
    // The game in this APK was recompiled from one ROM (clean, D-pad, or D-pad + QoL), and only runs with that one.
    private static final String ROM_SHA1 = BuildConfig.ROM_SHA1;
    private static final int SAVE_SIZE = 1024 * 1024;  // the cartridge's 8 Mbit flash

    private TextView status;
    private Button play, cont;

    private File rom() { return new File(getFilesDir(), "rom/bkie.nds"); }

    private File save() { return new File(getFilesDir(), "saves/bkie.sav"); }

    /** The resume state the game writes when it quits or goes to the background (frontend main.cpp). */
    private File resume(String ext) { return new File(getFilesDir(), "states/resume." + ext); }

    /** Intent extra from the game (GameActivity.restartWithResume): continue it again at once, in a fresh process. */
    static final String EXTRA_RELAUNCH = "relaunch";

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    /** Continue, once the old game process has exited (SDL's native side starts once per process), or after 4 s. */
    private void relaunchWhenGameGone(long until) {
        boolean alive = false;
        final android.app.ActivityManager am = (android.app.ActivityManager) getSystemService(ACTIVITY_SERVICE);
        final java.util.List<android.app.ActivityManager.RunningAppProcessInfo> procs = am.getRunningAppProcesses();
        if (procs != null)
            for (android.app.ActivityManager.RunningAppProcessInfo p : procs)
                if (p.processName.equals(getPackageName() + ":game")) alive = true;
        if (alive && System.currentTimeMillis() < until) {
            getWindow().getDecorView().postDelayed(() -> relaunchWhenGameGone(until), 100);
            return;
        }
        startActivity(new Intent(this, GameActivity.class).putExtra(GameActivity.EXTRA_CONTINUE, true));
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        final boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        final LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        final int pad = dp(landscape ? 12 : 20);
        box.setPadding(pad, pad, pad, pad);
        // A dark glass panel over the picture (LauncherArt), so the text reads on the bright sky.
        final GradientDrawable panel = new GradientDrawable();
        panel.setColor(0xC8101820);
        panel.setCornerRadius(dp(18));
        box.setBackground(panel);

        final TextView title = new TextView(this);
        title.setText("Spirit Tracks");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, landscape ? 26 : 34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        box.addView(title);

        final TextView sub = new TextView(this);
        sub.setText("The Legend of Zelda · native recomp");
        sub.setTextColor(0xFF8AB4FF);
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, landscape ? 11 : 13);
        sub.setGravity(Gravity.CENTER);
        box.addView(sub);

        status = new TextView(this);
        status.setTextColor(0xFFB0B0B8);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, landscape ? 11 : 14);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, dp(landscape ? 5 : 12), 0, dp(landscape ? 8 : 20));
        box.addView(status);

        // Continue: the game from where it was last quit (its resume state); Play: from the title screen.
        cont = button(box, "Continue", () -> startActivity(new Intent(this, GameActivity.class)
                .putExtra(GameActivity.EXTRA_CONTINUE, true)));
        play = button(box, "Play", () -> startActivity(new Intent(this, GameActivity.class)));
        button(box, "Choose your game ROM (.nds)", () -> pick(PICK_ROM));
        button(box, "Import a save (.sav)", () -> pick(PICK_SAVE));
        button(box, "Export the save", () -> {
            final Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            i.addCategory(Intent.CATEGORY_OPENABLE);
            i.setType("application/octet-stream");
            i.putExtra(Intent.EXTRA_TITLE, "SpiritTracks.sav");
            startActivityForResult(i, EXPORT_SAVE);
        });

        final TextView hint = new TextView(this);
        hint.setText("Use the ROM this app was built from (Spirit Tracks " + BuildConfig.ROM_VARIANT + ", made from your "
                + "own USA dump; the PC scripts copy it in for you). A save from melonDS (.sav) can be "
                + "imported; the save already here is kept as a dated backup. In the game, touch the picture: it is "
                + "the stylus. The ≡ button opens the menu. Continue picks up where you last quit; a save state never "
                + "changes the game's own save.");
        hint.setTextColor(0xFFA8B0B8);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, landscape ? 10 : 12);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(landscape ? 8 : 16), 0, 0);
        // (No wider than the screen leaves inside the panel: on a phone held upright 420 dp is too wide.)
        final int room = getResources().getDisplayMetrics().widthPixels - 2 * dp(16) - 2 * pad;
        box.addView(hint, new LinearLayout.LayoutParams(Math.min(dp(landscape ? 680 : 420), room),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        final LinearLayout page = new LinearLayout(this);
        page.setGravity(Gravity.CENTER);
        final int margin = dp(landscape ? 8 : 16);
        page.setPadding(margin, margin, margin, margin);
        page.addView(box, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        final ScrollView scroll = new ScrollView(this);
        scroll.setBackground(new LauncherArt());
        scroll.setFillViewport(true);
        scroll.addView(page);
        setContentView(scroll);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        if (getIntent() != null && getIntent().getBooleanExtra(EXTRA_RELAUNCH, false)) {
            getIntent().removeExtra(EXTRA_RELAUNCH);
            if (resume("state").isFile()) relaunchWhenGameGone(System.currentTimeMillis() + 4000);
        }
    }

    private void refresh() {
        final boolean haveRom = rom().isFile();
        final StringBuilder s = new StringBuilder();
        s.append(haveRom ? "Game ROM: ready" : "Choose your own Spirit Tracks (USA) ROM to start.");
        s.append('\n');
        s.append(save().isFile() ? "Save: " + new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US)
                .format(new Date(save().lastModified())) : "Save: none yet (the game makes one when you save)");
        status.setText(s);
        play.setEnabled(haveRom);
        play.setAlpha(haveRom ? 1f : 0.5f);
        final boolean haveResume = haveRom && resume("state").isFile();
        cont.setVisibility(haveResume ? View.VISIBLE : View.GONE);
        if (haveResume) cont.setText("Continue  (" + resumeTime() + ")");
    }

    /** When the resume state was written ("MM-DD HH:MM", from resume.txt). */
    private String resumeTime() {
        try (BufferedReader r = new BufferedReader(new FileReader(resume("txt")))) {
            final String line = r.readLine();
            if (line != null && !line.isEmpty()) return line.trim();
        } catch (IOException ignored) {
            // (no time: the file's date instead)
        }
        return new SimpleDateFormat("MM-dd HH:mm", Locale.US).format(new Date(resume("state").lastModified()));
    }

    private Button button(LinearLayout box, String text, Runnable action) {
        final boolean landscape = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        final Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        if (landscape) {
            b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            b.setMinHeight(dp(36));
            b.setMinimumHeight(dp(36));
            b.setPadding(dp(8), 0, dp(8), 0);
        }
        b.setOnClickListener(v -> action.run());
        final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(landscape ? 300 : 320),
                landscape ? dp(36) : ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(landscape ? 3 : 8);
        box.addView(b, lp);
        return b;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    private void pick(int request) {
        final Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        startActivityForResult(i, request);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        final Uri uri = data.getData();
        // Large copies off the UI thread.
        new Thread(() -> {
            String message;
            try {
                if (request == PICK_ROM) message = importRom(uri);
                else if (request == PICK_SAVE) message = importSave(uri);
                else message = exportSave(uri);
            } catch (IOException e) {
                message = "That did not work: " + e.getMessage();
            }
            final String m = message;
            runOnUiThread(() -> {
                refresh();
                new AlertDialog.Builder(this).setMessage(m).setPositiveButton("OK", null).show();
            });
        }).start();
    }

    private String importRom(Uri uri) throws IOException {
        final File dest = rom(), tmp = new File(dest.getPath() + ".tmp");
        //noinspection ResultOfMethodCallIgnored
        dest.getParentFile().mkdirs();
        final String sha1 = copy(uri, tmp);
        if (ROM_SHA1.isEmpty() || ROM_SHA1.equals(sha1)) {
            if (dest.exists() && !dest.delete()) throw new IOException("cannot replace the ROM");
            if (!tmp.renameTo(dest)) throw new IOException("cannot store the ROM");
            return "Game ROM ready. Press Play.";
        }
        //noinspection ResultOfMethodCallIgnored
        tmp.delete();
        return "That file is not the ROM this app was built from: Spirit Tracks " + BuildConfig.ROM_VARIANT
                + " (SHA-1 " + ROM_SHA1 + ").\nThis file's SHA-1: " + sha1;
    }

    private String importSave(Uri uri) throws IOException {
        final File dest = save(), tmp = new File(dest.getPath() + ".tmp");
        //noinspection ResultOfMethodCallIgnored
        dest.getParentFile().mkdirs();
        copy(uri, tmp);
        if (tmp.length() != SAVE_SIZE) {
            final long n = tmp.length();
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return "That save is " + n + " bytes; a Spirit Tracks save is " + SAVE_SIZE + " bytes (melonDS's .sav).";
        }
        if (dest.isFile()) {
            final String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date());
            if (!dest.renameTo(new File(dest.getPath() + ".bak-" + stamp))) throw new IOException("cannot back up the old save");
        }
        if (!tmp.renameTo(dest)) throw new IOException("cannot store the save");
        return "Save imported (the previous one, if any, is kept as a backup).";
    }

    private String exportSave(Uri uri) throws IOException {
        if (!save().isFile()) return "There is no save yet.";
        try (InputStream in = new FileInputStream(save()); OutputStream out = getContentResolver().openOutputStream(uri)) {
            if (out == null) throw new IOException("cannot write there");
            final byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return "Save exported.";
    }

    /** Copies a picked file to `to` and returns its SHA-1 (lowercase hex). */
    private String copy(Uri uri, File to) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri); OutputStream out = new FileOutputStream(to)) {
            if (in == null) throw new IOException("cannot open the file");
            final MessageDigest sha = MessageDigest.getInstance("SHA-1");
            final byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
                sha.update(buf, 0, n);
            }
            final StringBuilder hex = new StringBuilder();
            for (byte b : sha.digest()) hex.append(String.format(Locale.US, "%02x", b));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
