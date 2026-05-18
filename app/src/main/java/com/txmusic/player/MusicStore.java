package com.txmusic.player;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.*;

public class MusicStore {
    public static final String PREF = "txmusic";
    public static ArrayList<Song> queue = new ArrayList<>();
    public static int currentIndex = -1;

    static String key(String uri, String field){ return uri + "::" + field; }
    public static String meta(Context c, Song s, String f, String def){ return c.getSharedPreferences(PREF,0).getString(key(s.uri,f), def); }
    public static void setMeta(Context c, Song s, String f, String v){ c.getSharedPreferences(PREF,0).edit().putString(key(s.uri,f), v == null ? "" : v).apply(); }
    public static boolean fav(Context c, Song s){ return c.getSharedPreferences(PREF,0).getBoolean(key(s.uri,"fav"), false); }
    public static void toggleFav(Context c, Song s){ SharedPreferences p=c.getSharedPreferences(PREF,0); p.edit().putBoolean(key(s.uri,"fav"), !p.getBoolean(key(s.uri,"fav"),false)).apply(); }
    public static long minBytes(Context c){ return c.getSharedPreferences(PREF,0).getLong("minBytes", 512*1024); }
    public static void setMinBytes(Context c,long b){ c.getSharedPreferences(PREF,0).edit().putLong("minBytes",Math.max(0,b)).apply(); }
    public static boolean light(Context c){ return c.getSharedPreferences(PREF,0).getBoolean("light", false); }
    public static void setLight(Context c, boolean b){ c.getSharedPreferences(PREF,0).edit().putBoolean("light",b).apply(); }

    public static ArrayList<String> playlistNames(Context c){
        String raw=c.getSharedPreferences(PREF,0).getString("playlist_names", "Minha Playlist");
        ArrayList<String> out=new ArrayList<>();
        if(raw!=null) for(String n: raw.split("\\n")){ n=n.trim(); if(n.length()>0 && !out.contains(n)) out.add(n); }
        if(out.isEmpty()) out.add("Minha Playlist");
        return out;
    }
    public static void savePlaylistNames(Context c, ArrayList<String> names){
        LinkedHashSet<String> uniq=new LinkedHashSet<>();
        for(String n:names){ if(n!=null){ n=n.trim(); if(n.length()>0) uniq.add(n); }}
        if(uniq.isEmpty()) uniq.add("Minha Playlist");
        StringBuilder sb=new StringBuilder(); for(String n:uniq){ if(sb.length()>0) sb.append('\n'); sb.append(n); }
        c.getSharedPreferences(PREF,0).edit().putString("playlist_names",sb.toString()).apply();
    }
    public static void addPlaylistName(Context c, String name){ ArrayList<String> names=playlistNames(c); if(name!=null && name.trim().length()>0 && !names.contains(name.trim())){ names.add(name.trim()); savePlaylistNames(c,names); } }
    public static HashSet<String> playlist(Context c, String name){ return new HashSet<>(c.getSharedPreferences(PREF,0).getStringSet("pl_"+name, new HashSet<String>())); }
    public static void savePlaylist(Context c, String name, Set<String> set){ addPlaylistName(c,name); c.getSharedPreferences(PREF,0).edit().putStringSet("pl_"+name, new HashSet<>(set)).apply(); }
    public static boolean inAnyPlaylist(Context c, Song s){ for(String n:playlistNames(c)) if(playlist(c,n).contains(s.uri)) return true; return false; }

    public static void addRecent(Context c, Song s){
        if(s == null) return;
        SharedPreferences p = c.getSharedPreferences(PREF,0);
        String old = p.getString("recent", "");
        ArrayList<String> list = new ArrayList<>();
        list.add(s.uri);
        if(old != null && old.length() > 0){
            for(String item : old.split("\\n")){
                if(item.length() > 0 && !item.equals(s.uri) && !list.contains(item)) list.add(item);
                if(list.size() >= 80) break;
            }
        }
        StringBuilder sb = new StringBuilder();
        for(String item : list){ if(sb.length() > 0) sb.append('\n'); sb.append(item); }
        p.edit().putString("recent", sb.toString()).apply();
    }

    public static ArrayList<String> recent(Context c){
        String old = c.getSharedPreferences(PREF,0).getString("recent", "");
        ArrayList<String> out = new ArrayList<>();
        if(old != null && old.length() > 0){
            for(String item : old.split("\\n")) if(item.length() > 0 && !out.contains(item)) out.add(item);
        }
        return out;
    }
}
