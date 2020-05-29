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
package io.omam.peel.library;

import java.util.HashMap;
import java.util.Map;

import io.omam.peel.tracks.Artist;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

final class ArtistsView extends VBox {

    private VBox pending;

    private final Map<String, VBox> artists;

    ArtistsView() {
        getStyleClass().add("peel-library-artists");
        artists = new HashMap<>();
        pending = null;
    }

    final void clear() {
        getChildren().clear();
        artists.clear();
        pending = null;
    }

    final void done() {
        if (pending != null) {
            getChildren().add(pending);
        }
        pending = null;
    }

    final void withArtist(final Artist artist, final TextField searchField) {
        final VBox vbox;
        if (!artists.containsKey(artist.firstChar())) {
            vbox = new VBox();
            final Label label = new Label(artist.firstChar());
            label.getStyleClass().add("peel-library-artist-index");
            label.setMaxWidth(Double.MAX_VALUE);
            vbox.getChildren().add(label);
            artists.put(artist.firstChar(), vbox);
            if (pending != null) {
                getChildren().add(pending);
            }
            pending = vbox;
        } else {
            vbox = artists.get(artist.firstChar());
        }
        vbox.getChildren().add(new ArtistView(artist, searchField));
    }
}