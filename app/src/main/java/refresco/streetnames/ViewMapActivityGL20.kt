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
import java.io.File
import java.io.ObjectInputStream
import android.opengl.GLSurfaceView
import android.opengl.GLES20;
import android.opengl.Matrix
import android.os.Parcel
import android.os.Parcelable
import android.view.GestureDetector
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import refresco.streetnames.LineDrawer.Companion.DEFAULTCOLORINDEX
import refresco.streetnames.geo.LineCollection
import refresco.streetnames.geo.StreetCollection


class ViewMapActivityGL20 : Activity() {

    private var streetCollection: StreetCollection? = null
    fun getStreetCollection(): StreetCollection {
        return streetCollection!!
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
        val streets = getStreetCollection()

        val black = floatArrayOf(0.0f, 0.0f, 0.0f, 1.0f)
        val red = floatArrayOf(1f, 0f, 0f, 1.0f)
        val blue = floatArrayOf(0f, 0f, 1f, 1.0f)
        val colors: HashMap<String, FloatArray> = hashMapOf(DEFAULTCOLORINDEX to black)

        colors.put("Hauptstraße", red)
        colors.put("Plöck", blue)

        val mRenderer = GLRenderer20(getStreetCollection(), colors)

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

    class MyGLSurfaceView(context: Context, val mRenderer: GLRenderer20) : GLSurfaceView(context) {
        // Viewport extremes. See mCurrentViewport for a discussion of the viewport.

        private var screenWidth = -1
        private var screenHeight = -1

        private var mScaleFactor = 1f

        init {

            // Create an OpenGL ES 2.0 context
            setEGLContextClientVersion(2)

            // Set the Renderer for drawing on the GLSurfaceView
            setRenderer(mRenderer)

            mScaleFactor = mRenderer.zoom

            setPreserveEGLContextOnPause(true)

            renderMode = GLSurfaceView.RENDERMODE_WHEN_DIRTY
        }

        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)

            screenWidth = w
            screenHeight = h
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
        private val mScaleGestureDetector = ScaleGestureDetector(context, scaleListener)

        private val mGestureListener = object : GestureDetector.SimpleOnGestureListener() {

            override fun onDown(e: MotionEvent): Boolean {
                /*releaseEdgeEffects()
                mScrollerStartViewport.set(mCurrentViewport)
                mScroller.forceFinished(true)
                //ViewCompat.postInvalidateOnAnimation(InteractiveLineGraphView.this)
                */
                return true;
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                /*
                mZoomer.forceFinished(true)
                if (hitTest(e.getX(), e.getY(), mZoomFocalPoint)) {
                    mZoomer.startZoom(ZOOM_AMOUNT)
                }
                */
                //ViewCompat.postInvalidateOnAnimation(InteractiveLineGraphView.this)
                return true;
            }

            override fun onScroll(
                e1: MotionEvent,
                e2: MotionEvent,
                distanceX: Float,
                distanceY: Float
            ): Boolean {

                Log.v("setViewport", "distanceX = $distanceX, distanceY = $distanceY");
                Log.v("setViewport", "mScaleFactor = $mScaleFactor");
                val x = distanceX / screenWidth * 3f / mScaleFactor
                val y = distanceY / screenHeight * 3f / mScaleFactor

                mRenderer.eyeX -= x
                mRenderer.eyeY -= y

                requestRender()


                Log.v("setViewport", "x = $x, y = $y");
                Log.v("setViewport", "w = $screenWidth, h = $screenHeight");
                Log.v("setViewport", " ");
                return true
            }
        }

        private val mGestureDetector = GestureDetector(context, mGestureListener)


        override fun onTouchEvent(event: MotionEvent): Boolean {
            var retVal = mScaleGestureDetector.onTouchEvent(event);
            retVal = mGestureDetector.onTouchEvent(event) || retVal;
            return retVal || super.onTouchEvent(event);
        }


        override fun onSaveInstanceState(): Parcelable {
            val superState = super.onSaveInstanceState()
            val ss = SavedState(superState)
            ss.eyeX = mRenderer.eyeX
            ss.eyeY = mRenderer.eyeY
            ss.zoom = mRenderer.zoom

            return ss
        }

        override fun onRestoreInstanceState(state: Parcelable?) {
            if (state !is SavedState) {
                super.onRestoreInstanceState(state)
                return
            }
            super.onRestoreInstanceState(state.getSuperState())

            // TODO when is this method acutally called?
            mRenderer.eyeX = state.eyeX
            mRenderer.eyeY = state.eyeY
            mRenderer.zoom = state.zoom
            mScaleFactor = state.zoom
            Log.v("onRestoreInstanceState", "eyeX = ${state.eyeX}")
        }


        class SavedState(superState: Parcelable) : BaseSavedState(superState) {
            var eyeX: Float = 0f
            var eyeY: Float = 0f
            var zoom: Float = 1f

            constructor (parcelIn: Parcel) : this(parcelIn as Parcelable) {
                eyeX = parcelIn.readFloat()
                eyeY = parcelIn.readFloat()
                zoom = parcelIn.readFloat()
            }

            override fun writeToParcel(out: Parcel?, flags: Int) {
                super.writeToParcel(out, flags)
                out?.writeFloat(eyeX)
                out?.writeFloat(eyeY)
                out?.writeFloat(zoom)
            }
        }

        /**
         * Persistent state that is saved by InteractiveLineGraphView.

        public static class SavedState extends BaseSavedState {
        private RectF viewport;
        public SavedState(Parcelable superState) {
        super(superState);
        }
        @Override
        public void writeToParcel(Parcel out, int flags) {
        super.writeToParcel(out, flags);
        out.writeFloat(viewport.left);
        out.writeFloat(viewport.top);
        out.writeFloat(viewport.right);
        out.writeFloat(viewport.bottom);
        }
        @Override
        public String toString() {
        return "InteractiveLineGraphView.SavedState{"
        + Integer.toHexString(System.identityHashCode(this))
        + " viewport=" + viewport.toString() + "}";
        }
        public static final Parcelable.Creator<SavedState> CREATOR
        = ParcelableCompat.newCreator(new ParcelableCompatCreatorCallbacks<SavedState>() {
        @Override
        public SavedState createFromParcel(Parcel in, ClassLoader loader) {
        return new SavedState(in);
        }
        @Override
        public SavedState[] newArray(int size) {
        return new SavedState[size];
        }
        });
        SavedState(Parcel in) {
        super(in);
        viewport = new RectF(in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat());
        }
        }
         */
    }
}

