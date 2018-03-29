package controller_msgs.msg.dds;

import us.ihmc.communication.packets.Packet;
import us.ihmc.euclid.interfaces.Settable;
import us.ihmc.euclid.interfaces.EpsilonComparable;

public class FrameInformation extends Packet<FrameInformation> implements Settable<FrameInformation>, EpsilonComparable<FrameInformation>
{
   /**
    * This message is part of the IHMC whole-body controller API. This message carries the frame
    * related information needed for some messages such as taskspace trajectories. Valid codes and
    * their associated frames include:"
    */
   public static final long WORLD_FRAME = 83766130;
   public static final long MIDFEET_ZUP_FRAME = -100;
   public static final long PELVIS_ZUP_FRAME = -101;
   public static final long PELVIS_FRAME = -102;
   public static final long CHEST_FRAME = -103;
   public static final long CENTER_OF_MASS_FRAME = -104;
   public static final long LEFT_SOLE_FRAME = -105;
   public static final long RIGHT_SOLE_FRAME = -106;
   /**
    * As of March 2018, the header for this message is only use for its sequence ID.
    */
   public std_msgs.msg.dds.Header header_;
   /**
    * The ID of the reference frame that a trajectory is executed in.
    */
   public long trajectory_reference_frame_id_;
   /**
    * The ID of the reference frame that trajectory data in a packet is expressed in. The frame of
    * the trajectory data will be switched to the trajectory frame immediately when the message is
    * received by the controller. If set to the value 0 it will be assumed that this is the same
    * frame as the trajectory frame. It is recommended that this should be the same frame as the
    * trajectory_reference_frame_id to avoid unexpected behavior. When different, the controller
    * will change the data to be expressed in the trajectory frame at reception of the message. The
    * data frame is only useful if the user is unable to change the frame the data is expressed in
    * to the trajectory frame. However, unexpected behavior might occur if the data frame is moving
    * with respect to the trajectory frame during execution. To highlight this consider the
    * following example: A hand trajectory needs to be executed while the robot walks to a location
    * in world. The hand trajectory might be known in world frame but for safety the trajectory
    * execution frame is set to a frame attached to the robot. If the data is packed in world and
    * the data frame is set to world this will cause the resulting trajectory to be wrong since the
    * transformation to trajectory frame happens at the start of execution rather than every
    * controller tick.
    */
   public long data_reference_frame_id_;

   public FrameInformation()
   {
      header_ = new std_msgs.msg.dds.Header();
   }

   public FrameInformation(FrameInformation other)
   {
      this();
      set(other);
   }

   public void set(FrameInformation other)
   {
      std_msgs.msg.dds.HeaderPubSubType.staticCopy(other.header_, header_);
      trajectory_reference_frame_id_ = other.trajectory_reference_frame_id_;

      data_reference_frame_id_ = other.data_reference_frame_id_;

   }

   /**
    * As of March 2018, the header for this message is only use for its sequence ID.
    */
   public std_msgs.msg.dds.Header getHeader()
   {
      return header_;
   }

   /**
    * The ID of the reference frame that a trajectory is executed in.
    */
   public void setTrajectoryReferenceFrameId(long trajectory_reference_frame_id)
   {
      trajectory_reference_frame_id_ = trajectory_reference_frame_id;
   }

   /**
    * The ID of the reference frame that a trajectory is executed in.
    */
   public long getTrajectoryReferenceFrameId()
   {
      return trajectory_reference_frame_id_;
   }

   /**
    * The ID of the reference frame that trajectory data in a packet is expressed in. The frame of
    * the trajectory data will be switched to the trajectory frame immediately when the message is
    * received by the controller. If set to the value 0 it will be assumed that this is the same
    * frame as the trajectory frame. It is recommended that this should be the same frame as the
    * trajectory_reference_frame_id to avoid unexpected behavior. When different, the controller
    * will change the data to be expressed in the trajectory frame at reception of the message. The
    * data frame is only useful if the user is unable to change the frame the data is expressed in
    * to the trajectory frame. However, unexpected behavior might occur if the data frame is moving
    * with respect to the trajectory frame during execution. To highlight this consider the
    * following example: A hand trajectory needs to be executed while the robot walks to a location
    * in world. The hand trajectory might be known in world frame but for safety the trajectory
    * execution frame is set to a frame attached to the robot. If the data is packed in world and
    * the data frame is set to world this will cause the resulting trajectory to be wrong since the
    * transformation to trajectory frame happens at the start of execution rather than every
    * controller tick.
    */
   public void setDataReferenceFrameId(long data_reference_frame_id)
   {
      data_reference_frame_id_ = data_reference_frame_id;
   }

   /**
    * The ID of the reference frame that trajectory data in a packet is expressed in. The frame of
    * the trajectory data will be switched to the trajectory frame immediately when the message is
    * received by the controller. If set to the value 0 it will be assumed that this is the same
    * frame as the trajectory frame. It is recommended that this should be the same frame as the
    * trajectory_reference_frame_id to avoid unexpected behavior. When different, the controller
    * will change the data to be expressed in the trajectory frame at reception of the message. The
    * data frame is only useful if the user is unable to change the frame the data is expressed in
    * to the trajectory frame. However, unexpected behavior might occur if the data frame is moving
    * with respect to the trajectory frame during execution. To highlight this consider the
    * following example: A hand trajectory needs to be executed while the robot walks to a location
    * in world. The hand trajectory might be known in world frame but for safety the trajectory
    * execution frame is set to a frame attached to the robot. If the data is packed in world and
    * the data frame is set to world this will cause the resulting trajectory to be wrong since the
    * transformation to trajectory frame happens at the start of execution rather than every
    * controller tick.
    */
   public long getDataReferenceFrameId()
   {
      return data_reference_frame_id_;
   }

   @Override
   public boolean epsilonEquals(FrameInformation other, double epsilon)
   {
      if (other == null)
         return false;
      if (other == this)
         return true;

      if (!this.header_.epsilonEquals(other.header_, epsilon))
         return false;
      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.trajectory_reference_frame_id_, other.trajectory_reference_frame_id_, epsilon))
         return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.data_reference_frame_id_, other.data_reference_frame_id_, epsilon))
         return false;

      return true;
   }

   @Override
   public boolean equals(Object other)
   {
      if (other == null)
         return false;
      if (other == this)
         return true;
      if (!(other instanceof FrameInformation))
         return false;

      FrameInformation otherMyClass = (FrameInformation) other;

      if (!this.header_.equals(otherMyClass.header_))
         return false;
      if (this.trajectory_reference_frame_id_ != otherMyClass.trajectory_reference_frame_id_)
         return false;

      if (this.data_reference_frame_id_ != otherMyClass.data_reference_frame_id_)
         return false;

      return true;
   }

   @Override
   public java.lang.String toString()
   {
      StringBuilder builder = new StringBuilder();

      builder.append("FrameInformation {");
      builder.append("header=");
      builder.append(this.header_);
      builder.append(", ");
      builder.append("trajectory_reference_frame_id=");
      builder.append(this.trajectory_reference_frame_id_);
      builder.append(", ");
      builder.append("data_reference_frame_id=");
      builder.append(this.data_reference_frame_id_);
      builder.append("}");
      return builder.toString();
   }
}
