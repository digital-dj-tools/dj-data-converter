# dj-data-converter [![Build Status](https://dev.azure.com/digital-dj-tools/dj-data-converter/_apis/build/status/digital-dj-tools.dj-data-converter?branchName=master)](https://dev.azure.com/digital-dj-tools/dj-data-converter/_build/latest?definitionId=3&branchName=master)

A command-line app for converting data files to and from different DJ software formats, such as [Traktor](https://www.native-instruments.com/en/products/traktor/dj-software/traktor-pro-3/), [Rekordbox](https://rekordbox.com/en/) and [Serato DJ](https://serato.com/dj).

In addition to an automated test suite comprising specification-driven tests on expected input and output data, this app has been manually tested with Traktor Pro 2 and Rekordbox 5.3.0 on Windows 10.

## Features

- Downloads available for [Windows and Mac OS](https://github.com/digital-dj-tools/dj-data-converter/releases)
- Convert data from Traktor to Rekordbox
- Convert tempo (BPM), beat grid and cue point data, including loops
- Convert multiple beat grid markers
- Convert unsupported Traktor cue point types using a colour mapping

## Motivation

Although there are other tools available for handling this task, none are open source and/or available on multiple platforms, especially Windows.

## Donations

Donations are most welcome! This will help me to support more DJ software formats, add new features, improve performance and fix bugs.

## Conversion Rules

### From Traktor to Rekordbox

- For each cue point in Traktor, both a memory cue and a hot cue are created.
- No limit on the number of cue points.
- The cue point names are copied over as-is.
- The default cue point colours are used where possible, except when there is a conflict between Traktor and Rekordbox:

    Traktor | Traktor Colour | Rekordbox | Rekordbox Colour
    -|-|-|-
    Cue | Blue | Cue | Green (default)
    Fade-in | Orange | Cue | Pink
    Fade-out | Orange | Cue | Pink
    Load | Yellow | Cue | Yellow (unchanged)
    Grid | White | Cue | White (unchanged)
    Loop | Green | Loop | Orange (default)

## Current Limitations

- Conversion is only possible from Traktor to Rekordbox.
- Playlists are not converted.
- Only track name, track number, track artist, album title and playtime metadata are copied over.
- Disabling the "Store Beatmarker as Hotcue" Traktor setting is not supported.
- Performance is limited, however a ~10,000 track Traktor collection should convert in under one minute.

## Dependencies

None

## Install

- Download the app from the [releases](https://github.com/digital-dj-tools/dj-data-converter/releases) page.

## Usage

### Windows
Open a command prompt and navigate to the directory where the app was downloaded:
```
cd <download-dir>
```
Now execute the app, providing the location of the Traktor collection file:
```
dj-data-converter-win.exe [options] <traktor-collection-file>
```
For example, assuming Traktor is installed in the default location on Windows:
```
dj-data-converter-win.exe "C:\Users\<your-user-name>\Documents\Native Instruments\Traktor <version-number>\collection.nml"
```
A converted `rekordbox.xml` file will be created in the current directory.

If the conversion fails due to an error, an `error-report.edn` file will be created (also in the current directory).

### Mac
Open a terminal and navigate to the directory where the app was downloaded:
```
cd <download-dir>
```
Now execute the app, providing the location of the Traktor collection file:
```
dj-data-converter-macos [options] <traktor-collection-file>
```
For example, assuming Traktor is installed in the default location on Mac OS X:
```
dj-data-converter-macos "/Users/<your-user-name>/Documents/Native Instruments/Traktor <version-number>/collection.nml"
```
A converted `rekordbox.xml` file will be created in the current directory.

If the conversion fails due to an error, an `error-report.edn` file will be created (also in the current directory).

### Options
```
  -h, --help
```
### Importing to Rekordbox

- In the Rekordbox preferences, 
  - Go to `View` and under `Layout` enable `rekordbox xml`
  - Go to `Advanced` `Database` and under `rekordbox xml` change `Imported Library` to the location of the generated `rekordbox.xml` file
- The `rekordbox xml` entry should now be visible in the tree lower-left.
- Click the refresh icon to load the file.
- Expand the `rekordbox xml` icon in the tree and select the `All Tracks` entry
- The converted tracks should now be listed in the track list.
- Right-click and select `Import to Collection` as normal.

## Bug Reports

Please report any possible bugs as GitHub issues in this project, and remember to include the steps performed, what was expected and the actual result. If an error occurred during the conversion, please attach the generated `error-report.edn` file.

## Roadmap

To see planned and upcoming features, refer to the `Unreleased` section at the top of the [Changelog](CHANGELOG.md).

## Feature Requests

Requests are welcome, please create them as GitHub issues in this project. However, before creating any new requests, please check the `Unreleased` section at the top of the [Changelog](CHANGELOG.md) first, to see if the feature is already planned for.

## Developers

TODO

## Credits

- [Phoebox](https://github.com/pstare/phoebox) for the suggested cue point colour mapping

## License

Copyright © 2018 Digital DJ Tools

Released under the MIT license.
