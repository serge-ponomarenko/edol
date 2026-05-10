package org.spon.edoldashboard.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import org.spon.edoldashboard.model.entity.Filament;
import org.spon.edoldashboard.model.entity.FilamentSpool;
import org.spon.edoldashboard.model.entity.MaterialType;
import org.spon.edoldashboard.model.entity.Vendor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class LabelService {

    @Value("${edol-dashboard.qr-url}")
    private String baseUrl;

    public static void main(String[] args) throws Exception {
        FilamentSpool spool = new FilamentSpool();
        spool.setId(3564L);
        Filament filament = new Filament();
        Vendor vendor = new Vendor(1L, "JAMG HE", "");
        filament.setVendor(vendor);
        MaterialType materialType = new MaterialType(1L, "PETG-HS");
        filament.setMaterialType(materialType);
        filament.setBrand("High Speed");
        filament.setColorHex("#FFAABB");
        spool.setOpenedAt(LocalDateTime.now());
        spool.setFilament(filament);

        LabelService labelService = new LabelService();
        labelService.baseUrl = "http://edol.local:8090";
        byte[] bytes = labelService.generateLabel(spool);
        Files.write(Paths.get("./qr.png"), bytes);
    }

    public byte[] generateLabel(FilamentSpool spool) throws Exception {
        int width = 384;  // 58mm thermal printer
        int height = 155;

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_BINARY);
        Graphics2D g = image.createGraphics();

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);

        g.setColor(Color.BLACK);

        var filament = spool.getFilament();

        String url = baseUrl + "/s/" + spool.getId();

        // 🔳 QR
        BufferedImage qr = generateQr(url, 170);
        g.drawImage(qr, -12, -12, null);

        int xText = 160;
        int y = 24;

        g.setFont(new Font("SansSerif", Font.BOLD, 16));
        g.drawString("ID: " + spool.getId(), xText, y);

        y += 20;
        g.setFont(new Font("SansSerif", Font.PLAIN, 16));
        g.drawString(filament.getVendor().getName(), xText, y);

        y += 22;
        g.drawString(filament.getMaterialType().getName(), xText, y);

        y += 22;
        g.drawString(filament.getBrand(), xText, y);

        y += 22;

        String hex = filament.getColorHex();
        String rgb = hexToRgb(hex);

        g.drawString(hex + " (" + rgb + ")", xText, y);

        y += 24;
        g.drawString(formatDate(spool.getOpenedAt()), xText, y);

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);

        return baos.toByteArray();
    }

    private BufferedImage generateQr(String text, int size) throws Exception {
        BitMatrix matrix = new MultiFormatWriter()
                .encode(text, BarcodeFormat.QR_CODE, size, size);

        return MatrixToImageWriter.toBufferedImage(matrix);
    }

    private String hexToRgb(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return r + "," + g + "," + b;
    }

    private String formatDate(LocalDateTime date) {
        if (date == null) return "-";
        return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}
