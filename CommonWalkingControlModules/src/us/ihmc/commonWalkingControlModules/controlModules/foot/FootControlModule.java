package us.ihmc.commonWalkingControlModules.controlModules.foot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;

import us.ihmc.commonWalkingControlModules.bipedSupportPolygons.ContactablePlaneBody;
import us.ihmc.commonWalkingControlModules.configurations.WalkingControllerParameters;
import us.ihmc.commonWalkingControlModules.controlModules.RigidBodySpatialAccelerationControlModule;
import us.ihmc.commonWalkingControlModules.desiredFootStep.DesiredFootstepCalculatorTools;
import us.ihmc.commonWalkingControlModules.kinematics.SpatialAccelerationProjector;
import us.ihmc.commonWalkingControlModules.momentumBasedController.MomentumBasedController;
import us.ihmc.commonWalkingControlModules.trajectories.CoMHeightTimeDerivativesData;
import us.ihmc.commonWalkingControlModules.trajectories.OrientationProvider;
import us.ihmc.robotSide.RobotSide;
import us.ihmc.utilities.math.geometry.FrameVector;
import us.ihmc.utilities.math.geometry.FrameVector2d;
import us.ihmc.utilities.math.geometry.ReferenceFrame;
import us.ihmc.utilities.screwTheory.GeometricJacobian;
import us.ihmc.utilities.screwTheory.RigidBody;
import us.ihmc.utilities.screwTheory.TwistCalculator;

import com.yobotics.simulationconstructionset.BooleanYoVariable;
import com.yobotics.simulationconstructionset.DoubleYoVariable;
import com.yobotics.simulationconstructionset.EnumYoVariable;
import com.yobotics.simulationconstructionset.YoVariableRegistry;
import com.yobotics.simulationconstructionset.util.GainCalculator;
import com.yobotics.simulationconstructionset.util.graphics.DynamicGraphicObjectsListRegistry;
import com.yobotics.simulationconstructionset.util.math.frames.YoFramePoint;
import com.yobotics.simulationconstructionset.util.math.frames.YoFrameVector;
import com.yobotics.simulationconstructionset.util.statemachines.State;
import com.yobotics.simulationconstructionset.util.statemachines.StateMachine;
import com.yobotics.simulationconstructionset.util.statemachines.StateTransition;
import com.yobotics.simulationconstructionset.util.trajectory.DoubleProvider;
import com.yobotics.simulationconstructionset.util.trajectory.DoubleTrajectoryGenerator;
import com.yobotics.simulationconstructionset.util.trajectory.PositionProvider;
import com.yobotics.simulationconstructionset.util.trajectory.TrajectoryParametersProvider;

public class FootControlModule
{
   private static final ReferenceFrame worldFrame = ReferenceFrame.getWorldFrame();
   private final YoVariableRegistry registry;
   private final ContactablePlaneBody contactableBody;

   public enum ConstraintType
   {
      FULL, HOLD_POSITION, HEEL_TOUCHDOWN, TOES_TOUCHDOWN, TOES, SWING, MOVE_STRAIGHT
   }
   private final StateMachine<ConstraintType> stateMachine;
   private final EnumYoVariable<ConstraintType> requestedState;
   private final EnumMap<ConstraintType, boolean[]> contactStatesMap = new EnumMap<ConstraintType, boolean[]>(ConstraintType.class);

   private final RigidBodySpatialAccelerationControlModule accelerationControlModule;
   private final SpatialAccelerationProjector spatialAccelerationProjector;
//   private final DenseMatrix64F jointVelocities;
   private final MomentumBasedController momentumBasedController;

   private final DoubleYoVariable jacobianDeterminant;
   private final BooleanYoVariable jacobianDeterminantInRange;
   private final BooleanYoVariable isUnconstrained;
   private final BooleanYoVariable isTrajectoryDone;

   private final BooleanYoVariable doSingularityEscape;
   private final BooleanYoVariable waitSingularityEscapeBeforeTransitionToNextState;
   private final DoubleYoVariable singularityEscapeNullspaceMultiplier;
   private final DoubleYoVariable nullspaceMultiplier;
   private final GeometricJacobian jacobian;
//   private final NullspaceCalculator nullspaceCalculator;
   
