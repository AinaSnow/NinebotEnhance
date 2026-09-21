package dev.ichinomiya.ninebotenhance.navi;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import dev.ichinomiya.ninebotenhance.core.NaviDestination;
import dev.ichinomiya.ninebotenhance.core.NaviUpdate;
import dev.ichinomiya.ninebotenhance.diagnostics.Diagnostics;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * The module's foothold inside a navigation app process. It relays turn-by-turn state to the module process over a loopback TCP
 * connection to {@link NaviLoopbackServer}: ColorOS refuses a cross-app service bind or content-provider call from a navigation
 * app, but a 127.0.0.1 connection ignores package visibility. Log lines stay in this process's logcat.
 */
public final class NaviAppClient {
    private final String process;
    private final Handler worker;
    private volatile long lastPublish;private volatile NaviUpdate pendingUpdate;private long published,dropped;
    private Socket socket;private BufferedWriter out;private volatile int connectedPort=-1;private long lastConnectAttempt;
    public NaviAppClient(String process){
        this.process=process;
        HandlerThread thread=new HandlerThread("Ninebot-NaviReport");thread.start();worker=new Handler(thread.getLooper());
    }
    public void attach(Context context){/* loopback needs no context */}
    public String process(){return process;}
    /** Latest turn-by-turn state to the module process, at most a few times per second; the newest one wins. */
    public void publish(NaviUpdate update){
        if(update==null)return;pendingUpdate=update;
        long now=SystemClock.elapsedRealtime();if(now-lastPublish<400)return;lastPublish=now;
        worker.post(()->{NaviUpdate latest=pendingUpdate;if(latest==null)return;send(latest);});
    }
    /** The route destination changes rarely (new route, re-route) and goes out immediately, before the next turn-by-turn line. */
    public void publishDestination(NaviDestination destination){
        if(destination==null)return;
        worker.post(()->{
            try{if(!ensureConnected())return;out.write(NaviUpdates.toJson(destination));out.write('\n');out.flush();Diagnostics.add(process+" NAVI destination published "+destination.describe());}
            catch(Exception e){closeQuietly();Diagnostics.add(process+" NAVI destination publish failed "+e.getClass().getSimpleName());}
        });
    }
    private void send(NaviUpdate update){
        try{
            if(!ensureConnected())return;
            out.write(NaviUpdates.toJson(update));out.write('\n');out.flush();
            published++;if(published==1||published%100==0)Diagnostics.add(process+" NAVI published #"+published+" via 127.0.0.1:"+connectedPort+" "+update.describe());
        }catch(Exception e){
            closeQuietly();dropped++;
            if(dropped==1||dropped%50==0)Diagnostics.add(process+" NAVI publish failed "+e.getClass().getSimpleName()+" dropped="+dropped);
        }
    }
    private boolean ensureConnected()throws Exception{
        if(out!=null&&socket!=null&&socket.isConnected()&&!socket.isClosed())return true;
        long now=SystemClock.elapsedRealtime();if(now-lastConnectAttempt<2000)return false;lastConnectAttempt=now;
        for(int port:NaviLoopback.PORTS){
            try{
                Socket s=new Socket();s.connect(new InetSocketAddress("127.0.0.1",port),500);s.setTcpNoDelay(true);
                BufferedWriter w=new BufferedWriter(new OutputStreamWriter(s.getOutputStream(),NaviLoopback.CHARSET));
                w.write(NaviLoopback.TOKEN);w.write('\n');w.flush();
                socket=s;out=w;connectedPort=port;Diagnostics.add(process+" NAVI connected 127.0.0.1:"+port);return true;
            }catch(Exception e){/* try next port */}
        }
        return false;
    }
    private void closeQuietly(){try{if(socket!=null)socket.close();}catch(Exception ignored){}socket=null;out=null;connectedPort=-1;}
    public void report(String message){Diagnostics.add(process+" "+message);}
}
