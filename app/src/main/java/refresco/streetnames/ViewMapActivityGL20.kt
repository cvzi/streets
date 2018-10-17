package refresco.streetnames

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import android.opengl.GLSurfaceView
import android.opengl.GLES20;
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import android.view.MotionEvent
import android.view.ScaleGestureDetector


class ViewMapActivityGL20 : Activity() {

    private var streets: ArrayList<Feature>? = null
    fun getStreets(): ArrayList<Feature> {
        return streets!!
    }

    var surfaceView: GLSurfaceView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_view_map_gl20)

        val sharedPref: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val currentRegionPref: String = sharedPref.getString(SETTINGS_KEY_PREF_CURRENT_REGION, "")


        val textViewStatus = findViewById<TextView>(R.id.textViewStatus)

        if (hasGLES20()) {

            textViewStatus.setText("Activity loaded")

            openRegion(currentRegionPref)
        } else {
            textViewStatus.setText("OpenGL ES 2.0 not available on this device")
        }

    }

    override fun onResume() {
        super.onResume()

        surfaceView?.onResume()
    }

    override fun onPause() {
        super.onPause()

        surfaceView?.onPause()
    }


    fun hasGLES20(): Boolean {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager;
        val info = am.getDeviceConfigurationInfo();
        return info.reqGlEsVersion >= 0x20000;
    }


    fun drawMap() {
        initialize()

        setContentView(surfaceView);

    }


    fun initialize() {
        val streets = getStreets()

        var minLat = 999.0
        var maxLat = 0.0
        var minLng = 999.0
        var maxLng = 0.0

        Log.i("drawMap", "initialize()")

        for (i in 0 until streets.size) {
            for (c in streets.get(i).coordinates) {
                minLat = min(c.latitude, minLat)
                maxLat = max(c.latitude, maxLat)
                minLng = min(c.longitude, minLng)
                maxLng = max(c.longitude, maxLng)
            }
        }

        Log.i("drawMap", "min/max calculated")

        val cosPhi0 = cos((maxLng - minLng) / 2.0);


        val width = 1.0
        val height = width / cosPhi0
        Log.i("drawMap", "width = $width , height = $height")

        val lngFactor = width / (maxLng - minLng)
        val latFactor = height / (maxLat - minLat)

        Log.i("drawMap", "lngFactor calculated")


        val black = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)
        val red = floatArrayOf(1f, 0f, 0f, 1.0f)
        val colors: HashMap<Int, FloatArray> = hashMapOf(LineDrawer.DEFAULTCOLORINDEX to black)

        val allLines = Array<Line>(streets.size) { i ->
            val f = streets.get(i)

            var lineCoords = FloatArray(f.coordinates.size * LineDrawer.COORDS_PER_VERTEX)
            for (index in 0 until f.coordinates.size) {
                var c = f.coordinates[index]
                val x = (width * 0.5) - (lngFactor * (c.longitude - minLng))
                val y = (latFactor * (c.latitude - minLat)) - (height * 0.5)
                lineCoords[index * LineDrawer.COORDS_PER_VERTEX] = x.toFloat()
                lineCoords[index * LineDrawer.COORDS_PER_VERTEX + 1] = y.toFloat()
                lineCoords[index * LineDrawer.COORDS_PER_VERTEX + 2] = 0f
            }

            if (f.properties.containsKey("name") && f.properties.get("name")!!.contains("Hauptstraße")) {
                colors.put(i, red)
            }


            Line(lineCoords)
        }

        val mRenderer = GLRenderer20(allLines, colors)

        surfaceView = MyGLSurfaceView(this, mRenderer)

    }


    fun openRegion(filename: String) {

        textViewStatus.setText("Open $filename")

        val outfile = "$filename.ser"

        val file = File(this.getCacheDir(), outfile)

        if (file.exists()) {
            textViewStatus.setText("Exists $filename")

            loadFromFileAsync(file, this)
        } else {
            val intent = Intent(this, DownloadActivity::class.java)
            startActivity(intent)
        }

    }


    fun loadFromFileAsync(file: File, activity: ViewMapActivityGL20) = async(Dispatchers.Main) {

        try {
            textViewStatus.setText("Opening ${file.name}")
            // TODO Turn on busy indicator.

            val job = async(Dispatchers.Default) {
                //We're on a background thread here.
                //Execute blocking calls, such as retrofit call.execute().body() + caching.

                Log.v("Street", "File loading...")

                val inputStream = file.inputStream()
                val objectInputStream = ObjectInputStream(inputStream)
                activity.streets = objectInputStream.readObject() as ArrayList<Feature>?

                Log.v("File", "loaded")
                if (activity.streets == null) {
                    Log.v("Features:", "streets is null")
                } else {
                    Log.v("Features:", "Length=${activity.streets!!.size}")
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

    class MyGLSurfaceView(context: Context, val mRenderer: GLRenderer20) : GLSurfaceView(context) {
        private var mScaleFactor: Float

        init {

            // Create an OpenGL ES 2.0 context
            setEGLContextClientVersion(2)

            // Set the Renderer for drawing on the GLSurfaceView
            setRenderer(mRenderer)

            mScaleFactor = mRenderer.zoom

            setPreserveEGLContextOnPause(true)

            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        private val scaleListener = object : ScaleGestureDetector.SimpleOnScaleGestureListener() {

            override fun onScale(detector: ScaleGestureDetector): Boolean {
                mScaleFactor *= detector.scaleFactor

                // Don't let the object get too small or too large.
                mScaleFactor = Math.max(GLRenderer20.MIN_ZOOM, Math.min(mScaleFactor, GLRenderer20.MAX_ZOOM))

                mRenderer.zoom = mScaleFactor

                requestRender()
                return true
            }
        }
        private val mScaleDetector = ScaleGestureDetector(context, scaleListener)



        override fun onTouchEvent(event: MotionEvent): Boolean {
            // Let the ScaleGestureDetector inspect all events.
            if(mScaleDetector.onTouchEvent(event)) {
                return true
            }
            return super.onTouchEvent(event)
        }


    }
}

const val DEBUGGL20 = true

abstract class GLRenderer : GLSurfaceView.Renderer {

    protected var mFirstDraw: Boolean = false
    protected var mSurfaceCreated: Boolean = false
    protected var mWidth: Int = 0
    protected var mHeight: Int = 0
    protected var mLastTime: Long = 0
    var fps: Int = 0
        private set

    init {
        mFirstDraw = true
        mSurfaceCreated = false
        mWidth = -1
        mHeight = -1
        mLastTime = System.currentTimeMillis()
        fps = 0
    }


    override fun onSurfaceCreated(
        notUsed: GL10?,
        config: javax.microedition.khronos.egl.EGLConfig?
    ) {
        Log.i("gl20", "Surface created.")
        mSurfaceCreated = true
        mWidth = -1
        mHeight = -1
    }

    override fun onSurfaceChanged(
        notUsed: GL10, width: Int,
        height: Int
    ) {
        if (!mSurfaceCreated && width == mWidth
            && height == mHeight
        ) {
            Log.i(
                "gl20",
                "Surface changed but already handled."
            )
            return
        }


        mWidth = width
        mHeight = height

        onCreate(mWidth, mHeight, mSurfaceCreated)
        mSurfaceCreated = false
    }


    override fun onDrawFrame(notUsed: GL10) {
        onDrawFrame(mFirstDraw)

        if (DEBUGGL20) {
            fps++
            val currentTime = System.currentTimeMillis()
            if (currentTime - mLastTime >= 1000) {
                fps = 0
                mLastTime = currentTime
            }
        }

        if (mFirstDraw) {
            mFirstDraw = false
        }
    }

    abstract fun onCreate(
        width: Int, height: Int,
        contextLost: Boolean
    )

    abstract fun onDrawFrame(firstDraw: Boolean)

}

fun String.loadShader(type: Int): Int {

    // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
    // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
    return GLES20.glCreateShader(type).also { shader ->

        // add the source code to the shader and compile it
        GLES20.glShaderSource(shader, this)
        GLES20.glCompileShader(shader)
    }
}

class GLRenderer20(val lines: Array<Line>, val colors: HashMap<Int, FloatArray>) : GLRenderer() {
    companion object {
        val MIN_ZOOM = 1.0f
        val MAX_ZOOM = 30.0f
    }

    private lateinit var mLineDrawer: LineDrawer

    private val mMVPMatrix = FloatArray(16)
    private val mProjectionMatrix = FloatArray(16)
    private val mViewMatrix = FloatArray(16)

    private var ratio = 1f
    var zoom = 4f


    override fun onCreate(
        width: Int, height: Int,
        contextLost: Boolean
    ) {
        GLES20.glClearColor(1f, 1f, 1f, 1f)

    }

    override fun onSurfaceCreated(notUsed: GL10?, config: EGLConfig?) {
        super.onSurfaceCreated(notUsed, config)

        val lineColor = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)
        var coords = floatArrayOf(     // in counterclockwise order:
            0.0f, 0.622008459f, 0.0f,      // top
            -0.5f, -0.311004243f, 0.0f,    // bottom left
            0.5f, -0.311004243f, 0.0f      // bottom right
        )
        var coords2 = floatArrayOf(     // in counterclockwise order:
            0.0f, 0.622008459f, 0.0f,      // top
            -0.6f, -0.411004243f, 0.0f,    // bottom left
            0.6f, -0.411004243f, 0.0f      // bottom right
        )

        var coords3 = floatArrayOf(     // in counterclockwise order:
            0.0f, 0.9f, 0.0f,      // top
            -0.7f, -0.211004243f, 0.0f,    // bottom left
            0.7f, -0.211004243f, 0.0f      // bottom right
        )

        //val lines: Array<Line> = arrayOf(Line(coords), Line(coords2))

        mLineDrawer = LineDrawer(lines, colors)

    }

    override fun onSurfaceChanged(notUsed: GL10, width: Int, height: Int) {
        super.onSurfaceChanged(notUsed, width, height)

        GLES20.glViewport(0, 0, width, height)

        ratio = width.toFloat() / height.toFloat()
    }

    override fun onDrawFrame(firstDraw: Boolean) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)


        Matrix.frustumM(mProjectionMatrix, 0, -ratio/zoom, ratio/zoom, -1f/zoom, 1f/zoom, 1f, 25f)

        // Set the camera position (View matrix)
        Matrix.setLookAtM(mViewMatrix, 0, 0f, 0f, -3f, 0f, 0f, 0f, 0f, 1.0f, 0.0f)


        // Calculate the projection and view transformation
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0)


        mLineDrawer.draw(mMVPMatrix)
    }


}


