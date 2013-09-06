package us.ihmc.commonWalkingControlModules.momentumBasedController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.vecmath.Vector3d;

import org.ejml.data.DenseMatrix64F;

import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.ContactPointVisualizer;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.ContactableCylinderBody;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.ModifiableContactState;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.PlaneContactState;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.WrenchVisualizer;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.YoCylindricalContactState;
import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.YoPlaneContactState;
import us.ihmc.commonWalkingControlModules.controllers.regularWalkingGait.Updatable;
import us.ihmc.commonWalkingControlModules.dynamics.FullRobotModel;
import us.ihmc.commonWalkingControlModules.momentumBasedController.MomentumControlModuleBridge.MomentumControlModuleType;
import us.ihmc.commonWalkingControlModules.momentumBasedController.dataObjects.MomentumRateOfChangeData;
import us.ihmc.commonWalkingControlModules.momentumBasedController.optimization.OptimizationMomentumControlModule;
import us.ihmc.commonWalkingControlModules.outputs.ProcessedOutputsInterface;
import us.ihmc.commonWalkingControlModules.referenceFrames.CommonWalkingReferenceFrames;
import us.ihmc.commonWalkingControlModules.stateEstimation.DesiredCoMAndAngularAccelerationGrabber;
import us.ihmc.commonWalkingControlModules.stateEstimation.PointPositionGrabber;
import us.ihmc.commonWalkingControlModules.stateEstimation.PointPositionGrabberInterface;
import us.ihmc.commonWalkingControlModules.wrenchDistribution.CylindricalContactState;
import us.ihmc.robotSide.RobotSide;
import us.ihmc.robotSide.SideDependentList;
import us.ihmc.sensorProcessing.stateEstimation.StateEstimationDataFromControllerSink;
import us.ihmc.utilities.exeptions.NoConvergenceException;
import us.ihmc.utilities.math.MathTools;
import us.ihmc.utilities.math.geometry.FramePoint;
import us.ihmc.utilities.math.geometry.FramePoint2d;
import us.ihmc.utilities.math.geometry.FrameVector;
import us.ihmc.utilities.math.geometry.ReferenceFrame;
import us.ihmc.utilities.screwTheory.CenterOfMassJacobian;
import us.ihmc.utilities.screwTheory.GeometricJacobian;
import us.ihmc.utilities.screwTheory.InverseDynamicsCalculator;
import us.ihmc.utilities.screwTheory.InverseDynamicsJoint;
import us.ihmc.utilities.screwTheory.OneDoFJoint;
import us.ihmc.utilities.screwTheory.RigidBody;
import us.ihmc.utilities.screwTheory.ScrewTools;
import us.ihmc.utilities.screwTheory.SpatialAccelerationVector;
import us.ihmc.utilities.screwTheory.SpatialForceVector;
import us.ihmc.utilities.screwTheory.TotalMassCalculator;
import us.ihmc.utilities.screwTheory.TotalWrenchCalculator;
import us.ihmc.utilities.screwTheory.TwistCalculator;
import us.ihmc.utilities.screwTheory.Wrench;

import com.yobotics.simulationconstructionset.BooleanYoVariable;
import com.yobotics.simulationconstructionset.DoubleYoVariable;
import com.yobotics.simulationconstructionset.EnumYoVariable;
import com.yobotics.simulationconstructionset.YoVariableRegistry;
import com.yobotics.simulationconstructionset.util.graphics.DynamicGraphicObjectsListRegistry;
import com.yobotics.simulationconstructionset.util.math.frames.YoFrameVector;

public class MomentumBasedController
{
   public static final boolean SPY_ON_MOMENTUM_BASED_CONTROLLER = false;
   private final String name = getClass().getSimpleName();
   private final YoVariableRegistry registry = new YoVariableRegistry(name);

   private final ReferenceFrame centerOfMassFrame;

   private final FullRobotModel fullRobotModel;
   private final CenterOfMassJacobian centerOfMassJacobian;
   private final CommonWalkingReferenceFrames referenceFrames;
   private final TwistCalculator twistCalculator;

   private final SideDependentList<ContactablePlaneBody> feet, handsWithFingersBentBack, thighs;
   private final ContactablePlaneBody pelvis, pelvisBack;

