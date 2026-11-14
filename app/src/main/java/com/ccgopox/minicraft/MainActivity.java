package com.cccgopox.minicraft;

import android.app.Activity;
import android.os.Bundle;
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
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Random;

public class MainActivity extends Activity {
    private WorldView world;
    private Button[] hotbarButtons;
    private TextView survivalHud;
    private final Runnable hudUpdater = new Runnable(){ public void run(){ if(survivalHud!=null && world!=null){survivalHud.setText(world.status());survivalHud.postDelayed(this,1000);} } };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        FrameLayout root = new FrameLayout(this);
        world = new WorldView(this);
        root.addView(world);
        TextView title = new TextView(this);
        title.setText("MiniCraft  •  Android");
        title.setTextColor(0xffffffff); title.setTextSize(16); title.setPadding(18,12,18,12);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        root.addView(title,tp);
        survivalHud = new TextView(this);
        survivalHud.setTextColor(0xffffffff); survivalHud.setTextSize(16); survivalHud.setPadding(16,10,16,10); survivalHud.setShadowLayer(3,0,0,0xff000000);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.LEFT);
        sp.setMargins(12,48,0,0); root.addView(survivalHud,sp);
        TextView crosshair = new TextView(this);
        crosshair.setText("+");
        crosshair.setTextColor(0xffffffff);
        crosshair.setTextSize(26);
        crosshair.setGravity(Gravity.CENTER);
        crosshair.setShadowLayer(3, 0, 0, 0xff000000);
        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(48,48,Gravity.CENTER);
        root.addView(crosshair,cp);
        addButton(root,"▲",Gravity.BOTTOM|Gravity.LEFT,110,world,0);
        addButton(root,"◀",Gravity.BOTTOM|Gravity.LEFT,58,world,1);
        addButton(root,"▶",Gravity.BOTTOM|Gravity.LEFT,162,world,2);
        addButton(root,"BREAK",Gravity.BOTTOM|Gravity.RIGHT,112,world,3);
        addButton(root,"PLACE",Gravity.BOTTOM|Gravity.RIGHT,0,world,4);
        Button eat = new Button(this); eat.setText("EAT");
        FrameLayout.LayoutParams ep = new FrameLayout.LayoutParams(112,76,Gravity.TOP|Gravity.RIGHT);
        ep.setMargins(0,22,18,0); root.addView(eat,ep);
        eat.setOnClickListener(v -> {world.eat();refreshSurvivalHud();});
        Button craft = new Button(this); craft.setText("CRAFT");
        FrameLayout.LayoutParams craftParams = new FrameLayout.LayoutParams(112,76,Gravity.TOP|Gravity.RIGHT);
        craftParams.setMargins(0,0,18,108); root.addView(craft,craftParams);
        craft.setOnClickListener(v -> {world.craft();refreshHotbar();refreshSurvivalHud();});
        hotbarButtons = new Button[3];
        String[] names = {"GRASS", "DIRT", "STONE"};
        int[] leftMargins = {0, 0, 145};
        int[] rightMargins = {145, 0, 0};
        for (int i = 0; i < 3; i++) {
            final int type = i;
            Button slot = new Button(this);
            hotbarButtons[i] = slot;
            FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(140, 70, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            hp.setMargins(leftMargins[i], 0, rightMargins[i], 125);
            root.addView(slot, hp);
            slot.setOnClickListener(v -> { world.select(type); refreshHotbar(); });
        }
        refreshHotbar();
        Button jump = new Button(this); jump.setText("JUMP");
        FrameLayout.LayoutParams jp = new FrameLayout.LayoutParams(150,100,Gravity.BOTTOM|Gravity.RIGHT);
        jp.setMargins(0,0,245,24); root.addView(jump,jp);
        jump.setOnClickListener(v -> world.move(0,0,1));
        setContentView(root);
        refreshSurvivalHud(); survivalHud.postDelayed(hudUpdater,1000);
    }
    private void refreshSurvivalHud(){if(survivalHud!=null&&world!=null)survivalHud.setText(world.status());}
    private void refreshHotbar() {
        if (hotbarButtons == null || world == null) return;
        String[] names = {"GRASS", "DIRT", "STONE"};
        for (int i = 0; i < hotbarButtons.length; i++) {
            hotbarButtons[i].setText((world.selectedType() == i ? "▶ " : "") + (i + 1) + " " + names[i] + "\n" + world.inventoryCount(i));
            hotbarButtons[i].setAlpha(world.selectedType() == i ? 1.0f : 0.72f);
        }
    }
    private void addButton(FrameLayout root,String label,int gravity,int offset,WorldView w,int action) {
        Button b=new Button(this); b.setText(label);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(104,88,gravity);
        if ((gravity & Gravity.LEFT)==Gravity.LEFT) p.setMargins(18+((action==1)?0:(action==2?190:90)),0,0,24);
        else p.setMargins(0,0,18+offset,24);
        root.addView(b,p);
        b.setOnClickListener(v -> { if(action==0)w.move(0,1,0); if(action==1)w.move(-1,0,0); if(action==2)w.move(1,0,0); if(action==3){w.edit(false);refreshHotbar();} if(action==4){w.edit(true);refreshHotbar();} });
    }
    @Override protected void onPause(){super.onPause(); if(world!=null){world.saveWorld();world.onPause();}}
    @Override protected void onResume(){super.onResume(); if(world!=null)world.onResume();}

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
        String status(){return renderer.status();}
        void saveWorld(){renderer.saveWorld();}
        private float lastX,lastY;
        @Override public boolean onTouchEvent(MotionEvent e) {
            if(e.getAction()==MotionEvent.ACTION_DOWN){lastX=e.getX();lastY=e.getY();return true;}
            if(e.getAction()==MotionEvent.ACTION_MOVE){
                float dx=e.getX()-lastX,dy=e.getY()-lastY;
                lastX=e.getX();lastY=e.getY();
                renderer.rotate(dx*0.35f,dy*0.2f);
                return true;
            }
            return true;
        }
    }
    static class WorldRenderer implements GLSurfaceView.Renderer {
        private final float[] projection=new float[16], view=new float[16], vp=new float[16], model=new float[16], mvp=new float[16];
        private int program, pos, color, mvpLoc;
        private final float[] cube={
            -0.5f,0,-0.5f, 0.5f,0,-0.5f, 0.5f,1,-0.5f, -0.5f,1,-0.5f,
            -0.5f,0,0.5f, 0.5f,0,0.5f, 0.5f,1,0.5f, -0.5f,1,0.5f
        };
        private FloatBuffer buffer;
        private final int[][] map=new int[24][24];
        private final int[][][] blocks=new int[24][24][9];
        private final int[] inventory={64,32,16};
        private final SharedPreferences prefs;
        private volatile float px=12,pz=12;
        private volatile int selected=0;
        private volatile float yaw=0;
        private volatile float pitch=18;
        private volatile float jumpOffset=0;
        private float dayTime=0;
        private volatile int health=20;
        private volatile int hunger=20;
        private int food=8;
        private int survivalTicks=0;
        private volatile String notice="";
        private final float[] mobX={5f,18f,7f};
        private final float[] mobZ={6f,17f,19f};
        private final int[] mobAttack=new int[3];
        private int mobFrame=0;
        void rotate(float dx,float dy){yaw+=dx;pitch=Math.max(-5,Math.min(55,pitch+dy));}
        WorldRenderer(Context context){prefs=context.getSharedPreferences("minicraft_world",Context.MODE_PRIVATE);Random r=new Random(22);for(int z=0;z<24;z++)for(int x=0;x<24;x++){map[z][x]=Math.max(2,Math.min(6,(int)(2+Math.sin(x*.4)*1.4+Math.cos(z*.3)*1.2+r.nextDouble()*1.2)));for(int y=0;y<map[z][x];y++)blocks[z][x][y]=y==map[z][x]-1?1:(y>=map[z][x]-3?2:3);}loadWorld();}
        void loadWorld(){for(int i=0;i<3;i++)inventory[i]=Math.max(0,Math.min(999,prefs.getInt("inventory_"+i,inventory[i])));String data=prefs.getString("terrain","");if(data.length()==576){for(int z=0;z<24;z++)for(int x=0;x<24;x++){char c=data.charAt(z*24+x);if(c>='1'&&c<='9')map[z][x]=c-'0';}}String saved=prefs.getString("blocks","");if(saved.length()==24*24*9){for(int z=0;z<24;z++)for(int x=0;x<24;x++)for(int y=0;y<9;y++){char c=saved.charAt((z*24+x)*9+y);if(c>='0'&&c<='3')blocks[z][x][y]=c-'0';}}else{String old=prefs.getString("block_types","");if(old.length()==576){for(int z=0;z<24;z++)for(int x=0;x<24;x++){char c=old.charAt(z*24+x);if(c>='0'&&c<='2'&&map[z][x]>0)blocks[z][x][map[z][x]-1]=c-'0'+1;}}}px=Math.max(1,Math.min(22,prefs.getFloat("player_x",12)));pz=Math.max(1,Math.min(22,prefs.getFloat("player_z",12)));health=Math.max(1,Math.min(20,prefs.getInt("health",20)));hunger=Math.max(0,Math.min(20,prefs.getInt("hunger",20)));food=Math.max(0,Math.min(99,prefs.getInt("food",8)));}
        void saveWorld(){StringBuilder data=new StringBuilder(576),savedBlocks=new StringBuilder(24*24*9);for(int z=0;z<24;z++)for(int x=0;x<24;x++){data.append((char)('0'+Math.max(1,Math.min(9,map[z][x]))));for(int y=0;y<9;y++)savedBlocks.append((char)('0'+Math.max(0,Math.min(3,blocks[z][x][y]))));}SharedPreferences.Editor e=prefs.edit().putString("terrain",data.toString()).putString("blocks",savedBlocks.toString()).putFloat("player_x",px).putFloat("player_z",pz).putInt("health",health).putInt("hunger",hunger).putInt("food",food);for(int i=0;i<3;i++)e.putInt("inventory_"+i,inventory[i]);e.apply();}
        void move(int x,int z,int jump){double a=Math.toRadians(yaw);float nx=(float)(x*Math.cos(a)-z*Math.sin(a));float nz=(float)(x*Math.sin(a)+z*Math.cos(a));float targetX=Math.max(1,Math.min(22,px+nx));float targetZ=Math.max(1,Math.min(22,pz+nz));int oldX=Math.max(0,Math.min(23,Math.round(px))),oldZ=Math.max(0,Math.min(23,Math.round(pz)));int newX=Math.max(0,Math.min(23,Math.round(targetX))),newZ=Math.max(0,Math.min(23,Math.round(targetZ)));if(Math.abs(map[newZ][newX]-map[oldZ][oldX])<=1){px=targetX;pz=targetZ;}if(jump!=0)jumpOffset=1.5f; }
        void select(int type){if(type>=0&&type<3)selected=type;}
        synchronized void eat(){if(food>0&&hunger<20){food--;hunger=Math.min(20,hunger+5);notice="Ate an apple";}else notice=food==0?"No apples left":"Hunger is full";}
        synchronized void craft(){if(inventory[1]>=4){inventory[1]-=4;inventory[2]=Math.min(999,inventory[2]+1);notice="Crafted 1 stone from 4 dirt";}else notice="Need 4 dirt to craft";}
        synchronized String status(){return "♥ "+health+"/20    Hunger "+hunger+"/20    Apples "+food+"\nMobs: 3"+(notice.length()>0?"\n"+notice:"");}
        synchronized int inventoryCount(int type){return type>=0&&type<3?inventory[type]:0;}
         synchronized void edit(boolean place){double a=Math.toRadians(yaw);int x=Math.max(0,Math.min(23,Math.round(px+(float)Math.sin(a)*2.0f)));int z=Math.max(0,Math.min(23,Math.round(pz+(float)Math.cos(a)*2.0f)));if(place){if(inventory[selected]>0&&map[z][x]<9){blocks[z][x][map[z][x]]=selected+1;map[z][x]++;inventory[selected]--;}}else if(map[z][x]>1){map[z][x]--;int id=blocks[z][x][map[z][x]];if(id>=1&&id<=3)inventory[id-1]=Math.min(999,inventory[id-1]+1);blocks[z][x][map[z][x]]=0;}}
        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl, javax.microedition.khronos.egl.EGLConfig c){GLES20.glClearColor(.48f,.72f,.94f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);String vs="uniform mat4 uMVP; attribute vec3 aPos; void main(){gl_Position=uMVP*vec4(aPos,1.0);}";String fs="precision mediump float; uniform vec4 uColor; void main(){gl_FragColor=uColor;}";program=GLES20.glCreateProgram();int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs);GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glLinkProgram(program);int[] linkStatus=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linkStatus,0);if(linkStatus[0]==0){String error=GLES20.glGetProgramInfoLog(program);GLES20.glDeleteProgram(program);throw new IllegalStateException("MiniCraft shader link failed: "+error);}GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);pos=GLES20.glGetAttribLocation(program,"aPos");color=GLES20.glGetUniformLocation(program,"uColor");mvpLoc=GLES20.glGetUniformLocation(program,"uMVP");buffer=ByteBuffer.allocateDirect(108*4).order(ByteOrder.nativeOrder()).asFloatBuffer();}
        int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);int[] status=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,status,0);if(status[0]==0){String error=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IllegalStateException("MiniCraft shader compile failed: "+error);}return s;}
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(projection,0,55,(float)w/h,.1f,100f);}
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){jumpOffset=Math.max(0,jumpOffset-0.035f);if(++survivalTicks>=3600){survivalTicks=0;if(hunger>0)hunger--;else if(--health<=0){health=20;hunger=16;px=12;pz=12;}}dayTime+=0.00045f;float daylight=(float)(0.18+0.72*(0.5+0.5*Math.sin(dayTime)));GLES20.glClearColor(0.035f+0.42f*daylight,0.045f+0.62f*daylight,0.10f+0.78f*daylight,1);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);double angle=Math.toRadians(yaw);float distance=18;float eyeX=px-(float)Math.sin(angle)*distance;float eyeZ=pz-(float)Math.cos(angle)*distance;float eyeY=10+(pitch*.18f);Matrix.setLookAtM(view,0,eyeX,eyeY,eyeZ,px,1+jumpOffset*.15f,pz,0,1,0);Matrix.multiplyMM(vp,0,projection,0,view,0);GLES20.glUseProgram(program);GLES20.glEnableVertexAttribArray(pos);buffer.position(0);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,0,buffer);
            int tx=Math.max(0,Math.min(23,Math.round(px+(float)Math.sin(angle)*2.0f)));int tz=Math.max(0,Math.min(23,Math.round(pz+(float)Math.cos(angle)*2.0f)));for(int z=0;z<24;z++)for(int x=0;x<24;x++)for(int y=0;y<map[z][x];y++){int type=blocks[z][x][y];Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);if(x==tx&&z==tz&&y==map[z][x]-1)GLES20.glUniform4f(color,1.0f,.82f,.18f,1);else if(type==1)GLES20.glUniform4f(color,.30f,.68f,.24f,1);else if(type==2)GLES20.glUniform4f(color,.48f,.29f,.13f,1);else GLES20.glUniform4f(color,.48f,.5f,.54f,1);drawCube();}
            if(++mobFrame%4==0){for(int i=0;i<3;i++){float dx=px-mobX[i],dz=pz-mobZ[i];float dist=(float)Math.sqrt(dx*dx+dz*dz);if(dist<10f&&dist>1.15f){mobX[i]+=dx/dist*.045f;mobZ[i]+=dz/dist*.045f;}else if(dist>=10f){mobX[i]+=(float)Math.sin(mobFrame*.025f+i)*.018f;mobZ[i]+=(float)Math.cos(mobFrame*.021f+i)*.018f;}mobX[i]=Math.max(1,Math.min(22,mobX[i]));mobZ[i]=Math.max(1,Math.min(22,mobZ[i]));if(dist<1.25f){if(++mobAttack[i]>=23){health=Math.max(0,health-1);mobAttack[i]=0;notice="A mob hit you!";if(health==0){health=20;hunger=16;px=12;pz=12;notice="You respawned";}}}else mobAttack[i]=0;}}
            for(int i=0;i<3;i++){int mx=Math.max(0,Math.min(23,(int)mobX[i])),mz=Math.max(0,Math.min(23,(int)mobZ[i]));Matrix.setIdentityM(model,0);Matrix.translateM(model,0,mobX[i],map[mz][mx],mobZ[i]);Matrix.scaleM(model,0,.8f,.9f,.8f);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniform4f(color,.72f,.12f,.16f,1);drawCube();}
            Matrix.setIdentityM(model,0);Matrix.translateM(model,0,px, map[Math.max(0,Math.min(23,(int)pz))][Math.max(0,Math.min(23,(int)px))]+jumpOffset,pz);Matrix.scaleM(model,0,.55f,1.2f,.55f);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniform4f(color,.12f,.25f,.85f,1);drawCube();GLES20.glDisableVertexAttribArray(pos);
        }
        void drawCube(){
            float[] v={
                -.5f,0,-.5f, .5f,0,-.5f, .5f,1,-.5f,  -.5f,0,-.5f, .5f,1,-.5f, -.5f,1,-.5f,
                .5f,0,.5f, -.5f,0,.5f, -.5f,1,.5f,  .5f,0,.5f, -.5f,1,.5f, .5f,1,.5f,
                -.5f,0,.5f, -.5f,0,-.5f, -.5f,1,-.5f,  -.5f,0,.5f, -.5f,1,-.5f, -.5f,1,.5f,
                .5f,0,-.5f, .5f,0,.5f, .5f,1,.5f,  .5f,0,-.5f, .5f,1,.5f, .5f,1,-.5f,
                -.5f,1,-.5f, .5f,1,-.5f, .5f,1,.5f,  -.5f,1,-.5f, .5f,1,.5f, -.5f,1,.5f,
                -.5f,0,.5f, .5f,0,.5f, .5f,0,-.5f,  -.5f,0,.5f, .5f,0,-.5f, -.5f,0,-.5f
            };
            buffer.clear();buffer.put(v).position(0);
            GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,0,buffer);
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES,0,36);
        }
    }
}
