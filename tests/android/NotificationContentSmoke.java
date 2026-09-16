import android.app.Notification;
import android.app.Person;
import android.os.Bundle;
import dev.ichinomiya.ninebotenhance.notification.NotificationContent;
import dev.ichinomiya.ninebotenhance.core.NotificationDeduplicator;

/** Exercises Android's real MessagingStyle serialization with synthetic Telegram-like updates. */
public final class NotificationContentSmoke {
    private static int checks;
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);checks++;}
    private static Notification notification(Notification.MessagingStyle.Message... messages) throws Exception {
        Notification n=new Notification();n.extras=new Bundle();n.when=1000;
        // Use Android's message serializer without constructing a UI-bound Notification.Builder.
        var toBundle=Notification.MessagingStyle.Message.class.getDeclaredMethod("toBundle");toBundle.setAccessible(true);
        Bundle[] serialized=new Bundle[messages.length];for(int i=0;i<messages.length;i++)serialized[i]=(Bundle)toBundle.invoke(messages[i]);
        n.extras.putParcelableArray(Notification.EXTRA_MESSAGES,serialized);
        n.extras.putParcelableArray(Notification.EXTRA_HISTORIC_MESSAGES,new Bundle[]{(Bundle)toBundle.invoke(new Notification.MessagingStyle.Message("Historic message",100,(Person)null))});
        n.extras.putCharSequence(Notification.EXTRA_CONVERSATION_TITLE,"Test chat");
        n.extras.putCharSequence(Notification.EXTRA_TITLE,"Changing system summary");
        n.extras.putCharSequence(Notification.EXTRA_BIG_TEXT,"Old message plus new message summary");
        return n;
    }
    public static void run() throws Exception {
        Person alice=new Person.Builder().setName("Alice").setKey("alice").build();
        var first=new Notification.MessagingStyle.Message("First message",1000,alice);
        var second=new Notification.MessagingStyle.Message("Second message",2000,alice);
        var content=NotificationContent.read(notification(first));
        check(content.messages().size()==1,"historic messages must not be imported");
        check(content.messages().get(0).timestamp()==1000,"message timestamp must survive notification refreshes");
        check(content.messages().get(0).sender().equals("alice"),"sender identity must be stable");
        check(content.title().equals("Test chat"),"conversation title should not use a changing summary title");
        var dedup=new NotificationDeduplicator();
        check(dedup.acceptMessages("chatA",content.messages()).size()==1,"first message should be displayed");
        var update=NotificationContent.read(notification(first,second));
        var added=dedup.acceptMessages("chatA",update.messages());
        check(added.size()==1&&added.get(0).text().equals("Second message"),"update must add only the new message, not the old big-text summary");
        check(dedup.acceptMessages("chatA",NotificationContent.read(notification(first)).messages()).isEmpty(),"reposting another conversation's old notification must not replay it");
        check(dedup.acceptMessages("chatA",update.messages()).isEmpty(),"repeated Android snapshot should add nothing");
        var repeat=NotificationContent.read(notification(second,second));
        check(dedup.acceptMessages("chatA",repeat.messages()).size()==1,"identical same-second messages must remain independent");
        check(dedup.acceptMessages("chatA",repeat.messages()).isEmpty(),"identical-message snapshot must not replay");
        Notification summary=notification(first);summary.flags|=Notification.FLAG_GROUP_SUMMARY;
        check(NotificationContent.read(summary)==null,"group summary should be excluded");
        Notification plain=new Notification();plain.when=1234;plain.extras=new Bundle();
        plain.extras.putCharSequence(Notification.EXTRA_TITLE,"Plain");plain.extras.putCharSequence(Notification.EXTRA_TEXT,"Message");
        var fallback=NotificationContent.read(plain);
        check(fallback.messages().isEmpty()&&fallback.timestamp()==1234,"plain notifications should use their event time");
        check(dedup.accept("plain",fallback.timestamp(),fallback.title(),fallback.text())&&!dedup.accept("plain",fallback.timestamp(),fallback.title(),fallback.text()),"plain re-publish should not create a duplicate");
        plain.when=1235;fallback=NotificationContent.read(plain);
        check(dedup.accept("plain",fallback.timestamp(),fallback.title(),fallback.text()),"genuinely new same-text plain notification should be independent");
        System.out.println("PASS: "+checks+" Android notification checks");
    }
}
