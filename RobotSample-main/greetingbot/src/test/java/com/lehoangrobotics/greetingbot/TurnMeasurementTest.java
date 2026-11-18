package com.lehoangrobotics.greetingbot;

import org.junit.Test;
import static org.junit.Assert.*;

public class TurnMeasurementTest {
    @Test public void measuresAcrossTheMapYawWrapInRadians() {
        assertEquals(20, TurnMeasurement.magnitude(Math.toRadians(170), Math.toRadians(-170)), .001);
        assertEquals(90, TurnMeasurement.magnitude(0, -Math.PI / 2), .001);
    }
    @Test public void missingPoseCannotBeMistakenForACompletedPhysicalTurn() {
        assertTrue(Double.isNaN(TurnMeasurement.magnitude(Double.NaN, 0)));
        assertEquals(0, TurnMeasurement.magnitude(1, 1), 0);
    }
    @Test public void positiveCompensationExtendsBothTurnDirections() {
        assertEquals(41, TurnMeasurement.withOffset(31, 10), .001);
        assertEquals(320, TurnMeasurement.withOffset(330, 10), .001);
        assertEquals(-40, TurnMeasurement.signedAngle(TurnMeasurement.withOffset(330, 10)), .001);
        assertEquals(345, TurnMeasurement.withOffset(355, 10), .001);
        assertTrue(Double.isNaN(TurnMeasurement.withOffset(31, 91)));
    }
    @Test public void compensationKeepsZeroAndCannotReverseAtTheHalfTurnBoundary() {
        assertEquals(0, TurnMeasurement.withOffset(0, 10), 0);
        assertEquals(0, TurnMeasurement.withOffset(360, 10), 0);
        double positive = TurnMeasurement.signedAngle(TurnMeasurement.withOffset(175, 10));
        double negative = TurnMeasurement.signedAngle(TurnMeasurement.withOffset(185, 10));
        assertTrue(positive > 179 && positive < 180);
        assertTrue(negative < -179 && negative > -180);
    }
    @Test public void negativeCalibrationReducesMagnitudeWithoutReversingTheTurn() {
        assertEquals(20, TurnMeasurement.withOffset(30, -10), .001);
        assertEquals(340, TurnMeasurement.withOffset(330, -10), .001);
        assertEquals(0, TurnMeasurement.withOffset(5, -10), 0);
        assertEquals(0, TurnMeasurement.withOffset(355, -10), 0);
    }
    @Test public void measuredCorrectionTargetsTheCalibratedAngleWithoutAddingOffsetAgain() {
        double calibrated = TurnMeasurement.withOffset(330, 10);
        assertEquals(-10, TurnMeasurement.residual(0, Math.toRadians(30), calibrated), .001);
        assertEquals(0, TurnMeasurement.residual(0, Math.toRadians(40), calibrated), .001);
    }
    @Test public void calculatesTheRemainingTurnInTheSdkClockwiseConvention() {
        assertEquals(16, TurnMeasurement.residual(0, Math.toRadians(-164), 180), .001);
        assertEquals(-10, TurnMeasurement.residual(0, Math.toRadians(20), 330), .001);
        assertEquals(-10, TurnMeasurement.residual(0, Math.toRadians(-40), 30), .001);
    }
    @Test public void residualCalculationHandlesTheMapYawBoundary() {
        assertEquals(10, TurnMeasurement.residual(Math.toRadians(-170), Math.toRadians(170), 30), .001);
        assertTrue(Double.isNaN(TurnMeasurement.residual(Double.NaN, 0, 90)));
    }
    @Test public void permitsOnlyOneSmallFreshCorrection() {
        assertTrue(TurnMeasurement.canCorrect(16, 0, 3000));
        assertFalse(TurnMeasurement.canCorrect(16, 1, 3000));
        assertFalse(TurnMeasurement.canCorrect(46, 0, 3000));
        assertFalse(TurnMeasurement.canCorrect(4, 0, 3000));
        assertFalse(TurnMeasurement.canCorrect(16, 0, -1));
        assertFalse(TurnMeasurement.canCorrect(16, 0, 9000));
    }
}
