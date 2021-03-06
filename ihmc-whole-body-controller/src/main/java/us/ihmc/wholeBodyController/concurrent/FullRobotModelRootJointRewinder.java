package us.ihmc.wholeBodyController.concurrent;


import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.mecano.multiBodySystem.interfaces.FloatingJointBasics;
import us.ihmc.robotModels.FullRobotModel;
import us.ihmc.yoVariables.listener.RewoundListener;
import us.ihmc.yoVariables.registry.YoVariableRegistry;
import us.ihmc.yoVariables.variable.YoFrameQuaternion;
import us.ihmc.yoVariables.variable.YoFrameVector3D;

public class FullRobotModelRootJointRewinder implements RewoundListener
{
   private final YoVariableRegistry registry = new YoVariableRegistry(getClass().getSimpleName());
   private final FullRobotModel fullRobotModel;
   
   private final YoFrameVector3D yoRootJointTranslation = new YoFrameVector3D("yoRootJointTranslation", ReferenceFrame.getWorldFrame(), registry);
   private final Vector3D rootJointTranslation = new Vector3D();
   private final YoFrameQuaternion yoRootJointRotation = new YoFrameQuaternion("rootJointRotation", ReferenceFrame.getWorldFrame(), registry);
   private final Quaternion rootJointRotation = new Quaternion();

   public FullRobotModelRootJointRewinder(FullRobotModel fullRobotModel, YoVariableRegistry parentRegistry)
   {
      this.fullRobotModel = fullRobotModel;
      parentRegistry.addChild(registry);
   }
   
   public void recordCurrentState()
   {
      FloatingJointBasics rootJoint = fullRobotModel.getRootJoint();
      
      rootJointTranslation.set(rootJoint.getJointPose().getPosition());
      rootJointRotation.set(rootJoint.getJointPose().getOrientation());

      yoRootJointTranslation.set(rootJointTranslation);
      yoRootJointRotation.set(rootJointRotation);
   }

   @Override
   public void notifyOfRewind()
   {      
      FloatingJointBasics rootJoint = fullRobotModel.getRootJoint();
      
      rootJoint.setJointPosition(yoRootJointTranslation);
      rootJoint.setJointOrientation(yoRootJointRotation);
   }
}

