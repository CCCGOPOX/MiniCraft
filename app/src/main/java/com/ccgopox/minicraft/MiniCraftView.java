package com.ccgopox.minicraft;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public final class MiniCraftView extends View {
    private static final int WORLD_X = 128, WORLD_Z = 128, WORLD_Y = 40;
    private static final int RW = 200, RH = 112;
    private static final float FOV = 0.78f;
    private final byte[] world = new byte[WORLD_X * WORLD_Y * WORLD_Z];
    private final Bitmap frame = Bitmap.createBitmap(RW, RH, Bitmap.Config.RGB_565);
    private final int[] pixels = new int[RW * RH];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint pixelPaint = new Paint();
    private final SharedPreferences prefs;
    private final Map<Integer, Byte> changed = new HashMap<>();
    private final float density;

    private float px = 64, py = 20, pz = 64;
    private float yaw = 0, pitch = 0;
    private float moveX, moveY;
    private float velocityY;
    private boolean grounded, jumpQueued, running = true;
    private boolean creative = true;
    private int selected = 0;
    private final int[] hotbar = {1, 2, 3, 4, 5, 8, 9, 18, 19};
    private long lastFrame = System.nanoTime();
    private long fpsWindow = System.currentTimeMillis();
    private int fpsCount, fps = 0;
    private float touchLookX, touchLookY;
    private int lookPointer = -1, movePointer = -1;
    private float joyCenterX, joyCenterY;
    private float joyX, joyY;
    private boolean showHelp = true;
    private int hp = 20;

    private static final int[] COLORS = new int[32];

    static {
        COLORS[1] = 0xFF65A843;
        COLORS[2] = 0xFF8A603D;
        COLORS[3] = 0xFF858585;
        COLORS[4] = 0xFFB68A50;
        COLORS[5] = 0xFF397F32;
        COLORS[6] = 0xFF70502D;
        COLORS[7] = 0xFF353535;
        COLORS[8] = 0xFFD8CB91;
        COLORS[9] = 0xFFF1F5FA;
        COLORS[10] = 0xFF2868B8;
        COLORS[11] = 0xFF353535;
        COLORS[12] = 0xFFB8A080;
        COLORS[13] = 0xFFAD5945;
        COLORS[14] = 0xFF8E8981;
        COLORS[15] = 0xFF777777;
        COLORS[18] = 0xFFEAEAEA;
        COLORS[19] = 0xFFFFD46A;
    }

    public MiniCraftView(Context context) {
        super(context);
        prefs = context.getSharedPreferences("minicraft_save", Context.MODE_PRIVATE);
        density = getResources().getDisplayMetrics().density;
        setFocusable(true);
        generateWorld();
        loadGame();
    }

    private int index(int x, int y, int z) {
        return (y * WORLD_Z + z) * WORLD_X + x;
    }

    private int block(int x, int y, int z) {
        if (x < 0 || z < 0 || x >= WORLD_X || z >= WORLD_Z) return 7;
        if (y < 0) return 7;
        if (y >= WORLD_Y) return 0;
        return world[index(x, y, z)] & 255;
    }

    private void setBlock(int x, int y, int z, int id) {
        if (x < 0 || z < 0 || y < 0 || x >= WORLD_X || z >= WORLD_Z || y >= WORLD_Y) return;
        int i = index(x, y, z);
        world[i] = (byte) id;
        changed.put(i, (byte) id);
    }

    private int heightAt(int x, int z) {
        double n = 10
                + Math.sin(x * 0.105) * 2.7
                + Math.cos(z * 0.09) * 2.2
                + Math.sin((x + z) * 0.045) * 2.0
                + Math.cos((x - z) * 0.035) * 1.6;
        return Math.max(4, Math.min(20, (int) n));
    }

    private void generateWorld() {
        Random random = new Random(1700);
        for (int x = 0; x < WORLD_X; x++) {
            for (int z = 0; z < WORLD_Z; z++) {
                int h = heightAt(x, z);
                for (int y = 0; y <= h; y++) {
                    int id = y == h ? 1 : y > h - 4 ? 2 : 3;
                    if (y == 0) id = 7;
                    if (y < h - 5 && random.nextFloat() < 0.018f) id = 11;
                    world[index(x, y, z)] = (byte) id;
                }
                if (h > 8 && random.nextFloat() < 0.018f && x > 3 && z > 3 && x < WORLD_X - 4 && z < WORLD_Z - 4) {
                    for (int y = h + 1; y <= h + 4; y++) world[index(x, y, z)] = 6;
                    for (int ox = -2; ox <= 2; ox++) {
                        for (int oz = -2; oz <= 2; oz++) {
                            for (int oy = 2; oy <= 5; oy++) {
                                if (Math.abs(ox) + Math.abs(oz) + Math.abs(oy - 3) < 6) {
                                    int yy = h + oy;
                                    if (yy < WORLD_Y && world[index(x + ox, yy, z + oz)] == 0)
                                        world[index(x + ox, yy, z + oz)] = 5;
                                }
                            }
                        }
                    }
                }
            }
        }
        px = 64;
        pz = 64;
        py = heightAt(64, 64) + 1.8f;
    }

    private void loadGame() {
        px = prefs.getFloat("x", px);
        py = prefs.getFloat("y", py);
        pz = prefs.getFloat("z", pz);
        yaw = prefs.getFloat("yaw", yaw);
        pitch = prefs.getFloat("pitch", pitch);
        creative = prefs.getBoolean("creative", true);
        hp = prefs.getInt("hp", 20);
        selected = prefs.getInt("selected", 0);
        String data = prefs.getString("blocks", "");
        if (data != null && !data.isEmpty()) {
            String[] entries = data.split(";");
            for (String entry : entries) {
                String[] pair = entry.split(",");
                if (pair.length == 2) {
                    try {
                        int i = Integer.parseInt(pair[0]);
                        int id = Integer.parseInt(pair[1]);
                        if (i >= 0 && i < world.length) world[i] = (byte) id;
                    } catch (Exception ignored) { }
                }
            }
        }
    }

    public void saveGame() {
        StringBuilder data = new StringBuilder();
        for (Map.Entry<Integer, Byte> e : changed.entrySet()) {
            data.append(e.getKey()).append(',').append(e.getValue() & 255).append(';');
        }
        prefs.edit()
                .putFloat("x", px).putFloat("y", py).putFloat("z", pz)
                .putFloat("yaw", yaw).putFloat("pitch", pitch)
                .putBoolean("creative", creative).putInt("hp", hp)
                .putInt("selected", selected).putString("blocks", data.toString()).apply();
    }

    public void pauseGame() {
        running = false;
        saveGame();
    }

    public void resumeGame() {
        running = true;
        lastFrame = System.nanoTime();
        postInvalidate();
    }

    private void update(float dt) {
        float speed = creative ? 5.4f : 3.6f;
        float forward = -moveY;
        float strafe = moveX;
        float vx = (float) (Math.sin(yaw) * forward + Math.cos(yaw) * strafe);
        float vz = (float) (Math.cos(yaw) * forward - Math.sin(yaw) * strafe);
        float len = (float) Math.sqrt(vx * vx + vz * vz);
        if (len > 1) { vx /= len; vz /= len; }
        float nx = px + vx * speed * dt;
        float nz = pz + vz * speed * dt;
        if (!collides(nx, py, pz)) px = nx;
        if (!collides(px, py, nz)) pz = nz;

        if (jumpQueued && grounded) {
            velocityY = 7.0f;
            grounded = false;
        }
        jumpQueued = false;
        if (!creative) velocityY -= 18f * dt;
        else if (moveY < -0.75f) velocityY = 5.5f;
        else if (moveY > 0.75f) velocityY = -5.5f;
        else velocityY = 0;
        float ny = py + velocityY * dt;
        if (ny <= heightAt((int) px, (int) pz) + 1.8f) {
            ny = heightAt((int) px, (int) pz) + 1.8f;
            velocityY = 0;
            grounded = true;
        }
        if (ny < 1.8f) ny = 1.8f;
        py = ny;
    }

    private boolean collides(float x, float y, float z) {
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
        int by = (int) Math.floor(y - 1.65f);
        return block(bx, by, bz) != 0 || block(bx, by + 1, bz) != 0;
    }

    private void renderWorld() {
        float eyeY = py + 0.0f;
        float sin = (float) Math.sin(yaw), cos = (float) Math.cos(yaw);
        float aspect = (float) RW / RH;
        for (int sy = 0; sy < RH; sy++) {
            float ny = (sy - RH * 0.5f) / (RH * 0.5f);
            for (int sx = 0; sx < RW; sx++) {
                float nx = (sx - RW * 0.5f) / (RW * 0.5f);
                float dx = sin + cos * nx * FOV * aspect;
                float dz = cos - sin * nx * FOV * aspect;
                float dy = pitch - ny * FOV;
                float inv = 1f / (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
                dx *= inv; dy *= inv; dz *= inv;
                float t = 0.12f;
                int color = skyColor(sy);
                int lastX = Integer.MIN_VALUE, lastY = 0, lastZ = 0;
                while (t < 42f) {
                    float rx = px + dx * t;
                    float ry = eyeY + dy * t;
                    float rz = pz + dz * t;
                    int bx = (int) Math.floor(rx);
                    int by = (int) Math.floor(ry);
                    int bz = (int) Math.floor(rz);
                    if (bx != lastX || by != lastY || bz != lastZ) {
                        int id = block(bx, by, bz);
                        if (id != 0) {
                            int base = id < COLORS.length && COLORS[id] != 0 ? COLORS[id] : 0xFFAA55AA;
                            float shade = Math.max(0.22f, 1f - t / 52f);
                            if (bx != lastX) shade *= 0.82f;
                            else if (bz != lastZ) shade *= 0.92f;
                            if (by != lastY) shade *= 1.08f;
                            color = shade(base, shade);
                            break;
                        }
                        lastX = bx; lastY = by; lastZ = bz;
                    }
                    t += 0.12f;
                }
                pixels[sy * RW + sx] = color;
            }
        }
        frame.setPixels(pixels, 0, RW, 0, 0, RW, RH);
    }

    private int skyColor(int y) {
        float k = Math.max(0f, Math.min(1f, y / (float) RH));
        int top = 0xFF4B8FCB, bottom = 0xFFB8D8EA;
        return mix(top, bottom, k);
    }

    private int shade(int c, float k) {
        int a = Color.alpha(c);
        int r = Math.min(255, (int) (Color.red(c) * k));
        int g = Math.min(255, (int) (Color.green(c) * k));
        int b = Math.min(255, (int) (Color.blue(c) * k));
        return Color.argb(a, r, g, b);
    }

    private int mix(int a, int b, float t) {
        int r = (int) (Color.red(a) * (1 - t) + Color.red(b) * t);
        int g = (int) (Color.green(a) * (1 - t) + Color.green(b) * t);
        int bl = (int) (Color.blue(a) * (1 - t) + Color.blue(b) * t);
        return Color.rgb(r, g, bl);
    }

    private void breakBlock() {
        Hit hit = traceCenter();
        if (hit != null && hit.id != 7) {
            setBlock(hit.x, hit.y, hit.z, 0);
        }
    }

    private void placeBlock() {
        Hit hit = traceCenter();
        if (hit == null || hit.prevX < 0 || hit.prevY < 0) return;
        int id = hotbar[selected];
        if (block(hit.prevX, hit.prevY, hit.prevZ) == 0)
            setBlock(hit.prevX, hit.prevY, hit.prevZ, id);
    }

    private static final class Hit {
        int x, y, z, prevX, prevY, prevZ, id;
    }

    private Hit traceCenter() {
        float dx = (float) Math.sin(yaw) * (float) Math.cos(pitch);
        float dz = (float) Math.cos(yaw) * (float) Math.cos(pitch);
        float dy = (float) Math.sin(pitch);
        int lx = Integer.MIN_VALUE, ly = 0, lz = 0;
        for (float t = 0.1f; t < 6f; t += 0.08f) {
            int x = (int) Math.floor(px + dx * t);
            int y = (int) Math.floor(py + dy * t);
            int z = (int) Math.floor(pz + dz * t);
            if (x != lx || y != ly || z != lz) {
                int id = block(x, y, z);
                if (id != 0) {
                    Hit h = new Hit();
                    h.x = x; h.y = y; h.z = z; h.id = id;
                    h.prevX = lx; h.prevY = ly; h.prevZ = lz;
                    return h;
                }
                lx = x; ly = y; lz = z;
            }
        }
        return null;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (running) {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - lastFrame) / 1_000_000_000f);
            lastFrame = now;
            update(dt);
            renderWorld();
            fpsCount++;
            if (System.currentTimeMillis() - fpsWindow >= 1000) {
                fps = fpsCount;
                fpsCount = 0;
                fpsWindow = System.currentTimeMillis();
            }
        }
        canvas.drawBitmap(frame, null, new RectF(0, 0, getWidth(), getHeight()), pixelPaint);
        drawHud(canvas);
        if (running) postInvalidateDelayed(16);
    }

    private void drawHud(Canvas c) {
        float w = getWidth(), h = getHeight();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xAA101820);
        c.drawRoundRect(new RectF(w * 0.5f - 18, h * 0.5f - 2, w * 0.5f + 18, h * 0.5f + 2), 2, 2, paint);
        c.drawRoundRect(new RectF(w * 0.5f - 2, h * 0.5f - 18, w * 0.5f + 2, h * 0.5f + 18), 2, 2, paint);

        float slot = Math.min(52 * density, w / 11f);
        float total = slot * 9;
        float start = (w - total) / 2;
        for (int i = 0; i < 9; i++) {
            float x = start + i * slot;
            paint.setColor(i == selected ? 0xFFE8D28A : 0xAA18212A);
            c.drawRoundRect(new RectF(x, h - slot - 12 * density, x + slot - 3, h - 12 * density), 5 * density, 5 * density, paint);
            paint.setColor(COLORS[hotbar[i]] == 0 ? 0xFFFFFFFF : COLORS[hotbar[i]]);
            c.drawRect(x + slot * 0.29f, h - slot * 0.72f - 12 * density, x + slot * 0.68f, h - slot * 0.33f - 12 * density, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(10 * density);
            c.drawText(String.valueOf(i + 1), x + 4 * density, h - 16 * density, paint);
        }

        paint.setColor(0xAA101820);
        c.drawRoundRect(new RectF(18 * density, 18 * density, 138 * density, 48 * density), 8, 8, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(14 * density);
        c.drawText(creative ? "ТВОРЧЕСТВО" : "ВЫЖИВАНИЕ", 28 * density, 38 * density, paint);
        if (!creative) {
            paint.setColor(0xFFE04040);
            c.drawText("♥ " + hp, 150 * density, 38 * density, paint);
        }
        paint.setColor(Color.WHITE);
        paint.setTextSize(11 * density);
        c.drawText(fps + " FPS", w - 70 * density, 24 * density, paint);

        drawControl(c, w - 88 * density, h - 170 * density, "ПРЫЖОК");
        drawControl(c, w - 88 * density, h - 112 * density, "ЛОМАТЬ");
        drawControl(c, w - 88 * density, h - 54 * density, "СТАВИТЬ");

        paint.setColor(0x88607080);
        c.drawCircle(joyCenterX > 0 ? joyCenterX : 82 * density, joyCenterY > 0 ? joyCenterY : h - 110 * density, 48 * density, paint);
        paint.setColor(0xCCB8C8D8);
        c.drawCircle((joyCenterX > 0 ? joyCenterX : 82 * density) + joyX * 30 * density,
                (joyCenterY > 0 ? joyCenterY : h - 110 * density) + joyY * 30 * density, 19 * density, paint);

        if (showHelp) {
            paint.setColor(0xDD101820);
            c.drawRoundRect(new RectF(w * 0.22f, h * 0.08f, w * 0.78f, h * 0.25f), 10, 10, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(14 * density);
            c.drawText("Слева — движение • справа — обзор", w * 0.25f, h * 0.145f, paint);
            c.drawText("Кнопки справа — прыжок, ломать и ставить", w * 0.25f, h * 0.205f, paint);
        }
    }

    private void drawControl(Canvas c, float cx, float cy, String text) {
        paint.setColor(0xAA1B2935);
        c.drawCircle(cx, cy, 29 * density, paint);
        paint.setColor(Color.WHITE);
        paint.setTextSize(9 * density);
        paint.setTextAlign(Paint.Align.CENTER);
        c.drawText(text, cx, cy + 3 * density, paint);
        paint.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        int action = e.getActionMasked();
        int index = e.getActionIndex();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            int id = e.getPointerId(index);
            float tx = e.getX(index), ty = e.getY(index);
            float w = getWidth(), h = getHeight();
            if (tx > w - 125 * density && ty > h - 205 * density && ty < h - 135 * density) {
                jumpQueued = true;
            } else if (tx > w - 125 * density && ty > h - 135 * density && ty < h - 78 * density) {
                breakBlock();
            } else if (tx > w - 125 * density && ty > h - 78 * density) {
                placeBlock();
            } else if (ty > h - 75 * density && tx > w * 0.2f && tx < w * 0.8f) {
                float slot = Math.min(52 * density, w / 11f);
                float start = (w - slot * 9) / 2;
                selected = Math.max(0, Math.min(8, (int) ((tx - start) / slot)));
            } else if (tx < w * 0.43f) {
                movePointer = id;
                joyCenterX = 82 * density;
                joyCenterY = h - 110 * density;
                updateJoystick(tx, ty);
            } else {
                lookPointer = id;
                touchLookX = tx;
                touchLookY = ty;
            }
            showHelp = false;
            return true;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            for (int i = 0; i < e.getPointerCount(); i++) {
                int id = e.getPointerId(i);
                float tx = e.getX(i), ty = e.getY(i);
                if (id == movePointer) updateJoystick(tx, ty);
                if (id == lookPointer) {
                    float dx = tx - touchLookX;
                    float dy = ty - touchLookY;
                    yaw += dx * 0.006f;
                    pitch = Math.max(-1.15f, Math.min(1.15f, pitch - dy * 0.005f));
                    touchLookX = tx;
                    touchLookY = ty;
                }
            }
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP || action == MotionEvent.ACTION_CANCEL) {
            int id = e.getPointerId(index);
            if (id == movePointer) {
                movePointer = -1;
                moveX = moveY = joyX = joyY = 0;
            }
            if (id == lookPointer) lookPointer = -1;
            return true;
        }
        return true;
    }

    private void updateJoystick(float x, float y) {
        float dx = x - joyCenterX, dy = y - joyCenterY;
        float max = 44 * density;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len > max) { dx *= max / len; dy *= max / len; }
        joyX = dx / max;
        joyY = dy / max;
        moveX = joyX;
        moveY = joyY;
    }
}
