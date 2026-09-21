package dev.ichinomiya.ninebotenhance.core;

/**
 * Frames of the TX lamp-hoist controller (BLE advertising name {@code MOTORE} + the last six MAC digits). Every frame is
 * {@code AA 55 CMD LEN payload… SIGN}, where SIGN is the low byte of the sum of all preceding bytes. Command 0x11 authenticates
 * with a six digit ASCII password and must precede any control; 0x12 drives the hoist to a height percentage at a speed; 0x13
 * echoes the travel configuration; 0x16 changes the password. The device pushes 0x14 (self check, direction) and 0x15 (position,
 * speed, travel limits) on its notify characteristic. Pure byte work: no Android types, so the module can unit test it.
 */
public final class TxLampProtocol {
    public static final String SERVICE="0000ffe0-0000-1000-8000-00805f9b34fb";
    public static final String CHAR_WRITE="0000ffe3-0000-1000-8000-00805f9b34fb";
    public static final String CHAR_NOTIFY="0000ffe4-0000-1000-8000-00805f9b34fb";
    public static final String CCCD="00002902-0000-1000-8000-00805f9b34fb";
    /** Full advertised name: MOTORE plus the last six address digits. Matching uses the shorter stem so a firmware that
     *  spells the suffix differently still shows up in the scan. */
    public static final String NAME_PREFIX="MOTORE",NAME_STEM="MOTOR";
    public static boolean lampName(String name){
        if(name==null||name.length()<NAME_STEM.length())return false;
        for(int i=0;i<NAME_STEM.length();i++){
            char c=name.charAt(i);if(c>='a'&&c<='z')c-=32;
            if(c!=NAME_STEM.charAt(i))return false;
        }
        return true;
    }
    public static final int HEADER_HIGH=0xaa,HEADER_LOW=0x55;
    public static final int CMD_AUTH=0x11,CMD_MOVE=0x12,CMD_CONFIG=0x13,CMD_SELF_CHECK=0x14,CMD_STATE=0x15,CMD_PASSWORD=0x16;
    public static final int MIN_POSITION=0,MAX_POSITION=100,MIN_SPEED=0,MAX_SPEED=100,PASSWORD_LENGTH=6;
    /** Accepted answer to 0x11 / 0x16: the first payload byte is 1. */
    public static final int ACCEPTED=1;
    public static byte[] frame(int cmd,byte... payload){
        byte[] body=payload==null?new byte[0]:payload;
        if(cmd<0||cmd>0xff||body.length>0xff)throw new IllegalArgumentException("大灯帧超出范围");
        byte[] out=new byte[5+body.length];
        out[0]=(byte)HEADER_HIGH;out[1]=(byte)HEADER_LOW;out[2]=(byte)cmd;out[3]=(byte)body.length;
        System.arraycopy(body,0,out,4,body.length);
        out[out.length-1]=(byte)sign(out,out.length-1);
        return out;
    }
    /** Low byte of the running sum over the first {@code length} bytes. */
    public static int sign(byte[] data,int length){int total=0;for(int i=0;i<length;i++)total+=data[i]&0xff;return total&0xff;}
    public static boolean validPassword(String password){
        if(password==null||password.length()!=PASSWORD_LENGTH)return false;
        for(int i=0;i<PASSWORD_LENGTH;i++){char c=password.charAt(i);if(c<'0'||c>'9')return false;}
        return true;
    }
    public static byte[] auth(String password){return credential(CMD_AUTH,password);}
    public static byte[] password(String password){return credential(CMD_PASSWORD,password);}
    private static byte[] credential(int cmd,String password){
        if(!validPassword(password))throw new IllegalArgumentException("大灯密码必须是 6 位数字");
        byte[] ascii=new byte[PASSWORD_LENGTH];
        for(int i=0;i<PASSWORD_LENGTH;i++)ascii[i]=(byte)password.charAt(i);
        return frame(cmd,ascii);
    }
    public static byte[] moveTo(int position,int speed){
        if(position<MIN_POSITION||position>MAX_POSITION||speed<MIN_SPEED||speed>MAX_SPEED)throw new IllegalArgumentException("大灯高度和速度为 0–100");
        return frame(CMD_MOVE,(byte)position,(byte)speed);
    }
    public static byte[] config(int selfCheck,int direction,int low,int high,int pattern){
        if(low<MIN_POSITION||high>MAX_POSITION||low>high)throw new IllegalArgumentException("大灯行程范围无效");
        return frame(CMD_CONFIG,(byte)selfCheck,(byte)direction,(byte)low,(byte)high,(byte)pattern);
    }
    public static int clampPosition(int value){return Math.max(MIN_POSITION,Math.min(MAX_POSITION,value));}
    /** Target inside the travel limits the device last reported; an unusable range falls back to the full span. */
    public static int clampPosition(int value,int low,int high){
        int min=low<MIN_POSITION||low>MAX_POSITION?MIN_POSITION:low,max=high<MIN_POSITION||high>MAX_POSITION?MAX_POSITION:high;
        if(min>max){min=MIN_POSITION;max=MAX_POSITION;}
        return Math.max(min,Math.min(max,value));
    }
    /** Payload of a well formed frame, or null when the header, the declared length or the checksum do not hold. */
    public static byte[] payload(byte[] value){
        if(value==null||value.length<5)return null;
        if((value[0]&0xff)!=HEADER_HIGH||(value[1]&0xff)!=HEADER_LOW)return null;
        int length=value[3]&0xff;
        if(value.length<5+length)return null;
        if((value[4+length]&0xff)!=sign(value,4+length))return null;
        byte[] out=new byte[length];System.arraycopy(value,4,out,0,length);return out;
    }
    public static int command(byte[] value){return payload(value)==null?-1:value[2]&0xff;}
    /** Whether this is an accepted answer to the password or authentication command. */
    public static boolean accepted(byte[] value){
        byte[] payload=payload(value);int command=command(value);
        return payload!=null&&payload.length>0&&(command==CMD_AUTH||command==CMD_PASSWORD)&&(payload[0]&0xff)==ACCEPTED;
    }
    /** Self check, direction, position, speed and travel limits; -1 for fields the frame does not carry. */
    public record Report(int command,int selfCheck,int direction,int position,int speed,int low,int high){
        public boolean state(){return command==CMD_STATE;}
    }
    /** Device report 0x14 or 0x15; null for anything else, including the execution receipts of 0x11–0x13. */
    public static Report parse(byte[] value){
        byte[] payload=payload(value);if(payload==null)return null;
        int command=value[2]&0xff;
        if(command==CMD_SELF_CHECK&&payload.length>=2)return new Report(command,payload[0]&0xff,payload[1]&0xff,-1,-1,-1,-1);
        if(command==CMD_STATE&&payload.length>=4)return new Report(command,-1,-1,payload[0]&0xff,payload[1]&0xff,payload[2]&0xff,payload[3]&0xff);
        return null;
    }
    private TxLampProtocol(){}
}