   private final LegSingularityAndKneeCollapseAvoidanceControlModule legSingularityAndKneeCollapseAvoidanceControlModule;

   private final BooleanYoVariable forceFootAccelerateIntoGround;
   private final BooleanYoVariable requestHoldPosition;
   private final FrameVector fullyConstrainedNormalContactVector;
   
   private final BooleanYoVariable doFancyOnToesControl;
   
   private final YoFramePoint yoDesiredPosition;
   private final YoFrameVector yoDesiredLinearVelocity;
   private final YoFrameVector yoDesiredLinearAcceleration;

   private final HoldPositionState holdPositionState;
   private final SwingState swingState;
   private final MoveStraightState moveStraightState;
   private final TouchdownState touchdownOnToesState;
   private final TouchdownState touchdwonOnHeelState;
   private final OnToesState onToesState;
   
   public FootControlModule(int jacobianId, RobotSide robotSide,
         DoubleTrajectoryGenerator pitchTouchdownTrajectoryGenerator, DoubleProvider maximumTakeoffAngle,
         BooleanYoVariable requestHoldPosition, WalkingControllerParameters walkingControllerParameters,
         
         DoubleProvider swingTimeProvider,
         PositionProvider finalPositionProvider,
         OrientationProvider finalOrientationProvider, TrajectoryParametersProvider trajectoryParametersProvider,
         
         DynamicGraphicObjectsListRegistry dynamicGraphicObjectsListRegistry, MomentumBasedController momentumBasedController,
         YoVariableRegistry parentRegistry)
   {
      // remove and test:
      contactableBody = momentumBasedController.getContactableFeet().get(robotSide);
      momentumBasedController.setPlaneContactCoefficientOfFriction(contactableBody, 0.8);
      
      RigidBody rigidBody = contactableBody.getRigidBody();
      String namePrefix = rigidBody.getName();
      registry = new YoVariableRegistry(namePrefix + getClass().getSimpleName());
      parentRegistry.addChild(registry);
      
      this.jacobian = momentumBasedController.getJacobian(jacobianId);
      if (rigidBody != jacobian.getEndEffector())
         throw new RuntimeException("contactablePlaneBody does not match jacobian end effector!");
      
      this.requestedState = EnumYoVariable.create(namePrefix + "RequestedState", "", ConstraintType.class, registry, true);
      this.momentumBasedController = momentumBasedController;
      this.requestHoldPosition = requestHoldPosition;
      
      fullyConstrainedNormalContactVector = new FrameVector(contactableBody.getPlaneFrame(), 0.0, 0.0, 1.0);
      
      ReferenceFrame bodyFrame = contactableBody.getBodyFrame();
      TwistCalculator twistCalculator = momentumBasedController.getTwistCalculator();
      accelerationControlModule = new RigidBodySpatialAccelerationControlModule(namePrefix, twistCalculator, rigidBody, bodyFrame, momentumBasedController.getControlDT(), registry);
      spatialAccelerationProjector = new SpatialAccelerationProjector(namePrefix + "SpatialAccelerationProjector", registry);
      doSingularityEscape = new BooleanYoVariable(namePrefix + "DoSingularityEscape", registry);
      waitSingularityEscapeBeforeTransitionToNextState = new BooleanYoVariable(namePrefix + "WaitSingularityEscapeBeforeTransitionToNextState", registry);
      jacobianDeterminant = new DoubleYoVariable(namePrefix + "JacobianDeterminant", registry);
      jacobianDeterminantInRange = new BooleanYoVariable(namePrefix + "JacobianDeterminantInRange", registry);
      nullspaceMultiplier = new DoubleYoVariable(namePrefix + "NullspaceMultiplier", registry);
//      nullspaceCalculator = new NullspaceCalculator(jacobian.getNumberOfColumns(), true);
      singularityEscapeNullspaceMultiplier = new DoubleYoVariable(namePrefix + "SingularityEscapeNullspaceMultiplier", registry);
      isTrajectoryDone = new BooleanYoVariable(namePrefix + "IsTrajectoryDone", registry);
      isUnconstrained = new BooleanYoVariable(namePrefix + "IsUnconstrained", registry);
      forceFootAccelerateIntoGround = new BooleanYoVariable(namePrefix + "ForceFootAccelerateIntoGround", registry);
      
      yoDesiredLinearVelocity = new YoFrameVector(namePrefix + "DesiredLinearVelocity", worldFrame, registry);
      yoDesiredLinearAcceleration = new YoFrameVector(namePrefix + "DesiredLinearAcceleration", worldFrame, registry);
      yoDesiredPosition = new YoFramePoint(namePrefix + "DesiredPosition", worldFrame, registry);
      yoDesiredPosition.setToNaN();
      
      doFancyOnToesControl = new BooleanYoVariable(contactableBody.getName() + "DoFancyOnToesControl", registry);
      if (walkingControllerParameters.isRunningOnRealRobot())
         doFancyOnToesControl.set(false);
      else
         doFancyOnToesControl.set(true);
      
//      jointVelocities = new DenseMatrix64F(ScrewTools.computeDegreesOfFreedom(jacobian.getJointsInOrder()), 1);
      
      legSingularityAndKneeCollapseAvoidanceControlModule =
            new LegSingularityAndKneeCollapseAvoidanceControlModule(namePrefix, contactableBody, robotSide,
                  walkingControllerParameters, momentumBasedController, dynamicGraphicObjectsListRegistry, registry);
      
      // set up states and state machine
      DoubleYoVariable time = momentumBasedController.getYoTime();
      stateMachine = new StateMachine<ConstraintType>(namePrefix + "State", namePrefix + "SwitchTime", ConstraintType.class, time, registry);
      setupContactStatesMap();
      
      List<AbstractFootControlState> states = new ArrayList<AbstractFootControlState>();
      touchdownOnToesState = new TouchdownState(ConstraintType.TOES_TOUCHDOWN, pitchTouchdownTrajectoryGenerator,
            yoDesiredPosition, yoDesiredLinearVelocity, yoDesiredLinearAcceleration, accelerationControlModule,
            momentumBasedController, contactableBody, requestedState, jacobianId, nullspaceMultiplier,
            jacobianDeterminantInRange, doSingularityEscape, forceFootAccelerateIntoGround,
            legSingularityAndKneeCollapseAvoidanceControlModule, robotSide,registry,
            spatialAccelerationProjector);
      states.add(touchdownOnToesState);
      
      touchdwonOnHeelState = new TouchdownState(ConstraintType.HEEL_TOUCHDOWN, pitchTouchdownTrajectoryGenerator,
            yoDesiredPosition, yoDesiredLinearVelocity, yoDesiredLinearAcceleration, accelerationControlModule,
            momentumBasedController, contactableBody, requestedState, jacobianId, nullspaceMultiplier,
            jacobianDeterminantInRange, doSingularityEscape, forceFootAccelerateIntoGround,
            legSingularityAndKneeCollapseAvoidanceControlModule, robotSide,registry,
            spatialAccelerationProjector);
      states.add(touchdwonOnHeelState);
      
      onToesState = new OnToesState(maximumTakeoffAngle,
            yoDesiredPosition, yoDesiredLinearVelocity, yoDesiredLinearAcceleration, accelerationControlModule,
            momentumBasedController, contactableBody, requestedState, jacobianId, nullspaceMultiplier,
            jacobianDeterminantInRange, doSingularityEscape, forceFootAccelerateIntoGround,
            legSingularityAndKneeCollapseAvoidanceControlModule, robotSide,registry,
            spatialAccelerationProjector, contactStatesMap);
      states.add(onToesState);
      
      states.add(new FullyConstrainedState(yoDesiredPosition, yoDesiredLinearVelocity,
            yoDesiredLinearAcceleration, accelerationControlModule, momentumBasedController,
            contactableBody, requestHoldPosition, requestedState, jacobianId, nullspaceMultiplier,
            jacobianDeterminantInRange, doSingularityEscape, fullyConstrainedNormalContactVector,
            forceFootAccelerateIntoGround, doFancyOnToesControl, legSingularityAndKneeCollapseAvoidanceControlModule,
            robotSide, registry));
      
      holdPositionState = new HoldPositionState(yoDesiredPosition, yoDesiredLinearVelocity, yoDesiredLinearAcceleration,
            accelerationControlModule, momentumBasedController, contactableBody, requestHoldPosition,
            requestedState, jacobianId, nullspaceMultiplier, jacobianDeterminantInRange, doSingularityEscape,
            fullyConstrainedNormalContactVector, forceFootAccelerateIntoGround,
            legSingularityAndKneeCollapseAvoidanceControlModule,
            robotSide, registry);
      states.add(holdPositionState);
      
      swingState = new SwingState(swingTimeProvider,
            finalPositionProvider,
            finalOrientationProvider, trajectoryParametersProvider,
            yoDesiredPosition, yoDesiredLinearVelocity,
            yoDesiredLinearAcceleration, accelerationControlModule, momentumBasedController,
            contactableBody, requestedState, jacobianId, nullspaceMultiplier,
            jacobianDeterminantInRange, doSingularityEscape,
            forceFootAccelerateIntoGround, legSingularityAndKneeCollapseAvoidanceControlModule,
            robotSide, registry,
            isTrajectoryDone, isUnconstrained, dynamicGraphicObjectsListRegistry, walkingControllerParameters);
      states.add(swingState);
      
      moveStraightState = new MoveStraightState(swingTimeProvider,
            finalPositionProvider, finalOrientationProvider,
            yoDesiredPosition, yoDesiredLinearVelocity,
            yoDesiredLinearAcceleration, accelerationControlModule, momentumBasedController,
            contactableBody, requestedState, jacobianId, nullspaceMultiplier,
            jacobianDeterminantInRange, doSingularityEscape,
            forceFootAccelerateIntoGround, legSingularityAndKneeCollapseAvoidanceControlModule,
            robotSide, registry,
            isTrajectoryDone, isUnconstrained);
      states.add(moveStraightState);

      setUpStateMachine(states);
   }

