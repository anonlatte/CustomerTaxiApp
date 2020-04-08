package com.example.taxiapp.utils.directions_helpers


import com.google.android.gms.maps.model.LatLng
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.util.*

/**
 * Created by Vishal on 10/20/2018.
 * Edited by anonlatte on 23/05/2019
 */

internal class DataParser {
    var routeData: String = ""

    fun parse(jObject: JSONObject): List<List<HashMap<String, String>>> {

        val routes = ArrayList<List<HashMap<String, String>>>()
        val jRoutes: JSONArray
        val jLegs: JSONArray
        var jSteps: JSONArray
        try {
            jRoutes = jObject.getJSONArray("routes")
            /* Traversing only one route */
            jLegs = jRoutes.getJSONObject(0).getJSONArray("legs")
            routeData = jLegs.getJSONObject(0).getJSONObject("distance").getInt("value").toString()
            routeData += " " + jLegs.getJSONObject(0).getJSONObject("duration").getDouble("value")
            val path = ArrayList<HashMap<String, String>>()
            /* Traversing all legs */
            for (j in 0 until jLegs.length()) {
                jSteps = jLegs.getJSONObject(j).getJSONArray("steps")

                /* Traversing all steps */
                for (k in 0 until jSteps.length()) {
                    val polyline: String = jSteps.getJSONObject(k).getJSONObject("polyline").getString("points")
                    val list = decodePoly(polyline)

                    /* Traversing all points */
                    for (l in list.indices) {
                        val hm = HashMap<String, String>()
                        hm["lat"] = java.lang.Double.toString(list[l].latitude)
                        hm["lng"] = java.lang.Double.toString(list[l].longitude)
                        path.add(hm)
                    }
                }
                routes.add(path)
            }
        } catch (e: JSONException) {
            e.printStackTrace()
        }
        return routes
    }


    /**
     * Method to decode polyline points
     * Courtesy : https://jeffreysambells.com/2010/05/27/decoding-polylines-from-google-maps-direction-api-with-java
     */
    private fun decodePoly(encoded: String): List<LatLng> {

        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dLat

            shift = 0
            result = 0
            do {
                b = encoded[index++].toInt() - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dLng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dLng

            val p = LatLng(lat.toDouble() / 1E5,
                    lng.toDouble() / 1E5)
            poly.add(p)
        }

        return poly
    }
}