   private final SideDependentList<ContactableCylinderBody> graspingHands;
   private final List<ContactablePlaneBody> listOfAllContactablePlaneBodies;
   private final List<ContactableCylinderBody> listOfAllContactableCylinderBodies;

   // Creating a Map that will contain all of the YoPlaneContactState and YoRollingContactState to pass to the MomentumControlModule compute method
   private final Map<ContactablePlaneBody, PlaneContactState> contactStates = new LinkedHashMap<ContactablePlaneBody, PlaneContactState>();
   private final LinkedHashMap<ContactablePlaneBody, YoPlaneContactState> planeContactStates = new LinkedHashMap<ContactablePlaneBody, YoPlaneContactState>();
   private final LinkedHashMap<ContactableCylinderBody, YoCylindricalContactState> cylindricalContactStates = new LinkedHashMap<ContactableCylinderBody,
                                                                                                                 YoCylindricalContactState>();
   private final List<ModifiableContactState> modifiableContactStates = new ArrayList<ModifiableContactState>();

   private final ArrayList<Updatable> updatables = new ArrayList<Updatable>();
   private final DoubleYoVariable yoTime;
   private final double controlDT;
   private final double gravity;

   private final YoFrameVector finalDesiredPelvisLinearAcceleration;
   private final YoFrameVector finalDesiredPelvisAngularAcceleration;
   private final YoFrameVector desiredPelvisForce;
   private final YoFrameVector desiredPelvisTorque;

   private final YoFrameVector admissibleDesiredGroundReactionTorque;
   private final YoFrameVector admissibleDesiredGroundReactionForce;
   private final YoFrameVector groundReactionTorqueCheck;
   private final YoFrameVector groundReactionForceCheck;

   private final LinkedHashMap<OneDoFJoint, DoubleYoVariable> desiredAccelerationYoVariables = new LinkedHashMap<OneDoFJoint, DoubleYoVariable>();

   private final ProcessedOutputsInterface processedOutputs;
   private final InverseDynamicsCalculator inverseDynamicsCalculator;

   private final DesiredCoMAndAngularAccelerationGrabber desiredCoMAndAngularAccelerationGrabber;
   private final BooleanYoVariable resetEstimatorPositionsToCurrent = new BooleanYoVariable("resetEstimatorPositionsToCurrent", registry);
   private final PointPositionGrabberInterface pointPositionGrabber;

   private final MomentumControlModuleBridge momentumControlModuleBridge; 
   
   private final SpatialForceVector gravitationalWrench;
   private final EnumYoVariable<RobotSide> upcomingSupportLeg = EnumYoVariable.create("upcomingSupportLeg", "", RobotSide.class, registry, true);    // FIXME: not general enough; this should not be here

   private final PlaneContactWrenchProcessor planeContactWrenchProcessor;
   private final MomentumBasedControllerSpy momentumBasedControllerSpy;
   private final ContactPointVisualizer contactPointVisualizer;
   private final WrenchVisualizer wrenchVisualizer;

