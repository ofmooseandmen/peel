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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeoutException;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.omam.peel.server.UrlResolver;
import io.omam.peel.tracks.Track;
import io.omam.wire.device.CastDeviceController;
import io.omam.wire.device.CastDeviceControllerListener;
import io.omam.wire.device.CastDeviceStatus;
import io.omam.wire.media.Error;
import io.omam.wire.media.MediaController;
import io.omam.wire.media.MediaInfo;
import io.omam.wire.media.MediaRequestException;
import io.omam.wire.media.MediaStatus;
import io.omam.wire.media.MediaStatus.IdleReason;
import io.omam.wire.media.MediaStatus.PlayerState;
import io.omam.wire.media.MediaStatusListener;
import io.omam.wire.media.QueueItem;

final class ConnectedDeviceController implements MediaStatusListener, CastDeviceControllerListener {

    private static final Logger LOGGER = Logger.getLogger(ConnectedDeviceController.class.getName());

    private final CastDeviceController deviceController;

    private final MediaController mediaController;

    private final UrlResolver urlResolver;

    private final ConcurrentLinkedQueue<ConnectedDeviceListener> listeners;

    private final MediaSession mediaSession;

    ConnectedDeviceController(final CastDeviceController aCastDeviceController,
            final MediaController aMediaController, final UrlResolver anUrlResolver) {
        deviceController = aCastDeviceController;
        deviceController.addListener(this);
        mediaController = aMediaController;
        mediaController.addListener(this);
        urlResolver = anUrlResolver;
        listeners = new ConcurrentLinkedQueue<>();
        mediaSession = new MediaSession();
    }

    @Override
    public final void connectionDead() {
        listeners.forEach(l -> l.connectionClosed(deviceController.deviceId(), "connection dropped"));
    }

    @Override
    public final void deviceStatusUpdated(final CastDeviceStatus status) {
        // ignore.
    }

    @Override
    public void mediaErrorReceived(final Error error) {
        listeners.forEach(l -> l.playbackError(error));
    }

    @Override
    public final void mediaStatusUpdated(final MediaStatus newStatus) {
        mediaSession.update(newStatus);
        final PlayerState playerState = mediaSession.playerState();
        if (playerState == PlayerState.PAUSED) {
            listeners.forEach(ConnectedDeviceListener::playbackPaused);
        } else if (playerState == PlayerState.PLAYING) {
            final Optional<Track> currentTrack = mediaSession.currentTrack();
            if (currentTrack.isPresent()) {
                final Track t = currentTrack.get();
                listeners.forEach(l -> l.newTrackPlaying(t));
            } else {
                listeners.forEach(l -> l.playbackError("No current track"));
            }
        } else if (playerState == PlayerState.IDLE && newStatus.idleReason().isPresent()) {
            final IdleReason idle = newStatus.idleReason().get();
            if (idle == IdleReason.CANCELLED) {
                listeners.forEach(ConnectedDeviceListener::playbackStopped);
            } else if (idle == IdleReason.FINISHED) {
                listeners.forEach(ConnectedDeviceListener::playbackFinished);
            }
        }
    }

    @Override
    public final void remoteConnectionClosed() {
        listeners.forEach(l -> l.connectionClosed(deviceController.deviceId(), "connection closed by device"));
    }

    final void addListener(final ConnectedDeviceListener l) {
        listeners.add(l);
    }

    final QueueState appendToQueue(final List<Track> tracks)
            throws IOException, TimeoutException, MediaRequestException {
        if (mediaSession.isQueueEmpty()) {
            return play(tracks);
        }
        mediaController.appendToQueue(storeTracks(tracks));
        return queuedTracks();
    }

    final String deviceId() {
        return deviceController.deviceId();
    }

    final String deviceName() {
        return deviceController.deviceName().orElse(deviceController.deviceId());
    }

    final void disconnect() {
        mediaSession.reset();
        try {
            deviceController.stopApp(mediaController);
        } catch (final IOException | TimeoutException e) {
            LOGGER.log(Level.WARNING, e, () -> "Could not stop media application");
        }
        deviceController.disconnect();
    }

    final void next() throws IOException, TimeoutException, MediaRequestException {
        mediaController.next();
    }

    final QueueState play(final List<Track> tracks) throws IOException, TimeoutException, MediaRequestException {
        if (mediaSession.playerState() != PlayerState.IDLE) {
            stopPlayback();
        }
        mediaController.load(storeTracks(tracks));
        return queuedTracks();
    }

    final void play(final Track track) throws IOException, TimeoutException, MediaRequestException {
        mediaController.jump(mediaSession.jumpTo(track));
    }

    final QueueState playNext(final List<Track> tracks)
            throws IOException, TimeoutException, MediaRequestException {
        if (mediaSession.isQueueEmpty()) {
            return play(tracks);
        }
        mediaController.insertInQueue(mediaSession.nextItemId(), storeTracks(tracks));
        return queuedTracks();
    }

    final void prev() throws IOException, TimeoutException, MediaRequestException {
        mediaController.previous();
    }

    final QueueState removeFromQueue(final Track track)
            throws IOException, TimeoutException, MediaRequestException {
        final int id = mediaSession.remove(track);
        mediaController.removeFromQueue(Collections.singletonList(id));
        return queuedTracks();
    }

    final void removeListener(final ConnectedDeviceListener l) {
        listeners.remove(l);
    }

    final void stopPlayback() throws IOException, TimeoutException, MediaRequestException {
        /* no media status unsolicited message. */
        mediaController.stop();
        mediaSession.reset();
    }

    final PlayerState togglePlayback() throws IOException, TimeoutException, MediaRequestException {
        /* no media status unsolicited message. */
        final MediaStatus status;
        if (mediaSession.playerState() == PlayerState.PLAYING) {
            status = mediaController.pause();
        } else {
            status = mediaController.play();
        }
        final PlayerState playerState = status.playerState();
        mediaSession.setPlayerState(playerState);
        return playerState;
    }

    private QueueState queuedTracks() throws IOException, TimeoutException, MediaRequestException {
        final List<QueueItem> items = mediaController.getQueueItems();
        final List<Track> tracks = mediaSession.withQueueItems(items);
        return new QueueState(tracks, mediaSession.currentTrack());
    }

    private List<MediaInfo> storeTracks(final List<Track> tracks) {
        final List<MediaInfo> l = new ArrayList<>();
        for (final Track track : tracks) {
            try {
                final String contentId = urlResolver.resolveUrl(track.path());
                final MediaInfo media = MediaInfo.fromDataStream(contentId);
                l.add(media);
                mediaSession.add(contentId, track);
            } catch (final IOException e) {
                LOGGER.log(Level.WARNING, e, () -> "Ignoring track " + track.name());
            }
        }
        return l;
    }

}