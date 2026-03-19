package com.example.opengldemo;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MyGLRenderer implements GLSurfaceView.Renderer {

    private Context context;

    // The 3 matrices we talked about in theory
    private final float[] modelMatrix = new float[16]; //position the object in the world
    private final float[] viewMatrix = new float[16]; //position the camera
    private final float[] projectionMatrix = new float[16]; //perspective (foxy . aspect , near , far)
    private final float[] mvpMatrix = new float[16]; // final combined = Projection x View x Model




    private int shaderProgram;  // the compiled+linked GPU program
    private int vertexCount;    // how many vertices our OBJ has

    // ByteBuffers to send vertex data to the GPU
    private java.nio.FloatBuffer positionBuffer;
    private java.nio.FloatBuffer normalBuffer;



    public MyGLRenderer(Context context) {
        this.context = context;
    }

    /*
    onSurfaceCreated()   → called ONCE when the GL surface is ready
                         → this is where we set up shaders, load the OBJ file

    onSurfaceChanged()   → called when screen size changes (rotation etc.)
                         → this is where we set up the Projection matrix

    onDrawFrame()        → called EVERY FRAME (like 60 times per second)
                         → this is where we draw the 3D object
     */


    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // Set the background color (R, G, B, Alpha)
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f); //set background to black

        // Enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST); // compares the Z values of each fragment and just discards the ones behind.

        // compile and link our shaders → get program ID
        shaderProgram = compileShaders();

        // hardcoded triangle (3 vertices, each has x,y,z)
        /*float[] trianglePositions = {
                0.0f,  0.5f, 0.0f,   // top vertex
                -0.5f, -0.5f, 0.0f,   // bottom left
                0.5f, -0.5f, 0.0f    // bottom right
        };

        // all normals pointing toward camera (0,0,1 = forward)
        float[] triangleNormals = {
                0.0f, 0.0f, 1.0f,   // top
                0.0f, 0.0f, 1.0f,   // bottom left
                0.0f, 0.0f, 1.0f    // bottom right
        };*/

        // load OBJ file from assets folder
        ObjLoader loader = new ObjLoader(context, "Red_Triangular_Pyramid.obj");
        float[] trianglePositions = loader.positions;
        float[] triangleNormals   = loader.normals;

        // number of vertices = total floats / 3 (because each vertex has x,y,z)
        vertexCount = loader.positions.length / 3;

        // how many vertices do we have?
        vertexCount = 3;

        // copy positions into native memory (FloatBuffer)
        java.nio.ByteBuffer bb = java.nio.ByteBuffer.allocateDirect(trianglePositions.length * 4);
        bb.order(java.nio.ByteOrder.nativeOrder());
        positionBuffer = bb.asFloatBuffer();
        positionBuffer.put(trianglePositions);
        positionBuffer.position(0); // rewind to start

        // same for normals
        java.nio.ByteBuffer bb2 = java.nio.ByteBuffer.allocateDirect(triangleNormals.length * 4);
        bb2.order(java.nio.ByteOrder.nativeOrder());
        normalBuffer = bb2.asFloatBuffer();
        normalBuffer.put(triangleNormals);
        normalBuffer.position(0); // rewind to start
    }


    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        // Set the viewport (tell OpenGL the size of the screen)
        GLES20.glViewport(0, 0, width, height); //tells "screen starts at 0,0 and is this wide and tall"

        // Calculate aspect ratio
        float aspect = (float) width / height; //needed this to understand the ratio so we -> the bigger this and the bigger the frustum

        // Build the projection matrix
        Matrix.perspectiveM(projectionMatrix, 0,
                45.0f,           //45.0f → fovy (field of view, 45 degrees)
                aspect,               //aspect → width/height
                0.1f,                 //0.1f → near clipping plane
                100.0f);              //100.0f → far clipping plane

    }

    @Override
    public void onDrawFrame(GL10 unused) {
        // clear screen and depth buffer every frame
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // set up camera (view matrix)
        Matrix.setLookAtM(viewMatrix, 0,
                0, 0, 0.9f,    // camera position
                0, 0, 0,    // look at origin
                0, 1, 0);   // up is Y axis

        // model matrix → identity matrix (object stays at origin, no rotation/scale)
        Matrix.setIdentityM(modelMatrix, 0);

        // MVP = Projection x View x Model (order matters!)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, modelMatrix, 0);

        // tell GPU to use our compiled shader program
        GLES20.glUseProgram(shaderProgram);
        Matrix.rotateM(modelMatrix, 0, 45f, 1, 0, 0);  // rotate 45° around X axis

        // get the location of aPosition variable in the shader
        int posHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition");
        GLES20.glEnableVertexAttribArray(posHandle);
        // send position data to the shader
        // 3 = x,y,z per vertex | false = not normalized | 0 = no stride | positionBuffer = the data
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, positionBuffer);

        // same for normals
        int normalHandle = GLES20.glGetAttribLocation(shaderProgram, "aNormal");
        GLES20.glEnableVertexAttribArray(normalHandle);
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer);

        // get location of uMVPMatrix in the shader and send it
        int mvpHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);

        // DRAW! GL_TRIANGLES = draw triangles | 0 = start at first vertex | vertexCount = draw 3 vertices
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        // disable the attribute arrays when done
        GLES20.glDisableVertexAttribArray(posHandle);
        GLES20.glDisableVertexAttribArray(normalHandle);
    }


    //Returns the program ID , think of it like the architect that knows what to draw.
    private int compileShaders() {
        // load the shader code from the raw files as strings
        String vertexCode =
                "attribute vec4 aPosition;\n" +
                        "attribute vec3 aNormal;\n" +
                        "uniform mat4 uMVPMatrix;\n" +
                        "void main() {\n" +
                        "  gl_Position = uMVPMatrix * aPosition;\n" +
                        "}\n";

        String fragmentCode =
                "precision mediump float;\n" +
                        "void main() {\n" +
                        "  gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);\n" +
                        "}\n";

        /*
        Worker 1 (vertex shader)   → handles GEOMETRY
                             "where does each vertex go in clip space?"
                             runs once per VERTEX

        Worker 2 (fragment shader)  → handles COLOR
                             "what color is each pixel?"
                             runs once per FRAGMENT (pixel candidate)
         */

        // compile vertex shader
        int vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER); //ask OpenGL to create an empty shader slot , returns an ID
        GLES20.glShaderSource(vertexShader, vertexCode); //sends the GLSL string code to that slot
        GLES20.glCompileShader(vertexShader); //GPU compiles it

        // compile fragment shader
        int fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fragmentShader, fragmentCode);
        GLES20.glCompileShader(fragmentShader);

        // link both shaders into one program
        int program = GLES20.glCreateProgram(); //creates an empty program slot
        GLES20.glAttachShader(program, vertexShader); //attaches both compiled shaders to the program
        GLES20.glAttachShader(program, fragmentShader); //attaches both compiled shaders to the program
        GLES20.glLinkProgram(program); //links them together

        return program;
    }

}

