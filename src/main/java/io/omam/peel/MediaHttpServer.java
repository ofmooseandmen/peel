/*
Copyright 2020-2020 Cedric Liegeois

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright
      notice, this list of conditions and the following disclaimer.

    * Redistributions in binary form must reproduce the above
      copyright notice, this list of conditions and the following
      disclaimer in the documentation and/or other materials provided
      with the distribution.

    * Neither the name of the copyright holder nor the names of other
      contributors may be used to endorse or promote products derived
      from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
"AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/
package io.omam.peel;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

@SuppressWarnings("javadoc")
final class MediaHttpServer implements MediaServer {

    private static final class Handler implements HttpHandler {

        private final String root;

        Handler(final String aRoot) {
            root = aRoot;
        }

        @Override
        public final void handle(final HttpExchange exchange) throws IOException {
            if ("GET".equals(exchange.getRequestMethod())) {
                /* device will only request a file. */
                final String rPath = exchange.getRequestURI().getPath();
                if (rPath == null) {
                    sendError(400, "Missing path", exchange);
                } else {
                    final Path path = Paths.get(root, URLDecoder.decode(rPath, StandardCharsets.UTF_8));
                    final File file = path.toFile();
                    if (!file.exists() || file.isDirectory()) {
                        sendError(404, "File not found", exchange);
                    } else {
                        sendFile(path, exchange);
                    }
                }
            } else {
                sendError(405, exchange.getRequestMethod() + " not supported", exchange);
            }
        }

        private void sendError(final int errorCode, final String errorText, final HttpExchange exchange)
                throws IOException {
            LOGGER.warning(() -> "Error " + errorCode + " - " + errorText);
            try (final OutputStream out = exchange.getResponseBody()) {
                exchange.sendResponseHeaders(errorCode, errorText.length());
                out.write(errorText.getBytes());
                out.flush();
                out.close();
            }
        }

        private void sendFile(final Path path, final HttpExchange exchange) throws IOException {
            LOGGER.info(() -> "Sending file " + path);
            try (final OutputStream out = exchange.getResponseBody()) {
                final File f = path.toFile();
                try (final FileInputStream fis = new FileInputStream(f)) {
                    final String contentType = Files.probeContentType(path);
                    exchange.getResponseHeaders().put("Content-type", Arrays.asList(contentType));
                    final int length = (int) f.length();
                    exchange.sendResponseHeaders(200, length);
                    final byte[] buffer = new byte[length];
                    fis.read(buffer, 0, length);
                    out.write(buffer, 0, length);
                    out.flush();
                    out.close();
                }
            }
        }

    }

    private static final Logger LOGGER = Logger.getLogger(MediaHttpServer.class.getName());

    private final Path root;

    private final HttpServer httpServer;

    private final String ip;

    private final int port;

    private MediaHttpServer(final Path aRoot, final String anIp, final HttpServer aHttpServer) {
        root = aRoot;
        httpServer = aHttpServer;
        ip = anIp;
        port = httpServer.getAddress().getPort();
    }

    static MediaHttpServer start(final Path root, final int port) throws IOException {
        final InetSocketAddress addr = new InetSocketAddress(port);
        final HttpServer httpServer = HttpServer.create(addr, 0);
        httpServer.createContext("/", new Handler(root.toString())).setAuthenticator(null);
        httpServer.setExecutor(Executors.newCachedThreadPool(new PeelThreadFactory("media-server")));
        httpServer.start();
        final String localIp = InetAddress.getLocalHost().getHostAddress();
        LOGGER.info(() -> " Server started:  " + addr);
        return new MediaHttpServer(root, localIp, httpServer);
    }

    @Override
    public final String resolveUrl(final Path localPath) {
        final String path = URLEncoder.encode(root.relativize(localPath).toString(), StandardCharsets.UTF_8);
        return "http://" + ip + ":" + port + "/" + path;
    }

    @Override
    public final void stop() {
        /* wait 1 second. */
        httpServer.stop(1);
    }

}
