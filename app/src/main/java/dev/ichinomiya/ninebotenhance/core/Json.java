package dev.ichinomiya.ninebotenhance.core;

import java.util.*;

/** Minimal JSON reader for the dashboard cast configuration: objects become maps, arrays lists, numbers doubles. No Android dependency. */
public final class Json {
    private final String text;
    private int at;
    private Json(String text) { this.text=text; }
    public static Object parse(String text) {
        if(text==null)throw new IllegalArgumentException("empty json");
        Json json=new Json(text);Object value=json.value();json.skip();
        if(json.at!=text.length())throw new IllegalArgumentException("trailing data at "+json.at);
        return value;
    }
    @SuppressWarnings("unchecked") public static Map<String,Object> object(Object value) { return value instanceof Map?(Map<String,Object>)value:null; }
    @SuppressWarnings("unchecked") public static List<Object> array(Object value) { return value instanceof List?(List<Object>)value:null; }
    public static double number(Object value,double fallback) { return value instanceof Double?(Double)value:fallback; }
    public static String string(Object value) { return value instanceof String?(String)value:null; }
    /** Nested lookup: {@code get(root,"layout","baseMap","frame")}; null when any step is missing. */
    public static Object get(Object root,String... path) {
        Object current=root;
        for(String key:path) { Map<String,Object> map=object(current);if(map==null)return null;current=map.get(key); }
        return current;
    }
    private Object value() {
        skip();
        if(at>=text.length())throw new IllegalArgumentException("unexpected end");
        char c=text.charAt(at);
        switch(c) {
            case '{': return objectValue();
            case '[': return arrayValue();
            case '"': return stringValue();
            case 't': expect("true");return Boolean.TRUE;
            case 'f': expect("false");return Boolean.FALSE;
            case 'n': expect("null");return null;
            default: return numberValue();
        }
    }
    private Map<String,Object> objectValue() {
        LinkedHashMap<String,Object> map=new LinkedHashMap<>();at++;skip();
        if(peek()=='}'){at++;return map;}
        while(true) {
            skip();if(peek()!='"')throw new IllegalArgumentException("key expected at "+at);
            String key=stringValue();skip();
            if(peek()!=':')throw new IllegalArgumentException("colon expected at "+at);at++;
            map.put(key,value());skip();
            char c=peek();at++;
            if(c=='}')return map;
            if(c!=',')throw new IllegalArgumentException("comma expected at "+(at-1));
        }
    }
    private List<Object> arrayValue() {
        ArrayList<Object> list=new ArrayList<>();at++;skip();
        if(peek()==']'){at++;return list;}
        while(true) {
            list.add(value());skip();
            char c=peek();at++;
            if(c==']')return list;
            if(c!=',')throw new IllegalArgumentException("comma expected at "+(at-1));
        }
    }
    private String stringValue() {
        StringBuilder out=new StringBuilder();at++;
        while(true) {
            if(at>=text.length())throw new IllegalArgumentException("unterminated string");
            char c=text.charAt(at++);
            if(c=='"')return out.toString();
            if(c!='\\'){out.append(c);continue;}
            if(at>=text.length())throw new IllegalArgumentException("bad escape");
            char e=text.charAt(at++);
            switch(e) {
                case '"': out.append('"');break; case '\\': out.append('\\');break; case '/': out.append('/');break;
                case 'b': out.append('\b');break; case 'f': out.append('\f');break; case 'n': out.append('\n');break;
                case 'r': out.append('\r');break; case 't': out.append('\t');break;
                case 'u':
                    if(at+4>text.length())throw new IllegalArgumentException("bad unicode escape");
                    out.append((char)Integer.parseInt(text.substring(at,at+4),16));at+=4;break;
                default: throw new IllegalArgumentException("bad escape \\"+e);
            }
        }
    }
    private Double numberValue() {
        int start=at;
        while(at<text.length()&&"+-0123456789.eE".indexOf(text.charAt(at))>=0)at++;
        if(start==at)throw new IllegalArgumentException("unexpected character at "+at);
        try { return Double.valueOf(text.substring(start,at)); }
        catch(NumberFormatException e) { throw new IllegalArgumentException("bad number at "+start); }
    }
    private void expect(String word) {
        if(!text.startsWith(word,at))throw new IllegalArgumentException("unexpected token at "+at);
        at+=word.length();
    }
    private char peek() { if(at>=text.length())throw new IllegalArgumentException("unexpected end");return text.charAt(at); }
    private void skip() { while(at<text.length()&&Character.isWhitespace(text.charAt(at)))at++; }
}