const val DEBUGGL20 = true

abstract class GLRenderer : GLSurfaceView.Renderer {

    protected var mFirstDraw: Boolean = false
    protected var mSurfaceCreated: Boolean = false
    var mWidth: Int = 0
    var mHeight: Int = 0
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

class GLRenderer20(var streetCollection: StreetCollection, val colors: HashMap<String, FloatArray>) : GLRenderer() {
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

    var eyeX = 0f
    var eyeY = 0f


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

        mLineDrawer = LineDrawer(streetCollection, colors)

    }

    override fun onSurfaceChanged(notUsed: GL10, width: Int, height: Int) {
        super.onSurfaceChanged(notUsed, width, height)

        GLES20.glViewport(0, 0, width, height)

        ratio = width.toFloat() / height.toFloat()
    }

    override fun onDrawFrame(firstDraw: Boolean) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)


        Matrix.frustumM(mProjectionMatrix, 0, -ratio / zoom, ratio / zoom, -1f / zoom, 1f / zoom, 1f, 25f)

        // Set the camera position (View matrix)
        Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, -3f, eyeX, eyeY, 0f, 0f, 1.0f, 0.0f)


        // Calculate the projection and view transformation
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mViewMatrix, 0)


        mLineDrawer.draw(mMVPMatrix)
    }


}


class LineDrawer(var streetCollection: StreetCollection, val colors: HashMap<String, FloatArray>) {
    companion object {
        val DEFAULTCOLORINDEX = "<DEFAULTCOLOR>"
        val COORDS_PER_VERTEX = 3
    }

    private var vertexBuffers: HashMap<String, FloatBuffer>
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
        vertexBuffers = HashMap<String, FloatBuffer>(streetCollection.streets.size)
        for ((name, lineCollection) in streetCollection.streets) {

            vertexBuffers.put(name,
                ByteBuffer.allocateDirect(lineCollection.totalSize * java.lang.Float.BYTES).run {
                    // use the device hardware's native byte order
                    order(ByteOrder.nativeOrder())

                    // create a floating point buffer from the ByteBuffer
                    asFloatBuffer().apply {
                        // add the coordinates to the FloatBuffer
                        for (line in lineCollection.lines) {
                            put(line.lineCoords)
                        }
                        // set the buffer to read the first coordinate
                        position(0)
                    }
                })
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
        for ((name, lineCollection) in streetCollection.streets) {


            // Set color for drawing the triangle
            if (colors.containsKey(name)) {// Custom color
                if (colors.get(name) != lastColor) { // Custom color not set yet
                    lastColor = colors.get(name)
                    GLES20.glUniform4fv(mColorHandle, 1, lastColor, 0)
                }
            } else { // default color
                if (colors.get(DEFAULTCOLORINDEX) != lastColor) { // Default color not set yet
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
                vertexBuffers[name]
            )

            var offset = 0
            for (line in lineCollection.lines) {
                // Draw the triangle
                GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, offset, line.lineCoords.size / COORDS_PER_VERTEX)
                offset += line.lineCoords.size / COORDS_PER_VERTEX
            }

        }


        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle)

    }


}





