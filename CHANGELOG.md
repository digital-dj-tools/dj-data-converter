# Changelog
All notable changes to this project will be documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]
### Added
- Include app version number in comment field of each item (to be used by grid correction algorithm)
- Convert from Rekordbox to Traktor
- Copy additional tag metadata when converting (e.g. year)
- Convert color tags
- Convert from Serato DJ to Rekordbox
- Convert from Rekordbox to Serato DJ
- Support the case where an entry has a tempo, but no cues (Traktor to Rekordbox)
- Support disabling the "Store Beatmarker as Hotcue" Traktor setting

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