class Line(val lineCoords: FloatArray) {


}


class LineDrawer(var lines: Array<Line>, val colors: HashMap<Int, FloatArray>) {
    companion object {
        val DEFAULTCOLORINDEX = -1
        val COORDS_PER_VERTEX = 3
    }

    private var vertexBuffers: Array<FloatBuffer>
    private var mProgram: Int

    private val vertexShaderCode =
    // This matrix member variable provides a hook to manipulate
    // the coordinates of the objects that use this vertex shader
        "uniform mat4 uMVPMatrix;" +
                "attribute vec4 vPosition;" +
                "void main() {" +
                // the matrix must be included as a modifier of gl_Position
                // Note that the uMVPMatrix factor *must be first* in order
                // for the matrix multiplication product to be correct.
                "  gl_Position = uMVPMatrix * vPosition;" +
                "}"


    private val fragmentShaderCode =
        "precision mediump float;" +
                "uniform vec4 vColor;" +
                "void main() {" +
                "  gl_FragColor = vColor;" +
                "}"


    private var mPositionHandle: Int = 0
    private var mMVPMatrixHandle: Int = 0
    private var mColorHandle: Int = 0


    private val vertexStride: Int = COORDS_PER_VERTEX * java.lang.Float.BYTES // 4 bytes per vertex




