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
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(5894 | 1024 | 512);
        FrameLayout root = new FrameLayout(this);
        world = new WorldView(this);
        root.addView(world);
        TextView title = new TextView(this);
        title.setText("MiniCraft  •  Android prototype");
        title.setTextColor(0xffffffff); title.setTextSize(16); title.setPadding(18,12,18,12);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-2,-2,Gravity.TOP|Gravity.CENTER_HORIZONTAL);
        root.addView(title,tp);
        addButton(root,"▲",Gravity.BOTTOM|Gravity.LEFT,110,world,0);
        addButton(root,"◀",Gravity.BOTTOM|Gravity.LEFT,58,world,1);
        addButton(root,"▶",Gravity.BOTTOM|Gravity.LEFT,162,world,2);
        addButton(root,"BREAK",Gravity.BOTTOM|Gravity.RIGHT,120,world,3);
        addButton(root,"PLACE",Gravity.BOTTOM|Gravity.RIGHT,58,world,4);
        Button grass=new Button(this);grass.setText("1 GRASS");FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(140,70,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);gp.setMargins(0,0,145,18);root.addView(grass,gp);grass.setOnClickListener(v->world.select(0));
        Button dirt=new Button(this);dirt.setText("2 DIRT");FrameLayout.LayoutParams dp=new FrameLayout.LayoutParams(140,70,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);dp.setMargins(0,0,0,18);root.addView(dirt,dp);dirt.setOnClickListener(v->world.select(1));
        Button stone=new Button(this);stone.setText("3 STONE");FrameLayout.LayoutParams sp=new FrameLayout.LayoutParams(140,70,Gravity.BOTTOM|Gravity.CENTER_HORIZONTAL);sp.setMargins(145,0,0,18);root.addView(stone,sp);stone.setOnClickListener(v->world.select(2));
        Button jump = new Button(this); jump.setText("JUMP");
        FrameLayout.LayoutParams jp = new FrameLayout.LayoutParams(150,100,Gravity.BOTTOM|Gravity.RIGHT);
        jp.setMargins(0,0,245,24); root.addView(jump,jp);
        jump.setOnClickListener(v -> world.move(0,0,1));
        setContentView(root);
    }
    private void addButton(FrameLayout root,String label,int gravity,int offset,WorldView w,int action) {
        Button b=new Button(this); b.setText(label);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(104,88,gravity);
        if ((gravity & Gravity.LEFT)==Gravity.LEFT) p.setMargins(18+((action==1)?0:(action==2?190:90)),0,0,24);
        else p.setMargins(0,0,18+offset,24);
        root.addView(b,p);
        b.setOnClickListener(v -> { if(action==0)w.move(0,-1,0); if(action==1)w.move(-1,0,0); if(action==2)w.move(1,0,0); if(action==3)w.edit(false); if(action==4)w.edit(true); });
    }
    @Override protected void onPause(){super.onPause(); if(world!=null){world.saveWorld();world.onPause();}}
    @Override protected void onResume(){super.onResume(); if(world!=null)world.onResume();}

    static class WorldView extends GLSurfaceView {
        final WorldRenderer renderer;
        WorldView(Activity a){super(a);setEGLContextClientVersion(2);renderer=new WorldRenderer(a);setRenderer(renderer);setRenderMode(RENDERMODE_CONTINUOUSLY);}
        void select(int type){if(type>=0&&type<3)selected=type;}
        void move(int x,int z,int jump){renderer.move(x,z,jump);}
        void edit(boolean place){renderer.edit(place);}
        void select(int type){renderer.select(type);}
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
        private final int[][] blockTypes=new int[24][24];
        private final int[] inventory={64,32,16};
        private final SharedPreferences prefs;
        private volatile float px=12,pz=12;
        private volatile int selected=0;
        private volatile float yaw=0;
        private volatile float pitch=18;
        private volatile float jumpOffset=0;
        void rotate(float dx,float dy){yaw+=dx;pitch=Math.max(-5,Math.min(55,pitch+dy));}
        WorldRenderer(Context context){prefs=context.getSharedPreferences("minicraft_world",Context.MODE_PRIVATE);Random r=new Random(22);for(int z=0;z<24;z++)for(int x=0;x<24;x++)map[z][x]=(int)(2+Math.sin(x*.4)*1.4+Math.cos(z*.3)*1.2+r.nextDouble()*1.2);loadWorld();}
        void loadWorld(){String types=prefs.getString("block_types","");if(types.length()==576){for(int z=0;z<24;z++)for(int x=0;x<24;x++){char c=types.charAt(z*24+x);if(c>='0'&&c<='2')blockTypes[z][x]=c-'0';}}for(int i=0;i<3;i++)inventory[i]=prefs.getInt("inventory_"+i,inventory[i]);String data=prefs.getString("terrain","");if(data.length()==576){for(int z=0;z<24;z++)for(int x=0;x<24;x++){char c=data.charAt(z*24+x);if(c>='1'&&c<='9')map[z][x]=c-'0';}}px=Math.max(1,Math.min(22,prefs.getFloat("player_x",12)));pz=Math.max(1,Math.min(22,prefs.getFloat("player_z",12)));}
        void saveWorld(){StringBuilder data=new StringBuilder(576);for(int z=0;z<24;z++)for(int x=0;x<24;x++)data.append((char)('0'+Math.max(1,Math.min(9,map[z][x]))));prefs.edit().putString("terrain",data.toString()).putFloat("player_x",px).putFloat("player_z",pz).apply();}
        void move(int x,int z,int jump){double a=Math.toRadians(yaw);float nx=(float)(x*Math.cos(a)-z*Math.sin(a));float nz=(float)(x*Math.sin(a)+z*Math.cos(a));px=Math.max(1,Math.min(22,px+nx));pz=Math.max(1,Math.min(22,pz+nz));if(jump!=0)jumpOffset=1.5f; }
        synchronized void edit(boolean place){int x=Math.max(0,Math.min(23,Math.round(px))),z=Math.max(0,Math.min(23,Math.round(pz)));if(place){if(inventory[selected]>0&&map[z][x]<9){map[z][x]++;blockTypes[z][x]=selected;inventory[selected]--;}}else if(map[z][x]>1){map[z][x]--;inventory[blockTypes[z][x]]=Math.min(999,inventory[blockTypes[z][x]]+1);}}
        @Override public void onSurfaceCreated(javax.microedition.khronos.opengles.GL10 gl, javax.microedition.khronos.egl.EGLConfig c){GLES20.glClearColor(.48f,.72f,.94f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);String vs="uniform mat4 uMVP; attribute vec3 aPos; void main(){gl_Position=uMVP*vec4(aPos,1.0);}";String fs="precision mediump float; uniform vec4 uColor; void main(){gl_FragColor=uColor;}";program=GLES20.glCreateProgram();int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs);GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glLinkProgram(program);pos=GLES20.glGetAttribLocation(program,"aPos");color=GLES20.glGetUniformLocation(program,"uColor");mvpLoc=GLES20.glGetUniformLocation(program,"uMVP");buffer=ByteBuffer.allocateDirect(108*4).order(ByteOrder.nativeOrder()).asFloatBuffer();}
        int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}
        @Override public void onSurfaceChanged(javax.microedition.khronos.opengles.GL10 gl,int w,int h){GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(projection,0,55,(float)w/h,.1f,100f);}
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){jumpOffset=Math.max(0,jumpOffset-0.035f);GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);double angle=Math.toRadians(yaw);float distance=18;float eyeX=px-(float)Math.sin(angle)*distance;float eyeZ=pz-(float)Math.cos(angle)*distance;float eyeY=10+(pitch*.18f);Matrix.setLookAtM(view,0,eyeX,eyeY,eyeZ,px,1+jumpOffset*.15f,pz,0,1,0);Matrix.multiplyMM(vp,0,projection,0,view,0);GLES20.glUseProgram(program);GLES20.glEnableVertexAttribArray(pos);buffer.position(0);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,0,buffer);
            for(int z=0;z<24;z++)for(int x=0;x<24;x++){int h=map[z][x];Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,0,z);Matrix.scaleM(model,0,1,h,1);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);int type=blockTypes[z][x];if(type==0)GLES20.glUniform4f(color,x%3==0?.35f:.42f,.55f,.25f,1);else if(type==1)GLES20.glUniform4f(color,.48f,.29f,.13f,1);else GLES20.glUniform4f(color,.48f,.5f,.54f,1);drawCube();}
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
