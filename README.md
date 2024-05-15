# PositionMe
Indoor positioning data collection application created for the University of Edinburgh's Embedded Wireless course. This repo contains the modifications I've made to the positioning app to fulfill the coursework 1 criteria. It involved using sensor data to create a live indoor positioning view so the user can plot a path as they collect indoor positioning data within the Univeristy buildings. This required applying knowledge of the Google Maps API See the assignement pdf for more details.

Marks achieved: 70.25/100

## Requirements

- Android Studio 4.2 or later
- Android SDK 30 or later

## Installation

1. Clone the repository.
2. Open the project in Android Studio.
3. Add your own API key for Google Maps in AndroidManifest.xml
4. Set the website where you want to send your data. The application was built for use with openpositioning.org.
5. Build and run the project on your device.

## Usage

1. Install the application on a compatible device using Android Studio.
2. Launch the application on your device.
3. Allow sensor, location and internet permissions when asked.
4. Follow the instructions on the screen to start collecting sensor data.

## Creators

### Original contributors ([CloudWalk](https://github.com/openpositioning/DataCollectionTeam6))
- Virginia Cangelosi (virginia-cangelosi)
- Michal Dvorak (dvoramicha)
- Mate Stodulka (stodimp)

### New contributors
- Francisco Zampella (fzampella-huawei)
- Arsalan Siddique

## v1.4
- Correction fragment now keeps floor plans intact which helps user to align trajectory to the floor plan.
- UI updates made so up and down buttons blend in with theme.
- Fixed bugs where certain buildings weren't displaying the floor map correctly.
- Implemented toasts which alerts user that auto floor changes are disabled if using the up and down buttons
- Implemented travelled distance indicator on maps fragment

## V1.3
- Features 2a-2e all implemented and working
- Testing carried out and can confirm all floor plans automatically update based on elevation changes.
- Start location automatically centres just outside nucleus to make testing easier, marker can be changed by dragging and dropping as usual.
### TODOs
- If time allows, modify the corrections fragment to keep map state from maps fragment to allow easier correction of trajectories.

## V1.2
- Features 2a-2b successfully implemented and working.
- User is able to enter and exit building and floor plan will display/ disappear respectively.
### TODOs
- 2c-2e still needs to be implemented.
- Test entering and exiting MurrayLibrary and remaining buildings to see if floor plans display correctly for them.

## V1.1

- Features 1a-1d from requiremenrts document implemented and working.
- Created a new Maps fragment which user gets directed to via a "Live View" button on the RecordingFragment
- Plots a live view of the path traversed by the user as well as their orientation based on PDR values
- Displays live error distance between PDR location and GNSS location


### TODOs
- Implement features 2a-2e from the requirements document
- I.E. use GroundOverlays and layer floorplans on the map, impelement automatic map overlay when usxer enters buildings

