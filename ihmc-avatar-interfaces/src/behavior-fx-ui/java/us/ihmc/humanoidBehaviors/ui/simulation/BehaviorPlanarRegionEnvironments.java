package us.ihmc.humanoidBehaviors.ui.simulation;

import us.ihmc.euclid.Axis;
import us.ihmc.pathPlanning.DataSetIOTools;
import us.ihmc.pathPlanning.PlannerTestEnvironments;
import us.ihmc.robotEnvironmentAwareness.planarRegion.slam.PlanarRegionSLAM;
import us.ihmc.robotEnvironmentAwareness.planarRegion.slam.PlanarRegionSLAMParameters;
import us.ihmc.robotics.PlanarRegionFileTools;
import us.ihmc.robotics.geometry.PlanarRegionsList;
import us.ihmc.robotics.geometry.PlanarRegionsListGenerator;
import us.ihmc.simulationConstructionSetTools.util.planarRegions.PlanarRegionsListExamples;

import java.util.Random;

public class BehaviorPlanarRegionEnvironments extends PlannerTestEnvironments
{
   public static final double CINDER_SLOPE_ANGLE = 13.0;
   public static final double Z_STEP_UP_PER_ROW = 0.10;
   private static double cinderSquareSurfaceSize = 0.395;
   private static double cinderThickness = 0.145;
   private static double topRegionHeight = 5 * Z_STEP_UP_PER_ROW - cinderThickness;
   private static double superGridSize = cinderSquareSurfaceSize * 3;
   private static double groundSize = 20.0;
   private static int greenId = 6;

   public static PlanarRegionsList createTraversalRegionsRegions()
   {
      PlanarRegionsListGenerator generator = new PlanarRegionsListGenerator();
      PlanarRegionsListExamples.generateCinderBlockField(generator,
                                                         0.4,
                                                         0.1,
                                                         5,
                                                         6,
                                                         0.02,
                                                         -0.03,
                                                         1.5,
                                                         0.0,
                                                         Math.toRadians(CINDER_SLOPE_ANGLE),
                                                         Math.toRadians(CINDER_SLOPE_ANGLE),
                                                         0.05,
                                                         false);
      return generator.getPlanarRegionsList();
   }

   public static PlanarRegionsList createStairs()
   {
      PlanarRegionsListGenerator generator = new PlanarRegionsListGenerator();
      generator.setId(greenId);
      generator.addRectangle(0.75, 0.75);
      generator.translate(0.75, 0.0, -0.25);
      generator.addRectangle(0.75, 0.75);
      generator.translate(0.75, 0.0, -0.25);
      generator.addRectangle(0.75, 0.75);
      generator.translate(0.75, 0.0, -0.25);
      generator.addRectangle(0.75, 0.75);
      return generator.getPlanarRegionsList();
   }

   public static PlanarRegionsList createUpDownOpenHouseRegions()
   {
      Random random = new Random(8349829898174L);
      PlanarRegionsListGenerator generator = new PlanarRegionsListGenerator();
      generator.setId(greenId);
      generator.addRectangle(groundSize, groundSize); // ground TODO form around terrain with no overlap?

      generator.translate(1.0, -superGridSize / 2, 0.0);

      addTopFlatRegionOld(generator);
      addPlusFormationSlopes(random, generator);
      addXFormationSlopes(random, generator);
      return generator.getPlanarRegionsList();
   }

   public static PlanarRegionsList createRoughUpAndDownStairsWithFlatTop()
   {
      return generate((random, generator) -> {
         offsetGrid(generator, 1.0, -0.5, 0.0, () ->
         {
            offsetGrid(generator, 0.0, 0.0, 0.0, () ->
            {
               generateAngledCinderBlockStairs(random, generator);
            });
            offsetGrid(generator, 1.0, 0.0, topRegionHeight, () ->
            {
               addTopFlatRegion(generator);
            });
            offsetGrid(generator, 3.0, 1.0, 0.0, () ->
            {
               rotate(generator, Math.PI, () ->
               {
                  generateAngledCinderBlockStairs(random, generator);
               });
            });
         });
      });
   }

   private static PlanarRegionsList generate(GenerationInterface generationInterface)
   {
      Random random = new Random(8349829898174L);
      PlanarRegionsListGenerator generator = new PlanarRegionsListGenerator();
      generator.setId(greenId);
      generator.addRectangle(groundSize, groundSize); // ground TODO form around terrain with no overlap?
      generationInterface.generate(random, generator);
      return generator.getPlanarRegionsList();
   }