   public MomentumBasedController(RigidBody estimationLink, ReferenceFrame estimationFrame, FullRobotModel fullRobotModel,
                                  CenterOfMassJacobian centerOfMassJacobian, CommonWalkingReferenceFrames referenceFrames, DoubleYoVariable yoTime,
                                  double gravityZ, TwistCalculator twistCalculator, SideDependentList<ContactablePlaneBody> feet,
                                  SideDependentList<ContactablePlaneBody> handsWithFingersBentBack, SideDependentList<ContactableCylinderBody> graspingHands,
                                  SideDependentList<ContactablePlaneBody> thighs, ContactablePlaneBody pelvis, ContactablePlaneBody pelvisBack,
                                  double controlDT, ProcessedOutputsInterface processedOutputs,
                                  OptimizationMomentumControlModule optimizationMomentumControlModule, OldMomentumControlModule oldMomentumControlModule,
                                  ArrayList<Updatable> updatables, StateEstimationDataFromControllerSink stateEstimationDataFromControllerSink,
                                  DynamicGraphicObjectsListRegistry dynamicGraphicObjectsListRegistry)
   {
      momentumControlModuleBridge = new MomentumControlModuleBridge(optimizationMomentumControlModule, oldMomentumControlModule, registry);
      
      if (SPY_ON_MOMENTUM_BASED_CONTROLLER)
         momentumBasedControllerSpy = new MomentumBasedControllerSpy(registry);
      else
         momentumBasedControllerSpy = null;

      centerOfMassFrame = referenceFrames.getCenterOfMassFrame();

      MathTools.checkIfInRange(gravityZ, 0.0, Double.POSITIVE_INFINITY);

      this.fullRobotModel = fullRobotModel;
      this.centerOfMassJacobian = centerOfMassJacobian;
      this.referenceFrames = referenceFrames;
      this.twistCalculator = twistCalculator;
      this.controlDT = controlDT;
      this.gravity = gravityZ;
      this.yoTime = yoTime;

      // Initialize the contactable bodies
      this.feet = feet;
      this.handsWithFingersBentBack = handsWithFingersBentBack;
      this.graspingHands = graspingHands;    // Cylindrical contact used to bear load while grasping a cylinder
      this.thighs = thighs;
      this.pelvis = pelvis;
      this.pelvisBack = pelvisBack;

      RigidBody elevator = fullRobotModel.getElevator();

      this.processedOutputs = processedOutputs;
      this.inverseDynamicsCalculator = new InverseDynamicsCalculator(twistCalculator, gravityZ);

      double totalMass = TotalMassCalculator.computeSubTreeMass(elevator);

      if (stateEstimationDataFromControllerSink != null)
      {
         this.desiredCoMAndAngularAccelerationGrabber = new DesiredCoMAndAngularAccelerationGrabber(stateEstimationDataFromControllerSink, estimationLink,
                 estimationFrame, totalMass, registry);

         double touchdownTime = 0.12;
         double minCoPDistance = 0.01;

//       this.pointPositionGrabber = new SingleReferenceFramePointPositionGrabber(stateEstimationDataFromControllerSink, registry, controlDT, touchdownTime, minCoPDistance);
         this.pointPositionGrabber = new PointPositionGrabber(stateEstimationDataFromControllerSink, registry, controlDT, touchdownTime, minCoPDistance);
         setDelayTimeBeforeTrustingContacts(touchdownTime);
      }
      else
      {
         this.desiredCoMAndAngularAccelerationGrabber = null;
         this.pointPositionGrabber = null;
      }

      gravitationalWrench = new SpatialForceVector(centerOfMassFrame, new Vector3d(0.0, 0.0, totalMass * gravityZ), new Vector3d());


      ReferenceFrame pelvisFrame = referenceFrames.getPelvisFrame();
      this.finalDesiredPelvisLinearAcceleration = new YoFrameVector("finalDesiredPelvisLinearAcceleration", "", pelvisFrame, registry);
      this.finalDesiredPelvisAngularAcceleration = new YoFrameVector("finalDesiredPelvisAngularAcceleration", "", pelvisFrame, registry);
      this.desiredPelvisForce = new YoFrameVector("desiredPelvisForce", "", centerOfMassFrame, registry);
      this.desiredPelvisTorque = new YoFrameVector("desiredPelvisTorque", "", centerOfMassFrame, registry);


      this.admissibleDesiredGroundReactionTorque = new YoFrameVector("admissibleDesiredGroundReactionTorque", centerOfMassFrame, registry);
      this.admissibleDesiredGroundReactionForce = new YoFrameVector("admissibleDesiredGroundReactionForce", centerOfMassFrame, registry);

      this.groundReactionTorqueCheck = new YoFrameVector("groundReactionTorqueCheck", centerOfMassFrame, registry);
      this.groundReactionForceCheck = new YoFrameVector("groundReactionForceCheck", centerOfMassFrame, registry);


      if (updatables != null)
      {
         this.updatables.addAll(updatables);
      }


      double coefficientOfFriction = 1.0;    // TODO: magic number...

      // TODO: get rid of the null checks
      this.listOfAllContactablePlaneBodies = new ArrayList<ContactablePlaneBody>();

      if (feet != null)
      {
         this.listOfAllContactablePlaneBodies.addAll(feet.values());
      }

      if (handsWithFingersBentBack != null)
      {
         this.listOfAllContactablePlaneBodies.addAll(handsWithFingersBentBack.values());
      }

      if (thighs != null)
      {
         this.listOfAllContactablePlaneBodies.addAll(thighs.values());
      }

      if (pelvis != null)
         this.listOfAllContactablePlaneBodies.add(pelvis);

      if (pelvisBack != null)
         this.listOfAllContactablePlaneBodies.add(pelvisBack);

      for (ContactablePlaneBody contactablePlaneBody : this.listOfAllContactablePlaneBodies)
      {
//       RigidBody rigidBody = contactablePlaneBody.getRigidBody();
         YoPlaneContactState contactState = new YoPlaneContactState(contactablePlaneBody.getPlaneFrame().getName(), contactablePlaneBody.getBodyFrame(),
                                               contactablePlaneBody.getPlaneFrame(), registry);

//       contactState.set(contactablePlaneBody.getContactPoints2d(), coefficientOfFriction);    // initialize with flat 'feet'
         planeContactStates.put(contactablePlaneBody, contactState);
      }

      InverseDynamicsJoint[] joints = ScrewTools.computeSupportAndSubtreeJoints(fullRobotModel.getRootJoint().getSuccessor());
      for (InverseDynamicsJoint joint : joints)
      {
         if (joint instanceof OneDoFJoint)
         {
            desiredAccelerationYoVariables.put((OneDoFJoint) joint, new DoubleYoVariable(joint.getName() + "qdd_d", registry));
         }
      }

      for (ContactablePlaneBody contactablePlaneBody : planeContactStates.keySet())
      {
         contactStates.put(contactablePlaneBody, planeContactStates.get(contactablePlaneBody));
      }


      this.listOfAllContactableCylinderBodies = new ArrayList<ContactableCylinderBody>();

      if (graspingHands != null)
      {
         this.listOfAllContactableCylinderBodies.addAll(graspingHands.values());
      }

//    coefficientOfFriction = 0.0;
      for (ContactableCylinderBody contactableCylinderBody : this.listOfAllContactableCylinderBodies)
      {
         RigidBody rigidBody = contactableCylinderBody.getRigidBody();

         // YoCylindricalContactState: used to enable load bearing with hands while grasping a cylinder
         YoCylindricalContactState cylindricalContactState = new YoCylindricalContactState(rigidBody.getName(),
                                                                rigidBody.getParentJoint().getFrameAfterJoint(), contactableCylinderBody.getCylinderFrame(),
                                                                registry, dynamicGraphicObjectsListRegistry);
         cylindricalContactState.set(coefficientOfFriction, contactableCylinderBody, false);
         cylindricalContactStates.put(contactableCylinderBody, cylindricalContactState);
      }

      modifiableContactStates.addAll(planeContactStates.values());
      modifiableContactStates.addAll(cylindricalContactStates.values());

      this.planeContactWrenchProcessor = new PlaneContactWrenchProcessor(this.listOfAllContactablePlaneBodies, dynamicGraphicObjectsListRegistry, registry);

      if (dynamicGraphicObjectsListRegistry != null)
      {
         contactPointVisualizer = new ContactPointVisualizer(20, dynamicGraphicObjectsListRegistry, registry);
         wrenchVisualizer = new WrenchVisualizer(7, dynamicGraphicObjectsListRegistry, registry);
      }
      else
      {
         contactPointVisualizer = null;
         wrenchVisualizer = null;
      }
   }

