# This message is part of the IHMC footstep planning module.
# This message is sent from the planner in order to visualize and debug planner progress

# Node rejection reasons. See BipedalFootstepPlannerNodeRejectionReason
int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_STEP_TOO_HIGH_OR_LOW=0

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_STEP_TOO_LOW_AND_FORWARD_WHEN_PITCHED=1

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_STEP_TOO_FORWARD_AND_DOWN=2

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_STEP_TOO_WIDE_AND_DOWN=3

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_STEP_TOO_FAR=4

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_STEP_TOO_FAR_AND_HIGH=5

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_STEP_TOO_WIDE_AND_HIGH=6

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_STEP_NOT_WIDE_ENOUGH=7

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_STEP_IN_PLACE=8

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_NOT_ENOUGH_AREA=9

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_COULD_NOT_SNAP=10

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_SURFACE_NORMAL_TOO_STEEP_TO_SNAP=11

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_TOO_MUCH_PENETRATION_AFTER_WIGGLE=12

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_STEP_NOT_LONG_ENOUGH=13

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_STEP_TOO_WIDE=14

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_OBSTACLE_BLOCKING_BODY=15

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_OBSTACLE_HITTING_BODY=16

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_AT_CLIFF_BOTTOM=17

int8 BIPEDAL_FOOTSTEP_PLANNER_NODE_REJECTION_REASON_STEP_YAWS_TOO_MUCH=18

# Specifies snap transform translation. NaN if snap was unsuccessful
geometry_msgs/Point position

# Specifies the snap rotation. NaN if snap unsuccessful.
geometry_msgs/Quaternion orientation

# ID of parent node. This should reference the index of this node in a FootstepNodeDataList
# Field default value -1
int32 parent_node_id

controller_msgs/FootstepPlannerLatticeNodeMessage footstep_node

# Node rejection reason. 255 if node was accepted
# Field default value 255
int8 bipedal_footstep_planner_node_rejection_reason


