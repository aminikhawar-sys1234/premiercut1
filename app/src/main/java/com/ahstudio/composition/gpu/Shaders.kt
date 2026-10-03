package com.ahstudio.composition.gpu

object Shaders {
    val VERT = """#version 300 es
        uniform mat4 uClipFromMedia;   // media px -> clip (world transform + comp->clip baked in)
        uniform vec4 uUvXform;         // scale/offset for source uv (OES ST matrix support)
        in vec2 aPos; out vec2 vUv;
        void main(){ vUv = aPos * uUvXform.xy + uUvXform.zw;
                     gl_Position = uClipFromMedia * vec4(aPos, 0.0, 1.0); }"""

    val COPY_FS = """#version 300 es
        precision highp float; uniform sampler2D uTex; in vec2 vUv; out vec4 fragColor;
        void main(){ fragColor = texture(uTex, vUv); }"""

    val COPY_OES_FS = """#version 300 es
        #extension GL_OES_EGL_image_external_essl3 : require
        precision highp float; uniform samplerExternalOES uTex; in vec2 vUv; out vec4 fragColor;
        void main(){ fragColor = texture(uTex, vUv); }"""

    val SOLID_FS = """#version 300 es
        precision highp float; uniform vec4 uColor; in vec2 vUv; out vec4 fragColor;
        void main(){ fragColor = uColor; }"""

    val BLEND_FS = """#version 300 es
        precision highp float;
        uniform sampler2D uBackdrop; uniform sampler2D uLayer;
        uniform int uBlendMode; uniform int uCompositeOp; uniform float uOpacity;
        in vec2 vUv; out vec4 fragColor;

        vec3 unprem(vec4 p){ return p.a > 1e-5 ? p.rgb / p.a : vec3(0.0); }
        float hardL(float b,float s){ return s<=0.5 ? b*(2.0*s) : b+(2.0*s-1.0)-b*(2.0*s-1.0); }
        float dodge(float b,float s){ if(b<=0.0)return 0.0; if(s>=1.0)return 1.0; return min(1.0,b/(1.0-s)); }
        float burn (float b,float s){ if(b>=1.0)return 1.0; if(s<=0.0)return 0.0; return 1.0-min(1.0,(1.0-b)/s); }
        float D(float x){ return x<=0.25 ? ((16.0*x-12.0)*x+4.0)*x : sqrt(x); }
        float softL(float b,float s){ return s<=0.5 ? b-(1.0-2.0*s)*b*(1.0-b) : b+(2.0*s-1.0)*(D(b)-b); }
        float blendCh(int m,float b,float s){
            if(m==1) return b*s; if(m==2) return b+s-b*s; if(m==3) return hardL(s,b);
            if(m==4) return softL(b,s); if(m==5) return hardL(b,s); if(m==6) return dodge(b,s);
            if(m==7) return burn(b,s);  if(m==8) return min(b,s); if(m==9) return max(b,s);
            if(m==10) return abs(b-s);  if(m==11) return b+s-2.0*b*s;
            if(m==12) return min(1.0,b+s); if(m==13) return max(0.0,b-s); return s; }
        float lum(vec3 c){ return dot(c, vec3(0.3,0.59,0.11)); }
        vec3 clipC(vec3 c){ float l=lum(c); float n=min(c.r,min(c.g,c.b)); float x=max(c.r,max(c.g,c.b));
            if(n<0.0) c=l+(c-l)*l/(l-n); if(x>1.0) c=l+(c-l)*(1.0-l)/(x-l); return c; }
        vec3 setLum(vec3 c,float l){ return clipC(c+(l-lum(c))); }
        float satOf(vec3 c){ return max(c.r,max(c.g,c.b))-min(c.r,min(c.g,c.b)); }
        vec3 setSat(vec3 c,float s){ float mn=min(c.r,min(c.g,c.b)); float mx=max(c.r,max(c.g,c.b)); float rg=mx-mn; vec3 o;
            o.r=(c.r>=mx)?s:(rg>0.0?(c.r-mn)*s/rg:0.0);
            o.g=(c.g>=mx)?s:(rg>0.0?(c.g-mn)*s/rg:0.0);
            o.b=(c.b>=mx)?s:(rg>0.0?(c.b-mn)*s/rg:0.0); return o; }
        vec3 blendV(int m, vec3 b, vec3 s){
            if(m==14) return setLum(setSat(s,satOf(b)),lum(b));
            if(m==15) return setLum(setSat(b,satOf(s)),lum(b));
            if(m==16) return setLum(s,lum(b));
            if(m==17) return setLum(b,lum(s));
            return vec3(blendCh(m,b.r,s.r), blendCh(m,b.g,s.g), blendCh(m,b.b,s.b)); }
        vec4 pd(int op, vec3 CsPm, float as, vec3 CbPm, float ab){
            vec3 co; float ao;
            if(op==0){ co=CsPm+CbPm*(1.0-as); ao=as+ab*(1.0-as); }          // SRC_OVER
            else if(op==1){ co=CsPm*ab; ao=as*ab; }                          // SRC_IN
            else if(op==2){ co=CsPm*(1.0-ab); ao=as*(1.0-ab); }              // SRC_OUT
            else if(op==3){ co=CsPm*ab+CbPm*(1.0-as); ao=ab; }               // SRC_ATOP
            else if(op==4){ co=CbPm+CsPm*(1.0-ab); ao=ab+as*(1.0-ab); }      // DST_OVER
            else if(op==5){ co=CbPm*as; ao=as*ab; }                          // DST_IN
            else if(op==6){ co=CbPm*(1.0-as); ao=ab*(1.0-as); }              // DST_OUT
            else { co=CbPm*as+CsPm*(1.0-ab); ao=as; }                        // DST_ATOP
            return vec4(co, ao); }
        void main(){
            vec4 bd = texture(uBackdrop, vUv);
            vec4 ly = texture(uLayer, vUv);
            float ab = bd.a; float as = clamp(ly.a * uOpacity, 0.0, 1.0);
            vec3 Cb = unprem(bd); vec3 Cs = unprem(ly);
            vec3 B = blendV(uBlendMode, Cb, Cs);
            vec3 CsB = (uCompositeOp==0) ? B : Cs;
            vec3 CsPm = CsB * as; vec3 CbPm = bd.rgb;
            fragColor = pd(uCompositeOp, CsPm, as, CbPm, ab); }"""

