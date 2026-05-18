package com.txmusic.player;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.*;
import android.widget.*;
import java.util.*;

public class MainActivity extends Activity {
    ArrayList<Song> all = new ArrayList<>(), shown = new ArrayList<>();
    HashMap<String, Drawable> coverCache = new HashMap<>();
    SongAdapter adapter; LinearLayout root, tabBar, shortcutBar, mini, nowPanel; ListView list; Song pendingCoverSong;
    TextView miniTitle, miniSub, timeLeft, timeRight, bigTitle, bigSub, header, empty, settingsMinLabel;
    ImageView miniCover, bigCover, splashImg;
    SeekBar seek; ImageButton miniPlayBtn, miniPrevBtn, miniNextBtn, nowPlayBtn, nowPrevBtn, nowNextBtn, repeatBtn, playlistBtn, favBtn, settingsBtn, backBtn;
    EditText search; int currentTab=0; boolean nowOpen=false, dragging=false; String query=""; String openedPlaylist=null;
    final String[] tabs={"Músicas","Recentes","Artistas","Álbuns","Favoritos","Playlists"};
    int bg, card, text, sub, purple, stroke, chip;
    float coverDownX, coverDownY, pullDownY; boolean pullArmed=false;

    BroadcastReceiver stateReceiver = new BroadcastReceiver(){ public void onReceive(Context c, Intent i){ updateState(i); }};

    public void onCreate(Bundle b){
        super.onCreate(b); colors(); build(); splash(); handleIntent(getIntent());
        if(Build.VERSION.SDK_INT>=33) registerReceiver(stateReceiver, new IntentFilter(PlayerService.ACTION_STATE), RECEIVER_NOT_EXPORTED); else registerReceiver(stateReceiver, new IntentFilter(PlayerService.ACTION_STATE));
        askPerms();
    }
    public void onNewIntent(Intent i){ super.onNewIntent(i); setIntent(i); handleIntent(i); }
    public void onResume(){ super.onResume(); if(root!=null) load(); }
    void handleIntent(Intent i){ if(i!=null && i.getBooleanExtra("open_now", false)) new Handler(Looper.getMainLooper()).postDelayed(()->openNow(),160); }

