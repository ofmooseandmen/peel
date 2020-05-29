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
package io.omam.peel.tracks;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.DirectoryStream.Filter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings("javadoc")
public final class Tracks {

    private static final Comparator<? super Track> TRACK_COMPARATOR = Comparator.comparing(t -> t.name());

    private static final Comparator<? super Artist> ARTIST_COMPARATOR = Comparator
        .<Artist, String> comparing(a -> a.firstChar(), String.CASE_INSENSITIVE_ORDER)
        .thenComparing(a -> a.name());

    private static final String IGNORE = "the ";

    private Tracks() {
        // empty.
    }

    public static Runnable searchArtists(final Path artists, final SearchListener<List<Artist>> listener) {

        final Filter<Path> filter = p -> {
            final File f = p.toFile();
            return f.isDirectory() && !f.isHidden();
        };

        return () -> {
            listener.searchStarted();
            final List<Artist> res = new ArrayList<>();
            try (final DirectoryStream<Path> artistsStream = Files.newDirectoryStream(artists, filter)) {
                for (final Path artist : artistsStream) {
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    res.add(artist(artist));
                }
            } catch (final IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            res.sort(ARTIST_COMPARATOR);
            listener.found(res);
            listener.searchOver();
        };
    }

    public static Runnable searchByAlbum(final Path artists, final Set<String> supportedFormats,
            final Predicate<String> predicate, final SearchListener<Album> listener) {

        final Filter<Path> filter = p -> predicate.test(fileName(p));

        return () -> {
            listener.searchStarted();
            try (final DirectoryStream<Path> artistsStream = Files.newDirectoryStream(artists)) {
                for (final Path artist : artistsStream) {
                    final String artistName = fileName(artist);
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    try (final DirectoryStream<Path> albumsStream = Files.newDirectoryStream(artist, filter)) {
                        for (final Path album : albumsStream) {
                            final String albumName = fileName(album);
                            if (Thread.currentThread().isInterrupted()) {
                                return;
                            }
                            final List<Track> tracks = tracks(artistName, albumName, album, supportedFormats);
                            listener.found(new Album(artistName, albumName, tracks));
                        }
                    } catch (final IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            } catch (final IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            listener.searchOver();
        };
    }

    public static Runnable searchByArtist(final Path artists, final Set<String> supportedFormats,
            final Predicate<String> predicate, final SearchListener<Album> listener) {

        final Filter<Path> filter = p -> predicate.test(fileName(p));

        return () -> {
            listener.searchStarted();
            try (final DirectoryStream<Path> artistsStream = Files.newDirectoryStream(artists, filter)) {
                for (final Path artist : artistsStream) {
                    final String artistName = fileName(artist);
                    if (Thread.currentThread().isInterrupted()) {
                        return;
                    }
                    try (final DirectoryStream<Path> albumsStream = Files.newDirectoryStream(artist)) {
                        for (final Path album : albumsStream) {
                            final String albumName = fileName(album);
                            if (Thread.currentThread().isInterrupted()) {
                                return;
                            }
                            final List<Track> tracks = tracks(artistName, albumName, album, supportedFormats);
                            if (!tracks.isEmpty()) {
                                listener.found(new Album(artistName, albumName, tracks));
                            }
                        }
                    } catch (final IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            } catch (final IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            listener.searchOver();
        };
    }

    private static Artist artist(final Path artist) {
        final String name = fileName(artist);
        final char first;
        if (name.toLowerCase().startsWith(IGNORE)) {
            first = name.charAt(IGNORE.length());
        } else {
            first = name.charAt(0);
        }
        final String firstChar = Character.isLetter(first) ? Character.toString(first) : "#";
        return new Artist(name, firstChar);
    }

    private static String fileName(final Path p) {
        return p.getFileName().toString();
    }

    private static boolean isFormatSupported(final Path track, final Set<String> supportedFormats) {
        final String trackFileName = fileName(track);
        if (!trackFileName.contains(".")) {
            return false;
        }
        final String ext = trackFileName.substring(trackFileName.lastIndexOf(".") + 1).toUpperCase();
        return supportedFormats.contains(ext);
    }

    private static Stream<Path> listFiles(final Path p) {
        try {
            return Files.list(p);
        } catch (final IOException e) {
            return Stream.empty();
        }
    }

    private static String trackName(final Path track) {
        return fileName(track).replaceFirst("[.][^.]+$", "");
    }

    private static List<Track> tracks(final String artistName, final String albumName, final Path album,
            final Set<String> supportedFormats) {
        return listFiles(album)
            .filter(track -> isFormatSupported(track, supportedFormats))
            .map(track -> new Track(artistName, albumName, trackName(track), track))
            .sorted(TRACK_COMPARATOR)
            .collect(Collectors.toList());
    }

}
