package com.eopeter.fluttermapboxnavigation

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.lifecycle.LifecycleOwner
import com.eopeter.fluttermapboxnavigation.databinding.MapboxItemViewAnnotationBinding
import com.eopeter.fluttermapboxnavigation.databinding.NavigationActivityBinding
import com.eopeter.fluttermapboxnavigation.models.MapBoxEvents
import com.eopeter.fluttermapboxnavigation.models.MapBoxRouteProgressEvent
import com.eopeter.fluttermapboxnavigation.models.Waypoint
import com.eopeter.fluttermapboxnavigation.models.WaypointSet
import com.eopeter.fluttermapboxnavigation.utilities.CustomInfoPanelEndNavButtonBinder
import com.eopeter.fluttermapboxnavigation.utilities.PluginUtilities
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.mapbox.android.core.location.LocationEngineProvider;
import com.mapbox.android.core.location.LocationEngineRequest
import com.mapbox.android.core.permissions.PermissionsManager
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsRoute
import com.mapbox.api.directions.v5.models.RouteOptions
import com.mapbox.api.matching.v5.models.MapMatchingMatching
import com.mapbox.api.matching.v5.models.MapMatchingResponse
import com.mapbox.bindgen.Expected
import com.mapbox.geojson.Point
import com.mapbox.geojson.Polygon
import com.mapbox.maps.*
import com.mapbox.maps.plugin.animation.camera
import com.mapbox.maps.plugin.annotation.generated.*
import com.mapbox.maps.plugin.compass.compass
import com.mapbox.maps.plugin.scalebar.scalebar
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.mapbox.navigation.base.extensions.applyDefaultNavigationOptions
import com.mapbox.navigation.base.extensions.applyLanguageAndVoiceUnitOptions
import com.mapbox.navigation.base.options.NavigationOptions
import com.mapbox.navigation.base.route.*
import com.mapbox.navigation.base.trip.model.RouteLegProgress
import com.mapbox.navigation.base.trip.model.RouteProgress
import com.mapbox.navigation.core.MapboxNavigation
import com.mapbox.navigation.core.RoutesSetCallback
import com.mapbox.navigation.core.RoutesSetError
import com.mapbox.navigation.core.RoutesSetSuccess
import com.mapbox.navigation.core.arrival.ArrivalObserver
import com.mapbox.navigation.core.directions.session.RoutesObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.trip.session.*
import com.mapbox.navigation.dropin.infopanel.InfoPanelBinder
import com.mapbox.navigation.ui.maps.camera.NavigationCamera
import com.mapbox.navigation.ui.maps.camera.data.MapboxNavigationViewportDataSource
import com.mapbox.navigation.ui.maps.camera.lifecycle.NavigationBasicGesturesHandler
import com.mapbox.navigation.ui.maps.camera.state.NavigationCameraState
import com.mapbox.navigation.ui.maps.camera.transition.NavigationCameraTransitionOptions
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineApi
import com.mapbox.navigation.ui.maps.route.line.api.MapboxRouteLineView
import com.mapbox.navigation.ui.maps.route.line.model.MapboxRouteLineOptions
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineColorResources
import com.mapbox.navigation.ui.maps.route.line.model.RouteLineResources
import com.mapbox.navigation.ui.tripprogress.model.*
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.math.*

