package dev.ichinomiya.ninebotenhance.notification;

import android.os.Bundle;
import dev.ichinomiya.ninebotenhance.core.BmsData;
import dev.ichinomiya.ninebotenhance.core.BmsState;

/** The BMS link state and last reading as they travel inside the dashboard snapshot. */
public final class BmsBundle {
    public static Bundle write(BmsState state,int pollMs){
        Bundle b=new Bundle();BmsData d=state.data();
        b.putInt("phase",state.phase());b.putString("detail",state.detail());b.putInt("poll_ms",pollMs);
        b.putLong("at",d.at());b.putString("name",d.name());b.putInt("mos",d.mos());b.putInt("cells",d.cells());
        b.putFloat("capacity",d.capacityAh());b.putFloat("remaining",d.remainingAh());b.putFloat("volts",d.volts());b.putFloat("amps",d.amps());
        b.putInt("watts",d.watts());b.putInt("soc",d.soc());b.putIntArray("temps",d.temps());
        b.putInt("max_cell",d.maxCellMv());b.putInt("min_cell",d.minCellMv());b.putInt("avg_cell",d.avgCellMv());b.putInt("diff",d.diffMv());
        b.putFloat("cycle_ah",d.cycleAh());b.putInt("cycles",d.cycles());b.putIntArray("cells_mv",d.cellMv());
        return b;
    }
    public static BmsState read(Bundle b){
        if(b==null)return BmsState.NONE;
        BmsData d=new BmsData(b.getString("name",""),b.getInt("mos"),b.getInt("cells"),b.getFloat("capacity"),b.getFloat("remaining"),b.getFloat("volts"),b.getFloat("amps"),
                b.getInt("watts"),b.getInt("soc",-1),b.getIntArray("temps"),b.getInt("max_cell"),b.getInt("min_cell"),b.getInt("avg_cell"),b.getInt("diff"),
                b.getFloat("cycle_ah"),b.getInt("cycles"),b.getIntArray("cells_mv"),b.getLong("at"));
        return new BmsState(b.getInt("phase"),d,b.getString("detail",""));
    }
    public static int pollMs(Bundle b){return b==null?0:b.getInt("poll_ms",0);}
    private BmsBundle(){}
}
