package com.cccgopox.minicraft;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.content.Context;
import android.content.SharedPreferences;
import android.opengl.GLSurfaceView;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Button;
import android.widget.TextView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.ScrollView;
import android.widget.Switch;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

public class MainActivity extends Activity {
    private WorldView world;
    private FrameLayout pauseMenu;
    private Button[] hotbarButtons;
    private TextView survivalHud;
    private final Handler controlHandler=new Handler(Looper.getMainLooper());
    private ToneGenerator tone;
    private final Runnable hudUpdater = new Runnable(){ public void run(){ if(survivalHud!=null && world!=null){survivalHud.setText(world.status());survivalHud.postDelayed(this,1000);} } };
    private final Runnable autosaveUpdater = new Runnable(){ public void run(){if(world!=null){world.saveWorld();controlHandler.postDelayed(this,10000);}} };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        tone=new ToneGenerator(AudioManager.STREAM_MUSIC,45);
        FrameLayout root = new FrameLayout(this);
        world = new WorldView(this);
        root.addView(world);
        TextView title = new TextView(this);
        title.setText("MiniCraft 0.3.0  •  Android");
        title.setTextColor(0xffffffff); title.setTextSize(16); title.setPadding(dp(18),dp(12),dp(18),dp(12));
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        root.addView(title,tp);
        survivalHud = new TextView(this);
        survivalHud.setTextColor(0xffffffff); survivalHud.setTextSize(16); survivalHud.setPadding(dp(16),dp(10),dp(16),dp(10)); survivalHud.setShadowLayer(3,0,0,0xff000000);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);
        sp.setMargins(12,48,0,0); root.addView(survivalHud,sp);
        TextView crosshair = new TextView(this);
        crosshair.setText("+");
        crosshair.setTextColor(0xffffffff);
        crosshair.setTextSize(26);
        crosshair.setGravity(Gravity.CENTER);
        crosshair.setShadowLayer(3, 0, 0, 0xff000000);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(dp(48),dp(48),Gravity.CENTER);
        root.addView(crosshair,cp);
        addButton(root,"▲",Gravity.BOTTOM|Gravity.LEFT,110,world,0);
        addButton(root,"◀",Gravity.BOTTOM|Gravity.LEFT,58,world,1);
        addButton(root,"▶",Gravity.BOTTOM|Gravity.LEFT,162,world,2);
        addButton(root,"▼",Gravity.BOTTOM|Gravity.LEFT,0,world,5);
        addButton(root,"BREAK",Gravity.BOTTOM|Gravity.RIGHT,112,world,3);
        addButton(root,"PLACE",Gravity.BOTTOM|Gravity.RIGHT,0,world,4);
        Button eat = new Button(this); eat.setText("EAT");
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(dp(112),dp(76),Gravity.TOP|Gravity.RIGHT);
        ep.setMargins(0,dp(22),dp(18),0); root.addView(eat,ep);
        eat.setOnClickListener(v -> {world.eat();playTone(ToneGenerator.TONE_PROP_BEEP);refreshSurvivalHud();});
        Button craft = new Button(this); craft.setText("CRAFT");
        FrameLayout.LayoutParams craftParams = new FrameLayout.LayoutParams(112,76,Gravity.TOP|Gravity.RIGHT);
        craftParams.setMargins(0,0,dp(18),dp(108)); root.addView(craft,craftParams);
        craft.setOnClickListener(v -> {world.craft();playTone(ToneGenerator.TONE_PROP_ACK);refreshHotbar();refreshSurvivalHud();});
        Button hit = new Button(this); hit.setText("HIT");
        FrameLayout.LayoutParams hitParams = new FrameLayout.LayoutParams(112,76,Gravity.TOP|Gravity.RIGHT);
        hitParams.setMargins(0,dp(194),dp(18),0);root.addView(hit,hitParams);
        hit.setOnClickListener(v->{world.attack();playTone(ToneGenerator.TONE_PROP_NACK);refreshSurvivalHud();});
        hotbarButtons = new Button[9];
        String[] names = {"GRASS","DIRT","STONE","PLANKS","LEAVES","LOG","BEDROCK","SAND","SNOW"};
        for (int i = 0; i < 9; i++) {
            final int type = i;
            Button slot = new Button(this);
            hotbarButtons[i] = slot;
            FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(dp(56), dp(58), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            int shift=(i-4)*58;
            hp.setMargins(dp(Math.max(0,shift)),0,dp(Math.max(0,-shift)),dp(125));
            root.addView(slot, hp);
            slot.setOnClickListener(v -> { world.select(type); refreshHotbar(); });
        }
        refreshHotbar();
        Button jump = new Button(this); jump.setText("JUMP");
        FrameLayout.LayoutParams jp = new FrameLayout.LayoutParams(dp(150),dp(100),Gravity.BOTTOM|Gravity.RIGHT);
        jp.setMargins(0,0,dp(245),dp(24)); root.addView(jump,jp);
        jump.setOnClickListener(v -> world.move(0,0,1));
        Button pause = new Button(this); pause.setText("Ⅱ");
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(dp(76),dp(68),Gravity.TOP|Gravity.RIGHT);
        pp.setMargins(0,dp(18),dp(145),0); root.addView(pause,pp);
        pauseMenu = new FrameLayout(this);
        pauseMenu.setBackgroundColor(0xdd101820);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(28),dp(24),dp(28),dp(24));
        TextView menuTitle = new TextView(this);
        menuTitle.setText("MINICRAFT");
        menuTitle.setTextColor(0xffffffff);
        menuTitle.setTextSize(32);
        menuTitle.setGravity(Gravity.CENTER);
        panel.addView(menuTitle,new LinearLayout.LayoutParams(-1,-2));
        TextView subtitle = new TextView(this);
        subtitle.setText("ANDROID EDITION");
        subtitle.setTextColor(0xffa8c7dc);
        subtitle.setTextSize(14);
        subtitle.setGravity(Gravity.CENTER);
        panel.addView(subtitle,new LinearLayout.LayoutParams(-1,-2));
        Switch soundSwitch = new Switch(this);
        soundSwitch.setText("ЗВУКИ");
        soundSwitch.setTextColor(0xffffffff);
        soundSwitch.setChecked(world.soundEnabled());
        panel.addView(soundSwitch,new LinearLayout.LayoutParams(-1,-2));
        soundSwitch.setOnCheckedChangeListener((button,checked)->world.setSoundEnabled(checked));
        Button play = new Button(this); play.setText("ИГРАТЬ / ПРОДОЛЖИТЬ");
        panel.addView(play,new LinearLayout.LayoutParams(dp(280),dp(64)));
        Button newWorld = new Button(this); newWorld.setText("СОЗДАТЬ НОВЫЙ МИР");
        panel.addView(newWorld,new LinearLayout.LayoutParams(dp(280),dp(56)));
        newWorld.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Создать новый мир?").setMessage("Текущий мир и инвентарь будут удалены.").setNegativeButton("ОТМЕНА",null).setPositiveButton("СОЗДАТЬ",(dialog,which)->{world.resetWorld();recreate();}).show());
        TextView settingsTitle = new TextView(this);
        settingsTitle.setText("ЧУВСТВИТЕЛЬНОСТЬ КАМЕРЫ");
        settingsTitle.setTextColor(0xffffffff);
        settingsTitle.setGravity(Gravity.CENTER);
        panel.addView(settingsTitle,new LinearLayout.LayoutParams(-1,-2));
        SeekBar sensitivity = new SeekBar(this);
        sensitivity.setMax(100); sensitivity.setProgress(world.sensitivityPercent());
        panel.addView(sensitivity,new LinearLayout.LayoutParams(dp(280),-2));
        TextView speedTitle = new TextView(this);
        speedTitle.setText("СКОРОСТЬ ДВИЖЕНИЯ");
        speedTitle.setTextColor(0xffffffff);
        speedTitle.setGravity(Gravity.CENTER);
        panel.addView(speedTitle,new LinearLayout.LayoutParams(-1,-2));
        SeekBar movementSpeed = new SeekBar(this);
        movementSpeed.setMax(100);
        movementSpeed.setProgress(world.movementSpeedPercent());
        panel.addView(movementSpeed,new LinearLayout.LayoutParams(280,-2));
        TextView speedValue = new TextView(this);
        speedValue.setText(world.movementSpeedPercent()+"%");
        speedValue.setTextColor(0xffffffff);
        speedValue.setGravity(Gravity.CENTER);
        panel.addView(speedValue,new LinearLayout.LayoutParams(-1,-2));
        TextView distanceTitle = new TextView(this);
        distanceTitle.setText("ДАЛЬНОСТЬ ПРОРИСОВКИ");
        distanceTitle.setTextColor(0xffffffff);
        distanceTitle.setGravity(Gravity.CENTER);
        panel.addView(distanceTitle,new LinearLayout.LayoutParams(-1,-2));
        SeekBar renderDistance = new SeekBar(this);
        renderDistance.setMax(16);
        renderDistance.setProgress(world.renderDistance()-8);
        panel.addView(renderDistance,new LinearLayout.LayoutParams(280,-2));
        TextView distanceValue = new TextView(this);
        distanceValue.setText(world.renderDistance()+" БЛОКОВ");
        distanceValue.setTextColor(0xffffffff);
        distanceValue.setGravity(Gravity.CENTER);
        panel.addView(distanceValue,new LinearLayout.LayoutParams(-1,-2));
        TextView sensitivityValue = new TextView(this);
        sensitivityValue.setText(world.sensitivityPercent()+"%");
        sensitivityValue.setTextColor(0xffffffff);
        sensitivityValue.setGravity(Gravity.CENTER);
        panel.addView(sensitivityValue,new LinearLayout.LayoutParams(-1,-2));
        FrameLayout.LayoutParams mp = new FrameLayout.LayoutParams(-1,-1);
        ScrollView menuScroll = new ScrollView(this);menuScroll.setFillViewport(true);menuScroll.addView(panel);
        pauseMenu.addView(menuScroll,mp);
        root.addView(pauseMenu,new FrameLayout.LayoutParams(-1,-1));
        play.setOnClickListener(v -> {pauseMenu.setVisibility(View.GONE);world.setPaused(false);});
        pause.setOnClickListener(v -> {boolean show=pauseMenu.getVisibility()!=View.VISIBLE;pauseMenu.setVisibility(show?View.VISIBLE:View.GONE);world.setPaused(show);});
        movementSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean user){world.setMovementSpeed(.08f+(value/100f)*.24f);speedValue.setText(value+"%");}
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        renderDistance.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean user){int d=value+8;world.setRenderDistance(d);distanceValue.setText(d+" БЛОКОВ");}
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        sensitivity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar bar,int value,boolean user){float s=0.10f+(value/100f)*0.90f;world.setSensitivity(s);sensitivityValue.setText(value+"%");}
            public void onStartTrackingTouch(SeekBar bar){}
            public void onStopTrackingTouch(SeekBar bar){}
        });
        setContentView(root);
        refreshSurvivalHud(); survivalHud.postDelayed(hudUpdater,1000);controlHandler.postDelayed(autosaveUpdater,10000);
    }
    private int dp(float value){return (int)(value*getResources().getDisplayMetrics().density+0.5f);}
    private void playTone(int type){if(tone!=null&&world!=null&&world.soundEnabled())tone.startTone(type,90);}
    @Override protected void onDestroy(){if(tone!=null){tone.release();tone=null;}super.onDestroy();}
    @Override public void onBackPressed(){if(pauseMenu!=null&&pauseMenu.getVisibility()!=View.VISIBLE){pauseMenu.setVisibility(View.VISIBLE);if(world!=null)world.setPaused(true);}else super.onBackPressed();}
    private void refreshSurvivalHud(){if(survivalHud!=null&&world!=null)survivalHud.setText(world.status());}
    private void refreshHotbar() {
        if (hotbarButtons == null || world == null) return;
        String[] names = {"GRASS","DIRT","STONE","PLANKS","LEAVES","LOG","BEDROCK","SAND","SNOW"};
        for (int i = 0; i < hotbarButtons.length; i++) {
            hotbarButtons[i].setText((world.selectedType() == i ? "▶ " : "") + (i + 1) + " " + names[i] + "\n" + world.inventoryCount(i));
            hotbarButtons[i].setAlpha(world.selectedType() == i ? 1.0f : 0.72f);
        }
    }
    private void editAction(WorldView w,int action){if(action==3){w.edit(false);playTone(ToneGenerator.TONE_PROP_BEEP);}else if(action==4){w.edit(true);playTone(ToneGenerator.TONE_PROP_ACK);}refreshHotbar();refreshSurvivalHud();}
    private void moveAction(WorldView w,int action){if(action==0)w.move(0,1,0);if(action==1)w.move(-1,0,0);if(action==2)w.move(1,0,0);if(action==5)w.move(0,-1,0);}
    private void addButton(FrameLayout root,String label,int gravity,int offset,WorldView w,int action) {
        Button b=new Button(this); b.setText(label);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(dp(104),dp(88),gravity);
        if ((gravity & Gravity.LEFT)==Gravity.LEFT) {if(action==0)p.setMargins(dp(108),0,0,dp(120));else if(action==5)p.setMargins(dp(108),0,0,dp(24));else p.setMargins(dp(18+((action==1)?0:(action==2?190:90))),0,0,dp(24));}
        else p.setMargins(0,0,dp(18+offset),dp(24));
        root.addView(b,p);
        if(action<=2||action==5){
            final boolean[] held={false};
            Runnable repeat=new Runnable(){public void run(){if(!held[0])return;moveAction(w,action);controlHandler.postDelayed(this,100);}};
            b.setOnTouchListener((v,e)->{
                if(e.getAction()==MotionEvent.ACTION_DOWN){held[0]=true;moveAction(w,action);controlHandler.postDelayed(repeat,100);return true;}
                if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){held[0]=false;controlHandler.removeCallbacks(repeat);return true;}
                return true;
            });
        }else if(action==3||action==4){
            final boolean[] held={false};
            Runnable repeat=new Runnable(){public void run(){if(!held[0])return;editAction(w,action);controlHandler.postDelayed(this,240);}};
            b.setOnTouchListener((v,e)->{
                if(e.getAction()==MotionEvent.ACTION_DOWN){held[0]=true;editAction(w,action);controlHandler.postDelayed(repeat,240);return true;}
                if(e.getAction()==MotionEvent.ACTION_UP||e.getAction()==MotionEvent.ACTION_CANCEL){held[0]=false;controlHandler.removeCallbacks(repeat);return true;}
                return true;
            });
        }

    }
    @Override protected void onPause(){super.onPause();controlHandler.removeCallbacks(autosaveUpdater);if(survivalHud!=null)survivalHud.removeCallbacks(hudUpdater);if(world!=null){world.saveWorld();world.onPause();}}
    @Override protected void onResume(){super.onResume();if(world!=null)world.onResume();if(survivalHud!=null)survivalHud.postDelayed(hudUpdater,1000);controlHandler.removeCallbacks(autosaveUpdater);controlHandler.postDelayed(autosaveUpdater,10000);}

    static class WorldView extends GLSurfaceView {
        final WorldRenderer renderer;
        WorldView(Activity a){super(a);setEGLContextClientVersion(2);renderer=new WorldRenderer(a);setRenderer(renderer);setRenderMode(RENDERMODE_CONTINUOUSLY);}
        void move(int x,int z,int jump){renderer.move(x,z,jump);}
        void edit(boolean place){renderer.edit(place);}
        void select(int type){renderer.select(type);}
        int selectedType(){return renderer.selected;}
        int inventoryCount(int type){return renderer.inventoryCount(type);}
        void eat(){renderer.eat();}
        void craft(){renderer.craft();}
        void attack(){renderer.attack();}
        boolean soundEnabled(){return renderer.soundEnabled;}
        void setSoundEnabled(boolean value){renderer.setSoundEnabled(value);}
        String status(){return renderer.status();}
        void saveWorld(){renderer.saveWorld();}
        void resetWorld(){renderer.resetWorld();}
        void setSensitivity(float value){renderer.setSensitivity(value);}
        void setRenderDistance(int value){renderer.setRenderDistance(value);}
        int renderDistance(){return renderer.renderDistance();}
        void setMovementSpeed(float value){renderer.setMovementSpeed(value);}
        int movementSpeedPercent(){return renderer.movementSpeedPercent();}
        void setPaused(boolean value){renderer.paused=value;}
        int sensitivityPercent(){return renderer.sensitivityPercent();}
        private float lastX,lastY,lastPinchDistance;
        private float pointerDistance(MotionEvent e){float dx=e.getX(0)-e.getX(1),dy=e.getY(0)-e.getY(1);return (float)Math.sqrt(dx*dx+dy*dy);}
        @Override public boolean onTouchEvent(MotionEvent e) {
            int action=e.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();lastPinchDistance=0;return true;}
            if(action==MotionEvent.ACTION_POINTER_DOWN){if(e.getPointerCount()>=2)lastPinchDistance=pointerDistance(e);return true;}
            if(action==MotionEvent.ACTION_MOVE){
                if(e.getPointerCount()>=2){float d=pointerDistance(e);if(lastPinchDistance>0)renderer.zoom((lastPinchDistance-d)*0.025f);lastPinchDistance=d;}
                else{float dx=e.getX()-lastX,dy=e.getY()-lastY;renderer.rotate(dx*renderer.sensitivity,dy*renderer.sensitivity*0.58f);lastX=e.getX();lastY=e.getY();}
                return true;
            }
            if(action==MotionEvent.ACTION_POINTER_UP){lastPinchDistance=0;if(e.getPointerCount()>1){int p=e.getActionIndex()==0?1:0;lastX=e.getX(p);lastY=e.getY(p);}return true;}
            return true;
        }
    }
    static class WorldRenderer implements GLSurfaceView.Renderer {
        private final float[] projection=new float[16], view=new float[16], vp=new float[16], model=new float[16], mvp=new float[16];
        private int program, pos, color, mvpLoc, blockLoc;
        private final float[] cube={
            -0.5f,0,-0.5f, 0.5f,0,-0.5f, 0.5f,1,-0.5f, -0.5f,1,-0.5f,
            -0.5f,0,0.5f, 0.5f,0,0.5f, 0.5f,1,0.5f, -0.5f,1,0.5f
        };
        private FloatBuffer buffer;
        private final int[][] map=new int[24][24];
        private final int[][] biome=new int[24][24];
        private final int[][] vegetation=new int[24][24];
        private final int[][][] blocks=new int[24][24][64];
        private final int[] inventory={64,32,16,8,16,8,0,32,16};
        private long worldSeed=1;
        private final SharedPreferences prefs;
        private volatile float px=12,pz=12;
        private volatile int selected=0;
        private volatile float yaw=0;
        private volatile float pitch=18;
        private volatile float cameraDistance=18;
        private volatile float sensitivity=0.415f;
        private volatile int renderDistance=12;
        private volatile float movementSpeed=.16f;
        private volatile boolean soundEnabled=true;
        void setSensitivity(float value){sensitivity=Math.max(0.05f,Math.min(1.0f,value));prefs.edit().putFloat("camera_sensitivity",sensitivity).apply();}
        int sensitivityPercent(){return Math.max(0,Math.min(100,Math.round((sensitivity-0.10f)/0.90f*100f)));}
        void setRenderDistance(int value){renderDistance=Math.max(8,Math.min(24,value));prefs.edit().putInt("render_distance",renderDistance).apply();}
        void setMovementSpeed(float value){movementSpeed=Math.max(.08f,Math.min(.32f,value));prefs.edit().putFloat("movement_speed",movementSpeed).apply();}
        void setSoundEnabled(boolean value){soundEnabled=value;prefs.edit().putBoolean("sound_enabled",value).apply();}
        int movementSpeedPercent(){return Math.max(0,Math.min(100,Math.round((movementSpeed-.08f)/.24f*100f)));}
        int renderDistance(){return renderDistance;}
        private volatile boolean paused=true;
        private volatile boolean resetRequested=false;
        private volatile float jumpOffset=0;
        private float dayTime=0;
        private volatile int health=20;
        private volatile int hunger=20;
        private int food=8;
        private int survivalTicks=0;
        private volatile String notice="";
        private final float[] mobX={5f,18f,7f};
        private final float[] mobZ={6f,17f,19f};
        private final float[] mobSpawnX={5f,18f,7f};
        private final float[] mobSpawnZ={6f,17f,19f};
        private final int[] mobRespawn=new int[3];
        private final int[] mobAttack=new int[3];
        private final int[] mobHealth={3,3,3};
        private int mobFrame=0;
        void rotate(float dx,float dy){yaw+=dx;pitch=Math.max(-5,Math.min(55,pitch+dy));}
        void zoom(float amount){cameraDistance=Math.max(8,Math.min(28,cameraDistance+amount));prefs.edit().putFloat("camera_distance",cameraDistance).apply();}
        WorldRenderer(Context context){prefs=context.getSharedPreferences("minicraft_world",Context.MODE_PRIVATE);sensitivity=Math.max(0.05f,Math.min(1.0f,prefs.getFloat("camera_sensitivity",0.415f)));renderDistance=Math.max(8,Math.min(24,prefs.getInt("render_distance",12)));movementSpeed=Math.max(.08f,Math.min(.32f,prefs.getFloat("movement_speed",.16f)));soundEnabled=prefs.getBoolean("sound_enabled",true);cameraDistance=Math.max(8,Math.min(28,prefs.getFloat("camera_distance",18f)));dayTime=prefs.getFloat("day_time",0f);long seed=prefs.getLong("world_seed",System.currentTimeMillis());worldSeed=seed;prefs.edit().putLong("world_seed",seed).apply();for(int z=0;z<24;z++)for(int x=0;x<24;x++){int wx=x-12,wz=z-12;biome[z][x]=OriginalTerrain.biome(wx,wz,(int)seed);int h=OriginalTerrain.heightAt(wx,wz,(int)seed);boolean desert=biome[z][x]==1&&h>20;boolean sand=h<=24||desert;boolean snow=!sand&&(h>=44||(biome[z][x]==2&&h>24));map[z][x]=h;for(int y=0;y<=h;y++){int type;if(y==0)type=7;else if(y==h){type=sand?8:(snow?9:1);if(h<22&&OriginalTerrain.noiseAt(wx/7.0,wz/7.0,(int)seed*7+8)>.62)type=20;else if(h<22&&OriginalTerrain.noiseAt(wx/6.0,wz/6.0,(int)seed*7+11)>.66)type=14;}else if(y>=h-3)type=sand?8:2;else if(desert&&y>=h-7)type=29;else{type=3;int ore=OriginalTerrain.oreAt(wx,y,wz,(int)seed);if(ore!=0)type=ore;}if(OriginalTerrain.isCave(wx,y,wz,h,(int)seed))type=0;blocks[z][x][y]=type;}for(int y=h+1;y<=22&&y<64;y++)blocks[z][x][y]=10;if(OriginalTerrain.cactusAt(wx,wz,(int)seed))vegetation[z][x]=2;else if(OriginalTerrain.treeAt(wx,wz,(int)seed))vegetation[z][x]=biome[z][x]==2?3:1;}loadWorld();}
        void loadWorld(){
            for(int i=0;i<inventory.length;i++)inventory[i]=Math.max(0,Math.min(999,prefs.getInt("inventory_"+i,inventory[i])));
            String data=prefs.getString("terrain_v2","");
            if(data.length()==576){
                for(int z=0;z<24;z++)for(int x=0;x<24;x++){int h=data.charAt(z*24+x)-33;if(h>=1&&h<64)map[z][x]=h;}
            }else{
                String legacy=prefs.getString("terrain","");
                if(legacy.length()==576)for(int z=0;z<24;z++)for(int x=0;x<24;x++){char c=legacy.charAt(z*24+x);if(c>='1'&&c<='9')map[z][x]=c-'0';}
            }
            String saved=prefs.getString("blocks_v3","");
            if(saved.length()==24*24*64){
                for(int z=0;z<24;z++)for(int x=0;x<24;x++)for(int y=0;y<64;y++){int id=saved.charAt((z*24+x)*64+y)-33;if(id>=0&&id<64)blocks[z][x][y]=id;}
            }else{
                String legacyV2=prefs.getString("blocks_v2","");
                if(legacyV2.length()==24*24*64){
                    for(int z=0;z<24;z++)for(int x=0;x<24;x++)for(int y=0;y<64;y++){char c=legacyV2.charAt((z*24+x)*64+y);if(c>='0'&&c<='7')blocks[z][x][y]=c-'0';}
                }else{
                    String legacyBlocks=prefs.getString("blocks","");
                    if(legacyBlocks.length()==24*24*9){
                        for(int z=0;z<24;z++)for(int x=0;x<24;x++)for(int y=0;y<9;y++){char c=legacyBlocks.charAt((z*24+x)*9+y);if(c>='0'&&c<='4')blocks[z][x][y]=c-'0';}
                    }else{
                        String old=prefs.getString("block_types","");
                        if(old.length()==576)for(int z=0;z<24;z++)for(int x=0;x<24;x++){char c=old.charAt(z*24+x);if(c>='0'&&c<='2'&&map[z][x]>0)blocks[z][x][map[z][x]-1]=c-'0'+1;}
                    }
                }
            }
            px=Math.max(1,Math.min(22,prefs.getFloat("player_x",12)));
            pz=Math.max(1,Math.min(22,prefs.getFloat("player_z",12)));
            health=Math.max(1,Math.min(20,prefs.getInt("health",20)));
            hunger=Math.max(0,Math.min(20,prefs.getInt("hunger",20)));
            food=Math.max(0,Math.min(99,prefs.getInt("food",8)));
            String plants=prefs.getString("vegetation","");
            if(plants.length()==576)for(int z=0;z<24;z++)for(int x=0;x<24;x++){char p=plants.charAt(z*24+x);if(p>='0'&&p<='3')vegetation[z][x]=p-'0';}
        }
        void resetWorld(){resetRequested=true;prefs.edit().remove("terrain").remove("blocks").remove("terrain_v2").remove("blocks_v2").remove("blocks_v3").remove("player_x").remove("player_z").remove("health").remove("hunger").remove("food").remove("inventory_0").remove("inventory_1").remove("inventory_2").remove("inventory_3").remove("inventory_4").remove("inventory_5").remove("inventory_6").remove("inventory_7").remove("inventory_8").remove("vegetation").remove("day_time").remove("world_seed").apply();}
        void saveWorld(){if(resetRequested)return;StringBuilder data=new StringBuilder(576),savedBlocks=new StringBuilder(24*24*64),plants=new StringBuilder(576);for(int z=0;z<24;z++)for(int x=0;x<24;x++){data.append((char)(33+Math.max(1,Math.min(63,map[z][x]))));for(int y=0;y<64;y++)savedBlocks.append((char)(33+Math.max(0,Math.min(63,blocks[z][x][y]))));plants.append((char)('0'+vegetation[z][x]));}SharedPreferences.Editor e=prefs.edit().putString("terrain_v2",data.toString()).putString("blocks_v3",savedBlocks.toString()).putString("vegetation",plants.toString()).putFloat("player_x",px).putFloat("player_z",pz).putInt("health",health).putInt("hunger",hunger).putInt("food",food).putFloat("day_time",dayTime);for(int i=0;i<inventory.length;i++)e.putInt("inventory_"+i,inventory[i]);e.apply();}
        boolean clearAt(int x,int z){int h=map[z][x];if(h+2>=64)return false;for(int y=h+1;y<=h+2;y++){int id=blocks[z][x][y];if(id!=0&&id!=10)return false;}return true;}
        void move(int x,int z,int jump){double a=Math.toRadians(yaw);float nx=(float)(x*Math.cos(a)-z*Math.sin(a))*movementSpeed;float nz=(float)(x*Math.sin(a)+z*Math.cos(a))*movementSpeed;float targetX=Math.max(1,Math.min(22,px+nx));float targetZ=Math.max(1,Math.min(22,pz+nz));int oldX=Math.max(0,Math.min(23,Math.round(px))),oldZ=Math.max(0,Math.min(23,Math.round(pz)));int newX=Math.max(0,Math.min(23,Math.round(targetX))),newZ=Math.max(0,Math.min(23,Math.round(targetZ)));if(Math.abs(map[newZ][newX]-map[oldZ][oldX])<=1&&vegetation[newZ][newX]==0&&clearAt(newX,newZ)){px=targetX;pz=targetZ;}if(jump!=0&&jumpOffset<=0.01f)jumpOffset=1.5f; }
        void select(int type){if(type>=0&&type<inventory.length)selected=type;}
        synchronized void eat(){if(food>0&&(hunger<20||health<20)){food--;hunger=Math.min(20,hunger+5);health=Math.min(20,health+2);notice="Ate an apple (+5 hunger, +2 health)";}else notice=food==0?"No apples left":"Already full";}
        synchronized void craft(){if(inventory[1]>=4){inventory[1]-=4;inventory[2]=Math.min(999,inventory[2]+1);notice="Crafted 1 stone from 4 dirt";}else notice="Need 4 dirt to craft";}
        synchronized void attack(){int best=-1;float bestDistance=3.1f;double a=Math.toRadians(yaw);float fx=(float)Math.sin(a),fz=(float)Math.cos(a);for(int i=0;i<3;i++){if(mobHealth[i]<=0)continue;float dx=mobX[i]-px,dz=mobZ[i]-pz;float d=(float)Math.sqrt(dx*dx+dz*dz);if(d<bestDistance&&d>0.01f&&(dx/d*fx+dz/d*fz)>.25f){best=i;bestDistance=d;}}if(best<0){notice="No mob in front of you";return;}mobHealth[best]--;notice=mobHealth[best]<=0?"Mob defeated!":"Mob hit: "+mobHealth[best]+" HP left";}
        synchronized String status(){int x=Math.max(0,Math.min(23,Math.round(px))),z=Math.max(0,Math.min(23,Math.round(pz)));String area=biome[z][x]==1?"Desert":(biome[z][x]==2?"Snow": "Forest");String phase=Math.sin(dayTime)>0?"Day":"Night";int alive=0;for(int hp:mobHealth)if(hp>0)alive++;return "♥ "+health+"/20    Hunger "+hunger+"/20    Apples "+food+"\n"+area+"  •  "+phase+"  •  Mobs: "+alive+(notice.length()>0?"\n"+notice:"");}
        synchronized int inventoryCount(int type){return type>=0&&type<inventory.length?inventory[type]:0;}
         private int[] rayTarget(){
            double a=Math.toRadians(yaw);
            int lastX=Math.round(px),lastZ=Math.round(pz);
            for(float d=0.65f;d<=5.0f;d+=0.08f){
                int x=Math.max(0,Math.min(23,Math.round(px+(float)Math.sin(a)*d)));
                int z=Math.max(0,Math.min(23,Math.round(pz+(float)Math.cos(a)*d)));
                if(x==lastX&&z==lastZ)continue;
                lastX=x;lastZ=z;
                if(vegetation[z][x]>0)return new int[]{x,z,1};
                if(map[z][x]>0)return new int[]{x,z,0};
            }
            return null;
         }
         synchronized void edit(boolean place){
            int[] target=rayTarget();
            if(target==null){notice="No block in reach";return;}
            int x=target[0],z=target[1];
            if(target[2]==1){if(place){notice="Cannot place on a tree";return;}vegetation[z][x]=0;inventory[3]=Math.min(999,inventory[3]+2);notice="Collected 2 wood";return;}
            if(place){
                if(inventory[selected]<=0){notice="Not enough blocks";return;}
                if(map[z][x]>=63){notice="Block limit reached";return;}
                blocks[z][x][map[z][x]]=selected+1;map[z][x]++;inventory[selected]--;
                notice="Block placed";
            }else{
                if(map[z][x]<=1){notice="Cannot break the base";return;}
                map[z][x]--;
                int id=blocks[z][x][map[z][x]];
                if(id>=1&&id<=9)inventory[id-1]=Math.min(999,inventory[id-1]+1);
                blocks[z][x][map[z][x]]=0;
                notice="Block mined";
            }
         }
        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl, javax.microedition.khronos.egl.EGLConfig c){GLES20.glClearColor(.48f,.72f,.94f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);String vs="uniform mat4 uMVP; attribute vec3 aPos; varying vec3 vLocal; void main(){vLocal=aPos;gl_Position=uMVP*vec4(aPos,1.0);}";String fs="precision mediump float; uniform vec4 uColor; uniform float uBlock; varying vec3 vLocal; float hash(vec3 p){return fract(sin(dot(p,vec3(127.1,311.7,74.7)))*43758.5453);} void main(){float topFace=step(0.98,vLocal.y);float bottomFace=1.0-step(0.02,vLocal.y);float faceShade=mix(0.78,1.0,topFace);faceShade=mix(faceShade,0.58,bottomFace);vec3 p=vLocal*vec3(14.0,18.0,14.0);float n=hash(floor(p));float shade=0.90+n*0.15;vec3 tex=uColor.rgb;if(uBlock<1.5){if(topFace<0.5&&bottomFace<0.5&&vLocal.y>0.78)tex=mix(tex,vec3(0.36,0.20,0.09),0.55);shade=0.88+n*0.20;}else if(uBlock<2.5){shade=0.78+n*0.32;}else if(uBlock<3.5){float grain=hash(floor(p*1.7));shade=0.72+grain*0.40;}else if(uBlock<4.5){float stripe=step(0.82,fract(vLocal.y*7.0+hash(floor(p.xz))));shade=mix(0.82,1.12,stripe)+n*0.08;}else if(uBlock<5.5){shade=0.88+n*0.22;}else if(uBlock<6.5){float rings=step(0.82,fract(length(vLocal.xz)*12.0));shade=mix(0.78,1.08,rings)+n*0.08;}else if(uBlock<7.5){shade=0.72+n*0.22;}else if(uBlock<9.5){shade=0.86+n*0.24;}else if(uBlock>9.5&&uBlock<10.5){float ripple=sin((vLocal.x+vLocal.z)*28.0+sin(vLocal.x*9.0)*2.0);shade=0.94+ripple*0.045;tex=mix(tex,vec3(0.35,0.62,0.86),0.18);}else if(uBlock>10.5&&uBlock<18.5){float ore=hash(floor(p*0.65));if(ore>0.76){if(uBlock<11.5)tex=vec3(0.08,0.08,0.09);else if(uBlock<12.5)tex=vec3(0.82,0.48,0.25);else if(uBlock<16.5)tex=vec3(0.96,0.72,0.16);else tex=vec3(0.18,0.86,0.88);}shade=0.82+n*0.22;}else if(uBlock>19.5&&uBlock<20.5){shade=0.80+n*0.24;}else if(uBlock>28.5&&uBlock<29.5){float seam=step(0.88,fract(vLocal.y*3.0));shade=mix(0.78,1.0,seam)+n*0.12;}gl_FragColor=vec4(tex*faceShade*shade,uColor.a);}";program=GLES20.glCreateProgram();int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs);GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glLinkProgram(program);int[] linkStatus=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linkStatus,0);if(linkStatus[0]==0){String error=GLES20.glGetProgramInfoLog(program);GLES20.glDeleteProgram(program);throw new IllegalStateException("MiniCraft shader link failed: "+error);}GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);pos=GLES20.glGetAttribLocation(program,"aPos");color=GLES20.glGetUniformLocation(program,"uColor");mvpLoc=GLES20.glGetUniformLocation(program,"uMVP");blockLoc=GLES20.glGetUniformLocation(program,"uBlock");buffer=ByteBuffer.allocateDirect(108*4).order(ByteOrder.nativeOrder()).asFloatBuffer();}
        int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] status=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,status,0);if(status[0]==0){String error=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException("MiniCraft shader compile failed: "+error);}return s;}
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(projection,0,55,(float)w/h,.1f,100f);}
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){if(!paused){jumpOffset=Math.max(0,jumpOffset-0.035f);if(++survivalTicks>=3600){survivalTicks=0;if(hunger>0)hunger--;else if(--health<=0){health=20;hunger=16;px=12;pz=12;}}dayTime+=0.00045f;}float daylight=(float)(0.18+0.72*(0.5+0.5*Math.sin(dayTime)));GLES20.glClearColor(0.035f+0.42f*daylight,0.045f+0.62f*daylight,0.10f+0.78f*daylight,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);double angle=Math.toRadians(yaw);float eyeX=px-(float)Math.sin(angle)*cameraDistance;float eyeZ=pz-(float)Math.cos(angle)*cameraDistance;int playerCellX=Math.max(0,Math.min(23,Math.round(px))),playerCellZ=Math.max(0,Math.min(23,Math.round(pz)));float playerGround=map[playerCellZ][playerCellX];float eyeY=playerGround+8+(pitch*.18f);Matrix.setLookAtM(view,0,eyeX,eyeY,eyeZ,px,playerGround+1+jumpOffset*.15f,pz,0,1,0);Matrix.multiplyMM(vp,0,projection,0,view,0);GLES20.glUseProgram(program);GLES20.glEnableVertexAttribArray(pos);buffer.position(0);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,0,buffer);
            int tx=Math.max(0,Math.min(23,Math.round(px+(float)Math.sin(angle)*2.0f)));int tz=Math.max(0,Math.min(23,Math.round(pz+(float)Math.cos(angle)*2.0f)));for(int z=0;z<24;z++)for(int x=0;x<24;x++){if(Math.abs(x-px)>renderDistance||Math.abs(z-pz)>renderDistance)continue;for(int y=0;y<=map[z][x];y++){int type=blocks[z][x][y];if(type==0)continue;GLES20.glUniform1f(blockLoc,type);Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);if(x==tx&&z==tz&&y==map[z][x]-1)GLES20.glUniform4f(color,1.0f,.82f,.18f,1);else if(type==1)GLES20.glUniform4f(color,.30f,.68f,.24f,1);else if(type==2)GLES20.glUniform4f(color,.48f,.29f,.13f,1);else if(type==4)GLES20.glUniform4f(color,.66f,.43f,.22f,1);else if(type==5)GLES20.glUniform4f(color,.20f,.42f,.14f,1);else if(type==6)GLES20.glUniform4f(color,.40f,.25f,.12f,1);else if(type==7)GLES20.glUniform4f(color,.22f,.22f,.24f,1);else if(type==8)GLES20.glUniform4f(color,.78f,.68f,.38f,1);else if(type==9)GLES20.glUniform4f(color,.88f,.92f,.98f,1);else if(type==11)GLES20.glUniform4f(color,.35f,.35f,.37f,1);else if(type==12)GLES20.glUniform4f(color,.55f,.52f,.49f,1);else if(type==14)GLES20.glUniform4f(color,.50f,.49f,.45f,1);else if(type==16)GLES20.glUniform4f(color,.62f,.53f,.18f,1);else if(type==17)GLES20.glUniform4f(color,.24f,.68f,.72f,1);else if(type==20)GLES20.glUniform4f(color,.45f,.52f,.64f,1);else if(type==29)GLES20.glUniform4f(color,.72f,.61f,.40f,1);else GLES20.glUniform4f(color,.48f,.5f,.54f,1);drawCube();}}
            GLES20.glEnable(GLES20.GL_BLEND);
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA,GLES20.GL_ONE_MINUS_SRC_ALPHA);
            GLES20.glDepthMask(false);
            for(int z=0;z<24;z++)for(int x=0;x<24;x++){
                if(map[z][x]>=22||Math.abs(x-px)>renderDistance||Math.abs(z-pz)>renderDistance)continue;
                Matrix.setIdentityM(model,0);
                Matrix.translateM(model,0,x,22.0f,z);
                Matrix.scaleM(model,0,1.0f,0.12f,1.0f);
                Matrix.multiplyMM(mvp,0,vp,0,model,0);
                GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);
                GLES20.glUniform1f(blockLoc,10);
                GLES20.glUniform4f(color,0.12f,0.34f,0.66f,0.76f);
                drawCube();
            }
            GLES20.glDepthMask(true);
            GLES20.glDisable(GLES20.GL_BLEND);
            for(int z=0;z<24;z++)for(int x=0;x<24;x++){
                if(vegetation[z][x]==0||Math.abs(x-px)>renderDistance||Math.abs(z-pz)>renderDistance)continue;
                float base=map[z][x];
                if(vegetation[z][x]==1){
                    float treeH=OriginalTerrain.treeHeight(x-12,z-12,(int)worldSeed);
                    Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,base,z);Matrix.scaleM(model,0,.24f,treeH,.24f);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniform1f(blockLoc,4);GLES20.glUniform4f(color,.38f,.22f,.10f,1);drawCube();
                    Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,base+treeH-1.0f,z);Matrix.scaleM(model,0,2.25f,1.35f,2.25f);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniform1f(blockLoc,1);GLES20.glUniform4f(color,.12f,.43f,.13f,1);drawCube();
                    Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,base+treeH,z);Matrix.scaleM(model,0,1.65f,1.25f,1.65f);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniform1f(blockLoc,1);GLES20.glUniform4f(color,.16f,.54f,.17f,1);drawCube();
                    Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,base+treeH+1.0f,z);Matrix.scaleM(model,0,.95f,.8f,.95f);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniform1f(blockLoc,1);GLES20.glUniform4f(color,.18f,.58f,.19f,1);drawCube();
                }else if(vegetation[z][x]==2){
                    float cactusH=OriginalTerrain.cactusHeight(x-12,z-12,(int)worldSeed);
                    Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,base,z);Matrix.scaleM(model,0,.34f,cactusH,.34f);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniform1f(blockLoc,1);GLES20.glUniform4f(color,.18f,.48f,.22f,1);drawCube();
                }else{
                    float treeH=OriginalTerrain.treeHeight(x-12,z-12,(int)worldSeed);
                    Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,base,z);Matrix.scaleM(model,0,.25f,treeH,.25f);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniform1f(blockLoc,4);GLES20.glUniform4f(color,.34f,.23f,.12f,1);drawCube();
                    Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,base+treeH-1.0f,z);Matrix.scaleM(model,0,1.9f,1.35f,1.9f);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniform1f(blockLoc,6);GLES20.glUniform4f(color,.78f,.86f,.94f,1);drawCube();
                    Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,base+treeH,z);Matrix.scaleM(model,0,1.2f,1.0f,1.2f);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniform1f(blockLoc,6);GLES20.glUniform4f(color,.88f,.93f,.98f,1);drawCube();
                }
            }
            if(!paused&&++mobFrame%4==0){for(int i=0;i<3;i++){if(mobHealth[i]<=0){if(++mobRespawn[i]>=600){mobHealth[i]=3;mobX[i]=mobSpawnX[i];mobZ[i]=mobSpawnZ[i];mobRespawn[i]=0;}continue;}float dx=px-mobX[i],dz=pz-mobZ[i];float dist=(float)Math.sqrt(dx*dx+dz*dz);if(dist<10f&&dist>1.15f){float sx=dx/dist*.045f,sz=dz/dist*.045f;int cx=Math.max(0,Math.min(23,(int)mobX[i])),cz=Math.max(0,Math.min(23,(int)mobZ[i]));int nx=Math.max(0,Math.min(23,Math.round(mobX[i]+sx))),nz=Math.max(0,Math.min(23,Math.round(mobZ[i]+sz)));if(vegetation[nz][nx]==0&&Math.abs(map[nz][nx]-map[cz][cx])<=1&&clearAt(nx,nz)){mobX[i]+=sx;mobZ[i]+=sz;}else{mobX[i]+=sz*.7f;mobZ[i]-=sx*.7f;}}else if(dist>=10f){mobX[i]+=(float)Math.sin(mobFrame*.025f+i)*.018f;mobZ[i]+=(float)Math.cos(mobFrame*.021f+i)*.018f;}mobX[i]=Math.max(1,Math.min(22,mobX[i]));mobZ[i]=Math.max(1,Math.min(22,mobZ[i]));if(dist<1.25f){if(++mobAttack[i]>=23){health=Math.max(0,health-1);mobAttack[i]=0;notice="A mob hit you!";if(health==0){health=20;hunger=16;px=12;pz=12;notice="You respawned";}}}else mobAttack[i]=0;}}
            for(int i=0;i<3;i++){if(mobHealth[i]<=0)continue;int mx=Math.max(0,Math.min(23,(int)mobX[i])),mz=Math.max(0,Math.min(23,(int)mobZ[i]));float bx=mobX[i],by=map[mz][mx],bz=mobZ[i];drawPart(bx,by+.55f,bz,.62f,1.1f,.34f,.30f,.48f,.22f);drawPart(bx,by+1.28f,bz,.48f,.48f,.46f,.48f,.66f,.38f);drawPart(bx-.43f,by+.58f,bz,.22f,.92f,.24f,.25f,.42f,.20f);drawPart(bx+.43f,by+.58f,bz,.22f,.92f,.24f,.25f,.42f,.20f);drawPart(bx-.18f,by+.05f,bz,.24f,.62f,.28f,.20f,.22f,.34f);drawPart(bx+.18f,by+.05f,bz,.24f,.62f,.28f,.20f,.22f,.34f);}
            float playerY=map[Math.max(0,Math.min(23,(int)pz))][Math.max(0,Math.min(23,(int)px))]+jumpOffset;drawPart(px,playerY+.62f,pz,.58f,1.18f,.34f,.12f,.25f,.82f);drawPart(px,playerY+1.42f,pz,.48f,.48f,.46f,.78f,.62f,.48f);drawPart(px-.40f,playerY+.64f,pz,.20f,.90f,.22f,.12f,.25f,.82f);drawPart(px+.40f,playerY+.64f,pz,.20f,.90f,.22f,.12f,.25f,.82f);drawPart(px-.16f,playerY+.04f,pz,.24f,.64f,.28f,.18f,.18f,.24f);drawPart(px+.16f,playerY+.04f,pz,.24f,.64f,.28f,.18f,.18f,.24f);GLES20.glDisableVertexAttribArray(pos);
        }
        private final float[] cubeTriangles={
                -.5f,0,-.5f, .5f,0,-.5f, .5f,1,-.5f,  -.5f,0,-.5f, .5f,1,-.5f, -.5f,1,-.5f,
                .5f,0,.5f, -.5f,0,.5f, -.5f,1,.5f,  .5f,0,.5f, -.5f,1,.5f, .5f,1,.5f,
                -.5f,0,.5f, -.5f,0,-.5f, -.5f,1,-.5f,  -.5f,0,.5f, -.5f,1,-.5f, -.5f,1,.5f,
                .5f,0,-.5f, .5f,0,.5f, .5f,1,.5f,  .5f,0,-.5f, .5f,1,.5f, .5f,1,-.5f,
                -.5f,1,-.5f, .5f,1,-.5f, .5f,1,.5f,  -.5f,1,-.5f, .5f,1,.5f, -.5f,1,.5f,
                -.5f,0,.5f, .5f,0,.5f, .5f,0,-.5f,  -.5f,0,.5f, .5f,0,-.5f, -.5f,0,-.5f
            };
        void drawPart(float x,float y,float z,float sx,float sy,float sz,float r,float g,float b){
            Matrix.setIdentityM(model,0);
            Matrix.translateM(model,0,x,y,z);
            Matrix.scaleM(model,0,sx,sy,sz);
            Matrix.multiplyMM(mvp,0,vp,0,model,0);
            GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);
            GLES20.glUniform1f(blockLoc,1);
            GLES20.glUniform4f(color,r,g,b,1);
            drawCube();
        }
        void drawCube(){
            buffer.clear();buffer.put(cubeTriangles).position(0);
            GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,0,buffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);
        }
    }
}
