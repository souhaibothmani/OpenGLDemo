package com.example.opengldemo;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class MyGLRenderer implements GLSurfaceView.Renderer {

    private Context context;

    // The 3 matrices we talked about in theory
    private final float[] modelMatrix = new float[16]; //position the object in the world
    private final float[] viewMatrix = new float[16]; //position the camera
    private final float[] projectionMatrix = new float[16]; //perspective (foxy . aspect , near , far)
    private final float[] mvpMatrix = new float[16]; // final combined = Projection x View x Model


    //FRAGMENT SHADER
    private java.nio.FloatBuffer texCoordBuffer;  // UV coordinates buffer
    private int diffuseTextureId;                  // GPU ID for diffuse texture
    private int normalTextureId;                   // GPU ID for normal map
    private float rotationAngle = 0f;              // current rotation angle




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
        // set background to black
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

        // enable depth testing
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // compile shaders
        shaderProgram = compileShaders();

        // load OBJ file
        ObjLoader loader = new ObjLoader(context, "craneo.OBJ");
        vertexCount = loader.positions.length / 3;

        // --- POSITION BUFFER ---
        java.nio.ByteBuffer bb = java.nio.ByteBuffer
                .allocateDirect(loader.positions.length * 4);
        bb.order(java.nio.ByteOrder.nativeOrder());
        positionBuffer = bb.asFloatBuffer();
        positionBuffer.put(loader.positions);
        positionBuffer.position(0);

        // --- NORMAL BUFFER ---
        java.nio.ByteBuffer bb2 = java.nio.ByteBuffer
                .allocateDirect(loader.normals.length * 4);
        bb2.order(java.nio.ByteOrder.nativeOrder());
        normalBuffer = bb2.asFloatBuffer();
        normalBuffer.put(loader.normals);
        normalBuffer.position(0);

        // --- TEXCOORD BUFFER ---
        java.nio.ByteBuffer bb3 = java.nio.ByteBuffer
                .allocateDirect(loader.texCoords.length * 4);
        bb3.order(java.nio.ByteOrder.nativeOrder());
        texCoordBuffer = bb3.asFloatBuffer();
        texCoordBuffer.put(loader.texCoords);
        texCoordBuffer.position(0);

        // --- LOAD TEXTURES ---
        diffuseTextureId = loadTexture("difuso_flip_oscuro.jpg");
        normalTextureId  = loadTexture("normal_flip_3.jpg");
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
        // clear screen and depth buffer
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // set up camera
        Matrix.setLookAtM(viewMatrix, 0,
                0, 0, 10,
                0, 0, 0,
                0, 1, 0);

        // model matrix → rotate a little every frame
        Matrix.setIdentityM(modelMatrix, 0);
        rotationAngle += 0.5f;  // increment angle each frame → continuous rotation
        Matrix.rotateM(modelMatrix, 0, rotationAngle, 0, 1, 0); // rotate around Y axis

        // MVP = Projection × View × Model
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, modelMatrix, 0);

        // activate shader program
        GLES20.glUseProgram(shaderProgram);

        // --- PASS POSITION ---
        int posHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition");
        GLES20.glEnableVertexAttribArray(posHandle);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, positionBuffer);

        // --- PASS NORMAL ---
        int normalHandle = GLES20.glGetAttribLocation(shaderProgram, "aNormal");
        GLES20.glEnableVertexAttribArray(normalHandle);
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer);

        // --- PASS TEXCOORD ---
        int texCoordHandle = GLES20.glGetAttribLocation(shaderProgram, "aTexCoord");
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, texCoordBuffer);

        // --- PASS MVP MATRIX ---
        int mvpHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);

        // --- PASS MODEL MATRIX ---
        int modelHandle = GLES20.glGetUniformLocation(shaderProgram, "uModelMatrix");
        GLES20.glUniformMatrix4fv(modelHandle, 1, false, modelMatrix, 0);

        // --- BIND DIFFUSE TEXTURE to slot 0 ---
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);         // activate slot 0
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, diffuseTextureId); // bind our texture
        int diffuseHandle = GLES20.glGetUniformLocation(shaderProgram, "uDiffuseTexture");
        GLES20.glUniform1i(diffuseHandle, 0);               // tell shader: slot 0

        // --- BIND NORMAL TEXTURE to slot 1 ---
        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);         // activate slot 1
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, normalTextureId);  // bind our texture
        int normalTexHandle = GLES20.glGetUniformLocation(shaderProgram, "uNormalTexture");
        GLES20.glUniform1i(normalTexHandle, 1);             // tell shader: slot 1

        // --- DRAW ---
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCount);

        // cleanup
        GLES20.glDisableVertexAttribArray(posHandle);
        GLES20.glDisableVertexAttribArray(normalHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);
    }


    //Returns the program ID , think of it like the architect that knows what to draw.
    private int compileShaders() {
        // load the shader code from the raw files as strings
        String vertexCode =
                "attribute vec4 aPosition;\n" +
                        "attribute vec3 aNormal;\n" +
                        "attribute vec2 aTexCoord;\n" +        // NEW: UV input (2 values: u,v)

                        "uniform mat4 uMVPMatrix;\n" +
                        "uniform mat4 uModelMatrix;\n" +       // NEW: model matrix for world position

                        "varying vec2 vTexCoord;\n" +          // NEW: pass UV to fragment shader
                        "varying vec3 vNormal;\n" +            // NEW: pass normal to fragment shader
                        "varying vec3 vWorldPos;\n" +          // NEW: pass world position to fragment shader

                        "void main() {\n" +
                        "  gl_Position = uMVPMatrix * aPosition;\n" +
                        "  vTexCoord = aTexCoord;\n" +         // pass UV through
                        "  vNormal = aNormal;\n" +             // pass normal through
                        "  vWorldPos = vec3(uModelMatrix * aPosition);\n" + // world position
                        "}\n";

        String fragmentCode =
                "precision mediump float;\n" +

                        "varying vec2 vTexCoord;\n" +          // received from vertex shader
                        "varying vec3 vNormal;\n" +            // received from vertex shader
                        "varying vec3 vWorldPos;\n" +          // received from vertex shader

                        "uniform sampler2D uDiffuseTexture;\n" + // diffuse texture slot
                        "uniform sampler2D uNormalTexture;\n" +  // normal map slot

                        "void main() {\n" +
                        // sample diffuse map → base color
                        "  vec4 diffuseColor = texture2D(uDiffuseTexture, vTexCoord);\n" +

                        // sample normal map → decode from [0,1] to [-1,1]
                        "  vec3 normalMap = texture2D(uNormalTexture, vTexCoord).rgb;\n" +
                        "  vec3 N = normalize(normalMap * 2.0 - 1.0);\n" +

                        // light setup
                        "  vec3 lightPos = vec3(2.0, 2.0, 2.0);\n" +  // light position in world
                        "  vec3 L = normalize(lightPos - vWorldPos);\n" + // direction to light

                        // ambient
                        "  vec3 ambient = 0.2 * diffuseColor.rgb;\n" +

                        // diffuse → N·L
                        "  float diff = max(dot(N, L), 0.0);\n" +
                        "  vec3 diffuse = diff * diffuseColor.rgb;\n" +

                        // specular (Blinn-Phong) → N·H
                        "  vec3 viewPos = vec3(0.0, 0.0, 3.0);\n" +  // camera position
                        "  vec3 V = normalize(viewPos - vWorldPos);\n" +
                        "  vec3 H = normalize(L + V);\n" +             // halfway vector
                        "  float spec = pow(max(dot(N, H), 0.0), 32.0);\n" + // shininess = 32
                        "  vec3 specular = spec * vec3(1.0, 1.0, 1.0);\n" + // white highlight

                        // final color = ambient + diffuse + specular
                        "  gl_FragColor = vec4(ambient + diffuse + specular, 1.0);\n" +
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



    /*
    1. open image file from assets (jpg/png)
    2. decode it into raw pixels (Android Bitmap)
    3. upload those pixels to GPU memory
    4. GPU gives us back an integer ID
    5. we store that ID (diffuseTextureId / normalTextureId)
    6. later in onDrawFrame we say "use texture ID X for this draw"
     */
    private int loadTexture(String filename) {
        // ask OpenGL to create an empty texture slot → returns ID
        int[] textureIds = new int[1];
        GLES20.glGenTextures(1, textureIds, 0);
        int textureId = textureIds[0];

        // bind it → "I'm working on this texture now"
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);

        // set texture filters
        // what to do when texture is stretched (magnified)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        // what to do when texture is shrunk (minified)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

        try {
            // open image file from assets as Android Bitmap
            android.graphics.Bitmap bitmap = android.graphics.BitmapFactory
                    .decodeStream(context.getAssets().open(filename));

            // upload bitmap pixels to GPU
            android.opengl.GLUtils.texImage2D(
                    GLES20.GL_TEXTURE_2D, 0, bitmap, 0);

            // free the bitmap from CPU memory (it's on GPU now)
            bitmap.recycle();

        } catch (IOException e) {
            e.printStackTrace();
        }

        return textureId; // return the GPU handle
    }

}

