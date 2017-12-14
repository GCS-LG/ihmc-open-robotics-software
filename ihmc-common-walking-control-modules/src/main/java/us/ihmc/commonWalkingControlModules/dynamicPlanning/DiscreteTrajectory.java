package us.ihmc.commonWalkingControlModules.dynamicPlanning;

import org.ejml.data.DenseMatrix64F;
import org.ejml.ops.CommonOps;
import us.ihmc.commons.MathTools;
import us.ihmc.commons.PrintTools;
import us.ihmc.robotics.lists.RecyclingArrayList;

public class DiscreteTrajectory extends RecyclingArrayList<DenseMatrix64F>
{
   private double startTime = Double.NaN;
   private double endTime = Double.NaN;
   private double deltaT = Double.NaN;
   private double realDeltaT = Double.NaN;
   private int numberOfTimeSteps = 0;

   public DiscreteTrajectory(int dimensionality)
   {
      this(dimensionality, 1);
   }

   public DiscreteTrajectory(int xDimension, int yDimension)
   {
      super(1000, new VariableVectorBuilder(xDimension, yDimension));
      this.clear();
   }

   public void setTrajectorySize(double startTime, double endTime, double deltaT)
   {
      this.deltaT = deltaT;
      computeRequiredDeltaT((endTime - startTime), deltaT);

      this.clear();
      for (int i = 0; i < numberOfTimeSteps; i++)
         this.add().zero();
   }

   public void compute(double time, DenseMatrix64F valueToPack)
   {
      if (!MathTools.intervalContains(time, startTime, endTime))
      {
         PrintTools.warn("Requested computation time is outside the bounds of the trajectory. We do not extrapolate.");
         time = MathTools.clamp(time, startTime, endTime);
      }

      int startIndex = (int) Math.floor((time - startTime)/ realDeltaT);
      double startTime = startIndex * realDeltaT;
      double alpha = (time - startTime) / realDeltaT;

      CommonOps.scale((1.0 - alpha), get(startIndex), valueToPack);
      CommonOps.addEquals(valueToPack, alpha, get(startIndex + 1));
   }

   private void computeRequiredDeltaT(double trajectoryDuration, double deltaT)
   {
      numberOfTimeSteps = (int) Math.floor(trajectoryDuration / deltaT);
      realDeltaT = trajectoryDuration / numberOfTimeSteps;
   }
}
