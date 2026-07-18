package org.spon.edolcore.service.camera;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(
        value = "edol.printer.camera-provider",
        havingValue = "RTSPS"
)
class RtspsCameraProvider implements CameraProvider {

    private static final int RTSP_PORT = 322;

    @Value("${bambu.host}")
    private String bambuHost;
    @Value("${bambu.access-code}")
    private String accessCode;

    @Override
    public byte[] capture() {

        String uri =
                "rtsps://bblp:%s@%s:%d/streaming/live/1"
                        .formatted(
                                accessCode,
                                bambuHost,
                                RTSP_PORT
                        );

        log.info("Opening RTSPS stream: {}", uri.replace(accessCode, "********"));

        FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(uri);

        grabber.setOption("rtsp_transport", "tcp");

        // 5 sec
        grabber.setOption("stimeout", "5000000");

        // reduce latency
        grabber.setOption("fflags", "nobuffer");
        grabber.setOption("flags", "low_delay");

        Java2DFrameConverter converter =
                new Java2DFrameConverter();

        try {
            grabber.start();

            Frame frame = null;

            for (int i = 0; i < 30; i++) {
                frame = grabber.grabImage();

                if (frame != null) {
                    break;
                }
            }

            if (frame == null) {
                throw new IOException("Unable to receive frame from RTSPS stream.");
            }

            BufferedImage image =
                    converter.convert(frame);

            if (image == null) {
                throw new IOException("Unable to decode RTSPS frame.");
            }

            ByteArrayOutputStream out =
                    new ByteArrayOutputStream();

            ImageIO.write(
                    image,
                    "jpg",
                    out
            );

            return out.toByteArray();

        } catch (IOException e) {
            log.error("Error!", e);
            throw new RuntimeException(e);
        } finally {
            try {
                grabber.stop();
            } catch (Exception ignored) {
            }
            try {
                grabber.close();
            } catch (Exception ignored) {
            }
            converter.close();
        }
    }

    @Override
    public boolean supports() {
        return false;
    }

}