   public SideDependentList<ContactablePlaneBody> getFeet()
   {
      return feet;
   }

   public void getFeetContactStates(ArrayList<PlaneContactState> feetContactStatesToPack)
   {
      for (RobotSide robotSide : RobotSide.values)
      {
         feetContactStatesToPack.add(contactStates.get(feet.get(robotSide)));
      }
   }

   public SpatialForceVector getGravitationalWrench()
   {
      return gravitationalWrench;
   }

   private static double computeDesiredAcceleration(double k, double d, double qDesired, double qdDesired, OneDoFJoint joint)
   {
      return k * (qDesired - joint.getQ()) + d * (qdDesired - joint.getQd());
   }

   public void setExternalWrenchToCompensateFor(RigidBody rigidBody, Wrench wrench)
   {
      if (momentumBasedControllerSpy != null)
      {
         momentumBasedControllerSpy.setExternalWrenchToCompensateFor(rigidBody, wrench);
      }

//      momentumControlModuleBridge.getActiveMomentumControlModule().setExternalWrenchToCompensateFor(rigidBody, wrench);
      momentumControlModuleBridge.setExternalWrenchToCompensateFor(rigidBody, wrench);
   }

   public void setDesiredPointAcceleration(GeometricJacobian rootToEndEffectorJacobian, FramePoint contactPoint, FrameVector desiredAcceleration)
   {
      if (momentumBasedControllerSpy != null)
      {
         momentumBasedControllerSpy.setDesiredPointAcceleration(rootToEndEffectorJacobian, contactPoint, desiredAcceleration);
      }

      momentumControlModuleBridge.setDesiredPointAcceleration(rootToEndEffectorJacobian, contactPoint, desiredAcceleration);
   }

