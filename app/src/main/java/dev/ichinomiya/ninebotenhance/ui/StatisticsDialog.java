package dev.ichinomiya.ninebotenhance.ui;

import android.app.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import dev.ichinomiya.ninebotenhance.client.FrameClient;
import dev.ichinomiya.ninebotenhance.diagnostics.StreamStats;
import java.util.Locale;

public final class StatisticsDialog {
    public static void show(Activity activity,FrameClient frames,View reference) {
        MirrorUi theme=new MirrorUi(activity,reference); TextView body=new TextView(activity);
        body.setTextColor(theme.text);body.setTextSize(15);body.setTextIsSelectable(true);
        int pad=MirrorUi.dp(activity,20);body.setPadding(pad,pad/2,pad,pad);body.setLineSpacing(MirrorUi.dp(activity,5),1);
        ScrollView scroll=new ScrollView(activity);scroll.setBackgroundColor(theme.surface);scroll.addView(body);
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("投屏统计").setView(scroll).setPositiveButton("关闭",null).create();
        dialog.show();dialog.getWindow().setBackgroundDrawable(theme.background(activity,theme.surface,22,false));dialog.getButton(-1).setTextColor(theme.accent);
        Handler handler=new Handler(Looper.getMainLooper());Runnable tick=new Runnable(){@Override public void run(){
            if(!dialog.isShowing())return;body.setText("原会话编码配置\n"+frames.encoding().displaySummary()+"\n\n"+format(frames.statistics()));handler.postDelayed(this,1000);
        }};dialog.setOnDismissListener(v->handler.removeCallbacks(tick));tick.run();
    }
    private static String format(StreamStats.Snapshot s) {
        if(s==null)return "尚未开始投屏\n\n启动本地虚拟屏或车辆投屏后，这里会显示本次会话统计。";
        String encoded=s.encoderSource().isEmpty()?"尚未命中编码接口":"编码帧数  "+s.encoded()+" 帧\n编码帧率  "+decimal(s.encodeFps())+" FPS\n编码码率  "+rate(s.encodeBps())+"\n编码数据  "+bytes(s.encodedBytes());
        String sent=s.senderSource().isEmpty()?"尚未命中 RTP 发送接口\n本地预览不向车辆发送数据":s.sentFrames()+" 帧（RTP 结束标记）\n发送提交  "+s.packets()+" 包\n提交数据  "+bytes(s.sentBytes())+"\n提交码率  "+rate(s.sendBps())+"\n提交速率  "+bytes(s.sendBps()/8)+"/s";
        if(!s.submitSource().isEmpty())sent="发送帧  "+s.submittedFrames()+" 帧\n发送帧数据  "+bytes(s.submittedBytes())+"\n发送帧码率  "+rate(s.submitBps())+"\n"+sent;
        String link="队列丢帧  "+s.dropped()+" 帧\n丢包报告  "+s.lossReports()+" 次\n接收报告丢包  "+decimal(s.lossPercent())+"% / "+s.cumulativeLost()+" 包"
                +(s.requestedBitrate()>0?"\n仪表请求码率  "+rate(s.requestedBitrate()):"")+(s.requestedFps()>0?"\n仪表请求帧率  "+s.requestedFps()+" FPS":"");
        return (s.stopped()?"最近一次投屏已结束":"本次投屏正在运行")+"\n持续时间  "+s.durationMs()/60000+" 分 "+s.durationMs()/1000%60+" 秒\n\n"
                +"画面采集\n采集帧数  "+s.captured()+" 帧\n采集帧率  "+decimal(s.captureFps())+" FPS\n编码器供帧  "+s.replaced()+" 次\n\n"
                +"编码输出\n"+encoded+"\n\nRTP 发送提交\n"+sent+"\n\n链路反馈\n"+link+"\n\n"
                +"速率按最近约 2 秒计算，新投屏重新计数。发送统计是九号提交给发送器的数据，不能确认仪表已收到，也不含蓝牙链路额外开销。";
    }
    private static String decimal(double value){return String.format(Locale.ROOT,"%.1f",value);}
    private static String rate(double bits){return bits>=1000000?decimal(bits/1000000)+" Mbps":decimal(bits/1000)+" kbps";}
    private static String bytes(double value){return value>=1048576?decimal(value/1048576)+" MiB":decimal(value/1024)+" KiB";}
    private StatisticsDialog(){}
}
