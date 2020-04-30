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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

import javafx.application.Application;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.stage.Stage;

@SuppressWarnings("javadoc")
public final class PeelFX extends Application {

    private static final Set<String> SUPPORTED_FORMATS = Set.of("MP3", "AAC", "WAV", "FLAC", "M4A", "M4B", "AIF");

    private Player.Controller player;

    private Library.Controller library;

    private MediaServer server;

    public PeelFX() {
        // empty.
    }

    public static void main(final String[] args) {
        launch(args);
    }

    @Override
    public final void start(final Stage primaryStage) throws Exception {

        Runtime.getRuntime().addShutdownHook(new Thread(this::stop));

        primaryStage.setTitle("Peel");

        final GridPane root = new GridPane();
        root.setMaxWidth(Double.POSITIVE_INFINITY);
        root.setMaxHeight(Double.POSITIVE_INFINITY);

        final ColumnConstraints col1 = new ColumnConstraints();
        col1.setPercentWidth(50);
        final ColumnConstraints col2 = new ColumnConstraints();
        col2.setPercentWidth(50);
        root.getColumnConstraints().addAll(col1, col2);

        root.getStyleClass().add("peel-root");
        final Scene scene = new Scene(root, 1024, 768);
        scene.getStylesheets().add("/css/peel.css");
        primaryStage.setScene(scene);

        final Path libraryRootPath = libraryRootPath();
        final int mediaServerPort = mediaServerPort();
        server = MediaHttpServer.start(libraryRootPath, mediaServerPort);

        player = new Player.Controller(server);
        library = new Library.Controller(libraryRootPath, SUPPORTED_FORMATS, player);

        final Node lw = library.widget();
        GridPane.setVgrow(lw, Priority.ALWAYS);
        GridPane.setHgrow(lw, Priority.ALWAYS);
        root.add(lw, 0, 0);

        final Node pw = player.widget();
        GridPane.setVgrow(pw, Priority.ALWAYS);
        GridPane.setHgrow(pw, Priority.ALWAYS);
        root.add(pw, 1, 0);

        primaryStage.show();

        player.start();
    }

    @Override
    public final void stop() {
        if (library != null) {
            library.shutdown();
        }
        if (player != null) {
            player.shutdown();
        }
        if (server != null) {
            server.stop();
        }
    }

    private Path libraryRootPath() {
        final String value = getParameters().getNamed().get("libraryRootPath");
        if (value == null) {
            throw new IllegalArgumentException("missing --libraryRootPath argument");
        }
        final Path p = Paths.get(value);
        if (!p.toFile().exists()) {
            throw new IllegalArgumentException("libraryRootPath [" + p + "] does not exist");
        }
        return p;
    }

    private int mediaServerPort() {
        final String value = getParameters().getNamed().get("mediaServerPort");
        if (value == null) {
            throw new IllegalArgumentException("missing --mediaServerPort argument");
        }
        try {
            return Integer.parseInt(value);
        } catch (final NumberFormatException e) {
            throw new IllegalArgumentException("mediaServerPort [" + value + "] is not a valid port", e);
        }
    }

}
