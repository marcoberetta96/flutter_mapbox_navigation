package com.eopeter.fluttermapboxnavigation.models

enum class MapBoxEvents(val value: String) {
    MAP_READY("map_ready"),
    ROUTE_BUILDING("route_building"),
    ROUTE_BUILT("route_built"),
    ROUTE_BUILD_FAILED("route_build_failed"),
    ROUTE_BUILD_CANCELLED("route_build_cancelled"),
    ROUTE_BUILD_NO_ROUTES_FOUND("route_build_no_routes_found"),
    PROGRESS_CHANGE("progress_change"),
    USER_OFF_ROUTE("user_off_route"),
    MILESTONE_EVENT("milestone_event"),
    NAVIGATION_RUNNING("navigation_running"),
    NAVIGATION_CANCELLED("navigation_cancelled"),
    NAVIGATION_FINISHED("navigation_finished"),
    NAVIGATION_STOPPED("navigation_stopped"),
    FASTER_ROUTE_FOUND("faster_route_found"),
    SPEECH_ANNOUNCEMENT("speech_announcement"),
    BANNER_INSTRUCTION("banner_instruction"),
    ON_ARRIVAL("on_arrival"),
    WAY_POINT_ON_ARRIVAL("way_point_on_arrival"),
    FAILED_TO_REROUTE("failed_to_reroute"),
    REROUTE_ALONG("reroute_along"),
    ON_MAP_TAP("on_map_tap"),
    ANNOTATION_TAPPED("annotation_tapped"),
    MAP_POSITION_CHANGED("map_position_changed"),
    NEW_NEAR_POINT("new_near_point"),
    OLD_NEAR_POINT("old_near_point")
}
