package io.github.jamerlybob.windroute;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import io.github.jamerlybob.windroute.wind.WindEffect;
import io.github.jamerlybob.windroute.wind.WindMath;

public class WindMathTest {

    private static final double TOLERANCE = 1e-9;

    // ---- wrapping angles -----------------------------------------------------------

    @Test
    public void normalize360_wrapsBothWays() {
        assertEquals(0.0, WindMath.normalize360(360), TOLERANCE);
        assertEquals(10.0, WindMath.normalize360(370), TOLERANCE);
        assertEquals(350.0, WindMath.normalize360(-10), TOLERANCE);
        assertEquals(0.0, WindMath.normalize360(-720), TOLERANCE);
    }

    @Test
    public void normalize180_putsAnglesEitherSideOfZero() {
        assertEquals(180.0, WindMath.normalize180(180), TOLERANCE);
        assertEquals(-179.0, WindMath.normalize180(181), TOLERANCE);
        assertEquals(-90.0, WindMath.normalize180(270), TOLERANCE);
        assertEquals(0.0, WindMath.normalize180(360), TOLERANCE);
    }

    @Test
    public void relativeAngle_takesTheShortWayRoundThroughNorth() {
        // Riding 350, wind from 010: twenty degrees off the nose to the right,
        // not three hundred and forty to the left.
        assertEquals(20.0, WindMath.relativeAngle(350, 10), TOLERANCE);
        assertEquals(-20.0, WindMath.relativeAngle(10, 350), TOLERANCE);
    }

    // ---- components ----------------------------------------------------------------

    @Test
    public void ridingNorthIntoANortherly_isAllHeadwind() {
        assertEquals(20.0, WindMath.headwindComponent(0, 0, 20), TOLERANCE);
        assertEquals(0.0, WindMath.crosswindComponent(0, 0, 20), TOLERANCE);
    }

    @Test
    public void ridingSouthWithANortherly_isAllTailwind() {
        assertEquals(-20.0, WindMath.headwindComponent(180, 0, 20), TOLERANCE);
    }

    @Test
    public void windFromTheSide_hasNoHeadwindComponent() {
        // Riding north, wind from the east: all crosswind, from the right.
        assertEquals(0.0, WindMath.headwindComponent(0, 90, 20), TOLERANCE);
        assertEquals(20.0, WindMath.crosswindComponent(0, 90, 20), TOLERANCE);
        // From the west it comes from the left.
        assertEquals(-20.0, WindMath.crosswindComponent(0, 270, 20), TOLERANCE);
    }

    @Test
    public void windAtFortyFiveDegrees_splitsEvenly() {
        double expected = 20 * Math.sqrt(0.5);
        assertEquals(expected, WindMath.headwindComponent(0, 45, 20), TOLERANCE);
        assertEquals(expected, WindMath.crosswindComponent(0, 45, 20), TOLERANCE);
    }

    @Test
    public void componentsAreTheSameEitherSideOfTheWrap() {
        // 359 and 361 are two degrees apart. So are heading 359 and wind from 1.
        assertEquals(WindMath.headwindComponent(10, 12, 20),
                WindMath.headwindComponent(359, 1, 20), TOLERANCE);
        assertEquals(WindMath.headwindComponent(0, 0, 20),
                WindMath.headwindComponent(360, 720, 20), TOLERANCE);
    }

    // ---- classification ------------------------------------------------------------

    @Test
    public void classify_byCone() {
        assertEquals(WindEffect.HEADWIND, WindMath.classify(0, 0, 20));
        assertEquals(WindEffect.HEADWIND, WindMath.classify(0, 60, 20));
        assertEquals(WindEffect.CROSSWIND, WindMath.classify(0, 61, 20));
        assertEquals(WindEffect.CROSSWIND, WindMath.classify(0, 90, 20));
        assertEquals(WindEffect.CROSSWIND, WindMath.classify(0, 119, 20));
        assertEquals(WindEffect.TAILWIND, WindMath.classify(0, 120, 20));
        assertEquals(WindEffect.TAILWIND, WindMath.classify(0, 180, 20));
    }

    @Test
    public void classify_isSymmetricLeftAndRight() {
        assertEquals(WindEffect.HEADWIND, WindMath.classify(0, 300, 20));
        assertEquals(WindEffect.CROSSWIND, WindMath.classify(0, 270, 20));
        assertEquals(WindEffect.TAILWIND, WindMath.classify(0, 240, 20));
    }

    @Test
    public void classify_acrossTheNorthWrap() {
        assertEquals(WindEffect.HEADWIND, WindMath.classify(350, 20, 20));
        assertEquals(WindEffect.TAILWIND, WindMath.classify(10, 200, 20));
        assertEquals(WindEffect.HEADWIND, WindMath.classify(360, 0, 20));
    }

    @Test
    public void lightWindIsCalmWhateverItsDirection() {
        assertEquals(WindEffect.CALM, WindMath.classify(0, 0, 4.9));
        assertEquals(WindEffect.CALM, WindMath.classify(0, 180, 0));
        assertEquals(WindEffect.HEADWIND, WindMath.classify(0, 0, 5.0));
    }
}
