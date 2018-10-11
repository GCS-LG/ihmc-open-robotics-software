package us.ihmc.footstepPlanning.remoteStandaloneDataSet;

import controller_msgs.msg.dds.*;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;
import org.junit.After;
import us.ihmc.commons.Conversions;
import us.ihmc.commons.PrintTools;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.communication.IHMCRealtimeROS2Publisher;
import us.ihmc.communication.ROS2Tools;
import us.ihmc.communication.packets.PlanarRegionMessageConverter;
import us.ihmc.continuousIntegration.ContinuousIntegrationTools;
import us.ihmc.euclid.geometry.ConvexPolygon2D;
import us.ihmc.euclid.referenceFrame.FramePose3D;
import us.ihmc.footstepPlanning.FootstepPlan;
import us.ihmc.footstepPlanning.FootstepPlannerDataSetTest;
import us.ihmc.footstepPlanning.FootstepPlanningResult;
import us.ihmc.footstepPlanning.SimpleFootstep;
import us.ihmc.footstepPlanning.communication.FootstepPlannerCommunicationProperties;
import us.ihmc.footstepPlanning.communication.FootstepPlannerSharedMemoryAPI;
import us.ihmc.footstepPlanning.tools.FootstepPlannerIOTools.FootstepPlannerUnitTestDataset;
import us.ihmc.footstepPlanning.tools.PlannerTools;
import us.ihmc.footstepPlanning.ui.ApplicationRunner;
import us.ihmc.footstepPlanning.ui.FootstepPlannerUI;
import us.ihmc.footstepPlanning.ui.RemotePlannerMessageConverter;
import us.ihmc.footstepPlanning.ui.RemoteStandaloneFootstepPlannerUI;
import us.ihmc.footstepPlanning.ui.components.FootstepPathCalculatorModule;
import us.ihmc.javaFXToolkit.messager.JavaFXMessager;
import us.ihmc.javaFXToolkit.messager.SharedMemoryJavaFXMessager;
import us.ihmc.javaFXToolkit.messager.SharedMemoryMessager;
import us.ihmc.pubsub.DomainFactory;
import us.ihmc.robotics.robotSide.RobotSide;
import us.ihmc.ros2.RealtimeRos2Node;

import java.util.concurrent.atomic.AtomicReference;

import static us.ihmc.footstepPlanning.communication.FootstepPlannerSharedMemoryAPI.FootstepPlanTopic;
import static us.ihmc.footstepPlanning.communication.FootstepPlannerSharedMemoryAPI.PlanningResultTopic;

public abstract class RemoteStandalonePlannerDataSetTest extends FootstepPlannerDataSetTest
{
   private static final String robotName = "testBot";

   private RealtimeRos2Node ros2Node;

   private IHMCRealtimeROS2Publisher<FootstepPlanningRequestPacket> footstepPlanningRequestPublisher;

   private final AtomicReference<FootstepPlan> publishedPlanReference = new AtomicReference<>(null);
   private final AtomicReference<FootstepPlanningResult> publishedResultReference = new AtomicReference<>(null);
   private AtomicReference<Boolean> publishedReceivedPlan = new AtomicReference<>(false);

   private AtomicReference<FootstepPlan> uiFootstepPlanReference;
   private AtomicReference<FootstepPlanningResult> uiPlanningResultReference;
   private AtomicReference<Boolean> uiReceivedPlan = new AtomicReference<>(false);
   private AtomicReference<Boolean> uiReceivedResult = new AtomicReference<>(false);

   private final AtomicReference<FootstepPlan> expectedPlan = new AtomicReference<>(null);
   private final AtomicReference<FootstepPlan> actualPlan = new AtomicReference<>(null);
   private final AtomicReference<FootstepPlanningResult> expectedResult = new AtomicReference<>(null);
   private final AtomicReference<FootstepPlanningResult> actualResult = new AtomicReference<>(null);

   protected DomainFactory.PubSubImplementation pubSubImplementation;

   private SharedMemoryMessager messager = null;
   private RemotePlannerMessageConverter messageConverter = null;
   private FootstepPathCalculatorModule module = null;
   private FootstepPlannerUI ui = null;