    val MATTE_FS = """#version 300 es
        precision highp float;
        uniform sampler2D uSource; uniform sampler2D uMatte; uniform int uMode;
        in vec2 vUv; out vec4 fragColor;
        void main(){
            vec4 s = texture(uSource, vUv); vec4 m = texture(uMatte, vUv);
            vec3 mr = m.a > 1e-5 ? m.rgb / m.a : vec3(0.0);
            float a = (uMode>=2) ? dot(mr, vec3(0.2126,0.7152,0.0722)) : m.a;
            if (uMode==1 || uMode==3) a = 1.0 - a;
            fragColor = vec4(s.rgb * a, s.a * a); }"""

    val MASK_COMBINE_FS = """#version 300 es
        precision highp float;
        uniform sampler2D uDst; uniform sampler2D uShape;
        uniform int uMode; uniform float uOpacity; uniform float uInvert;
        in vec2 vUv; out vec4 fragColor;
        void main(){
            float d = texture(uDst, vUv).a;
            float s = texture(uShape, vUv).a * uOpacity;
            s = mix(s, 1.0 - s, uInvert);
            float o; if(uMode==0) o=d+s-d*s; else if(uMode==1) o=d*(1.0-s);
            else if(uMode==2) o=d*s; else o=abs(d-s);
            fragColor = vec4(o,o,o,o); }"""

    val MASK_MULTIPLY_FS = """#version 300 es
        precision highp float;
        uniform sampler2D uLayer; uniform sampler2D uMask; in vec2 vUv; out vec4 fragColor;
        void main(){ vec4 s = texture(uLayer, vUv); float m = texture(uMask, vUv).a;
            fragColor = vec4(s.rgb * m, s.a * m); }"""

    val BLUR_FS = """#version 300 es
        precision highp float;
        uniform sampler2D uTex; uniform vec2 uStep; uniform int uRadius; uniform float uSigma;
        in vec2 vUv; out vec4 fragColor;
        void main(){ float a=0.0; float wsum=0.0;
            for(int i=-32;i<=32;++i){ if(i<-uRadius||i>uRadius) continue;
                float w=exp(-float(i*i)/(2.0*uSigma*uSigma+1e-6));
                a+=texture(uTex, vUv+uStep*float(i)).a*w; wsum+=w; }
            a/=wsum; fragColor=vec4(a,a,a,a); }"""
}
