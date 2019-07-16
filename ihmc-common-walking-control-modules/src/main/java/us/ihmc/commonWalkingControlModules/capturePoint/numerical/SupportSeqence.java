package us.ihmc.commonWalkingControlModules.capturePoint.numerical;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.util.Precision;

import gnu.trove.list.TDoubleList;
import gnu.trove.list.array.TDoubleArrayList;
import us.ihmc.commons.lists.RecyclingArrayList;
import us.ihmc.euclid.geometry.ConvexPolygon2D;
import us.ihmc.euclid.geometry.interfaces.ConvexPolygon2DBasics;
import us.ihmc.euclid.geometry.interfaces.ConvexPolygon2DReadOnly;
import us.ihmc.euclid.geometry.tools.EuclidGeometryPolygonTools;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.euclid.referenceFrame.FrameQuaternion;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tuple2D.Point2D;
import us.ihmc.euclid.tuple2D.interfaces.Point2DReadOnly;
import us.ihmc.graphicsDescription.yoGraphics.YoGraphicsListRegistry;
import us.ihmc.graphicsDescription.yoGraphics.plotting.YoArtifactPolygon;
import us.ihmc.humanoidRobotics.footstep.Footstep;
import us.ihmc.humanoidRobotics.footstep.FootstepTiming;
import us.ihmc.robotics.math.trajectories.trajectorypoints.FrameSE3TrajectoryPoint;
import us.ihmc.robotics.referenceFrames.PoseReferenceFrame;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.robotics.robotSide.SideDependentList;
import us.ihmc.robotics.trajectories.TrajectoryType;
import us.ihmc.yoVariables.providers.DoubleProvider;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoDouble;
import us.ihmc.yoVariables.variable.YoFrameConvexPolygon2D;

