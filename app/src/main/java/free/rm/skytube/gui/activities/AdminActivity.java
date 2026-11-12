/*
 * SafeTube - based on SkyTube (GPLv3). Whitelist-only modifications.
 */
package free.rm.skytube.gui.activities;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import free.rm.skytube.businessobjects.YouTube.POJOs.YouTubeChannel;
import free.rm.skytube.businessobjects.YouTube.newpipe.NewPipeService;
import free.rm.skytube.businessobjects.whitelist.Whitelist;
import free.rm.skytube.businessobjects.whitelist.WhitelistSync;
import free.rm.skytube.businessobjects.whitelist.Whitelists;

/**
 * PIN-protected content management. Everything is built in code to keep the screen self-contained.
 * Leaving the screen (onStop) closes it, so it is locked again next time.
 */
public class AdminActivity extends AppCompatActivity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout root;
    private Whitelist wl;
    private boolean unlocked = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE);
        setTitle("Administrator");
        wl = Whitelists.get();
        ScrollView sv = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        sv.addView(root);
        setContentView(sv);
        render();
    }

    @Override
    protected void onStop() {
        super.onStop();
        // re-lock whenever the screen is left
        unlocked = false;
        if (!isChangingConfigurations()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdownNow();
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void render() {
        root.removeAllViews();
        if (wl == null) {
            label("Not ready. Please restart the app.");
        } else if (!wl.isPinSet()) {
            showCreatePin();
        } else if (!unlocked) {
            showEnterPin();
        } else {
            showAdmin();
        }
    }

    // ------------------------------------------------------------------ PIN screens

    private void showCreatePin() {
        heading("Create administrator PIN");
        label("There is no default PIN. Choose one (at least " + Whitelist.MIN_PIN_LENGTH + " digits) and keep it safe. It cannot be recovered.");
        final EditText p1 = pinField("New PIN");
        final EditText p2 = pinField("Repeat PIN");
        button("Save PIN", v -> {
            String a = p1.getText().toString();
            String b = p2.getText().toString();
            if (!a.equals(b)) {
                toast("PINs do not match");
            } else if (!wl.setInitialPin(a)) {
                toast("PIN must be at least " + Whitelist.MIN_PIN_LENGTH + " digits");
            } else {
                unlocked = true;
                render();
            }
        });
    }

    private void showEnterPin() {
        heading("Enter administrator PIN");
        final EditText pin = pinField("PIN");
        button("Unlock", v -> {
            Whitelist.PinResult r = wl.verifyPin(pin.getText().toString());
            switch (r) {
                case OK:
                    unlocked = true;
                    render();
                    break;
                case LOCKED:
                    toast("Too many attempts. Try again in " + wl.lockedForSeconds() + " seconds.");
                    pin.setText("");
                    break;
                default:
                    toast("Wrong PIN");
                    pin.setText("");
            }
        });
    }

    // ------------------------------------------------------------------ management screen

    private void showAdmin() {
        heading("Approved channels");
        final EditText add = textField("Channel URL or ID (e.g. https://www.youtube.com/channel/UC...)");
        button("Look up and add channel", v -> addChannel(add.getText().toString()));
        Map<String, String> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Map.Entry<String, String> e : wl.getApprovedChannels().entrySet()) {
            sorted.put(e.getValue() + "  [" + e.getKey() + "]", e.getKey());
        }
        if (sorted.isEmpty()) {
            label("No channels approved yet. Nothing is shown in the app until you add one.");
        }
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            final String id = e.getValue();
            row(e.getKey(), "Remove", v -> new AlertDialog.Builder(this)
                    .setMessage("Remove this channel? Its videos will disappear from the app.")
                    .setPositiveButton("Remove", (d, w) -> {
                        wl.removeChannel(id);
                        WhitelistSync.reconcileAsync(this);
                        render();
                    })
                    .setNegativeButton("Cancel", null).show());
        }

        heading("Blocked videos");
        final EditText bv = textField("Video URL or ID to block");
        button("Block video", v -> {
            if (wl.blockVideo(bv.getText().toString().trim())) {
                render();
            } else {
                toast("Not a valid video URL or ID");
            }
        });
        for (final String id : new TreeSet<>(wl.getBlockedVideos())) {
            row(id, "Restore", v -> { wl.unblockVideo(id); render(); });
        }

        heading("Blocked playlists");
        final EditText bp = textField("Playlist URL or ID to block");
        button("Block playlist", v -> {
            if (wl.blockPlaylist(bp.getText().toString().trim())) {
                render();
            } else {
                toast("Not a valid playlist URL or ID");
            }
        });
        for (final String id : new TreeSet<>(wl.getBlockedPlaylists())) {
            row(id, "Restore", v -> { wl.unblockPlaylist(id); render(); });
        }

        heading("Change PIN");
        final EditText oldPin = pinField("Current PIN");
        final EditText newPin = pinField("New PIN");
        button("Change PIN", v -> {
            if (wl.changePin(oldPin.getText().toString(), newPin.getText().toString())) {
                toast("PIN changed");
                oldPin.setText("");
                newPin.setText("");
            } else {
                toast("Current PIN wrong, or new PIN too short");
            }
        });
    }

    private void addChannel(final String input) {
        if (input == null || input.trim().isEmpty()) {
            toast("Enter a channel URL or ID");
            return;
        }
        toast("Looking up channel...");
        executor.execute(() -> {
            try {
                YouTubeChannel ch = NewPipeService.get().lookupChannelForAdmin(input.trim());
                final String id = Whitelist.normalizeChannelId(ch.getChannelId() == null ? null : ch.getChannelId().getRawId());
                final String name = ch.getTitle();
                runOnUiThread(() -> {
                    if (id == null) {
                        toast("Could not verify a channel ID for that address");
                        return;
                    }
                    new AlertDialog.Builder(this)
                            .setTitle("Add this channel?")
                            .setMessage(name + "\n" + id)
                            .setPositiveButton("Add", (d, w) -> {
                                wl.addChannel(id, name);
                                WhitelistSync.reconcileAsync(this);
                                render();
                            })
                            .setNegativeButton("Cancel", null).show();
                });
            } catch (Exception e) {
                runOnUiThread(() -> toast("Lookup failed: " + e.getMessage()));
            }
        });
    }

    // ------------------------------------------------------------------ tiny view helpers

    private void heading(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(20);
        tv.setPadding(0, dp(20), 0, dp(8));
        root.addView(tv);
    }

    private void label(String t) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setPadding(0, dp(4), 0, dp(4));
        root.addView(tv);
    }

    private EditText textField(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setSingleLine(true);
        root.addView(et);
        return et;
    }

    private EditText pinField(String hint) {
        EditText et = textField(hint);
        et.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        return et;
    }

    private void button(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setOnClickListener(l);
        root.addView(b);
    }

    private void row(String text, String action, View.OnClickListener l) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setLayoutParams(new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button b = new Button(this);
        b.setText(action);
        b.setOnClickListener(l);
        r.addView(tv);
        r.addView(b);
        root.addView(r);
    }

    private void toast(String m) {
        Toast.makeText(this, m, Toast.LENGTH_SHORT).show();
    }
}