   public void setMaxAccelerationAndJerk(double maxPositionAcceleration, double maxPositionJerk, 
         double maxOrientationAcceleration, double maxOrientationJerk)
   {
      accelerationControlModule.setPositionMaxAccelerationAndJerk(maxPositionAcceleration, maxPositionJerk);
      accelerationControlModule.setOrientationMaxAccelerationAndJerk(maxOrientationAcceleration, maxOrientationJerk);
      
   }
   
   public void setSwingGains(double swingKpXY, double swingKpZ, double swingKpOrientation, double swingZetaXYZ, double swingZetaOrientation)
   {
      swingState.setSwingGains(swingKpXY, swingKpZ, swingKpOrientation, swingZetaXYZ, swingZetaOrientation);
      moveStraightState.setSwingGains(swingKpXY, swingKpZ, swingKpOrientation, swingZetaXYZ, swingZetaOrientation);
   }
   
   public void setHoldGains(double holdKpXY, double holdKpOrientation, double holdZeta)
   {
      double holdKpz = 0.0;
      double holdKdz = GainCalculator.computeDerivativeGain(holdKpz, holdZeta);
      holdPositionState.setHoldGains(holdZeta, holdKpXY, holdKpXY, holdKpz, holdKdz,
            holdKpOrientation, holdKpOrientation, holdKpOrientation);
   }