   public void setDesiredPointAcceleration(GeometricJacobian rootToEndEffectorJacobian, FramePoint contactPoint, FrameVector desiredAcceleration, DenseMatrix64F selectionMatrix)
   {
      if (momentumBasedControllerSpy != null)
      {
         momentumBasedControllerSpy.setDesiredPointAcceleration(rootToEndEffectorJacobian, contactPoint, desiredAcceleration);
      }

      momentumControlModuleBridge.setDesiredPointAcceleration(rootToEndEffectorJacobian, contactPoint, desiredAcceleration, selectionMatrix);
   }

   // TODO: Temporary method for a big refactor allowing switching between high level behaviors
   public void doPrioritaryControl()
   {
      if (momentumBasedControllerSpy != null)
      {
         momentumBasedControllerSpy.doPrioritaryControl();
      }

      callUpdatables();

      inverseDynamicsCalculator.reset();
      momentumControlModuleBridge.reset();
      
      resetWeightsForContactRegularization();
   }

   //TODO  (Sylvain): get rid of that
   private void resetWeightsForContactRegularization()
   {
      // TODO: get rid of contactStates or planeContactStates. This is confusing.
      for (PlaneContactState contactState : contactStates.values())
      {
         contactState.resetContactRegularization();
      }

      for (PlaneContactState contactState : planeContactStates.values())
      {
         contactState.resetContactRegularization();
      }
      
      for (CylindricalContactState contactState : cylindricalContactStates.values())
      {
         contactState.resetContactRegularization();
      }
   }

   // TODO: Temporary method for a big refactor allowing switching between high level behaviors
   public void doSecondaryControl()
   {
      if (contactPointVisualizer != null)
         contactPointVisualizer.update(this.contactStates.values());

      updateMomentumBasedControllerSpy();

      try
      {
         momentumControlModuleBridge.compute(this.contactStates, this.cylindricalContactStates, upcomingSupportLeg.getEnumValue());
      }
      catch (NoConvergenceException e)
      {
         if (momentumBasedControllerSpy != null)
            momentumBasedControllerSpy.printMomentumCommands(System.err);
         throw new RuntimeException(e);
      }

      SpatialForceVector desiredCentroidalMomentumRate = momentumControlModuleBridge.getDesiredCentroidalMomentumRate();
      Map<RigidBody, Wrench> externalWrenches = momentumControlModuleBridge.getExternalWrenches();


      for (RigidBody rigidBody : externalWrenches.keySet())
      {
         inverseDynamicsCalculator.setExternalWrench(rigidBody, externalWrenches.get(rigidBody));
      }

      planeContactWrenchProcessor.compute(externalWrenches);
      if (wrenchVisualizer != null)
         wrenchVisualizer.visualize(externalWrenches.values());

      SpatialForceVector totalGroundReactionWrench = new SpatialForceVector(centerOfMassFrame);
      Wrench admissibleGroundReactionWrench = TotalWrenchCalculator.computeTotalWrench(externalWrenches.values(),
                                                 totalGroundReactionWrench.getExpressedInFrame());
      admissibleDesiredGroundReactionTorque.set(admissibleGroundReactionWrench.getAngularPartCopy());
      admissibleDesiredGroundReactionForce.set(admissibleGroundReactionWrench.getLinearPartCopy());

      SpatialForceVector groundReactionWrenchCheck = inverseDynamicsCalculator.computeTotalExternalWrench(centerOfMassFrame);
      groundReactionTorqueCheck.set(groundReactionWrenchCheck.getAngularPartCopy());
      groundReactionForceCheck.set(groundReactionWrenchCheck.getLinearPartCopy());

      if (desiredCoMAndAngularAccelerationGrabber != null)
         this.desiredCoMAndAngularAccelerationGrabber.set(inverseDynamicsCalculator.getSpatialAccelerationCalculator(), desiredCentroidalMomentumRate);

      if (pointPositionGrabber != null)
      {
         if (resetEstimatorPositionsToCurrent.getBooleanValue())
         {
            pointPositionGrabber.resetToCurrentLocations(contactStates, planeContactWrenchProcessor.getCops());
            resetEstimatorPositionsToCurrent.set(false);
         }
         pointPositionGrabber.set(contactStates, planeContactWrenchProcessor.getCops());
      }
      
      inverseDynamicsCalculator.compute();

      if (processedOutputs != null)
         fullRobotModel.setTorques(processedOutputs);
      updateYoVariables();
   }

