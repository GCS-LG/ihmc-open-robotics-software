package us.ihmc.robotEnvironmentAwareness.fusion.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import gnu.trove.list.array.TIntArrayList;
import us.ihmc.robotEnvironmentAwareness.fusion.parameters.PlanarRegionPropagationParameters;
import us.ihmc.robotEnvironmentAwareness.fusion.parameters.SegmentationRawDataFilteringParameters;
import us.ihmc.robotEnvironmentAwareness.fusion.parameters.SuperPixelNormalEstimationParameters;
import us.ihmc.robotEnvironmentAwareness.fusion.tools.SegmentationRawDataFiltering;
import us.ihmc.robotEnvironmentAwareness.fusion.tools.SuperPixelTools;
import us.ihmc.robotEnvironmentAwareness.planarRegion.PlanarRegionSegmentationRawData;

public class StereoREAPlanarRegionSegmentationCalculator
{
   private final PlanarRegionPropagationParameters planarRegionPropagationParameters = new PlanarRegionPropagationParameters();
   private final SegmentationRawDataFilteringParameters segmentationRawDataFilteringParameters = new SegmentationRawDataFilteringParameters();
   private final SuperPixelNormalEstimationParameters normalEstimationParameters = new SuperPixelNormalEstimationParameters();

   private static final int NUMBER_OF_ITERATE = 1000;
   private static final int MAXIMUM_NUMBER_OF_TRIALS_TO_FIND_UN_ID_LABEL = 500;
   private static final int MINIMUM_NUMBER_OF_SEGMENTATION_RAW_DATA_FOR_PLANAR_REGION = 3;
   private static final int MINIMUM_NUMBER_OF_LABELS_FOR_BIG_SEGMENT = 7;

   private static final boolean resetSmallNodeData = true;

   private final AtomicReference<RawSuperPixelImage> latestSuperPixelImage = new AtomicReference<>(null);
   private final List<FusedSuperPixelData> fusedSuperPixels = new ArrayList<>();
   private List<PlanarRegionSegmentationRawData> regionsNodeData = new ArrayList<>();

   private final Random random = new Random(0612L);

   public void updateFusionData(RawSuperPixelImage rawSuperPixelImage, SegmentationRawDataFilteringParameters rawDataFilteringParameters,
                                PlanarRegionPropagationParameters propagationParameters, SuperPixelNormalEstimationParameters normalEstimationParameters)
   {
      SegmentationRawDataFiltering.updateSparsity(rawSuperPixelImage, rawDataFilteringParameters);
      latestSuperPixelImage.set(rawSuperPixelImage);
      planarRegionPropagationParameters.set(propagationParameters);
      segmentationRawDataFilteringParameters.set(rawDataFilteringParameters);
      this.normalEstimationParameters.set(normalEstimationParameters);
   }

   public void initialize()
   {
      fusedSuperPixels.clear();
      regionsNodeData.clear();
   }

   public List<PlanarRegionSegmentationRawData> getSegmentationRawData()
   {
      return regionsNodeData;
   }

   public boolean calculate()
   {
      RawSuperPixelImage rawSuperPixelImage = latestSuperPixelImage.get();
      for (int i = 0; i < NUMBER_OF_ITERATE; i++)
      {
         if (!iterateSegmentationPropagation(i, rawSuperPixelImage))
         {
            break;
         }
      }

      if (planarRegionPropagationParameters.isExtendingEnabled())
      {
         extendSuperPixelsToIncludeNewImage(rawSuperPixelImage);
      }

      convertSuperPixelsToPlanarRegionSegmentationRawData();
      return true;
   }

   private boolean iterateSegmentationPropagation(int segmentId, RawSuperPixelImage rawSuperPixelImage)
   {
      int nonIDLabel = selectRandomNonIdentifiedLabel(rawSuperPixelImage);

      if (nonIDLabel == RawSuperPixelData.DEFAULT_SEGMENT_ID)
      {
         return false;
      }
      else
      {
         FusedSuperPixelData segmentNodeData = createSegmentNodeData(nonIDLabel, segmentId, rawSuperPixelImage, planarRegionPropagationParameters);
         if (segmentNodeData != null)
            fusedSuperPixels.add(segmentNodeData);
      }

      return true;
   }

   private static int selectRandomNonIdentifiedLabel(RawSuperPixelImage rawSuperPixelImage)
   {
      int randomSeedLabel = RawSuperPixelData.DEFAULT_SEGMENT_ID;
      for (int i = 0; i < MAXIMUM_NUMBER_OF_TRIALS_TO_FIND_UN_ID_LABEL; i++)
      {
         randomSeedLabel = ThreadLocalRandom.current().nextInt(rawSuperPixelImage.getNumberOfImageSegments() - 1);
         RawSuperPixelData fusionDataSegment = rawSuperPixelImage.getSuperPixelData(randomSeedLabel);
         if (fusionDataSegment.getId() == RawSuperPixelData.DEFAULT_SEGMENT_ID && !fusionDataSegment.isSparse())
            return randomSeedLabel;
      }
      return -1;
   }

