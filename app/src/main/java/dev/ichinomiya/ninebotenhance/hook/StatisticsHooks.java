package dev.ichinomiya.ninebotenhance.hook;

import android.media.MediaCodec;
import android.os.SystemClock;
import dev.ichinomiya.ninebotenhance.client.FrameClient;
import dev.ichinomiya.ninebotenhance.diagnostics.*;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Read-only observation. Original arguments, result, continuation and send behavior are untouched. */
public final class StatisticsHooks {
    private final XposedModule module;
    private final FrameClient frames;
    private final Set<Method> hooked=ConcurrentHashMap.newKeySet();
    public StatisticsHooks(XposedModule module,FrameClient frames){this.module=module;this.frames=frames;}
    public void inspect(Class<?> type) {
        String name=type.getName();
        boolean transport=name.equals("cn.ninebot.library.screencast.BluetoothRtpSender") || name.equals("cn.ninebot.mapcapture.NBBluetoothRtpSender");
        boolean encoder=implementsEncoder(type,0); if(!transport&&!encoder)return;
        for(Method method:type.getDeclaredMethods()) {
            if(Modifier.isAbstract(method.getModifiers())||method.isSynthetic()||method.isBridge())continue;
            String methodName=method.getName().toLowerCase(Locale.ROOT);
            if(transport&&!methodName.contains("send") || encoder&&!methodName.startsWith("on"))continue;
            int payload=-1,info=-1;
            Class<?>[] types=method.getParameterTypes();
            for(int i=0;i<types.length;i++) {if(types[i]==byte[].class||types[i]==ByteBuffer.class){if(payload!=-1){payload=-2;break;}payload=i;} if(types[i]==MediaCodec.BufferInfo.class)info=i;}
            if(payload<0||!hooked.add(method))continue;
            final int index=payload,infoIndex=info; final boolean isTransport=transport;
            String source=method.toGenericString();
            try { module.hook(method).intercept(chain->{
                StreamStats stats=frames.transportStats(); ByteBuffer data=null; int size=0; RtpPacket packet=null;
                if(stats!=null) try {
                    Object value=chain.getArg(index);
                    data=value instanceof byte[]?ByteBuffer.wrap((byte[])value):value instanceof ByteBuffer?((ByteBuffer)value).duplicate():null;
                    if(data!=null&&infoIndex>=0) {
                        MediaCodec.BufferInfo details=(MediaCodec.BufferInfo)chain.getArg(infoIndex);
                        if(details==null || details.size<=0 || (details.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG)!=0 || details.offset<0 || details.offset>data.capacity()-details.size) data=null;
                        else {data.clear();data.position(details.offset);data.limit(details.offset+details.size);}
                    }
                    if(data!=null) {size=data.remaining();if(isTransport)packet=RtpPacket.parse(data);}
                } catch(RuntimeException ignored) {}
                Object result=chain.proceed();
                if(stats!=null && !(result instanceof Boolean && !((Boolean)result))) {
                    if(isTransport&&packet!=null)stats.packet(SystemClock.elapsedRealtime(),source,packet.bytes,packet.frameKey,packet.marker);
                    else if(!isTransport&&size>0)stats.encoded(SystemClock.elapsedRealtime(),source,size);
                }
                return result;
            }); frames.report("STATS observer "+source); }
            catch(Throwable e){hooked.remove(method);frames.report("STATS observer unavailable "+method.getName());}
        }
    }
    private static boolean implementsEncoder(Class<?> type,int depth) {
        if(type==null||depth>8)return false;
        if(type.getName().equals("cn.ninebot.capture.encoder.EncodeListener"))return true;
        for(Class<?> face:type.getInterfaces())if(implementsEncoder(face,depth+1))return true;
        return implementsEncoder(type.getSuperclass(),depth+1);
    }
}
