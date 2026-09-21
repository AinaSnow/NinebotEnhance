package dev.ichinomiya.ninebotenhance.core;

/**
 * One turn-by-turn state from a phone navigation app, in the units the dashboard commands use: metres, seconds, and the
 * NaviState turn icon numbering (2 left, 3 right, 4 left-front, 5 right-front, 6 left-back, 7 right-back, 8 u-turn, 9 straight,
 * 11 / 12 roundabout, 15 arrived). {@code receivedAt} is the module service's elapsed-realtime clock when the update arrived.
 */
public record NaviUpdate(String source,int totalDistance,int remainDistance,int remainSeconds,int drivenDistance,int drivenSeconds,
                         String currentRoad,String nextRoad,int segmentRemain,int maneuver,int lights,long receivedAt) {
    public static final long FRESH_MS=8000;
    public boolean fresh(long now){return receivedAt>0&&now-receivedAt<=FRESH_MS;}
    public boolean arrived(){return maneuver==15||remainDistance<=0&&totalDistance>0;}
    public NaviUpdate receivedAt(long at){return new NaviUpdate(source,totalDistance,remainDistance,remainSeconds,drivenDistance,drivenSeconds,currentRoad,nextRoad,segmentRemain,maneuver,lights,at);}
    public String describe(){
        return source+" remain="+remainDistance+"m/"+remainSeconds+"s driven="+drivenDistance+"m/"+drivenSeconds+"s seg="+segmentRemain+"m icon="+maneuver+" lights="+lights+" cur="+currentRoad+" next="+nextRoad+" total="+totalDistance;
    }
    static String clean(String value){return value==null?"":value.trim();}
}
