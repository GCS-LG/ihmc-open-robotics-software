package us.ihmc.atlas;


import us.ihmc.bambooTools.BambooTools;
import us.ihmc.darpaRoboticsChallenge.DRCHumanoidBehaviorICPFaultDetectionTest;
import us.ihmc.darpaRoboticsChallenge.drcRobot.DRCRobotModel;

public class HumanoidBehaviorsICPFaultDetectionTest extends DRCHumanoidBehaviorICPFaultDetectionTest
{  
   public DRCRobotModel getRobotModel()
   {
      return new AtlasRobotModel(AtlasRobotVersion.DRC_NO_HANDS, AtlasRobotModel.AtlasTarget.SIM, false);
   }

   public String getSimpleRobotName()
   {
      return BambooTools.getSimpleRobotNameFor(BambooTools.SimpleRobotNameKeys.ATLAS);
   }
}
