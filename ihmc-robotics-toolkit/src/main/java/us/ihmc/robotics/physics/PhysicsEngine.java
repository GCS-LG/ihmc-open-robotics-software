package us.ihmc.robotics.physics;

import java.util.ArrayList;
import java.util.List;

import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tuple3D.interfaces.Vector3DReadOnly;
import us.ihmc.mecano.multiBodySystem.interfaces.RigidBodyBasics;
import us.ihmc.yoVariables.registry.YoVariableRegistry;

public class PhysicsEngine
{
   private final ReferenceFrame rootFrame = ReferenceFrame.getWorldFrame();
   private final YoVariableRegistry parentRegistry;

   private final YoVariableRegistry physicsEngineRegistry = new YoVariableRegistry("PhysicsPlugins");
   private final List<PhysicsEngineRobotData> robotList = new ArrayList<>();
   private final List<MultiBodySystemStateReader> physicsOutputReaders = new ArrayList<>();

   private final List<Collidable> environmentCollidables = new ArrayList<>();

   private final SimpleCollisionDetection collisionDetectionPlugin;
   private final MultiRobotForwardDynamicsPlugin multiRobotPhysicsEnginePlugin;
   private final MultiRobotFirstOrderIntegrator integrationMethod = new MultiRobotFirstOrderIntegrator();

   private boolean initialize = true;

   public PhysicsEngine(YoVariableRegistry parentRegistry)
   {
      this.parentRegistry = parentRegistry;
      collisionDetectionPlugin = new SimpleCollisionDetection(rootFrame);
      multiRobotPhysicsEnginePlugin = new MultiRobotForwardDynamicsPlugin(rootFrame);
      parentRegistry.addChild(physicsEngineRegistry);
   }

   public void addRobot(String robotName, RigidBodyBasics rootBody, MultiBodySystemStateWriter controllerOutputWriter,
                        MultiBodySystemStateWriter robotInitialStateWriter, RobotCollisionModel robotCollisionModel, MultiBodySystemStateReader physicsOutputReader)
   {
      PhysicsEngineRobotData robot = new PhysicsEngineRobotData(robotName, rootBody, robotInitialStateWriter, robotCollisionModel);
      multiRobotPhysicsEnginePlugin.addMultiBodySystem(robot.getMultiBodySystem(), controllerOutputWriter);
      integrationMethod.addMultiBodySystem(robot.getMultiBodySystem());
      physicsOutputReader.setMultiBodySystem(robot.getMultiBodySystem());
      physicsOutputReaders.add(physicsOutputReader);
      parentRegistry.addChild(robot.getRobotRegistry());
      robotList.add(robot);
   }

   public void initialize()
   {
      if (!initialize)
         return;

      robotList.forEach(PhysicsEngineRobotData::initialize);

      for (int i = 0; i < robotList.size(); i++)
      {
         PhysicsEngineRobotData robot = robotList.get(i);
         robot.initialize();
         physicsOutputReaders.get(i).read();
      }
      initialize = false;
   }

   public void simulate(double dt, Vector3DReadOnly gravity)
   {
      for (PhysicsEngineRobotData robot : robotList)
      {
         robot.updateCollidableBoundingBoxes();
      }

      environmentCollidables.forEach(Collidable::updateBoundingBox);
      collisionDetectionPlugin.evaluationCollisions(robotList, () -> environmentCollidables);
      multiRobotPhysicsEnginePlugin.submitCollisions(collisionDetectionPlugin);
      multiRobotPhysicsEnginePlugin.doScience(dt, gravity);
      integrationMethod.integrate(dt);

      for (PhysicsEngineRobotData robot : robotList)
         robot.updateFrames();

      for (int i = 0; i < robotList.size(); i++)
      {
         PhysicsEngineRobotData robot = robotList.get(i);
         robot.initialize();
         physicsOutputReaders.get(i).read();
      }
   }
}
