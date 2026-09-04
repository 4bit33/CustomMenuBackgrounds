package com.cmb.cui.client.render;

import com.cmb.cui.util.TextureUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;
import org.bytedeco.ffmpeg.global.avutil;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Plays a WebM/MP4 file behind the menu using JavaCV's FFmpeg bindings.
 * * Decoding happens on a single dedicated background thread that sleeps
 * between frames according to the video's own frame rate (scaled by the
 * user's configured playback speed) - it never decodes faster than
 * playback needs, and the render thread never re-decodes anything: it just
 * uploads whichever decoded frame is currently ready and re-draws it every
 * call to {@link #render}, which is the cheap part.
 * If the file cannot be opened (missing codec, corrupt file, unsupported
 * container) the constructor throws and {@link BackgroundManager} falls
 * back to a static image per the mod spec.
 */
public class VideoBackgroundRenderer implements BackgroundRenderer {

    private final TextureUtil texture = new TextureUtil("video");
    private final ScaleMode scaleMode;
    private final boolean loop;
    private final float speed;

    private final FFmpegFrameGrabber grabber;
    private final Java2DFrameConverter converter = new Java2DFrameConverter();

    private final BlockingQueue<NativeImage> decodedFrames = new ArrayBlockingQueue<>(2);
    private final AtomicBoolean running = new AtomicBoolean(true);
    private Thread decodeThread;

    private final int width;
    private final int height;
    private final double frameDurationSeconds;
    private float frameTimer = 0f;

    public VideoBackgroundRenderer(Path file, ScaleMode scaleMode, boolean loop, float speed) throws IOException {
        this.scaleMode = scaleMode;
        this.loop = loop;
        this.speed = Math.max(0.05f, speed);

        avutil.av_log_set_level(avutil.AV_LOG_QUIET);

        this.grabber = new FFmpegFrameGrabber(file.toFile());
        try {
            grabber.start();
        } catch (Exception e) {
            grabber.releaseUnsafe();
            throw new IOException("Could not open video '" + file.getFileName() + "': " + e.getMessage(), e);
        }

        this.width = grabber.getImageWidth();
        this.height = grabber.getImageHeight();
        double fps = grabber.getVideoFrameRate() > 0 ? grabber.getVideoFrameRate() : 30.0;
        this.frameDurationSeconds = 1.0 / fps;

        startDecodeThread();
    }

    private void startDecodeThread() {
        decodeThread = new Thread(this::decodeLoop, "CustomMenuBackgrounds-VideoDecode");
        decodeThread.setDaemon(true);
        decodeThread.start();
    }

    private void decodeLoop() {
        try {
            while (running.get()) {
                Frame frame = grabber.grabImage();
                if (frame == null) {
                    if (loop) {
                        grabber.setTimestamp(0);
                        continue;
                    } else {
                        break;
                    }
                }
                BufferedImage buffered = converter.convert(frame);
                if (buffered == null) {
                    continue;
                }
                NativeImage image = ImageDecoding.bufferedImageToNativeImage(buffered);

                // Blocks here if the render thread hasn't consumed the previous
                // frame yet - this is the throttle that stops us decoding
                // faster than playback actually needs.
                try {
                    if (!running.get()) {
                        image.close();
                        break;
                    }
                    decodedFrames.put(image);
                } catch (InterruptedException e) {
                    image.close();
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("[CUI] Video decode thread stopped: " + e.getMessage());
        }
    }

    @Override
    public void tick(float deltaSeconds) {
        frameTimer += deltaSeconds * speed;
        double scaledDuration = frameDurationSeconds; // grabber already yields frames in source order
        if (frameTimer >= scaledDuration) {
            frameTimer -= (float) scaledDuration;
            NativeImage next = decodedFrames.poll();
            if (next != null) {
                texture.upload(next);
            }
        }
    }

    @Override
    public void render(DrawContext context, int screenWidth, int screenHeight, float brightness, float overlayOpacity) {
        ScaleMode.Rect rect = scaleMode.compute(screenWidth, screenHeight, width, height);
        texture.drawRect(context, rect.x(), rect.y(), rect.width(), rect.height(), brightness);
        if (overlayOpacity > 0f) {
            context.fill(0, 0, screenWidth, screenHeight, ((int) (overlayOpacity * 255) << 24));
        }
    }

    @Override
    public void close() {
        running.set(false);
        if (decodeThread != null) {
            decodeThread.interrupt();
        }
        NativeImage queued;
        while ((queued = decodedFrames.poll()) != null) {
            queued.close();
        }
        try {
            grabber.stop();
            grabber.release();
        } catch (Exception ignored) {
        }
        texture.close();
    }
}
