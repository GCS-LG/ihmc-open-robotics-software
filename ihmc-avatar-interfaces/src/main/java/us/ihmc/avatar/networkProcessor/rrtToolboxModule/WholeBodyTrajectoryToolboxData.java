package us.ihmc.avatar.networkProcessor.rrtToolboxModule;

import java.util.*;

import us.ihmc.avatar.networkProcessor.kinematicsToolboxModule.HumanoidKinematicsSolver;
import us.ihmc.commons.PrintTools;
import us.ihmc.communication.packets.KinematicsToolboxRigidBodyMessage;
import us.ihmc.euclid.geometry.Pose3D;
import us.ihmc.humanoidRobotics.communication.packets.manipulation.wholeBodyTrajectory.WholeBodyTrajectoryToolboxMessage;
import us.ihmc.humanoidRobotics.communication.wholeBodyTrajectoryToolboxAPI.RigidBodyExplorationConfigurationCommand;
import us.ihmc.humanoidRobotics.communication.wholeBodyTrajectoryToolboxAPI.WaypointBasedTrajectoryCommand;
import us.ihmc.manipulation.planning.rrt.constrainedplanning.configurationAndTimeSpace.SpatialNode;
import us.ihmc.robotModels.FullHumanoidRobotModel;
import us.ihmc.robotics.screwTheory.RigidBody;

/**
 * This class is for packing input of the controller as like as a packet
 * {@link WholeBodyTrajectoryToolboxMessage}.
 * <p>
 * - trajectory time, initial configuration.
 * <p>
 * - list of WaypointBasedTrajectoryMessage
 * <p>
 * - list of RigidBodyExplorationConfigurationMessage
 * <p>
 * 
 * @link {WholeBodyTrajectoryToolboxMessage is converted to (this).
 *       <p>
 *       This will be used for {@link HumanoidKinematicsSolver}.
 * @author Inho, Sylvain.
 *
 */
public class WholeBodyTrajectoryToolboxData
{
   private static final boolean VERBOSE = true;

   private double trajectoryTime;

   /**
    * Left hand Right hand Chest Pelvis
    */
   private final List<RigidBody> allRigidBodies = new ArrayList<>();
   private final Map<String, RigidBody> nameToRigidBodyMap = new HashMap<>();
   private final Map<RigidBody, ConstrainedRigidBodyTrajectory> rigidBodyDataMap = new HashMap<>();

   public WholeBodyTrajectoryToolboxData(FullHumanoidRobotModel fullRobotModel, List<WaypointBasedTrajectoryCommand> endEffectorTrajectories,
                                         List<RigidBodyExplorationConfigurationCommand> explorationConfigurations)
   {
      // trajectory time.
      this.trajectoryTime = 0.0;
      for (int i = 0; i < endEffectorTrajectories.size(); i++)
         this.trajectoryTime = Math.max(trajectoryTime, endEffectorTrajectories.get(i).getLastWaypointTime());

      Map<RigidBody, WaypointBasedTrajectoryCommand> trajectoryMap = new HashMap<>();
      for (int i = 0; i < endEffectorTrajectories.size(); i++)
      {
         WaypointBasedTrajectoryCommand traj = endEffectorTrajectories.get(i);
         trajectoryMap.put(traj.getEndEffector(), traj);
      }

      Map<RigidBody, RigidBodyExplorationConfigurationCommand> explorationMap = new HashMap<>();
      
      for (int i = 0; i < explorationConfigurations.size(); i++)
      {
         RigidBodyExplorationConfigurationCommand exp = explorationConfigurations.get(i);
         explorationMap.put(exp.getRigidBody(), exp);
      }

      Set<RigidBody> rigidBodySet = new HashSet<>(explorationMap.keySet());
      rigidBodySet.addAll(trajectoryMap.keySet());
      allRigidBodies.addAll(rigidBodySet);

      allRigidBodies.forEach(body -> nameToRigidBodyMap.put(body.getName(), body));

      for (RigidBody rigidBody : allRigidBodies)
      {
         WaypointBasedTrajectoryCommand trajectory = trajectoryMap.get(rigidBody);
         RigidBodyExplorationConfigurationCommand exploration = explorationMap.get(rigidBody);
         if (VERBOSE)
         {
            String message = "Received for rigid body: " + rigidBody.getName();
            if (trajectory != null)
               message += " a trajectory request";
            if (exploration != null)
               message += " an exploration request";
            PrintTools.info(message);
         }
         rigidBodyDataMap.put(rigidBody, new ConstrainedRigidBodyTrajectory(trajectory, exploration));
      }
   }

   public SpatialNode createRandomNode()
   {
      int size = allRigidBodies.size();
      String[] names = new String[size];
      Pose3D[] spatials = new Pose3D[size];

      for (int i = 0; i < size; i++)
      {
         RigidBody rigidBody = allRigidBodies.get(i);

         names[i] = rigidBody.getName();
         spatials[i] = rigidBodyDataMap.get(rigidBody).generateRandomPose();
      }

      return new SpatialNode(names, spatials);
   }

   public List<KinematicsToolboxRigidBodyMessage> createMessages(SpatialNode node)
   {
      List<KinematicsToolboxRigidBodyMessage> messages = new ArrayList<>();
      double timeInTrajectory = node.getTime();

      for (int i = 0; i < node.getSize(); i++)
      {
         RigidBody rigidBody = nameToRigidBodyMap.get(node.getName(i));
         Pose3D poseToAppend = node.getSpatial(i);
         messages.add(rigidBodyDataMap.get(rigidBody).createMessage(timeInTrajectory, poseToAppend));
      }

      return messages;
   }

   public double getTrajectoryTime()
   {
      return trajectoryTime;
   }
}
