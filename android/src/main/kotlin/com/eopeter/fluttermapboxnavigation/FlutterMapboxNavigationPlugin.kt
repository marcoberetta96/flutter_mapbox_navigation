package com.eopeter.fluttermapboxnavigation

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.eopeter.fluttermapboxnavigation.factory.EmbeddedNavigationViewFactory
import com.eopeter.fluttermapboxnavigation.models.Waypoint
import com.mapbox.api.directions.v5.DirectionsCriteria
import com.mapbox.api.directions.v5.models.DirectionsRoute
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.platform.PlatformViewRegistry

/** FlutterMapboxNavigationPlugin */
class FlutterMapboxNavigationPlugin : FlutterPlugin, MethodCallHandler,
    EventChannel.StreamHandler, ActivityAware {

    private lateinit var channel: MethodChannel
    private lateinit var progressEventChannel: EventChannel
    private var currentActivity: Activity? = null
    private lateinit var currentContext: Context

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        val messenger = binding.binaryMessenger
        channel = MethodChannel(messenger, "flutter_mapbox_navigation")
        channel.setMethodCallHandler(this)

        progressEventChannel = EventChannel(messenger, "flutter_mapbox_navigation/events")
        progressEventChannel.setStreamHandler(this)

        platformViewRegistry = binding.platformViewRegistry
        binaryMessenger = messenger


    }

    companion object {

        var eventSink: EventChannel.EventSink? = null

        var PERMISSION_REQUEST_CODE: Int = 367

        lateinit var routes: List<DirectionsRoute>
        private var currentRoute: DirectionsRoute? = null
        val wayPoints: MutableList<Waypoint> = mutableListOf()

        var showAlternateRoutes: Boolean = true
        var longPressDestinationEnabled: Boolean = true
        var allowsUTurnsAtWayPoints: Boolean = false
        var enableOnMapTapCallback: Boolean = false
        var navigationMode = DirectionsCriteria.PROFILE_DRIVING_TRAFFIC
        var simulateRoute = false
        var enableFreeDriveMode = false
        var mapStyleUrlDay: String? = null
        var mapStyleUrlNight: String? = null
        var navigationLanguage = "en"
        var navigationVoiceUnits = DirectionsCriteria.IMPERIAL
        var voiceInstructionsEnabled = true
        var bannerInstructionsEnabled = true
        var zoom = 15.0
        var bearing = 0.0
        var tilt = 0.0
        var distanceRemaining: Float? = null
        var durationRemaining: Double? = null
        var platformViewRegistry: PlatformViewRegistry? = null
        var binaryMessenger: BinaryMessenger? = null

        var viewId = "FlutterMapboxNavigationView"
    }

    override fun onMethodCall(call: MethodCall, result: Result) {
        when (call.method) {
            "getPlatformVersion" -> {
                result.success("Android ${Build.VERSION.RELEASE}")
            }
            "getDistanceRemaining" -> {
                result.success(distanceRemaining)
            }
            "getDurationRemaining" -> {
                result.success(durationRemaining)
            }
            else -> result.notImplemented()
        }
    }

    override fun onListen(args: Any?, events: EventChannel.EventSink?) {
        eventSink = events
    }

    override fun onCancel(args: Any?) {
        eventSink = null
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        currentActivity = null
        channel.setMethodCallHandler(null)
        progressEventChannel.setStreamHandler(null)
    }

    override fun onDetachedFromActivity() {
        currentActivity!!.finish()
        currentActivity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        currentActivity = binding.activity
    }

    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        currentActivity = binding.activity
        currentContext = binding.activity.applicationContext
        if (platformViewRegistry != null && binaryMessenger != null && currentActivity != null) {
            platformViewRegistry?.registerViewFactory(
                viewId,
                EmbeddedNavigationViewFactory(binaryMessenger!!, currentActivity!!)
            )
        }
    }

    override fun onDetachedFromActivityForConfigChanges() {
        // To change body of created functions use File | Settings | File Templates.
    }
}

private const val MAPBOX_ACCESS_TOKEN_PLACEHOLDER = "YOUR_MAPBOX_ACCESS_TOKEN_GOES_HERE"