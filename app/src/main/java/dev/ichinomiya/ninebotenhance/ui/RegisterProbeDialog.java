package dev.ichinomiya.ninebotenhance.ui;

import android.app.*;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.*;
import dev.ichinomiya.ninebotenhance.client.FrameClient;
import dev.ichinomiya.ninebotenhance.core.RegisterProbe;
import java.util.*;

/** Which registers the debug probe polls and draws: one check box per candidate with select-all / select-none shortcuts. */
public final class RegisterProbeDialog {
    public static void show(Activity activity,View reference,FrameClient frames){
        MirrorUi theme=new MirrorUi(activity,reference);
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        int pad=MirrorUi.dp(activity,20),gap=MirrorUi.dp(activity,8);content.setPadding(pad,gap,pad,0);
        LinearLayout shortcuts=new LinearLayout(activity);
        Button all=new Button(activity);all.setText("全选");theme.button(all,null);
        Button none=new Button(activity);none.setText("全不选");theme.button(none,null);
        LinearLayout.LayoutParams first=new LinearLayout.LayoutParams(0,-2,1);first.rightMargin=gap;
        shortcuts.addView(all,first);shortcuts.addView(none,new LinearLayout.LayoutParams(0,-2,1));
        content.addView(shortcuts,new LinearLayout.LayoutParams(-1,-2));
        content.addView(caption(activity,theme,"全索引扫描（注入 0–255 的读取指令，一个一个读，慢）"));
        Set<String> rawSelected=frames.probeRawModules();CheckBox[] rawBoxes=new CheckBox[RegisterProbe.RAW_MODULES.length];
        for(int i=0;i<rawBoxes.length;i++){
            CheckBox box=new CheckBox(activity);rawBoxes[i]=box;box.setText(RegisterProbe.RAW_MODULES[i]+" 模块 0–255");box.setTextColor(theme.text);box.setTextSize(14);
            box.setButtonTintList(ColorStateList.valueOf(theme.accent));box.setPadding(0,MirrorUi.dp(activity,4),0,MirrorUi.dp(activity,4));
            box.setChecked(rawSelected.contains(RegisterProbe.RAW_MODULES[i]));content.addView(box,new LinearLayout.LayoutParams(-1,-2));
        }
        content.addView(caption(activity,theme,"已命名的寄存器"));
        Set<String> selected=frames.probeSelection();
        CheckBox[] boxes=new CheckBox[RegisterProbe.CANDIDATES.length];
        for(int i=0;i<boxes.length;i++){
            CheckBox box=new CheckBox(activity);boxes[i]=box;box.setText(RegisterProbe.CANDIDATES[i]);box.setTextColor(theme.text);box.setTextSize(14);
            box.setButtonTintList(ColorStateList.valueOf(theme.accent));box.setPadding(0,MirrorUi.dp(activity,4),0,MirrorUi.dp(activity,4));
            box.setChecked(selected.contains(RegisterProbe.CANDIDATES[i]));content.addView(box,new LinearLayout.LayoutParams(-1,-2));
        }
        all.setOnClickListener(v->{for(CheckBox box:boxes)box.setChecked(true);});
        none.setOnClickListener(v->{for(CheckBox box:boxes)box.setChecked(false);});
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        TextView title=new TextView(activity);title.setText("探测的寄存器");title.setTextSize(20);title.setTextColor(theme.text);title.setPadding(pad,pad,pad,pad/2);
        AlertDialog dialog=new AlertDialog.Builder(activity).setCustomTitle(title).setView(scroll).setNegativeButton("关闭",null)
                .setPositiveButton("保存",(d,w)->{
                    Set<String> chosen=new LinkedHashSet<>();
                    for(int i=0;i<boxes.length;i++)if(boxes[i].isChecked())chosen.add(RegisterProbe.CANDIDATES[i]);
                    frames.saveProbeSelection(chosen);frames.report("DEBUG probe registers "+chosen.size()+"/"+boxes.length);
                    Set<String> rawChosen=new LinkedHashSet<>();
                    for(int i=0;i<rawBoxes.length;i++)if(rawBoxes[i].isChecked())rawChosen.add(RegisterProbe.RAW_MODULES[i]);
                    frames.saveProbeRawModules(rawChosen);frames.report("DEBUG probe raw modules "+rawChosen);
                }).create();
        dialog.show();dialog.getWindow().setBackgroundDrawable(theme.background(activity,theme.surface,22,false));
        dialog.getButton(-1).setTextColor(theme.accent);dialog.getButton(-2).setTextColor(theme.accent);
    }
    private static TextView caption(Activity activity,MirrorUi theme,String text){TextView v=new TextView(activity);v.setText(text);v.setTextColor(theme.secondary);v.setTextSize(13);v.setPadding(0,MirrorUi.dp(activity,10),0,MirrorUi.dp(activity,2));return v;}
    private RegisterProbeDialog(){}
}