    void colors(){ boolean light=MusicStore.light(this); bg=Color.rgb(light?246:10, light?242:7, light?255:22); card=Color.rgb(light?255:30, light?255:24, light?255:45); chip=Color.rgb(light?244:44, light?235:36, light?255:64); text=light?Color.rgb(25,21,35):Color.WHITE; sub=light?Color.rgb(92,82,110):Color.rgb(188,178,205); purple=Color.rgb(168,85,247); stroke=light?Color.argb(60,80,50,120):Color.argb(45,255,255,255); }
    int dp(float v){ return (int)(v*getResources().getDisplayMetrics().density+0.5f); }
    GradientDrawable round(int color, float r){ GradientDrawable g=new GradientDrawable(); g.setColor(color); g.setCornerRadius(dp(r)); g.setStroke(dp(1), stroke); return g; }
    GradientDrawable grad(){ GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(126,34,206),Color.rgb(168,85,247),Color.rgb(49,46,129)}); g.setCornerRadius(dp(30)); return g; }
    TextView tv(String s,int sp,int style){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTypeface(Typeface.create("sans-serif", style)); v.setTextColor(text); return v; }
    TextView marquee(String s,int sp,int style){ LoopTextView v=new LoopTextView(this); v.setText(s); v.setTextSize(sp); v.setTypeface(Typeface.create("sans-serif", style)); v.setTextColor(text); v.setSingleLine(true); v.setEllipsize(null); return v; }
    ImageButton icon(int res, int tint, float pad){ ImageButton b=new ImageButton(this); b.setImageResource(res); b.setColorFilter(tint); b.setScaleType(ImageView.ScaleType.CENTER); b.setPadding(dp(pad),dp(pad),dp(pad),dp(pad)); b.setBackgroundColor(Color.TRANSPARENT); b.setFocusable(false); return b; }

    void build(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(16),dp(14),dp(16),dp(10)); root.setBackgroundColor(bg); setContentView(root);
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); top.setOrientation(LinearLayout.HORIZONTAL); root.addView(top,new LinearLayout.LayoutParams(-1,dp(58)));
        header=tv("TXMusic",32,Typeface.BOLD); header.setLetterSpacing(0.02f); top.addView(header,new LinearLayout.LayoutParams(0,-1,1));
        settingsBtn=icon(R.drawable.ic_settings,purple,10); top.addView(settingsBtn,new LinearLayout.LayoutParams(dp(48),dp(44))); settingsBtn.setOnClickListener(v->settings());

        search=new EditText(this); search.setSingleLine(true); search.setHint("Buscar música, artista ou álbum"); search.setHintTextColor(sub); search.setTextColor(text); search.setTextSize(14); search.setPadding(dp(16),0,dp(16),0); search.setBackground(round(card,22)); root.addView(search,new LinearLayout.LayoutParams(-1,dp(48))); search.addTextChangedListener(new android.text.TextWatcher(){ public void beforeTextChanged(CharSequence s,int st,int c,int a){} public void onTextChanged(CharSequence s,int st,int b,int c){ query=s.toString().toLowerCase(); apply(); } public void afterTextChanged(android.text.Editable e){} });
        shortcutBar=new LinearLayout(this); shortcutBar.setOrientation(LinearLayout.HORIZONTAL); shortcutBar.setGravity(Gravity.CENTER); root.addView(shortcutBar,new LinearLayout.LayoutParams(-1,dp(70))); buildShortcuts();

        HorizontalScrollView hsv=new HorizontalScrollView(this); hsv.setHorizontalScrollBarEnabled(false); tabBar=new LinearLayout(this); tabBar.setOrientation(LinearLayout.HORIZONTAL); hsv.addView(tabBar); root.addView(hsv,new LinearLayout.LayoutParams(-1,dp(58))); buildTabs();

        FrameLayout frame=new FrameLayout(this); root.addView(frame,new LinearLayout.LayoutParams(-1,0,1));
        list=new ListView(this); list.setDivider(null); list.setCacheColorHint(Color.TRANSPARENT); list.setBackgroundColor(Color.TRANSPARENT); adapter=new SongAdapter(); list.setAdapter(adapter); frame.addView(list,new FrameLayout.LayoutParams(-1,-1));
        empty=tv("Nenhuma música encontrada. Ajuste o tamanho mínimo nas configurações ou verifique a permissão de áudio.",15,Typeface.NORMAL); empty.setGravity(Gravity.CENTER); empty.setPadding(dp(20),dp(20),dp(20),dp(20)); frame.addView(empty,new FrameLayout.LayoutParams(-1,-1));
        list.setOnItemClickListener((p,v,pos,id)->clickItem(pos));
        list.setOnTouchListener((v,e)->pullRefresh(e));
        buildMini(); buildNowPanel(); apply();
    }

    void splash(){
        final FrameLayout sp=new FrameLayout(this); sp.setBackgroundColor(bg); sp.setClickable(true);
        splashImg=new ImageView(this); splashImg.setImageResource(R.drawable.txmusic_splash); splashImg.setAdjustViewBounds(true); splashImg.setScaleType(ImageView.ScaleType.FIT_CENTER);
        FrameLayout.LayoutParams ilp=new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER); ilp.setMargins(dp(28),dp(28),dp(28),dp(28)); sp.addView(splashImg,ilp);
        addContentView(sp,new ViewGroup.LayoutParams(-1,-1)); sp.setAlpha(0f); sp.animate().alpha(1f).setDuration(180).withEndAction(()->new Handler(Looper.getMainLooper()).postDelayed(()->sp.animate().alpha(0f).setDuration(260).withEndAction(()->{ try{ ((ViewGroup)sp.getParent()).removeView(sp); }catch(Exception ignored){} }).start(),850)).start();
    }

    void buildMini(){
        mini=new LinearLayout(this); mini.setOrientation(LinearLayout.HORIZONTAL); mini.setGravity(Gravity.CENTER_VERTICAL); mini.setPadding(dp(10),dp(8),dp(8),dp(8)); mini.setBackground(round(card,28)); root.addView(mini,new LinearLayout.LayoutParams(-1,dp(78))); mini.setOnClickListener(v->openNow());
        miniCover=new ImageView(this); miniCover.setScaleType(ImageView.ScaleType.CENTER_CROP); miniCover.setImageDrawable(defaultCoverDrawable()); miniCover.setBackground(grad()); clipRound(miniCover,16); mini.addView(miniCover,new LinearLayout.LayoutParams(dp(58),dp(58)));
        LinearLayout mt=new LinearLayout(this); mt.setOrientation(LinearLayout.VERTICAL); mt.setPadding(dp(12),0,0,0); mini.addView(mt,new LinearLayout.LayoutParams(0,-1,1)); miniTitle=marquee("Toque uma música",15,Typeface.BOLD); miniSub=marquee("TXMusic está pronto",12,Typeface.NORMAL); miniSub.setTextColor(sub); mt.addView(miniTitle); mt.addView(miniSub);
        miniPrevBtn=icon(R.drawable.ic_prev,sub,10); miniPlayBtn=icon(R.drawable.ic_play,Color.WHITE,8); miniNextBtn=icon(R.drawable.ic_next,sub,10); mini.addView(miniPrevBtn,new LinearLayout.LayoutParams(dp(40),dp(40))); mini.addView(miniPlayBtn,new LinearLayout.LayoutParams(dp(48),dp(48))); mini.addView(miniNextBtn,new LinearLayout.LayoutParams(dp(40),dp(40)));
        miniPrevBtn.setOnClickListener(v->cmd(PlayerService.ACTION_PREV)); miniPlayBtn.setOnClickListener(v->cmd(PlayerService.ACTION_TOGGLE)); miniNextBtn.setOnClickListener(v->cmd(PlayerService.ACTION_NEXT));
    }

    void buildTabs(){ tabBar.removeAllViews(); int[] mainTabs={0,2,3}; for(int k=0;k<mainTabs.length;k++){ final int ix=mainTabs[k]; TextView t=tv(tabs[ix],14,Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(dp(18),0,dp(18),0); t.setTextColor(ix==currentTab?Color.WHITE:sub); t.setBackground(round(ix==currentTab?purple:Color.TRANSPARENT,12)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,dp(40)); lp.setMargins(0,dp(9),dp(8),0); tabBar.addView(t,lp); t.setOnClickListener(v->{ currentTab=ix; openedPlaylist=null; buildTabs(); buildShortcuts(); apply(); }); } }
    void buildShortcuts(){ if(shortcutBar==null) return; shortcutBar.removeAllViews(); addShortcut(R.drawable.ic_favorite,"Favoritos",4); addShortcut(R.drawable.ic_playlist,"Playlists",5); addShortcut(R.drawable.ic_queue,"Recentes",1); }
    void addShortcut(int iconRes,String label,int tab){ LinearLayout b=new LinearLayout(this); b.setGravity(Gravity.CENTER); b.setPadding(dp(8),0,dp(8),0); b.setBackground(round(currentTab==tab?purple:chip,12)); ImageView ic=new ImageView(this); ic.setImageResource(iconRes); ic.setColorFilter(currentTab==tab?Color.WHITE:purple); b.addView(ic,new LinearLayout.LayoutParams(dp(22),dp(22))); TextView t=tv(label,13,Typeface.BOLD); t.setTextColor(currentTab==tab?Color.WHITE:text); t.setPadding(dp(7),0,0,0); b.addView(t,new LinearLayout.LayoutParams(-2,-1)); LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(48),1); lp.setMargins(dp(4),dp(11),dp(4),0); shortcutBar.addView(b,lp); b.setOnClickListener(v->{ currentTab=tab; openedPlaylist=null; buildTabs(); buildShortcuts(); apply(); }); }

    void buildNowPanel(){
        nowPanel=new LinearLayout(this); nowPanel.setOrientation(LinearLayout.VERTICAL); nowPanel.setPadding(dp(22),dp(18),dp(22),dp(18)); GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{Color.rgb(65,22,105),bg}); nowPanel.setBackground(g); nowPanel.setVisibility(View.GONE); addContentView(nowPanel,new ViewGroup.LayoutParams(-1,-1));
        LinearLayout top=new LinearLayout(this); top.setGravity(Gravity.CENTER_VERTICAL); nowPanel.addView(top,new LinearLayout.LayoutParams(-1,dp(48)));
        backBtn=icon(R.drawable.ic_down,MusicStore.light(this)?Color.rgb(20,20,25):Color.WHITE,10); top.addView(backBtn,new LinearLayout.LayoutParams(dp(48),dp(44))); backBtn.setOnClickListener(v->closeNow());
        TextView title=tv("TOCANDO AGORA",13,Typeface.BOLD); title.setTextColor(sub); title.setGravity(Gravity.CENTER); title.setLetterSpacing(0.12f); top.addView(title,new LinearLayout.LayoutParams(0,-1,1));
        ImageButton more=icon(R.drawable.ic_more_vert,MusicStore.light(this)?Color.rgb(20,20,25):Color.WHITE,10); top.addView(more,new LinearLayout.LayoutParams(dp(48),dp(44))); more.setOnClickListener(v->{ Song s=currentSong(); if(s!=null) showOptions(s); });

        bigCover=new ImageView(this); bigCover.setScaleType(ImageView.ScaleType.CENTER_CROP); bigCover.setImageDrawable(defaultCoverDrawable()); bigCover.setBackground(grad()); clipRound(bigCover,24); LinearLayout.LayoutParams alp=new LinearLayout.LayoutParams(-1,0,1.15f); alp.setMargins(0,dp(18),0,dp(24)); nowPanel.addView(bigCover,alp); bigCover.setOnTouchListener((v,e)->coverGesture(e));
        LinearLayout songRow=new LinearLayout(this); songRow.setGravity(Gravity.CENTER_VERTICAL); songRow.setOrientation(LinearLayout.HORIZONTAL); nowPanel.addView(songRow,new LinearLayout.LayoutParams(-1,dp(46)));
        LinearLayout titleCol=new LinearLayout(this); titleCol.setOrientation(LinearLayout.VERTICAL); songRow.addView(titleCol,new LinearLayout.LayoutParams(0,-1,1));
        bigTitle=marquee("Nada tocando",25,Typeface.BOLD); bigSub=marquee("Escolha uma música",15,Typeface.NORMAL); bigSub.setTextColor(sub); titleCol.addView(bigTitle,new LinearLayout.LayoutParams(-1,dp(28))); titleCol.addView(bigSub,new LinearLayout.LayoutParams(-1,dp(20)));
        favBtn=icon(R.drawable.ic_favorite_border,Color.WHITE,8); songRow.addView(favBtn,new LinearLayout.LayoutParams(dp(48),dp(48))); favBtn.setOnClickListener(v->cmd(PlayerService.ACTION_FAV));
        seek=new SeekBar(this); seek.setPadding(0,dp(10),0,0); tintSeek(seek); nowPanel.addView(seek,new LinearLayout.LayoutParams(-1,dp(58))); seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onStartTrackingTouch(SeekBar s){ dragging=true; } public void onStopTrackingTouch(SeekBar s){ dragging=false; Intent x=new Intent(MainActivity.this,PlayerService.class); x.setAction(PlayerService.ACTION_SEEK); x.putExtra("pos",s.getProgress()); startService(x); } public void onProgressChanged(SeekBar s,int p,boolean f){} });
        LinearLayout times=new LinearLayout(this); timeLeft=tv("0:00",12,Typeface.NORMAL); timeRight=tv("0:00",12,Typeface.NORMAL); timeLeft.setTextColor(sub); timeRight.setTextColor(sub); times.addView(timeLeft,new LinearLayout.LayoutParams(0,dp(22),1)); times.addView(timeRight,new LinearLayout.LayoutParams(0,dp(22),1)); timeRight.setGravity(Gravity.RIGHT); nowPanel.addView(times);
        LinearLayout controls=new LinearLayout(this); controls.setGravity(Gravity.CENTER); controls.setPadding(0,dp(14),0,0); nowPanel.addView(controls,new LinearLayout.LayoutParams(-1,dp(102)));
        int playerIcon=MusicStore.light(this)?Color.rgb(20,20,25):Color.WHITE; int playBg=MusicStore.light(this)?Color.rgb(20,20,25):Color.WHITE; int playIcon=MusicStore.light(this)?Color.WHITE:Color.rgb(12,8,20);
        playlistBtn=icon(R.drawable.ic_playlist,playerIcon,9); nowPrevBtn=icon(R.drawable.ic_prev,playerIcon,9); nowPlayBtn=icon(R.drawable.ic_play,playIcon,12); nowNextBtn=icon(R.drawable.ic_next,playerIcon,9); repeatBtn=icon(R.drawable.ic_repeat,playerIcon,9); nowPlayBtn.setBackground(round(playBg,42));
        controls.addView(playlistBtn,new LinearLayout.LayoutParams(dp(54),dp(54))); controls.addView(nowPrevBtn,new LinearLayout.LayoutParams(dp(60),dp(60))); controls.addView(nowPlayBtn,new LinearLayout.LayoutParams(dp(84),dp(84))); controls.addView(nowNextBtn,new LinearLayout.LayoutParams(dp(60),dp(60))); controls.addView(repeatBtn,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout.LayoutParams mlp; for(int i=0;i<controls.getChildCount();i++){ mlp=(LinearLayout.LayoutParams)controls.getChildAt(i).getLayoutParams(); mlp.setMargins(dp(4),0,dp(4),0); }
        repeatBtn.setOnClickListener(v->cmd(PlayerService.ACTION_REPEAT)); playlistBtn.setOnClickListener(v->{ Song s=currentSong(); if(s!=null) choosePlaylistForSong(s); }); nowPrevBtn.setOnClickListener(v->cmd(PlayerService.ACTION_PREV)); nowPlayBtn.setOnClickListener(v->cmd(PlayerService.ACTION_TOGGLE)); nowNextBtn.setOnClickListener(v->cmd(PlayerService.ACTION_NEXT));
    }

    boolean coverGesture(MotionEvent e){
        if(e.getAction()==MotionEvent.ACTION_DOWN){ coverDownX=e.getX(); coverDownY=e.getY(); bigCover.clearAnimation(); return true; }
        if(e.getAction()==MotionEvent.ACTION_MOVE){ float dx=e.getX()-coverDownX; float dy=e.getY()-coverDownY; if(Math.abs(dx)>Math.abs(dy)){ bigCover.setTranslationX(dx*0.72f); bigCover.setRotation(dx/70f); bigCover.setAlpha(Math.max(0.78f,1f-Math.abs(dx)/850f)); } return true; }
        if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL){ float dx=e.getX()-coverDownX, dy=e.getY()-coverDownY; if(Math.abs(dx)>dp(58) && Math.abs(dx)>Math.abs(dy)*1.35f){ final boolean next=dx<0; bigCover.animate().translationX(next?-bigCover.getWidth()*1.05f:bigCover.getWidth()*1.05f).rotation(next?-9:9).alpha(0.22f).setDuration(120).withEndAction(()->{ cmd(next?PlayerService.ACTION_NEXT:PlayerService.ACTION_PREV); bigCover.setTranslationX(next?bigCover.getWidth()*0.85f:-bigCover.getWidth()*0.85f); bigCover.setRotation(next?7:-7); bigCover.animate().translationX(0).rotation(0).alpha(1f).setDuration(150).start(); }).start(); } else bigCover.animate().translationX(0).rotation(0).alpha(1f).setDuration(120).start(); return true; }
        return true;
    }

    Drawable defaultCoverDrawable(){ Bitmap b=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888); Canvas c=new Canvas(b); Paint p=new Paint(1); LinearGradient lg=new LinearGradient(0,0,512,512,Color.rgb(168,85,247),Color.rgb(49,46,129),Shader.TileMode.CLAMP); p.setShader(lg); c.drawRoundRect(new RectF(0,0,512,512),42,42,p); p.setShader(null); p.setTextAlign(Paint.Align.CENTER); p.setColor(Color.argb(235,255,255,255)); p.setTypeface(Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD)); p.setTextSize(90); c.drawText("TX",256,250,p); p.setTextSize(38); c.drawText("MUSIC",256,315,p); return new BitmapDrawable(getResources(),b); }
    Drawable coverFor(Song s){
        if(s==null || s.uri.startsWith("group:")) return defaultCoverDrawable();
        String ck=s.uri+"|"+MusicStore.meta(this,s,"cover",""); if(coverCache.containsKey(ck)) return coverCache.get(ck);
        Drawable out=null;
        try{ String cu=MusicStore.meta(this,s,"cover",""); if(cu.length()>0){ Bitmap x=MediaStore.Images.Media.getBitmap(getContentResolver(), Uri.parse(cu)); if(x!=null) out=new BitmapDrawable(getResources(),squareBitmap(x)); } }catch(Exception ignored){}
        if(out==null){ try{ MediaMetadataRetriever mmr=new MediaMetadataRetriever(); mmr.setDataSource(this,Uri.parse(s.uri)); byte[] data=mmr.getEmbeddedPicture(); mmr.release(); if(data!=null){ Bitmap bm=BitmapFactory.decodeByteArray(data,0,data.length); if(bm!=null) out=new BitmapDrawable(getResources(),squareBitmap(bm)); } }catch(Exception ignored){} }
        if(out==null) out=defaultCoverDrawable(); coverCache.put(ck,out); return out;
    }


    void tintSeek(SeekBar s){ try{ int active=purple; int base=MusicStore.light(this)?Color.WHITE:Color.argb(95,255,255,255); s.setProgressTintList(android.content.res.ColorStateList.valueOf(active)); s.setThumbTintList(android.content.res.ColorStateList.valueOf(active)); s.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(base)); }catch(Exception ignored){} }
    boolean pullRefresh(MotionEvent e){ if(e.getAction()==MotionEvent.ACTION_DOWN){ pullDownY=e.getY(); pullArmed=list.getFirstVisiblePosition()==0; return false; } if(e.getAction()==MotionEvent.ACTION_MOVE && pullArmed && list.getChildCount()>0 && list.getChildAt(0).getTop()>=0){ float dy=e.getY()-pullDownY; if(dy>0){ list.setTranslationY(Math.min(dp(54),dy*0.32f)); return false; }} if(e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL){ if(pullArmed && list.getTranslationY()>dp(34)){ Toast.makeText(this,"Atualizando músicas...",Toast.LENGTH_SHORT).show(); load(); } list.animate().translationY(0).setDuration(170).start(); pullArmed=false; return false; } return false; }
    void clipRound(ImageView v,float radius){ if(Build.VERSION.SDK_INT>=21){ v.setClipToOutline(true); v.setOutlineProvider(new ViewOutlineProvider(){ public void getOutline(View view, android.graphics.Outline outline){ outline.setRoundRect(0,0,view.getWidth(),view.getHeight(),dp(radius)); }}); } }
    Bitmap squareBitmap(Bitmap bm){ if(bm==null) return null; int side=Math.min(bm.getWidth(),bm.getHeight()); int x=(bm.getWidth()-side)/2, y=(bm.getHeight()-side)/2; Bitmap cropped=Bitmap.createBitmap(bm,x,y,side,side); return Bitmap.createScaledBitmap(cropped,512,512,true); }

    void askPerms(){ ArrayList<String> ps=new ArrayList<>(); if(Build.VERSION.SDK_INT>=33){ if(checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO)!=PackageManager.PERMISSION_GRANTED) ps.add(Manifest.permission.READ_MEDIA_AUDIO); if(checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) ps.add(Manifest.permission.POST_NOTIFICATIONS); } else if(checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED) ps.add(Manifest.permission.READ_EXTERNAL_STORAGE); if(ps.isEmpty()) load(); else requestPermissions(ps.toArray(new String[0]),5); }
    public void onRequestPermissionsResult(int r,String[] p,int[] g){ super.onRequestPermissionsResult(r,p,g); load(); }

    void load(){ all.clear(); Uri u=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI; String[] pr={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DURATION,MediaStore.Audio.Media.SIZE,MediaStore.Audio.Media.ALBUM_ID}; String sel=MediaStore.Audio.Media.IS_MUSIC+"!=0"; try(Cursor c=getContentResolver().query(u,pr,sel,null,MediaStore.Audio.Media.TITLE+" ASC")){ if(c!=null) while(c.moveToNext()){ long id=c.getLong(0), dur=c.getLong(4), size=c.getLong(5), albumId=c.getLong(6); if(size<MusicStore.minBytes(this)) continue; Uri cu=Uri.withAppendedPath(u, String.valueOf(id)); Song s=new Song(id,cu.toString(),val(c.getString(1),"Sem título"),val(c.getString(2),"Artista desconhecido"),val(c.getString(3),"Álbum desconhecido"),dur,size,albumId); all.add(s); }} catch(Exception e){} apply(); }
    String val(String s,String d){ return s==null||s.trim().isEmpty()?d:s; }
    boolean match(Song s){ if(query.length()==0) return true; return (MusicStore.meta(this,s,"title",s.title)+" "+MusicStore.meta(this,s,"artist",s.artist)+" "+MusicStore.meta(this,s,"album",s.album)).toLowerCase().contains(query); }

    void apply(){ shown.clear(); if(currentTab==0){ for(Song s:all) if(match(s)) shown.add(s); }
        else if(currentTab==1){ ArrayList<String> r=MusicStore.recent(this); for(String uri:r){ Song s=findByUri(uri); if(s!=null && match(s)) shown.add(s); } }
        else if(currentTab==4){ for(Song s:all) if(MusicStore.fav(this,s)&&match(s)) shown.add(s); }
        else if(currentTab==2||currentTab==3||currentTab==5){ makeGroups(); return; }
        adapter.notifyDataSetChanged(); empty.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE); list.setVisibility(shown.isEmpty()?View.GONE:View.VISIBLE); }
    Song findByUri(String uri){ for(Song s:all) if(s.uri.equals(uri)) return s; return null; }
    void makeGroups(){ shown.clear(); LinkedHashMap<String,Integer> map=new LinkedHashMap<>();
        if(currentTab==5 && openedPlaylist==null){ shown.add(new Song(-2,"create_playlist","Criar nova playlist","Toque para nomear uma playlist","",0,0)); for(String name:MusicStore.playlistNames(this)){ int count=0; for(String u:MusicStore.playlist(this,name)) if(findByUri(u)!=null) count++; if(query.length()==0 || name.toLowerCase().contains(query)) shown.add(new Song(-1,"group:playlist:"+name,name,count+" músicas","Playlist",0,0)); } adapter.notifyDataSetChanged(); empty.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE); list.setVisibility(shown.isEmpty()?View.GONE:View.VISIBLE); return; }
        for(Song s:all){ if(!match(s)) continue; String name=currentTab==2?MusicStore.meta(this,s,"artist",s.artist):(currentTab==3?MusicStore.meta(this,s,"album",s.album):openedPlaylist); if(currentTab==5 && !MusicStore.playlist(this,openedPlaylist).contains(s.uri)) continue; if(!map.containsKey(name)){ Song fake=new Song(-1,"group:"+name,name,(currentTab==2?"Artista":currentTab==3?"Álbum":"Playlist"),"",0,0); shown.add(fake); map.put(name,1); } else map.put(name,map.get(name)+1); }
        if(currentTab==5 && openedPlaylist!=null){ ArrayList<Song> items=new ArrayList<>(); for(String u:MusicStore.playlist(this,openedPlaylist)){ Song ss=findByUri(u); if(ss!=null && match(ss)) items.add(ss); } shown.clear(); shown.addAll(items); }
        adapter.notifyDataSetChanged(); empty.setVisibility(shown.isEmpty()?View.VISIBLE:View.GONE); list.setVisibility(shown.isEmpty()?View.GONE:View.VISIBLE); }

    void clickItem(int pos){ if(pos<0||pos>=shown.size()) return; Song s=shown.get(pos); if(s.uri.equals("create_playlist")){ createPlaylistDialog(); return; } if(s.uri.startsWith("group:playlist:")){ openedPlaylist=s.uri.substring("group:playlist:".length()); apply(); return; } if(s.uri.startsWith("group:")){ String name=s.title; ArrayList<Song> groupSongs=new ArrayList<>(); for(Song x:all){ String a=MusicStore.meta(this,x,"artist",x.artist), al=MusicStore.meta(this,x,"album",x.album); if(name.equals(a)||name.equals(al)) groupSongs.add(x); } shown.clear(); shown.addAll(groupSongs); adapter.notifyDataSetChanged(); return; } MusicStore.queue=new ArrayList<>(shown); Intent i=new Intent(this,PlayerService.class); i.setAction(PlayerService.ACTION_PLAY_INDEX); i.putExtra("index",pos); startService(i); openNow(); }
    Song currentSong(){ if(MusicStore.currentIndex>=0 && MusicStore.currentIndex<MusicStore.queue.size()) return MusicStore.queue.get(MusicStore.currentIndex); return null; }

    void showOptions(Song s){
        final Dialog dlg=new Dialog(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(18),dp(18),dp(18)); box.setBackground(round(card,28));
        TextView title=marquee(MusicStore.meta(this,s,"title",s.title),20,Typeface.BOLD); box.addView(title,new LinearLayout.LayoutParams(-1,dp(42)));
        addOption(box,R.drawable.ic_edit,"Editar informações",v->{dlg.dismiss(); edit(s);});
        addOption(box,MusicStore.fav(this,s)?R.drawable.ic_favorite:R.drawable.ic_favorite_border,MusicStore.fav(this,s)?"Remover dos favoritos":"Favoritar",v->{MusicStore.toggleFav(this,s); dlg.dismiss(); apply();});
        addOption(box,R.drawable.ic_playlist,MusicStore.inAnyPlaylist(this,s)?"Gerenciar playlists":"Adicionar na playlist",v->{dlg.dismiss(); choosePlaylistForSong(s);});
        addOption(box,R.drawable.ic_queue,"Adicionar à fila atual",v->{MusicStore.queue.add(s); Toast.makeText(this,"Adicionada à fila atual",Toast.LENGTH_SHORT).show(); dlg.dismiss();});
        addOption(box,R.drawable.ic_delete,"Excluir música",v->{dlg.dismiss(); deleteSong(s);});
        dlg.setContentView(box); Window w=dlg.getWindow(); dlg.show(); Window win=dlg.getWindow(); if(win!=null){ win.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); WindowManager.LayoutParams lp=new WindowManager.LayoutParams(); lp.copyFrom(win.getAttributes()); lp.width=getResources().getDisplayMetrics().widthPixels-dp(34); lp.height=WindowManager.LayoutParams.WRAP_CONTENT; lp.gravity=Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL; lp.y=dp(18); win.setAttributes(lp); } box.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.slide_in_left));
    }
    void addOption(LinearLayout box,int iconRes,String label,View.OnClickListener l){ LinearLayout r=new LinearLayout(this); r.setGravity(Gravity.CENTER_VERTICAL); r.setPadding(dp(8),0,dp(8),0); ImageView ic=new ImageView(this); ic.setImageResource(iconRes); ic.setColorFilter(purple); r.addView(ic,new LinearLayout.LayoutParams(dp(38),dp(38))); TextView t=tv(label,15,Typeface.BOLD); t.setPadding(dp(10),0,0,0); r.addView(t,new LinearLayout.LayoutParams(0,dp(54),1)); r.setOnClickListener(l); box.addView(r,new LinearLayout.LayoutParams(-1,dp(58))); }

    void choosePlaylistForSong(Song s){ ArrayList<String> names=MusicStore.playlistNames(this); if(names.size()==1){ toggleSongPlaylist(s,names.get(0)); return; } final Dialog dlg=new Dialog(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(18),dp(18),dp(18)); box.setBackground(round(card,28)); TextView h=tv("Escolha a playlist",20,Typeface.BOLD); box.addView(h,new LinearLayout.LayoutParams(-1,dp(46))); for(String name:names){ boolean inside=MusicStore.playlist(this,name).contains(s.uri); addOption(box,R.drawable.ic_playlist,(inside?"Remover de ":"Adicionar em ")+name,v->{ toggleSongPlaylist(s,name); dlg.dismiss(); }); } dlg.setContentView(box); dlg.show(); if(dlg.getWindow()!=null){ dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); dlg.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(34), WindowManager.LayoutParams.WRAP_CONTENT); } }
    void toggleSongPlaylist(Song s,String name){ HashSet<String> pl=MusicStore.playlist(this,name); if(pl.contains(s.uri)){ pl.remove(s.uri); Toast.makeText(this,"Removida da playlist",Toast.LENGTH_SHORT).show(); } else { pl.add(s.uri); Toast.makeText(this,"Adicionada em "+name,Toast.LENGTH_SHORT).show(); } MusicStore.savePlaylist(this,name,pl); apply(); }
    void createPlaylistDialog(){ final Dialog dlg=new Dialog(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(18),dp(18),dp(18)); box.setBackground(round(card,28)); TextView h=tv("Nova playlist",20,Typeface.BOLD); EditText name=field("", "Nome da playlist"); Button ok=new Button(this); ok.setText("Criar playlist"); ok.setTextColor(Color.WHITE); ok.setAllCaps(false); ok.setBackground(round(purple,22)); box.addView(h,new LinearLayout.LayoutParams(-1,dp(42))); box.addView(name,new LinearLayout.LayoutParams(-1,dp(54))); box.addView(ok,new LinearLayout.LayoutParams(-1,dp(50))); ok.setOnClickListener(v->{ MusicStore.addPlaylistName(this,name.getText().toString()); dlg.dismiss(); apply(); }); dlg.setContentView(box); dlg.show(); if(dlg.getWindow()!=null){ dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); dlg.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(34), WindowManager.LayoutParams.WRAP_CONTENT); } }

    void edit(Song s){ final Dialog dlg=new Dialog(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(18),dp(18),dp(18),dp(18)); box.setBackground(round(card,28)); TextView h=tv("Editar música",21,Typeface.BOLD); EditText title=field(MusicStore.meta(this,s,"title",s.title),"Nome da música"); EditText artist=field(MusicStore.meta(this,s,"artist",s.artist),"Artista"); EditText album=field(MusicStore.meta(this,s,"album",s.album),"Álbum"); Button cover=new Button(this); cover.setText("Escolher arte da capa"); cover.setTextColor(Color.WHITE); cover.setAllCaps(false); cover.setBackground(round(purple,22)); Button applyBtn=new Button(this); applyBtn.setText("Aplicar alterações"); applyBtn.setTextColor(Color.WHITE); applyBtn.setAllCaps(false); applyBtn.setBackground(round(Color.rgb(126,34,206),22)); box.addView(h,new LinearLayout.LayoutParams(-1,dp(42))); box.addView(title,new LinearLayout.LayoutParams(-1,dp(54))); box.addView(artist,new LinearLayout.LayoutParams(-1,dp(54))); box.addView(album,new LinearLayout.LayoutParams(-1,dp(54))); box.addView(cover,new LinearLayout.LayoutParams(-1,dp(50))); box.addView(applyBtn,new LinearLayout.LayoutParams(-1,dp(54))); cover.setOnClickListener(v->{ pendingCoverSong=s; Intent pick=new Intent(Intent.ACTION_OPEN_DOCUMENT); pick.setType("image/*"); pick.addCategory(Intent.CATEGORY_OPENABLE); startActivityForResult(pick,44); }); applyBtn.setOnClickListener(v->{ MusicStore.setMeta(this,s,"title",title.getText().toString()); MusicStore.setMeta(this,s,"artist",artist.getText().toString()); MusicStore.setMeta(this,s,"album",album.getText().toString()); coverCache.clear(); dlg.dismiss(); apply(); }); dlg.setContentView(box); dlg.show(); if(dlg.getWindow()!=null){ dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); dlg.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(34), WindowManager.LayoutParams.WRAP_CONTENT); } }
    TextView label(String s){ TextView v=tv(s,12,Typeface.BOLD); v.setTextColor(purple); v.setPadding(0,dp(10),0,0); return v; }
    EditText field(String s,String hint){ EditText e=new EditText(this); e.setText(s); e.setHint(hint); e.setSingleLine(true); e.setTextColor(text); e.setHintTextColor(sub); return e; }

    void settings(){
        final Dialog dlg=new Dialog(this); LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(20),dp(18),dp(20),dp(18)); box.setBackground(round(card,28));
        TextView h=tv("Configurações",23,Typeface.BOLD); TextView info=tv("Tudo é aplicado automaticamente",13,Typeface.BOLD); info.setTextColor(sub); box.addView(h,new LinearLayout.LayoutParams(-1,dp(42))); box.addView(info,new LinearLayout.LayoutParams(-1,dp(30)));
        TextView tema=label("Tema do app"); box.addView(tema);
        LinearLayout themeRow=new LinearLayout(this); themeRow.setOrientation(LinearLayout.HORIZONTAL); themeRow.setGravity(Gravity.CENTER); box.addView(themeRow,new LinearLayout.LayoutParams(-1,dp(64)));
        TextView dark=tv("Escuro",15,Typeface.BOLD); dark.setGravity(Gravity.CENTER); TextView light=tv("Claro",15,Typeface.BOLD); light.setGravity(Gravity.CENTER);
        boolean isLight=MusicStore.light(this); dark.setTextColor(!isLight?Color.WHITE:text); light.setTextColor(isLight?Color.WHITE:text); dark.setBackground(round(!isLight?purple:chip,16)); light.setBackground(round(isLight?purple:chip,16));
        LinearLayout.LayoutParams thlp=new LinearLayout.LayoutParams(0,dp(50),1); thlp.setMargins(0,dp(7),dp(6),0); themeRow.addView(dark,thlp); LinearLayout.LayoutParams thlp2=new LinearLayout.LayoutParams(0,dp(50),1); thlp2.setMargins(dp(6),dp(7),0,0); themeRow.addView(light,thlp2);
        dark.setOnClickListener(v->{ if(MusicStore.light(this)){ MusicStore.setLight(this,false); dlg.dismiss(); recreate(); }}); light.setOnClickListener(v->{ if(!MusicStore.light(this)){ MusicStore.setLight(this,true); dlg.dismiss(); recreate(); }});
        settingsMinLabel=label("Tamanho mínimo: "+(MusicStore.minBytes(this)/1024)+" KB"); box.addView(settingsMinLabel);
        SeekBar minSeek=new SeekBar(this); tintSeek(minSeek); minSeek.setMax(10240); minSeek.setProgress((int)Math.min(10240,MusicStore.minBytes(this)/1024)); box.addView(minSeek,new LinearLayout.LayoutParams(-1,dp(58)));
        Button close=new Button(this); close.setText("Fechar"); close.setTextColor(Color.WHITE); close.setAllCaps(false); close.setBackground(round(purple,18)); LinearLayout.LayoutParams clp=new LinearLayout.LayoutParams(-1,dp(50)); clp.setMargins(0,dp(12),0,0); box.addView(close,clp); close.setOnClickListener(v->dlg.dismiss());
        minSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onStartTrackingTouch(SeekBar s){} public void onProgressChanged(SeekBar s,int p,boolean from){ if(from){ MusicStore.setMinBytes(MainActivity.this,p*1024L); settingsMinLabel.setText("Tamanho mínimo: "+p+" KB"); load(); }} public void onStopTrackingTouch(SeekBar s){} });
        dlg.setContentView(box); dlg.show(); if(dlg.getWindow()!=null){ dlg.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT)); dlg.getWindow().setLayout(getResources().getDisplayMetrics().widthPixels-dp(34), WindowManager.LayoutParams.WRAP_CONTENT); }
    }

    void deleteSong(Song s){ try{ int n=getContentResolver().delete(Uri.parse(s.uri), null, null); if(n>0){ all.remove(s); shown.remove(s); adapter.notifyDataSetChanged(); Toast.makeText(this,"Música excluída",Toast.LENGTH_SHORT).show(); } else Toast.makeText(this,"Não foi possível excluir essa música",Toast.LENGTH_LONG).show(); } catch(Exception e){ Toast.makeText(this,"O Android bloqueou a exclusão direta deste arquivo",Toast.LENGTH_LONG).show(); } }
    void openNow(){ nowOpen=true; nowPanel.setVisibility(View.VISIBLE); }
    void closeNow(){ nowOpen=false; nowPanel.setVisibility(View.GONE); }
    public void onBackPressed(){ if(nowOpen){ closeNow(); return; } if(currentTab==5 && openedPlaylist!=null){ openedPlaylist=null; apply(); return; } super.onBackPressed(); }
    void cmd(String a){ Intent i=new Intent(this,PlayerService.class); i.setAction(a); startService(i); }

    void updateState(Intent i){
        String title=i.getStringExtra("title"), artist=i.getStringExtra("artist"), uri=i.getStringExtra("uri"); boolean playing=i.getBooleanExtra("playing",false), fav=i.getBooleanExtra("fav",false); int pos=i.getIntExtra("pos",0), dur=i.getIntExtra("dur",0), rep=i.getIntExtra("repeat",0);
        miniTitle.setText(title==null?"TXMusic":title); miniSub.setText(artist==null?"Player premium":artist); bigTitle.setText(title==null?"Nada tocando":title); bigSub.setText(artist==null?"Escolha uma música":artist);
        int playerIcon=MusicStore.light(this)?Color.rgb(20,20,25):Color.WHITE; int playIcon=MusicStore.light(this)?Color.WHITE:Color.rgb(12,8,20);
        miniPlayBtn.setImageResource(playing?R.drawable.ic_pause:R.drawable.ic_play); nowPlayBtn.setImageResource(playing?R.drawable.ic_pause:R.drawable.ic_play); nowPlayBtn.setColorFilter(playIcon); favBtn.setImageResource(fav?R.drawable.ic_favorite:R.drawable.ic_favorite_border); favBtn.setColorFilter(fav?purple:playerIcon); repeatBtn.setImageResource(rep==2?R.drawable.ic_repeat_one:R.drawable.ic_repeat); repeatBtn.setColorFilter(rep==0?playerIcon:purple); if(playlistBtn!=null) playlistBtn.setColorFilter(playerIcon);
        Song s=uri==null?currentSong():findByUri(uri); Drawable d=coverFor(s); miniCover.setImageDrawable(d); bigCover.setImageDrawable(d);
        if(!dragging){ seek.setMax(Math.max(dur,1)); seek.setProgress(pos); } timeLeft.setText(fmt(pos)); timeRight.setText(fmt(dur));
        if(currentTab==1 || currentTab==4) apply();
    }
    String fmt(long ms){ long s=ms/1000; return (s/60)+":"+String.format(Locale.US,"%02d",s%60); }
    public void onActivityResult(int r,int c,Intent data){ super.onActivityResult(r,c,data); if(r==44 && c==RESULT_OK && data!=null && pendingCoverSong!=null){ Uri u=data.getData(); try{ getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); }catch(Exception ignored){} MusicStore.setMeta(this,pendingCoverSong,"cover",u.toString()); coverCache.clear(); Toast.makeText(this,"Capa personalizada salva",Toast.LENGTH_SHORT).show(); pendingCoverSong=null; apply(); } if(r==45) load(); }
    public void onDestroy(){ try{ unregisterReceiver(stateReceiver); }catch(Exception e){} super.onDestroy(); }

    class SongAdapter extends BaseAdapter { public int getCount(){ return shown.size(); } public Object getItem(int p){ return shown.get(p); } public long getItemId(int p){ return p; } public View getView(int p, View v, ViewGroup parent){
            Song s=shown.get(p); LinearLayout row=new LinearLayout(MainActivity.this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(dp(12),dp(8),dp(8),dp(8)); row.setBackground(round(card,24)); row.setLayoutParams(new AbsListView.LayoutParams(-1,dp(82)));
            ImageView art=new ImageView(MainActivity.this); art.setScaleType(ImageView.ScaleType.CENTER_CROP); clipRound(art,14); if(s.uri.startsWith("group:")||s.uri.equals("create_playlist")){ art.setImageResource(s.uri.equals("create_playlist")?R.drawable.ic_playlist:R.drawable.ic_album); art.setColorFilter(Color.WHITE); art.setBackground(grad()); } else { art.setImageDrawable(coverFor(s)); art.setColorFilter(null); } row.addView(art,new LinearLayout.LayoutParams(dp(58),dp(58)));
            LinearLayout col=new LinearLayout(MainActivity.this); col.setOrientation(LinearLayout.VERTICAL); col.setPadding(dp(12),0,0,0); row.addView(col,new LinearLayout.LayoutParams(0,-1,1)); TextView title=marquee(MusicStore.meta(MainActivity.this,s,"title",s.title),15,Typeface.BOLD); TextView meta=marquee(s.uri.startsWith("group:")||s.uri.equals("create_playlist")?s.artist:(MusicStore.meta(MainActivity.this,s,"artist",s.artist)+" • "+MusicStore.meta(MainActivity.this,s,"album",s.album)),12,Typeface.NORMAL); meta.setTextColor(sub); col.addView(title,new LinearLayout.LayoutParams(-1,dp(32))); col.addView(meta,new LinearLayout.LayoutParams(-1,dp(26)));
            if(s.uri.startsWith("group:")||s.uri.equals("create_playlist")){ TextView arrow=tv("›",26,Typeface.BOLD); arrow.setTextColor(sub); arrow.setGravity(Gravity.CENTER); row.addView(arrow,new LinearLayout.LayoutParams(dp(44),-1)); }
            else { TextView dur=tv(fmt(s.duration),12,Typeface.BOLD); dur.setTextColor(sub); dur.setGravity(Gravity.CENTER); row.addView(dur,new LinearLayout.LayoutParams(dp(44),-1)); ImageButton more=icon(R.drawable.ic_more_vert,sub,8); row.addView(more,new LinearLayout.LayoutParams(dp(42),dp(42))); more.setOnClickListener(vv->showOptions(s)); }
            return wrap(row); }
        View wrap(View row){ LinearLayout out=new LinearLayout(MainActivity.this); out.setPadding(0,0,0,dp(10)); out.addView(row); return out; }}

    class LoopTextView extends TextView implements Runnable{
        boolean running=false; int gap=70;
        LoopTextView(Context c){ super(c); }
        protected void onAttachedToWindow(){ super.onAttachedToWindow(); running=true; postDelayed(this,900); }
        protected void onDetachedFromWindow(){ running=false; removeCallbacks(this); super.onDetachedFromWindow(); }
        public void run(){ if(!running) return; int w=getWidth(); String s=getText()==null?"":getText().toString(); int max=(int)(getPaint().measureText(s)-w); if(max>0){ int x=getScrollX()+1; if(x>max+dp(gap)) x=0; scrollTo(x,0); } else scrollTo(0,0); postDelayed(this,28); }
    }
}
