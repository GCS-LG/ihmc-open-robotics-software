package us.ihmc.robotics.kinematics;

import static us.ihmc.robotics.Assert.*;

import java.util.Random;

import org.junit.jupiter.api.Test;

import us.ihmc.commons.RandomNumbers;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Disabled;
import us.ihmc.euclid.tools.EuclidCoreRandomTools;
import us.ihmc.euclid.transform.RigidBodyTransform;

public class TimeStampedTransform3DTest
{

   private static final double EPSILON = 1.0e-15;

   @Test
   public void testEmptyConstructor()
   {
      TimeStampedTransform3D toBeTested = new TimeStampedTransform3D();

      RigidBodyTransform expectedTransform = new RigidBodyTransform();
      long expectedTimestamp = 0;

      assertEquals("Timestamp is different from what was expected", expectedTimestamp, toBeTested.getTimeStamp());
      assertTrue("Transform is different from what was expected", expectedTransform.epsilonEquals(toBeTested.getTransform3D(), EPSILON));
   }

   @Test
   public void testConstructor()
   {
      Random random = new Random(3213620L);
      RigidBodyTransform expectedTransform = EuclidCoreRandomTools.nextRigidBodyTransform(random);
      long expectedTimestamp = RandomNumbers.nextInt(random, 132, 51568418);
      

      TimeStampedTransform3D toBeTested = new TimeStampedTransform3D(expectedTransform, expectedTimestamp);

      assertEquals("Timestamp is different from what was expected", expectedTimestamp, toBeTested.getTimeStamp());
      assertTrue("Transform is different from what was expected", expectedTransform.epsilonEquals(toBeTested.getTransform3D(), EPSILON));

      assertTrue("TimestampedTransform should only copy the given transform into an internal variable", expectedTransform != toBeTested.getTransform3D());
   }

   @Test
   public void testSetters()
   {
      TimeStampedTransform3D toBeTested = new TimeStampedTransform3D();

      RigidBodyTransform expectedTransform = new RigidBodyTransform();
      long expectedTimestamp = 0;

      assertEquals("Timestamp is different from what was expected", expectedTimestamp, toBeTested.getTimeStamp());
      assertTrue("Transform is different from what was expected", expectedTransform.epsilonEquals(toBeTested.getTransform3D(), EPSILON));

      Random random = new Random(3213620L);
      expectedTimestamp = RandomNumbers.nextInt(random, 132, 51568418);
      toBeTested.setTimeStamp(expectedTimestamp);

      assertEquals("Timestamp is different from what was expected", expectedTimestamp, toBeTested.getTimeStamp());
      assertTrue("Transform is different from what was expected", expectedTransform.epsilonEquals(toBeTested.getTransform3D(), EPSILON));

      expectedTransform = EuclidCoreRandomTools.nextRigidBodyTransform(random);
      toBeTested.setTransform3D(expectedTransform);

      assertEquals("Timestamp is different from what was expected", expectedTimestamp, toBeTested.getTimeStamp());
      assertTrue("Transform is different from what was expected", expectedTransform.epsilonEquals(toBeTested.getTransform3D(), EPSILON));

      expectedTimestamp = RandomNumbers.nextInt(random, 132, 51568418);
      expectedTransform = EuclidCoreRandomTools.nextRigidBodyTransform(random);
      TimeStampedTransform3D expectedTimeStampedTransform = new TimeStampedTransform3D(expectedTransform, expectedTimestamp);

      toBeTested.set(expectedTimeStampedTransform);

      assertEquals("Timestamp is different from what was expected", expectedTimeStampedTransform.getTimeStamp(), toBeTested.getTimeStamp());
      assertTrue("Transform is different from what was expected", expectedTimeStampedTransform.getTransform3D().epsilonEquals(toBeTested.getTransform3D(), EPSILON));
   }

   @Test
   public void testGetTransform()
   {
      TimeStampedTransform3D toBeTested = new TimeStampedTransform3D();

      RigidBodyTransform expectedTransform = new RigidBodyTransform();
      // Test that the getter returns the original transform and not a copy.
      toBeTested.getTransform3D().set(expectedTransform);

      assertTrue("Transform is different from what was expected", expectedTransform.epsilonEquals(toBeTested.getTransform3D(), EPSILON));
   }
}
