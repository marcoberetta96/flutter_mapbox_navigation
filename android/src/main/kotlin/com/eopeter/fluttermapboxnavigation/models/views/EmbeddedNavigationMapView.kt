package com.eopeter.fluttermapboxnavigation.models.views

import android.app.Activity
import android.content.Context
import android.location.Location
import android.view.View
import com.eopeter.fluttermapboxnavigation.R
import com.eopeter.fluttermapboxnavigation.TurnByTurn
import com.eopeter.fluttermapboxnavigation.databinding.MapboxItemViewAnnotationBinding
import com.eopeter.fluttermapboxnavigation.databinding.NavigationActivityBinding
import com.eopeter.fluttermapboxnavigation.models.MapBoxEvents
import com.eopeter.fluttermapboxnavigation.utilities.PluginUtilities
import com.mapbox.geojson.Point
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.MapView
import com.mapbox.maps.plugin.gestures.OnMapClickListener
import com.mapbox.maps.plugin.gestures.gestures
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.mapbox.navigation.dropin.EmptyBinder
import com.mapbox.navigation.dropin.map.MapViewObserver
import com.mapbox.navigation.core.lifecycle.MapboxNavigationApp
import com.mapbox.navigation.core.lifecycle.forwardMapboxNavigation
import com.mapbox.navigation.core.trip.session.LocationObserver
import com.mapbox.navigation.core.trip.session.LocationMatcherResult
import com.mapbox.navigation.utils.internal.ifNonNull
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.platform.PlatformView
import org.json.JSONObject
import android.util.Log

class EmbeddedNavigationMapView(
    context: Context,
    activity: Activity,
    binding: NavigationActivityBinding,
    binaryMessenger: BinaryMessenger,
    vId: Int,
    args: Any?,
    accessToken: String
) : PlatformView, TurnByTurn(context, activity, binding, accessToken) {

    private val viewId: Int = vId
    private val messenger: BinaryMessenger = binaryMessenger
    private val arguments = args as Map<*, *>
    private val options: MapInitOptions = MapInitOptions(context)
    private val mapView: MapView = MapView(context, options)
    private var mapView2: MapView? = null
    var done: Boolean? = null

    override fun initFlutterChannelHandlers() {
        methodChannel = MethodChannel(messenger, "flutter_mapbox_navigation/${viewId}")
        eventChannel = EventChannel(messenger, "flutter_mapbox_navigation/${viewId}/events")
        super.initFlutterChannelHandlers()
    }

    open fun initialize() {
        initFlutterChannelHandlers()
        initNavigation(mapView, arguments)

        if(!(this.arguments?.get("longPressDestinationEnabled") as Boolean)) {
            this.binding.navigationView.customizeViewOptions {
                enableMapLongClickIntercept = false;
            }
        }

        if((this.arguments?.get("enableOnMapTapCallback") as Boolean)) {
            this.binding.navigationView.registerMapObserver(onMapClick)
        }

        MapboxNavigationApp.current()?.registerLocationObserver(locationObserver)

        binding.navigationView.registerMapObserver(mapViewObserver)
        MapboxNavigationApp.registerObserver(navigationObserver)

        // Hide `SpeedLimit`, `RoadNameLabel` and `ActionButtons`
        val viewBinder = EmptyBinder()
        binding.navigationView.customizeViewBinders {
//            roadNameBinder = viewBinder
            speedLimitBinder = viewBinder
            // actionButtonsBinder = viewBinder
        }
    }

    private val mapViewObserver = object : MapViewObserver() {
        override fun onAttached(mapView: MapView) {
            super.onAttached(mapView)
            this@EmbeddedNavigationMapView.mapView2 = mapView
        }

        override fun onDetached(mapView: MapView) {
            super.onDetached(mapView)
            this@EmbeddedNavigationMapView.mapView2 = null
        }
    }

    private val navigationObserver = forwardMapboxNavigation(
        attach = { mapboxNavigation ->
            mapboxNavigation.registerLocationObserver(locationObserver)
        },
        detach = { mapboxNavigation ->
            mapboxNavigation.unregisterLocationObserver(locationObserver)
        }
    )


    override fun getView(): View {
        return binding.root
    }

    override fun dispose() {
        if((this.arguments?.get("enableOnMapTapCallback") as Boolean)) {
            this.binding.navigationView.unregisterMapObserver(onMapClick)
        }
        unregisterObservers()
    }

    /**
     * Notifies with attach and detach events on [MapView]
     */
    private val onMapClick = object : MapViewObserver(), OnMapClickListener {

        override fun onAttached(mapView: MapView) {
            mapView.gestures.addOnMapClickListener(this)
        }

        override fun onDetached(mapView: MapView) {
            mapView.gestures.removeOnMapClickListener(this)
        }

        override fun onMapClick(point: Point): Boolean {
            var waypoint = mapOf<String, String>(
                Pair("latitude", point.latitude().toString()),
                Pair("longitude", point.longitude().toString())
            )
            PluginUtilities.sendEvent(MapBoxEvents.ON_MAP_TAP, JSONObject(waypoint).toString())
            return false
        }
    }


    /**
     * Gets notified with location updates.
     *
     * Exposes raw updates coming directly from the location services
     * and the updates enhanced by the Navigation SDK (cleaned up and matched to the road).
     */
    private val locationObserver = object : LocationObserver {
        override fun onNewLocationMatcherResult(locationMatcherResult: LocationMatcherResult) {
            ifNonNull(mapView2) { mapView2 ->
                val location = locationMatcherResult.enhancedLocation
                if(done == true) return
                setMapViewForAnnotation(mapView2)
                done = true
            }
        }

        override fun onNewRawLocation(rawLocation: Location) {
            // no impl
        }
    }
}
