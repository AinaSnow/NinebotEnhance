package dev.ichinomiya.ninebotenhance.hook;

import android.media.MediaCodec;
import android.media.MediaFormat;
import android.os.Bundle;
import dev.ichinomiya.ninebotenhance.client.FrameClient;
import dev.ichinomiya.ninebotenhance.diagnostics.*;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only configuration/output observers, installed only inside the Ninebot process. */
public final class EncodingHooks {
    private final XposedModule module;
    private final FrameClient frames;
    private final EncodingDiagnostics log;
    private final Set<Executable> hooked=ConcurrentHashMap.newKeySet();
    private record Scope(EncodingDiagnostics.Session session) {}
    private final ThreadLocal<Scope> context=new ThreadLocal<>();
    private final WeakIdentityMap<EncodingDiagnostics.Session> created=new WeakIdentityMap<>();
    private final WeakIdentityMap<EncodingDiagnostics.Session> captureOwners=new WeakIdentityMap<>();
    public EncodingHooks(XposedModule module,FrameClient frames) { this.module=module;this.frames=frames;log=frames.encoding(); }

    public void install() {
        int installed=0;
        for(Method method:MediaCodec.class.getDeclaredMethods()) {
            String name=method.getName();
            Class<?>[] types=method.getParameterTypes();
            int flagIndex=types.length==4?(types[3]==int.class?3:types[2]==int.class?2:-1):-1;
            boolean configure=name.equals("configure") && types.length==4 && types[0]==MediaFormat.class && flagIndex>=0;
            boolean create=Modifier.isStatic(method.getModifiers()) && (name.equals("createEncoderByType")||name.equals("createByCodecName"));
            boolean callback=name.equals("setCallback") && types.length>0 && types[0]==MediaCodec.Callback.class;
            boolean output=name.equals("getOutputFormat") && method.getReturnType()==MediaFormat.class;
            boolean dequeue=name.equals("dequeueOutputBuffer") && types.length==2 && types[0]==MediaCodec.BufferInfo.class;
            boolean start=name.equals("start")&&types.length==0;
            boolean parameters=name.equals("setParameters")&&types.length==1&&types[0]==Bundle.class;
            boolean terminal=(name.equals("stop")||name.equals("reset")||name.equals("release"))&&types.length==0;
            if(!configure&&!create&&!callback&&!output&&!dequeue&&!start&&!terminal&&!parameters)continue;
            try { module.hook(method).intercept(chain->{
                Object codec=chain.getThisObject();
                EncodingDiagnostics.Session session=log.owner(codec);
                EncodingFormat requested=null;
                String parameterValues=null;
                if(create)session=captureSession();
                if(configure) {
                    try {
                        requested=read((MediaFormat)chain.getArg(0));
                        if((((Integer)chain.getArg(flagIndex))&MediaCodec.CONFIGURE_FLAG_ENCODE)!=0 && requested.mime().startsWith("video/")) {
                            EncodingDiagnostics.Session candidate=created.get(codec);
                            if(candidate==null)candidate=captureSession();
                            if(candidate!=null&&log.associate(candidate,codec))session=candidate;
                        }
                        // A decoder or non-video reconfiguration must never appear as a cast encoder.
                        if((((Integer)chain.getArg(flagIndex))&MediaCodec.CONFIGURE_FLAG_ENCODE)==0 || !requested.mime().startsWith("video/")) {
                            log.retire(session,codec);session=null;
                        }
                        if(session!=null)log.capture(session,"MediaCodec.configure.attempt",requested.describe());
                    } catch(Throwable ignored) { session=null; }
                }
                if(callback) {
                    try { Object value=chain.getArg(0);if(value!=null)inspectCallback(value.getClass()); }
                    catch(Throwable ignored) {}
                }
                if(parameters&&session!=null)try {
                    Bundle bundle=(Bundle)chain.getArg(0);List<String> values=new ArrayList<>();
                    if(bundle!=null)for(String key:new String[]{"video-bitrate","frame-rate","operating-rate","max-fps-to-encoder"}) {
                        String value=CaptureConfigReader.scalar(bundle.get(key),false);if(value!=null)values.add(key+"="+value);
                    }
                    if(!values.isEmpty())parameterValues=String.join(",",values);
                }catch(Throwable ignored) {}
                Object result;
                try { result=chain.proceed(); }
                catch(Throwable error) {
                    try { log.event(session,codec,name+" failed="+error.getClass().getSimpleName()); } catch(Throwable ignored) {}
                    throw error;
                }
                // Observer failures cannot change an original return value or throw into Ninebot.
                try {
                    if(create&&session!=null&&result!=null) {
                        synchronized(created) { if(created.size()<128)created.put(result,session); }
                    }
                    if(configure&&session!=null&&requested!=null) {
                        log.configured(session,codec,requested);
                    }
                    if(parameterValues!=null)log.event(session,codec,"setParameters accepted raw={"+parameterValues+"}");
                    if(output&&result instanceof MediaFormat)log.format(session,codec,read((MediaFormat)result),true,"MediaCodec."+name);
                    if(start&&session!=null) {
                        log.event(session,codec,"start");
                        // Calling this after start is legal for sync and async codecs; no buffers are consumed.
                        log.format(session,codec,read(((MediaCodec)codec).getOutputFormat()),true,"MediaCodec.start/getOutputFormat");
                    }
                    if(dequeue&&result instanceof Integer&&(Integer)result>=0)buffer(session,codec,(MediaCodec.BufferInfo)chain.getArg(0));
                    if(dequeue&&session!=null&&Integer.valueOf(MediaCodec.INFO_OUTPUT_FORMAT_CHANGED).equals(result))
                        log.format(session,codec,read(((MediaCodec)codec).getOutputFormat()),true,"INFO_OUTPUT_FORMAT_CHANGED");
                    if(terminal) { log.event(session,codec,name);log.retire(session,codec); }
                } catch(Throwable ignored) {}
                return result;
            });installed++; }
            catch(Throwable error) { frames.report("ENCODING hook unavailable MediaCodec."+name+" "+error.getClass().getSimpleName()); }
        }
        frames.report("ENCODING observers MediaCodec="+installed+"; metadata only; waiting for vehicle session");
    }

