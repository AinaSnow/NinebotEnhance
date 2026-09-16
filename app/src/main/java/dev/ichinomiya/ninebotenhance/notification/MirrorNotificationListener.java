package dev.ichinomiya.ninebotenhance.notification;

import android.service.notification.*;

/** No history import, no notification cancellation, and no notification content in logs. */
public final class MirrorNotificationListener extends NotificationListenerService {
    @Override public void onListenerConnected(){NotificationHub.get(this).clear();}
    @Override public void onListenerDisconnected(){NotificationHub.get(this).clear();}
    @Override public void onNotificationPosted(StatusBarNotification sbn){
        if(sbn==null)return;
        try{
            NotificationHub.get(this).post(sbn.getKey(),sbn.getPackageName(),sbn.getNotification());
        }catch(RuntimeException ignored){} // Host app extras are untrusted; skip malformed records.
    }
    @Override public void onNotificationRemoved(StatusBarNotification sbn){if(sbn!=null)NotificationHub.get(this).removed(sbn.getKey());}
}
