package controller_msgs.msg.dds;

import us.ihmc.communication.packets.Packet;
import us.ihmc.euclid.interfaces.Settable;
import us.ihmc.euclid.interfaces.EpsilonComparable;

/**
 * Message part of the localization module
 */
public class LocalizationPointMapPacket extends Packet<LocalizationPointMapPacket>
      implements Settable<LocalizationPointMapPacket>, EpsilonComparable<LocalizationPointMapPacket>
{
   /**
    * As of March 2018, the header for this message is only use for its sequence ID.
    */
   public std_msgs.msg.dds.Header header_;
   public long timestamp_;
   public us.ihmc.idl.IDLSequence.Float localization_point_map_;

   public LocalizationPointMapPacket()
   {
      header_ = new std_msgs.msg.dds.Header();
      localization_point_map_ = new us.ihmc.idl.IDLSequence.Float(100, "type_5");

   }

   public LocalizationPointMapPacket(LocalizationPointMapPacket other)
   {
      this();
      set(other);
   }

   public void set(LocalizationPointMapPacket other)
   {
      std_msgs.msg.dds.HeaderPubSubType.staticCopy(other.header_, header_);
      timestamp_ = other.timestamp_;

      localization_point_map_.set(other.localization_point_map_);
   }

   /**
    * As of March 2018, the header for this message is only use for its sequence ID.
    */
   public std_msgs.msg.dds.Header getHeader()
   {
      return header_;
   }

   public void setTimestamp(long timestamp)
   {
      timestamp_ = timestamp;
   }

   public long getTimestamp()
   {
      return timestamp_;
   }

   public us.ihmc.idl.IDLSequence.Float getLocalizationPointMap()
   {
      return localization_point_map_;
   }

   @Override
   public boolean epsilonEquals(LocalizationPointMapPacket other, double epsilon)
   {
      if (other == null)
         return false;
      if (other == this)
         return true;

      if (!this.header_.epsilonEquals(other.header_, epsilon))
         return false;
      if (!us.ihmc.idl.IDLTools.epsilonEqualsPrimitive(this.timestamp_, other.timestamp_, epsilon))
         return false;

      if (!us.ihmc.idl.IDLTools.epsilonEqualsFloatSequence(this.localization_point_map_, other.localization_point_map_, epsilon))
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
      if (!(other instanceof LocalizationPointMapPacket))
         return false;

      LocalizationPointMapPacket otherMyClass = (LocalizationPointMapPacket) other;

      if (!this.header_.equals(otherMyClass.header_))
         return false;
      if (this.timestamp_ != otherMyClass.timestamp_)
         return false;

      if (!this.localization_point_map_.equals(otherMyClass.localization_point_map_))
         return false;

      return true;
   }

   @Override
   public java.lang.String toString()
   {
      StringBuilder builder = new StringBuilder();

      builder.append("LocalizationPointMapPacket {");
      builder.append("header=");
      builder.append(this.header_);
      builder.append(", ");
      builder.append("timestamp=");
      builder.append(this.timestamp_);
      builder.append(", ");
      builder.append("localization_point_map=");
      builder.append(this.localization_point_map_);
      builder.append("}");
      return builder.toString();
   }
}
