# Peel

[![license](https://img.shields.io/badge/license-BSD3-lightgray.svg)](https://opensource.org/licenses/BSD-3-Clause)

A small music player to play tracks stored on a local drive to a Google Cast device.

Named after [John Peel](https://en.wikipedia.org/wiki/John_Peel) an English disc jockey, radio presenter, record producer and journalist, famous (amongst other things) for the [Peel sessions](https://en.wikipedia.org/wiki/Peel_Sessions_(disambiguation)).

Built with [JavaFx 14](https://openjfx.io) and [wire](https://github.com/ofmooseandmen/wire).

**This is a work in progress**

## Build & Run

[wire](https://github.com/ofmooseandmen/wire) is not yet available on mavencentral. In the meantime, please clone the wire repository.

```
./gradlew --include-build ../wire run --args="--libraryRootPath=[/path/to/music] --mediaServerPort=[port number]"
```