   public void setToeOffGains(double toeOffKpXY, double toeOffKpOrientation, double toeOffZeta)
   {
      double toeOffKpz = 0.0;
      touchdownOnToesState.setToeOffGains(toeOffZeta, toeOffKpXY, toeOffKpXY, toeOffKpz,toeOffKpOrientation, toeOffKpOrientation, toeOffKpOrientation);
      touchdwonOnHeelState.setToeOffGains(toeOffZeta, toeOffKpXY, toeOffKpXY, toeOffKpz, toeOffKpOrientation, toeOffKpOrientation, toeOffKpOrientation);
      onToesState.setToeOffGains(toeOffZeta, toeOffKpXY, toeOffKpXY, toeOffKpz, toeOffKpOrientation, toeOffKpOrientation, toeOffKpOrientation);
   }
   
   private void setupContactStatesMap()
   {
      boolean[] falses = new boolean[contactableBody.getTotalNumberOfContactPoints()];
      Arrays.fill(falses, false);
      boolean[] trues = new boolean[contactableBody.getTotalNumberOfContactPoints()];
      Arrays.fill(trues, true);

      contactStatesMap.put(ConstraintType.SWING, falses);
      contactStatesMap.put(ConstraintType.MOVE_STRAIGHT, falses);
      contactStatesMap.put(ConstraintType.FULL, trues);
      contactStatesMap.put(ConstraintType.HOLD_POSITION, trues);
      contactStatesMap.put(ConstraintType.HEEL_TOUCHDOWN, getOnEdgeContactPointStates(contactableBody, ConstraintType.HEEL_TOUCHDOWN));
      contactStatesMap.put(ConstraintType.TOES, trues);
      contactStatesMap.put(ConstraintType.TOES_TOUCHDOWN, contactStatesMap.get(ConstraintType.TOES));
   }

