package dev.ichinomiya.ninebotenhance.navi;

import android.os.Bundle;
import android.os.SystemClock;
import dev.ichinomiya.ninebotenhance.core.NaviDestination;
import dev.ichinomiya.ninebotenhance.core.NaviResume;
import dev.ichinomiya.ninebotenhance.core.NaviUpdate;
import dev.ichinomiya.ninebotenhance.diagnostics.Diagnostics;

/** Latest turn-by-turn state received from a navigation app, held in the module service for the Ninebot-side sender to poll. */
public final class NaviHub {
    private static final NaviHub INSTANCE=new NaviHub();
    public static NaviHub get(){return INSTANCE;}
    private NaviUpdate latest;private NaviDestination destination;private long published;private long lastLog;private String lastSource="";
    public void publish(Bundle bundle){publish(NaviUpdates.fromBundle(bundle));}
    public synchronized void publish(NaviUpdate update){
        if(update==null)return;
        long now=SystemClock.elapsedRealtime();latest=update.receivedAt(now);published++;
        if(!lastSource.equals(update.source())||now-lastLog>=30000){lastSource=update.source();lastLog=now;Diagnostics.add("NAVI hub "+latest.describe()+" (#"+published+")");}
    }
    public synchronized Bundle snapshot(){
        NaviUpdate update=latest;
        return update==null?new Bundle():NaviUpdates.toBundle(update);
    }
    public synchronized void publishDestination(NaviDestination value){
        if(value==null||!value.valid())return;
        destination=value.receivedAt(SystemClock.elapsedRealtime());Diagnostics.add("NAVI hub destination "+destination.describe());
    }
    /**
     * AMap's route-plan URI for the navigation that was live moments ago, for the helper process to send once the app has been
     * relaunched on the virtual display; null when nothing was being navigated or the app is not AMap.
     */
    public synchronized String resumeUri(String pkg){
        if(!NaviApps.AMAP.equals(pkg))return null;
        return NaviResume.amapUri(destination,latest,SystemClock.elapsedRealtime());
    }
    public synchronized void clear(){latest=null;destination=null;}
    private NaviHub(){}
}
