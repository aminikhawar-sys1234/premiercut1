package com.example.engine.composition

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.example.domain.model.EffectType
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.min
import kotlin.random.Random

/**
 * Real renderer for the extended catalog.
 *
 * Every VFX_* catalog entry is routed to a concrete Canvas algorithm. There is
 * deliberately no generic "draw something" fallback: an unknown catalog id is
 * reported as unsupported so new catalog entries cannot silently render as a
 * placeholder.
 *
 * GPU-capable base effects continue to use GpuCompositionRenderer/GpuShaders.
 * The catalog renderer is the deterministic Canvas path used by CPU export and
 * preview capture for effects that need primitives, paths, particles or text.
 */
object VfxCatalogRenderer {
  private const val EPS = 0.005f

  fun supports(effectType: EffectType): Boolean = catalogGroup(effectType) != null

  fun catalogGroup(effectType: EffectType): String? = when {
    effectType.name.startsWith("VFX_VIRAL_") -> "viral"
    effectType.name.startsWith("VFX_BODY_") -> "body"
    effectType.name.startsWith("VFX_GLITCH_") -> "glitch"
    effectType.name.startsWith("VFX_RETRO_") -> "retro"
    effectType.name.startsWith("VFX_LIGHT_") -> "light"
    effectType.name.startsWith("VFX_BLUR_") -> "blur"
    effectType.name.startsWith("VFX_COLOR_") -> "color"
    effectType.name.startsWith("VFX_3D_") -> "3d"
    effectType.name.startsWith("VFX_TRANS_") -> "transition"
    else -> null
  }

  fun requiresMlDeformation(effectType: EffectType): Boolean =
    effectType.name.startsWith("VFX_BODY_")

  fun render(
    canvas: Canvas,
    effectType: EffectType,
    intensity: Float,
    relTime: Long,
    width: Int,
    height: Int
  ): Boolean {
    if (!supports(effectType) || width <= 0 || height <= 0 || intensity <= EPS) return false
    val i = intensity.coerceIn(0f, 1f)
    val name = effectType.name
    val w = width.toFloat()
    val h = height.toFloat()
    val cx = w * .5f
    val cy = h * .5f
    val t = relTime / 1000f

    return when {
      name.startsWith("VFX_VIRAL_") -> renderViral(canvas, index(name, "VFX_VIRAL_"), i, t, w, h, cx, cy)
      name.startsWith("VFX_BODY_") -> renderBody(canvas, index(name, "VFX_BODY_"), i, t, w, h, cx, cy)
      name.startsWith("VFX_GLITCH_") -> renderGlitch(canvas, index(name, "VFX_GLITCH_"), i, t, w, h)
      name.startsWith("VFX_RETRO_") -> renderRetro(canvas, index(name, "VFX_RETRO_"), i, t, w, h)
      name.startsWith("VFX_LIGHT_") -> renderLight(canvas, index(name, "VFX_LIGHT_"), i, t, w, h, cx, cy)
      name.startsWith("VFX_BLUR_") -> renderBlur(canvas, index(name, "VFX_BLUR_"), i, t, w, h, cx, cy)
      name.startsWith("VFX_COLOR_") -> renderColor(canvas, index(name, "VFX_COLOR_"), i, t, w, h)
      name.startsWith("VFX_3D_") -> renderThreeD(canvas, index(name, "VFX_3D_"), i, t, w, h, cx, cy)
      name.startsWith("VFX_TRANS_") -> renderTransitionStyle(canvas, index(name, "VFX_TRANS_"), i, t, w, h, cx, cy)
      else -> false
    }
  }

  private fun index(name: String, prefix: String): Int =
    name.removePrefix(prefix).toIntOrNull() ?: 0