   private void setUpStateMachine(List<AbstractFootControlState> states)
   {
      for (AbstractFootControlState state : states)
      {
         for (AbstractFootControlState stateToTransitionTo : states)
         {
            FootStateTransitionCondition footStateTransitionCondition = new FootStateTransitionCondition(stateToTransitionTo,
                  jacobian, requestedState, doSingularityEscape, jacobianDeterminantInRange, waitSingularityEscapeBeforeTransitionToNextState);
            state.addStateTransition(new StateTransition<ConstraintType>(stateToTransitionTo.getStateEnum(), footStateTransitionCondition,
                  new FootStateTransitionAction(requestedState, doSingularityEscape, waitSingularityEscapeBeforeTransitionToNextState)));
         }
      }

      for (State<ConstraintType> state : states)
      {
         stateMachine.addState(state);
      }

      stateMachine.setCurrentState(ConstraintType.FULL);
   }
   
   public void replanTrajectory(double swingTimeRemaining)
   {
      swingState.replanTrajectory(swingTimeRemaining);
   }

   public void doSingularityEscape(boolean doSingularityEscape)
   {
      this.doSingularityEscape.set(doSingularityEscape);
      this.nullspaceMultiplier.set(singularityEscapeNullspaceMultiplier.getDoubleValue());
   }

   public void doSingularityEscape(double temporarySingularityEscapeNullspaceMultiplier)
   {
      doSingularityEscape.set(true);
      this.nullspaceMultiplier.set(temporarySingularityEscapeNullspaceMultiplier);
   }

   public void doSingularityEscapeBeforeTransitionToNextState()
   {
      doSingularityEscape(true);
      waitSingularityEscapeBeforeTransitionToNextState.set(true);
   }

   public double getJacobianDeterminant()
   {
      return jacobianDeterminant.getDoubleValue();
   }

   public boolean isInSingularityNeighborhood()
   {
      return jacobianDeterminantInRange.getBooleanValue();
   }

   public void forceConstrainedFootToAccelerateIntoGround(boolean accelerateIntoGround)
   {
      forceFootAccelerateIntoGround.set(accelerateIntoGround);
   }

   public void setParameters(double singularityEscapeNullspaceMultiplier)
   {
      this.singularityEscapeNullspaceMultiplier.set(singularityEscapeNullspaceMultiplier);
   }

   public void setContactState(ConstraintType constraintType)
   {
      setContactState(constraintType, null);
   }

   public void setContactState(ConstraintType constraintType, FrameVector normalContactVector)
   {
      if (constraintType == ConstraintType.HOLD_POSITION || constraintType == ConstraintType.FULL)
      {
         if (constraintType == ConstraintType.HOLD_POSITION)
            System.out.println("Warning: HOLD_POSITION state is handled internally.");
         
         if (requestHoldPosition != null && requestHoldPosition.getBooleanValue())
            constraintType = ConstraintType.HOLD_POSITION;
         else
            constraintType = ConstraintType.FULL;
         
         if (normalContactVector != null)
            fullyConstrainedNormalContactVector.setIncludingFrame(normalContactVector);
         else
            fullyConstrainedNormalContactVector.setIncludingFrame(contactableBody.getPlaneFrame(), 0.0, 0.0, 1.0);
      }
      
      momentumBasedController.setPlaneContactState(contactableBody, contactStatesMap.get(constraintType), normalContactVector);

      if (getCurrentConstraintType() == constraintType) // Use resetCurrentState() for such case
         return;
      
      requestedState.set(constraintType);
   }

