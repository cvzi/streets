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
import refresco.streetnames.geo.Feature
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream


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
        val url = "http://192.168.178.36/streetnames/$filename.json"

        val file = File(context.getCacheDir(), outfile)

        if(file.exists()) {
            Log.v("Street", "File exists")

            loadFromFileAsync(file)
        } else {
            Log.v("Street", "File does not exists")
            donwloadFileAsync(file, url)
        }
    }


    fun donwloadFileAsync(file: File, url: String) = async(UI) {

            //TODO Turn on busy indicator.

            val job = async(Dispatchers.Default) {
                //We're on a background thread here.
                //Execute blocking calls, such as retrofit call.execute().body() + caching.


                // Download

                Log.v("URL", url)


                val(_, _, result) = url.httpGet().responseJson()

                val o = result.get().obj()

                Log.v("URL", "loaded")

                // Filter streets
                val features = o.getJSONArray("features")
                val allstreets = ArrayList<Feature>()
                for(i in 0 until features.length()) {
                    val feature = features.getJSONObject(i)
                    val geometry = feature.getJSONObject("geometry")
                    if (geometry.getString("type") != "LineString" ){
                        continue
                    }
                    val properties = feature.getJSONObject("properties")
                    if(properties.has("highway") && properties.has("name")) {
                        Log.v("Street", properties.getString("name"))
                        allstreets.add(Feature(feature))
                    }
                }

                file.outputStream().use{ ObjectOutputStream(it) .use{it.writeObject(allstreets)}}
                Log.v("URL", "file saved")

            }
            job.await();
            //We're back on the main thread here.
            //Update UI controls such as RecyclerView adapter data.

    }

    fun  loadFromFileAsync(file: File) = async(UI) {
        try {
            // TODO Turn on busy indicator.
            val job = async(Dispatchers.Default) {
                //We're on a background thread here.
                //Execute blocking calls, such as retrofit call.execute().body() + caching.

                Log.v("Street", "File loading...")
                //file.inputStream().use {ObjectInputStream(it).use {streets = it.readObject() as ArrayList<Feature>}}

                val inputStream = file.inputStream()
                val objectInputStream = ObjectInputStream(inputStream)
                var streets = objectInputStream.readObject() as ArrayList<Feature>?


                Log.v("File", "loaded")
                if(streets == null) {
                    Log.v("Features:", "streets is null")
                } else {
                    Log.v("Features:", "Length=${streets.size}")
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
