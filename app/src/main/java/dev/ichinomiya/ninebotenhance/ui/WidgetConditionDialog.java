package dev.ichinomiya.ninebotenhance.ui;

import android.app.*;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import dev.ichinomiya.ninebotenhance.client.FrameClient;
import dev.ichinomiya.ninebotenhance.core.WidgetCondition;

/** Display condition of one widget: always, for a time after a chosen change, or while chosen measurements stay inside their ranges. */
public final class WidgetConditionDialog {
    /** One checkable range: stored units and limits, slider step, display scale and unit; listed in the record's component order. */
    private record Range(int check,String label,int limitMin,int limitMax,int step,int scale,String unit){}
    private static final Range[] RANGES={
        new Range(WidgetCondition.SPEED,"速度",WidgetCondition.MIN_SPEED,WidgetCondition.MAX_SPEED,1,1,"km/h"),
        new Range(WidgetCondition.POWER,"功率",WidgetCondition.MIN_POWER,WidgetCondition.MAX_POWER,WidgetCondition.POWER_STEP,1,"W"),
        new Range(WidgetCondition.VOLTAGE,"电压",WidgetCondition.MIN_VOLTAGE,WidgetCondition.MAX_VOLTAGE,1,1,"V"),
        new Range(WidgetCondition.VOLUME,"音量",WidgetCondition.MIN_VOLUME,WidgetCondition.MAX_VOLUME,1,1,"%"),
        new Range(WidgetCondition.TYRE_FRONT_PRESSURE,"前胎压",WidgetCondition.MIN_PRESSURE,WidgetCondition.MAX_PRESSURE,1,WidgetCondition.PRESSURE_SCALE,"bar"),
        new Range(WidgetCondition.TYRE_REAR_PRESSURE,"后胎压",WidgetCondition.MIN_PRESSURE,WidgetCondition.MAX_PRESSURE,1,WidgetCondition.PRESSURE_SCALE,"bar"),
        new Range(WidgetCondition.TYRE_FRONT_TEMP,"前胎温度",WidgetCondition.MIN_TEMP,WidgetCondition.MAX_TEMP,1,1,"°C"),
        new Range(WidgetCondition.TYRE_REAR_TEMP,"后胎温度",WidgetCondition.MIN_TEMP,WidgetCondition.MAX_TEMP,1,1,"°C"),
    };
    public static void show(Activity activity,FrameClient frames,View reference,int widget,String name){
        MirrorUi theme=new MirrorUi(activity,reference);WidgetCondition c=frames.widgetSettings().condition(widget);
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        int pad=MirrorUi.dp(activity,20),gap=MirrorUi.dp(activity,8);content.setPadding(pad,gap,pad,gap);
        RadioGroup modes=new RadioGroup(activity);RadioButton[] modeButtons=new RadioButton[3];String[] modeLabels={"始终显示","条件变动时","满足条件时"};
        for(int i=0;i<3;i++){
            RadioButton b=new RadioButton(activity);b.setId(View.generateViewId());b.setText(modeLabels[i]);b.setTextColor(theme.text);b.setTextSize(15);
            b.setButtonTintList(ColorStateList.valueOf(theme.accent));b.setPadding(0,gap,0,gap);modes.addView(b);modeButtons[i]=b;
        }
        modeButtons[c.mode()].setChecked(true);content.addView(modes);
        LinearLayout change=new LinearLayout(activity);change.setOrientation(LinearLayout.VERTICAL);
        CheckBox volumeChange=check(activity,theme,"音量变动",(c.triggers()&WidgetCondition.VOLUME_CHANGE)!=0);
        CheckBox trackChange=check(activity,theme,"歌曲变更",(c.triggers()&WidgetCondition.TRACK_CHANGE)!=0);
        CheckBox playbackChange=check(activity,theme,"播放状态变更",(c.triggers()&WidgetCondition.PLAYBACK_CHANGE)!=0);
        change.addView(volumeChange);change.addView(trackChange);change.addView(playbackChange);
        SeekBar seconds=WidgetOptionsDialog.slider(activity,theme,change,"显示时长",WidgetCondition.MIN_SHOW_SECONDS,WidgetCondition.MAX_SHOW_SECONDS,c.showSeconds(),v->v+" 秒");
        content.addView(change);
        LinearLayout hold=new LinearLayout(activity);hold.setOrientation(LinearLayout.VERTICAL);
        CheckBox[] rangeChecks=new CheckBox[RANGES.length];RangeBar[] bars=new RangeBar[RANGES.length];
        for(int i=0;i<RANGES.length;i++){
            Range r=RANGES[i];int[] bounds=bounds(c,r.check());
            CheckBox box=check(activity,theme,r.label(),(c.checks()&r.check())!=0);hold.addView(box);rangeChecks[i]=box;
            LinearLayout group=new LinearLayout(activity);group.setOrientation(LinearLayout.VERTICAL);
            TextView shown=new TextView(activity);shown.setTextColor(theme.text);shown.setTextSize(13);shown.setGravity(Gravity.END);
            RangeBar bar=new RangeBar(activity,theme,r.limitMin()/r.step(),r.limitMax()/r.step(),bounds[0]/r.step(),bounds[1]/r.step());bars[i]=bar;
            Runnable describe=()->shown.setText(WidgetCondition.describe(bar.low()*r.step(),bar.high()*r.step(),r.limitMin(),r.limitMax(),r.scale(),r.unit()));
            bar.setListener((lo,hi)->describe.run());describe.run();
            group.addView(shown,new LinearLayout.LayoutParams(-1,-2));group.addView(bar,new LinearLayout.LayoutParams(-1,MirrorUi.dp(activity,44)));
            group.setVisibility(box.isChecked()?View.VISIBLE:View.GONE);box.setOnCheckedChangeListener((b,on)->group.setVisibility(on?View.VISIBLE:View.GONE));
            hold.addView(group);
        }
        CheckBox playing=check(activity,theme,"正在播放",(c.checks()&WidgetCondition.PLAYING)!=0);hold.addView(playing);
        CheckBox bmsConnected=check(activity,theme,"BMS 已连接",(c.checks()&WidgetCondition.BMS_CONNECTED)!=0);hold.addView(bmsConnected);
        content.addView(hold);
        Runnable refresh=()->{change.setVisibility(modeButtons[1].isChecked()?View.VISIBLE:View.GONE);hold.setVisibility(modeButtons[2].isChecked()?View.VISIBLE:View.GONE);};
        modes.setOnCheckedChangeListener((g,id)->refresh.run());refresh.run();
        ScrollView scroll=new ScrollView(activity);scroll.addView(content);
        TextView title=new TextView(activity);title.setText(name);title.setTextSize(20);title.setTextColor(theme.text);title.setPadding(pad,pad,pad,pad/2);
        AlertDialog dialog=new AlertDialog.Builder(activity).setCustomTitle(title).setView(scroll).setNegativeButton("关闭",null)
                .setPositiveButton("保存",(d,w)->{
                    int mode=modeButtons[1].isChecked()?WidgetCondition.ON_CHANGE:modeButtons[2].isChecked()?WidgetCondition.WHILE:WidgetCondition.ALWAYS;
                    int triggers=(volumeChange.isChecked()?WidgetCondition.VOLUME_CHANGE:0)|(trackChange.isChecked()?WidgetCondition.TRACK_CHANGE:0)|(playbackChange.isChecked()?WidgetCondition.PLAYBACK_CHANGE:0);
                    int checks=(playing.isChecked()?WidgetCondition.PLAYING:0)|(bmsConnected.isChecked()?WidgetCondition.BMS_CONNECTED:0);int[] v=new int[RANGES.length*2];
                    for(int i=0;i<RANGES.length;i++){if(rangeChecks[i].isChecked())checks|=RANGES[i].check();v[2*i]=bars[i].low()*RANGES[i].step();v[2*i+1]=bars[i].high()*RANGES[i].step();}
                    WidgetCondition next=new WidgetCondition(mode,triggers,seconds.getProgress(),checks,v[0],v[1],v[2],v[3],v[4],v[5],v[6],v[7],v[8],v[9],v[10],v[11],v[12],v[13],v[14],v[15]);
                    frames.saveWidgetSettings(frames.widgetSettings().withCondition(widget,next));
                }).create();
        dialog.show();dialog.getWindow().setBackgroundDrawable(theme.background(activity,theme.surface,22,false));
        dialog.getButton(-1).setTextColor(theme.accent);dialog.getButton(-2).setTextColor(theme.accent);
    }
    private static int[] bounds(WidgetCondition c,int check){
        return switch(check){
            case WidgetCondition.SPEED->new int[]{c.speedMin(),c.speedMax()};case WidgetCondition.POWER->new int[]{c.powerMin(),c.powerMax()};
            case WidgetCondition.VOLTAGE->new int[]{c.voltageMin(),c.voltageMax()};case WidgetCondition.VOLUME->new int[]{c.volumeMin(),c.volumeMax()};
            case WidgetCondition.TYRE_FRONT_PRESSURE->new int[]{c.frontPressureMin(),c.frontPressureMax()};case WidgetCondition.TYRE_REAR_PRESSURE->new int[]{c.rearPressureMin(),c.rearPressureMax()};
            case WidgetCondition.TYRE_FRONT_TEMP->new int[]{c.frontTempMin(),c.frontTempMax()};case WidgetCondition.TYRE_REAR_TEMP->new int[]{c.rearTempMin(),c.rearTempMax()};
            default->new int[]{0,0};
        };
    }
    private static CheckBox check(Activity activity,MirrorUi theme,String label,boolean checked){
        CheckBox b=new CheckBox(activity);b.setText(label);b.setTextColor(theme.text);b.setTextSize(15);b.setButtonTintList(ColorStateList.valueOf(theme.accent));
        b.setPadding(0,MirrorUi.dp(activity,6),0,MirrorUi.dp(activity,6));b.setChecked(checked);return b;
    }
    private WidgetConditionDialog(){}
}
