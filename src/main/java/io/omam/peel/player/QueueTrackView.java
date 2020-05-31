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
package io.omam.peel.player;

import io.omam.peel.jfx.Jfx;
import io.omam.peel.tracks.Track;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

final class QueueTrackView extends HBox {

    private static final String PLAY_ICON = "play_circle_outline-24px";

    private static final String REMOVE_ICON = "remove_circle_outline-24px";

    private static final PseudoClass PAST = PseudoClass.getPseudoClass("past");

    private static final PseudoClass ODD = PseudoClass.getPseudoClass("odd");

    private final Track track;

    QueueTrackView(final Track aTrack, final int trackIndex, final ActionHandler ah) {
        track = aTrack;
        getStyleClass().add("peel-player-queue-track");
        setAlignment(Pos.CENTER_LEFT);
        if (trackIndex % 2 != 0) {
            pseudoClassStateChanged(ODD, true);
        }
        final VBox vbox = new VBox();
        final Label trackName = new Label(track.name());
        trackName.getStyleClass().add("peel-player-queue-track-name");
        vbox.getChildren().add(trackName);
        final Label albumArtist = new Label(track.album() + " by " + track.artist());
        albumArtist.getStyleClass().add("peel-player-queue-track-album-artist");
        vbox.getChildren().add(albumArtist);

        getChildren().add(vbox);

        Jfx.addSpacing(this);

        final Button play = Jfx.button(PLAY_ICON, "peel-player-queue-track-play");
        play.setOnAction(e -> ah.play(track));
        getChildren().add(play);
        final Button remove = Jfx.button(REMOVE_ICON, "peel-player-queue-track-remove");
        remove.setOnAction(e -> ah.removeFromQueue(trackIndex));
        getChildren().add(remove);
    }

    final void setPast() {
        pseudoClassStateChanged(PAST, true);
    }

    final Track track() {
        return track;
    }

    final void unsetPast() {
        pseudoClassStateChanged(PAST, false);
    }

}
