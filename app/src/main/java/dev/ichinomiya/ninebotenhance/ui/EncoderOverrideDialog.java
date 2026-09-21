package dev.ichinomiya.ninebotenhance.ui;

import android.app.*;
import android.content.res.ColorStateList;
import android.text.InputFilter;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import dev.ichinomiya.ninebotenhance.client.FrameClient;
import dev.ichinomiya.ninebotenhance.core.EncoderOverride;

/** Encoder overrides, the composed-frame size override and the preview statistics switch. Labels only, no explanatory copy. */
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
        // The frame override only changes the module's composed frame; the size Ninebot encodes for the vehicle stays its own.
        CheckBox frame=check(activity,theme,content,"覆盖分辨率",current.overridesFrame());
        LinearLayout frameBlock=block(activity,content,current.overridesFrame());
        RadioGroup presets=new RadioGroup(activity);presets.setOrientation(RadioGroup.VERTICAL);
        RadioButton half=radio(activity,theme,presets,"半屏仪表 "+EncoderOverride.HALF_SCREEN_WIDTH+" × "+EncoderOverride.HALF_SCREEN_HEIGHT);
        RadioButton five=radio(activity,theme,presets,"五寸仪表 "+EncoderOverride.FIVE_INCH_WIDTH+" × "+EncoderOverride.FIVE_INCH_HEIGHT);
        RadioButton custom=radio(activity,theme,presets,"自定义");
        frameBlock.addView(presets,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout customRow=new LinearLayout(activity);customRow.setGravity(Gravity.CENTER_VERTICAL);customRow.setPadding(0,gap,0,0);
        int width=current.overridesFrame()?current.frameWidth():frames.frameWidth(),height=current.overridesFrame()?current.frameHeight():frames.frameHeight();
        EditText widthField=field(activity,theme,customRow,"宽",width),heightField=field(activity,theme,customRow,"高",height);
        frameBlock.addView(customRow,new LinearLayout.LayoutParams(-1,-2));
        boolean customSelected=current.overridesFrame()&&!current.halfScreen()&&!current.fiveInch();
        (current.halfScreen()?half:current.fiveInch()?five:customSelected?custom:half).setChecked(true);
        customRow.setVisibility(customSelected?View.VISIBLE:View.GONE);
        presets.setOnCheckedChangeListener((g,id)->customRow.setVisibility(id==custom.getId()?View.VISIBLE:View.GONE));
        bitrate.setOnCheckedChangeListener((b,checked)->bitrateBlock.setVisibility(checked?View.VISIBLE:View.GONE));
        fps.setOnCheckedChangeListener((b,checked)->fpsBlock.setVisibility(checked?View.VISIBLE:View.GONE));
        frame.setOnCheckedChangeListener((b,checked)->frameBlock.setVisibility(checked?View.VISIBLE:View.GONE));
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        TextView title=new TextView(activity);title.setText("编码覆盖");title.setTextSize(20);title.setTextColor(theme.text);title.setPadding(pad,pad,pad,pad/2);
        AlertDialog dialog=new AlertDialog.Builder(activity).setCustomTitle(title).setView(scroll).setNegativeButton("关闭",null)
                .setPositiveButton("保存",(d,w)->{
                    int frameWidth=0,frameHeight=0;
                    if(frame.isChecked()){
                        if(half.isChecked()){frameWidth=EncoderOverride.HALF_SCREEN_WIDTH;frameHeight=EncoderOverride.HALF_SCREEN_HEIGHT;}
                        else if(five.isChecked()){frameWidth=EncoderOverride.FIVE_INCH_WIDTH;frameHeight=EncoderOverride.FIVE_INCH_HEIGHT;}
                        else{frameWidth=number(widthField);frameHeight=number(heightField);}
                    }
                    frames.saveEncoderOverride(new EncoderOverride(bitrate.isChecked()?bitrateBar.getProgress()*STEP:0,fps.isChecked()?fpsBar.getProgress():0,preview.isChecked(),frameWidth,frameHeight));
                }).create();
        dialog.show();dialog.getWindow().setBackgroundDrawable(theme.background(activity,theme.surface,22,false));
        dialog.getButton(-1).setTextColor(theme.accent);dialog.getButton(-2).setTextColor(theme.accent);
    }
    private static int number(EditText field){try{return Integer.parseInt(field.getText().toString().trim());}catch(NumberFormatException e){return 0;}}
    private static CheckBox check(Activity activity,MirrorUi theme,LinearLayout parent,String label,boolean checked){
        CheckBox box=new CheckBox(activity);box.setText(label);box.setTextColor(theme.text);box.setTextSize(15);
        box.setButtonTintList(ColorStateList.valueOf(theme.accent));box.setPadding(0,MirrorUi.dp(activity,8),0,MirrorUi.dp(activity,8));box.setChecked(checked);
        parent.addView(box,new LinearLayout.LayoutParams(-1,-2));return box;
    }
    private static RadioButton radio(Activity activity,MirrorUi theme,RadioGroup parent,String label){
        RadioButton button=new RadioButton(activity);button.setId(View.generateViewId());button.setText(label);button.setTextColor(theme.text);button.setTextSize(15);
        button.setButtonTintList(ColorStateList.valueOf(theme.accent));button.setPadding(0,MirrorUi.dp(activity,6),0,MirrorUi.dp(activity,6));
        parent.addView(button,new RadioGroup.LayoutParams(-1,-2));return button;
    }
    private static EditText field(Activity activity,MirrorUi theme,LinearLayout row,String caption,int value){
        TextView label=new TextView(activity);label.setText(caption);label.setTextColor(theme.secondary);label.setTextSize(13);
        LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(-2,-2);labelParams.setMarginEnd(MirrorUi.dp(activity,6));
        if(row.getChildCount()>0)labelParams.setMarginStart(MirrorUi.dp(activity,16));
        row.addView(label,labelParams);
        int pad=MirrorUi.dp(activity,10);
        EditText edit=new EditText(activity);edit.setSingleLine(true);edit.setInputType(InputType.TYPE_CLASS_NUMBER);edit.setText(String.valueOf(value));
        edit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(4)});edit.setTextColor(theme.text);edit.setTextSize(15);edit.setBackgroundTintList(null);
        edit.setBackground(theme.background(activity,theme.input,10,false));edit.setPadding(pad,pad,pad,pad);edit.setGravity(Gravity.CENTER);
        row.addView(edit,new LinearLayout.LayoutParams(0,-2,1));return edit;
    }
    private static LinearLayout block(Activity activity,LinearLayout parent,boolean visible){
        LinearLayout block=new LinearLayout(activity);block.setOrientation(LinearLayout.VERTICAL);block.setVisibility(visible?View.VISIBLE:View.GONE);
        parent.addView(block,new LinearLayout.LayoutParams(-1,-2));return block;
    }
    private EncoderOverrideDialog(){}
}