   public void requestResetEstimatorPositionsToCurrent()
   {
      this.resetEstimatorPositionsToCurrent.set(true);
   }
   
   private void updateMomentumBasedControllerSpy()
   {
      if (momentumBasedControllerSpy != null)
      {
         for (ContactablePlaneBody contactablePlaneBody : planeContactStates.keySet())
         {
            YoPlaneContactState contactState = planeContactStates.get(contactablePlaneBody);
            if (contactState.inContact())
            {
               momentumBasedControllerSpy.setPlaneContactState(contactablePlaneBody, contactState.getContactPoints2d(),
                       contactState.getCoefficientOfFriction(), contactState.getContactNormalFrameVector());
            }
         }

         for (ContactableCylinderBody contactableCylinderBody : cylindricalContactStates.keySet())
         {
            YoCylindricalContactState contactState = cylindricalContactStates.get(contactableCylinderBody);
            if (contactState.isInContact())
               momentumBasedControllerSpy.setCylindricalContactInContact(contactableCylinderBody, contactState.isInContact());
         }

         momentumBasedControllerSpy.doSecondaryControl();
      }
   }

   private void resetGroundReactionWrenchFilter()
   {
      momentumControlModuleBridge.resetGroundReactionWrenchFilter();
   }

   private void callUpdatables()
   {
      double time = yoTime.getDoubleValue();
      for (Updatable updatable : updatables)
      {
         updatable.update(time);
      }
   }

   public void addUpdatable(Updatable updatable)
   {
      updatables.add(updatable);
   }

   public void doPDControl(OneDoFJoint[] joints, double k, double d)
   {
      for (OneDoFJoint joint : joints)
      {
         doPDControl(joint, k, d, 0.0, 0.0);
      }
   }

   public void doPDControl(OneDoFJoint joint, double k, double d, double desiredPosition, double desiredVelocity)
   {
      double desiredAcceleration = computeDesiredAcceleration(k, d, desiredPosition, desiredVelocity, joint);
      setOneDoFJointAcceleration(joint, desiredAcceleration);
   }

   public void setOneDoFJointAcceleration(OneDoFJoint joint, double desiredAcceleration)
   {
      if (momentumBasedControllerSpy != null)
      {
         momentumBasedControllerSpy.setOneDoFJointAcceleration(joint, desiredAcceleration);
      }

      DenseMatrix64F jointAcceleration = new DenseMatrix64F(joint.getDegreesOfFreedom(), 1);
      jointAcceleration.set(0, 0, desiredAcceleration);
      momentumControlModuleBridge.setDesiredJointAcceleration(joint, jointAcceleration);
  
   }

