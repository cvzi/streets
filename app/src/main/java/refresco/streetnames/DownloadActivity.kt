package refresco.streetnames

import android.os.Bundle
import android.app.Activity
import android.content.SharedPreferences
import android.preference.PreferenceManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import com.github.kittinunf.fuel.httpGet
import com.github.kittinunf.fuel.android.extension.responseJson
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.async
import kotlinx.coroutines.experimental.android.UI
import refresco.streetnames.geo.*
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min


class DownloadActivity : Activity() {

    private val context = this

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_download)
        actionBar?.setDisplayHomeAsUpEnabled(true)

        val sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        var currentRegionPref = sharedPref.getString(SETTINGS_KEY_PREF_CURRENT_REGION, "")

        val availableRegions = resources.getStringArray(R.array.pref_available_regions)
        val availableRegionsTitles = resources.getStringArray(R.array.pref_available_regions_titles)


        val linearLayout = findViewById<LinearLayout>(R.id.linearLayout)


        val clickListener = View.OnClickListener { view  ->
            val index = view.getTag(R.id.DOWNLOADACTIVITY_REGION_INDEX_TAG) as Int
            currentRegionPref = availableRegions[index]

            val editor = sharedPref.edit()
            editor.putString(SETTINGS_KEY_PREF_CURRENT_REGION, currentRegionPref);
            editor.commit();

            downloadRegion(currentRegionPref)
        }

        for(i in 0 until availableRegions.size) {
            val button = Button(this)
            button.setText(availableRegionsTitles[i])
            button.setTag(R.id.DOWNLOADACTIVITY_REGION_INDEX_TAG, i)
            button.setOnClickListener(clickListener)
            linearLayout.addView(button)
        }

    }



    fun downloadRegion(filename : String) {
        // val filename = "planet_8.668_49.398_83f4c70b.osm"
        val outfile = "$filename.ser"
        //val url = "http://192.168.178.36/streetnames/$filename.json"
        val url = "http://192.168.8.101/streetnames/$filename.json"

        val file = File(context.getCacheDir(), outfile)

        val fileOk = file.exists()

        if(fileOk) {
            Log.v("Street", "File exists")
            loadFromFileAsync(file)
        } else {
            Log.v("Street", "File does not exists")
            downloadFileAsync(file, url)
        }
    }


    fun downloadFileAsync(file: File, url: String) = async(UI) {

            //TODO Turn on busy indicator.

            val job = async(Dispatchers.Default) {
                //We're on a background thread here.
                //Execute blocking calls, such as retrofit call.execute().body() + caching.


                // Download

                Log.v("downloadFileAsync", url)

                val(_, _, result) = url.httpGet().responseJson()

                val o = result.get().obj()

                Log.v("downloadFileAsync", "loaded")

                // Filter streets
                val features = o.getJSONArray("features")
                val allStreets = ArrayList<Feature>()
                for(i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val geometry = feature.getJSONObject("geometry")
                    if (geometry.getString("type") != "LineString" ){
                        continue
                    }
                    val properties = feature.getJSONObject("properties")
                    if(properties.has("highway") && properties.has("name")) {
                        Log.v("Street", properties.getString("name"))
                        allStreets.add(Feature(feature))
                    }
                }

                Log.v("downloadFileAsync", "streets filtered")

                var minLat = 999.0
                var maxLat = 0.0
                var minLng = 999.0
                var maxLng = 0.0

                for (i in 0 until allStreets.size) {
                    for (c in allStreets.get(i).coordinates) {
                        minLat = min(c.latitude, minLat)
                        maxLat = max(c.latitude, maxLat)
                        minLng = min(c.longitude, minLng)
                        maxLng = max(c.longitude, maxLng)
                    }
                }
                Log.v("downloadFileAsync", "minLat/max... calculated")

                val cosPhi0 = cos((maxLng - minLng) / 2.0);


                val width = 1.0
                val height = width / cosPhi0
                Log.i("downloadFileAsync", "width = $width , height = $height")

                val lngFactor = width / (maxLng - minLng)
                val latFactor = height / (maxLat - minLat)

                Log.i("downloadFileAsync", "lngFactor,latFactor calculated: $lngFactor , $latFactor")

                // Create hashmap and convert coordinates:
                val streetsHashMap = HashMap<String, LineCollection>(allStreets.size)
                for(f in allStreets) {

                    var lineCoords = FloatArray(f.coordinates.size * LineDrawer.COORDS_PER_VERTEX)
                    for (index in 0 until f.coordinates.size) {
                        var c = f.coordinates[index]
                        val x = (width * 0.5) - (lngFactor * (c.longitude - minLng))
                        val y = (latFactor * (c.latitude - minLat)) - (height * 0.5)
                        lineCoords[index * LineDrawer.COORDS_PER_VERTEX] = x.toFloat()
                        lineCoords[index * LineDrawer.COORDS_PER_VERTEX + 1] = y.toFloat()
                        lineCoords[index * LineDrawer.COORDS_PER_VERTEX + 2] = 0f
                    }
                    var name = UNDEFINEDSTREETNAME
                    if (f.properties.containsKey("name") && f.properties.get("name") != null && f.properties.get("name")!!.length > 0) {
                        name = f.properties.get("name") as String
                    }

                    val line = Line(lineCoords, name)
                    if (streetsHashMap.containsKey(name)) {
                        var lineCollection = streetsHashMap.get(name)
                        lineCollection!!.add(line)
                    } else {
                        streetsHashMap.put(name, LineCollection(line))
                    }

                }

                val streetCollection = StreetCollection(file.nameWithoutExtension, streetsHashMap, minLat, maxLat, minLng, maxLng)


                file.outputStream().use{ ObjectOutputStream(it) .use{it.writeObject(streetCollection)}}
                Log.v("downloadFileAsync", "file saved")

            }
            job.await();
            //We're back on the main thread here.
            //Update UI controls such as RecyclerView adapter data.

    }

    fun  loadFromFileAsync(file: File) = async(Dispatchers.Main) {
        try {
            // TODO Turn on busy indicator.
            val job = async(Dispatchers.Default) {
                //We're on a background thread here.
                //Execute blocking calls, such as retrofit call.execute().body() + caching.

                Log.v("Street", "File loading...")
                //file.inputStream().use {ObjectInputStream(it).use {streets = it.readObject() as ArrayList<Feature>}}

                val inputStream = file.inputStream()
                val objectInputStream = ObjectInputStream(inputStream)

                var streetCollection: StreetCollection?
                try {
                    streetCollection = objectInputStream.readObject() as StreetCollection
                } catch (e: Exception) {
                    Log.e("Street", "Could not load from file:\n$e")
                    streetCollection = null
                }

                Log.v("File", "loaded")
                if(streetCollection == null) {
                    Log.v("Features:", "streets is null")
                    Log.i("File", "Deleting ${file.absoluteFile}")
                    file.delete()
                    // TODO start download
                } else {
                    Log.v("Features:", "Length=${streetCollection.streets.size}")
                }
            }
            val result = job.await();
            //We're back on the main thread here.
            //Update UI controls such as RecyclerView adapter data.
        }
        catch (e: Exception) {
            Log.e("loadfromfileasync", e.toString())
        }
        finally {
            // TODO Turn off busy indicator.

        }
    }
}
