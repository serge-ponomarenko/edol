package org.spon.edolcore.service.camera;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bytedeco.javacv.FFmpegFrameGrabber;
import org.bytedeco.javacv.Frame;
import org.bytedeco.javacv.Java2DFrameConverter;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfiguration;
import org.spon.edolcore.persistence.printer.PrinterConnectionConfigurationRepository;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
class RtspsCameraProvider implements CameraProvider {

    private static final int RTSP_PORT = 322;

    private final PrinterConnectionConfigurationRepository configurationRepository;

    @Override
    public boolean supports(UUID printerId) {
        return true;
    }

    @Override
    public byte[] capture(UUID printerId) {
        PrinterConnectionConfiguration configuration = configuration(printerId);
        String uri =
                "rtsps://bblp:%s@%s:%d/streaming/live/1"
                        .formatted(
                                configuration.getAccessCode(),
                                configuration.getMqttHost(),
                                RTSP_PORT
                        );

        log.info("Opening RTSPS stream: {}", uri.replace(configuration.getAccessCode(), "********"));

        try (
                FFmpegFrameGrabber grabber = new FFmpegFrameGrabber(uri);
                Java2DFrameConverter converter = new Java2DFrameConverter()
        ) {
            grabber.setOption("rtsp_transport", "tcp");

            // 5 sec
            grabber.setOption("stimeout", "5000000");

            // reduce latency
            grabber.setOption("fflags", "nobuffer");
            grabber.setOption("flags", "low_delay");

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
        }
    }

    private PrinterConnectionConfiguration configuration(
            UUID printerId
    ) {
        return configurationRepository
                .findByPrinterId(printerId)
                .orElseThrow(() ->
                        new IllegalStateException(
                                "Configuration not found for printer "
                                        + printerId
                        )
                );
    }

}