   public ConstraintType getCurrentConstraintType()
   {
      return stateMachine.getCurrentStateEnum();
   }

   public ReferenceFrame getFootFrame()
   {
      return contactableBody.getBodyFrame();
   }

   public void doControl()
   {
      jacobianDeterminant.set(jacobian.det());
      
      stateMachine.checkTransitionConditions();
      stateMachine.doAction();
   }

   public boolean isTrajectoryDone()
   {
      return isTrajectoryDone.getBooleanValue();
   }

   public void resetTrajectoryDone()
   {
      isTrajectoryDone.set(false);
   }

   // Used to restart the current state reseting the current state time
   public void resetCurrentState()
   {
      stateMachine.setCurrentState(getCurrentConstraintType());
   }

   public boolean isInFlatSupportState()
   {
      return getCurrentConstraintType() == ConstraintType.FULL || getCurrentConstraintType() == ConstraintType.HOLD_POSITION;
   }
   
   public boolean isInEdgeTouchdownState()
   {
      return getCurrentConstraintType() == ConstraintType.HEEL_TOUCHDOWN || getCurrentConstraintType() == ConstraintType.TOES_TOUCHDOWN;
   }

   private boolean[] getOnEdgeContactPointStates(ContactablePlaneBody contactableBody, ConstraintType constraintType)
   {
      FrameVector direction = new FrameVector(contactableBody.getBodyFrame(), 1.0, 0.0, 0.0);
      if (constraintType == ConstraintType.HEEL_TOUCHDOWN)
         direction.scale(-1.0);

      int[] indexOfPointsInContact = DesiredFootstepCalculatorTools.findMaximumPointIndexesInDirection(contactableBody.getContactPointsCopy(), direction, 2);

      boolean[] contactPointStates = new boolean[contactableBody.getTotalNumberOfContactPoints()];

      for (int i = 0; i < indexOfPointsInContact.length; i++)
      {
         contactPointStates[indexOfPointsInContact[i]] = true;
      }

      return contactPointStates;
   }

   public double updateAndGetLegLength()
   {
      return legSingularityAndKneeCollapseAvoidanceControlModule.updateAndGetLegLength();
   }

   public void correctCoMHeightTrajectoryForSingularityAvoidance(FrameVector2d comXYVelocity, CoMHeightTimeDerivativesData comHeightDataToCorrect, double zCurrent,
         ReferenceFrame pelvisZUpFrame)
   {
      legSingularityAndKneeCollapseAvoidanceControlModule.correctCoMHeightTrajectoryForSingularityAvoidance(comXYVelocity, comHeightDataToCorrect, zCurrent, pelvisZUpFrame, getCurrentConstraintType());
   }

   public void correctCoMHeightTrajectoryForCollapseAvoidance(FrameVector2d comXYVelocity, CoMHeightTimeDerivativesData comHeightDataToCorrect, double zCurrent,
         ReferenceFrame pelvisZUpFrame, double footLoadPercentage)
   {
      legSingularityAndKneeCollapseAvoidanceControlModule.correctCoMHeightTrajectoryForCollapseAvoidance(comXYVelocity, comHeightDataToCorrect, zCurrent, pelvisZUpFrame, footLoadPercentage, getCurrentConstraintType());
   }
   
   public void correctCoMHeightTrajectoryForUnreachableFootStep(CoMHeightTimeDerivativesData comHeightDataToCorrect)
   {
      legSingularityAndKneeCollapseAvoidanceControlModule.correctCoMHeightTrajectoryForUnreachableFootStep(comHeightDataToCorrect, getCurrentConstraintType());
   }
}