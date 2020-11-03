# Changelog
All notable changes to this project will be documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Copy additional tag metadata when converting (e.g. year)
- Convert color tags
- Convert from Serato DJ to Rekordbox
- Convert from Rekordbox to Serato DJ
- Support the case where an entry has a tempo, but no cues (Traktor to Rekordbox)
- Support disabling the "Store Beatmarker as Hotcue" Traktor setting

## 0.5.0 (2020-11-03)
### Changed
- Migrated from JavaScript/NodeJS runtime to Java/GraalVM runtime, in order to fix [#33](https://github.com/digital-dj-tools/dj-data-converter/issues/33) and improve performance generally

## 0.4.1 (2020-02-04)
### Fixed
- Allow DateAdded blank string for Rekordbox tracks [#27](https://github.com/digital-dj-tools/dj-data-converter/issues/27)

## 0.4.0 (2019-10-24)
### Added
- Convert from Rekordbox to Traktor [#9](https://github.com/digital-dj-tools/dj-data-converter/issues/9)
- Offset correction [#3](https://github.com/digital-dj-tools/dj-data-converter/issues/3)
### Changed
- Downgrade Nodejs from 10 to 8 for the pkg build, to avoid `"buffer" argument must be one of type Buffer or Uint8Array. Received type object` error (will be investigated)
### Fixed
- Correct Rekordbox date format [#26](https://github.com/digital-dj-tools/dj-data-converter/issues/26). Rekordbox xml data written by earlier versions of the converter has malformed date formatting, which Rekordbox will unfortunately accept without validation. This effectively corrupts the Rekordbox database, and so it will need to be deleted and re-created before running this new version of the converter to produce correctly formatted xml data, which can then be imported. The Rekordbox database is located at `C:\Users\youruser name\AppData\Roaming\Pioneer\rekordbox` on Windows, and `/yourharddrivename/Users/yourusername/Library/Pioneer/rekordbox` on a Mac.

## 0.3.4 (2019-10-08)
### Added
- Convert Traktor import date to Rekordbox date added
- Add optional stems in Traktor entry spec
### Fixed
- Filter entries with location and non-blank location file, so that only these Traktor entries are converted [#21](https://github.com/digital-dj-tools/dj-data-converter/issues/21)
- Correct Rekordbox hot cue colours, match Rekordbox green for cues

## 0.3.3 (2019-08-02)
### Added
- Copy additional tag metadata when converting (genre, comment)

## 0.3.2 (2019-06-27)
### Changed
- Packages for Windows and Mac are now made available as archives: zip for Windows and tar.gz for Mac, to preserve execute permissions

## 0.3.1 (2019-06-24)
### Changed
- Add digital-dj-tools/utils dependency
- All bpm values are now type double
- The max tempo inizio and marker start/end is now 7200 seconds

## 0.3.0 (2019-03-02)
### Changed
- Revised colour mapping for markers (cue points). The default cue point colours are used where possible, except when there is a conflict between Traktor and Rekordbox

## 0.2.2 (2019-02-03)
### Changed
- Fixed track numbers in Rekordbox (Rekordbox xml uses a TrackNumber attribute, not a Track attribute)

## 0.2.1 (2019-01-26)
### Changed
- End attr of position marks is now optional, it's only included when the marker is a loop. This avoids "zero-length" position marks, which Rekordbox accepts but DJ hardware e.g. CDJ does not

## 0.2.0 (2019-01-14)
### Changed
- Internal changes to simplify data conversions 
- Internal changes allowing this project to be used as a library and extended by other projects.
### Removed
- The -c/--check-input command line option (no longer required since the input is now always checked).

## 0.1.0 (2018-11-24)
### Added
- Initial release.