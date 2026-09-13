package dev.ichinomiya.ninebotenhance.diagnostics;

import java.nio.ByteBuffer;

/** Header-only RTP v2 observation; reject RTCP and malformed/truncated packets. Does not advance buffers. */
public final class RtpPacket {
    public final int bytes;
    public final boolean marker;
    public final String frameKey;
    private RtpPacket(int bytes, boolean marker, String key) { this.bytes=bytes;this.marker=marker;frameKey=key; }
    public static RtpPacket parse(ByteBuffer source) {
        if (source == null) return null;
        ByteBuffer b=source.duplicate(); int p=b.position(),length=b.remaining(); if(length<12)return null;
        int first=b.get(p)&255,second=b.get(p+1)&255;
        if((first>>>6)!=2 || second>=192 && second<=223) return null;
        int header=12+(first&15)*4; if(header>length)return null;
        if((first&16)!=0) {if(header+4>length)return null;
            int words=((b.get(p+header+2)&255)<<8)|(b.get(p+header+3)&255);header+=4+words*4;if(header>length)return null;}
        int padding=(first&32)==0?0:b.get(p+length-1)&255;
        if((first&32)!=0 && padding==0 || header+padding>=length)return null;
        long timestamp=0,ssrc=0;for(int i=0;i<4;i++){timestamp=(timestamp<<8)|(b.get(p+4+i)&255);ssrc=(ssrc<<8)|(b.get(p+8+i)&255);}
        return new RtpPacket(length,(second&128)!=0,ssrc+":"+timestamp);
    }
}
