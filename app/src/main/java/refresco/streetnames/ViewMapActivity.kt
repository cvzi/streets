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
import android.view.SurfaceView
import android.graphics.Bitmap
import android.support.constraint.ConstraintLayout
import android.util.AttributeSet
import android.view.SurfaceHolder
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import android.R.attr.y
import android.R.attr.x
import android.view.Display




class ViewMapActivity : Activity() {

    private var streets: ArrayList<Feature>? = null
    fun getStreets(): ArrayList<Feature> {
        return streets!!
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

        val streets = getStreets()

        var minLat = 999.0
        var maxLat = 0.0
        var minLng = 999.0
        var maxLng = 0.0


        for (i in 0 until streets.size) {
            for (c in streets.get(i).coordinates) {
                minLat = min(c.latitude, minLat)
                maxLat = max(c.latitude, maxLat)
                minLng = min(c.longitude, minLng)
                maxLng = max(c.longitude, maxLng)
            }
        }

        Log.i("drawMap", "min/max calculated")

        /*
        val cosPhi0 = cos((maxLng - minLng) / 2.0);
        var height = layout.height - 20
        var w = floor(height * cosPhi0)
        Log.i("Dimensions", "h=$height, w=$w")

        Log.i("drawCanvas width", drawCanvas.getMeasuredWidth().toString())
        while (w > drawCanvas.getMeasuredWidth()) {
            height -= 10
            w = floor(height  * cosPhi0)
            drawCanvas.setLayoutParams(LinearLayout.LayoutParams(w.toInt(), height))
            Log.i("Dimensions2", "h=$height, w=$w")
            Log.i("drawCanvas width", drawCanvas.getMeasuredWidth().toString())
        }
       val width = w.toInt()
       Log.i("Dimensions3", "h=$height, w=$width")
        */




        val cosPhi0 = cos((maxLng - minLng) / 2.0);


        val (screenwidth, _) = screenSize()

        val width = screenwidth
        val height = (width/cosPhi0).toInt()



        val lngFactor = width / (maxLng - minLng)
        val latFactor = height / (maxLat - minLat)

        Log.i("drawMap", "lngFactor calculated")

        for (i in 0 until streets.size) {
            var first = true
            val f = streets.get(i)

            /*
            val name = f.properties.get("name")
            if(name != null) {
            }
             */

            for (c in f.coordinates) {
                val x = lngFactor * (c.longitude - minLng)
                val y = height - latFactor * (c.latitude - minLat)
                if (first) {
                    drawCanvas.startPath(x.toFloat(), y.toFloat())
                    first = false
                } else {
                    drawCanvas.addPointToPath(x.toFloat(), y.toFloat())
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

            loadFromFileAsync(this, file)
        } else {
            val intent = Intent(this, DownloadActivity::class.java)
            startActivity(intent)
        }

    }


    fun loadFromFileAsync(ctx: ViewMapActivity, file: File) = async(Dispatchers.Main) {
        textViewStatus.setText("Opening ${file.name}")
        try {

            // TODO Turn on busy indicator.

            val job = async(Dispatchers.Default) {
                //We're on a background thread here.
                //Execute blocking calls, such as retrofit call.execute().body() + caching.

                Log.v("Street", "File loading...")

                val inputStream = file.inputStream()
                val objectInputStream = ObjectInputStream(inputStream)
                ctx.streets = objectInputStream.readObject() as ArrayList<Feature>?

                Log.v("File", "loaded")
                if (ctx.streets == null) {
                    Log.v("Features:", "streets is null")
                } else {
                    Log.v("Features:", "Length=${ctx.streets!!.size}")
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


