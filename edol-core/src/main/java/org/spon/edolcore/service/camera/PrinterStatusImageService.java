package org.spon.edolcore.service.camera;

import lombok.RequiredArgsConstructor;
import org.spon.edol.model.PrinterState;
import org.spon.edolcore.service.PrinterStateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Service
@RequiredArgsConstructor
public class PrinterStatusImageService {

    private final PrinterStateService printerStateService;
    private final CameraSnapshotStore cameraSnapshotStore;

    @Value("${camera.snapshot-dir}")
    private String snapshotDir;

    public File getStatusImage() {
        File plate = Path.of("models", "metadata", "plate_" + printerStateService.getState().getPlateIndex() + ".png").toFile();
        File snapshot = cameraSnapshotStore.getLatestSnapshotFile();

        if (printerStateService.getState().getCurrentFile() != null
                && printerStateService.getState().getFilaments() != null
                && snapshot != null) {
            return renderStatusImage(printerStateService.getState(), snapshot, plate);
        }

        return null;
    }

    public File renderStatusImage(
            PrinterState state,
            File cameraFile,
            File plateFile
    ) {

        try {
            BufferedImage camera = ImageIO.read(cameraFile);

            BufferedImage plate = null;
            if (plateFile != null && plateFile.exists())
                plate = ImageIO.read(plateFile);

            int width = camera.getWidth();
            int height = camera.getHeight();

            BufferedImage result =
                    new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

            Graphics2D g = result.createGraphics();

            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);

            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);

            g.drawImage(camera, 0, 0, null);

            int margin = 20;

            drawInfoPanel(g, state, margin);

            if (plate != null)
                drawPlatePreview(g, plate, width, margin);

            drawProgressBar(g, width, height, state.getProgress());

            g.dispose();

            Path snapshotPath = Paths.get(snapshotDir);
            File file = snapshotPath.resolve("printer_status_tmp.jpg").toFile();

            ImageIO.write(result, "jpg", file);

            return file;
        } catch (IOException | IllegalArgumentException e) {
            return cameraFile;
        }
    }

    private void drawInfoPanel(Graphics2D g, PrinterState state, int margin) {
        int panelWidth = 420;
        int panelHeight = 140;

        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(margin, margin, panelWidth, panelHeight, 20, 20);

        g.setColor(Color.WHITE);

        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        g.drawString(state.getCurrentTask(), margin + 15, margin + 30);

        g.setFont(new Font("SansSerif", Font.PLAIN, 18));

        g.drawString("Progress: " + state.getProgress() + "%",
                margin + 15, margin + 60);

        g.drawString("Layer: " + state.getLayer() + "/" + state.getTotalLayers(),
                margin + 15, margin + 85);

        g.drawString("ETA: " + state.getRemainingTime() + " min",
                margin + 15, margin + 110);

        g.drawString("Nozzle: " + (int) state.getNozzleTemp() + "°C",
                margin + 220, margin + 60);

        g.drawString("Bed: " + (int) state.getBedTemp() + "°C",
                margin + 220, margin + 85);
    }

    private void drawPlatePreview(
            Graphics2D g,
            BufferedImage plate,
            int cameraWidth,
            int margin
    ) {

        int previewWidth = cameraWidth / 4;

        int previewHeight =
                (int) ((double) plate.getHeight() / plate.getWidth() * previewWidth);

        Image scaled =
                plate.getScaledInstance(previewWidth, previewHeight, Image.SCALE_SMOOTH);

        int x = cameraWidth - previewWidth - margin;
        int y = margin;

        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(x - 10, y - 10,
                previewWidth + 20, previewHeight + 20,
                20, 20);

        g.drawImage(scaled, x, y, null);
    }

    private void drawProgressBar(Graphics2D g, int width, int height, int progress) {
        int barHeight = 22;
        int y = height - barHeight - 10;

        g.setColor(new Color(0, 0, 0, 160));
        g.fillRoundRect(10, y - 4, width - 20, barHeight + 8, 12, 12);

        g.setColor(Color.DARK_GRAY);
        g.fillRect(10, y, width - 20, barHeight);

        int filled = (int) ((width - 20) * (progress / 100.0));

        g.setColor(new Color(76, 175, 80));
        g.fillRect(10, y, filled, barHeight);

        g.setColor(Color.WHITE);
        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString(progress + "%", width / 2 - 15, y + 17);
    }
}


