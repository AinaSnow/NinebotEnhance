package dev.ichinomiya.ninebotenhance.core;

import java.math.BigInteger;
import java.security.SecureRandom;

/**
 * secp256k1 key agreement in plain BigInteger arithmetic: the DL BMS handshake needs one scalar multiplication per connection,
 * and Android's bundled providers no longer offer this curve. Public keys travel as X||Y (64 bytes, big endian, no 0x04 prefix)
 * and the shared secret is the X coordinate of the product, exactly as the vendor application uses it.
 */
public final class Secp256k1 {
    public static final BigInteger P=new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFC2F",16);
    public static final BigInteger N=new BigInteger("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",16);
    static final BigInteger GX=new BigInteger("79BE667EF9DCBBAC55A06295CE870B07029BFCDB2DCE28D959F2815B16F81798",16);
    static final BigInteger GY=new BigInteger("483ADA7726A3C4655DA4FBFC0E1108A8FD17B448A68554199C47D08FFB10D4B8",16);
    private static final BigInteger SEVEN=BigInteger.valueOf(7);
    /** Affine point; null stands for the point at infinity. */
    public record Point(BigInteger x,BigInteger y){}
    public static final Point G=new Point(GX,GY);
    public static byte[] privateKey(SecureRandom random){
        while(true){
            byte[] bytes=new byte[32];random.nextBytes(bytes);
            BigInteger d=new BigInteger(1,bytes);
            if(d.signum()>0&&d.compareTo(N)<0)return fixed32(d);
        }
    }
    public static byte[] publicKey(byte[] privateKey){
        Point q=multiply(new BigInteger(1,privateKey),G);
        if(q==null)throw new IllegalArgumentException("invalid private key");
        return encode(q);
    }
    /** ECDH: X coordinate of privateKey * peer, 32 bytes; the peer key must be a valid point of the curve. */
    public static byte[] shared(byte[] privateKey,byte[] peerPublicKey){
        Point peer=decode(peerPublicKey);
        Point s=multiply(new BigInteger(1,privateKey),peer);
        if(s==null)throw new IllegalArgumentException("degenerate shared point");
        return fixed32(s.x());
    }
    public static byte[] encode(Point p){byte[] out=new byte[64];System.arraycopy(fixed32(p.x()),0,out,0,32);System.arraycopy(fixed32(p.y()),0,out,32,32);return out;}
    public static Point decode(byte[] xy){
        if(xy==null||xy.length!=64)throw new IllegalArgumentException("public key must be 64 bytes");
        byte[] x=new byte[32],y=new byte[32];System.arraycopy(xy,0,x,0,32);System.arraycopy(xy,32,y,0,32);
        Point p=new Point(new BigInteger(1,x),new BigInteger(1,y));
        if(!onCurve(p))throw new IllegalArgumentException("public key is not on secp256k1");
        return p;
    }
    public static boolean onCurve(Point p){
        if(p==null||p.x().signum()<0||p.y().signum()<0||p.x().compareTo(P)>=0||p.y().compareTo(P)>=0)return false;
        BigInteger left=p.y().multiply(p.y()).mod(P),right=p.x().modPow(BigInteger.valueOf(3),P).add(SEVEN).mod(P);
        return left.equals(right);
    }
    public static Point multiply(BigInteger k,Point p){
        Point result=null,addend=p;
        for(int i=0;i<k.bitLength();i++){
            if(k.testBit(i))result=add(result,addend);
            addend=add(addend,addend);
        }
        return result;
    }
    static Point add(Point a,Point b){
        if(a==null)return b;if(b==null)return a;
        BigInteger lambda;
        if(a.x().equals(b.x())){
            if(!a.y().equals(b.y())||a.y().signum()==0)return null;
            lambda=a.x().multiply(a.x()).multiply(BigInteger.valueOf(3)).multiply(a.y().shiftLeft(1).modInverse(P)).mod(P);
        }else lambda=b.y().subtract(a.y()).multiply(b.x().subtract(a.x()).mod(P).modInverse(P)).mod(P);
        BigInteger x=lambda.multiply(lambda).subtract(a.x()).subtract(b.x()).mod(P);
        BigInteger y=lambda.multiply(a.x().subtract(x)).subtract(a.y()).mod(P);
        return new Point(x,y);
    }
    static byte[] fixed32(BigInteger value){
        byte[] raw=value.toByteArray();byte[] out=new byte[32];
        int copy=Math.min(32,raw.length);System.arraycopy(raw,raw.length-copy,out,32-copy,copy);
        return out;
    }
    private Secp256k1(){}
}