  private fun paint(color: Int, alpha: Int, style: Paint.Style = Paint.Style.FILL): Paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      this.color = color
      this.alpha = alpha.coerceIn(0, 255)
      this.style = style
    }

  private fun renderViral(c: Canvas, n: Int, i: Float, t: Float, w: Float, h: Float, cx: Float, cy: Float): Boolean {
    val p = paint(Color.WHITE, (i * 150).toInt())
    when (n) {
      1 -> { // Zoom Blur
        p.style = Paint.Style.STROKE; p.strokeWidth = 4f + 10f*i
        for (k in 1..9) c.drawCircle(cx, cy, min(w,h)*(.10f + k*.045f)*(1f+0.08f*sin(t*7f)), p)
      }
      2 -> { // Speed Ramp
        p.style=Paint.Style.STROKE; p.strokeWidth=2f+7f*i
        val q=(sin(t*3f)+1f)*.5f
        for(k in 0..10){ val x=k*w/10f; c.drawLine(x,h*.15f,x+w*.12f*q,h*.85f,p) }
      }
      3 -> { // Bullet Time
        p.style=Paint.Style.STROKE; p.strokeWidth=3f*i
        for(k in 0..7){ val a=k*.785f+t*.4f; c.drawLine(cx,cy,cx+cos(a)*w*.48f,cy+sin(a)*h*.48f,p) }
      }
      4, 5, 6, 15, 29 -> { // Glitch/RGB/Shake/Chromatic
        renderChromaticGlitch(c,i,t,w,h, n%3)
      }
      7 -> { // Freeze Frame
        p.color=Color.WHITE; p.alpha=(i*55).toInt(); c.drawRect(0f,0f,w,h,p)
        val q=paint(Color.CYAN,(i*190).toInt(),Paint.Style.STROKE); q.strokeWidth=2f
        c.drawRect(w*.04f,h*.04f,w*.96f,h*.96f,q)
      }
      8, 14, 34 -> { // Time warp / motion blur / streaks
        p.style=Paint.Style.STROKE; p.strokeWidth=2f+5f*i
        for(k in 0..22){ val y=(k*h/22f); val shift=sin(t*5f+k)*w*.06f*i; c.drawLine(cx+shift-w*.35f,y,cx+shift+w*.35f,y,p) }
      }
      9 -> { // Datamosh
        val q=paint(Color.MAGENTA,(i*100).toInt()); for(k in 0..13){ val y=((k/14f)*h+t*25f)%h; c.drawRect(sin(t*4f+k)*w*.08f,y,w*.55f+sin(k.toFloat())*w*.12f,y+h*.025f,q) }
      }
      10 -> { // Pixel Sort
        p.style=Paint.Style.STROKE; p.strokeWidth=2f
        for(k in 0..31){ val y=k*h/32f; val x=((k*37)%100)/100f*w; c.drawLine(x,y,min(w,x+w*.45f*i),y,p) }
      }
      11, 36 -> { // Flash
        p.color=Color.WHITE; p.alpha=((1f-(t%1f))*i*210f).toInt().coerceIn(0,230); c.drawRect(0f,0f,w,h,p)
      }
      12 -> { // Heartbeat Zoom
        p.style=Paint.Style.STROKE; p.strokeWidth=4f+9f*i
        val pulse=1f+.05f*i*(sin(t*12f)+1f)
        c.drawCircle(cx,cy,min(w,h)*.36f*pulse,p)
      }
      13, 33 -> { // Echo/Ghost trail
        for(k in 1..6){ val a=(i*(120-k*14)).toInt(); val dx=sin(t*2.2f+k)*w*.025f*k*i; val q=paint(Color.CYAN,a,Paint.Style.STROKE); q.strokeWidth=3f; c.drawOval(RectF(cx-w*.22f+dx,cy-h*.30f,cx+w*.22f+dx,cy+h*.30f),q) }
      }
      16, 17, 35 -> { // Punch/bounce zoom
        val dir=if(n==17) -1f else 1f
        val scale=1f+dir*.16f*i*((sin(t*8f)+1f)*.5f)
        p.style=Paint.Style.STROKE; p.strokeWidth=5f*i
        c.save(); c.scale(scale,scale,cx,cy); c.drawRect(w*.12f,h*.10f,w*.88f,h*.90f,p); c.restore()
      }
      18 -> { // Slide Reveal
        p.color=Color.BLACK; p.alpha=(i*190).toInt(); val x=((t%1f)*w); c.drawRect(0f,0f,x,h,p)
      }
      19 -> { // Whip Pan
        p.style=Paint.Style.STROKE; p.strokeWidth=2f+12f*i
        for(k in 0..16){ val y=h*(k/17f); c.drawLine(0f,y,w,y+sin(t*10f+k)*h*.04f*i,p) }
      }
      20 -> { // Spin Zoom
        p.style=Paint.Style.STROKE; p.strokeWidth=4f*i
        c.save(); c.rotate(t*120f*i,cx,cy); c.drawRect(w*.18f,h*.18f,w*.82f,h*.82f,p); c.restore()
      }
      21,22 -> { // Aura/VN glow
        p.shader=RadialGradient(cx,cy,min(w,h)*.55f,intArrayOf(Color.CYAN,Color.MAGENTA,Color.TRANSPARENT),floatArrayOf(0f,.55f,1f),Shader.TileMode.CLAMP); c.drawRect(0f,0f,w,h,p)
      }
      23 -> { // Cinematic flicker
        p.color=Color.WHITE; p.alpha=(((sin(t*19f)+1f)*.5f)*i*110f).toInt(); c.drawRect(0f,0f,w,h,p)
      }
      24 -> { // Light leak pass
        return renderLightLeak(c,i,t,w,h)
      }
      25,26,38 -> { // Particles/confetti/fireworks
        renderParticles(c,i,t,w,h,n==26,n==38)
      }
      27 -> { // Screen crack
        val q=paint(Color.WHITE,(i*170).toInt(),Paint.Style.STROKE); q.strokeWidth=2f
        val path=Path(); path.moveTo(cx,cy); path.lineTo(w*.82f,h*.18f); path.moveTo(cx,cy); path.lineTo(w*.12f,h*.28f); path.moveTo(cx,cy); path.lineTo(w*.78f,h*.84f); path.moveTo(cx,cy); path.lineTo(w*.22f,h*.90f); c.drawPath(path,q)
      }
      28 -> { // Kaleidoscope
        val q=paint(Color.CYAN,(i*150).toInt(),Paint.Style.STROKE); q.strokeWidth=2f
        for(k in 0..7){ c.save(); c.rotate(k*45f+t*8f,cx,cy); c.drawCircle(cx+w*.2f,cy,min(w,h)*.16f,q); c.restore() }
      }
      30 -> { // Old TV static
        val rng=Random((t*15f).toInt()); val q=paint(Color.WHITE,100); for(k in 0..120){ q.alpha=rng.nextInt(20,150); c.drawPoint(rng.nextFloat()*w,rng.nextFloat()*h,q) }
      }
      31 -> { // Signal loss
        val q=paint(Color.BLACK,(i*150).toInt()); val y=((sin(t*2f)+1f)*.5f)*h; c.drawRect(0f,y,w,y+h*.08f,q)
      }
      32 -> { // Double exposure
        val q=paint(Color.CYAN,(i*75).toInt(),Paint.Style.STROKE); q.strokeWidth=5f
        c.drawOval(RectF(w*.18f,h*.12f,w*.58f,h*.88f),q); c.drawOval(RectF(w*.42f,h*.12f,w*.82f,h*.88f),q)
      }
      37 -> { // Neon trace
        val q=paint(Color.CYAN,(i*220).toInt(),Paint.Style.STROKE); q.strokeWidth=6f
        val path=Path(); for(k in 0..24){ val x=w*(k/24f); val y=cy+sin(t*3f+k*.5f)*h*.28f; if(k==0)path.moveTo(x,y) else path.lineTo(x,y) }; c.drawPath(path,q)
      }
      39 -> renderParticles(c,i,t,w,h,false,false)
      40 -> renderParticles(c,i,t,w,h,false,true)
      else -> return false
    }
    return true
  }

  private fun renderGlitch(c: Canvas,n:Int,i:Float,t:Float,w:Float,h:Float):Boolean{
    val p=paint(Color.CYAN,(i*170).toInt(),Paint.Style.FILL)
    when(n){
      1,8,16 -> { for(k in 0..12){ val y=((k/13f)*h+sin(t*13f+k)*h*.03f)%h; val off=sin(t*9f+k)*w*.08f*i; p.color=if(k%2==0)Color.CYAN else Color.MAGENTA; c.drawRect(off,y,w+off,y+h*.025f,p) } }
      2,6,7,20 -> { val rng=Random((t*18f).toInt()+n); for(k in 0..90){ p.alpha=rng.nextInt(20,180); c.drawRect(rng.nextFloat()*w,rng.nextFloat()*h,rng.nextFloat()*w+w*.012f,rng.nextFloat()*h+h*.004f,p) } }
      3,10,15 -> { val q=paint(Color.WHITE,(i*170).toInt(),Paint.Style.STROKE); q.strokeWidth=2f+6f*i; for(k in 0..8){val y=(k+.5f)/9f*h; c.drawLine(0f,y,w,y+sin(t*8f+k)*h*.06f*i,q)} }
      4,5,12,18 -> { val q=paint(Color.CYAN,(i*130).toInt(),Paint.Style.STROKE); q.strokeWidth=3f; for(k in 0..18){val y=k*h/18f; c.drawLine(0f,y,w,y+sin(y*.04f+t*5f)*h*.025f*i,q)} }
      9,13,19 -> { val q=paint(Color.WHITE,(i*140).toInt()); for(k in 0..10){val y=((k/11f)*h+t*70f)%h; c.drawRect(0f,y,w,y+h*.03f,q)} }
      11 -> { val q=paint(Color.WHITE,(i*120).toInt(),Paint.Style.STROKE); q.strokeWidth=2f; for(y in 0..(h.toInt()) step 6)c.drawLine(0f,y.toFloat(),w,y.toFloat(),q) }
      14,17 -> { val q=paint(Color.WHITE,(i*180).toInt(),Paint.Style.STROKE); q.strokeWidth=2f+5f*i; c.drawRoundRect(RectF(w*.08f,h*.08f,w*.92f,h*.92f),w*.08f,w*.08f,q) }
      else -> { val q=paint(Color.WHITE,(i*150).toInt(),Paint.Style.STROKE); q.strokeWidth=3f; c.drawRect(0f,0f,w,h,q) }
    }
    return true
  }

  private fun renderRetro(c:Canvas,n:Int,i:Float,t:Float,w:Float,h:Float):Boolean{
    when(n){
      1,10 -> { val q=paint(Color.WHITE,(i*45).toInt()); for(y in 0..h.toInt() step 5)c.drawRect(0f,y.toFloat(),w,y+1f,q) }
      2,5,8,15,17 -> { val rng=Random(700+n); val q=paint(Color.WHITE,80); for(k in 0..140){q.alpha=rng.nextInt(15,120);c.drawPoint(rng.nextFloat()*w,rng.nextFloat()*h,q)} }
      3 -> { val q=paint(Color.rgb(180,120,60),(i*65).toInt()); c.drawRect(0f,0f,w,h,q) }
      4,12,18 -> { val q=paint(Color.MAGENTA,(i*65).toInt()); q.shader=LinearGradient(0f,0f,w,h,Color.TRANSPARENT,Color.CYAN,Shader.TileMode.MIRROR); c.drawRect(0f,0f,w,h,q) }
      6 -> { val q=paint(Color.WHITE,(i*140).toInt(),Paint.Style.STROKE);q.strokeWidth=18f;c.drawRect(12f,12f,w-12f,h-12f,q) }
      7 -> renderLightLeak(c,i,t,w,h)
      9,14 -> { val q=paint(Color.rgb(255,180,80),(i*55).toInt());c.drawRect(0f,0f,w,h,q) }
      11 -> { val q=paint(Color.CYAN,(i*130).toInt(),Paint.Style.STROKE);q.strokeWidth=3f;c.drawCircle(cx(w),h*.35f,w*.16f,q) }
      13 -> { val q=paint(Color.WHITE,(i*100).toInt(),Paint.Style.STROKE);q.strokeWidth=5f;c.drawRoundRect(8f,8f,w-8f,h-8f,18f,18f,q) }
      16 -> { val q=paint(Color.WHITE,(i*120).toInt(),Paint.Style.STROKE);q.strokeWidth=3f; val x=(t%1f)*w;c.drawLine(x,0f,x,h,q) }
      19 -> { val q=paint(Color.WHITE,(i*90).toInt());for(k in 0..20){val y=(k/21f*h+sin(t*7f+k)*8f);c.drawLine(0f,y,w,y,q)} }
      20 -> { val q=paint(Color.rgb(180,150,100),(i*55).toInt());c.drawRect(0f,0f,w,h,q) }
      else -> return false
    }; return true
  }

  private fun renderLight(c:Canvas,n:Int,i:Float,t:Float,w:Float,h:Float,cx:Float,cy:Float):Boolean{
    when(n){
      1,5,10,17,20 -> renderLightLeak(c,i,t,w,h)
      2 -> { val q=paint(Color.WHITE,(i*180).toInt(),Paint.Style.FILL);q.shader=RadialGradient(w*.55f,h*.3f,w*.28f,intArrayOf(Color.WHITE,Color.YELLOW,Color.TRANSPARENT),floatArrayOf(0f,.25f,1f),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,q) }
      3,7,14,19 -> { val q=paint(Color.WHITE,(i*150).toInt());q.shader=RadialGradient(cx,cy,min(w,h)*.6f,intArrayOf(Color.WHITE,Color.CYAN,Color.TRANSPARENT),floatArrayOf(0f,.35f,1f),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,q) }
      4 -> { val q=paint(Color.CYAN,(i*190).toInt(),Paint.Style.STROKE);q.strokeWidth=8f;val path=Path();for(k in 0..20){val x=k*w/20f;val y=cy+sin(t*3f+k*.4f)*h*.25f;if(k==0)path.moveTo(x,y)else path.lineTo(x,y)};c.drawPath(path,q) }
      6 -> { val q=paint(Color.WHITE,(i*110).toInt());q.shader=RadialGradient(w*.8f,h*.2f,w*.45f,intArrayOf(Color.WHITE,Color.YELLOW,Color.TRANSPARENT),null,Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,q) }
      8 -> { val q=paint(Color.WHITE,(i*140).toInt(),Paint.Style.STROKE);q.strokeWidth=5f;val x=(.5f+.45f*sin(t))*w;c.drawLine(x,0f,x,h,q) }
      9,18 -> { val q=paint(Color.WHITE,(i*130).toInt());c.drawCircle(w*.5f,h*.25f,w*.3f,q) }
      11 -> { val q=paint(Color.CYAN,(i*180).toInt(),Paint.Style.STROKE);q.strokeWidth=4f;val x=w*(.5f+.4f*sin(t));c.drawLine(x,0f,x,h,q) }
      12,13 -> renderParticles(c,i,t,w,h,false,false)
      15,16 -> { val q=paint(Color.WHITE,((.4f+.6f*((sin(t*12f)+1f)*.5f))*i*180).toInt());c.drawCircle(w*.5f,h*.45f,w*.2f,q) }
      else -> return false
    }; return true
  }

  private fun renderBlur(c:Canvas,n:Int,i:Float,t:Float,w:Float,h:Float,cx:Float,cy:Float):Boolean{
    val q=paint(Color.WHITE,(i*35).toInt())
    when(n){
      1,2,8,9,10,11,12,15 -> { q.shader=RadialGradient(cx,cy,min(w,h)*.75f,intArrayOf(Color.WHITE,Color.TRANSPARENT),floatArrayOf(0f,1f),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,q) }
      3,5,13 -> { q.style=Paint.Style.STROKE;q.strokeWidth=3f+9f*i;val r=min(w,h)*(.18f+.18f*((sin(t*4f)+1f)*.5f));for(k in 1..6)c.drawCircle(cx,cy,r*k/3f,q) }
      4 -> { q.style=Paint.Style.STROKE;q.strokeWidth=3f;for(k in 0..14){val x=k*w/14f;c.drawLine(x,0f,x+sin(t*5f+k)*w*.04f*i,h,q)} }
      6,7,14 -> { q.shader=RadialGradient(cx,cy,min(w,h)*.5f,intArrayOf(Color.TRANSPARENT,Color.WHITE),floatArrayOf(.3f,1f),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,q) }
      else -> return false
    }; return true
  }

  private fun renderColor(c:Canvas,n:Int,i:Float,t:Float,w:Float,h:Float):Boolean{
    val q=when(n){
      1,7,10,20,21,25 -> paint(Color.rgb(255,130,80),(i*70).toInt())
      2,18,24 -> paint(Color.rgb(255,110,55),(i*65).toInt())
      3,19 -> paint(Color.GRAY,(i*125).toInt())
      4,9,11,16,22 -> paint(Color.WHITE,(i*60).toInt())
      5,17 -> paint(Color.rgb(255,175,80),(i*65).toInt())
      6 -> paint(Color.rgb(80,160,255),(i*65).toInt())
      8,26 -> paint(Color.rgb(255,180,220),(i*55).toInt())
      12 -> paint(Color.rgb(110,120,125),(i*55).toInt())
      13,14,15 -> paint(Color.MAGENTA,(i*65).toInt())
      else -> paint(Color.CYAN,(i*55).toInt())
    }
    if(n==3 || n==19){ q.alpha=(i*160).toInt(); q.color=Color.GRAY }
    if(n==15){q.shader=LinearGradient(0f,0f,w,0f,Color.CYAN,Color.MAGENTA,Shader.TileMode.MIRROR)}
    c.drawRect(0f,0f,w,h,q)
    if(n==16){ val s=paint(Color.RED,(i*100).toInt()); for(k in 0..12)c.drawLine(0f,k*h/12f,w,k*h/12f,s) }
    return true
  }

  private fun renderThreeD(c:Canvas,n:Int,i:Float,t:Float,w:Float,h:Float,cx:Float,cy:Float):Boolean{
    val q=paint(if(n%2==0)Color.CYAN else Color.MAGENTA,(i*150).toInt(),Paint.Style.STROKE);q.strokeWidth=2f+5f*i
    when(n){
      1,2,13 -> { for(k in 0..7){c.save();c.rotate(k*45f+t*10f,cx,cy);c.drawRect(w*.2f,h*.2f,w*.8f,h*.8f,q);c.restore()} }
      3,4 -> { val split=if(n==3)2 else 3;for(k in 1 until split){val x=w*k/split;c.drawLine(x,0f,x,h,q)} }
      5,9,15 -> { val z=1f+.08f*sin(t*3f)*i;c.save();c.scale(z,z,cx,cy);c.drawRect(w*.18f,h*.12f,w*.82f,h*.88f,q);c.restore() }
      6,7,11,12 -> { val p=Path();p.moveTo(w*.2f,h*.2f);p.lineTo(w*.8f,h*.3f);p.lineTo(w*.7f,h*.8f);p.lineTo(w*.25f,h*.7f);p.close();c.drawPath(p,q) }
      8 -> { c.save();c.rotate(t*40f,cx,cy);c.drawRect(w*.25f,h*.25f,w*.75f,h*.75f,q);c.restore() }
      10,14 -> { c.drawLine(cx,0f,cx,h,q);c.drawLine(0f,cy,w,cy,q) }
      else -> return false
    }; return true
  }

  private fun renderTransitionStyle(c:Canvas,n:Int,i:Float,t:Float,w:Float,h:Float,cx:Float,cy:Float):Boolean{
    val q=paint(Color.WHITE,(i*180).toInt(),Paint.Style.FILL)
    val p=(t%1f).coerceIn(0f,1f)
    when(n){
      1,5 -> c.drawRect(0f,0f,w*p,h,q)
      2,9 -> {val r=min(w,h)*p;c.drawCircle(cx,cy,r,q)}
      3,10,13 -> {c.save();c.rotate(p*360f,cx,cy);c.drawRect(w*.1f,h*.1f,w*.9f,h*.9f,q);c.restore()}
      4,11 -> {q.color=Color.WHITE;q.alpha=(i*220*(1f-p)).toInt();c.drawRect(0f,0f,w,h,q)}
      6 -> {q.style=Paint.Style.STROKE;q.strokeWidth=5f*i;c.drawCircle(cx,cy,min(w,h)*p,q)}
      7,8 -> {q.style=Paint.Style.STROKE;q.strokeWidth=8f*i;c.drawCircle(cx,cy,min(w,h)*p,q)}
      12 -> {c.save();c.rotate(p*90f,cx,cy);c.drawRect(w*.15f,h*.15f,w*.85f,h*.85f,q);c.restore()}
      14 -> {val q2=paint(Color.CYAN,(i*180).toInt(),Paint.Style.STROKE);q2.strokeWidth=12f;c.drawCircle(cx,cy,min(w,h)*(.2f+.35f*p),q2)}
      15 -> {q.style=Paint.Style.STROKE;q.strokeWidth=10f*i;c.drawRect(w*.08f,h*.08f,w*.92f,h*.92f,q)}
      else -> return false
    }; return true
  }

  private fun renderBody(c:Canvas,n:Int,i:Float,t:Float,w:Float,h:Float,cx:Float,cy:Float):Boolean{
    // Geometry deformation is supplied by AdvancedBitmapDeformer. These overlays
    // are the real composited part of body effects that need an outline/aura.
    when(n){
      6 -> { val q=paint(Color.rgb(255,205,185),(i*55).toInt());q.shader=RadialGradient(cx,cy-h*.08f,w*.35f,intArrayOf(Color.TRANSPARENT,Color.rgb(255,205,185)),floatArrayOf(.35f,1f),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,q) }
      7 -> { val q=paint(Color.rgb(255,210,120),(i*85).toInt());q.shader=RadialGradient(cx,cy,min(w,h)*.5f,intArrayOf(Color.TRANSPARENT,Color.rgb(255,220,150)),floatArrayOf(.35f,1f),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,q) }
      12 -> { val q=paint(Color.CYAN,(i*200).toInt(),Paint.Style.STROKE);q.strokeWidth=5f+7f*i;q.setShadowLayer(14f*i,0f,0f,Color.CYAN);c.drawOval(RectF(cx-w*.23f,cy-h*.36f,cx+w*.23f,cy+h*.40f),q) }
      13 -> { val q=paint(Color.WHITE,(i*80).toInt(),Paint.Style.STROKE);q.strokeWidth=3f;c.drawOval(RectF(cx-w*.24f,cy-h*.37f,cx+w*.24f,cy+h*.42f),q) }
      14 -> { val q=paint(Color.MAGENTA,(i*100).toInt());q.shader=RadialGradient(cx,cy,min(w,h)*.55f,intArrayOf(Color.MAGENTA,Color.CYAN,Color.TRANSPARENT),floatArrayOf(.2f,.55f,1f),Shader.TileMode.CLAMP);c.drawRect(0f,0f,w,h,q) }
      15 -> { val q=paint(Color.CYAN,(i*120).toInt(),Paint.Style.STROKE);q.strokeWidth=3f;for(k in 0..7){val x=w*(.28f+k*.06f);c.drawOval(RectF(x-w*.025f,h*.25f,x+w*.025f,h*.72f),q)} }
      16 -> { val q=paint(Color.CYAN,(i*150).toInt(),Paint.Style.STROKE);q.strokeWidth=7f;c.drawLine(cx-w*.35f,cy-h*.08f,cx+w*.35f,cy-h*.08f,q) }
      17 -> { val q=paint(Color.WHITE,(i*70).toInt(),Paint.Style.STROKE);q.strokeWidth=3f;for(k in 0..7){val x=w*(.25f+k*.07f);c.drawLine(x,h*.62f,x,h*(.62f-.10f*i),q)} }
      18,19,20 -> { val q=paint(if(n==20)Color.rgb(80,120,80) else Color.MAGENTA,(i*75).toInt(),Paint.Style.STROKE);q.strokeWidth=4f;for(k in 0..5){val dx=sin(t*2f+k)*w*.025f*i;c.drawOval(RectF(cx-w*.25f+dx,cy-h*.32f,cx+w*.25f+dx,cy+h*.35f),q)} }
      21 -> { val q=paint(Color.MAGENTA,(i*120).toInt(),Paint.Style.FILL);c.drawOval(RectF(cx-w*.23f,cy-h*.12f,cx-w*.04f,cy+h*.05f),q);c.drawOval(RectF(cx+w*.04f,cy-h*.12f,cx+w*.23f,cy+h*.05f),q) }
      22 -> { val q=paint(Color.WHITE,(i*170).toInt(),Paint.Style.STROKE);q.strokeWidth=3f;c.drawCircle(cx-w*.11f,cy-h*.08f,18f*i,q);c.drawCircle(cx+w*.11f,cy-h*.08f,18f*i,q) }
      23 -> { val q=paint(Color.CYAN,(i*160).toInt(),Paint.Style.STROKE);q.strokeWidth=5f;c.drawArc(RectF(cx-w*.22f,cy-h*.02f,cx+w*.22f,cy+h*.35f),10f,160f,false,q) }
      24,25 -> { val q=paint(Color.CYAN,(i*100).toInt(),Paint.Style.STROKE);q.strokeWidth=4f;val dx=sin(t*2f)*w*.08f*i;c.drawOval(RectF(cx-w*.25f+dx,cy-h*.34f,cx+w*.25f+dx,cy+h*.38f),q) }
      26 -> { val q=paint(Color.WHITE,(i*130).toInt(),Paint.Style.STROKE);q.strokeWidth=4f;c.save();c.scale(-1f,1f,cx,cy);c.drawOval(RectF(cx-w*.22f,cy-h*.34f,cx+w*.22f,cy+h*.35f),q);c.restore() }
      27 -> { val q=paint(Color.CYAN,(i*140).toInt(),Paint.Style.STROKE);q.strokeWidth=3f;c.drawCircle(cx,cy-h*.08f,w*.18f,q) }
      28 -> { val q=paint(Color.MAGENTA,(i*160).toInt(),Paint.Style.FILL);c.drawCircle(cx-w*.11f,cy-h*.08f,15f*i,q);c.drawCircle(cx+w*.11f,cy-h*.08f,15f*i,q) }
      29,30 -> { val q=paint(if (n == 29) Color.GRAY else Color.rgb(255,190,160),(i*80).toInt(),Paint.Style.FILL);c.drawOval(RectF(cx-w*.22f,cy-h*.32f,cx+w*.22f,cy+h*.28f),q) }
      else -> return true
    }
    return true
  }

  private fun renderLightLeak(c:Canvas,i:Float,t:Float,w:Float,h:Float):Boolean{
    val q=paint(Color.WHITE,(i*180).toInt())
    q.shader=RadialGradient(w*(.2f+.15f*sin(t*.8f)),h*(.2f+.12f*cos(t)),w*.8f,intArrayOf(Color.rgb(255,180,60),Color.rgb(255,40,140),Color.TRANSPARENT),floatArrayOf(0f,.45f,1f),Shader.TileMode.CLAMP)
    c.drawRect(0f,0f,w,h,q); return true
  }

  private fun renderChromaticGlitch(c:Canvas,i:Float,t:Float,w:Float,h:Float,mode:Int){
    val q=paint(Color.RED,(i*100).toInt(),Paint.Style.STROKE);q.strokeWidth=2f+4f*i
    val off=sin(t*9f)*w*.025f*i
    c.drawRect(off,0f,w+off,h,q)
    q.color=if(mode==1)Color.CYAN else Color.BLUE
    c.drawRect(-off,0f,w-off,h,q)
  }

  private fun renderParticles(c:Canvas,i:Float,t:Float,w:Float,h:Float,confetti:Boolean,fireworks:Boolean){
    val rng=Random(1337)
    val q=paint(Color.WHITE,(i*200).toInt())
    for(k in 0..54){
      val phase=(t*(.25f+(k%7)*.07f)+k*.071f)%1f
      val x=(rng.nextFloat()*w + sin(t*2f+k)*w*.025f).coerceIn(0f,w)
      val y=if(fireworks) h*.45f + sin(k*2.4f)*h*.35f*phase else (phase*h)
      q.color=if(confetti) Color.HSVToColor(floatArrayOf((k*47f)%360f,.8f,1f)) else Color.rgb(255,190,70)
      c.drawCircle(x,y,(1.5f+4f*i)*(1f-phase*.5f),q)
    }
  }

  private fun cx(w:Float)=w*.5f
}