    public void inspect(Class<?> type) {
        if(!HookPolicy.captureClass(type.getName()))return;
        boolean config=CaptureConfigReader.videoConfig(type);
        if(!type.isInterface()&&!type.getName().contains("$"))
            for(Constructor<?> constructor:type.getDeclaredConstructors())installCapture(constructor,config);
        for(Method method:type.getDeclaredMethods()) {
            String name=method.getName().toLowerCase(Locale.ROOT);
            if(!Modifier.isAbstract(method.getModifiers())&&!method.isBridge() && (name.startsWith("create")||name.startsWith("prepare")
                    ||name.startsWith("configure")||name.startsWith("init")||name.startsWith("start")||name.startsWith("setup")
                    ||name.matches("set.*(width|height|fps|framerate|bitrate|videoconfig)")))installCapture(method,config);
        }
    }
    private void installCapture(Executable executable,boolean config) {
        if(!hooked.add(executable))return;
        try { module.hook(executable).intercept(chain->{
            EncodingDiagnostics.Session session=log.active();
            if(session==null)return chain.proceed();
            Object owner=chain.getThisObject();
            // CaptureClient is a reusable factory; controllers/encoders belong to the session that created them.
            boolean factory=executable.getDeclaringClass().getName().equals("cn.ninebot.capture.CaptureClient")||config;
            try { if(owner!=null&&!factory) {
                synchronized(captureOwners) {
                    EncodingDiagnostics.Session prior=captureOwners.get(owner);
                    if(prior!=null&&prior!=session)session=null;
                    else if(prior==null&&captureOwners.size()<256)captureOwners.put(owner,session);
                }
            } } catch(Throwable ignored) { session=null; }
            Scope previous=context.get();
            if(previous!=null)session=previous.session();
            context.set(new Scope(session));
            String source=executable.getDeclaringClass().getSimpleName()+"."+(executable instanceof Constructor?"<init>":executable.getName())
                    +"("+String.join(",",Arrays.stream(executable.getParameterTypes()).map(Class::getSimpleName).toArray(String[]::new))+")";
            try {
                try {
                    List<?> args=chain.getArgs();
                    List<String> values=new ArrayList<>();
                    for(int i=0;i<args.size();i++) {
                        Object value=args.get(i);
                        String scalar=CaptureConfigReader.scalar(value,false);
                        if(scalar!=null)values.add("arg"+i+"="+scalar);
                        else if(value!=null&&CaptureConfigReader.videoConfig(value.getClass()))
                            values.add("arg"+i+" VideoConfig={"+CaptureConfigReader.fields(value,true)+"}");
                        else if(value instanceof MediaFormat)log.capture(session,source+".arg"+i,read((MediaFormat)value).describe());
                    }
                    if(!values.isEmpty())log.capture(session,source+".arguments",String.join(",",values));
                } catch(Throwable ignored) {}
                Object result=chain.proceed();
                try {
                    String fields=CaptureConfigReader.fields(chain.getThisObject(),config);
                    if(!fields.equals("未读取")||config)log.capture(session,source+".fields",fields);
                } catch(Throwable ignored) {}
                return result;
            } finally { if(previous==null)context.remove();else context.set(previous); }
        }); }
        catch(Throwable error) { hooked.remove(executable);frames.report("ENCODING capture hook unavailable "+executable.getDeclaringClass().getSimpleName()+" "+error.getClass().getSimpleName()); }
    }
    private EncodingDiagnostics.Session captureSession() {
        Scope scoped=context.get();
        if(scoped!=null)return scoped.session();
        EncodingDiagnostics.Session current=log.active();if(current==null)return null;
        for(StackTraceElement frame:Thread.currentThread().getStackTrace())
            if(HookPolicy.captureClass(frame.getClassName()))return current;
        return null;
    }
    private void inspectCallback(Class<?> type) throws NoSuchMethodException {
        hookCallback(type.getMethod("onOutputFormatChanged",MediaCodec.class,MediaFormat.class),true);
        hookCallback(type.getMethod("onOutputBufferAvailable",MediaCodec.class,int.class,MediaCodec.BufferInfo.class),false);
    }
    private void hookCallback(Method method,boolean format) {
        if(Modifier.isAbstract(method.getModifiers())||!hooked.add(method))return;
        try { module.hook(method).intercept(chain->{
            try {
                Object codec=chain.getArg(0);EncodingDiagnostics.Session session=log.owner(codec);
                if(session!=null) {
                    if(format)log.format(session,codec,read((MediaFormat)chain.getArg(1)),true,"onOutputFormatChanged");
                    else buffer(session,codec,(MediaCodec.BufferInfo)chain.getArg(2));
                }
            } catch(Throwable ignored) {}
            return chain.proceed();
        }); } catch(Throwable error) { hooked.remove(method);frames.report("ENCODING callback hook unavailable "+method.getName()); }
    }
    private void buffer(EncodingDiagnostics.Session session,Object codec,MediaCodec.BufferInfo info) {
        if(info!=null)log.buffer(session,codec,info.presentationTimeUs,info.size,info.flags);
    }
    private static EncodingFormat read(MediaFormat format) {
        Map<String,Object> fields=new HashMap<>();
        if(format!=null) {
            try { fields.put("mime",format.getString("mime")); } catch(RuntimeException ignored) {}
            for(String key:new String[]{"width","height","frame-rate","bitrate","crop-left","crop-top","crop-right","crop-bottom"})
                try { if(format.containsKey(key))fields.put(key,format.getNumber(key)); } catch(RuntimeException ignored) {}
        }
        return EncodingFormat.read(fields);
    }
}
