package us.ihmc.valkyrie;

import java.net.URI;
import java.net.URISyntaxException;

import com.martiansoftware.jsap.JSAPException;

import us.ihmc.avatar.drcRobot.DRCRobotModel;
import us.ihmc.avatar.drcRobot.RobotTarget;
import us.ihmc.avatar.networkProcessor.DRCNetworkModuleParameters;
import us.ihmc.avatar.networkProcessor.DRCNetworkProcessor;
import us.ihmc.communication.configuration.NetworkParameters;

public class ValkyrieNetworkProcessor
{
   private static final DRCRobotModel model = new ValkyrieRobotModel(RobotTarget.REAL_ROBOT, true);
   public static final boolean launchFootstepPlannerModule = true;
   
   public static void main(String[] args) throws URISyntaxException, JSAPException
   {
      DRCNetworkModuleParameters networkModuleParams = new DRCNetworkModuleParameters();
      boolean startREA = false;
      
      networkModuleParams.enableControllerCommunicator(true);
      networkModuleParams.enableLocalControllerCommunicator(false);
      String is_gazebo = System.getenv("IS_GAZEBO");
      if (is_gazebo != null && is_gazebo.equals("true")) {
    	  System.out.println("IS_GAZEBO=true -- starting REA");
          startREA = true;
      }
      networkModuleParams.enableRobotEnvironmentAwerenessModule(startREA);
      networkModuleParams.enableRobotEnvironmentAwerenessModule(false);
      networkModuleParams.enableKinematicsToolbox(true);
      networkModuleParams.enableKinematicsPlanningToolbox(true);
      networkModuleParams.enableFootstepPlanningToolbox(launchFootstepPlannerModule);
      networkModuleParams.enableFootstepPlanningToolboxVisualizer(false);
      networkModuleParams.enableBipedalSupportPlanarRegionPublisher(true);
      networkModuleParams.enableWalkingPreviewToolbox(true);
      networkModuleParams.enableAutoREAStateUpdater(true);

//      uncomment these for the sensors
      URI rosuri = NetworkParameters.getROSURI();

      if(rosuri != null)
      {
         networkModuleParams.enableRosModule(true);
         networkModuleParams.setRosUri(rosuri);
         networkModuleParams.enableSensorModule(true);
         System.out.println("ROS_MASTER_URI="+rosuri);
      }
      
      new DRCNetworkProcessor(model, networkModuleParams);
   }
}
