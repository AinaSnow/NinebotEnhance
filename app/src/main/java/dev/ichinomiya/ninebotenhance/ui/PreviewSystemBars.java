package dev.ichinomiya.ninebotenhance.ui;

import android.app.Activity;
import android.content.ComponentCallbacks;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Insets;
import android.graphics.Paint;
import android.view.*;
import dev.ichinomiya.ninebotenhance.client.FrameClient;
import java.util.function.Consumer;

/** Paint real app content behind Android 15+ transparent bars; restore a borrowed Activity window. */
final class PreviewSystemBars {
    private static final int LIGHT = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
            | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
    private static final int FLAGS = WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS
            | WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS | WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION;
    private final Activity activity;
    private final Window window;
    private final ViewGroup decor;
    private final View reference;
    private final Consumer<MirrorUi> themeChanged;
    private final FrameClient frames;
    private final Backdrop backdrop;
    private final int oldFlags, oldStatus, oldNavigation, oldDivider, oldAppearance;
    private final boolean oldStatusContrast, oldNavigationContrast;
    private boolean closed;
    private final Runnable refresh = this::apply;
    private final ViewTreeObserver.OnGlobalLayoutListener layout = this::updateInsets;
    private final ViewTreeObserver.OnWindowFocusChangeListener focus = focused -> { if (focused) schedule(); };
    private final ComponentCallbacks configuration = new ComponentCallbacks() {
        @Override public void onConfigurationChanged(Configuration config) { schedule(); }
        @Override public void onLowMemory() {}
    };
    PreviewSystemBars(Activity activity, Window window, View reference, Consumer<MirrorUi> themeChanged, FrameClient frames) {
        this.activity = activity; this.window = window; this.reference = reference; this.themeChanged = themeChanged; this.frames = frames;
        decor = (ViewGroup)window.getDecorView();
        oldFlags = window.getAttributes().flags & FLAGS;
        oldStatus = window.getStatusBarColor(); oldNavigation = window.getNavigationBarColor(); oldDivider = window.getNavigationBarDividerColor();
        oldStatusContrast = window.isStatusBarContrastEnforced(); oldNavigationContrast = window.isNavigationBarContrastEnforced();
        WindowInsetsController controller = window.getInsetsController();
        oldAppearance = controller == null ? 0 : controller.getSystemBarsAppearance() & LIGHT;
        backdrop = new Backdrop(activity); decor.getOverlay().add(backdrop);
        decor.getViewTreeObserver().addOnGlobalLayoutListener(layout);
        decor.getViewTreeObserver().addOnWindowFocusChangeListener(focus);
        activity.registerComponentCallbacks(configuration);
        try { apply(); schedule(); }
        catch (RuntimeException e) { close(); throw e; }
    }
    private void schedule() { if (!closed) { decor.removeCallbacks(refresh); decor.post(refresh); } }
    private void apply() {
        if (closed) return;
        MirrorUi theme = new MirrorUi(activity, reference);
        themeChanged.accept(theme);
        window.setFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS, FLAGS);
        // API 35+ ignores the background colour setters. The overlay below supplies actual pixels.
        window.setStatusBarColor(theme.surface); window.setNavigationBarColor(theme.surface);
        window.setNavigationBarDividerColor(theme.surface);
        window.setStatusBarContrastEnforced(false); window.setNavigationBarContrastEnforced(false);
        WindowInsetsController controller = window.getInsetsController();
        if (controller != null) controller.setSystemBarsAppearance(theme.dark ? 0 : LIGHT, LIGHT);
        backdrop.color(theme.surface); updateInsets();
        frames.report("PREVIEW BARS dark=" + theme.dark + " surface=" + Integer.toHexString(theme.surface)
                + " night=" + (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                + " statusTop=" + backdrop.status.top + " navigationBottom=" + backdrop.navigation.bottom);
    }
    void updateInsets() {
        if (closed) return;
        WindowInsets insets = decor.getRootWindowInsets();
        backdrop.layout(0, 0, decor.getWidth(), decor.getHeight());
        if (insets != null) backdrop.insets(insets.getInsets(WindowInsets.Type.statusBars()), insets.getInsets(WindowInsets.Type.navigationBars()));
    }
    void resume() { schedule(); }
    void close() {
        if (closed) return;
        closed = true; decor.removeCallbacks(refresh);
        if (decor.getViewTreeObserver().isAlive()) {
            decor.getViewTreeObserver().removeOnGlobalLayoutListener(layout);
            decor.getViewTreeObserver().removeOnWindowFocusChangeListener(focus);
        }
        activity.unregisterComponentCallbacks(configuration); decor.getOverlay().remove(backdrop);
        window.setFlags(oldFlags, FLAGS);
        window.setStatusBarColor(oldStatus); window.setNavigationBarColor(oldNavigation); window.setNavigationBarDividerColor(oldDivider);
        window.setStatusBarContrastEnforced(oldStatusContrast); window.setNavigationBarContrastEnforced(oldNavigationContrast);
        WindowInsetsController controller = window.getInsetsController();
        if (controller != null) controller.setSystemBarsAppearance(oldAppearance, LIGHT);
    }
    /** Overlay views receive no input. Only the status/nav insets are painted, never the IME area. */
    private static final class Backdrop extends View {
        private final Paint paint = new Paint();
        private Insets status = Insets.NONE, navigation = Insets.NONE;
        Backdrop(Activity context) { super(context); setForceDarkAllowed(false); setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
        void color(int color) { if (paint.getColor() != color) { paint.setColor(color); invalidate(); } }
        void insets(Insets status, Insets navigation) {
            if (this.status.equals(status) && this.navigation.equals(navigation)) return;
            this.status = status; this.navigation = navigation; invalidate();
        }
        @Override protected void onDraw(Canvas canvas) { bands(canvas, status); bands(canvas, navigation); }
        private void bands(Canvas canvas, Insets bars) {
            int width = getWidth(), height = getHeight();
            canvas.drawRect(0, 0, width, bars.top, paint);
            canvas.drawRect(0, height - bars.bottom, width, height, paint);
            canvas.drawRect(0, 0, bars.left, height, paint);
            canvas.drawRect(width - bars.right, 0, width, height, paint);
        }
    }
}