   private void updateYoVariables()
   {
      SpatialAccelerationVector pelvisAcceleration = new SpatialAccelerationVector();
      fullRobotModel.getRootJoint().packDesiredJointAcceleration(pelvisAcceleration);

      finalDesiredPelvisAngularAcceleration.checkReferenceFrameMatch(pelvisAcceleration.getExpressedInFrame());
      finalDesiredPelvisAngularAcceleration.set(pelvisAcceleration.getAngularPartCopy());

      finalDesiredPelvisLinearAcceleration.checkReferenceFrameMatch(pelvisAcceleration.getExpressedInFrame());
      finalDesiredPelvisLinearAcceleration.set(pelvisAcceleration.getLinearPartCopy());

      Wrench pelvisJointWrench = new Wrench();
      fullRobotModel.getRootJoint().packWrench(pelvisJointWrench);
      pelvisJointWrench.changeFrame(referenceFrames.getCenterOfMassFrame());
      desiredPelvisForce.set(pelvisJointWrench.getLinearPartCopy());
      desiredPelvisTorque.set(pelvisJointWrench.getAngularPartCopy());

      for (OneDoFJoint joint : desiredAccelerationYoVariables.keySet())
      {
         desiredAccelerationYoVariables.get(joint).set(joint.getQddDesired());
      }
   }

   public void initialize()
   {
      // When you initialize into this controller, reset the estimator positions to current. Otherwise it might be in a bad state 
      // where the feet are all jacked up. For example, after falling and getting back up.
      resetEstimatorPositionsToCurrent.set(true);
      inverseDynamicsCalculator.compute();
      momentumControlModuleBridge.initialize();
      planeContactWrenchProcessor.initialize();
      callUpdatables();
   }

   public YoVariableRegistry getYoVariableRegistry()
   {
      return registry;
   }

   public String getName()
   {
      return name;
   }

   public String getDescription()
   {
      return getName();
   }

   public FramePoint2d getCoP(ContactablePlaneBody contactablePlaneBody)
   {
      return planeContactWrenchProcessor.getCops().get(contactablePlaneBody);
   }


   public void setPlaneContactState(ContactablePlaneBody contactableBody, List<FramePoint2d> contactPoints, double coefficientOfFriction,
                                    FrameVector normalContactVector)
   {
      YoPlaneContactState yoPlaneContactState = planeContactStates.get(contactableBody);

      if (normalContactVector == null)
      {
         yoPlaneContactState.set(contactPoints, coefficientOfFriction);
      }
      else
      {
         yoPlaneContactState.set(contactPoints, coefficientOfFriction, normalContactVector);
      }
   }

   public void setCylindricalContactInContact(ContactableCylinderBody contactableCylinderBody, boolean setInContact)
   {
      YoCylindricalContactState yoCylindricalContactState = cylindricalContactStates.get(contactableCylinderBody);
      yoCylindricalContactState.setInContact(setInContact);
   }

   public ReferenceFrame getCenterOfMassFrame()
   {
      return centerOfMassFrame;
   }

   public void setDesiredSpatialAcceleration(GeometricJacobian jacobian, TaskspaceConstraintData taskspaceConstraintData)
   {
      if (momentumBasedControllerSpy != null)
      {
         momentumBasedControllerSpy.setDesiredSpatialAcceleration(jacobian, taskspaceConstraintData);
      }

      momentumControlModuleBridge.setDesiredSpatialAcceleration(jacobian, taskspaceConstraintData);
   }

   public void setDesiredRateOfChangeOfMomentum(MomentumRateOfChangeData momentumRateOfChangeData)
   {
      if (momentumBasedControllerSpy != null)
      {
         momentumBasedControllerSpy.setDesiredRateOfChangeOfMomentum(momentumRateOfChangeData);
      }

      momentumControlModuleBridge.setDesiredRateOfChangeOfMomentum(momentumRateOfChangeData);
   }

   public ReferenceFrame getPelvisZUpFrame()
   {
      return referenceFrames.getPelvisZUpFrame();
   }

   public EnumYoVariable<RobotSide> getUpcomingSupportLeg()
   {
      return upcomingSupportLeg;
   }

   public CommonWalkingReferenceFrames getReferenceFrames()
   {
      return referenceFrames;
   }

   public PointPositionGrabberInterface getPointPositionGrabber()
   {
      return pointPositionGrabber;
   }

   public DoubleYoVariable getYoTime()
   {
      return yoTime;
   }

   public double getGravityZ()
   {
      return gravity;
   }

   public double getControlDT()
   {
      return controlDT;
   }

   public FullRobotModel getFullRobotModel()
   {
      return fullRobotModel;
   }

