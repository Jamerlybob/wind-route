# Polish for the Milestone 2 and 3 screen

Read `docs/specs/COMMON.md` first. Claude ran the screen on the emulator. It
works. Fix these before starting anything else, each checked with a
screenshot.

1. **The fully expanded sheet runs under the status bar.** The headline sits
   beside the clock. The sheet must stop below the status bar inset (or pad
   its top by it) when expanded.
2. **Empty band under the collapsed sheet.** Below Google's cycling notice
   there is about 70 dp of nothing. The collapsed sheet should end just under
   the notice plus the navigation bar inset. Look at `collapsed_spacer` and
   the peek height in `RideSheetController`.
3. **The collapsed details line is three lines long.** Keep the first line to
   distance, time and total climb. Put the net wind, gusts and wind cost on a
   second line. "N climbs into the wind" becomes its own short line in the
   headwind colour, since it is the thing WindRoute wants noticed.
4. **"14 to 14 degrees."** When the rounded low and high are the same, say
   "14 degrees." Put that decision in a plain Java formatter with a test.
5. **The info icon beside "The wind adds about 5 minutes"** floats above the
   line at the right edge. Put it on the same baseline, directly after the
   text.
6. **The departure strip reserves as much room below the centre line as
   above**, so on a tailwind day the lower half is blank. Scale the two halves
   to the data: give each side room in proportion to its largest bar, with a
   small minimum so the centre line never sits on an edge. Comment the maths.
7. **Recent places on an empty field.** Focusing an empty From or To field
   should list recent places; today they only appear once you type.
8. **The comment pass from `M1-review-fixes.md` item 3 was cut short.**
   Finish it for `RouteMapRenderer`, `RouteStore`, `SettingsActivity`, and
   apply the same standard to the classes added for this screen
   (`DepartureStripView`, `ElevationProfileView`, `RideAnalysis`,
   `RideSheetController`, `SearchCardController`, `DeparturePickerController`).
   `ElevationProfileView.onDraw` in particular needs the coordinate maths
   explained for someone who has never drawn on a `Canvas`.