   interface GenerationInterface
   {
      void generate(Random random, PlanarRegionsListGenerator generator);
   }

   public static PlanarRegionsList createUpDownTwoHighWithFlatBetween()
   {
      Random random = new Random(8349829898174L);
      PlanarRegionsListGenerator generator = new PlanarRegionsListGenerator();
      generator.setId(greenId);
      generator.addRectangle(groundSize, groundSize); // ground TODO form around terrain with no overlap?

      generator.translate(1.0, -superGridSize / 2, 0.0);
      addTopFlatRegionOld(generator);
      generateAngledCinderBlockStairs(random, generator, cinderSquareSurfaceSize, cinderThickness);

      generator.rotate(Math.PI, Axis.Z);
      generator.translate(2.0, -1.2, 0.0);
      addTopFlatRegionOld(generator);
      generateAngledCinderBlockStairs(random, generator, cinderSquareSurfaceSize, cinderThickness);

      return generator.getPlanarRegionsList();
   }

   public static PlanarRegionsList createUpDownFourHighWithFlatCenter()
   {
      Random random = new Random(8349829898174L);
      PlanarRegionsListGenerator generator = new PlanarRegionsListGenerator();
      generator.setId(greenId);
      generator.addRectangle(groundSize, groundSize); // ground TODO form around terrain with no overlap?

      generator.rotate(Math.PI/4, Axis.Z);

      double xSize = 0.5;
      double ySize = xSize + 2.0;
      generator.translate(xSize * superGridSize, -ySize * superGridSize, 0.0);
      addHighCorner(random, generator);
      generator.translate(-xSize * superGridSize, ySize * superGridSize, 0.0);

      generator.rotate(Math.PI/2, Axis.Z);
      generator.translate(xSize * superGridSize, -ySize * superGridSize, 0.0);
      addHighCorner(random, generator);
      generator.translate(-xSize * superGridSize, ySize * superGridSize, 0.0);

      generator.rotate(Math.PI/2, Axis.Z);
      generator.translate(xSize * superGridSize, -ySize * superGridSize, 0.0);
      addHighCorner(random, generator);
      generator.translate(-xSize * superGridSize, ySize * superGridSize, 0.0);

      generator.rotate(Math.PI/2, Axis.Z);
      generator.translate(xSize * superGridSize, -ySize * superGridSize, 0.0);
      addHighCorner(random, generator);
      generator.translate(-xSize * superGridSize, ySize * superGridSize, 0.0);

      return generator.getPlanarRegionsList();
   }

