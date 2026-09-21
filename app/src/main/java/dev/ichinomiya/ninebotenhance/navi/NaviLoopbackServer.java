package dev.ichinomiya.ninebotenhance.navi;

import dev.ichinomiya.ninebotenhance.core.NaviDestination;
import dev.ichinomiya.ninebotenhance.core.NaviUpdate;
import dev.ichinomiya.ninebotenhance.diagnostics.Diagnostics;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Loopback listener in the module process. A navigation app's module code connects to 127.0.0.1, sends the shared token and one
 * JSON navigation update per line; each valid line goes to {@link NaviHub}. Bound to the loopback address only, so nothing off
 * the device can reach it. Started once when the module service is created.
 */
public final class NaviLoopbackServer {
    private static volatile NaviLoopbackServer instance;
    public static synchronized void start(){if(instance==null){instance=new NaviLoopbackServer();instance.begin();}}
    private volatile ServerSocket server;private volatile boolean running;private volatile int port=-1;
    private void begin(){
        Thread thread=new Thread(this::accept,"Ninebot-NaviLoopback");thread.setDaemon(true);thread.start();
    }
    private void accept(){
        InetAddress loopback;try{loopback=InetAddress.getByName("127.0.0.1");}catch(Exception e){Diagnostics.add("NAVI loopback no address "+e.getClass().getSimpleName());return;}
        for(int candidate:NaviLoopback.PORTS){
            try{server=new ServerSocket(candidate,8,loopback);port=candidate;running=true;Diagnostics.add("NAVI loopback listening on 127.0.0.1:"+candidate);break;}
            catch(Exception e){/* try next port */}
        }
        if(!running){Diagnostics.add("NAVI loopback could not bind any port");return;}
        while(running){
            try{Socket socket=server.accept();Thread worker=new Thread(()->serve(socket),"Ninebot-NaviLoopbackConn");worker.setDaemon(true);worker.start();}
            catch(Exception e){if(running)Diagnostics.add("NAVI loopback accept "+e.getClass().getSimpleName());}
        }
    }
    private void serve(Socket socket){
        try(Socket s=socket;BufferedReader reader=new BufferedReader(new InputStreamReader(s.getInputStream(),NaviLoopback.CHARSET))){
            s.setTcpNoDelay(true);
            String token=reader.readLine();
            if(!NaviLoopback.TOKEN.equals(token)){Diagnostics.add("NAVI loopback bad token");return;}
            String line;int count=0;
            while((line=reader.readLine())!=null){
                NaviDestination destination=NaviUpdates.destinationFromJson(line);
                if(destination!=null){NaviHub.get().publishDestination(destination);continue;}
                NaviUpdate update=NaviUpdates.fromJson(line);
                if(update!=null){NaviHub.get().publish(update);count++;}
            }
            if(count>0)Diagnostics.add("NAVI loopback connection closed after "+count+" updates");
        }catch(Exception e){/* client closed; normal */}
    }
    public int port(){return port;}
    private NaviLoopbackServer(){}
}