   public void setup()
   {
      if (VISUALIZE)
         messager = new SharedMemoryJavaFXMessager(FootstepPlannerSharedMemoryAPI.API);
      else
         messager = new SharedMemoryMessager(FootstepPlannerSharedMemoryAPI.API);
      messageConverter = RemotePlannerMessageConverter.createConverter(messager, robotName, DomainFactory.PubSubImplementation.INTRAPROCESS);
      module = new FootstepPathCalculatorModule(messager);

      try
      {
         messager.startMessager();
      }
      catch (Exception e)
      {
         throw new RuntimeException("Failed to start messager.");
      }

      module.start();

      if (VISUALIZE)
      {

         ApplicationRunner.runApplication(new Application()
         {
            @Override
            public void start(Stage stage) throws Exception
            {
               ui = FootstepPlannerUI.createMessagerUI(stage, (SharedMemoryJavaFXMessager) messager);
               ui.show();
            }

            @Override
            public void stop() throws Exception
            {
               ui.stop();
               Platform.exit();
            }
         });

         double maxWaitTime = 5.0;
         double totalTime = 0.0;
         long sleepDuration = 100;

         while (ui == null)
         {
            if (totalTime > maxWaitTime)
               throw new RuntimeException("Timed out waiting for the UI to start.");
            ThreadTools.sleep(sleepDuration);
            totalTime += Conversions.millisecondsToSeconds(sleepDuration);
         }

      }

      ros2Node = ROS2Tools.createRealtimeRos2Node(pubSubImplementation, "ihmc_footstep_planner_test");

      footstepPlanningRequestPublisher = ROS2Tools
            .createPublisher(ros2Node, FootstepPlanningRequestPacket.class, FootstepPlannerCommunicationProperties.subscriberTopicNameGenerator(robotName));

      ROS2Tools.createCallbackSubscription(ros2Node, FootstepPlanningToolboxOutputStatus.class,
                                           FootstepPlannerCommunicationProperties.publisherTopicNameGenerator(robotName),
                                           s -> processFootstepPlanningOutputStatus(s.takeNextData()));

      uiReceivedPlan = new AtomicReference<>(false);
      uiReceivedResult = new AtomicReference<>(false);

      publishedReceivedPlan = new AtomicReference<>(false);

      messager.registerTopicListener(FootstepPlanTopic, request -> uiReceivedPlan.set(true));
      messager.registerTopicListener(PlanningResultTopic, request -> uiReceivedResult.set(true));

      uiFootstepPlanReference = messager.createInput(FootstepPlanTopic);
      uiPlanningResultReference = messager.createInput(PlanningResultTopic);

      ros2Node.spin();

      for (int i = 0; i < 100; i++)
         ThreadTools.sleep(10);
   }

   @After
   public void tearDown() throws Exception
   {
      ros2Node.destroy();

      module.stop();
      messager.closeMessager();
      messageConverter.destroy();
      if (ui != null)
         ui.stop();

      uiReceivedPlan = null;
      uiReceivedResult = null;

      publishedReceivedPlan = null;
      pubSubImplementation = null;

      uiFootstepPlanReference = null;
      uiPlanningResultReference = null;

      ros2Node = null;
      footstepPlanningRequestPublisher = null;

      module = null;
      messageConverter = null;
      messager = null;
      ui = null;
   }

   @Override
   public void submitDataSet(FootstepPlannerUnitTestDataset dataset)
   {
      for (int i = 0; i < 10; i++)
         ThreadTools.sleep(100);

      byte plannerType = getPlannerType().toByte();
      PlanarRegionsListMessage planarRegions = PlanarRegionMessageConverter.convertToPlanarRegionsListMessage(dataset.getPlanarRegionsList());

      FootstepPlanningRequestPacket planningRequestPacket = new FootstepPlanningRequestPacket();
      planningRequestPacket.getStanceFootPositionInWorld().set(dataset.getStart());
      planningRequestPacket.getGoalPositionInWorld().set(dataset.getGoal());
      planningRequestPacket.setRequestedFootstepPlannerType(plannerType);
      planningRequestPacket.getPlanarRegionsListMessage().set(planarRegions);

      double timeoutMultiplier = ContinuousIntegrationTools.isRunningOnContinuousIntegrationServer() ? 2.0 : 1.0;
      planningRequestPacket.setTimeout(timeoutMultiplier * dataset.getTimeout(getPlannerType()));

      if (dataset.hasGoalOrientation())
         planningRequestPacket.getGoalOrientationInWorld().set(dataset.getGoalOrientation());
      if (dataset.hasStartOrientation())
         planningRequestPacket.getStanceFootOrientationInWorld().set(dataset.getStartOrientation());

      if (DEBUG)
         PrintTools.info("Sending out planning request packet.");

      footstepPlanningRequestPublisher.publish(planningRequestPacket);
   }

   @Override
   public String findPlanAndAssertGoodResult(FootstepPlannerUnitTestDataset dataset)
   {
      double totalTimeWaiting = 0;
      double maxTimeToWait = 2.0 * dataset.getTimeout(getPlannerType());
      long waitTime = 10;

      queryUIResults();
      queryPlannerResults();

      while (actualPlan.get() == null || actualResult.get() == null || expectedPlan.get() == null || expectedResult.get() == null)
      {
         if (totalTimeWaiting > maxTimeToWait)
            throw new RuntimeException("Overran our maximum wait time for a result");

         ThreadTools.sleep(waitTime);
         totalTimeWaiting += Conversions.millisecondsToSeconds(waitTime);
         queryUIResults();
         queryPlannerResults();
      }

      String datasetName = dataset.getDatasetName();

      String errorMessage = "";

      FootstepPlanningResult expectedResult = this.expectedResult.getAndSet(null);
      FootstepPlanningResult actualResult = this.actualResult.getAndSet(null);
      FootstepPlan expectedPlan = this.expectedPlan.getAndSet(null);
      FootstepPlan actualPlan = this.actualPlan.getAndSet(null);

      uiReceivedResult.set(false);
      uiReceivedPlan.set(false);
      publishedReceivedPlan.set(false);

      errorMessage += assertTrue(datasetName, "Planning result for " + datasetName + " is invalid, result was " + actualResult,
                                 actualResult.validForExecution());
      errorMessage += assertTrue(datasetName, "Planning results for " + datasetName + " are not equal: " + expectedResult + " and " + actualResult + ".\n",
                                 expectedResult.equals(actualResult));

      if (actualResult.validForExecution())
      {
         errorMessage += areFootstepPlansEqual(actualPlan, expectedPlan);
         errorMessage += assertTrue(datasetName, datasetName + " did not reach goal.", PlannerTools.isGoalNextToLastStep(dataset.getGoal(), actualPlan));
      }

      return errorMessage;
   }

