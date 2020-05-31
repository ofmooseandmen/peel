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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import io.omam.peel.tracks.Track;
import io.omam.wire.media.MediaInfo;
import io.omam.wire.media.MediaStatus;
import io.omam.wire.media.MediaStatus.PlayerState;
import io.omam.wire.media.QueueItem;

final class MediaSession {

    private static class QueueTrack {

        final String uuid;

        final Track track;

        final QueueItem item;

        QueueTrack(final String anUuid, final Track aTrack, final QueueItem anItem) {
            uuid = anUuid;
            track = aTrack;
            item = anItem;
        }

    }

    static final String UUID_KEY = "UUID";

    private final List<QueueTrack> queue;

    private PlayerState playerState;

    private MediaInfo currentMedia;

    private Optional<Integer> currentItemId;

    MediaSession() {
        queue = new ArrayList<>();
        playerState = PlayerState.IDLE;
        currentMedia = null;
    }

    final Optional<Track> currentTrack() {
        return Optional.ofNullable(currentMedia).flatMap(this::track).map(qt -> qt.track);
    }

    final QueueState insertAll(final List<QueueItem> items, final Map<String, Track> tracks) {
        boolean unsynch = false;
        for (int i = 0; i < items.size() && !unsynch; i++) {
            final QueueItem item = items.get(i);
            if (item.media().customData().isEmpty()) {
                unsynch = true;
            } else {
                final Optional<String> optUuid = uuid(item.media());
                if (optUuid.isEmpty()) {
                    unsynch = true;
                } else {
                    final String uuid = optUuid.get();
                    if (tracks.containsKey(uuid) && queue.stream().noneMatch(qt -> qt.uuid.equals(uuid))) {
                        final QueueTrack qt = new QueueTrack(uuid, tracks.get(uuid), item);
                        try {
                            queue.add(i, qt);
                        } catch (final IndexOutOfBoundsException e) {
                            unsynch = true;
                        }
                    }
                }
            }
        }
        return state(unsynch);
    }

    final boolean isQueueEmpty() {
        return queue.isEmpty();
    }

    final int itemId(final int trackIndex) {
        try {
            return queue.get(trackIndex).item.itemId();
        } catch (final IndexOutOfBoundsException e) {
            return -1;
        }
    }

    final int jumpTo(final Track track) {
        if (currentItemId.isEmpty()) {
            return 0;
        }

        final int curId = currentItemId.get();
        int currentIndex = -1;
        for (int i = 0; i < queue.size(); i++) {
            final MediaSession.QueueTrack qt = queue.get(i);
            if (qt.item.itemId() == curId) {
                currentIndex = i;
                break;
            }
        }
        if (currentIndex == -1) {
            return 0;
        }

        int jumpIndex = -1;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).track == track) {
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
        final Optional<QueueItem> next =
                queue.stream().dropWhile(i -> i.item.itemId() != current).skip(1).findFirst().map(qt -> qt.item);
        return next.map(QueueItem::itemId).orElse(-1);
    }

    final PlayerState playerState() {
        return playerState;
    }

    final void reset() {
        queue.clear();
        playerState = PlayerState.IDLE;
        currentMedia = null;
    }

    final void setPlayerState(final PlayerState state) {
        playerState = state;
    }

    final QueueState synch(final List<QueueItem> items) {
        final List<String> uuids = items
            .stream()
            .map(i -> uuid(i.media()))
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
        queue.removeIf(qt -> !uuids.contains(qt.uuid));
        return state(false);
    }

    final void update(final MediaStatus newStatus) {
        if (newStatus.media().isPresent()) {
            currentMedia = newStatus.media().get();
        }
        currentItemId = newStatus.currentItemId();
        playerState = newStatus.playerState();
    }

    private QueueState state(final boolean unsynch) {
        return new QueueState(queue.stream().map(qt -> qt.track).collect(Collectors.toList()), currentTrack(),
                              unsynch);
    }

    private Optional<QueueTrack> track(final MediaInfo media) {
        return uuid(media).flatMap(u -> queue.stream().filter(qt -> qt.uuid.equals(u)).findFirst());
    }

    @SuppressWarnings("unchecked")
    private Optional<String> uuid(final MediaInfo media) {
        try {
            return media.customData().flatMap(d -> Optional.ofNullable(((Map<String, String>) d).get(UUID_KEY)));
        } catch (final ClassCastException e) {
            return Optional.empty();
        }
    }

}
