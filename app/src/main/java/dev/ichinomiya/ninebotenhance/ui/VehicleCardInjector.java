package dev.ichinomiya.ninebotenhance.ui;

import dev.ichinomiya.ninebotenhance.client.FrameClient;
import dev.ichinomiya.ninebotenhance.ui.DirectCastController;
import dev.ichinomiya.ninebotenhance.ui.MirrorUi;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.view.*;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.ArrayDeque;

/** Exact 6.10.10 card structure recovered from layout_detail_navigation_card (res/0oW.xml). */
public final class VehicleCardInjector {
    private static final String MARKER = "dev.ichinomiya.ninebotenhance.direct-button";
    private final DirectCastController controller;
    private final FrameClient frames;
    public VehicleCardInjector(DirectCastController controller, FrameClient frames) { this.controller = controller; this.frames = frames; }
    public void scan(View root) {
        if (root == null) return;
        ArrayDeque<View> queue = new ArrayDeque<>(); queue.add(root);
        for (int count = 0; !queue.isEmpty() && count < 1400; count++) {
            View view = queue.removeFirst();
            if (view instanceof ViewGroup && name(view).equals("vMainContainer")) {
                View cruise = child((ViewGroup)view, "ivCruise");
                View history = child((ViewGroup)view, "layoutHistory");
                View navigation = child((ViewGroup)view, "layoutNavigation");
                if (cruise != null && history != null && navigation != null) {
                    install((ViewGroup)view, history, cruise); continue;
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup group = (ViewGroup)view;
                for (int i = 0; i < group.getChildCount(); i++) queue.addLast(group.getChildAt(i));
            }
        }
    }
    private void install(ViewGroup card, View history, View cruise) {
        for (int i = 0; i < card.getChildCount(); i++) {
            View child = card.getChildAt(i);
            if (MARKER.equals(child.getTag()) && child instanceof LinearLayout) {
                controller.decorate((Button)((LinearLayout)child).getChildAt(0), card); return;
            }
        }
        if (!card.getClass().getName().equals("androidx.constraintlayout.widget.ConstraintLayout")) return;
        Activity activity = activity(card.getContext());
        if (activity == null || activity.isFinishing()) return;
        ViewGroup.LayoutParams original = history.getLayoutParams();
        LinearLayout row = null;
        try {
            Class<?> params = original.getClass();
            if (!params.getName().equals("androidx.constraintlayout.widget.ConstraintLayout$LayoutParams")) return;
            // Preserve the original params object so a failed insertion has a complete rollback.
            java.lang.reflect.Constructor<?> copy;
            try { copy = params.getConstructor(params); }
            catch (NoSuchMethodException e) { copy = params.getConstructor(ViewGroup.LayoutParams.class); }
            ViewGroup.LayoutParams historyParams = (ViewGroup.LayoutParams)copy.newInstance(original);
            if (params.getField("bottomToBottom").getInt(original) != 0) return;
            params.getField("bottomToBottom").setInt(historyParams, -1);
            ViewGroup.MarginLayoutParams buttonParams = (ViewGroup.MarginLayoutParams)params.getConstructor(int.class, int.class).newInstance(0, -2);
            set(params, buttonParams, "startToStart", 0); set(params, buttonParams, "endToEnd", 0);
            set(params, buttonParams, "topToBottom", history.getId()); set(params, buttonParams, "bottomToBottom", 0);
            buttonParams.topMargin = dp(card, 12);
            row = new LinearLayout(card.getContext()); row.setOrientation(LinearLayout.HORIZONTAL);
            row.setId(View.generateViewId()); row.setTag(MARKER);
            MirrorUi theme = new MirrorUi(card.getContext(), card);
            Button button = new Button(card.getContext()); button.setId(View.generateViewId());
            theme.button(button, "cast"); button.setTextSize(15);
            button.setOnClickListener(v -> controller.click(activity(card.getContext()), card));
            button.setOnLongClickListener(v -> { controller.entryDetails(activity(card.getContext()), card); return true; });
            row.addView(button, new LinearLayout.LayoutParams(0, dp(card, 50), 1));
            Button settings = new Button(card.getContext()); settings.setText("设置"); settings.setAllCaps(false);
            theme.button(settings, "settings");
            settings.setOnClickListener(v -> controller.settings(activity(card.getContext()), card));
            settings.setOnLongClickListener(v -> { controller.entryDetails(activity(card.getContext()), card); return true; });
            LinearLayout.LayoutParams settingParams = new LinearLayout.LayoutParams(dp(card, 100), dp(card, 50)); settingParams.setMarginStart(dp(card, 10));
            row.addView(settings, settingParams);
            history.setLayoutParams(historyParams); card.addView(row, buttonParams);
            controller.decorate(button, card);
            frames.report("DIRECT UI installed layout_detail_navigation_card below layoutHistory; " + entryInfo(card));
        } catch (ReflectiveOperationException | RuntimeException e) {
            if (row != null && row.getParent() == card) card.removeView(row);
            history.setLayoutParams(original);
            frames.report("DIRECT UI insertion failed " + e.getClass().getSimpleName());
        }
    }
    private static void set(Class<?> type, Object value, String name, int number) throws ReflectiveOperationException { type.getField(name).setInt(value, number); }
    public static View child(ViewGroup group, String name) {
        for (int i = 0; i < group.getChildCount(); i++) if (name(group.getChildAt(i)).equals(name)) return group.getChildAt(i);
        return null;
    }
    public static String name(View view) {
        if (view.getId() == View.NO_ID) return "";
        try { return view.getResources().getResourceEntryName(view.getId()); } catch (RuntimeException e) { return ""; }
    }
    public static Activity activity(Context context) {
        for (int i = 0; context != null && i < 12; i++) {
            if (context instanceof Activity) return (Activity)context;
            if (!(context instanceof ContextWrapper)) break;
            Context next = ((ContextWrapper)context).getBaseContext(); if (next == context) break; context = next;
        }
        return null;
    }
    public static View cruise(View card) {
        return card instanceof ViewGroup ? child((ViewGroup)card, "ivCruise") : null;
    }
    public static String entryInfo(View card) {
        View cruise = cruise(card);
        return "cardAttached=" + (card != null && card.isAttachedToWindow())
                + " cardShown=" + (card != null && card.isShown())
                + " cruiseFound=" + (cruise != null)
                + (cruise == null ? "" : " cruiseAttached=" + cruise.isAttachedToWindow()
                + " cruiseVisibility=" + cruise.getVisibility() + " cruiseShown=" + cruise.isShown()
                + " cruiseEnabled=" + cruise.isEnabled() + " cruiseListener=" + cruise.hasOnClickListeners());
    }
    private static int dp(View view, int size) { return Math.round(size * view.getResources().getDisplayMetrics().density); }
}