    init {
        vertexBuffers = Array(lines.size) { index ->

            ByteBuffer.allocateDirect(lines[index].lineCoords.size * java.lang.Float.BYTES ).run {
                // use the device hardware's native byte order
                order(ByteOrder.nativeOrder())

                // create a floating point buffer from the ByteBuffer
                asFloatBuffer().apply {
                    // add the coordinates to the FloatBuffer
                    put(lines[index].lineCoords)
                    // set the buffer to read the first coordinate
                    position(0)
                }
            }
        }


        val vertexShader: Int = vertexShaderCode.loadShader(GLES20.GL_VERTEX_SHADER)
        val fragmentShader: Int = fragmentShaderCode.loadShader(GLES20.GL_FRAGMENT_SHADER)

        // create empty OpenGL ES Program
        mProgram = GLES20.glCreateProgram().also {

            // add the vertex shader to program
            GLES20.glAttachShader(it, vertexShader)

            // add the fragment shader to program
            GLES20.glAttachShader(it, fragmentShader)

            // creates OpenGL ES program executables
            GLES20.glLinkProgram(it)


        }
    }


    fun add(line: Line) {
        lines = lines.plus(line)
        vertexBuffers = vertexBuffers.plus(
            ByteBuffer.allocateDirect(line.lineCoords.size * 4).run {
                // use the device hardware's native byte order
                order(ByteOrder.nativeOrder())

                // create a floating point buffer from the ByteBuffer
                asFloatBuffer().apply {
                    // add the coordinates to the FloatBuffer
                    put(line.lineCoords)
                    // set the buffer to read the first coordinate
                    position(0)
                }
            })
    }


    fun draw(mvpMatrix: FloatArray) {
        // Add program to OpenGL ES environment
        GLES20.glUseProgram(mProgram)

        // get handle to fragment shader's vColor member
        mColorHandle = GLES20.glGetUniformLocation(mProgram, "vColor")

        // get handle to shape's transformation matrix
        mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgram, "uMVPMatrix")
        // Pass the projection and view transformation to the shader
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mvpMatrix, 0)

        // get handle to vertex shader's vPosition member
        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "vPosition")

        // Enable a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(mPositionHandle)


        var lastColor = colors.get(DEFAULTCOLORINDEX)
        GLES20.glUniform4fv(mColorHandle, 1, lastColor, 0)
        for (index in 0 until lines.size) {


            // Set color for drawing the triangle
            if (colors.containsKey(index)) { // New color
                if (colors.get(index) != lastColor) {
                    lastColor = colors.get(index)
                    GLES20.glUniform4fv(mColorHandle, 1, lastColor, 0)
                }
            } else {
                if (colors.get(DEFAULTCOLORINDEX) != lastColor) { // Default color, index=-1
                    lastColor = colors.get(DEFAULTCOLORINDEX)
                    GLES20.glUniform4fv(mColorHandle, 1, lastColor, 0)
                }
            }

            // Prepare the triangle coordinate data
            GLES20.glVertexAttribPointer(
                mPositionHandle,
                COORDS_PER_VERTEX,
                GLES20.GL_FLOAT,
                false,
                vertexStride,
                vertexBuffers[index]
            )

            // Draw the triangle
            GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, lines[index].lineCoords.size / COORDS_PER_VERTEX)

        }


        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle)

    }


}





