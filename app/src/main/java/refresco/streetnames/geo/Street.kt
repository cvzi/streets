package refresco.streetnames.geo

import org.json.JSONArray
import org.json.JSONObject
import java.io.Serializable

open class Coordinates(val longitude: Double, val latitude: Double) : Serializable  {
    constructor(coords: JSONArray) : this(coords.getDouble(0), coords.getDouble(1))
}

const val UNDEFINEDSTREETNAME = "<undefinedStreetName>"


open class Feature(j_obj: JSONObject) : Serializable {
    val type : String
    val coordinates: ArrayList<Coordinates> = ArrayList()
    val properties: HashMap<String, String> = HashMap()

    init {
        val j_geometry = j_obj.getJSONObject("geometry")
        val j_coordinates = j_geometry.getJSONArray("coordinates")
        type = j_geometry.getString("type")
        for(i in 0 until j_coordinates.length()) {
            val coords = j_coordinates.getJSONArray(i)
            coordinates.add(Coordinates(coords))
        }
        val j_properties = j_obj.getJSONObject("properties")
        val j_properties_keys = j_properties.keys()
        for(i in 0 until j_properties.length()) {
            val key = j_properties_keys.next()
            properties.put(key, j_properties.getString(key))
        }
    }

}

class Line(val lineCoords: FloatArray, val name: String = UNDEFINEDSTREETNAME) : Serializable

class LineCollection() : Serializable {
    constructor(linesParam: Array<Line>) : this() {
        lines = linesParam
    }
    constructor(line: Line) : this() {
        lines = lines.plus(line)
    }
    var lines: Array<Line> = arrayOf<Line>()

    val size: Int
        get() = lines.size

    val totalSize: Int
        get() {
            var sum = 0
            for(line in lines) {
                sum += line.lineCoords.size
            }
            return sum
        }


    fun add(line: Line) {
        lines = lines.plus(line)
    }

}

class StreetCollection(    val name: String, val streets: HashMap<String, LineCollection>,
                 val minLat: Double,
                 val maxLat: Double,
                 val minLng : Double,
                 val maxLng: Double) : Serializable