   /**
    * iterate computation until there is no more candidate to try merge.
    */
   private static FusedSuperPixelData createSegmentNodeData(int seedLabel, int segmentId, RawSuperPixelImage rawSuperPixelImage,
                                                            PlanarRegionPropagationParameters planarRegionPropagationParameters)
   {
      RawSuperPixelData seedSuperPixel = rawSuperPixelImage.getSuperPixelData(seedLabel);
      seedSuperPixel.setId(segmentId);
      FusedSuperPixelData newFusedSuperPixel = new FusedSuperPixelData(seedSuperPixel);

      boolean isPropagating = true;

      TIntArrayList labelsAlreadyInFusedSuperPixel = newFusedSuperPixel.getComponentPixelLabels();
      while (isPropagating)
      {
         isPropagating = false;
         boolean isBigSegment = labelsAlreadyInFusedSuperPixel.size() > MINIMUM_NUMBER_OF_LABELS_FOR_BIG_SEGMENT;

         Set<Integer> adjacentPixelLabels = rawSuperPixelImage.getLabelsOfSuperPixelsAdjacentToOtherSuperPixels(labelsAlreadyInFusedSuperPixel);

         for (int adjacentPixelLabel : adjacentPixelLabels)
         {
            RawSuperPixelData candidateAdjacentPixel = rawSuperPixelImage.getSuperPixelData(adjacentPixelLabel);

            if (candidateAdjacentPixel.getId() != RawSuperPixelData.DEFAULT_SEGMENT_ID || candidateAdjacentPixel.isSparse())
            {
               continue;
            }

            boolean isParallel = SuperPixelTools.areSuperPixelsParallel(newFusedSuperPixel, candidateAdjacentPixel, planarRegionPropagationParameters.getPlanarityThreshold());
            boolean isCoplanar = SuperPixelTools.areSuperPixelsCoplanar(newFusedSuperPixel, isBigSegment, candidateAdjacentPixel, planarRegionPropagationParameters.getProximityThreshold());

            if (isParallel && isCoplanar)
            {
               candidateAdjacentPixel.setId(segmentId);
               newFusedSuperPixel.merge(candidateAdjacentPixel);
               isPropagating = true;
            }
         }
      }

      if (resetSmallNodeData)
      {
         boolean isSmallNodeData = labelsAlreadyInFusedSuperPixel.size() < MINIMUM_NUMBER_OF_SEGMENTATION_RAW_DATA_FOR_PLANAR_REGION;

         if (isSmallNodeData)
         {
            for (int label : labelsAlreadyInFusedSuperPixel.toArray())
            {
               RawSuperPixelData rawData = rawSuperPixelImage.getSuperPixelData(label);
               rawData.setId(RawSuperPixelData.DEFAULT_SEGMENT_ID);
            }

            return null;
         }
      }

      return newFusedSuperPixel;
   }


   private void extendSuperPixelsToIncludeNewImage(RawSuperPixelImage rawSuperPixelImage)
   {
      for (FusedSuperPixelData segment : fusedSuperPixels)
      {
         Set<Integer> adjacentPixelLabels = rawSuperPixelImage.getLabelsOfSuperPixelsAdjacentToOtherSuperPixels(segment.getComponentPixelLabels());
         for (int adjacentLabel : adjacentPixelLabels)
         {
            RawSuperPixelData adjacentData = rawSuperPixelImage.getSuperPixelData(adjacentLabel);
            if (adjacentData.getId() == RawSuperPixelData.DEFAULT_SEGMENT_ID)
            {
               SuperPixelTools.extendFusedSuperPixel(segment, adjacentData, planarRegionPropagationParameters);
            }

         }
      }
   }

   /**
    * The id of the PlanarRegionSegmentationRawData is randomly selected to be visualized efficiently rather than selected by SegmentationNodeData.getId().
    */
   private void convertSuperPixelsToPlanarRegionSegmentationRawData()
   {
      for (FusedSuperPixelData fusedSuperPixelData : fusedSuperPixels)
      {
         if (fusedSuperPixelData.getComponentPixelLabels().size() < MINIMUM_NUMBER_OF_SEGMENTATION_RAW_DATA_FOR_PLANAR_REGION)
            continue;
         PlanarRegionSegmentationRawData planarRegionSegmentationRawData = new PlanarRegionSegmentationRawData(random.nextInt(),
                                                                                                               fusedSuperPixelData.getNormal(),
                                                                                                               fusedSuperPixelData.getCenter(),
                                                                                                               fusedSuperPixelData.getPointsInPixel());
         regionsNodeData.add(planarRegionSegmentationRawData);
      }
   }
}
