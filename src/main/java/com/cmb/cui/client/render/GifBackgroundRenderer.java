package com.cmb.cui.client.render;

import com.cmb.cui.util.TextureUtil;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.texture.NativeImage;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Decodes every frame of an animated GIF exactly once at load time (frame
 * compositing/disposal handled the same way ImageIO's GIF reader does, i.e.
 * each frame is drawn onto a running canvas so partial-frame GIFs work),
 * then simply swaps which pre-decoded frame is uploaded to the GPU based on
 * elapsed time. No file re-decoding happens during playback.
 */
public class GifBackgroundRenderer implements BackgroundRenderer {

    private static final class Frame {
        final NativeImage image;
        final int delayMillis;

        Frame(NativeImage image, int delayMillis) {
            this.image = image;
            this.delayMillis = delayMillis;
        }
    }

    private final TextureUtil texture = new TextureUtil("gif");
    private final ScaleMode scaleMode;
    private final List<Frame> frames = new ArrayList<>();
    private int currentFrame = -1;
    private float accumulatedMillis = 0f;
    private int width;
    private int height;
    private boolean loaded;

    public GifBackgroundRenderer(Path file, ScaleMode scaleMode) throws IOException {
        this.scaleMode = scaleMode;
        decode(file);
        loaded = !frames.isEmpty();
        if (loaded) {
            advanceFrame(0);
        }
    }

    private void decode(Path file) throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReadersBySuffix("gif");
        if (!readers.hasNext()) {
            throw new IOException("No GIF ImageReader available on this JVM");
        }
        ImageReader reader = readers.next();
        try (ImageInputStream stream = ImageIO.createImageInputStream(file.toFile())) {
            reader.setInput(stream, false);
            int frameCount = reader.getNumImages(true);

            BufferedImage canvas = null;
            for (int i = 0; i < frameCount; i++) {
                BufferedImage raw = reader.read(i);
                if (canvas == null) {
                    canvas = new BufferedImage(raw.getWidth(), raw.getHeight(), BufferedImage.TYPE_INT_ARGB);
                }
                canvas.getGraphics().drawImage(raw, 0, 0, null);

                int delay = readDelayMillis(reader, i);
                NativeImage native_ = ImageDecoding.bufferedImageToNativeImage(deepCopy(canvas));
                frames.add(new Frame(native_, delay));
            }
            if (!frames.isEmpty()) {
                width = frames.getFirst().image.getWidth();
                height = frames.getFirst().image.getHeight();
            }
        } finally {
            reader.dispose();
        }
    }

    private static BufferedImage deepCopy(BufferedImage source) {
        BufferedImage copy = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copy.getGraphics().drawImage(source, 0, 0, null);
        return copy;
    }

    private static int readDelayMillis(ImageReader reader, int index) {
        try {
            IIOMetadata metadata = reader.getImageMetadata(index);
            String format = metadata.getNativeMetadataFormatName();
            IIOMetadataNode root = (IIOMetadataNode) metadata.getAsTree(format);
            IIOMetadataNode gce = findNode(root, "GraphicControlExtension");
            if (gce != null) {
                String delayAttr = gce.getAttribute("delayTime");
                int centiseconds = Integer.parseInt(delayAttr);
                // GIF delay is in 1/100s; guard against the common "0 delay
                // means as-fast-as-possible" authoring mistake.
                return Math.max(centiseconds * 10, 20);
            }
        } catch (Exception ignored) {
            // Fall through to default below.
        }
        return 100;
    }

    @SuppressWarnings("SameParameterValue") // node type is fixed by the GIF metadata spec
    private static IIOMetadataNode findNode(IIOMetadataNode root, String name) {
        for (int i = 0; i < root.getLength(); i++) {
            org.w3c.dom.Node child = root.item(i);
            if (child.getNodeName().equals(name)) {
                return (IIOMetadataNode) child;
            }
        }
        return null;
    }

    private void advanceFrame(int index) {
        currentFrame = index;
        // TextureUtil#upload takes ownership and closes the image, so we must
        // always hand over a copy to keep our decoded frames for the next loop.
        texture.upload(copyForUpload(frames.get(index)));
    }

    private NativeImage copyForUpload(Frame frame) {
        // TextureUtil#upload takes ownership (and eventually closes) the image
        // it is handed, but we must keep our own decoded frames alive for the
        // whole animation loop, so hand over a light copy instead of the
        // original buffer.
        NativeImage src = frame.image;
        NativeImage copy = new NativeImage(NativeImage.Format.RGBA, src.getWidth(), src.getHeight(), false);
        copy.copyFrom(src);
        return copy;
    }

    @Override
    public void tick(float deltaSeconds) {
        if (!loaded || frames.size() <= 1) {
            return;
        }
        accumulatedMillis += deltaSeconds * 1000f;
        Frame current = frames.get(currentFrame);
        while (accumulatedMillis >= current.delayMillis) {
            accumulatedMillis -= current.delayMillis;
            int next = (currentFrame + 1) % frames.size();
            advanceFrame(next);
            current = frames.get(currentFrame);
        }
    }

    @Override
    public void render(DrawContext context, int screenWidth, int screenHeight, float brightness, float overlayOpacity) {
        if (!loaded) {
            return;
        }
        ScaleMode.Rect rect = scaleMode.compute(screenWidth, screenHeight, width, height);
        texture.drawRect(context, rect.x(), rect.y(), rect.width(), rect.height(), brightness);
        if (overlayOpacity > 0f) {
            context.fill(0, 0, screenWidth, screenHeight, ((int) (overlayOpacity * 255) << 24));
        }
    }

    @Override
    public void close() {
        texture.close();
        for (Frame frame : frames) {
            frame.image.close();
        }
        frames.clear();
    }
}
