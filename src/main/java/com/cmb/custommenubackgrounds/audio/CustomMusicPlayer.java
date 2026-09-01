package com.cmb.custommenubackgrounds.audio;

import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL10;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.stb.STBVorbisInfo;
import org.lwjgl.system.MemoryStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Plays a single custom OGG Vorbis file on Minecraft's existing OpenAL
 * context (Minecraft/LWJGL already initializes OpenAL for vanilla sound, so
 * this reuses that context rather than opening a second one). Decoding the
 * whole file up front into an AL buffer is deliberate: menu music tracks are
 * short enough that streaming isn't worth the complexity, and it avoids any
 * per-frame decode cost while the menu is open.
 */
public class CustomMusicPlayer {

    private int bufferId = -1;
    private int sourceId = -1;
    private boolean playing = false;

    /** Loads and starts playing {@code file}. Call {@link #stop()} before loading another track. */
    public void play(Path file, boolean loop, float volume) throws IOException {
        stop();

        ByteBuffer fileData = readFileToDirectBuffer(file);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            STBVorbisInfo info = STBVorbisInfo.malloc(stack);
            IntBuffer error = stack.mallocInt(1);
            long decoder = STBVorbis.stb_vorbis_open_memory(fileData, error, null);
            if (decoder == 0L) {
                throw new IOException("STBVorbis failed to open '" + file.getFileName() + "' (error code " + error.get(0) + ")");
            }

            STBVorbis.stb_vorbis_get_info(decoder, info);
            int channels = info.channels();
            int sampleRate = info.sample_rate();
            int totalSamples = STBVorbis.stb_vorbis_stream_length_in_samples(decoder);

            ShortBuffer pcm = BufferUtils.createShortBuffer(totalSamples * channels);
            STBVorbis.stb_vorbis_get_samples_short_interleaved(decoder, channels, pcm);
            pcm.flip();
            STBVorbis.stb_vorbis_close(decoder);

            int format = channels == 1 ? AL10.AL_FORMAT_MONO16 : AL10.AL_FORMAT_STEREO16;

            bufferId = AL10.alGenBuffers();
            AL10.alBufferData(bufferId, format, pcm, sampleRate);

            sourceId = AL10.alGenSources();
            AL10.alSourcei(sourceId, AL10.AL_BUFFER, bufferId);
            AL10.alSourcei(sourceId, AL10.AL_LOOPING, loop ? AL10.AL_TRUE : AL10.AL_FALSE);
            AL10.alSourcef(sourceId, AL10.AL_GAIN, clamp01(volume));
            AL10.alSourcePlay(sourceId);
            playing = true;
        } catch (Exception e) {
            stop();
            throw e;
        }
    }

    public void setVolume(float volume) {
        if (sourceId != -1) {
            AL10.alSourcef(sourceId, AL10.AL_GAIN, clamp01(volume));
        }
    }

    public void stop() {
        if (sourceId != -1) {
            AL10.alSourceStop(sourceId);
            AL10.alSourcei(sourceId, AL10.AL_BUFFER, 0);
            AL10.alDeleteSources(sourceId);
            sourceId = -1;
        }
        if (bufferId != -1) {
            AL10.alDeleteBuffers(bufferId);
            bufferId = -1;
        }
        playing = false;
    }

    public boolean isPlaying() {
        return playing;
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static ByteBuffer readFileToDirectBuffer(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        ByteBuffer buffer = BufferUtils.createByteBuffer(bytes.length);
        buffer.put(bytes);
        buffer.flip();
        return buffer;
    }
}
