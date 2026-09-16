package dev.ichinomiya.ninebotenhance.notification;

import android.app.Notification;
import android.app.Person;
import android.os.Bundle;
import android.os.Parcelable;
import dev.ichinomiya.ninebotenhance.core.NotificationDeduplicator.Message;
import java.util.*;

/** Parse bounded display text and stable message identity; never read historic-message bundles. */
public final class NotificationContent {
    public record Content(String title,String text,long timestamp,List<Message> messages) {}
    public static Content read(Notification notification){
        if(notification==null||(notification.flags&Notification.FLAG_GROUP_SUMMARY)!=0)return null;
        Bundle extras=notification.extras;if(extras==null)return null;
        String title=clean(extras.getCharSequence(Notification.EXTRA_TITLE),120);
        Parcelable[] raw=extras.getParcelableArray(Notification.EXTRA_MESSAGES);
        if(raw!=null&&raw.length>0){
            ArrayList<Message> messages=new ArrayList<>();
            // Android retains at most 25 messages, but cap untrusted producers as well.
            if(raw.length>64)raw=Arrays.copyOfRange(raw,raw.length-64,raw.length);
            for(Notification.MessagingStyle.Message item:Notification.MessagingStyle.Message.getMessagesFromBundleArray(raw)){
                String body=clean(item.getText(),240);if(body.isEmpty())continue;
                Person person=item.getSenderPerson();
                String sender=person==null?clean(item.getSender(),120):clean(person.getKey(),256);
                if(sender.isEmpty()&&person!=null)sender=clean(person.getName(),120);
                messages.add(new Message(item.getTimestamp(),sender,body));
            }
            if(!messages.isEmpty()){
                String conversation=clean(extras.getCharSequence(Notification.EXTRA_CONVERSATION_TITLE),120);
                if(!conversation.isEmpty())title=conversation;
                if(title.isEmpty())title=clean(extras.getCharSequence(Notification.EXTRA_TITLE_BIG),120);
                return new Content(title,"",notification.when,List.copyOf(messages));
            }
        }
        CharSequence body=extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if(body==null||body.length()==0)body=extras.getCharSequence(Notification.EXTRA_TEXT);
        if(body==null){CharSequence[] lines=extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);if(lines!=null&&lines.length>0)body=lines[lines.length-1];}
        String text=clean(body,240);if(title.isEmpty()&&text.isEmpty())return null;
        return new Content(title,text,notification.when,List.of());
    }
    private static String clean(CharSequence text,int max){
        if(text==null)return "";String s=text.toString().replaceAll("[\\r\\n\\t]+"," ").trim();
        int end=Math.min(s.length(),max);if(end>0&&end<s.length()&&Character.isHighSurrogate(s.charAt(end-1)))end--;
        return s.substring(0,end);
    }
    private NotificationContent(){}
}
