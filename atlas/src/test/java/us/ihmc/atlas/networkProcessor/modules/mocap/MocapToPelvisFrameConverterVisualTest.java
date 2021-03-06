package us.ihmc.atlas.networkProcessor.modules.mocap;

import java.util.ArrayList;
import java.util.Random;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import optiTrack.MocapMarker;
import optiTrack.MocapRigidBody;
import us.ihmc.avatar.networkProcessor.modules.mocap.MocapToPelvisFrameConverter;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.euclid.matrix.RotationMatrix;
import us.ihmc.euclid.referenceFrame.FramePoint3D;
import us.ihmc.euclid.referenceFrame.ReferenceFrame;
import us.ihmc.euclid.referenceFrame.tools.ReferenceFrameTools;
import us.ihmc.euclid.transform.RigidBodyTransform;
import us.ihmc.euclid.tuple3D.Vector3D;
import us.ihmc.euclid.tuple4D.Quaternion;
import us.ihmc.graphicsDescription.Graphics3DObject;
import us.ihmc.graphicsDescription.appearance.YoAppearance;
import us.ihmc.robotics.random.RandomGeometry;
import us.ihmc.simulationConstructionSetTools.bambooTools.BambooTools;
import us.ihmc.simulationconstructionset.Robot;
import us.ihmc.simulationconstructionset.SimulationConstructionSet;
import us.ihmc.simulationconstructionset.SimulationConstructionSetParameters;
import us.ihmc.simulationconstructionset.util.simulationTesting.SimulationTestingParameters;
import us.ihmc.tools.MemoryTools;

@Tag("gui-slow")
public class MocapToPelvisFrameConverterVisualTest
{
   private static final SimulationTestingParameters simulationTestingParameters = SimulationTestingParameters.createFromSystemProperties();
   private static final String modelDirectory = "models/GFE/atlas_description/meshes_unplugged/";
   private final Random random = new Random(456654321123L);

   @BeforeEach
   public void setUp()
   {
      MemoryTools.printCurrentMemoryUsageAndReturnUsedMemoryInMB(getClass().getSimpleName() + " before: ");
   }

   @AfterEach
   public void destroySimulationAndRecycleMemory()
   {
      if (simulationTestingParameters.getKeepSCSUp())
      {
         ThreadTools.sleepForever();
      }

      MemoryTools.printCurrentMemoryUsageAndReturnUsedMemoryInMB(getClass().getSimpleName() + " after test.");
   }

   @Test
   public void testVisuallyForMarkerToPelvisAlignment()
   {
      BambooTools.reportTestStartedMessage(simulationTestingParameters.getShowWindows());

      Vector3D pelvisTranslation = RandomGeometry.nextVector3D(random, 1.5);
      Vector3D mocapTranslation = RandomGeometry.nextVector3D(random, 1.5);
      RotationMatrix pelvisRotation = RandomGeometry.nextRotationMatrix(random);
      RotationMatrix mocapRotation = RandomGeometry.nextRotationMatrix(random);

      RigidBodyTransform pelvisToWorldTransform = new RigidBodyTransform(pelvisRotation, pelvisTranslation);
      RigidBodyTransform mocapToWorldTransform = new RigidBodyTransform(mocapRotation, mocapTranslation);

      RigidBodyTransform worldToMocapTransform = new RigidBodyTransform(mocapToWorldTransform);
      worldToMocapTransform.invert();

      RigidBodyTransform worldToPelvisTranform = new RigidBodyTransform(pelvisToWorldTransform);
      worldToPelvisTranform.invert();

      ReferenceFrame pelvisFrame = ReferenceFrameTools.constructFrameWithUnchangingTransformToParent("originalPelvisFrame", ReferenceFrame.getWorldFrame(), pelvisToWorldTransform);
      ReferenceFrame mocapFrame = ReferenceFrameTools.constructFrameWithUnchangingTransformToParent("mocapFrame", ReferenceFrame.getWorldFrame(), mocapToWorldTransform);

      RigidBodyTransform pelvisToMarker2Transform = MocapToPelvisFrameConverter.getPelvisToMarker2Transform();
      Vector3D pelvisToMarker2Translation = new Vector3D();
      pelvisToMarker2Transform.getTranslation(pelvisToMarker2Translation);
      pelvisToMarker2Translation.negate();
      FramePoint3D markerPoint2 = new FramePoint3D(pelvisFrame, pelvisToMarker2Translation);

      markerPoint2.changeFrame(ReferenceFrame.getWorldFrame());

      Graphics3DObject mocapAndPelvisGraphics = new Graphics3DObject();

      mocapAndPelvisGraphics.transform(pelvisToWorldTransform);
      mocapAndPelvisGraphics.addModelFile(modelDirectory + "pelvis.obj");
      mocapAndPelvisGraphics.identity();

      mocapAndPelvisGraphics.translate(markerPoint2);
      mocapAndPelvisGraphics.addSphere(0.005, YoAppearance.White());
      mocapAndPelvisGraphics.identity();

      mocapAndPelvisGraphics.transform(mocapToWorldTransform);
      mocapAndPelvisGraphics.addCoordinateSystem(0.5);

      RigidBodyTransform pelvisToMocapTransform = new RigidBodyTransform();
      pelvisFrame.getTransformToDesiredFrame(pelvisToMocapTransform, mocapFrame);
      Quaternion pelvisToMocapRotation = new Quaternion();
      pelvisToMocapTransform.getRotation(pelvisToMocapRotation);

      ArrayList<MocapMarker> mocapMarkers = new ArrayList<MocapMarker>();
      mocapMarkers.add(new MocapMarker(1, new Vector3D(), 0.024f));
      mocapMarkers.add(new MocapMarker(2, new Vector3D(), 0.024f));
      mocapMarkers.add(new MocapMarker(3, new Vector3D(), 0.024f));
      mocapMarkers.add(new MocapMarker(4, new Vector3D(), 0.024f));
      markerPoint2.changeFrame(mocapFrame);
      MocapRigidBody markerRigidBody = new MocapRigidBody(1, new Vector3D(markerPoint2), pelvisToMocapRotation, mocapMarkers, false);

      MocapToPelvisFrameConverter frameConverter = new MocapToPelvisFrameConverter();
      frameConverter.initialize(pelvisFrame, markerRigidBody);
      RigidBodyTransform computedPelvisTransform = new RigidBodyTransform();
      frameConverter.computePelvisToWorldTransform(markerRigidBody, computedPelvisTransform);

      mocapAndPelvisGraphics.identity();
      mocapAndPelvisGraphics.transform(computedPelvisTransform);
      mocapAndPelvisGraphics.addCoordinateSystem(0.5);

      SimulationConstructionSet scs = new SimulationConstructionSet(new Robot("dummy"), new SimulationConstructionSetParameters());
      scs.setGroundVisible(false);
      scs.addStaticLinkGraphics(mocapAndPelvisGraphics);
      scs.startOnAThread();
   }
}
