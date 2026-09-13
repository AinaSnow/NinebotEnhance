package dev.ichinomiya.ninebotenhance.diagnostics;

import java.util.*;
import java.util.function.Consumer;

/** Session-scoped, bounded metadata. Tokens captured before a target call reject late results. */
public final class EncodingDiagnostics {
    public static final class Session {
        public final String request;
        private final boolean vehicle;
        private boolean ended;
        private long lastRates;
        private String encoderSource="",senderSource="";
        private final LinkedHashMap<String, String> capture = new LinkedHashMap<>();
        private final List<Codec> codecs = new ArrayList<>();
        private Session(String request, boolean vehicle, long now) { this.request=request;this.vehicle=vehicle;lastRates=now; }
    }
    private static final class Codec {
        final Session session;
        final int id;
        final LinkedHashSet<Long> completedPts = new LinkedHashSet<>();
        EncodingFormat requested, output;
        boolean retired, buffersObserved;
        long frames, bytes, lastFrames, lastBytes;
        Codec(Session session, int id) { this.session=session;this.id=id; }
    }
    private final Consumer<String> log;
    // Keep weak tombstones across sessions so late callbacks from old codec instances cannot be adopted.
    private final WeakIdentityMap<Codec> owners = new WeakIdentityMap<>();
    private Session current;
    public EncodingDiagnostics(Consumer<String> log) { this.log=log; }
    public synchronized void begin(String request, boolean vehicle, long now) {
        if (current != null && !current.ended && current.request.equals(request)) return;
        if (current != null) current.ended=true;
        current=new Session(request,vehicle,now);
        if (vehicle) emit(current,"BEGIN requestedSize=未读取 targetFps=未读取 bitrateBps=未读取 outputSize=未读取");
    }
    public synchronized Session active() { return current != null && current.vehicle && !current.ended ? current : null; }
    private boolean accepts(Session session) { return session != null && session == current && session.vehicle && !session.ended; }
    public synchronized void capture(Session session, String source, String details) {
        if (!accepts(session) || details == null || details.equals(session.capture.get(source))) return;
        if (!session.capture.containsKey(source) && session.capture.size() >= 32) return;
        session.capture.put(source,details);
        emit(session,"CAPTURE source="+source+" raw={"+details+"}");
    }
    public synchronized boolean associate(Session session, Object codec) {
        if (!accepts(session) || codec == null) return false;
        Codec existing=owners.get(codec);
        if (existing != null) return existing.session == session;
        if (session.codecs.size() >= 16 || owners.size() >= 128) return false;
        Codec entry=new Codec(session,session.codecs.size()+1);owners.put(codec,entry);session.codecs.add(entry);return true;
    }
    public synchronized Session owner(Object codec) {
        Codec entry=owners.get(codec);return entry != null && !entry.retired && accepts(entry.session) ? entry.session : null;
    }
    public synchronized void configured(Session session,Object codec,EncodingFormat format) {
        if(!accepts(session))return;
        Codec entry=owners.get(codec);if(entry==null||entry.session!=session)return;
        entry.retired=false;entry.output=null;entry.requested=null;entry.completedPts.clear();
        format(session,codec,format,false,"MediaCodec.configure accepted");
    }
    public synchronized void retire(Session session,Object codec) {
        Codec entry=owners.get(codec);if(entry!=null&&entry.session==session)entry.retired=true;
    }
    public synchronized void format(Session session, Object codec, EncodingFormat format, boolean output, String source) {
        if (!accepts(session)) return;
        Codec entry=owners.get(codec);if(entry==null||entry.session!=session||entry.retired)return;
        if (format.equals(output?entry.output:entry.requested)) return;
        if(output)entry.output=format;else entry.requested=format;
        emit(session,(output?"OUTPUT":"CONFIG")+" codec="+entry.id+" source="+source+" "+format.describe()
                +(output?"":" targetFps="+EncodingFormat.value(format.fps())));
    }
    public synchronized void event(Session session, Object codec, String event) {
        if (!accepts(session)) return;
        Codec entry=owners.get(codec);if(entry!=null&&entry.session==session)emit(session,"CODEC codec="+entry.id+" "+event);
    }
    public synchronized void buffer(Session session, Object codec, long pts, int size, int flags) {
        if (!accepts(session)) return;
        Codec entry=owners.get(codec);if(entry==null||entry.session!=session||entry.retired)return;
        entry.buffersObserved=true;
        if(size<=0||(flags&2)!=0)return; // BUFFER_FLAG_CODEC_CONFIG
        // Partial output contributes bytes; only a complete access unit contributes a frame.
        if(entry.completedPts.contains(pts))return;
        entry.bytes+=size;
        if((flags&8)==0 && entry.completedPts.add(pts)) { // BUFFER_FLAG_PARTIAL_FRAME
            entry.frames++;
            if(entry.completedPts.size()>128)entry.completedPts.remove(entry.completedPts.iterator().next());
        }
    }
    public synchronized void tick(long now, StreamStats.Snapshot stats) {
        Session session=active();if(session==null||now-session.lastRates<10000)return;
        rates(session,now,stats,false);
    }
    public synchronized void stop(String request, long now, StreamStats.Snapshot stats) {
        if(current==null||current.ended||!current.request.equals(request))return;
        if(current.vehicle) { rates(current,now,stats,true);emit(current,"END "+summaryLine(current)); }
        current.ended=true;
    }
    private void rates(Session session,long now,StreamStats.Snapshot stats,boolean end) {
        double seconds=Math.max(.001,(now-session.lastRates)/1000.0);
        for(Codec entry:session.codecs) {
            boolean measured=entry.buffersObserved && now>session.lastRates;
            emit(session,"RATE codec="+entry.id+" final="+end+" windowMs="+(now-session.lastRates)+" frames="+entry.frames+" bytes="+entry.bytes
                    +" actualFps="+(measured?decimal((entry.frames-entry.lastFrames)/seconds):"未读取")
                    +" actualBps="+(measured?decimal((entry.bytes-entry.lastBytes)*8/seconds):"未读取"));
            entry.lastFrames=entry.frames;entry.lastBytes=entry.bytes;
        }
        if(stats!=null) {
            if(!stats.encoderSource().equals(session.encoderSource)) { session.encoderSource=stats.encoderSource();emit(session,"ENCODER_CALLBACK source="+session.encoderSource); }
            if(!stats.senderSource().equals(session.senderSource)) { session.senderSource=stats.senderSource();emit(session,"RTP_SENDER source="+session.senderSource); }
            emit(session,"STREAM final="+end+" captured="+stats.captured()+" captureFps="+decimal(stats.captureFps())
                    +" supplied="+stats.replaced()+" encodedCallbacks="+stats.encoded()+" callbackFps="+(session.encoderSource.isEmpty()?"未读取":decimal(stats.encodeFps()))
                    +" callbackBps="+(session.encoderSource.isEmpty()?"未读取":decimal(stats.encodeBps()))+" rtpFrames="+stats.sentFrames()+" rtpPackets="+stats.packets()
                    +" submittedBytes="+stats.sentBytes()+" submittedBps="+(session.senderSource.isEmpty()?"未读取":decimal(stats.sendBps())));
        }
        session.lastRates=now;
    }
    private static String decimal(double value) { return String.format(Locale.ROOT,"%.1f",value); }
    private void emit(Session session,String text) { log.accept("ENCODING session="+session.request+" "+text); }
    private String summaryLine(Session session) {
        if(session.codecs.isEmpty())return "MediaCodec=未读取 captureSources="+session.capture.size()+" targetFps=未读取";
        StringBuilder text=new StringBuilder();
        for(Codec entry:session.codecs) {
            if(text.length()>0)text.append('\n');
            text.append("codec=").append(entry.id).append(" requestedSize=").append(entry.requested==null?"未读取":entry.requested.dimensions())
                .append(" targetFps=").append(entry.requested==null?"未读取":EncodingFormat.value(entry.requested.fps()))
                .append(" bitrateBps=").append(entry.requested==null?"未读取":EncodingFormat.value(entry.requested.bitrate()))
                .append(" outputSize=").append(entry.output==null?"未读取":entry.output.dimensions());
        }
        return text.toString();
    }
    public synchronized String summary() {
        if(current==null)return "尚未开始投屏";
        if(!current.vehicle)return "本地模拟，没有车辆编码会话";
        return "session="+current.request+(current.ended?" 已结束":"")+"\n"+summaryLine(current);
    }
    public synchronized String details() {
        StringBuilder text=new StringBuilder(summary());
        if(current!=null&&current.vehicle) {
            for(Map.Entry<String,String> entry:current.capture.entrySet())text.append("\nCAPTURE source=").append(entry.getKey()).append(" raw={").append(entry.getValue()).append('}');
            for(Codec codec:current.codecs) {
                text.append("\nCONFIG codec=").append(codec.id).append(' ').append(codec.requested==null?"未读取":codec.requested.describe());
                text.append("\nOUTPUT codec=").append(codec.id).append(' ').append(codec.output==null?"未读取":codec.output.describe());
                text.append("\nOUTPUT_TOTAL codec=").append(codec.id).append(" observed=").append(codec.buffersObserved).append(" frames=").append(codec.frames).append(" bytes=").append(codec.bytes);
            }
        }
        return text.toString();
    }
    public synchronized String displaySummary() {
        if(current==null)return "尚未开始投屏";
        if(!current.vehicle)return "本地模拟，没有车辆编码会话";
        if(current.codecs.isEmpty())return "尚未读取编码器参数\n原始捕获配置可在完整日志中查看";
        StringBuilder text=new StringBuilder();
        for(Codec entry:current.codecs) {
            if(text.length()>0)text.append("\n\n");
            text.append("编码器 ").append(entry.id)
                .append("\n请求分辨率  ").append(entry.requested==null?"未读取":entry.requested.dimensions().replace("x"," × "))
                .append("\n目标 FPS  ").append(entry.requested==null?"未读取":EncodingFormat.value(entry.requested.fps()))
                .append("\n目标码率  ").append(entry.requested==null||entry.requested.bitrate()==null?"未读取":entry.requested.bitrate()+" bit/s")
                .append("\n输出分辨率  ").append(entry.output==null?"未读取":entry.output.dimensions().replace("x"," × "));
        }
        return text.toString();
    }
}
