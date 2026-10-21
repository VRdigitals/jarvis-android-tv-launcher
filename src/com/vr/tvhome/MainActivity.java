package com.vr.tvhome;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    /** Big cards in the "Featured" row: see Config.FEATURED. */
    private static final String[][] FEATURED = Config.FEATURED;

    /** Not shown as apps: this app, other launchers, and things that have their own tile. */
    private static final Set<String> HIDDEN = new HashSet<>(Arrays.asList(
            "com.vr.tvhome",
            "com.google.android.apps.tv.launcherx",
            "me.efesser.flauncher",
            "com.android.tv.settings",
            "com.tcl.tv",
            "com.tcl.dashboard",
            "com.tcl.suspension",
            "com.tcl.guard",
            "com.tcl.partnercustomizer",
            "com.tcl.t_solo"));

    private static final int ACCENT = 0xFF4DA3FF;
    private static final int GLASS = 0xA60A111C;
    private static final int TEXT_DIM = 0xB3FFFFFF;
    private static final long WEATHER_MS = 30 * 60_000;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            new Thread(() -> {
                Weather w = Weather.fetch(MainActivity.this);
                if (w != null) runOnUiThread(() -> showWeather(w));
            }).start();
            handler.postDelayed(this, WEATHER_MS);
        }
    };
    private final BroadcastReceiver packagesChanged = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            buildApps();
        }
    };

    private ImageView weatherIcon;
    private TextView weatherCity, weatherTemp, weatherDesc, weatherRange;
    private LinearLayout featuredRow, appsPanelRow, inputsRow, allAppsGrid;
    private HorizontalScrollView featuredScroll;
    private View homePage, appsPage;
    private TextView tabHome, tabApps;
    private Assistant assistant;
    /** Blocks that slide in when the screen appears, in order. */
    private final List<View> entrance = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setBackgroundDrawable(null); // the wallpaper covers the screen
        setContentView(buildUi());
        buildApps();
        buildInputs();
        Weather cached = Weather.cached(this);
        if (cached != null) showWeather(cached);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packagesChanged, f);
        playEntrance(true);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packagesChanged);
        assistant.release();
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        handler.post(weatherTick);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        playEntrance(false);
    }

    @Override
    protected void onStop() {
        handler.removeCallbacks(weatherTick);
        assistant.onHidden();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        assistant.onShown();
        if (appsPage.getVisibility() != View.VISIBLE) focusFeatured();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Home pressed while already home: back to the Home tab, first card
        showPage(false);
        featuredScroll.smoothScrollTo(0, 0);
        focusFeatured();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN && assistant != null) assistant.onUserActivity();
        return super.dispatchKeyEvent(event);
    }

    @Override
    public void onBackPressed() {
        if (appsPage.getVisibility() == View.VISIBLE) showPage(false);
        // On the home page there is nothing to go back to
    }

    // ================================================================ layout

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xFF02040A);

        ImageView wallpaper = new ImageView(this);
        wallpaper.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Bitmap wp = loadWallpaper();
        if (wp != null) {
            wallpaper.setImageBitmap(wp);
        } else {
            // No assets/wallpaper.jpg: a deep-navy gradient instead
            wallpaper.setImageDrawable(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                    new int[]{0xFF0B2347, 0xFF06122A, 0xFF02040A}));
        }
        root.addView(wallpaper, match());

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(32), dp(16), dp(32), dp(14));
        page.setClipChildren(false);
        page.setClipToPadding(false);
        root.addView(page, match());

        View bar = topBar();
        entrance.add(bar);
        page.addView(bar);

        FrameLayout body = new FrameLayout(this);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        page.addView(body, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
        homePage = homePage();
        appsPage = appsPage();
        appsPage.setVisibility(View.GONE);
        body.addView(homePage, match());
        body.addView(appsPage, match());
        return root;
    }

    private View topBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        bar.setPadding(0, dp(4), 0, dp(4));

        tabHome = new TextView(this);
        tabApps = new TextView(this);
        bar.addView(tab(tabHome, "Home", Icons.Type.HOME, () -> showPage(false)));
        bar.addView(tab(tabApps, "Apps", Icons.Type.GRID, () -> showPage(true)));
        bar.addView(tab(new TextView(this), "Live TV", Icons.Type.LIVE, withLine("input.tv", () -> launchPackage("com.tcl.tv"))));
        bar.addView(tab(new TextView(this), "Settings", Icons.Type.SETTINGS, () -> {
            assistant.sayNow("sys.settings");
            start(new Intent(Settings.ACTION_SETTINGS));
        }));
        setTabSelected(tabHome, true);
        setTabSelected(tabApps, false);

        bar.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        LinearLayout clockBox = new LinearLayout(this);
        clockBox.setOrientation(LinearLayout.VERTICAL);
        clockBox.setGravity(Gravity.END);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm a");
        clock.setFormat24Hour("H:mm");
        style(clock, 20, "sans-serif-medium", Color.WHITE);
        clock.setIncludeFontPadding(false);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEE, d MMM");
        date.setFormat24Hour("EEE, d MMM");
        style(date, 11, "sans-serif", TEXT_DIM);
        clockBox.addView(clock);
        clockBox.addView(date);
        bar.addView(clockBox);

        ImageView wifi = new ImageView(this);
        wifi.setImageDrawable(Icons.of(Icons.Type.WIFI, Color.WHITE));
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(dp(22), dp(22));
        wl.leftMargin = dp(14);
        bar.addView(wifi, wl);
        return bar;
    }

    private View homePage() {
        LinearLayout home = new LinearLayout(this);
        home.setOrientation(LinearLayout.VERTICAL);
        home.setClipChildren(false);
        home.setClipToPadding(false);

        // --- hero: weather | (hologram shows through) | voice assistant | cast & quick settings
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.HORIZONTAL);
        hero.setClipChildren(false);
        hero.setClipToPadding(false);
        hero.setPadding(0, dp(14), 0, 0);
        home.addView(hero, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(212)));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        View weather = weatherCard();
        entrance.add(weather);
        left.addView(weather, new LinearLayout.LayoutParams(dp(196), dp(112)));
        hero.addView(left, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));

        View voice = assistantCard();
        entrance.add(voice);
        LinearLayout.LayoutParams vl = (LinearLayout.LayoutParams) voice.getLayoutParams();
        vl.height = ViewGroup.LayoutParams.MATCH_PARENT;
        vl.leftMargin = dp(16);
        hero.addView(voice);

        LinearLayout right = new LinearLayout(this);
        right.setOrientation(LinearLayout.VERTICAL);
        right.setClipChildren(false);
        right.setClipToPadding(false);
        View cast = iconPanel("Cast & Connect", new Object[][]{
                {"AirPlay", Icons.Type.AIRPLAY, (Runnable) this::airplayHint},
                {"Mirror", Icons.Type.MIRROR, (Runnable) () -> startAction("com.tcl.miracast.app.DeviceList")},
                {"Headphones", Icons.Type.HEADPHONES, (Runnable) () -> launchPackage("com.tcl.t_solo")},
                {"Devices", Icons.Type.DEVICES, (Runnable) () -> startAction("com.android.tv.settings.ACCESSORIES_CONNECTED_DEVICES")},
        });
        View quick = iconPanel("Quick Settings", new Object[][]{
                {"Wi-Fi", Icons.Type.WIFI, (Runnable) () -> startAction(Settings.ACTION_WIFI_SETTINGS)},
                {"Bluetooth", Icons.Type.BLUETOOTH, (Runnable) () -> startAction("com.android.tv.settings.ACCESSORIES_CONNECTED_DEVICES")},
                {"Picture", Icons.Type.PICTURE, (Runnable) () -> startAction(Settings.ACTION_DISPLAY_SETTINGS)},
                {"Sound", Icons.Type.SOUND, (Runnable) () -> startAction(Settings.ACTION_SOUND_SETTINGS)},
        });
        entrance.add(cast);
        entrance.add(quick);
        right.addView(cast, new LinearLayout.LayoutParams(dp(272), 0, 1f));
        right.addView(quick, sized(dp(272), 0, 0, dp(8), 1f));
        hero.addView(right, sized(dp(272), ViewGroup.LayoutParams.MATCH_PARENT, dp(16)));

        // --- featured apps
        TextView featuredLabel = sectionLabel("Featured");
        entrance.add(featuredLabel);
        home.addView(featuredLabel, sized(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(12), 0f));
        featuredRow = row(dp(10));
        featuredScroll = scroller(featuredRow);
        entrance.add(featuredScroll);
        home.addView(featuredScroll);

        // --- bottom: apps panel | inputs panel
        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.HORIZONTAL);
        bottom.setClipChildren(false);
        bottom.setClipToPadding(false);
        appsPanelRow = row(dp(8));
        inputsRow = row(dp(8));
        View appsPanel = panel("Apps", appsPanelRow);
        View inputsPanel = panel("Inputs", inputsRow);
        entrance.add(appsPanel);
        entrance.add(inputsPanel);
        bottom.addView(appsPanel, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        bottom.addView(inputsPanel, sized(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, dp(12)));
        home.addView(bottom, sized(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(4), 0f));
        return home;
    }

    private View appsPage() {
        ScrollView scroll = new ScrollView(this);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        scroll.setVerticalScrollBarEnabled(false);
        allAppsGrid = new LinearLayout(this);
        allAppsGrid.setOrientation(LinearLayout.VERTICAL);
        allAppsGrid.setPadding(dp(10), dp(24), dp(10), dp(24));
        allAppsGrid.setClipChildren(false);
        allAppsGrid.setClipToPadding(false);
        scroll.addView(allAppsGrid);
        return scroll;
    }

    // ---------------------------------------------------------------- animation

    /** Panels rise and fade in one after another. Full on first show, quicker on return. */
    private void playEntrance(boolean full) {
        long step = full ? 55 : 25;
        long duration = full ? 560 : 320;
        float rise = dp(full ? 26 : 12);
        for (int i = 0; i < entrance.size(); i++) {
            View v = entrance.get(i);
            v.animate().cancel();
            v.setAlpha(0f);
            v.setTranslationY(rise);
            v.animate().alpha(1f).translationY(0f)
                    .setStartDelay(i * step).setDuration(duration)
                    .setInterpolator(new DecelerateInterpolator(2.2f)).start();
        }
    }

    private void showPage(boolean apps) {
        View in = apps ? appsPage : homePage;
        View out = apps ? homePage : appsPage;
        setTabSelected(tabHome, !apps);
        setTabSelected(tabApps, apps);
        if (in.getVisibility() == View.VISIBLE) return;

        float shift = dp(40) * (apps ? 1 : -1);
        out.animate().cancel();
        out.animate().alpha(0f).translationX(-shift).setDuration(200)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    out.setVisibility(View.GONE);
                    out.setTranslationX(0f);
                    out.setAlpha(1f);
                }).start();
        in.animate().cancel();
        in.setVisibility(View.VISIBLE);
        in.setAlpha(0f);
        in.setTranslationX(shift);
        in.animate().alpha(1f).translationX(0f).setStartDelay(90).setDuration(340)
                .setInterpolator(new DecelerateInterpolator(2f)).start();

        if (apps && allAppsGrid.getChildCount() > 0) {
            // grid row -> column -> FocusFx holder -> card
            ViewGroup row = (ViewGroup) allAppsGrid.getChildAt(0);
            ViewGroup column = (ViewGroup) row.getChildAt(0);
            View first = ((ViewGroup) column.getChildAt(0)).getChildAt(0);
            first.post(first::requestFocus);
        } else if (!apps) {
            focusFeatured();
        }
    }

    // ---------------------------------------------------------------- cards

    private View weatherCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(glass());
        card.setPadding(dp(14), dp(10), dp(14), dp(10));

        weatherIcon = new ImageView(this);
        weatherIcon.setImageDrawable(Icons.of(Icons.Type.PARTLY_DAY, Color.WHITE));
        card.addView(weatherIcon, new LinearLayout.LayoutParams(dp(54), dp(54)));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setPadding(dp(12), 0, 0, 0);
        TextView label = new TextView(this);
        style(label, 10, "sans-serif-medium", TEXT_DIM);
        label.setText(Config.WEATHER_CITY.isEmpty() ? "Weather" : Config.WEATHER_CITY);
        weatherCity = label;
        weatherTemp = new TextView(this);
        style(weatherTemp, 28, "sans-serif-light", Color.WHITE);
        weatherTemp.setIncludeFontPadding(false);
        weatherTemp.setText("--°");
        weatherDesc = new TextView(this);
        style(weatherDesc, 11, "sans-serif", Color.WHITE);
        weatherDesc.setText("Loading weather");
        weatherRange = new TextView(this);
        style(weatherRange, 10, "sans-serif", TEXT_DIM);
        text.addView(label);
        text.addView(weatherTemp);
        text.addView(weatherDesc);
        text.addView(weatherRange);
        card.addView(text);
        return card;
    }

    private void showWeather(Weather w) {
        weatherIcon.setImageDrawable(Icons.of(w.icon(), Color.WHITE));
        if (!w.city.isEmpty()) weatherCity.setText(w.city);
        weatherTemp.setText(w.temp + "°C");
        weatherDesc.setText(w.description());
        weatherRange.setText("H: " + w.high + "°   L: " + w.low + "°");
    }

    /** The J.A.R.V.I.S. card: avatar, status, a speech bubble that types out, and voice bars. */
    private View assistantCard() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(glass());
        card.setPadding(dp(16), dp(12), dp(16), dp(10));
        card.setFocusable(true);
        card.setClickable(true);
        card.setLongClickable(true);

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        // Arc reactor with a soft blue glow behind it; both come alive while speaking
        FrameLayout reactorBox = new FrameLayout(this);
        reactorBox.setClipChildren(false);
        View glow = new View(this);
        GradientDrawable halo = new GradientDrawable();
        halo.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        halo.setGradientRadius(dp(30));
        halo.setColors(new int[]{0xCC4DA3FF, 0x404DA3FF, 0x004DA3FF});
        glow.setBackground(halo);
        glow.setAlpha(Assistant.IDLE_GLOW);
        reactorBox.addView(glow, new FrameLayout.LayoutParams(dp(60), dp(60), Gravity.CENTER));
        ImageView avatar = new ImageView(this);
        Bitmap reactorImage = loadAsset("arc_reactor.png");
        if (reactorImage != null) avatar.setImageBitmap(reactorImage);
        else avatar.setImageDrawable(Icons.reactor()); // no assets/arc_reactor.png: drawn one
        reactorBox.addView(avatar, new FrameLayout.LayoutParams(dp(46), dp(46), Gravity.CENTER));
        head.addView(reactorBox, new LinearLayout.LayoutParams(dp(56), dp(56)));
        LinearLayout names = new LinearLayout(this);
        names.setOrientation(LinearLayout.VERTICAL);
        names.setPadding(dp(10), 0, 0, 0);
        TextView title = new TextView(this);
        style(title, 12, "sans-serif-medium", ACCENT);
        title.setText("J.A.R.V.I.S.");
        title.setLetterSpacing(0.15f);
        TextView status = new TextView(this);
        style(status, 10, "sans-serif", TEXT_DIM);
        names.addView(title);
        names.addView(status);
        head.addView(names);
        card.addView(head);

        TextView bubble = new TextView(this);
        style(bubble, 14, "sans-serif-medium", Color.WHITE);
        bubble.setMaxLines(3);
        bubble.setLineSpacing(dp(2), 1f);
        card.addView(bubble, sized(ViewGroup.LayoutParams.MATCH_PARENT, 0, 0, dp(10), 1f));

        View waveView = new View(this);
        Icons.WaveDrawable wave = Icons.wave(ACCENT);
        waveView.setBackground(wave);
        card.addView(waveView, sized(ViewGroup.LayoutParams.MATCH_PARENT, dp(18), 0, dp(4), 0f));

        TextView hint = new TextView(this);
        style(hint, 9, "sans-serif", 0x80FFFFFF);
        hint.setText("OK: suggestion / Google  ·  Hold OK: mute");
        card.addView(hint, sized(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(6), 0f));

        assistant = new Assistant(this, bubble, status, wave, avatar, glow);
        FrameLayout holder = FocusFx.wrap(card, dp(214), ViewGroup.LayoutParams.MATCH_PARENT, dp(14), 1.03f, false, () -> {
            String pkg = assistant.suggestion();
            if (pkg != null) launchPackage(pkg);
            else startAction(Intent.ACTION_ASSIST);
        });
        card.setOnLongClickListener(v -> {
            assistant.toggleMute();
            return true;
        });
        return holder;
    }

    /** Runs {@code r} when {@code card} gains focus, on top of its FocusFx effect. */
    private void onFocused(View card, Runnable r) {
        View.OnFocusChangeListener fx = card.getOnFocusChangeListener();
        card.setOnFocusChangeListener((v, has) -> {
            fx.onFocusChange(v, has);
            if (has) r.run();
        });
    }

    /** The assistant comments first; the switch follows a beat later so you see the line. */
    private Runnable withLine(String key, Runnable go) {
        return () -> {
            assistant.sayNow(key);
            handler.postDelayed(go, 1200);
        };
    }

    /** A glass panel with a title and a row of small icon tiles. Each entry: {label, Icons.Type, Runnable}. */
    private View iconPanel(String title, Object[][] items) {
        LinearLayout row = row(dp(6));
        for (Object[] it : items) {
            row.addView(iconTile((String) it[0], (Icons.Type) it[1], (Runnable) it[2], dp(54), dp(52), 18));
        }
        return panel(title, row);
    }

    private View panel(String title, LinearLayout content) {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(glass());
        p.setPadding(dp(12), dp(8), dp(12), dp(10));
        p.setClipChildren(false);
        p.setClipToPadding(false);
        TextView t = new TextView(this);
        style(t, 12, "sans-serif-medium", Color.WHITE);
        t.setText(title);
        p.addView(t);
        p.addView(content);
        return p;
    }

    // ---------------------------------------------------------------- data

    private void buildApps() {
        PackageManager pm = getPackageManager();
        Intent query = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        Map<String, ResolveInfo> byPkg = new LinkedHashMap<>();
        for (ResolveInfo ri : pm.queryIntentActivities(query, 0)) {
            String pkg = ri.activityInfo.packageName;
            if (!HIDDEN.contains(pkg) && !byPkg.containsKey(pkg)) byPkg.put(pkg, ri);
        }
        List<ResolveInfo> all = new ArrayList<>(byPkg.values());
        all.sort((a, b) -> a.loadLabel(pm).toString().compareToIgnoreCase(b.loadLabel(pm).toString()));

        // Featured cards
        featuredRow.removeAllViews();
        Set<String> featured = new HashSet<>();
        for (String[] f : FEATURED) {
            ResolveInfo ri = byPkg.get(f[0]);
            if (ri == null) continue;
            featured.add(f[0]);
            featuredRow.addView(featureCard(ri, f[1]));
        }

        // Apps panel: up to 4 non-featured apps, then "All apps"
        appsPanelRow.removeAllViews();
        int shown = 0;
        for (ResolveInfo ri : all) {
            if (featured.contains(ri.activityInfo.packageName) || shown == 4) continue;
            final String pkg = ri.activityInfo.packageName;
            appsPanelRow.addView(smallTile(ri.loadLabel(pm).toString(), ri.loadIcon(pm), () -> launchPackage(pkg), dp(64), dp(60), 24));
            shown++;
        }
        appsPanelRow.addView(iconTile("All apps", Icons.Type.GRID, () -> showPage(true), dp(64), dp(60), 20));

        // All apps page: banners, 6 per row
        allAppsGrid.removeAllViews();
        LinearLayout line = null;
        for (int i = 0; i < all.size(); i++) {
            if (i % 6 == 0) {
                line = row(0);
                allAppsGrid.addView(line, sized(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, i == 0 ? 0 : dp(22), 0f));
            }
            line.addView(bannerTile(all.get(i)));
        }
    }

    private void buildInputs() {
        inputsRow.removeAllViews();
        if (launchIntent("com.tcl.tv") != null) {
            inputsRow.addView(iconTile("TV", Icons.Type.TV, withLine("input.tv", () -> launchPackage("com.tcl.tv")), dp(60), dp(60), 20));
        }
        TvInputManager tim = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        if (tim != null) {
            List<TvInputInfo> inputs = new ArrayList<>();
            for (TvInputInfo info : tim.getTvInputList()) {
                if (!info.isPassthroughInput() || info.getParentId() != null || info.isHidden(this)) continue;
                inputs.add(info);
            }
            inputs.sort((a, b) -> inputLabel(a).compareToIgnoreCase(inputLabel(b)));
            for (TvInputInfo info : inputs) {
                final String id = info.getId();
                String label = inputLabel(info);
                Icons.Type icon = label.toUpperCase().startsWith("AV") ? Icons.Type.AV : Icons.Type.HDMI;
                // Explicit type: TCL's Live TV app only matches on the channel MIME type
                String line = icon == Icons.Type.AV ? "input.av" : "input.hdmi";
                inputsRow.addView(iconTile(label, icon, withLine(line, () -> start(new Intent(Intent.ACTION_VIEW)
                        .setDataAndType(TvContract.buildChannelUriForPassthroughInput(id),
                                TvContract.Channels.CONTENT_ITEM_TYPE))), dp(60), dp(60), 20));
            }
        }
        if (launchIntent("com.tcl.ui_mediaCenter") != null) {
            inputsRow.addView(iconTile("USB", Icons.Type.USB, withLine("input.usb", () -> launchPackage("com.tcl.ui_mediaCenter")), dp(60), dp(60), 20));
        }
    }

    private String inputLabel(TvInputInfo info) {
        CharSequence custom = info.loadCustomLabel(this);
        return (custom != null && custom.length() > 0 ? custom : info.loadLabel(this)).toString();
    }

    // ---------------------------------------------------------------- tiles

    private Drawable bannerOrNull(ResolveInfo ri) {
        PackageManager pm = getPackageManager();
        Drawable banner = ri.activityInfo.loadBanner(pm);
        return banner != null ? banner : ri.activityInfo.applicationInfo.loadBanner(pm);
    }

    private View featureCard(ResolveInfo ri, String subtitle) {
        PackageManager pm = getPackageManager();
        final String pkg = ri.activityInfo.packageName;
        FrameLayout card = new FrameLayout(this);
        card.setBackground(rounded(0xFF151C28, 12));
        card.setClipToOutline(true);
        card.setFocusable(true);
        card.setClickable(true);

        ImageView img = new ImageView(this);
        Drawable banner = bannerOrNull(ri);
        if (banner != null) {
            img.setImageDrawable(banner);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            img.setImageDrawable(ri.loadIcon(pm));
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setPadding(dp(40), dp(16), dp(40), dp(34));
        }
        card.addView(img, match());

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.BOTTOM_TOP,
                new int[]{0xE6000000, 0x66000000, 0x00000000}));
        card.addView(shade, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(30), Gravity.BOTTOM));

        // The banner already shows the app's name, so only add the subtitle
        TextView sub = new TextView(this);
        style(sub, 10, "sans-serif-medium", Color.WHITE);
        sub.setText(subtitle);
        sub.setShadowLayer(dp(3), 0, 0, 0xFF000000);
        sub.setPadding(dp(10), 0, dp(10), dp(6));
        card.addView(sub, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM));

        FrameLayout holder = FocusFx.wrap(card, dp(162), dp(91), dp(12), 1.08f, false, () -> launchPackage(pkg));
        onFocused(card, () -> assistant.react("app." + pkg));
        ((LinearLayout.LayoutParams) holder.getLayoutParams()).rightMargin = dp(14);
        return holder;
    }

    private View bannerTile(ResolveInfo ri) {
        PackageManager pm = getPackageManager();
        final String pkg = ri.activityInfo.packageName;
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);

        FrameLayout card = new FrameLayout(this);
        card.setBackground(rounded(0x33FFFFFF, 10));
        card.setClipToOutline(true);
        card.setFocusable(true);
        card.setClickable(true);
        ImageView img = new ImageView(this);
        Drawable banner = bannerOrNull(ri);
        if (banner != null) {
            img.setImageDrawable(banner);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        } else {
            img.setImageDrawable(ri.loadIcon(pm));
            img.setScaleType(ImageView.ScaleType.FIT_CENTER);
            img.setPadding(dp(16), dp(12), dp(16), dp(12));
        }
        card.addView(img, match());
        column.addView(FocusFx.wrap(card, dp(128), dp(72), dp(10), 1.1f, false, () -> launchPackage(pkg)));

        TextView name = new TextView(this);
        style(name, 11, "sans-serif", TEXT_DIM);
        name.setText(ri.loadLabel(pm));
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        column.addView(name, sized(dp(128), ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(10), 0f));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.rightMargin = dp(18);
        column.setLayoutParams(lp);
        return column;
    }

    private View iconTile(String label, Icons.Type type, Runnable onClick, int w, int h, int iconDp) {
        return smallTile(label, Icons.of(type, Color.WHITE), onClick, w, h, iconDp);
    }

    /** Small glass tile: icon on top, label below. */
    private View smallTile(String label, Drawable icon, Runnable onClick, int w, int h, int iconDp) {
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.setBackground(rounded(0x1FFFFFFF, 10));
        t.setFocusable(true);
        t.setClickable(true);
        ImageView img = new ImageView(this);
        img.setImageDrawable(icon);
        t.addView(img, new LinearLayout.LayoutParams(dp(iconDp), dp(iconDp)));
        TextView name = new TextView(this);
        style(name, 9, "sans-serif", Color.WHITE);
        name.setText(label);
        name.setSingleLine(true);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        t.addView(name, sized(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 0, dp(5), 0f));

        FrameLayout holder = FocusFx.wrap(t, w, h, dp(10), 1.12f, true, onClick);
        ((LinearLayout.LayoutParams) holder.getLayoutParams()).leftMargin = dp(8);
        return holder;
    }

    private View tab(TextView t, String label, Icons.Type icon, Runnable onClick) {
        style(t, 14, "sans-serif-medium", TEXT_DIM);
        t.setText(label);
        Drawable d = Icons.of(icon, Color.WHITE);
        d.setBounds(0, 0, dp(18), dp(18));
        t.setCompoundDrawables(d, null, null, null);
        t.setCompoundDrawablePadding(dp(8));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(dp(14), dp(8), dp(14), dp(8));
        t.setFocusable(true);
        t.setClickable(true);
        FrameLayout holder = FocusFx.wrap(t, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(19), 1.06f, true, onClick);
        ((LinearLayout.LayoutParams) holder.getLayoutParams()).rightMargin = dp(6);
        return holder;
    }

    private void setTabSelected(TextView t, boolean selected) {
        t.setTextColor(selected ? Color.WHITE : TEXT_DIM);
        t.setBackground(selected ? underline() : null);
    }

    private void focusFeatured() {
        if (featuredRow.getChildCount() > 0) {
            // FocusFx holder -> card
            View first = ((ViewGroup) featuredRow.getChildAt(0)).getChildAt(0);
            first.post(first::requestFocus);
        }
    }

    // ---------------------------------------------------------------- launching

    /**
     * Loads the wallpaper with the top/bottom shading painted in once, so each frame
     * draws one opaque image instead of the image plus two translucent layers.
     */
    private Bitmap loadWallpaper() {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inPreferredConfig = Bitmap.Config.ARGB_8888; // full colour: the glow gradients band in 565
        o.inMutable = true;
        Bitmap bmp;
        try (InputStream in = getAssets().open("wallpaper.jpg")) {
            bmp = BitmapFactory.decodeStream(in, null, o);
        } catch (IOException e) {
            return null;
        }
        if (bmp == null) return null;
        int w = bmp.getWidth(), h = bmp.getHeight();
        Canvas c = new Canvas(bmp);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 0, h * 0.22f, 0x99000000, 0x00000000, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h * 0.22f, p);
        p.setShader(new LinearGradient(0, h * 0.45f, 0, h, 0x00000000, 0xCC02040A, Shader.TileMode.CLAMP));
        c.drawRect(0, h * 0.45f, w, h, p);
        return bmp;
    }

    private Bitmap loadAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            return BitmapFactory.decodeStream(in);
        } catch (IOException e) {
            return null;
        }
    }

    private void airplayHint() {
        String name = Settings.Global.getString(getContentResolver(), "device_name");
        Toast.makeText(this, "AirPlay is on. On your iPhone: Control Center → Screen Mirroring → "
                + (name != null ? name : "this TV"), Toast.LENGTH_LONG).show();
    }

    private Intent launchIntent(String pkg) {
        PackageManager pm = getPackageManager();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        return i != null ? i : pm.getLaunchIntentForPackage(pkg);
    }

    private void launchPackage(String pkg) {
        Intent i = launchIntent(pkg);
        if (i == null) {
            Toast.makeText(this, "Can't open that app", Toast.LENGTH_SHORT).show();
            return;
        }
        start(i);
    }

    private void startAction(String action) {
        start(new Intent(action));
    }

    private void start(Intent i) {
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Can't open that", Toast.LENGTH_SHORT).show();
        }
    }

    // ---------------------------------------------------------------- helpers

    private TextView sectionLabel(String text) {
        TextView t = new TextView(this);
        style(t, 14, "sans-serif-medium", Color.WHITE);
        t.setText(text);
        return t;
    }

    private LinearLayout row(int topPad) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setClipChildren(false);
        r.setClipToPadding(false);
        r.setPadding(dp(2), topPad, dp(16), dp(2));
        return r;
    }

    private HorizontalScrollView scroller(LinearLayout row) {
        HorizontalScrollView s = new HorizontalScrollView(this);
        s.setHorizontalScrollBarEnabled(false);
        s.setClipChildren(false);
        s.setClipToPadding(false);
        s.setOverScrollMode(View.OVER_SCROLL_NEVER);
        s.setSmoothScrollingEnabled(true);
        s.setPadding(0, 0, 0, dp(8));
        s.addView(row);
        return s;
    }

    private int dp(float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private void style(TextView t, float sp, String family, int color) {
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(Typeface.create(family, Typeface.NORMAL));
        t.setTextColor(color);
    }

    private GradientDrawable glass() {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(14));
        d.setColor(GLASS);
        d.setStroke(dp(1), 0x26FFFFFF);
        return d;
    }

    private GradientDrawable rounded(int fill, int radiusDp) {
        GradientDrawable d = new GradientDrawable();
        d.setCornerRadius(dp(radiusDp));
        d.setColor(fill);
        return d;
    }

    private Drawable underline() {
        GradientDrawable line = new GradientDrawable();
        line.setColor(ACCENT);
        LayerDrawable d = new LayerDrawable(new Drawable[]{line});
        d.setLayerGravity(0, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL);
        d.setLayerHeight(0, dp(2));
        d.setLayerInsetLeft(0, dp(14));
        d.setLayerInsetRight(0, dp(14));
        return d;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    private static LinearLayout.LayoutParams sized(int w, int h, int leftMargin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h);
        lp.leftMargin = leftMargin;
        return lp;
    }

    private static LinearLayout.LayoutParams sized(int w, int h, int leftMargin, int topMargin, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(w, h, weight);
        lp.leftMargin = leftMargin;
        lp.topMargin = topMargin;
        return lp;
    }
}
