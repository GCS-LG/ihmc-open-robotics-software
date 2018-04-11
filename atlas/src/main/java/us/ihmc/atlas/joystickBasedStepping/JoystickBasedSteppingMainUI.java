package us.ihmc.atlas.joystickBasedStepping;

import controller_msgs.msg.dds.RobotConfigurationData;
import javafx.animation.AnimationTimer;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.transform.Translate;
import javafx.stage.Stage;
import us.ihmc.commonWalkingControlModules.configurations.WalkingControllerParameters;
import us.ihmc.communication.configuration.NetworkParameterKeys;
import us.ihmc.communication.configuration.NetworkParameters;
import us.ihmc.communication.packetCommunicator.PacketCommunicator;
import us.ihmc.communication.util.NetworkPorts;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.humanoidRobotics.kryo.IHMCCommunicationKryoNetClassList;
import us.ihmc.javaFXToolkit.cameraControllers.FocusBasedCameraMouseEventHandler;
import us.ihmc.javaFXToolkit.messager.JavaFXMessager;
import us.ihmc.javaFXToolkit.messager.SharedMemoryJavaFXMessager;
import us.ihmc.javaFXToolkit.scenes.View3DFactory;
import us.ihmc.robotModels.FullHumanoidRobotModelFactory;

public class JoystickBasedSteppingMainUI
{
   private static final String HOST = NetworkParameters.getHost(NetworkParameterKeys.robotController);
   private static final NetworkPorts PORT = NetworkPorts.JOYSTICK_BASED_CONTINUOUS_STEPPING;
   private static final IHMCCommunicationKryoNetClassList NET_CLASS_LIST = new IHMCCommunicationKryoNetClassList();

   private final PacketCommunicator packetCommunicator = PacketCommunicator.createTCPPacketCommunicatorClient(HOST, PORT, NET_CLASS_LIST);

   private final Stage primaryStage;
   private final BorderPane mainPane;

   private final JavaFXRobotVisualizer javaFXRobotVisualizer;
   private final StepGeneratorJavaFXController stepGeneratorJavaFXController;
   private final AnimationTimer cameraTracking;
   private final JavaFXMessager messager = new SharedMemoryJavaFXMessager(StepGeneratorJavaFXTopics.API);
   private final XBoxOneJavaFXController xBoxOneJavaFXController;

   public JoystickBasedSteppingMainUI(Stage primaryStage, FullHumanoidRobotModelFactory fullRobotModelFactory,
                                      WalkingControllerParameters walkingControllerParameters)
         throws Exception
   {
      this.primaryStage = primaryStage;
      xBoxOneJavaFXController = new XBoxOneJavaFXController(messager);

      FXMLLoader loader = new FXMLLoader();
      loader.setController(this);
      loader.setLocation(getClass().getResource(getClass().getSimpleName() + ".fxml"));

      mainPane = loader.load();

      View3DFactory view3dFactory = View3DFactory.createSubscene();
      FocusBasedCameraMouseEventHandler cameraController = view3dFactory.addCameraController(true);
      view3dFactory.addWorldCoordinateSystem(0.3);
      Pane subScene = view3dFactory.getSubSceneWrappedInsidePane();
      mainPane.setCenter(subScene);

      javaFXRobotVisualizer = new JavaFXRobotVisualizer(fullRobotModelFactory);
      packetCommunicator.attachListener(RobotConfigurationData.class, javaFXRobotVisualizer::submitNewConfiguration);
      view3dFactory.addNodeToView(javaFXRobotVisualizer.getRootNode());

      Translate rootJointOffset = new Translate();
      cameraController.prependTransform(rootJointOffset);

      cameraTracking = new AnimationTimer()
      {
         @Override
         public void handle(long now)
         {
            FramePoint3D rootJointPosition = new FramePoint3D(javaFXRobotVisualizer.getFullRobotModel().getRootJoint().getFrameAfterJoint());
            rootJointPosition.changeFrame(ReferenceFrame.getWorldFrame());
            rootJointOffset.setX(rootJointPosition.getX());
            rootJointOffset.setY(rootJointPosition.getY());
            rootJointOffset.setZ(rootJointPosition.getZ());
         }
      };

      stepGeneratorJavaFXController = new StepGeneratorJavaFXController(messager, walkingControllerParameters, packetCommunicator, javaFXRobotVisualizer);
      view3dFactory.addNodeToView(stepGeneratorJavaFXController.getRootNode());

      primaryStage.setTitle(getClass().getSimpleName());
      primaryStage.setMaximized(true);
      Scene mainScene = new Scene(mainPane, 600, 400);

      primaryStage.setScene(mainScene);
      primaryStage.setOnCloseRequest(event -> stop());

      start();
   }

   public void start() throws Exception
   {
      messager.startMessager();
      primaryStage.show();
      javaFXRobotVisualizer.start();
      stepGeneratorJavaFXController.start();
      cameraTracking.start();
      packetCommunicator.connect();
   }

   public void stop()
   {
      try
      {
         messager.closeMessager();
      }
      catch (Exception e)
      {
         e.printStackTrace();
      }
      xBoxOneJavaFXController.stop();
      javaFXRobotVisualizer.stop();
      stepGeneratorJavaFXController.stop();
      cameraTracking.stop();
      packetCommunicator.disconnect();
      packetCommunicator.closeConnection();
   }
}