open class TurnByTurn(
    ctx: Context,
    act: Activity,
    bind: NavigationActivityBinding,
    accessToken: String
) : MethodChannel.MethodCallHandler,
    EventChannel.StreamHandler,
    Application.ActivityLifecycleCallbacks {

    open fun initFlutterChannelHandlers() {
        this.methodChannel?.setMethodCallHandler(this)
        this.eventChannel?.setStreamHandler(this)
        Log.d("MARCO", "COMPLETED initFlutterChannelHandlers")
    }

    open fun initNavigation(mv: MapView, arguments: Map<*, *>) {
        val navigationOptions = NavigationOptions.Builder(this.context)
            .accessToken(this.token)
            .build()

        MapboxNavigationApp
            .setup(navigationOptions)
            .attach(this.activity as LifecycleOwner)

        mapView = mv
        mapView.compass.visibility = true
        mapView.scalebar.enabled = true
        setOptions(arguments)

        registerObservers()
        mapboxNavigation = MapboxNavigationApp.current()!!
        mapboxNavigation.startTripSession(withForegroundService = false) // TODO true?

        // Hide info panel
        binding.navigationView.customizeViewBinders {
            infoPanelBinder = CustomInfoPanelBinder()
        }

        if (!PermissionsManager.areLocationPermissionsGranted(this.context)) {
            PermissionsManager(null).requestLocationPermissions(this.activity);
        }

        Log.d("MARCO", "COMPLETED initNavigation")
    }

    class CustomInfoPanelBinder : InfoPanelBinder() {
        override fun onCreateLayout(layoutInflater: LayoutInflater, root: ViewGroup): ViewGroup {
            return layoutInflater.inflate(R.layout.mapbox_layout_info_panel, root, false) as ViewGroup
        }
        override fun getHeaderLayout(layout: ViewGroup): ViewGroup? = layout.findViewById(R.id.infoPanelHeader)
        override fun getContentLayout(layout: ViewGroup): ViewGroup? = layout.findViewById(R.id.infoPanelContent)
    }


    override fun onMethodCall(methodCall: MethodCall, result: MethodChannel.Result) {
        when (methodCall.method) {
            "getPlatformVersion" -> {
                result.success("Android ${android.os.Build.VERSION.RELEASE}")
            }
            "enableOfflineRouting" -> {
                // downloadRegionForOfflineRouting(call, result)
            }
            "buildRoute" -> {
                this.buildRoute(methodCall, result)
            }
            "buildCustomRouteFromJsonString" -> {
                this.buildCustomRouteFromJsonString(methodCall, result)
            }
            "clearRoute" -> {
                this.clearRoute(methodCall, result)
            }
            "startFreeDrive" -> {
                FlutterMapboxNavigationPlugin.enableFreeDriveMode = true
                this.startFreeDrive()
            }
            "startNavigation" -> {
                FlutterMapboxNavigationPlugin.enableFreeDriveMode = false
                this.startNavigation(methodCall, result)
            }
            "finishNavigation" -> {
                this.finishNavigation(methodCall, result)
            }
            "getDistanceRemaining" -> {
                result.success(this.distanceRemaining)
            }
            "getDurationRemaining" -> {
                result.success(this.durationRemaining)
            }
            "setPOIs" -> {
                addPOIs(methodCall, result)
            }
            "removePOIs" -> {
                removePOIs(methodCall, result)
            }
            "centerCameraWholeRoute" -> {
                centerCameraWholeRoute(methodCall, result)
            }
            else -> result.notImplemented()
        }
    }

    private fun buildRoute(methodCall: MethodCall, result: MethodChannel.Result) {
        this.isNavigationCanceled = false
        val arguments = methodCall.arguments as? Map<*, *>
        if (arguments != null) this.setOptions(arguments)
        this.addedWaypoints.clear()
        val points = arguments?.get("wayPoints") as HashMap<*, *>
        for (item in points) {
            val point = item.value as HashMap<*, *>
            val latitude = point["Latitude"] as Double
            val longitude = point["Longitude"] as Double
            val isSilent = point["IsSilent"] as Boolean
            this.addedWaypoints.add(Waypoint(Point.fromLngLat(longitude, latitude),isSilent))
        }
        this.getRoute(this.context)
        result.success(true)
    }

    private fun buildCustomRouteFromJsonString(methodCall: MethodCall, result: MethodChannel.Result) {
        this.isNavigationCanceled = false
        val arguments = methodCall.arguments as? Map<*, *>
        if (arguments != null) this.setOptions(arguments)
        this.addedWaypoints.clear()
        val points = arguments?.get("wayPoints") as HashMap<*, *>
        for (item in points) {
            val point = item.value as HashMap<*, *>
            val latitude = point["Latitude"] as Double
            val longitude = point["Longitude"] as Double
            val isSilent = true // point["IsSilent"] as Boolean
            this.addedWaypoints.add(Waypoint(Point.fromLngLat(longitude, latitude),isSilent))
        }
        println("KOTLIN HAS " + points.size + " WAYPOINTS")
        val routeOptions = RouteOptions
            .builder()
            .applyDefaultNavigationOptions()
//            .applyDefaultNavigationOptions(navigationMode)
            .coordinatesList(this.addedWaypoints.coordinatesList())
            .waypointIndicesList(this.addedWaypoints.waypointsIndices())
            .waypointNamesList(this.addedWaypoints.waypointsNames())
            .alternatives(false) // .alternatives(alternatives)
            .steps(true)
            .bannerInstructions(true)
            .language("it")
            .roundaboutExits(true)
            .voiceInstructions(true)
            // .voiceUnits(navigationVoiceUnits) ??
//            .bannerInstructions(bannerInstructionsEnabled)
//            .voiceInstructions(voiceInstructionsEnabled)
            .build();

        val jsonString = arguments?.get("jsonString") as String
        val response: MapMatchingResponse = MapMatchingResponse.fromJson(jsonString)
        val mapMatchingMatching = response.matchings()
        mapMatchingMatching?.let { matchingList ->
            val matching : MapMatchingMatching = matchingList[0]
            val directionsRoute : DirectionsRoute = matching.toDirectionRoute()
            val myDirectionsRoute : DirectionsRoute = directionsRoute.toBuilder()
                .routeIndex("0")
                .routeOptions(routeOptions)
                .requestUuid("PwKdMwkJawckcptgwAtACMsPlqSMz4nACTPFOeET9LQsiwxj2bmlfA==") // TODO parse from JSON directionsRoute.requestUuid()
                .build();
            println(myDirectionsRoute.requestUuid())
            println(myDirectionsRoute.routeOptions()?.language())
            println(myDirectionsRoute.routeIndex())
            println(matching.confidence())

            val routerOrigin : RouterOrigin = RouterOrigin.Custom()
            val navigationRoute : NavigationRoute = myDirectionsRoute.toNavigationRoute(routerOrigin)

            mapboxNavigation.setNavigationRoutes(
                listOf(navigationRoute),
                0,
                callback = object : RoutesSetCallback {
                    override fun onRoutesSet(result: Expected<RoutesSetError, RoutesSetSuccess>) {
                        println("I AM INSIDE of onRoutesSet() !!!!!!!!!!!!!!!!")
                        val routes: List<NavigationRoute> = listOf(navigationRoute)
                        this@TurnByTurn.currentRoutes = routes
                        PluginUtilities.sendEvent(
                            MapBoxEvents.ROUTE_BUILT,
                            Gson().toJson(routes.map { it.directionsRoute.toJson() })
                        )
                        this@TurnByTurn.binding.navigationView.api.routeReplayEnabled(
                            this@TurnByTurn.simulateRoute
                        )
                        this@TurnByTurn.binding.navigationView.api.startRoutePreview(routes)
                        this@TurnByTurn.binding.navigationView.customizeViewBinders {
                            this.infoPanelEndNavigationButtonBinder =
                                CustomInfoPanelEndNavButtonBinder(activity)
                        }
                    }
                }
            )
        }

// java.lang.IllegalArgumentException: Provided DirectionsRoute has to have #routeIndex property set.
// If the route was generated independently of Nav SDK,
//     rebuild the object and assign the index based on the position in the response collection.

// 	at com.mapbox.navigation.base.route.NavigationRouteEx.toNavigationRoute(NavigationRoute.kt:494)
// 	at com.eopeter.fluttermapboxnavigation.TurnByTurn.buildCustomRouteFromJsonString(TurnByTurn.kt:217)

        result.success(true)
    }
/*

 java.lang.IllegalArgumentException: Provided DirectionsRoute has to have #routeOptions property set.
 If the route was generated independently of Nav SDK, rebuild the object and assign the options based on the used request URL.

 	at com.eopeter.fluttermapboxnavigation.TurnByTurn.buildCustomRouteFromJsonString(TurnByTurn.kt:170)
 	at com.eopeter.fluttermapboxnavigation.TurnByTurn.onMethodCall(TurnByTurn.kt:111)

 */

    private fun getRoute(context: Context) {
        mapboxNavigation.startTripSession(withForegroundService = false)
        MapboxNavigationApp.current()!!.requestRoutes(
            routeOptions = RouteOptions
                .builder()
                .applyDefaultNavigationOptions(navigationMode)
                .applyLanguageAndVoiceUnitOptions(context)
                .coordinatesList(this.addedWaypoints.coordinatesList())
                .waypointIndicesList(this.addedWaypoints.waypointsIndices())
                .waypointNamesList(this.addedWaypoints.waypointsNames())
                .language(navigationLanguage)
                .alternatives(false) // .alternatives(alternatives)
                .steps(true)
                .voiceUnits(navigationVoiceUnits)
                .bannerInstructions(bannerInstructionsEnabled)
                .voiceInstructions(voiceInstructionsEnabled)
                .enableRefresh(true)
                .build(),
            callback = object : NavigationRouterCallback {
                override fun onRoutesReady(routes: List<NavigationRoute>, routerOrigin: RouterOrigin) {
                    this@TurnByTurn.currentRoutes = routes
                    PluginUtilities.sendEvent(
                        MapBoxEvents.ROUTE_BUILT,
                        Gson().toJson(routes.map { it.directionsRoute.toJson() })
                    )
                    this@TurnByTurn.binding.navigationView.api.routeReplayEnabled(
                        this@TurnByTurn.simulateRoute
                    )
                    this@TurnByTurn.binding.navigationView.api.startRoutePreview(routes)
                    this@TurnByTurn.binding.navigationView.customizeViewBinders {
                        this.infoPanelEndNavigationButtonBinder = CustomInfoPanelEndNavButtonBinder(activity)
                    }
                    Log.d("MARCO", "Route should be set right now")
                }
                override fun onFailure(reasons: List<RouterFailure>, routeOptions: RouteOptions) {
                    PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILD_FAILED)
                }
                override fun onCanceled(routeOptions: RouteOptions, routerOrigin: RouterOrigin) {
                    PluginUtilities.sendEvent(MapBoxEvents.ROUTE_BUILD_CANCELLED)
                }
            }
        )
    }

    private fun clearRoute(methodCall: MethodCall, result: MethodChannel.Result) {
        this.currentRoutes = null
        val navigation = MapboxNavigationApp.current()
        navigation?.stopTripSession()
        PluginUtilities.sendEvent(MapBoxEvents.NAVIGATION_CANCELLED)
    }

    private fun startFreeDrive() {
        this.binding.navigationView.api.startFreeDrive()
    }

    private fun startNavigation(methodCall: MethodCall, result: MethodChannel.Result) {
        val arguments = methodCall.arguments as? Map<*, *>
        if (arguments != null) {
            this.setOptions(arguments)
        }

        this.startNavigation()

        if (this.currentRoutes != null) {
            result.success(true)
        } else {
            result.success(false)
        }
    }

    private fun finishNavigation(methodCall: MethodCall, result: MethodChannel.Result) {
        this.finishNavigation()

        if (this.currentRoutes != null) {
            result.success(true)
        } else {
            result.success(false)
        }
    }

    private lateinit var mapViewForAnnotation: MapView

    open fun setMapViewForAnnotation(mv: MapView) {
        mapViewForAnnotation = mv
        Log.d("MARCO", "COMPLETED setMapViewForAnnotation")

        // NOT WORKING FROM HERE
        /*
        mapboxMap = mv.getMapboxMap()
        // initialize Navigation Camera
        viewportDataSource = MapboxNavigationViewportDataSource(mapboxMap)
        navigationCamera = NavigationCamera(
            mapboxMap,
            mv.camera,
            viewportDataSource
        )
        // set the animations lifecycle listener to ensure the NavigationCamera stops
        // automatically following the user location when the map is interacted with
        mv.camera.addCameraAnimationsLifecycleListener(
            NavigationBasicGesturesHandler(navigationCamera)
        )
        navigationCamera.registerNavigationCameraStateChangeObserver { navigationCameraState ->
            println("INSIDE registerNavigationCameraStateChangeObserver " + navigationCameraState)
            // shows/hide the recenter button depending on the camera state
            // when (navigationCameraState) {
            //     NavigationCameraState.TRANSITION_TO_FOLLOWING,
            //     NavigationCameraState.FOLLOWING -> binding.recenter.visibility = View.INVISIBLE
            //     NavigationCameraState.TRANSITION_TO_OVERVIEW,
            //     NavigationCameraState.OVERVIEW,
            //     NavigationCameraState.IDLE -> binding.recenter.visibility = View.INVISIBLE
            // }
        }
        Log.d("MARCO", "COMPLETED initNavigation completed camera")
         */
    }

    private fun addPOIs(methodCall: MethodCall, result: MethodChannel.Result) {
        val arguments = methodCall.arguments as? Map<*, *>
        if(arguments != null) {
            val poiPoints = arguments["poi"] as? HashMap<*, *>
            this@TurnByTurn.currentPoiPoints = poiPoints
            addPOIAnnotations(poiPoints!!)
            Log.d("MARCO", "completed addPOIs")
        }
        result.success(true)
    }

    private fun addPOIAnnotations(pois: HashMap<*, *>) {
        for (item in pois) {
            val poi = item.value as HashMap<*, *>
            val id = poi["Id"] as String
            // val name = poi["Name"] as String
            val text = poi["Text"] as String
            val latitude = poi["Latitude"] as Double
            val longitude = poi["Longitude"] as Double
            val coordinate = Point.fromLngLat(longitude, latitude)
            val viewAnnotation = mapViewForAnnotation.viewAnnotationManager.addViewAnnotation(
                resId = R.layout.mapbox_item_view_annotation,
                options = viewAnnotationOptions {
                    geometry(coordinate)
                    // offsetY(170)
                },
            )
            viewAnnotation.setOnClickListener { b ->
                PluginUtilities.sendEvent(MapBoxEvents.ANNOTATION_TAPPED, id)

            }
            MapboxItemViewAnnotationBinding.bind(viewAnnotation).apply {
                tvLocation.clipToOutline = false
                tvLocation.text = text
            }
        }
    }

    private fun removePOIs(methodCall: MethodCall, result: MethodChannel.Result) {
        // val arguments = methodCall.arguments as? Map<*, *>
        // if(arguments != null) {
            // val groupName = arguments["group"] as? String
        mapViewForAnnotation.viewAnnotationManager.removeAllViewAnnotations() // remove all
        // }
        result.success(true)
    }

    private fun centerCameraWholeRoute(methodCall: MethodCall, result: MethodChannel.Result) {
        // NOT WORKING
        /*
        println("MARCO: SET CAMERA  from  " + navigationCamera.state)
        if (navigationCamera.state == NavigationCameraState.FOLLOWING)
            navigationCamera.requestNavigationCameraToOverview()
        else
            navigationCamera.requestNavigationCameraToFollowing()
        return
        val coordinates = listOf(
            Point.fromLngLat(8.39005, 45.85317),
            Point.fromLngLat(8.18951, 45.46427),
            )
        println(mapboxMap.cameraState.center)
        val cameraPosition = mapboxMap.cameraForCoordinates(coordinates, EdgeInsets(1.0, 1.0, 1.0, 1.0))
        mapboxMap.setCamera(cameraPosition)
        println("MARCO: SET CAMERA")
        println(cameraPosition)
        println(mapboxMap)
        println(mapboxMap.cameraState.center)
        return
        println("MARCO: SET CAMERA 3")
        navigationCamera.requestNavigationCameraToFollowing()
        println("MARCO: SET CAMERA 4")
        navigationCamera.requestNavigationCameraToOverview(
            stateTransitionOptions = NavigationCameraTransitionOptions.Builder()
                .maxDuration(0) // instant transition
                .build()
        )
        */
        result.success(true)
    }

    private fun zoomIn(methodCall: MethodCall, result: MethodChannel.Result) {
        /*
        val center = mapboxNavigation.camera.center
        val zoom = mapboxNavigation.mapboxMap.cameraState.zoom
        val zoom2 = mapboxNavigation.cameraState.zoom
        println("--------||||||||||||||||||||||||||||  zoomIn() ")
        println(center)
        println(zoom)
        // define camera position
        val cameraPosition = CameraOptions.Builder()
            .zoom(14.0)
            .center(center)
            .build()
        // set camera position
        mapView.mapboxMap.setCamera(cameraPosition)         */
        result.success(true)
    }

    @SuppressLint("MissingPermission")
    private fun startNavigation() {
        if (this.currentRoutes == null) {
            PluginUtilities.sendEvent(MapBoxEvents.NAVIGATION_CANCELLED)
            return
        }
        this.binding.navigationView.api.startActiveGuidance(this.currentRoutes!!)
        PluginUtilities.sendEvent(MapBoxEvents.NAVIGATION_RUNNING)
        Log.d("MARCO", "Route should be running right now")
    }

    private fun finishNavigation(isOffRouted: Boolean = false) {
        MapboxNavigationApp.current()!!.stopTripSession()
        this.isNavigationCanceled = true
        PluginUtilities.sendEvent(MapBoxEvents.NAVIGATION_CANCELLED)
    }

    private fun setOptions(arguments: Map<*, *>) {
        val navMode = arguments["mode"] as? String
        if (navMode != null) {
            when (navMode) {
                "walking" -> this.navigationMode = DirectionsCriteria.PROFILE_WALKING
                "cycling" -> this.navigationMode = DirectionsCriteria.PROFILE_CYCLING
                "driving" -> this.navigationMode = DirectionsCriteria.PROFILE_DRIVING
            }
        }

        val simulated = arguments["simulateRoute"] as? Boolean
        if (simulated != null) {
            this.simulateRoute = simulated
        }

        val language = arguments["language"] as? String
        if (language != null) {
            this.navigationLanguage = language
        }

        val units = arguments["units"] as? String

        if (units != null) {
            if (units == "imperial") {
                this.navigationVoiceUnits = DirectionsCriteria.IMPERIAL
            } else if (units == "metric") {
                this.navigationVoiceUnits = DirectionsCriteria.METRIC
            }
        }

        this.mapStyleUrlDay = arguments["mapStyleUrlDay"] as? String
        this.mapStyleUrlNight = arguments["mapStyleUrlNight"] as? String

        //Set the style Uri
        if (this.mapStyleUrlDay == null) this.mapStyleUrlDay = Style.MAPBOX_STREETS
        if (this.mapStyleUrlNight == null) this.mapStyleUrlNight = Style.DARK

        // Differentiate the route traveled (https://docs.mapbox.com/android/navigation/v2/guides/ui-components/route-line/#differentiate-the-route-traveled)
        val customColorResources = RouteLineColorResources.Builder()
            // .routeDefaultColor(Color.parseColor("#FFCC00"))
            .routeLineTraveledColor(Color.parseColor("#f9e1b2"))
            .build()
        val routeLineResources = RouteLineResources.Builder()
            .routeLineColorResources(customColorResources)
            .build()
        val myRouteLineOptions = MapboxRouteLineOptions.Builder(this.context)
            .withVanishingRouteLineEnabled(true)
            .vanishingRouteLineUpdateInterval(1)
            .withRouteLineResources(routeLineResources)
            .build()


        this@TurnByTurn.binding.navigationView.customizeViewOptions {
            mapStyleUriDay = this@TurnByTurn.mapStyleUrlDay
            mapStyleUriNight = this@TurnByTurn.mapStyleUrlNight
            routeLineOptions = myRouteLineOptions
        }

        this.initialLatitude = arguments["initialLatitude"] as? Double
        this.initialLongitude = arguments["initialLongitude"] as? Double

        val zm = arguments["zoom"] as? Double
        if (zm != null) {
            this.zoom = zm
        }
        val br = arguments["bearing"] as? Double
        if (br != null) {
            this.bearing = br
        }
        val tt = arguments["tilt"] as? Double
        if (tt != null) {
            this.tilt = tt
        }
        val optim = arguments["isOptimized"] as? Boolean
        if (optim != null) {
            this.isOptimized = optim
        }
        val anim = arguments["animateBuildRoute"] as? Boolean
        if (anim != null) {
            this.animateBuildRoute = anim
        }
        val altRoute = arguments["alternatives"] as? Boolean
        if (altRoute != null) {
            this.alternatives = altRoute
        }
        this.alternatives = false
        val voiceEnabled = arguments["voiceInstructionsEnabled"] as? Boolean
        if (voiceEnabled != null) {
            this.voiceInstructionsEnabled = voiceEnabled
        }
        val bannerEnabled = arguments["bannerInstructionsEnabled"] as? Boolean
        if (bannerEnabled != null) {
            this.bannerInstructionsEnabled = bannerEnabled
        }
        val longPress = arguments["longPressDestinationEnabled"] as? Boolean
        if (longPress != null) {
            this.longPressDestinationEnabled = longPress
        }
        val onMapTap = arguments["enableOnMapTapCallback"] as? Boolean
        if (onMapTap != null) {
            this.enableOnMapTapCallback = onMapTap
        }
    }

    open fun registerObservers() {
        // register event listeners
        MapboxNavigationApp.current()?.registerBannerInstructionsObserver(this.bannerInstructionObserver)
        MapboxNavigationApp.current()?.registerVoiceInstructionsObserver(this.voiceInstructionObserver)
        MapboxNavigationApp.current()?.registerOffRouteObserver(this.offRouteObserver)
        MapboxNavigationApp.current()?.registerRoutesObserver(this.routesObserver)
        MapboxNavigationApp.current()?.registerLocationObserver(this.locationObserver)
        MapboxNavigationApp.current()?.registerRouteProgressObserver(this.routeProgressObserver)
        MapboxNavigationApp.current()?.registerArrivalObserver(this.arrivalObserver)
    }

    open fun unregisterObservers() {
        // unregister event listeners to prevent leaks or unnecessary resource consumption
        MapboxNavigationApp.current()?.unregisterBannerInstructionsObserver(this.bannerInstructionObserver)
        MapboxNavigationApp.current()?.unregisterVoiceInstructionsObserver(this.voiceInstructionObserver)
        MapboxNavigationApp.current()?.unregisterOffRouteObserver(this.offRouteObserver)
        MapboxNavigationApp.current()?.unregisterRoutesObserver(this.routesObserver)
        MapboxNavigationApp.current()?.unregisterLocationObserver(this.locationObserver)
        MapboxNavigationApp.current()?.unregisterRouteProgressObserver(this.routeProgressObserver)
        MapboxNavigationApp.current()?.unregisterArrivalObserver(this.arrivalObserver)
    }

    // Flutter stream listener delegate methods
    override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
        FlutterMapboxNavigationPlugin.eventSink = events
    }
    override fun onCancel(arguments: Any?) {
        FlutterMapboxNavigationPlugin.eventSink = null
    }

    private val context: Context = ctx
    var mapMoved = false
    val activity: Activity = act
    private val token: String = accessToken
    open var methodChannel: MethodChannel? = null
    open var eventChannel: EventChannel? = null
    private var lastLocation: Location? = null

    /**
     * Helper class that keeps added waypoints and transforms them to the [RouteOptions] params.
     */
    private val addedWaypoints = WaypointSet()

    // Config
    private var initialLatitude: Double? = null
    private var initialLongitude: Double? = null

    // val wayPoints: MutableList<Point> = mutableListOf()
    private var navigationMode = DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
    var simulateRoute = false
    private var mapStyleUrlDay: String? = null
    private var mapStyleUrlNight: String? = null
    private var navigationLanguage = "en"
    private var navigationVoiceUnits = DirectionsCriteria.IMPERIAL
    private var zoom = 15.0
    private var bearing = 0.0
    private var tilt = 0.0
    private var distanceRemaining: Float? = null
    private var durationRemaining: Double? = null

    private var alternatives = false

    var allowsUTurnAtWayPoints = false
    var enableRefresh = false
    private var voiceInstructionsEnabled = true
    private var bannerInstructionsEnabled = true
    private var longPressDestinationEnabled = true
    private var enableOnMapTapCallback = false
    private var animateBuildRoute = true
    private var isOptimized = false

    private var currentRoutes: List<NavigationRoute>? = null
    private var isNavigationCanceled = false

    private lateinit var mapboxNavigation: MapboxNavigation

    private lateinit var mapboxMap: MapboxMap
    /**
     * Used to execute camera transitions based on the data generated by the [viewportDataSource].
     * This includes transitions from route overview to route following and continuously updating the camera as the location changes.
     */
    private lateinit var navigationCamera: NavigationCamera
    /**
     * Produces the camera frames based on the location and routing data for the [navigationCamera] to execute.
     */
    private lateinit var viewportDataSource: MapboxNavigationViewportDataSource

    private lateinit var mapView: MapView
    private var currentPoiPoints: HashMap<*, *>? = null
    private var nearPoiIds: MutableList<String> = mutableListOf()

    /**
     * Bindings to the example layout.
     */
    open val binding: NavigationActivityBinding = bind

    /**
     * Gets notified with location updates.
     *
     * Exposes raw updates coming directly from the location services
     * and the updates enhanced by the Navigation SDK (cleaned up and matched to the road).
     */
    private val locationObserver = object : LocationObserver {
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            val location: Location = locationMatcherResult.enhancedLocation;
            this@TurnByTurn.lastLocation = location
            println("MARCO: got new location from locationObserver")
            // println("LAT = " + location.latitude)
            // println("LON = " + location.longitude)

            // Send NEW_LOCATION event
            val json = JsonObject()
            json.addProperty("latitude", location.latitude)
            json.addProperty("longitude", location.longitude)
            json.addProperty("altitude", location.altitude)
            json.addProperty("speed", location.speed)
            json.addProperty("bearing", location.bearing)
            PluginUtilities.sendEvent(MapBoxEvents.NEW_LOCATION, json.toString())

            // Search for current near waypoints
            if(this@TurnByTurn.currentPoiPoints == null) return
            for (item in this@TurnByTurn.currentPoiPoints!!) {
                val poi = item.value as HashMap<*, *>
                val id = poi["Id"] as String
                val latitude = poi["Latitude"] as Double
                val longitude = poi["Longitude"] as Double
                val distance = meterDistanceBetweenPoints(
                    location.latitude.toString().toDouble(),
                    location.longitude.toString().toDouble(),
                    latitude.toString().toDouble(),
                    longitude.toString().toDouble(),
                )
                println(id)
                println(latitude)
                println(longitude)
                println("onNewLocationMatcherResult: POI_ID=$id   DISTANCE=$distance")

                // POI is near
                if(distance < 70) {
                    if(id in this@TurnByTurn.nearPoiIds) return;
                    println("ADDING POI$id")
                    this@TurnByTurn.nearPoiIds.add(id)
                    PluginUtilities.sendEvent(MapBoxEvents.NEW_NEAR_POINT, id)
                    return;
                }

                // POI is not near
                if(id !in this@TurnByTurn.nearPoiIds) continue;
                println("REMOVING POI$id")
                this@TurnByTurn.nearPoiIds.remove(id)
                PluginUtilities.sendEvent(MapBoxEvents.OLD_NEAR_POINT, id)
            }
        }

        override fun onNewRawLocation(rawLocation: Location) {
            // no impl
        }
    }

    private fun meterDistanceBetweenPoints(latA: Double, lngA: Double, latB: Double, lngB: Double): Double {
        val pk = (180f / Math.PI).toString().toDouble()
        val a1 = latA / pk
        val a2 = lngA / pk
        val b1 = latB / pk
        val b2 = lngB / pk
        val t1: Double = cos(a1) * cos(a2) * cos(b1) * cos(b2)
        val t2: Double = cos(a1) * sin(a2) * cos(b1) * sin(b2)
        val t3: Double = sin(a1) * sin(b1)
        val tt: Double = acos(t1 + t2 + t3)
        return 6366000 * tt
    }

    private val bannerInstructionObserver = BannerInstructionsObserver { bannerInstructions ->
        PluginUtilities.sendEvent(MapBoxEvents.BANNER_INSTRUCTION, bannerInstructions.primary().text())
    }

    private val voiceInstructionObserver = VoiceInstructionsObserver { voiceInstructions ->
        PluginUtilities.sendEvent(MapBoxEvents.SPEECH_ANNOUNCEMENT, voiceInstructions.announcement().toString())
    }

    private val offRouteObserver = OffRouteObserver { offRoute ->
        if (offRoute) {
            // PluginUtilities.sendEvent(MapBoxEvents.USER_OFF_ROUTE)
        }
    }

    private val routesObserver = RoutesObserver { routeUpdateResult ->
        if (routeUpdateResult.navigationRoutes.isNotEmpty()) {
            PluginUtilities.sendEvent(MapBoxEvents.REROUTE_ALONG);
        }
    }

    /**
     * Gets notified with progress along the currently active route.
     */
    private val routeProgressObserver = RouteProgressObserver { routeProgress ->
        if (!this.isNavigationCanceled) {
            try {
                this.distanceRemaining = routeProgress.distanceRemaining
                this.durationRemaining = routeProgress.durationRemaining
                PluginUtilities.sendEvent(MapBoxRouteProgressEvent(routeProgress))
            } catch (_: java.lang.Exception) {
                // handle this error
            }
        }
    }

    private val arrivalObserver: ArrivalObserver = object : ArrivalObserver {
        override fun onFinalDestinationArrival(routeProgress: RouteProgress) {
            PluginUtilities.sendEvent(MapBoxEvents.ON_ARRIVAL)
            val navigation = MapboxNavigationApp.current()
            navigation?.stopTripSession()
        }

        override fun onNextRouteLegStart(routeLegProgress: RouteLegProgress) {
            // not impl
        }

        override fun onWaypointArrival(routeProgress: RouteProgress) {
            PluginUtilities.sendEvent(MapBoxEvents.WAY_POINT_ON_ARRIVAL)
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
        Log.d("Embedded", "onActivityCreated not implemented")
    }
    override fun onActivityStarted(activity: Activity) {
        Log.d("Embedded", "onActivityStarted not implemented")
    }
    override fun onActivityResumed(activity: Activity) {
        Log.d("Embedded", "onActivityResumed not implemented")
    }
    override fun onActivityPaused(activity: Activity) {
        Log.d("Embedded", "onActivityPaused not implemented")
    }
    override fun onActivityStopped(activity: Activity) {
        Log.d("Embedded", "onActivityStopped not implemented")
    }
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {
        Log.d("Embedded", "onActivitySaveInstanceState not implemented")
    }
    override fun onActivityDestroyed(activity: Activity) {
        Log.d("Embedded", "onActivityDestroyed not implemented")
    }
}
