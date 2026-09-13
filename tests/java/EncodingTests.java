import dev.ichinomiya.ninebotenhance.diagnostics.*;
import java.util.*;

final class EncodingTests {
    private static class ParentConfig { private final int width=860; }
    private static final class Config extends ParentConfig {
        private final int height=450;
        private final float frameRate=29.97f;
        private final long bitRate=1200000;
        private final int a=25; // An obfuscated field is evidence, not an inferred FPS.
        private static final int secretConstant=987;
        private final String account="should-never-be-read";
        private final String mime="video/avc";
        private final Object arbitrary=new Object() { @Override public String toString(){throw new AssertionError("object stringification");} };
        public int getWidth(){throw new AssertionError("getter called");}
    }
    private static void check(boolean result,String message){CoreTests.check(result,message);}
    static void run() {
        WeakIdentityMap<String> identities=new WeakIdentityMap<>();
        Object hostile=new Object(){@Override public int hashCode(){throw new AssertionError("target hashCode invoked");}@Override public boolean equals(Object o){throw new AssertionError("target equals invoked");}};
        Object other=new Object();identities.put(hostile,"one");identities.put(other,"two");
        check(identities.get(hostile).equals("one")&&identities.get(other).equals("two"),"observers use identity without invoking target methods");
        identities.put(hostile,"updated");check(identities.size()==2&&identities.get(hostile).equals("updated"),"identity reassignment does not duplicate entries");
        check(identities.get(null)==null&&identities.get(new Object())==null,"unknown objects do not inherit an encoder session");
        EncodingFormat format=EncodingFormat.read(Map.of("width",860,"height",480,"frame-rate",29.97f,"bitrate",1200000,"mime","video/avc"));
        check(format.dimensions().equals("860x480"),"encoder size is independent of virtual display defaults and padding");
        check(Math.abs(format.fps()-29.97)<.001,"fractional configured FPS preserved");
        check(format.bitrate()==1200000,"configured bitrate remains in bits per second");
        check(EncodingFormat.read(Map.of("frame-rate",30)).fps()==30,"integer FPS accepted");
        check(EncodingFormat.read(Map.of()).fps()==null,"missing target FPS never substituted with source 15 FPS");
        check(EncodingFormat.read(Map.of("frame-rate",Double.NaN,"width",-1,"height",480.5,"bitrate",0)).describe().contains("size=未读取x未读取"),"invalid dimensions remain unknown");
        for(Object invalid:new Object[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY,"30"})check(EncodingFormat.read(Map.of("frame-rate",invalid)).fps()==null,"invalid FPS rejected");
        Map<String,Object> crop=new HashMap<>(Map.of("width",864,"height",496,"crop-left",0,"crop-top",0,"crop-right",859,"crop-bottom",494));
        check(EncodingFormat.read(crop).describe().contains("visibleCrop=860x495@0,0"),"crop dimensions are inclusive, separate from coded dimensions");
        crop.put("crop-right",900);
        check(EncodingFormat.read(crop).describe().contains("visibleCrop=未读取"),"out-of-range crop is not advertised as actual dimensions");
        String raw=CaptureConfigReader.fields(new Config(),true);
        check(raw.contains("width=860")&&raw.contains("frameRate=29.97")&&raw.contains("a=25"),"private inherited and obfuscated numeric config fields recorded without getters");
        check(!raw.contains("account")&&!raw.contains("arbitrary")&&!raw.contains("secretConstant"),"config observer excludes unrelated strings, objects and static fields");
        String selected=CaptureConfigReader.fields(new Config(),false);
        check(selected.contains("mime=video/avc")&&!selected.contains("a=25"),"encoder fallback reads only named encoding metadata");
        check(CaptureConfigReader.scalar("https://example.com/token",true)==null,"non-codec strings excluded");

        List<String> lines=new ArrayList<>();
        EncodingDiagnostics log=new EncodingDiagnostics(lines::add);
        log.begin("first",true,1000);var first=log.active();Object codec=new Object();
        check(log.associate(first,codec),"codec can be associated before virtual display creation");
        log.capture(first,"createCapture.arguments","arg3=860,arg4=480,VideoConfig={fps=30}");
        log.configured(first,codec,format);
        int count=lines.size();log.format(first,codec,format,false,"duplicate");
        check(lines.size()==count,"unchanged format does not flood logs");
        log.format(first,codec,EncodingFormat.read(Map.of("width",864,"height",480,"mime","video/avc")),true,"output callback");
        check(log.summary().contains("requestedSize=860x480")&&log.summary().contains("outputSize=864x480"),"requested and output formats retained separately");
        check(log.summary().contains("targetFps=29.97"),"output missing FPS never erases requested target");
        log.tick(11000,null);
        check(lines.get(lines.size()-1).contains("actualFps=未读取"),"no buffer observations is unknown, not measured zero");
        log.buffer(first,codec,0,99,2); // codec config excluded
        log.buffer(first,codec,100,20,8); // partial access unit
        log.buffer(first,codec,100,30,0); // completion
        log.buffer(first,codec,100,30,0); // duplicate callback
        log.buffer(first,codec,50,10,0); // reordered PTS still a new frame
        log.buffer(first,codec,150,0,4); // empty EOS
        log.tick(21000,null);
        String rate=lines.get(lines.size()-1);
        check(rate.contains("frames=2 bytes=60")&&rate.contains("actualFps=0.2"),"access units exclude config, empty EOS and duplicates but include partial bytes and reordered PTS");
        log.configured(first,codec,format);
        check(log.summary().contains("outputSize=未读取"),"reconfigure invalidates the previous output format");
        log.retire(first,codec);check(log.owner(codec)==null,"released codec cannot emit late output into an active session");
        log.configured(first,codec,format);check(log.owner(codec)==first,"explicit same-session reconfigure restores codec observation");
        log.stop("wrong",22000,null);check(log.active()==first,"stale stop cannot end a newer session");
        log.stop("first",22000,null);count=lines.size();
        log.buffer(first,codec,250,100,0);log.capture(first,"late","fps=60");
        check(lines.size()==count&&log.active()==null,"stopped session rejects late capture and output observations");
        check(log.details().contains("VideoConfig={fps=30}")&&log.details().contains("frames=2 bytes=60"),"full metadata survives stop and rate-log eviction");
        log.begin("local",false,23000);
        check(log.active()==null&&!log.associate(first,new Object())&&log.summary().contains("本地模拟"),"local simulation cannot adopt vehicle encoders");
        log.begin("next",true,24000);var next=log.active();
        check(!log.associate(next,codec)&&log.owner(codec)==null,"old codec instance cannot be attributed to a new session");
        Object second=new Object();check(log.associate(next,second),"new session accepts its own codec");
        count=lines.size();log.format(first,second,format,false,"late configure result");
        check(lines.size()==count&&!log.summary().contains("29.97"),"late original call return cannot overwrite current parameters");
        log.capture(next,"fallback","width=860,frameRate=25");count=lines.size();log.capture(next,"fallback","width=860,frameRate=25");
        check(lines.size()==count,"native/FFmpeg fallback config log deduplicates identical readings");
        check(log.summary().contains("targetFps=未读取"),"raw fallback fields are not silently asserted to be MediaCodec targets");
        for(int i=0;i<100;i++)log.capture(next,"source"+i,"arg0="+i);
        check(log.details().split("CAPTURE source=").length==33,"capture sources are bounded per session");
        int codecs=1;for(int i=0;i<25;i++)if(log.associate(next,new Object()))codecs++;
        check(codecs==16,"codec bookkeeping is bounded");
        StreamStats counters=new StreamStats(25000);
        log.tick(35000,counters.snapshot(35000));
        check(lines.get(lines.size()-1).contains("callbackFps=未读取")&&lines.get(lines.size()-1).contains("submittedBps=未读取"),"missing encoder and sender callbacks never imply measured zero rate");
        counters.encoded(36000,"encoder callback",100);counters.packet(36000,"sender",120,"1:1",true);
        log.tick(46000,counters.snapshot(46000));
        check(lines.stream().anyMatch(x->x.contains("ENCODER_CALLBACK source=encoder callback"))&&lines.stream().anyMatch(x->x.contains("RTP_SENDER source=sender")),"rate logs name the selected observation sources");
        check(log.displaySummary().contains("目标 FPS")&&!log.displaySummary().contains("session="),"statistics UI describes parameters without internal session identifiers");
        log.begin("instant",true,47000);var instant=log.active();Object quick=new Object();log.associate(instant,quick);
        log.buffer(instant,quick,1,5,0);log.stop("instant",47000,null);
        check(lines.stream().anyMatch(x->x.contains("session=instant RATE")&&x.contains("frames=1 bytes=5 actualFps=未读取")),"zero-duration final window retains totals without inventing a rate");
    }
}
