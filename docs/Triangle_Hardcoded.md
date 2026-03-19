# OpenGL ES — Hardcoded Triangle Recap

## Project Architecture

```
MainActivity.java        → entry point, sets up the GL canvas
MyGLSurfaceView.java     → the GL canvas (extends GLSurfaceView)
MyGLRenderer.java        → all OpenGL logic (implements GLSurfaceView.Renderer)
ObjLoader.java           → reads OBJ file into float[] arrays
vertex_shader.glsl       → GPU program: transforms geometry
fragment_shader.glsl     → GPU program: colors pixels (stub)
```

---

## MainActivity.java

```
onCreate()   →  entry point of the app
                checks device supports OpenGL ES 2.0 (0x20000)
                creates MyGLSurfaceView
                makes it fill the whole screen via setContentView()

onResume()   →  forwards Android lifecycle to GL canvas
onPause()    →  forwards Android lifecycle to GL canvas
```

> `setContentView(glSurfaceView)` bypasses XML layouts entirely — OpenGL owns the whole screen.

---

## MyGLSurfaceView.java

```
constructor  →  extends Android's GLSurfaceView
                setEGLContextClientVersion(2) → use OpenGL ES 2.0
                setRenderer(new MyGLRenderer()) → attach the painter
```

> Think of it like: GLSurfaceView = canvas, MyGLRenderer = painter, MyGLSurfaceView = setting up the canvas and handing it to the painter.

---

## ObjLoader.java

```
constructor  →  opens OBJ file from assets folder (context.getAssets())
                reads line by line with BufferedReader:

                   "v  x y z"   → parse x,y,z → store in vList
                   "vn x y z"   → parse nx,ny,nz → store in vnList
                   "f v//vn ..."  → for each of 3 vertices:
                                    look up vList[vIndex]
                                    look up vnList[vnIndex]
                                    append to tempPositions, tempNormals

                converts ArrayList<Float> → float[]
                (OpenGL needs raw float[], not Java Lists)

output:
   float[] positions  →  [x,y,z, x,y,z, x,y,z, ...]  one set per vertex
   float[] normals    →  [nx,ny,nz, nx,ny,nz, ...]    one set per vertex
```

### OBJ File Format
```
v  0.5 -0.3  0.8    ← vertex position (index 1)
vn 0.0  0.0  1.0    ← vertex normal   (index 1)
f  1//1  2//1  3//1 ← triangle: vertex 1 + normal 1, vertex 2 + normal 1, vertex 3 + normal 1
```
> The face line connects the dots into triangles. Without `f` lines, OpenGL just has a cloud of points.

---

## MyGLRenderer.java

### Key Fields
```java
float[] modelMatrix      // places object in world space
float[] viewMatrix       // positions the camera
float[] projectionMatrix // perspective (fovy, aspect, near, far)
float[] mvpMatrix        // final combined = Projection × View × Model

int shaderProgram        // integer ID of the compiled+linked GPU program
int vertexCount          // how many vertices to draw

FloatBuffer positionBuffer  // vertex positions in native memory (for GPU)
FloatBuffer normalBuffer    // vertex normals in native memory (for GPU)
```

---

### compileShaders()
```
→ write vertex shader code as a String
→ write fragment shader code as a String
→ glCreateShader()   → reserve slot on GPU, get ID
→ glShaderSource()   → upload GLSL string to that slot
→ glCompileShader()  → GPU compiles it (like javac but for GPU)
→ glCreateProgram()  → create empty program container
→ glAttachShader()   → attach vertex shader to program
→ glAttachShader()   → attach fragment shader to program
→ glLinkProgram()    → link them so they can communicate
→ return program ID  → integer handle we use later with glUseProgram()
```

> The program is **instructions** (how to draw), not the object data itself.
> OpenGL uses integer IDs for everything — you never hold the actual GPU object, just a reference.

---

### onSurfaceCreated() — runs ONCE
```
→ glClearColor(0,0,0,1)       set background to black
→ glEnable(GL_DEPTH_TEST)     discard fragments hidden behind others
→ shaderProgram = compileShaders()   compile shaders once at startup
→ define hardcoded triangle:
     top vertex      ( 0.0,  0.5, 0.0)
     bottom left     (-0.5, -0.5, 0.0)
     bottom right    ( 0.5, -0.5, 0.0)
→ all normals pointing forward (0, 0, 1)
→ vertexCount = 3
→ copy float[] → FloatBuffer (native memory the GPU can access)
     ByteBuffer.allocateDirect(length * 4)  ← 4 bytes per float
     ByteOrder.nativeOrder()                ← match device CPU byte order
     position(0)                            ← rewind to start for GPU
```

