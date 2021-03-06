package us.ihmc.commonWalkingControlModules.trajectories;

import static us.ihmc.robotics.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Disabled;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FrameVector3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.tools.ReferenceFrameTools;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoFramePoint3D;
import us.ihmc.robotics.math.trajectories.PositionTrajectoryGenerator;
import us.ihmc.robotics.math.trajectories.providers.YoPositionProvider;
import us.ihmc.robotics.math.trajectories.providers.YoVariableDoubleProvider;
import us.ihmc.robotics.trajectories.providers.ConstantPositionProvider;
import us.ihmc.robotics.trajectories.providers.ConstantVectorProvider;
import us.ihmc.robotics.trajectories.providers.PositionProvider;
import us.ihmc.robotics.trajectories.providers.TrajectoryParameters;
import us.ihmc.robotics.trajectories.providers.TrajectoryParametersProvider;
import us.ihmc.robotics.trajectories.providers.VectorProvider;

public class PushRecoveryTrajectoryGeneratorTest
{

   private static ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();

   @AfterEach
   public void tearDown()
   {
      ReferenceFrameTools.clearWorldFrameTree();
   }

   @Test
   public void testSimpleTrajectories()
   {
      testSimpleTrajectory(3);
      testSimpleTrajectory(4);
   }

   private void testSimpleTrajectory(int numDesiredSplines)
   {
      YoVariableDoubleProvider stepTimeProvider = new YoVariableDoubleProvider("", new YoVariableRegistry(""));
      stepTimeProvider.set(0.8);
      YoVariableDoubleProvider timeRemainingProvider = new YoVariableDoubleProvider("", new YoVariableRegistry(""));
      PositionProvider initialPositionProvider = new ConstantPositionProvider(new FramePoint3D(worldFrame, new double[] {-0.1, 2.3, 0.0}));
      VectorProvider initialVelocityProvider = new ConstantVectorProvider(new FrameVector3D(worldFrame, new double[] {0.2, 0.0, -0.05}));

      Point3D firstIntermediatePosition = new Point3D(new double[] {0.12, 2.4, 0.2});
      Point3D secondIntermediatePosition = new Point3D(new double[] {0.16, 2.3, 0.15});
      ArrayList<Point3D> waypoints = new ArrayList<Point3D>();
      waypoints.add(firstIntermediatePosition);
      waypoints.add(secondIntermediatePosition);

      YoFramePoint3D finalPosition = new YoFramePoint3D("", worldFrame, new YoVariableRegistry(""));
      finalPosition.set(new FramePoint3D(worldFrame, new double[] {0.2, 2.35, 0.03}));
      YoPositionProvider finalPositionProvider = new YoPositionProvider(finalPosition);
      VectorProvider finalVelocityProvider = new ConstantVectorProvider(new FrameVector3D(worldFrame, new double[] {0, 0, -0.02}));

      TrajectoryParameters trajectoryParameters = new TrajectoryParameters();
      TrajectoryParametersProvider trajectoryParametersProvider = new TrajectoryParametersProvider(trajectoryParameters);

      TwoWaypointPositionTrajectoryGenerator trajectory = new TwoWaypointPositionTrajectoryGenerator("", worldFrame, stepTimeProvider, initialPositionProvider,
            initialVelocityProvider, null, finalPositionProvider, finalVelocityProvider, trajectoryParametersProvider, new YoVariableRegistry(""), null, 0.0,
            false);

      List<Point3D> points = new ArrayList<Point3D>();
      points.add(firstIntermediatePosition);
      points.add(secondIntermediatePosition);
      trajectory.initialize();
      trajectory.compute(0.0);
      FramePoint3D actual = new FramePoint3D(worldFrame);
      FramePoint3D expected = new FramePoint3D(worldFrame);
      initialPositionProvider.getPosition(expected);
      trajectory.getPosition(actual);
      assertEquals(actual.getX(), expected.getX(), 1e-7);
      assertEquals(actual.getY(), expected.getY(), 1e-7);
      assertEquals(actual.getZ(), expected.getZ(), 1e-7);

      FrameVector3D actualVel = new FrameVector3D(worldFrame);
      FrameVector3D expectedVel = new FrameVector3D(worldFrame);
      trajectory.getVelocity(actualVel);
      initialVelocityProvider.get(expectedVel);
      assertEquals(actualVel.getX(), expectedVel.getX(), 1e-7);
      assertEquals(actualVel.getY(), expectedVel.getY(), 1e-7);
      assertEquals(actualVel.getZ(), expectedVel.getZ(), 1e-7);

      trajectory.compute(0.8);
      finalPositionProvider.getPosition(expected);
      trajectory.getPosition(actual);
      assertEquals(actual.getX(), expected.getX(), 1e-7);
      assertEquals(actual.getY(), expected.getY(), 1e-7);
      assertEquals(actual.getZ(), expected.getZ(), 1e-7);

      trajectory.getVelocity(actualVel);
      finalVelocityProvider.get(expectedVel);
      assertEquals(actualVel.getX(), expectedVel.getX(), 1e-7);
      assertEquals(actualVel.getY(), expectedVel.getY(), 1e-7);
      assertEquals(actualVel.getZ(), expectedVel.getZ(), 1e-7);

      trajectory.compute(0.4);

      FramePoint3D intermediatePosition = new FramePoint3D();
      FrameVector3D intermediateVelocity = new FrameVector3D();
      trajectory.getPosition(intermediatePosition);
      trajectory.getVelocity(intermediateVelocity);

      PositionProvider intermediatePositionProvider = new ConstantPositionProvider(intermediatePosition);
      VectorProvider intermediateVelocityProvider = new ConstantVectorProvider(intermediateVelocity);

      PositionTrajectoryGenerator pushRecoveryTrajectoryGenerator = new PushRecoveryTrajectoryGenerator("", worldFrame, stepTimeProvider, timeRemainingProvider,
            intermediatePositionProvider, intermediateVelocityProvider, finalPositionProvider, new YoVariableRegistry(""), null, trajectory);

      double tIntoStep = 0.4;
      timeRemainingProvider.set(stepTimeProvider.getValue() - tIntoStep);

      pushRecoveryTrajectoryGenerator.initialize();
      pushRecoveryTrajectoryGenerator.compute(0.4);

      intermediatePositionProvider.getPosition(expected);
      pushRecoveryTrajectoryGenerator.getPosition(actual);
      assertEquals(actual.getX(), expected.getX(), 1e-7);
      assertEquals(actual.getY(), expected.getY(), 1e-7);

      intermediateVelocityProvider.get(expectedVel);
      pushRecoveryTrajectoryGenerator.getVelocity(actualVel);
      assertEquals(actualVel.getX(), expectedVel.getX(), 1e-7);
      assertEquals(actualVel.getY(), expectedVel.getY(), 1e-7);

      pushRecoveryTrajectoryGenerator.compute(0.8);

      finalPositionProvider.getPosition(expected);
      pushRecoveryTrajectoryGenerator.getPosition(actual);
      assertEquals(actual.getX(), expected.getX(), 1e-7);
      assertEquals(actual.getY(), expected.getY(), 1e-7);
   }
}
