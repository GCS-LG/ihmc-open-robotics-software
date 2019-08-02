package us.ihmc.robotEnvironmentAwareness.fusion.data;

import javafx.collections.ObservableList;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.MeshView;
import us.ihmc.commons.thread.ThreadTools;
import us.ihmc.euclid.tuple3D.Point3D;
import us.ihmc.euclid.tuple3D.interfaces.Point3DReadOnly;
import us.ihmc.euclid.tuple3D.interfaces.Vector3DReadOnly;
import us.ihmc.graphicsDescription.MeshDataGenerator;
import us.ihmc.javaFXToolkit.messager.SharedMemoryJavaFXMessager;
import us.ihmc.javaFXToolkit.shapes.JavaFXMultiColorMeshBuilder;
import us.ihmc.javaFXToolkit.shapes.TextureColorAdaptivePalette;
import us.ihmc.log.LogTools;
import us.ihmc.robotEnvironmentAwareness.communication.LidarImageFusionAPI;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

public class FusedSuperPixelImageViewer
{
   protected final JavaFXMultiColorMeshBuilder meshBuilder;

   private final ExecutorService executorService = Executors.newSingleThreadExecutor(ThreadTools.getNamedThreadFactory(getClass().getSimpleName()));


   private final AtomicReference<MeshView> meshToRender = new AtomicReference<>(null);
   private final Group root = new Group();
   protected final ObservableList<Node> children = root.getChildren();
   private final AtomicReference<Boolean> showSolution = new AtomicReference<>(false);
   private final AtomicReference<Boolean> clearSolution = new AtomicReference<>(false);

   private static final double lineWidth = 0.01;

   public FusedSuperPixelImageViewer(SharedMemoryJavaFXMessager messager)
   {
      meshBuilder = new JavaFXMultiColorMeshBuilder(new TextureColorAdaptivePalette(2048));

      messager.registerTopicListener(LidarImageFusionAPI.FusedSuperPixelData, superPixelImage -> executorService.submit(() -> unpackFusionData(superPixelImage)));
      messager.registerTopicListener(LidarImageFusionAPI.ShowFusedSuperPixelData, this::handleShowSolution);
   }

   private void handleShowSolution(boolean show)
   {
      showSolution.set(show);
      if (!show)
         clearSolution.set(true);
   }

   private synchronized void unpackFusionData(List<FusedSuperPixelData> fusedSuperPixels)
   {
      clear();
      meshBuilder.clear();

      if (fusedSuperPixels == null)
         return;

      fusedSuperPixels.forEach(rawSuperPixelData -> addSuperPixelToMeshBuilder(meshBuilder, rawSuperPixelData));

      MeshView scanMeshView = new MeshView(meshBuilder.generateMesh());
      scanMeshView.setMaterial(meshBuilder.generateMaterial());
      meshToRender.set(scanMeshView);
      meshBuilder.clear();

      LogTools.info("unpacked fused data");
   }

   public void render()
   {
      MeshView newScanMeshView = meshToRender.get();

      if (clearSolution.getAndSet(false))
         children.clear();

      if (newScanMeshView != null && showSolution.get())
      {
         if (children.isEmpty())
         {
            LogTools.info("rendering fused data.");
            children.add(newScanMeshView);
         }
      }
   }

   public void clear()
   {
      meshToRender.set(null);
      clearSolution.set(true);
   }

   public Node getRoot()
   {
      return root;
   }

   private static void addSuperPixelToMeshBuilder(JavaFXMultiColorMeshBuilder meshBuilder, FusedSuperPixelData rawSuperPixelData)
   {
      // todo this needs to be faster
      Color regionColor = getRegionColor();
      Point3DReadOnly center = rawSuperPixelData.getCenter();
      Vector3DReadOnly normal = rawSuperPixelData.getNormal();

      Point3D centerEnd = new Point3D(normal);
      centerEnd.scaleAdd(0.1, rawSuperPixelData.getCenter());

      meshBuilder.addLine(center, centerEnd, lineWidth, regionColor);
      rawSuperPixelData.getPointsInPixel().forEach(point -> meshBuilder.addMesh(MeshDataGenerator.Tetrahedron(0.02), point, regionColor));
   }

   private static Color getRegionColor()
   {
      java.awt.Color awtColor = new java.awt.Color(new Random().nextInt());
      return Color.rgb(awtColor.getRed(), awtColor.getGreen(), awtColor.getBlue());
   }
}
