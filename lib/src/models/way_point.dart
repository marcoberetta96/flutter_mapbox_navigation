///A `WayPoint` object indicates a location along a route.
///It may be the route’s origin or destination, or it may be another location
///that the route visits. A waypoint object indicates the location’s geographic
///location along with other optional information, such as a name or
///the user’s direction approaching the waypoint.
class WayPoint {
  ///Constructor
  WayPoint({
    required this.name,
    required this.text,
    required this.latitude,
    required this.longitude,
    this.isSilent = false,
    this.id,
  });

  /// create [WayPoint] from a json
  WayPoint.fromJson(Map<String, dynamic> json) {
    id = json['id'] as String?;
    name = json['name'] as String?;
    text = json['text'] as String?;
    latitude = (json['latitude'] is String)
        ? double.tryParse(json['latitude'] as String)
        : json['latitude'] as double?;
    longitude = (json['longitude'] is String)
        ? double.tryParse(json['longitude'] as String)
        : json['longitude'] as double?;

    if (json['isSilent'] == null) {
      isSilent = false;
    } else {
      isSilent = json['isSilent'] as bool;
    }
  }

  /// Waypoint [id]
  String? id;

  /// Waypoint [name]
  String? name;

  /// Waypoint [text]
  String? text;

  /// Waypoint latitude
  double? latitude;

  /// Waypoint longitude
  double? longitude;

  /// Waypoint property isSilent
  bool? isSilent;

  @override
  String toString() {
    return 'WayPoint{id: $id, name: $name, latitude: $latitude, longitude: $longitude}';
  }
}
