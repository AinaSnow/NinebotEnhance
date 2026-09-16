import android.graphics.*;
import android.os.*;
import dev.ichinomiya.ninebotenhance.notification.DashboardHud;
import java.io.FileOutputStream;

/** Native-device close-up for visual inspection, using synthetic media metadata. */
public final class MusicLayoutRender {
    public static void main(String[] args) {
        try {
            Looper.prepareMainLooper();
            Typeface.class.getDeclaredMethod("setSystemFontMap",SharedMemory.class).invoke(null,new Object[]{null});
            Bitmap image=Bitmap.createBitmap(1872,669,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);canvas.drawColor(0xff242424);
            for(int index=0;index<3;index++){
                int save=canvas.save();canvas.translate(index*624,0);canvas.clipRect(0,0,624,669);canvas.scale(3,3);canvas.translate(-640,-257);
                Bundle state=HudSmoke.state(0);if(index==1)state.getBundle("music").putInt("state",2);
                if(index==2){Bundle idle=new Bundle();idle.putBoolean("granted",true);state.putBundle("music",idle);}
                DashboardHud hud=new DashboardHud();hud.reset("layout");hud.accept("layout",state,100000);hud.acceptTires(HudSmoke.tireState());hud.draw(canvas,848,480,100000);canvas.restoreToCount(save);
            }
            try(FileOutputStream output=new FileOutputStream(args[0])){image.compress(Bitmap.CompressFormat.PNG,100,output);}
            System.out.println("Native music layout rendered with synthetic metadata");
        }catch(Throwable e){e.printStackTrace(System.out);System.exit(1);}
    }
}
