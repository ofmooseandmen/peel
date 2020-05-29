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
package io.omam.peel.jfx;

import java.io.IOException;
import java.io.InputStream;

import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

@SuppressWarnings("javadoc")
public final class Jfx {

    private Jfx() {
        // empty.
    }

    public static void addSpacing(final HBox hbox) {
        final Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        hbox.getChildren().add(region);
    }

    public static void addSpacing(final VBox vbox) {
        final Region region = new Region();
        VBox.setVgrow(region, Priority.ALWAYS);
        vbox.getChildren().add(region);
    }

    public static Button button(final String iconName, final String styleclass) {
        final Button button = new Button();
        button.setGraphic(icon(iconName));
        button.getStyleClass().add(styleclass);
        return button;
    }

    public static Region icon(final String name) {
        try (final InputStream is = Jfx.class.getClassLoader().getResourceAsStream("icons/" + name + ".svg")) {
            return SvgParser.parse(is);
        } catch (final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
