package dev.ichinomiya.ninebotenhance.ui;

import android.app.*;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.*;
import dev.ichinomiya.ninebotenhance.client.FrameClient;
import dev.ichinomiya.ninebotenhance.core.EncoderOverride;

/** Encoder overrides and the preview statistics switch. Labels only, no explanatory copy. */
public final class EncoderOverrideDialog {
    private static final int STEP=EncoderOverride.BITRATE_STEP_KBPS;
    public static void show(Activity activity,FrameClient frames,View reference){
        MirrorUi theme=new MirrorUi(activity,reference);EncoderOverride current=frames.encoderOverride();
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        int pad=MirrorUi.dp(activity,20),gap=MirrorUi.dp(activity,8);content.setPadding(pad,gap,pad,gap);
        CheckBox preview=check(activity,theme,content,"预览统计",current.previewStats());
        CheckBox bitrate=check(activity,theme,content,"覆盖码率",current.overridesBitrate());
        LinearLayout bitrateBlock=block(activity,content,current.overridesBitrate());
        SeekBar bitrateBar=WidgetOptionsDialog.slider(activity,theme,bitrateBlock,"码率",EncoderOverride.MIN_BITRATE_KBPS/STEP,EncoderOverride.MAX_BITRATE_KBPS/STEP,
                (current.overridesBitrate()?current.bitrateKbps():EncoderOverride.DEFAULT_BITRATE_KBPS)/STEP,v->EncoderOverride.describeBitrate(v*STEP));
        CheckBox fps=check(activity,theme,content,"覆盖帧率",current.overridesFps());
        LinearLayout fpsBlock=block(activity,content,current.overridesFps());
        SeekBar fpsBar=WidgetOptionsDialog.slider(activity,theme,fpsBlock,"帧率",EncoderOverride.MIN_FPS,EncoderOverride.MAX_FPS,
                current.overridesFps()?current.fps():EncoderOverride.DEFAULT_FPS,v->v+" fps");
        bitrate.setOnCheckedChangeListener((b,checked)->bitrateBlock.setVisibility(checked?View.VISIBLE:View.GONE));
        fps.setOnCheckedChangeListener((b,checked)->fpsBlock.setVisibility(checked?View.VISIBLE:View.GONE));
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        TextView title=new TextView(activity);title.setText("编码覆盖");title.setTextSize(20);title.setTextColor(theme.text);title.setPadding(pad,pad,pad,pad/2);
        AlertDialog dialog=new AlertDialog.Builder(activity).setCustomTitle(title).setView(scroll).setNegativeButton("关闭",null)
                .setPositiveButton("保存",(d,w)->frames.saveEncoderOverride(new EncoderOverride(bitrate.isChecked()?bitrateBar.getProgress()*STEP:0,fps.isChecked()?fpsBar.getProgress():0,preview.isChecked()))).create();
        dialog.show();dialog.getWindow().setBackgroundDrawable(theme.background(activity,theme.surface,22,false));
        dialog.getButton(-1).setTextColor(theme.accent);dialog.getButton(-2).setTextColor(theme.accent);
    }
    private static CheckBox check(Activity activity,MirrorUi theme,LinearLayout parent,String label,boolean checked){
        CheckBox box=new CheckBox(activity);box.setText(label);box.setTextColor(theme.text);box.setTextSize(15);
        box.setButtonTintList(ColorStateList.valueOf(theme.accent));box.setPadding(0,MirrorUi.dp(activity,8),0,MirrorUi.dp(activity,8));box.setChecked(checked);
        parent.addView(box,new LinearLayout.LayoutParams(-1,-2));return box;
    }
    private static LinearLayout block(Activity activity,LinearLayout parent,boolean visible){
        LinearLayout block=new LinearLayout(activity);block.setOrientation(LinearLayout.VERTICAL);block.setVisibility(visible?View.VISIBLE:View.GONE);
        parent.addView(block,new LinearLayout.LayoutParams(-1,-2));return block;
    }
    private EncoderOverrideDialog(){}
}
