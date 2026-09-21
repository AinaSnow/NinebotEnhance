package dev.ichinomiya.ninebotenhance.ui;

import android.app.*;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.*;
import dev.ichinomiya.ninebotenhance.client.FrameClient;
import dev.ichinomiya.ninebotenhance.core.HiddenFeatures;

/** Ninebot features to force on. Labels only, no explanatory copy. */
public final class HiddenFeatureDialog {
    public static void show(Activity activity,FrameClient frames,View reference){
        MirrorUi theme=new MirrorUi(activity,reference);HiddenFeatures current=frames.hiddenFeatures();
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        int pad=MirrorUi.dp(activity,20),gap=MirrorUi.dp(activity,8);content.setPadding(pad,gap,pad,gap);
        CheckBox throttle=check(activity,theme,content,"双向转把选项",current.throttle());
        CheckBox hardkey=check(activity,theme,content,"仪表按键卡片",current.hardkey());
        CheckBox cruise=check(activity,theme,content,"原版巡航入口",current.cruise());
        TextView title=new TextView(activity);title.setText("隐藏功能");title.setTextSize(20);title.setTextColor(theme.text);title.setPadding(pad,pad,pad,pad/2);
        AlertDialog dialog=new AlertDialog.Builder(activity).setCustomTitle(title).setView(content).setNegativeButton("关闭",null)
                .setPositiveButton("保存",(d,w)->frames.saveHiddenFeatures(new HiddenFeatures(throttle.isChecked(),hardkey.isChecked(),cruise.isChecked()))).create();
        dialog.show();dialog.getWindow().setBackgroundDrawable(theme.background(activity,theme.surface,22,false));
        dialog.getButton(-1).setTextColor(theme.accent);dialog.getButton(-2).setTextColor(theme.accent);
    }
    private static CheckBox check(Activity activity,MirrorUi theme,LinearLayout parent,String label,boolean checked){
        CheckBox box=new CheckBox(activity);box.setText(label);box.setTextColor(theme.text);box.setTextSize(15);
        box.setButtonTintList(ColorStateList.valueOf(theme.accent));box.setPadding(0,MirrorUi.dp(activity,8),0,MirrorUi.dp(activity,8));box.setChecked(checked);
        parent.addView(box,new LinearLayout.LayoutParams(-1,-2));return box;
    }
    private HiddenFeatureDialog(){}
}