public class SupportSeqence
{
   private static final int INITIAL_CAPACITY = 50;
   private static final double UNSET_TIME = -1.0;

   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());

   private final YoDouble supportSequenceStartTime = new YoDouble("SupportSequenceStartTime", registry);
   private final YoDouble transferPhaseEndTime = new YoDouble("TransferPhaseEndTime", registry);
   private final YoDouble swingPhaseEndTime = new YoDouble("SwingPhaseEndTime", registry);

   private final DoubleProvider time;
   private final SideDependentList<? extends ReferenceFrame> soleFrames;

   private final RecyclingArrayList<ConvexPolygon2D> supportPolygons = new RecyclingArrayList<>(INITIAL_CAPACITY, ConvexPolygon2D.class);
   private final TDoubleArrayList supportInitialTimes = new TDoubleArrayList(INITIAL_CAPACITY, UNSET_TIME);

   private final ConvexPolygon2D defaultSupportPolygon = new ConvexPolygon2D();
   private final SideDependentList<ConvexPolygon2D> footPolygons = new SideDependentList<>(new ConvexPolygon2D(), new ConvexPolygon2D());
   private final SideDependentList<FramePose3D> footPoses = new SideDependentList<>(new FramePose3D(), new FramePose3D());

   private final SideDependentList<PoseReferenceFrame> movingSoleFrames = new SideDependentList<>();
   private final SideDependentList<ConvexPolygon2D> movingPolygons = new SideDependentList<>(new ConvexPolygon2D(), new ConvexPolygon2D());

   private final SideDependentList<RecyclingArrayList<ConvexPolygon2D>> footSupportSequences = new SideDependentList<>();
   private final SideDependentList<TDoubleArrayList> footSupportInitialTimes = new SideDependentList<>();

   private final List<YoDouble> polygonStartTimes = new ArrayList<>();
   private final List<ConvexPolygon2DBasics> vizPolygons = new ArrayList<>();

   public SupportSeqence(ConvexPolygon2DReadOnly defaultSupportPolygon, SideDependentList<? extends ReferenceFrame> soleFrames, DoubleProvider time)
   {
      this(defaultSupportPolygon, soleFrames, time, null, null);
   }

   public SupportSeqence(ConvexPolygon2DReadOnly defaultSupportPolygon, SideDependentList<? extends ReferenceFrame> soleFrames, DoubleProvider time,
                         YoVariableRegistry parentRegistry, YoGraphicsListRegistry graphicRegistry)
   {
      if (graphicRegistry != null)
      {
         for (int i = 0; i < 10; i++)
         {
            YoFrameConvexPolygon2D yoPolygon = new YoFrameConvexPolygon2D("SupportPolygon" + i, ReferenceFrame.getWorldFrame(), 10, registry);
            graphicRegistry.registerArtifact(getClass().getSimpleName(), new YoArtifactPolygon("SupportPolygon" + i, yoPolygon, Color.GRAY, false));
            vizPolygons.add(yoPolygon);
            polygonStartTimes.add(new YoDouble("SupportPolygonStartTime" + i, registry));
         }
      }

      if (parentRegistry != null)
         parentRegistry.addChild(registry);

      for (RobotSide robotSide : RobotSide.values)
      {
         movingSoleFrames.put(robotSide, new PoseReferenceFrame(robotSide.getLowerCaseName() + "MovingSole", ReferenceFrame.getWorldFrame()));
         footSupportSequences.put(robotSide, new RecyclingArrayList<ConvexPolygon2D>(INITIAL_CAPACITY, ConvexPolygon2D.class));
         footSupportInitialTimes.put(robotSide, new TDoubleArrayList(INITIAL_CAPACITY, UNSET_TIME));
      }

      this.defaultSupportPolygon.set(defaultSupportPolygon);
      this.time = time;
      this.soleFrames = soleFrames;
   }

   /**
    * Get the list of upcoming support polygons with the first polygon in the list being the current support.
    *
    * @return the list of upcoming support polygons.
    */
   public List<? extends ConvexPolygon2DReadOnly> getSupportPolygons()
   {
      return supportPolygons;
   }

   /**
    * Get the times at which the respective support polygons obtained with {@link #getSupportPolygons()} will be active.
    * The first value in this list should always be 0.0. The time is relative to the start time of the support sequence
    * (see {@link #getTimeInSequence()}).
    *
    * @return the times at which the support polygon will change.
    */
   public TDoubleList getSupportTimes()
   {
      return supportInitialTimes;
   }

   /**
    * Gets the time in the current support sequence. This time is reset when the support sequence is reinitialized (e.g.
    * at the start of a swing or a transfer). All times provided by this class are relative to the start of the support
    * sequence.
    *
    * @return time that has passed since the start of the support sequence.
    */
   public double getTimeInSequence()
   {
      return time.getValue() - supportSequenceStartTime.getValue();
   }

   /**
    * Indicates whether the first transfer in this sequence has finished. The first support phase can have several
    * support polygons. This happens if the robot uses toe off or heel strike as this changes the support polygon but
    * does not end the support phase (swing or transfer).
    * <p>
    * Note, that if the robot is standing this will always return {@code true}.
    *
    * @return whether the first transfer phase at the start of this sequence should be over based on time only.
    */
   public boolean isDoubleSupportPhaseOver()
   {
      if (supportInitialTimes.size() == 1)
         return true;
      return getTimeInSequence() >= transferPhaseEndTime.getValue();
   }

   /**
    * Indicates whether the first single support in this sequence has finished.
    * <p>
    * Note, that if the robot is standing this will always return {@code true}.
    *
    * @return whether the first single support phase at the start of this sequence should be over based on time only.
    */
   public boolean isSingleSupportPhaseOver()
   {
      if (supportInitialTimes.size() == 1)
         return true;
      return getTimeInSequence() >= swingPhaseEndTime.getValue();
   }

   /**
    * Obtains the time that is remaining until the first single support phase in this sequence should end according to
    * plan (see {@link #isSingleSupportPhaseOver()}).
    * <p>
    * Note, that if the robot is standing this will always return {@code 0.0}.
    *
    * @return the time that (according to plan) remains until first foot touchdown.
    */
   public double getTimeUntilTouchdown()
   {
      if (supportInitialTimes.size() == 1)
         return 0.0;
      return swingPhaseEndTime.getValue() - getTimeInSequence();
   }

   public void setStance()
   {
      supportSequenceStartTime.set(time.getValue());
      initializeStance();
      reset();

      ConvexPolygon2D supportPolygon = supportPolygons.add();
      supportInitialTimes.add(0.0);
      supportPolygon.clear();
      for (RobotSide robotSide : RobotSide.values)
      {
         supportPolygon.addVertices(footPolygons.get(robotSide));
      }
      supportPolygon.update();

      updateViz();
   }

   public void setFromFootsteps(List<Footstep> footsteps, List<FootstepTiming> footstepTimings)
   {
      // Record when the swing and support phase will be over we can check on that from outside.
      FootstepTiming firstFootstepTiming = footstepTimings.get(0);
      transferPhaseEndTime.set(firstFootstepTiming.getTransferTime());
      swingPhaseEndTime.set(firstFootstepTiming.getStepTime());

      supportSequenceStartTime.set(time.getValue());
      initializeStance();
      updateFromFootsteps(footsteps, footstepTimings);
   }

   public void updateFromFootsteps(List<Footstep> footsteps, List<FootstepTiming> footstepTimings)
   {
      reset();

      // Add initial support states of the feet and set the moving polygons
      for (RobotSide robotSide : RobotSide.values)
      {
         movingPolygons.get(robotSide).set(footPolygons.get(robotSide));
         movingSoleFrames.get(robotSide).setPoseAndUpdate(footPoses.get(robotSide));
         footSupportSequences.get(robotSide).add().set(movingPolygons.get(robotSide));
         footSupportInitialTimes.get(robotSide).add(0.0);
      }

      // Assemble the individual foot support trajectories
      for (int stepIndex = 0; stepIndex < footsteps.size(); stepIndex++)
      {
         FootstepTiming footstepTiming = footstepTimings.get(stepIndex);
         Footstep footstep = footsteps.get(stepIndex);
         RobotSide stepSide = footstep.getRobotSide();
         TDoubleArrayList swingFootInitialTimes = footSupportInitialTimes.get(stepSide);
         RecyclingArrayList<ConvexPolygon2D> swingFootSupports = footSupportSequences.get(stepSide);

         // Add swing - no support for foot
         double stepStartTime = Math.max(last(swingFootInitialTimes), last(footSupportInitialTimes.get(stepSide.getOppositeSide())));
         boolean liftOffRequestedByFootstep = checkForAndAddLiftOffPolygon(footstep, footstepTiming, stepStartTime);
         if (!liftOffRequestedByFootstep && shouldDoToeOff(movingSoleFrames.get(stepSide.getOppositeSide()), movingSoleFrames.get(stepSide)))
         {
            double toeOffTime = footstepTiming.getTransferTime() / 2.0;
            swingFootInitialTimes.add(stepStartTime + footstepTiming.getTransferTime() - toeOffTime);
            computeToePolygon(swingFootSupports.add(), movingPolygons.get(stepSide), movingSoleFrames.get(stepSide));
         }
         swingFootSupports.add().clearAndUpdate();
         swingFootInitialTimes.add(stepStartTime + footstepTiming.getTransferTime());

         // Update the moving polygon and sole frame to reflect that the step was taken.
         ConvexPolygon2D newFootPolygon = movingPolygons.get(stepSide);
         PoseReferenceFrame newSoleFrame = movingSoleFrames.get(stepSide);
         extractSupportPolygon(footstep, newFootPolygon, defaultSupportPolygon);
         newSoleFrame.setPoseAndUpdate(footstep.getFootstepPose());
         newFootPolygon.applyTransform(newSoleFrame.getTransformToRoot(), false);

         // Add touchdown polygon
         boolean touchDownRequestedByFootstep = checkForAndAddTouchDownPolygon(footstep, footstepTiming);
         if (!touchDownRequestedByFootstep)
         {
            swingFootSupports.add().set(newFootPolygon);
            swingFootInitialTimes.add(last(swingFootInitialTimes) + footstepTiming.getSwingTime());
         }
      }

      // Convert the foot support trajectories to a full support trajectory
      int lIndex = 0;
      int rIndex = 0;
      ConvexPolygon2D lPolygon = footSupportSequences.get(RobotSide.LEFT).get(lIndex);
      ConvexPolygon2D rPolygon = footSupportSequences.get(RobotSide.RIGHT).get(rIndex);
      combinePolygons(supportPolygons.add(), lPolygon, rPolygon);
      supportInitialTimes.add(0.0);

      while (true)
      {
         double lNextTime;
         double rNextTime;
         if (footSupportInitialTimes.get(RobotSide.LEFT).size() == lIndex + 1)
            lNextTime = Double.POSITIVE_INFINITY;
         else
            lNextTime = footSupportInitialTimes.get(RobotSide.LEFT).get(lIndex + 1);
         if (footSupportInitialTimes.get(RobotSide.RIGHT).size() == rIndex + 1)
            rNextTime = Double.POSITIVE_INFINITY;
         else
            rNextTime = footSupportInitialTimes.get(RobotSide.RIGHT).get(rIndex + 1);

         if (Double.isInfinite(rNextTime) && Double.isInfinite(lNextTime))
            break;

         if (Precision.equals(lNextTime, rNextTime))
         {
            rIndex++;
            lIndex++;
            rPolygon = footSupportSequences.get(RobotSide.RIGHT).get(rIndex);
            lPolygon = footSupportSequences.get(RobotSide.LEFT).get(lIndex);
            supportInitialTimes.add(footSupportInitialTimes.get(RobotSide.LEFT).get(lIndex));
         }
         else if (lNextTime > rNextTime)
         {
            rIndex++;
            rPolygon = footSupportSequences.get(RobotSide.RIGHT).get(rIndex);
            supportInitialTimes.add(footSupportInitialTimes.get(RobotSide.RIGHT).get(rIndex));
         }
         else
         {
            lIndex++;
            lPolygon = footSupportSequences.get(RobotSide.LEFT).get(lIndex);
            supportInitialTimes.add(footSupportInitialTimes.get(RobotSide.LEFT).get(lIndex));
         }

         combinePolygons(supportPolygons.add(), lPolygon, rPolygon);
      }

      updateViz();
   }

   private boolean checkForAndAddTouchDownPolygon(Footstep footstep, FootstepTiming footstepTiming)
   {
      if (footstep.getTrajectoryType() != TrajectoryType.WAYPOINTS)
         return false;
      if (footstepTiming.getTouchdownDuration() <= 0.0)
         return false;
      if (!Precision.equals(footstep.getSwingTrajectory().get(footstep.getSwingTrajectory().size() - 1).getTime(), footstepTiming.getSwingTime()))
         return false;

      RobotSide stepSide = footstep.getRobotSide();
      ReferenceFrame soleFrame = movingSoleFrames.get(stepSide);

      FrameSE3TrajectoryPoint lastWaypoint = footstep.getSwingTrajectory().get(footstep.getSwingTrajectory().size() - 1);
      tempOrientation.setIncludingFrame(lastWaypoint.getOrientation());
      tempOrientation.changeFrame(soleFrame);
      double pitch = tempOrientation.getPitch();
      if (Math.abs(pitch) < Math.toRadians(5.0))
         return false;

      if (pitch > 0.0)
         computeToePolygon(footSupportSequences.get(stepSide).add(), movingPolygons.get(stepSide), movingSoleFrames.get(stepSide));
      else
         computeHeelPolygon(footSupportSequences.get(stepSide).add(), movingPolygons.get(stepSide), movingSoleFrames.get(stepSide));

      footSupportInitialTimes.get(stepSide).add(last(footSupportInitialTimes.get(stepSide)) + footstepTiming.getSwingTime());
      footSupportSequences.get(stepSide).add().set(movingPolygons.get(stepSide));
      footSupportInitialTimes.get(stepSide).add(last(footSupportInitialTimes.get(stepSide)) + footstepTiming.getTouchdownDuration());
      return true;
   }

   private final FrameQuaternion tempOrientation = new FrameQuaternion();

   private boolean checkForAndAddLiftOffPolygon(Footstep footstep, FootstepTiming footstepTiming, double stepStartTime)
   {
      if (footstep.getTrajectoryType() != TrajectoryType.WAYPOINTS)
         return false;
      if (footstepTiming.getLiftoffDuration() <= 0.0)
         return false;
      if (!Precision.equals(footstep.getSwingTrajectory().get(0).getTime(), 0.0))
         return false;

      RobotSide stepSide = footstep.getRobotSide();
      ReferenceFrame soleFrame = movingSoleFrames.get(stepSide);

      FrameSE3TrajectoryPoint firstWaypoint = footstep.getSwingTrajectory().get(0);
      tempOrientation.setIncludingFrame(firstWaypoint.getOrientation());
      tempOrientation.changeFrame(soleFrame);
      double pitch = tempOrientation.getPitch();
      if (Math.abs(pitch) < Math.toRadians(5.0))
         return false;

      if (pitch > 0.0)
         computeToePolygon(footSupportSequences.get(stepSide).add(), movingPolygons.get(stepSide), movingSoleFrames.get(stepSide));
      else
         computeHeelPolygon(footSupportSequences.get(stepSide).add(), movingPolygons.get(stepSide), movingSoleFrames.get(stepSide));

      footSupportInitialTimes.get(stepSide).add(stepStartTime + footstepTiming.getTransferTime() - footstepTiming.getLiftoffDuration());
      return true;
   }

   private final List<Point2DReadOnly> vertices = new ArrayList<>();

   private void combinePolygons(ConvexPolygon2DBasics result, ConvexPolygon2DReadOnly polygonA, ConvexPolygon2DReadOnly polygonB)
   {
      vertices.clear();
      for (int i = 0; i < polygonA.getNumberOfVertices(); i++)
         vertices.add(polygonA.getVertex(i));
      for (int i = 0; i < polygonB.getNumberOfVertices(); i++)
         vertices.add(polygonB.getVertex(i));
      int n = EuclidGeometryPolygonTools.inPlaceGrahamScanConvexHull2D(vertices);
      result.clear();
      for (int i = 0; i < n; i++)
         result.addVertex(vertices.get(i));
      result.update();
   }

   private final ConvexPolygon2D partialPolygonInSwingFootFrame = new ConvexPolygon2D();

   private void computeToePolygon(ConvexPolygon2D toePolygon, ConvexPolygon2D fullPolygon, PoseReferenceFrame swingFootFrame)
   {
      partialPolygonInSwingFootFrame.set(fullPolygon);
      partialPolygonInSwingFootFrame.applyInverseTransform(swingFootFrame.getTransformToRoot(), false);
      toePolygon.clear();

      double maxX = Double.NEGATIVE_INFINITY;
      for (int i = 0; i < partialPolygonInSwingFootFrame.getNumberOfVertices(); i++)
      {
         double x = partialPolygonInSwingFootFrame.getVertex(i).getX();
         maxX = Math.max(maxX, x);
      }
      for (int i = 0; i < partialPolygonInSwingFootFrame.getNumberOfVertices(); i++)
      {
         Point2DReadOnly vertex = partialPolygonInSwingFootFrame.getVertex(i);
         if (Precision.equals(vertex.getX(), maxX, 0.01))
            toePolygon.addVertex(vertex);
      }
      toePolygon.update();
      toePolygon.applyTransform(swingFootFrame.getTransformToRoot(), false);
   }

   private void computeHeelPolygon(ConvexPolygon2D heelPolygon, ConvexPolygon2D fullPolygon, PoseReferenceFrame swingFootFrame)
   {
      partialPolygonInSwingFootFrame.set(fullPolygon);
      partialPolygonInSwingFootFrame.applyInverseTransform(swingFootFrame.getTransformToRoot(), false);
      heelPolygon.clear();

      double minX = Double.POSITIVE_INFINITY;
      for (int i = 0; i < partialPolygonInSwingFootFrame.getNumberOfVertices(); i++)
      {
         double x = partialPolygonInSwingFootFrame.getVertex(i).getX();
         minX = Math.min(minX, x);
      }
      for (int i = 0; i < partialPolygonInSwingFootFrame.getNumberOfVertices(); i++)
      {
         Point2DReadOnly vertex = partialPolygonInSwingFootFrame.getVertex(i);
         if (Precision.equals(vertex.getX(), minX, 0.01))
            heelPolygon.addVertex(vertex);
      }
      heelPolygon.update();
      heelPolygon.applyTransform(swingFootFrame.getTransformToRoot(), false);
   }

   private final FramePoint3D stepLocation = new FramePoint3D();

   private boolean shouldDoToeOff(ReferenceFrame stanceFrame, ReferenceFrame swingFootFrame)
   {
      stepLocation.setToZero(swingFootFrame);
      stepLocation.changeFrame(stanceFrame);
      return stepLocation.getX() < -0.02;
   }

   private void reset()
   {
      supportPolygons.clear();
      supportInitialTimes.reset();
      for (RobotSide robotSide : RobotSide.values)
      {
         footSupportSequences.get(robotSide).clear();
         footSupportInitialTimes.get(robotSide).reset();
      }
   }

   private void initializeStance()
   {
      for (RobotSide robotSide : RobotSide.values)
      {
         footPolygons.get(robotSide).set(defaultSupportPolygon);
         footPolygons.get(robotSide).applyTransform(soleFrames.get(robotSide).getTransformToRoot(), false);
         footPoses.get(robotSide).setFromReferenceFrame(soleFrames.get(robotSide));
      }
   }

   private void updateViz()
   {
      int max = Math.min(vizPolygons.size(), supportPolygons.size());
      for (int i = 0; i < max; i++)
      {
         vizPolygons.get(i).set(supportPolygons.get(i));
         double lastStart = i == 0 ? 0.0 : polygonStartTimes.get(i - 1).getValue();
         polygonStartTimes.get(i).set(lastStart + supportInitialTimes.get(i));
      }
      for (int i = max; i < vizPolygons.size(); i++)
      {
         vizPolygons.get(i).setToNaN();
         polygonStartTimes.get(i).set(UNSET_TIME);
      }
   }

   private static void extractSupportPolygon(Footstep footstep, ConvexPolygon2D newFootPolygon, ConvexPolygon2DReadOnly defaultSupportPolygon)
   {
      List<Point2D> predictedContactPoints = footstep.getPredictedContactPoints();
      if (predictedContactPoints != null && !predictedContactPoints.isEmpty())
      {
         newFootPolygon.clear();
         for (int i = 0; i < predictedContactPoints.size(); i++)
            newFootPolygon.addVertex(predictedContactPoints.get(i));
         newFootPolygon.update();
      }
      else
      {
         newFootPolygon.set(defaultSupportPolygon);
      }
   }

   private static double last(TDoubleList list)
   {
      return list.get(list.size() - 1);
   }
}
