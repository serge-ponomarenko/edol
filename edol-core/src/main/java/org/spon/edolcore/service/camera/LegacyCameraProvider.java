package org.spon.edolcore.service.camera;

import org.spon.edolcore.util.SslUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

@Service
@ConditionalOnProperty(
        value = "edol.printer.camera-provider",
        havingValue = "LEGACY",
        matchIfMissing = true
)
public class LegacyCameraProvider implements CameraProvider {

    @Value("${bambu.camera-url}")
    private String ip;

    @Value("${bambu.access-code}")
    private String password;

    private static final int PORT = 6000;

    @Override
    public boolean supports() {
        return true;
    }

    public byte[] capture() throws Exception {
        SSLSocketFactory factory = SslUtil.createTrustAllSocketFactory();

        try (SSLSocket socket = (SSLSocket) factory.createSocket(ip, PORT)) {
            socket.setSSLParameters(new SSLParameters() {{
                setEndpointIdentificationAlgorithm(null);
            }});

            socket.startHandshake();

            OutputStream out = socket.getOutputStream();
            InputStream in = socket.getInputStream();

            sendAuth(out);

            return readFrame(in);
        }
    }

    private void sendAuth(OutputStream out) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(80);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        buf.putInt(0x40);      // payload size
        buf.putInt(0x3000);    // type
        buf.putInt(0);         // flags
        buf.putInt(0);         // reserved

        writeFixedString(buf, "bblp", 32);
        writeFixedString(buf, password, 32);

        out.write(buf.array());
        out.flush();
    }

    private byte[] readFrame(InputStream in) throws Exception {
        byte[] header = readFully(in, 16);

        ByteBuffer headerBuf =
                ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);

        int payloadSize = headerBuf.getInt();

        byte[] jpeg = readFully(in, payloadSize);

        return jpeg;
    }

    private byte[] readFully(InputStream in, int size) throws Exception {
        byte[] data = new byte[size];
        int offset = 0;

        while (offset < size) {

            int read = in.read(data, offset, size - offset);

            if (read == -1)
                throw new RuntimeException("Stream closed");

            offset += read;
        }

        return data;
    }

    private void writeFixedString(ByteBuffer buf, String value, int size) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);

        int len = Math.min(bytes.length, size);

        buf.put(bytes, 0, len);

        for (int i = len; i < size; i++) {
            buf.put((byte) 0);
        }
    }
}
