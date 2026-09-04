package com.cmb.cui.audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plays a single custom OGG Vorbis file via Java Sound (javax.sound.sampled.Clip).
 *
 * <p>Previously this used raw OpenAL (AL10) on Minecraft's context, but on 1.21.11
 * that fails from the render/tick thread with
 * {@code IllegalStateException: No ALCapabilities instance has been set} because
 * vanilla owns OpenAL on its dedicated sound thread. Java Sound uses the OS mixer
 * on its own thread, so no OpenAL capabilities are needed and it works from any
 * thread, across all menu screens.</p>
 *
 * <p>Decoding is still done up front with STB Vorbis (no per-frame cost); the
 * decoded PCM is cached per file so re-entering menus doesn't re-decode.</p>
 */
public class CustomMusicPlayer {

    private Clip clip;
    private boolean playing = false;

    // Simple PCM cache to avoid re-decoding an 8M-sample file every menu enter
    private Path cachedFile;
    private long cachedMtime = -1;
    private long cachedSize = -1;
    private byte[] cachedBytes;
    private AudioFormat cachedFormat;

    /** Loads and starts playing {@code file}. Call {@link #stop()} before loading another track. */
    public synchronized void play(Path file, boolean loop, float volume) throws IOException {
        stop();

        DecodedAudio audio = decodeIfNeeded(file);
        try {
            Clip c = AudioSystem.getClip();
            c.open(audio.format, audio.bytes, 0, audio.bytes.length);
            applyVolume(c, volume);
            if (loop) {
                c.loop(Clip.LOOP_CONTINUOUSLY);
            } else {
                c.start();
            }
            clip = c;
            playing = true;
            System.out.println("[CUI] Custom music started (JavaSound): file=" + file.getFileName()
                    + " sr=" + (int) audio.format.getSampleRate()
                    + " ch=" + audio.format.getChannels()
                    + " bytes=" + audio.bytes.length
                    + " vol=" + volume + " loop=" + loop);
        } catch (Exception e) {
            if (e instanceof IOException ioe) throw ioe;
            throw new IOException("JavaSound failed to play '" + file.getFileName() + "': " + e.getMessage(), e);
        }
    }

    public synchronized void setVolume(float volume) {
        if (clip != null && clip.isOpen()) {
            applyVolume(clip, volume);
        }
    }

    public synchronized void stop() {
        if (clip != null) {
            try {
                clip.stop();
                clip.flush();
                clip.close();
            } catch (Throwable ignored) {
            }
            clip = null;
        }
        playing = false;
    }

    /** True only if the Clip is currently running (audible or looping). */
    public synchronized boolean isActuallyPlaying() {
        if (clip == null) return false;
        try {
            return clip.isRunning();
        } catch (Throwable t) {
            return playing;
        }
    }

