package com.cccgopox.minicraft;

import android.app.Activity;
import android.os.Bundle;
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
    @Override protected void onPause(){super.onPause(); if(world!=null)world.onPause();}
    @Override protected void onResume(){super.onResume(); if(world!=null)world.onResume();}

    static class WorldView extends GLSurfaceView {
        final WorldRenderer renderer;
        WorldView(Activity a){super(a);setEGLContextClientVersion(2);renderer=new WorldRenderer();setRenderer(renderer);setRenderMode(RENDERMODE_CONTINUOUSLY);}
        void move(int x,int z,int jump){renderer.move(x,z,jump);}
        void edit(boolean place){renderer.edit(place);}
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
        private volatile float px=12,pz=12;
        private volatile int selected=1;
        private volatile float yaw=0;
        WorldRenderer(){Random r=new Random(22);for(int z=0;z<24;z++)for(int x=0;x<24;x++)map[z][x]= (int)(2+Math.sin(x*.4)*1.4+Math.cos(z*.3)*1.2+r.nextDouble()*1.2);}
        void move(int x,int z,int jump){px=Math.max(1,Math.min(22,px+x));pz=Math.max(1,Math.min(22,pz+z));if(jump!=0){} }
        void edit(boolean place){int x=Math.max(0,Math.min(23,Math.round(px))),z=Math.max(0,Math.min(23,Math.round(pz)));if(place)map[z][x]++;else if(map[z][x]>1)map[z][x]--;}
        @Override public void onSurfaceCreated(javax.microedition.khronos.egl.EGLConfig c){GLES20.glClearColor(.48f,.72f,.94f,1);GLES20.glEnable(GLES20.GL_DEPTH_TEST);String vs="uniform mat4 uMVP; attribute vec3 aPos; void main(){gl_Position=uMVP*vec4(aPos,1.0);}";String fs="precision mediump float; uniform vec4 uColor; void main(){gl_FragColor=uColor;}";program=GLES20.glCreateProgram();int v=shader(GLES20.GL_VERTEX_SHADER,vs),f=shader(GLES20.GL_FRAGMENT_SHADER,fs);GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glLinkProgram(program);pos=GLES20.glGetAttribLocation(program,"aPos");color=GLES20.glGetUniformLocation(program,"uColor");mvpLoc=GLES20.glGetUniformLocation(program,"uMVP");buffer=ByteBuffer.allocateDirect(cube.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();buffer.put(cube).position(0);}
        int shader(int type,String src){int s=GLES20.glCreateShader(type);GLES20.glShaderSource(s,src);GLES20.glCompileShader(s);return s;}
        @Override public void onSurfaceChanged(javax.microedition.khronos.egl.EGLConfig c,int w,int h){GLES20.glViewport(0,0,w,h);Matrix.perspectiveM(projection,0,55,(float)w/h,.1f,100f);}
        @Override public void onDrawFrame(javax.microedition.khronos.opengles.GL10 gl){GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT|GLES20.GL_DEPTH_BUFFER_BIT);Matrix.setLookAtM(view,0,px+13,18,pz+19,px,0,pz,0,1,0);Matrix.multiplyMM(vp,0,projection,0,view,0);GLES20.glUseProgram(program);GLES20.glEnableVertexAttribArray(pos);buffer.position(0);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,0,buffer);
            for(int z=0;z<24;z++)for(int x=0;x<24;x++){int h=map[z][x];for(int y=0;y<h;y++){Matrix.setIdentityM(model,0);Matrix.translateM(model,0,x,y,z);Matrix.scaleM(model,0,1,1,1);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);float shade=y==h-1?1f:.62f;GLES20.glUniform4f(color,(x%3==0?.35f:.42f)*shade,(y==h-1?.66f:.42f)*shade,.25f*shade,1);drawCube();}}
            Matrix.setIdentityM(model,0);Matrix.translateM(model,0,px, map[Math.max(0,Math.min(23,(int)pz))][Math.max(0,Math.min(23,(int)px))]+.5f,pz);Matrix.scaleM(model,0,.55f,1.2f,.55f);Matrix.multiplyMM(mvp,0,vp,0,model,0);GLES20.glUniformMatrix4fv(mvpLoc,1,false,mvp,0);GLES20.glUniform4f(color,.12f,.25f,.85f,1);drawCube();GLES20.glDisableVertexAttribArray(pos);
        }
        void drawCube(){int[][] faces={{0,1,2,0,2,3},{4,6,5,4,7,6}}; // top and bottom approximated with two quads
            float[] verts={-.5f,0,-.5f,.5f,0,-.5f,.5f,1,-.5f,-.5f,1,-.5f,-.5f,0,.5f,.5f,0,.5f,.5f,1,.5f,-.5f,1,.5f};
            buffer.clear();buffer.put(verts).position(0);GLES20.glVertexAttribPointer(pos,3,GLES20.GL_FLOAT,false,0,buffer);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN,0,4);GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN,4,4); }
    }
}
