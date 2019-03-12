package us.ihmc.quadrupedFootstepPlanning.footstepPlanning.graphSearch.heuristics;

import us.ihmc.quadrupedFootstepPlanning.footstepPlanning.graphSearch.graph.FootstepNode;
import us.ihmc.quadrupedFootstepPlanning.footstepPlanning.graphSearch.parameters.FootstepPlannerParameters;
import us.ihmc.quadrupedPlanning.QuadrupedXGaitSettingsReadOnly;
import us.ihmc.robotics.geometry.AngleTools;

public class DistanceAndYawBasedHeuristics extends CostToGoHeuristics
{
   private final QuadrupedXGaitSettingsReadOnly xGaitSettings;

   public DistanceAndYawBasedHeuristics(FootstepPlannerParameters parameters, QuadrupedXGaitSettingsReadOnly xGaitSettings)
   {
      super(parameters);

      this.xGaitSettings = xGaitSettings;
   }

   @Override
   protected double computeHeuristics(FootstepNode node, FootstepNode goalNode)
   {
      double yaw = AngleTools.computeAngleDifferenceMinusPiToPi(node.getNominalYaw(), goalNode.getNominalYaw());
      double bodyDistance = node.getOrComputeXGaitCenterPoint().distance(goalNode.getOrComputeXGaitCenterPoint());
      double minSteps = 2.0 * bodyDistance / parameters.getMaximumStepReach();
      double distanceWeight = 0.5 * (parameters.getForwardWeight() + parameters.getLateralWeight());

      double bodyBasedHeuristicDistance = node.euclideanDistance(goalNode);

      double yawHeuristicCost = parameters.getYawWeight() * Math.abs(yaw);
      double stepHeuristicCost = parameters.getCostPerStep() * minSteps;
      double distanceCost = distanceWeight * bodyBasedHeuristicDistance;

      return distanceCost + yawHeuristicCost + stepHeuristicCost;
   }
}