   private static void addHighCorner(Random random, PlanarRegionsListGenerator generator)
   {
      addTopFlatRegionOld(generator);

      generateAngledCinderBlockStairs(random, generator, cinderSquareSurfaceSize, cinderThickness);
      generator.translate(2*superGridSize, -superGridSize, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
      generator.translate(2*superGridSize, -superGridSize, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
      generator.translate(2*superGridSize, -superGridSize, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
      generateAngledCinderBlockStairs(random, generator, cinderSquareSurfaceSize, cinderThickness);
      generator.translate(2*superGridSize, -superGridSize, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);

      generator.translate(0.0, -superGridSize, 0.0);
      generator.translate(3*superGridSize, 0.0, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
      generator.translate(3*superGridSize, 0.0, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
      generator.translate(3*superGridSize, 0.0, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
      generateCorner(random, generator, cinderSquareSurfaceSize, cinderThickness);
      generator.translate(2*superGridSize, 0.0, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
   }

   private static void addXFormationSlopes(Random random, PlanarRegionsListGenerator generator)
   {
      generator.translate(0.0, -superGridSize, 0.0);
      generateCorner(random, generator, cinderSquareSurfaceSize, cinderThickness);
      generator.translate(3*superGridSize, 0.0, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
      generateCorner(random, generator, cinderSquareSurfaceSize, cinderThickness);
      generator.translate(3*superGridSize, 0.0, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
      generateCorner(random, generator, cinderSquareSurfaceSize, cinderThickness);
      generator.translate(3*superGridSize, 0.0, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
      generateCorner(random, generator, cinderSquareSurfaceSize, cinderThickness);
      generator.translate(2*superGridSize, 0.0, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
   }

   private static void addPlusFormationSlopes(Random random, PlanarRegionsListGenerator generator)
   {
      generateAngledCinderBlockStairs(random, generator, cinderSquareSurfaceSize, cinderThickness);
      generator.translate(2*superGridSize, -superGridSize, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
      generateAngledCinderBlockStairs(random, generator, cinderSquareSurfaceSize, cinderThickness);
      generator.translate(2*superGridSize, -superGridSize, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
      generateAngledCinderBlockStairs(random, generator, cinderSquareSurfaceSize, cinderThickness);
      generator.translate(2*superGridSize, -superGridSize, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
      generateAngledCinderBlockStairs(random, generator, cinderSquareSurfaceSize, cinderThickness);
      generator.translate(2*superGridSize, -superGridSize, 0.0);
      generator.rotate(Math.PI/2, Axis.Z);
   }

   private static void addTopFlatRegionOld(PlanarRegionsListGenerator generator)
   {
      offsetGrid(generator, superGridSize, 0.0, topRegionHeight, () -> {
         generator.addCubeReferencedAtBottomNegativeXYCorner(superGridSize, superGridSize, cinderThickness);
      });
   }

   private static void addTopFlatRegion(PlanarRegionsListGenerator generator)
   {
      generator.addCubeReferencedAtBottomNegativeXYCorner(superGridSize, superGridSize, cinderThickness);
   }

   private static void generateAngledCinderBlockStairs(Random random, PlanarRegionsListGenerator generator)
   {
      PlanarRegionsListExamples.generateCinderBlockSlope(generator,
                                                         random,
                                                         cinderSquareSurfaceSize,
                                                         cinderThickness,
                                                         3,
                                                         3,
                                                         Z_STEP_UP_PER_ROW,
                                                         0.0,
                                                         0.0,
                                                         Math.toRadians(CINDER_SLOPE_ANGLE),
                                                         0.0);
   }
   private static void generateAngledCinderBlockStairs(Random random, PlanarRegionsListGenerator generator, double cinderSquareSurfaceSize, double cinderThickness)
   {
      PlanarRegionsListExamples.generateCinderBlockSlope(generator,
                                                         random,
                                                         cinderSquareSurfaceSize,
                                                         cinderThickness,
                                                         3,
                                                         3,
                                                         Z_STEP_UP_PER_ROW,
                                                         0.0,
                                                         0.0,
                                                         Math.toRadians(CINDER_SLOPE_ANGLE),
                                                         0.0);
   }

   private static void generateCorner(Random random, PlanarRegionsListGenerator generator, double cinderSquareSurfaceSize, double cinderThickness)
   {
      PlanarRegionsListExamples.generateCinderBlockCornerSlope(generator,
                                                               random,
                                                               cinderSquareSurfaceSize,
                                                               cinderThickness,
                                                               3,
                                                               3,
                                                               Z_STEP_UP_PER_ROW,
                                                               0.0,
                                                               0.0,
                                                               Math.toRadians(0.0),
                                                               0.0);
   }

   private static void offsetGrid(PlanarRegionsListGenerator generator, double x, double y, Runnable runnable)
   {
      offsetGrid(generator, x, y, 0.0, runnable);
   }

   private static void offsetGrid(PlanarRegionsListGenerator generator, double x, double y, double z, Runnable runnable)
   {
      generator.translate(x * superGridSize, y * superGridSize, z * superGridSize);
      runnable.run();
      generator.translate(-x * superGridSize, -y * superGridSize, -z * superGridSize);
   }

   private static void rotate(PlanarRegionsListGenerator generator, double yaw, Runnable runnable)
   {
      generator.rotate(yaw, Axis.Z);
      runnable.run();
      generator.rotate(-yaw, Axis.Z);
   }

   public static PlanarRegionsList realDataFromAtlasSLAMDataset20190710()
   {
      PlanarRegionsList map = PlanarRegionsList.flatGround(10.0);
      PlanarRegionSLAMParameters parameters = new PlanarRegionSLAMParameters();
      map = PlanarRegionSLAM.slam(map, loadDataSet("20190710_174025_PlanarRegion"), parameters).getMergedMap();
      map = PlanarRegionSLAM.slam(map, loadDataSet("IntentionallyDrifted"), parameters).getMergedMap();
      map = PlanarRegionSLAM.slam(map, loadDataSet("20190710_174422_PlanarRegion"), parameters).getMergedMap();
      return map;
   }

   private static PlanarRegionsList loadDataSet(String dataSetName)
   {
      return PlanarRegionFileTools.importPlanarRegionData(ClassLoader.getSystemClassLoader(),
                                                          DataSetIOTools.DATA_SET_DIRECTORY_PATH + "/20190710_SLAM_PlanarRegionFittingExamples/" + dataSetName);
   }
}