   private void queryUIResults()
   {
      if (uiReceivedPlan.get() && uiFootstepPlanReference.get() != null && actualPlan.get() == null)
         actualPlan.set(uiFootstepPlanReference.get());

      if (uiReceivedResult.get() && uiPlanningResultReference.get() != null && actualResult.get() == null)
         actualResult.set(uiPlanningResultReference.get());
   }

   private void queryPlannerResults()
   {
      if (publishedReceivedPlan.get() && publishedPlanReference.get() != null && expectedPlan.get() == null)
         expectedPlan.set(publishedPlanReference.get());

      if (publishedReceivedPlan.get() && publishedResultReference.get() != null && expectedResult.get() == null)
         expectedResult.set(publishedResultReference.get());
   }

   private void processFootstepPlanningOutputStatus(FootstepPlanningToolboxOutputStatus packet)
   {
      publishedResultReference.set(FootstepPlanningResult.fromByte(packet.getFootstepPlanningResult()));
      publishedPlanReference.set(convertToFootstepPlan(packet.getFootstepDataList()));
      publishedReceivedPlan.set(true);
   }

   private static FootstepPlan convertToFootstepPlan(FootstepDataListMessage footstepDataListMessage)
   {
      FootstepPlan footstepPlan = new FootstepPlan();

      for (FootstepDataMessage footstepMessage : footstepDataListMessage.getFootstepDataList())
      {
         FramePose3D stepPose = new FramePose3D();
         stepPose.setPosition(footstepMessage.getLocation());
         stepPose.setOrientation(footstepMessage.getOrientation());
         SimpleFootstep footstep = footstepPlan.addFootstep(RobotSide.fromByte(footstepMessage.getRobotSide()), stepPose);

         ConvexPolygon2D foothold = new ConvexPolygon2D();
         for (int i = 0; i < footstepMessage.getPredictedContactPoints2d().size(); i++)
            foothold.addVertex(footstepMessage.getPredictedContactPoints2d().get(i));
         foothold.update();
         footstep.setFoothold(foothold);
      }

      return footstepPlan;
   }

   private String assertTrue(String datasetName, String message, boolean condition)
   {
      if (VISUALIZE || DEBUG)
      {
         if (!condition)
            PrintTools.error(datasetName + ": " + message);
      }
      return !condition ? "\n" + message : "";
   }

   private String areFootstepPlansEqual(FootstepPlan footstepPlanA, FootstepPlan footstepPlanB)
   {
      String errorMessage = "";

      if (footstepPlanA.getNumberOfSteps() != footstepPlanB.getNumberOfSteps())
      {
         errorMessage += "Plan A has " + footstepPlanA.getNumberOfSteps() + ", while Plan B has " + footstepPlanB.getNumberOfSteps() + ".\n";
      }

      for (int i = 0; i < Math.min(footstepPlanA.getNumberOfSteps(), footstepPlanB.getNumberOfSteps()); i++)
      {
         errorMessage += areFootstepsEqual(i, footstepPlanA.getFootstep(i), footstepPlanB.getFootstep(i));
      }

      return errorMessage;
   }

   private String areFootstepsEqual(int footstepNumber, SimpleFootstep footstepA, SimpleFootstep footstepB)
   {
      String errorMessage = "";

      if (!footstepA.getRobotSide().equals(footstepB.getRobotSide()))
      {
         errorMessage += "Footsteps " + footstepNumber + " are different robot sides: " + footstepA.getRobotSide() + " and " + footstepB.getRobotSide() + ".\n";
      }

      FramePose3D poseA = new FramePose3D();
      FramePose3D poseB = new FramePose3D();

      footstepA.getSoleFramePose(poseA);
      footstepB.getSoleFramePose(poseB);

      if (!poseA.epsilonEquals(poseB, 1e-5))
      {
         errorMessage += "Footsteps " + footstepNumber + " have different poses: \n \t" + poseA.toString() + "\n and \n\t " + poseB.toString() + ".\n";
      }

      if (!footstepA.epsilonEquals(footstepB, 1e-5))
      {
         errorMessage += "Footsteps " + footstepNumber + " are not equal: \n \t" + footstepA.toString() + "\n and \n\t " + footstepB.toString() + ".\n";
      }

      return errorMessage;
   }
}