   public TwistCalculator getTwistCalculator()
   {
      return twistCalculator;
   }

   public CenterOfMassJacobian getCenterOfMassJacobian()
   {
      return centerOfMassJacobian;
   }


   public boolean isUsingOptimizationMomentumControlModule()
   {
      return momentumControlModuleBridge.isUsingOptimizationMomentumControlModule();
   }

   public FrameVector getAdmissibleDesiredGroundReactionForceCopy()
   {
      return admissibleDesiredGroundReactionForce.getFrameVectorCopy();
   }

   public FrameVector getAdmissibleDesiredGroundReactionTorqueCopy()
   {
      return admissibleDesiredGroundReactionTorque.getFrameVectorCopy();
   }

   public SideDependentList<ContactablePlaneBody> getContactablePlaneFeet()
   {
      return feet;
   }

   public SideDependentList<ContactablePlaneBody> getContactablePlaneHandsWithFingersBentBack()
   {
      return handsWithFingersBentBack;
   }

   public ContactablePlaneBody getContactablePlaneHandWithFingersBentBack(RobotSide robotSide)
   {
      return handsWithFingersBentBack.get(robotSide);
   }

   public SideDependentList<ContactableCylinderBody> getContactableCylinderHands()
   {
      return graspingHands;
   }

   public ContactableCylinderBody getContactableCylinderHand(RobotSide robotSide)
   {
      return graspingHands.get(robotSide);
   }

   public SideDependentList<ContactablePlaneBody> getContactablePlaneThighs()
   {
      return thighs;
   }

   public List<FramePoint> getContactPoints(ContactablePlaneBody contactablePlaneBody)
   {
      return contactStates.get(contactablePlaneBody).getContactPoints();
   }

   public PlaneContactState getContactState(ContactablePlaneBody contactablePlaneBody)
   {
      return contactStates.get(contactablePlaneBody);
   }

   public Collection<PlaneContactState> getPlaneContactStates()
   {
      return contactStates.values();
   }

   public void clearContacts()
   {
      for (ModifiableContactState modifiableContactState : modifiableContactStates)
      {
         modifiableContactState.clear();
      }
   }

   public ContactablePlaneBody getContactablePlanePelvis()
   {
      return pelvis;
   }

   public ContactablePlaneBody getContactablePlanePelvisBack()
   {
      return pelvisBack;
   }

   public void setMomentumControlModuleToUse(MomentumControlModuleType momentumControlModuleToUse)
   {
      momentumControlModuleBridge.setMomentumControlModuleToUse(momentumControlModuleToUse);
   }

   public void setDelayTimeBeforeTrustingContacts(double delayTimeBeforeTrustingContacts)
   {
      pointPositionGrabber.setDelayTimeBeforeTrustingContacts(delayTimeBeforeTrustingContacts);
   }
   
   //TODO (Sylvain): get rid of these methods changing wRho & wPhi of contact state
   public void setPlaneContactState_wRho (ContactablePlaneBody contactablePlaneBody, double wRho)
   {
      planeContactStates.get(contactablePlaneBody).setRhoContactRegularization(wRho);
   }

   public void setCylindricalContactState_wRho_wPhi(ContactableCylinderBody contactableCylinderBody, double wRho, double wPhi)
   {
      cylindricalContactStates.get(contactableCylinderBody).setRhoContactRegularization(wRho);
      cylindricalContactStates.get(contactableCylinderBody).setPhiContactRegularization(wPhi);
   }

   public void setCylindricalContactStateProperties(ContactableCylinderBody contactableCylinderBody, double coefficientOfFriction, double gripStrength,
         double cylinderRadius, double halfHandWidth, double gripWeaknessFactor, boolean inContact)
   {
      cylindricalContactStates.get(contactableCylinderBody).set(coefficientOfFriction, gripStrength, cylinderRadius, halfHandWidth, gripWeaknessFactor,
            inContact);
   }

   public void setCylindricalContactStateProperties(ContactableCylinderBody contactableCylinderBody, double coefficientOfFriction, boolean inContact)
   {
      cylindricalContactStates.get(contactableCylinderBody).set(coefficientOfFriction, contactableCylinderBody, inContact);
   }
}
