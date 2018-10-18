package refresco.streetnames

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.*
import android.os.Bundle
import android.preference.PreferenceManager
import android.util.Log
import android.widget.TextView
import kotlinx.android.synthetic.main.activity_view_map.*
import kotlinx.coroutines.experimental.Dispatchers
import kotlinx.coroutines.experimental.async
import refresco.streetnames.geo.Feature
import java.io.File
import java.io.ObjectInputStream
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import refresco.streetnames.geo.StreetCollection


class ViewMapActivity : Activity() {

    private var streetCollection: StreetCollection? = null
    fun getStreetCollection(): StreetCollection {
        return streetCollection!!
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_map)

        val sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val currentRegionPref: String = sharedPref.getString(SETTINGS_KEY_PREF_CURRENT_REGION, "")


        val textViewStatus: TextView = findViewById<TextView>(R.id.textViewStatus)


        textViewStatus.setText("Activity loaded")

        /*
        val layout = findViewById<LinearLayout>(R.id.linearLayout)
        val canvass = Canvass(this)
        layout.addView(canvass)
*/

        openRegion(currentRegionPref)

    }

    data class IntPair(val x: Int, val y: Int)
    fun screenSize(): IntPair {
        val display = windowManager.defaultDisplay
        val size = Point()
        display.getSize(size)
        val width = size.x
        val height = size.y
        return IntPair(width, height)
    }

    fun drawMap() {

        textViewStatus.setText("drawMap()")

        val drawCanvas = Canvass(this)
        val layout = findViewById<LinearLayout>(R.id.linearLayout)

        val params = layout.getLayoutParams()
        params.height = LinearLayout.LayoutParams.MATCH_PARENT
        params.width = LinearLayout.LayoutParams.MATCH_PARENT

        layout.addView(drawCanvas, params)
        layout.requestLayout();

        val streetCollection = getStreetCollection()


        val (screenwidth, _) = screenSize()

        val cosPhi0 = cos((streetCollection.maxLng - streetCollection.minLng) / 2.0);

        val width = screenwidth
        val height = (width/cosPhi0).toInt()


        Log.v("drawMap", "streetCollection.streets.size = ${streetCollection.streets.size}")

        val offset = 0f
        for((name, lineCollection) in streetCollection.streets) {
            for (line in lineCollection.lines) {
                var x = (1f - line.lineCoords[0])*width - width/2f
                var y = (1f - line.lineCoords[1])*height - height/2f
                drawCanvas.startPath(x, y)
                for (i in 3 until line.lineCoords.size step 3) {
                    x = (1f - line.lineCoords[i])*width - width/2f
                    y = (1f - line.lineCoords[i+1])*height - height/2f
                    drawCanvas.addPointToPath(x, y)
                }
            }
        }

        textViewStatus.setText("drawMap() ended")

        drawCanvas.invalidate()

    }


    fun openRegion(filename: String) {

        textViewStatus.setText("Open $filename")

        val outfile = "$filename.ser"

        val file = File(this.getCacheDir(), outfile)

        if (file.exists()) {
            textViewStatus.setText("Exists $filename")

            loadFromFileAsync( file,this)
        } else {
            val intent = Intent(this, DownloadActivity::class.java)
            startActivity(intent)
        }

    }

    fun loadFromFileAsync(file: File, activity: ViewMapActivity) = async(Dispatchers.Main) {

        try {
            textViewStatus.setText("Opening ${file.name}")
            // TODO Turn on busy indicator.

            val job = async(Dispatchers.Default) {
                //We're on a background thread here.
                //Execute blocking calls, such as retrofit call.execute().body() + caching.

                Log.v("Street", "File loading...")

                val inputStream = file.inputStream()
                val objectInputStream = ObjectInputStream(inputStream)
                try {
                    activity.streetCollection = objectInputStream.readObject() as StreetCollection
                } catch (e: java.io.InvalidClassException) {
                    Log.e("Street", "Could not load from file:\n$e")
                    streetCollection = null
                }

                Log.v("File", "loaded")
                if (activity.streetCollection == null) {
                    Log.v("Features:", "streets is null")
                } else {
                    Log.v("Features:", "Length=${activity.streetCollection!!.streets.size}")
                }
            }
            val result = job.await();
            //We're back on the main thread here.
            //Update UI controls such as RecyclerView adapter data.
        } catch (e: Exception) {
            Log.e("loadfromfileasync", e.toString())
        } finally {
            // TODO Turn off busy indicator.
            textViewStatus.setText("Loaded ${file.name}")
            drawMap()

        }
    }


    class Canvass : View {
        constructor(ctx: Context) : super(ctx)
        constructor(ctx: Context, attrs: AttributeSet) : super(ctx, attrs)

        private val mPaint: Paint
        private val pathList: ArrayList<Path>
        private var currentPath: Path

        init {
            mPaint = Paint();
            mPaint.setAntiAlias(true);
            mPaint.setColor(Color.BLACK);
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setStrokeJoin(Paint.Join.ROUND);
            mPaint.setStrokeWidth(1f);

            currentPath = Path()
            pathList = ArrayList<Path>()
        }


        fun startPath(x: Float, y: Float) {

            currentPath = Path()
            pathList.add(currentPath)
            currentPath.moveTo(x, y)

        }

        fun addPointToPath(x: Float, y: Float) {
            currentPath.lineTo(x, y)
        }


        override fun onDraw(canvas: Canvas) {
            canvas.drawRGB(255, 255, 255)

            if (!pathList.isEmpty()) {
                for (path in pathList) {
                    canvas.drawPath(path, mPaint)
                }
            }


        }
    }

}


