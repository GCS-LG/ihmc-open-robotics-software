package us.ihmc.robotics.linearAlgebra.careSolvers;

import org.ejml.data.DenseMatrix64F;
import us.ihmc.commons.MathTools;

public class MatrixToolsLocal
{
   public static void setMatrixBlockToIdentity(DenseMatrix64F dest, int row, int col, int sizeToSet)
   {
      setMatrixBlockToConstant(dest, row, col, sizeToSet, 1.0);
   }

   public static void setMatrixBlockToConstant(DenseMatrix64F dest, int row, int col, int sizeToSet, double value)
   {
      for (int i = 0; i < sizeToSet; i++)
      {
         dest.set(row + i, col + i, value);
      }
   }

   /** Computes the distance between two matrices, which is defined as the L2 normSquared of their difference. */
   public static double distance(DenseMatrix64F A, DenseMatrix64F B)
   {
      MatrixChecking.assertRowDimensionsMatch(A, B);
      MatrixChecking.assertColDimensionsMatch(A, B);

      double norm = 0.0;
      for (int col = 0; col < A.getNumCols(); col++)
      {
         double rowSum = 0.0;
         for (int row = 0; row < A.getNumRows(); row++)
         {
            rowSum += MathTools.square(A.get(row, col) - B.get(row, col));
         }
         norm += MathTools.square(rowSum);
      }

      return norm;
   }

   public static double normSquared(DenseMatrix64F A)
   {
      double normSquared = 0.0;
      for (int col = 0; col < A.getNumCols(); col++)
      {
         double rowSum = 0.0;
         for (int row = 0; row < A.getNumRows(); row++)
         {
            rowSum += MathTools.square(A.get(row, col));
         }
         normSquared += MathTools.square(rowSum);
      }

      return normSquared;
   }

   public static double norm(DenseMatrix64F A)
   {
      return Math.sqrt(normSquared(A));
   }

   static boolean isZero(DenseMatrix64F P, double epsilon)
   {
      return normSquared(P) < epsilon;
   }
}
