package dev.ichinomiya.ninebotenhance.core;

import java.util.*;

/**
 * Diagnostic register table for the debug overlay: which two-byte status registers to poll, the raw value of their
 * last reply, when it arrived and last changed, whether a read is in flight, and whether the last read went unanswered.
 * The candidates are the 14103 registers the app itself never displays, so a register that follows the gear, reverse
 * or hill-hold state can be spotted on the vehicle.
 */
public final class RegisterProbe {
    public static final String[] CANDIDATES={"rInfoBool2","rStateBool","rBool_0x02","rAlarm","rDisBoolAA","rFunAppBool","rFunAppBool2","rFunAppBool3","rFunAppBool4","rFunAppBoolF3","rFunBool6","rFunBool8","rLightLevel","rTimeFull","rCTLBool","rFunBool","rBool_0xFA","rFunBool5","rInfSet","rInfRtstateBool2","rFunECU_0x4F","rFunSupBoolEcu","rDVRFStatus","rWarn","rCTLBool2","rDisBoolA9","rBool2","rBool_0xF0","rBool_0xF1","rBool_0xF2","rBool_0xF6","rBool_0xFD","rCTL_BOOL_8","rBoolVcu_14F","rInfoBool3","rNos","rPower","rSpeed","rGearValue1","rGearValue2","rCfgMode","rEnergyStatus","rTCS","rSlope","rEleBrake","rSpeedIntensity","rTftStatusBool","rTftBool","rBoolTftA1","rTftBool2","rTftSetBool","rWidgetSwitch"};
    /** A value that changed within this window is highlighted on the overlay. */
    public static final long CHANGE_HIGHLIGHT_MS=3000;
    /** Boards whose whole 0-255 index range can be read through injected read commands (2 bytes each). */
    public static final String[] RAW_MODULES={"dis","ecu","mcu"};
    public static final int RAW_INDEXES=256;
    /** Synthetic command name for a raw register read, e.g. xdis_1a; configured names start with r, s or w, so no collision. */
    public static String rawName(String module,int index){return "x"+module+"_"+String.format(java.util.Locale.ROOT,"%02x",index);}
    public static boolean raw(String name){return name!=null&&name.length()>3&&name.charAt(0)=='x'&&name.indexOf('_')>1;}
    /** Last reply bytes and little-endian value, receipt and change times (elapsedRealtime, 0 = never), in-flight read, unanswered last read. */
    public record Value(String hex,int littleEndian,long repliedAt,long changedAt,boolean pending,boolean silent){
        public boolean changedWithin(long now,long window){return changedAt>0&&now-changedAt<=window;}
    }
    public record Row(String name,Value value){}
    private final Map<String,Value> values=new HashMap<>();
    public static Set<String> all(){return new LinkedHashSet<>(Arrays.asList(CANDIDATES));}
    public static boolean candidate(String name){for(String c:CANDIDATES)if(c.equals(name))return true;return false;}
    /** A read was dispatched; the row shows as updating until it is answered or given up. */
    public synchronized void sent(String name,long now){
        Value v=values.get(name);
        values.put(name,v==null?new Value("",-1,0,0,true,false):new Value(v.hex(),v.littleEndian(),v.repliedAt(),v.changedAt(),true,v.silent()));
    }
    /** Reply bytes arrived; returns whether the value differs from the previous reply. */
    public synchronized boolean reply(String name,String hex,int littleEndian,long now){
        Value v=values.get(name);boolean changed=v==null||v.repliedAt()==0||!hex.equals(v.hex());
        values.put(name,new Value(hex,littleEndian,now,changed?now:v.changedAt(),false,false));
        return changed;
    }
    /** The callback answered but no frame carried bytes (or the frame path handled it already): just clear the in-flight mark. */
    public synchronized void settled(String name){
        Value v=values.get(name);if(v==null||!v.pending())return;
        values.put(name,new Value(v.hex(),v.littleEndian(),v.repliedAt(),v.changedAt(),false,v.silent()));
    }
    /** The read timed out or was rejected; earlier values stay visible but dimmed. */
    public synchronized void silent(String name,long now){
        Value v=values.get(name);
        values.put(name,v==null?new Value("",-1,0,0,false,true):new Value(v.hex(),v.littleEndian(),v.repliedAt(),v.changedAt(),false,true));
    }
    public synchronized void clear(){values.clear();}
    /** Rows in catalogue order for the selected names; a null value means the register has not been read yet. */
    public synchronized List<Row> snapshot(Collection<String> names){return snapshot(names,Collections.emptySet());}
    /** Catalogue rows first, then every raw register of the given boards that has been read at least once, in index order. */
    public synchronized List<Row> snapshot(Collection<String> names,Collection<String> rawModules){
        List<Row> rows=new ArrayList<>();
        for(String name:CANDIDATES)if(names.contains(name))rows.add(new Row(name,values.get(name)));
        for(String module:RAW_MODULES)if(rawModules.contains(module))for(int i=0;i<RAW_INDEXES;i++){String name=rawName(module,i);Value v=values.get(name);if(v!=null)rows.add(new Row(name,v));}
        return rows;
    }
    /** When the table overflows: rows whose value changed most recently first, then the most recently answered, pending ones kept; unread rows last. */
    public static List<Row> prioritized(List<Row> rows,int limit){
        List<Row> sorted=new ArrayList<>(rows);
        sorted.sort((a,b)->{Value x=a.value(),y=b.value();long cx=x==null?0:x.changedAt(),cy=y==null?0:y.changedAt();if(cx!=cy)return Long.compare(cy,cx);
            long rx=x==null?0:x.repliedAt(),ry=y==null?0:y.repliedAt();if(rx!=ry)return Long.compare(ry,rx);
            boolean px=x!=null&&x.pending(),py=y!=null&&y.pending();return px==py?0:px?-1:1;});
        return sorted.size()>limit?new ArrayList<>(sorted.subList(0,limit)):sorted;
    }
    public static int replied(List<Row> rows){int n=0;for(Row r:rows)if(r.value()!=null&&r.value().repliedAt()>0)n++;return n;}
    public static int changed(List<Row> rows,long now,long window){int n=0;for(Row r:rows)if(r.value()!=null&&r.value().changedWithin(now,window))n++;return n;}
}