    private static void applyVolume(Clip c, float volume) {
        try {
            if (c.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                FloatControl gain = (FloatControl) c.getControl(FloatControl.Type.MASTER_GAIN);
                float v = Math.max(0f, Math.min(1f, volume));
                float dB;
                if (v <= 0.0001f) {
                    dB = gain.getMinimum();
                } else {
                    dB = (float) (20.0 * Math.log10(v));
                    dB = Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), dB));
                }
                gain.setValue(dB);
            }
        } catch (Throwable ignored) {
        }
    }

    private static final class DecodedAudio {
        final AudioFormat format;
        final byte[] bytes;

        DecodedAudio(AudioFormat format, byte[] bytes) {
            this.format = format;
            this.bytes = bytes;
        }
    }

    private synchronized DecodedAudio decodeIfNeeded(Path file) throws IOException {
        long mtime = -1;
        long size = -1;
        try {
            mtime = Files.getLastModifiedTime(file).toMillis();
            size = Files.size(file);
        } catch (IOException ignored) {
        }
        if (cachedBytes != null && file.equals(cachedFile) && mtime == cachedMtime && size == cachedSize) {
            return new DecodedAudio(cachedFormat, cachedBytes);
        }
        DecodedAudio decoded = decodeOgg(file);
        cachedFile = file;
        cachedMtime = mtime;
        cachedSize = size;
        cachedBytes = decoded.bytes;
        cachedFormat = decoded.format;
        return decoded;
    }

    private static DecodedAudio decodeOgg(Path file) throws IOException {
        ByteBuffer fileData = readFileToDirectBuffer(file);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            IntBuffer error = stack.mallocInt(1);
            long decoder = STBVorbis.stb_vorbis_open_memory(fileData, error, null);
            if (decoder == 0L) {
                throw new IOException("STBVorbis failed to open '" + file.getFileName() + "' (error code " + error.get(0) + ")");
            }

            try {
                STBVorbis.stb_vorbis_get_info(decoder, info);
                int channels = info.channels();
                int sampleRate = info.sample_rate();
                int totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);
                boolean lengthKnown = totalSamples > 0;
                if (!lengthKnown) {
                    System.out.println("[CUI] totalSamples unknown (0), will stream-decode until EOF");
                    totalSamples = 48000 * 60; // 1 min initial, grows if needed
                }
                // NOTE: LWJGL's stb_vorbis_get_samples_short_interleaved does NOT advance
                // the ShortBuffer position. We track totalDecoded manually.
                ShortBuffer pcm = BufferUtils.createShortBuffer(totalSamples * channels);
                int totalDecoded = 0;
                int iters = 0;
                while (iters < 5000) {
                    iters++;
                    int shortsSoFar = totalDecoded * channels;
                    if (shortsSoFar >= pcm.capacity()) {
                        // If header length was exact and we've got it all, stop instead of growing
                        if (lengthKnown && totalDecoded >= totalSamples) {
                            break;
                        }
                        int newCap = pcm.capacity() * 2;
                        System.out.println("[CUI] PCM buffer full, growing to " + newCap);
                        ShortBuffer bigger = BufferUtils.createShortBuffer(newCap);
                        pcm.position(0);
                        pcm.limit(shortsSoFar);
                        bigger.put(pcm);
                        pcm = bigger;
                        continue;
                    }
                    pcm.position(shortsSoFar);
                    pcm.limit(pcm.capacity());
                    int decoded = STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
                    if (decoded == 0) break;
                    totalDecoded += decoded;
                }
                System.out.println("[CUI] Vorbis decode finished: decodedSamples=" + totalDecoded + " shorts=" + (totalDecoded * channels) + " cap=" + pcm.capacity() + " iters=" + iters);
                if (totalDecoded == 0) {
                    int err = STBVorbis.stb_vorbis_get_error(decoder);
                    throw new IOException("Decoded 0 samples from " + file.getFileName() + " (stb error=" + err + ", is it valid OGG Vorbis? Opus not supported)");
                }
                int totalShorts = totalDecoded * channels;
                byte[] bytes = new byte[totalShorts * 2];
                // STB writes interleaved shorts starting at index 0 (position was manual).
                // Read via absolute get to avoid position pitfalls.
                pcm.position(0);
                for (int i = 0; i < totalShorts; i++) {
                    short s = pcm.get(i);
                    bytes[i * 2] = (byte) (s & 0xFF);
                    bytes[i * 2 + 1] = (byte) ((s >>> 8) & 0xFF);
                }
                AudioFormat format = new AudioFormat((float) sampleRate, 16, channels, true, false);
                System.out.println("[CUI] Decoding OGG: channels=" + channels + " sr=" + sampleRate + " totalSamples=" + totalSamples + " pcmBytes=" + bytes.length + " file=" + file.getFileName());
                return new DecodedAudio(format, bytes);
            } finally {
                STBVorbis.stb_vorbis_close(decoder);
            }
        }
    }

    private static ByteBuffer readFileToDirectBuffer(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
        buffer.put(bytes);
        buffer.flip();
        return buffer;
    }
}
