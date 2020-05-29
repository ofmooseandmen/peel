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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.omam.peel.tracks.Track;
import io.omam.wire.media.MediaInfo;
import io.omam.wire.media.MediaStatus;
import io.omam.wire.media.MediaStatus.PlayerState;
import io.omam.wire.media.QueueItem;

final class MediaSession {

    private static class QueueTrack {

        final String contentId;

        final Track track;

        QueueItem item;

        QueueTrack(final String aContentId, final Track aTrack) {
            contentId = aContentId;
            track = aTrack;
        }

        final void associate(final QueueItem anItem) {
            item = anItem;
        }

    }

    private final List<MediaSession.QueueTrack> tracks;

    private PlayerState playerState;

    private MediaInfo currentMedia;

    private Optional<Integer> currentItemId;

    MediaSession() {
        tracks = new ArrayList<>();
        playerState = PlayerState.IDLE;
        currentMedia = null;
    }

    final void add(final String contentId, final Track track) {
        tracks.add(new QueueTrack(contentId, track));
    }

    final Optional<Track> currentTrack() {
        return Optional.ofNullable(currentMedia).flatMap(this::track).map(qt -> qt.track);
    }

    final boolean isQueueEmpty() {
        return tracks.isEmpty();
    }

    final int jumpTo(final Track track) {
        if (currentItemId.isEmpty()) {
            return 0;
        }

        final int curId = currentItemId.get();
        int currentIndex = -1;
        for (int i = 0; i < tracks.size(); i++) {
            final MediaSession.QueueTrack qt = tracks.get(i);
            if (qt.item != null && qt.item.itemId() == curId) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex == -1) {
            return 0;
        }

        int jumpIndex = -1;
        for (int i = 0; i < tracks.size(); i++) {
            if (tracks.get(i).track == track) {
                jumpIndex = i;
                break;
            }
        }
        if (jumpIndex == -1) {
            return 0;
        }

        return jumpIndex - currentIndex;
    }

    final int nextItemId() {
        if (currentItemId.isEmpty()) {
            return -1;
        }
        final int current = currentItemId.get();
        final Optional<QueueItem> next = tracks
            .stream()
            .dropWhile(i -> i.item != null && i.item.itemId() != current)
            .skip(1)
            .findFirst()
            .map(qt -> qt.item);
        return next.map(QueueItem::itemId).orElse(-1);
    }

    final PlayerState playerState() {
        return playerState;
    }

    final int remove(final Track track) {
        final int id = tracks
            .stream()
            .filter(qt -> qt.track == track)
            .findFirst()
            .flatMap(qt -> Optional.ofNullable(qt.item))
            .map(QueueItem::itemId)
            .orElse(-1);
        tracks.removeIf(qt -> Objects.equals(qt.track, track));
        return id;
    }

    final void reset() {
        tracks.clear();
        playerState = PlayerState.IDLE;
        currentMedia = null;
    }

    final void setPlayerState(final PlayerState state) {
        playerState = state;
    }

    final Optional<MediaSession.QueueTrack> track(final MediaInfo media) {
        final String contentId = media.contentId();
        return tracks.stream().filter(qt -> qt.contentId.equals(contentId)).findFirst();
    }

    final void update(final MediaStatus newStatus) {
        if (newStatus.media().isPresent()) {
            currentMedia = newStatus.media().get();
        }
        currentItemId = newStatus.currentItemId();
        playerState = newStatus.playerState();
    }

    final List<Track> withQueueItems(final List<QueueItem> queueItems) {
        final List<Track> result = new ArrayList<>();
        for (final QueueItem qi : queueItems) {
            track(qi.media()).ifPresent(qt -> {
                qt.associate(qi);
                result.add(qt.track);
            });
        }
        return result;
    }

}
