package dev.ichinomiya.ninebotenhance.ui;

import android.app.*;
import android.content.Context;
import android.graphics.Canvas;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import dev.ichinomiya.ninebotenhance.client.FrameClient;
import dev.ichinomiya.ninebotenhance.core.BmsCard;
import dev.ichinomiya.ninebotenhance.core.HudPalette;
import dev.ichinomiya.ninebotenhance.core.SidebarLayout;
import dev.ichinomiya.ninebotenhance.notification.BmsCardPainter;
import java.util.*;

/**
 * The BMS card layout: every field on its own row with a drag handle for the order and a button cycling the row it sits in
 * (hidden, 1, 2 or 3); the preview at the bottom draws the card from the sample reading with the same painter as the dashboard.
 */
public final class BmsCardDialog {
    private static final String[] ROW_LABELS={"隐藏","第 1 行","第 2 行","第 3 行"};
    public static void show(Activity activity,FrameClient frames,View reference){
        MirrorUi theme=new MirrorUi(activity,reference);BmsCard.Layout current=frames.bmsLayout();
        LinearLayout content=new LinearLayout(activity);content.setOrientation(LinearLayout.VERTICAL);
        int pad=MirrorUi.dp(activity,20),gap=MirrorUi.dp(activity,8);content.setPadding(pad,gap,pad,gap);
        ScrollView scroll=new ScrollView(activity);
        LinearLayout list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);
        Preview preview=new Preview(activity,theme.dark);
        Map<Integer,Integer> rowOf=new LinkedHashMap<>();
        ArrayList<Integer> ordered=new ArrayList<>(current.fields());
        for(int f:BmsCard.FIELDS)if(!ordered.contains(f))ordered.add(f);
        for(int field:ordered){
            rowOf.put(field,current.rowOf(field));
            LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setTag(field);row.setMinimumHeight(MirrorUi.dp(activity,44));
            TextView handle=WidgetSettingsDialog.handle(activity,theme);row.addView(handle,new LinearLayout.LayoutParams(MirrorUi.dp(activity,32),-1));
            handle.setOnTouchListener(new WidgetSettingsDialog.DragHandle(row,list,scroll));
            TextView name=new TextView(activity);name.setText(BmsCard.label(field));name.setTextColor(theme.text);name.setTextSize(16);row.addView(name,new LinearLayout.LayoutParams(0,-2,1));
            Button place=new Button(activity);place.setAllCaps(false);theme.button(place,null);place.setTextSize(12);place.setMinHeight(0);place.setMinimumHeight(0);place.setMinWidth(0);place.setMinimumWidth(0);
            place.setPadding(MirrorUi.dp(activity,8),0,MirrorUi.dp(activity,8),0);place.setText(ROW_LABELS[rowOf.get(field)+1]);
            place.setOnClickListener(v->{int next=(rowOf.get(field)+2)%(BmsCard.MAX_ROWS+1)-1;rowOf.put(field,next);place.setText(ROW_LABELS[next+1]);preview.setLayout(layout(list,rowOf));});
            LinearLayout.LayoutParams placeParams=new LinearLayout.LayoutParams(MirrorUi.dp(activity,80),MirrorUi.dp(activity,32));placeParams.leftMargin=MirrorUi.dp(activity,6);
            row.addView(place,placeParams);
            LinearLayout.LayoutParams rowParams=new LinearLayout.LayoutParams(-1,-2);rowParams.topMargin=MirrorUi.dp(activity,4);list.addView(row,rowParams);
        }
        content.addView(list);
        list.setOnHierarchyChangeListener(new ViewGroup.OnHierarchyChangeListener(){
            @Override public void onChildViewAdded(View parent,View child){preview.setLayout(layout(list,rowOf));}
            @Override public void onChildViewRemoved(View parent,View child){}
        });
        LinearLayout.LayoutParams previewParams=new LinearLayout.LayoutParams(-1,-2);previewParams.topMargin=MirrorUi.dp(activity,12);
        content.addView(preview,previewParams);preview.setLayout(current);
        scroll.addView(content);
        TextView title=new TextView(activity);title.setText("BMS");title.setTextSize(20);title.setTextColor(theme.text);title.setPadding(pad,pad,pad,pad/2);
        AlertDialog dialog=new AlertDialog.Builder(activity).setCustomTitle(title).setView(scroll).setNegativeButton("关闭",null)
                .setPositiveButton("保存",(d,w)->frames.saveBmsLayout(layout(list,rowOf))).create();
        dialog.show();dialog.getWindow().setBackgroundDrawable(theme.background(activity,theme.surface,22,false));
        dialog.getButton(-1).setTextColor(theme.accent);dialog.getButton(-2).setTextColor(theme.accent);
    }
    /** Rows in list order, each field placed in the row its button shows. */
    private static BmsCard.Layout layout(LinearLayout list,Map<Integer,Integer> rowOf){
        ArrayList<List<Integer>> rows=new ArrayList<>();for(int i=0;i<BmsCard.MAX_ROWS;i++)rows.add(new ArrayList<>());
        for(int i=0;i<list.getChildCount();i++){int field=(Integer)list.getChildAt(i).getTag();int row=rowOf.getOrDefault(field,-1);if(row>=0&&row<BmsCard.MAX_ROWS)rows.get(row).add(field);}
        return new BmsCard.Layout(rows);
    }
    /** The card at the width of the dialog, scaled from the reference column width; height follows the visible rows. */
    private static final class Preview extends View {
        private final BmsCardPainter painter=new BmsCardPainter();private final HudPalette palette;private BmsCard.Layout layout=BmsCard.DEFAULT;
        Preview(Context context,boolean dark){super(context);palette=HudPalette.of(dark);}
        void setLayout(BmsCard.Layout value){layout=value;requestLayout();invalidate();}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            int width=MeasureSpec.getSize(widthSpec);float scale=width/SidebarLayout.WIDTH;
            setMeasuredDimension(width,Math.round(painter.height(layout,true)*scale)+1);
        }
        @Override protected void onDraw(Canvas canvas){
            float scale=getWidth()/SidebarLayout.WIDTH;int saved=canvas.save();canvas.scale(scale,scale);
            painter.draw(canvas,palette,0,SidebarLayout.WIDTH,layout,BmsCard.SAMPLE,true);
            canvas.restoreToCount(saved);
        }
    }
    private BmsCardDialog(){}
}
