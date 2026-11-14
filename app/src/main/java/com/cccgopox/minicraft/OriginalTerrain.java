package com.cccgopox.minicraft;

public final class OriginalTerrain {
    private static final double SEA=22.0;
    private OriginalTerrain() {}
    private static int s1(int seed){return seed*7+1;}
    private static int s2(int seed){return seed*7+2;}
    private static int s3(int seed){return seed*7+3;}
    private static double hash2(int x,int z,int seed){int h=x*374761393+z*668265263+seed*1442695041;h=(h^(h>>>13))*1274126177;h^=h>>>16;return(h&0xFFFFFF)/(double)0x1000000;}
    private static double noise(double x,double z,int seed){int xi=(int)Math.floor(x),zi=(int)Math.floor(z);double fx=x-xi,fz=z-zi;fx=fx*fx*(3-2*fx);fz=fz*fz*(3-2*fz);double a=hash2(xi,zi,seed),b=hash2(xi+1,zi,seed),c=hash2(xi,zi+1,seed),d=hash2(xi+1,zi+1,seed);return(a+(b-a)*fx)*(1-fz)+(c+(d-c)*fx)*fz;}
    private static double hash3(int x,int y,int z,int seed){int h=x*374761393+y*1274126177+z*668265263+seed*1442695041;h=(h^(h>>>13))*1274126177;h^=h>>>16;return(h&0xFFFFFF)/(double)0x1000000;}
    private static double noise3(double x,double y,double z,int seed){int xi=(int)Math.floor(x),yi=(int)Math.floor(y),zi=(int)Math.floor(z);double fx=x-xi,fy=y-yi,fz=z-zi;fx=fx*fx*(3-2*fx);fy=fy*fy*(3-2*fy);fz=fz*fz*(3-2*fz);double a=hash3(xi,yi,zi,seed),b=hash3(xi+1,yi,zi,seed),c=hash3(xi,yi+1,zi,seed),d=hash3(xi+1,yi+1,zi,seed),e=hash3(xi,yi,zi+1,seed),f=hash3(xi+1,yi,zi+1,seed),g=hash3(xi,yi+1,zi+1,seed),h=hash3(xi+1,yi+1,zi+1,seed);double x0=a+(b-a)*fx,x1=c+(d-c)*fx,x2=e+(f-e)*fx,x3=g+(h-g)*fx,y0=x0+(x1-x0)*fy,y1=x2+(x3-x2)*fy;return y0+(y1-y0)*fz;}
    public static int biome(int x,int z,int seed){double t=noise(x/240.0+100,z/240.0-60,s1(seed)+77);return t>.62?1:t<.38?2:0;}
    public static int heightAt(int x,int z,int seed){return(int)(14+noise(x/90.0,z/90.0,s1(seed))*22+noise(x/26.0,z/26.0,s2(seed))*8+noise(x/8.0,z/8.0,s3(seed))*2);}
    public static int oreAt(int x,int y,int z,int seed){if(y<2)return 0;if(noise3(x*.16,y*.16,z*.16,s2(seed)+5)>.85)return 11;if(y<34&&noise3(x*.19+50,y*.19,z*.19,s3(seed)+9)>.88)return 12;if(y<18&&noise3(x*.22+90,y*.22,z*.22,s1(seed)+21)>.92)return 16;if(y<12&&noise3(x*.25+17,y*.25,z*.25+40,s1(seed)+31)>.94)return 17;return 0;}
    public static boolean isCave(int x,int y,int z,int h,int seed){if(y<3)return false;int top=h>SEA+3?h:h-4;if(y>top)return false;double a=noise3(x*.05,y*.08,z*.05,s1(seed)+11)-.5,b=noise3(x*.05+37.1,y*.08+11.3,z*.05+91.7,s1(seed)+13)-.5;if(a*a+b*b<.0013)return true;return y<30&&noise3(x*.028,y*.05,z*.028,s2(seed)+17)>.84;}
}