---

### onSurfaceChanged() — runs when screen size is known
```
→ glViewport(0, 0, width, height)   map NDC (-1 to 1) to screen pixels
→ aspect = width / height           needed for frustum shape
→ Matrix.perspectiveM(projectionMatrix,
       45.0f,    ← fovy: field of view angle
       aspect,   ← screen width/height ratio
       0.1f,     ← near clip plane
       100.0f)   ← far clip plane
```

> This is where we build the projection matrix — we need width and height first, which only become available here.

---

### onDrawFrame() — runs 60× per second
```
→ glClear(COLOR | DEPTH)         wipe screen + reset depth buffer
→ Matrix.setLookAtM(viewMatrix,
       0, 0, 3,   ← camera at z=3
       0, 0, 0,   ← looking at origin
       0, 1, 0)   ← Y axis is up
→ Matrix.setIdentityM(modelMatrix)   object stays at origin (no transform)
→ mvpMatrix = projection × view      step 1
→ mvpMatrix = mvpMatrix  × model     step 2
→ glUseProgram(shaderProgram)        activate our GPU program
→ glGetAttribLocation("aPosition")   find aPosition variable in shader
→ glEnableVertexAttribArray()        activate it
→ glVertexAttribPointer(3 floats)    connect positionBuffer → aPosition
→ same for aNormal + normalBuffer
→ glGetUniformLocation("uMVPMatrix") find uMVPMatrix in shader
→ glUniformMatrix4fv(mvpMatrix)      send MVP matrix to GPU
→ glDrawArrays(GL_TRIANGLES, 0, 3)  ← THE DRAW CALL 🎯
→ glDisableVertexAttribArray()       cleanup
```

---

## vertex_shader.glsl

```glsl
attribute vec4 aPosition;   // input: per-vertex position (x,y,z,w)
attribute vec3 aNormal;     // input: per-vertex normal (nx,ny,nz)
uniform mat4 uMVPMatrix;    // input: MVP matrix (same for all vertices)

void main() {
    gl_Position = uMVPMatrix * aPosition;  // transform to clip space
}
```

| Keyword     | Meaning |
|-------------|---------|
| `attribute` | different per vertex (position, normal) |
| `uniform`   | same for all vertices (MVP matrix) |
| `vec4`      | x,y,z,w — needs 4th dimension for matrix math + perspective |
| `vec3`      | x,y,z — normals are directions, no perspective needed |
| `gl_Position` | built-in output — must be set — clip space position |

---

## fragment_shader.glsl

```glsl
precision mediump float;   // medium precision for mobile GPU

void main() {
    gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0);  // white
}
```

> Stub only — colleague will implement Phong lighting here using the normals.

---

## The Full Pipeline

```
OBJ file
   v  → positions
   vn → normals       →  ObjLoader  →  float[] arrays
   f  → triangles
                               ↓
                      onSurfaceCreated
                      float[] → FloatBuffer (native memory)
                               ↓
                      onDrawFrame (60× per second)
                      glDrawArrays → triggers GPU
                               ↓
                      Vertex Shader (per vertex)
                      gl_Position = MVP × position
                               ↓
                      Rasterizer (automatic)
                      fills triangles → generates fragments
                      interpolates normals between vertices
                               ↓
                      Fragment Shader (per fragment)
                      gl_FragColor = white (for now)
                               ↓
                      Depth Test
                      discard hidden fragments
                               ↓
                      Screen 🎉
```

---

## Coordinate Space Journey

```
local space   →  ×Model  →  world space    (place object in scene)
world space   →  ×View   →  camera space   (apply camera position)
camera space  →  ×Proj   →  clip space     (apply perspective, w component)
clip space    →  ÷w      →  NDC (-1 to 1)  (perspective divide, GPU does this)
NDC           →  viewport →  screen pixels  (glViewport maps this)
```

---

## Key Concepts

| Concept | Explanation |
|---------|-------------|
| Shader | A small program that runs on the GPU |
| Vertex Shader | Runs once per vertex — handles geometry |
| Fragment Shader | Runs once per fragment — handles color |
| Fragment | Candidate pixel before depth test |
| Uniform | Same value for all vertices (e.g. MVP matrix) |
| Attribute | Different value per vertex (e.g. position, normal) |
| FloatBuffer | Native memory bridge between Java and GPU |
| Program ID | Integer handle to compiled+linked shaders on GPU |
| Depth Test | Discards fragments hidden behind others |
| Identity Matrix | 4×4 matrix that does nothing (like multiplying by 1) |
| w component | 4th dimension — enables translation in matrix math + perspective divide |
