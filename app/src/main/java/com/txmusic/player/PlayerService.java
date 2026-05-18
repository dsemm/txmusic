package com.txmusic.player;

import android.app.*;
import android.content.*;
import android.graphics.*;
import android.media.*;
import android.media.session.MediaSession;
import android.net.Uri;
import android.os.*;
import java.util.*;

public class PlayerService extends Service {
    public static final String ACTION_PLAY_INDEX="play_index", ACTION_TOGGLE="toggle", ACTION_NEXT="next", ACTION_PREV="prev", ACTION_REPEAT="repeat", ACTION_SHUFFLE="shuffle", ACTION_FAV="fav", ACTION_SEEK="seek", ACTION_STATE="txmusic_state";
    MediaPlayer mp; Handler handler = new Handler(Looper.getMainLooper());
    boolean shuffle=false; int repeat=0; Random random=new Random(); MediaSession session;

    Runnable ticker = new Runnable(){ public void run(){ broadcast(); handler.postDelayed(this, 1000); }};
    public void onCreate(){ super.onCreate(); createChannel(); session=new MediaSession(this,"TXMusic"); session.setActive(true); }
    public int onStartCommand(Intent i, int flags, int id){ if(i!=null){ String a=i.getAction(); if(ACTION_PLAY_INDEX.equals(a)){ play(i.getIntExtra("index",0)); } else if(ACTION_TOGGLE.equals(a)){ toggle(); } else if(ACTION_NEXT.equals(a)){ next(); } else if(ACTION_PREV.equals(a)){ prev(); } else if(ACTION_REPEAT.equals(a)){ repeat=(repeat+1)%3; notifyNow(); broadcast(); } else if(ACTION_SHUFFLE.equals(a)){ shuffle=!shuffle; notifyNow(); broadcast(); } else if(ACTION_FAV.equals(a)){ Song s=current(); if(s!=null) MusicStore.toggleFav(this,s); notifyNow(); broadcast(); } else if(ACTION_SEEK.equals(a)){ if(mp!=null) mp.seekTo(i.getIntExtra("pos",0)); broadcast(); }} return START_STICKY; }
    public android.os.IBinder onBind(Intent intent){ return null; }
    Song current(){ if(MusicStore.currentIndex>=0 && MusicStore.currentIndex<MusicStore.queue.size()) return MusicStore.queue.get(MusicStore.currentIndex); return null; }
    void play(int index){ if(MusicStore.queue.isEmpty()) return; if(index<0) index=0; if(index>=MusicStore.queue.size()) index=0; MusicStore.currentIndex=index; Song s=current(); try{ if(mp!=null){ mp.release(); } mp=new MediaPlayer(); mp.setDataSource(this, Uri.parse(s.uri)); mp.setAudioStreamType(AudioManager.STREAM_MUSIC); mp.setOnCompletionListener(m->{ if(repeat==2) play(MusicStore.currentIndex); else next(); }); mp.prepare(); mp.start(); MusicStore.addRecent(this,s); handler.removeCallbacks(ticker); handler.post(ticker); startForeground(77, notification()); broadcast(); }catch(Exception e){ next(); } }
    void toggle(){ if(mp==null){ play(Math.max(0,MusicStore.currentIndex)); return; } if(mp.isPlaying()) mp.pause(); else mp.start(); notifyNow(); broadcast(); }
    void next(){ if(MusicStore.queue.isEmpty()) return; int n = shuffle ? random.nextInt(MusicStore.queue.size()) : MusicStore.currentIndex+1; if(n>=MusicStore.queue.size()){ if(repeat==1) n=0; else { if(mp!=null) mp.pause(); notifyNow(); broadcast(); return; } } play(n); }
    void prev(){ if(MusicStore.queue.isEmpty()) return; int n = MusicStore.currentIndex-1; if(n<0) n=MusicStore.queue.size()-1; play(n); }
    void broadcast(){ Intent b=new Intent(ACTION_STATE); Song s=current(); b.putExtra("playing", mp!=null && mp.isPlaying()); b.putExtra("index", MusicStore.currentIndex); b.putExtra("pos", mp==null?0:mp.getCurrentPosition()); b.putExtra("dur", mp==null?0:mp.getDuration()); b.putExtra("shuffle", shuffle); b.putExtra("repeat", repeat); if(s!=null){ b.putExtra("title", MusicStore.meta(this,s,"title",s.title)); b.putExtra("artist", MusicStore.meta(this,s,"artist",s.artist)); b.putExtra("fav", MusicStore.fav(this,s)); b.putExtra("cover", MusicStore.meta(this,s,"cover", "")); b.putExtra("uri", s.uri); } sendBroadcast(b); }
    void notifyNow(){ NotificationManager nm=(NotificationManager)getSystemService(NOTIFICATION_SERVICE); nm.notify(77, notification()); }
    PendingIntent pi(String action){ Intent i=new Intent(this, PlayerService.class); i.setAction(action); return PendingIntent.getService(this, action.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); }
    PendingIntent openAppIntent(){ Intent i=new Intent(this, MainActivity.class); i.putExtra("open_now", true); i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP|Intent.FLAG_ACTIVITY_CLEAR_TOP); return PendingIntent.getActivity(this, 770, i, PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE); }
    Notification notification(){ Song s=current(); String title=s==null?"TXMusic":MusicStore.meta(this,s,"title",s.title); String artist=s==null?"Player premium":MusicStore.meta(this,s,"artist",s.artist); boolean playing=mp!=null&&mp.isPlaying(); Bitmap art=artwork(s); Notification.Action aPrev=new Notification.Action.Builder(R.drawable.ic_prev,"Voltar",pi(ACTION_PREV)).build(); Notification.Action aPlay=new Notification.Action.Builder(playing?R.drawable.ic_pause:R.drawable.ic_play,playing?"Pausar":"Tocar",pi(ACTION_TOGGLE)).build(); Notification.Action aNext=new Notification.Action.Builder(R.drawable.ic_next,"Pular",pi(ACTION_NEXT)).build(); Notification.Action aFav=new Notification.Action.Builder(s!=null&&MusicStore.fav(this,s)?R.drawable.ic_favorite:R.drawable.ic_favorite_border,"Favoritar",pi(ACTION_FAV)).build(); Notification.Action aRep=new Notification.Action.Builder(repeat==2?R.drawable.ic_repeat_one:R.drawable.ic_repeat,"Repetir",pi(ACTION_REPEAT)).build(); Notification.Builder nb = Build.VERSION.SDK_INT>=26 ? new Notification.Builder(this, "txmusic") : new Notification.Builder(this); nb.setSmallIcon(R.drawable.ic_music).setContentTitle(title).setContentText(artist).setLargeIcon(art).setContentIntent(openAppIntent()).setOngoing(playing).addAction(aRep).addAction(aPrev).addAction(aPlay).addAction(aNext).addAction(aFav).setStyle(new Notification.MediaStyle().setMediaSession(session.getSessionToken()).setShowActionsInCompactView(1,2,3)); return nb.build(); }
    Bitmap squareBitmap(Bitmap bm){ if(bm==null) return null; int side=Math.min(bm.getWidth(),bm.getHeight()); int x=(bm.getWidth()-side)/2, y=(bm.getHeight()-side)/2; Bitmap cropped=Bitmap.createBitmap(bm,x,y,side,side); return Bitmap.createScaledBitmap(cropped,512,512,true); }
    Bitmap artwork(Song s){
        try{
            if(s!=null){
                String cu=MusicStore.meta(this,s,"cover","");
                if(cu.length()>0){ Bitmap x=android.provider.MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.parse(cu)); if(x!=null) return squareBitmap(x); }
                MediaMetadataRetriever mmr=new MediaMetadataRetriever(); mmr.setDataSource(this, Uri.parse(s.uri)); byte[] data=mmr.getEmbeddedPicture(); mmr.release(); if(data!=null){ Bitmap bm=BitmapFactory.decodeByteArray(data,0,data.length); if(bm!=null) return squareBitmap(bm); }
            }
        }catch(Exception ignored){}
        Bitmap b=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(b); Paint p=new Paint(1); LinearGradient g=new LinearGradient(0,0,512,512,Color.rgb(168,85,247),Color.rgb(49,46,129),Shader.TileMode.CLAMP); p.setShader(g); c.drawRect(0,0,512,512,p); p.setShader(null); p.setColor(Color.argb(230,255,255,255)); p.setTextAlign(Paint.Align.CENTER); p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD)); p.setTextSize(92); c.drawText("TX",256,285,p); p.setTextSize(32); c.drawText("MUSIC",256,335,p); return b; }
    void createChannel(){ if(Build.VERSION.SDK_INT>=26){ NotificationChannel c=new NotificationChannel("txmusic","TXMusic Player",NotificationManager.IMPORTANCE_LOW); c.setDescription("Controles de música em segundo plano"); ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c); } }
    public void onDestroy(){ handler.removeCallbacks(ticker); if(mp!=null) mp.release(); if(session!=null) session.release(); super.onDestroy(); }
}
