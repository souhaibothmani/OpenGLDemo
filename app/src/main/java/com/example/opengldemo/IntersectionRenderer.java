package com.example.opengldemo;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * IntersectionRenderer - Renders ray intersection tests with OpenGL ES 2.0.
 *
 * This is a SIMPLE renderer compared to Session 1-2:
 * - No textures, no normal maps, no Phong shading
 * - Just flat-colored lines and points
 * - Draws: ray (line), primitives (wireframes), hit points (dots), normals (lines)
 *
 * Uses the simplest possible shaders:
 * - Vertex shader: just transforms position by MVP matrix
 * - Fragment shader: just outputs a uniform color
 */
public class IntersectionRenderer implements GLSurfaceView.Renderer {

    // PURPOSE: ID of the compiled shader program on the GPU, used for all draw calls
    private int shaderProgram;

    // PURPOSE: the 3 matrices needed to transform 3D world coordinates → 2D screen pixels
    private final float[] viewMatrix = new float[16];       // where the camera is
    private final float[] projectionMatrix = new float[16]; // perspective (fov, aspect, near, far)
    private final float[] mvpMatrix = new float[16];        // final combined matrix sent to GPU

    // =============================================
    // SCENE OBJECTS - these are what we test intersections on
    // =============================================

    // PURPOSE: which primitive is currently being tested (0=Sphere, 1=AABB, 2=Triangle)
    private int mode = 0;

    // PURPOSE: the ray we shoot to test intersections — shared across all modes
    private Ray ray = new Ray(
            new Vec3(-5, 0, 0),   // origin: starts on the left at z=2 (same plane as triangle)
            new Vec3(1, 0, 0)     // direction: points right
    );

    // PURPOSE: sphere primitive for ray-sphere intersection testing
    private Sphere sphere = new Sphere(
            new Vec3(0, 0, 0),    // center at origin
            2.0f                   // radius 2
    );

    // PURPOSE: axis-aligned bounding box for ray-AABB intersection testing
    private AABB aabb = new AABB(
            new Vec3(-1, -1, -1), // min corner
            new Vec3(1, 1, 1)     // max corner
    );

    // PURPOSE: triangle primitive for ray-triangle intersection + normal interpolation testing
    private Triangle triangle = new Triangle(
            new Vec3(-2, -1, 2),  // vertex A (bottom-left)
            new Vec3(2, -1, 2),   // vertex B (bottom-right)
            new Vec3(0, 2, 2),    // vertex C (top-center)
            new Vec3(0, 0, 1),    // normal A (pointing towards camera)
            new Vec3(0, 0, 1),    // normal B
            new Vec3(0, 0, 1)     // normal C
    );

    // =============================================
    // SETTERS - called from the UI (MainActivity) to update values when sliders change
    // =============================================

    // PURPOSE: switch which primitive is being rendered and tested
    public void setMode(int mode) { this.mode = mode; }

    // PURPOSE: move where the ray starts (keeps same direction)
    public void setRayOrigin(float x, float y, float z) {
        ray = new Ray(new Vec3(x, y, z), ray.direction);
    }

    // PURPOSE: change which way the ray points (keeps same origin)
    public void setRayDirection(float x, float y, float z) {
        ray = new Ray(ray.origin, new Vec3(x, y, z));
    }

    // PURPOSE: update sphere position and size
    public void setSphere(float cx, float cy, float cz, float r) {
        sphere = new Sphere(new Vec3(cx, cy, cz), r);
    }

    // PURPOSE: update AABB bounds (min and max corners)
    public void setAABB(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        aabb = new AABB(new Vec3(minX, minY, minZ), new Vec3(maxX, maxY, maxZ));
    }

    // PURPOSE: update triangle vertex positions (keeps existing normals)
    public void setTriangleVertices(float ax, float ay, float az,
                                    float bx, float by, float bz,
                                    float cx, float cy, float cz) {
        triangle = new Triangle(
                new Vec3(ax, ay, az), new Vec3(bx, by, bz), new Vec3(cx, cy, cz),
                triangle.nA, triangle.nB, triangle.nC
        );
    }

    // =============================================
    // OPENGL LIFECYCLE - these 3 methods are called automatically by the GL thread
    // =============================================

    /**
     * onSurfaceCreated — called ONCE when the OpenGL surface is first ready.
     * PURPOSE: one-time setup — set background color, enable features, compile shaders.
     * Think of it as "preparing the canvas before you start painting".
     */
    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        // PURPOSE: set the background color to dark gray (so colored lines are visible against it)
        GLES20.glClearColor(0.15f, 0.15f, 0.15f, 1.0f);

        // PURPOSE: enable depth testing so closer objects hide farther ones (3D ordering)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // PURPOSE: make lines thicker (default is 1 pixel, 3 is easier to see)
        GLES20.glLineWidth(10.0f);

        // PURPOSE: compile vertex + fragment shaders and link them into a GPU program
        shaderProgram = compileShaders();
    }

    /**
     * onSurfaceChanged — called when the screen size changes (rotation, first launch).
     * PURPOSE: update the viewport and projection matrix to match the new screen dimensions.
     * Without this, the scene would look stretched/squished after rotation.
     */
    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        // PURPOSE: tell OpenGL the drawable area size (full screen)
        GLES20.glViewport(0, 0, width, height);

        // PURPOSE: calculate aspect ratio so circles don't look like ovals
        float aspect = (float) width / height;

        // PURPOSE: build perspective projection (things farther away look smaller)
        Matrix.perspectiveM(projectionMatrix, 0, 45.0f, aspect, 0.1f, 100.0f);
    }

    /**
     * onDrawFrame — called ~60 times per second by the GL thread.
     * PURPOSE: redraws the ENTIRE scene from scratch each frame.
     * This is where all the visual output happens.
     */
    @Override
    public void onDrawFrame(GL10 unused) {
        // PURPOSE: wipe the previous frame (color + depth) so we start fresh
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // PURPOSE: position the camera at z=10 looking at the origin
        Matrix.setLookAtM(viewMatrix, 0,
                0, 0, 10,    // eye position
                0, 0, 0,    // look at center
                0, 1, 0);   // up direction

        // PURPOSE: combine projection + view into one matrix to transform 3D → screen
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0);

        // PURPOSE: tell the GPU which shader program to use for all upcoming draw calls
        GLES20.glUseProgram(shaderProgram);

        // PURPOSE: send the MVP matrix to the vertex shader so it can transform vertices
        int mvpHandle = GLES20.glGetUniformLocation(shaderProgram, "uMVPMatrix");
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0);

        // PURPOSE: draw the ray as a yellow line (always visible regardless of mode)
        drawRay();

        // PURPOSE: draw the active primitive wireframe + intersection results based on selected mode
        switch (mode) {
            case 0: drawSphereScene(); break;
            case 1: drawAABBScene(); break;
            case 2: drawTriangleScene(); break;
        }
    }

    // =============================================
    // SCENE DRAWING METHODS - each method draws one complete test scene
    // =============================================

    /**
     * drawRay — draws the ray as a long yellow line.
     * PURPOSE: visualize the ray so you can see where it's going.
     * We extend it far in both directions (t=-10 to t=20) so it's always visible on screen.
     */
    private void drawRay() {
        Vec3 start = ray.pointAt(-10);  // PURPOSE: extend behind origin so we see the full line
        Vec3 end = ray.pointAt(20);     // PURPOSE: extend far ahead past any primitive
        drawLine(start, end, 1.0f, 1.0f, 0.0f); // yellow
    }

    /**
     * drawSphereScene — draws the sphere wireframe and its intersection points.
     * PURPOSE: visually test ray-sphere intersection.
     * Shows entry point (red dot) and exit point (green dot) if the ray hits.
     */
    private void drawSphereScene() {
        // PURPOSE: draw 3 circles to represent the sphere wireframe
        drawWireframeSphere(sphere.center, sphere.radius, 0.0f, 1.0f, 1.0f); // cyan

        // PURPOSE: run the intersection math from Sphere.intersect()
        float[] hits = sphere.intersect(ray);
        if (hits != null) {
            // PURPOSE: show WHERE the ray enters the sphere (red dot at t1)
            Vec3 entry = ray.pointAt(hits[0]);
            drawPoint(entry, 1.0f, 0.0f, 0.0f); // red

            // PURPOSE: show WHERE the ray exits the sphere (green dot at t2)
            Vec3 exit = ray.pointAt(hits[1]);
            drawPoint(exit, 0.0f, 1.0f, 0.0f); // green
        }
    }

    /**
     * drawAABBScene — draws the AABB wireframe box and its intersection points.
     * PURPOSE: visually test ray-AABB intersection (slab method).
     * Shows entry point (red) and exit point (green) if the ray hits.
     */
    private void drawAABBScene() {
        // PURPOSE: draw the 12 edges of the bounding box
        drawWireframeBox(aabb.min, aabb.max, 0.0f, 1.0f, 1.0f); // cyan

        // PURPOSE: run the slab method intersection from AABB.intersect()
        float[] hits = aabb.intersect(ray);
        if (hits != null) {
            // PURPOSE: show WHERE the ray enters the box (red dot at tEnter)
            Vec3 entry = ray.pointAt(hits[0]);
            drawPoint(entry, 1.0f, 0.0f, 0.0f); // red = entry

            // PURPOSE: show WHERE the ray exits the box (green dot at tExit)
            Vec3 exit = ray.pointAt(hits[1]);
            drawPoint(exit, 0.0f, 1.0f, 0.0f); // green = exit
        }
    }

    /**
     * drawTriangleScene — draws the triangle wireframe, intersection point, and interpolated normal.
     * PURPOSE: visually test ray-triangle intersection (Möller-Trumbore) AND normal interpolation.
     * Shows hit point (red dot) and the interpolated normal (magenta line) at that point.
     */
    private void drawTriangleScene() {
        // PURPOSE: draw the 3 edges of the triangle
        drawLine(triangle.A, triangle.B, 0.0f, 1.0f, 1.0f); // cyan edge A→B
        drawLine(triangle.B, triangle.C, 0.0f, 1.0f, 1.0f); // cyan edge B→C
        drawLine(triangle.C, triangle.A, 0.0f, 1.0f, 1.0f); // cyan edge C→A

        // PURPOSE: run Möller-Trumbore intersection from Triangle.intersect()
        float[] hits = triangle.intersect(ray);
        if (hits != null) {
            float t = hits[0]; // PURPOSE: how far along the ray the hit is
            float u = hits[1]; // PURPOSE: barycentric coordinate for vertex B
            float v = hits[2]; // PURPOSE: barycentric coordinate for vertex C

            // PURPOSE: show WHERE the ray hits the triangle (red dot)
            Vec3 hitPoint = ray.pointAt(t);
            drawPoint(hitPoint, 1.0f, 0.0f, 0.0f);

            // PURPOSE: compute and show the interpolated normal at the hit point (slide 6)
            // This demonstrates smooth shading — the normal is blended from vertex normals
            Vec3 normal = triangle.getInterpolatedNormal(u, v);
            Vec3 normalEnd = hitPoint.add(normal.scale(1.5f)); // PURPOSE: scale so the line is visible
            drawLine(hitPoint, normalEnd, 1.0f, 0.0f, 1.0f);  // magenta line = normal direction
        }
    }

    // =============================================
    // PRIMITIVE DRAWING HELPERS - low-level OpenGL drawing methods
    // =============================================

    /**
     * drawLine — draws a single line between two 3D points with the given RGB color.
     * PURPOSE: the basic building block for all wireframe rendering.
     * Used by: drawRay, drawTriangleScene, drawWireframeBox, drawCircle (normal line).
     */
    private void drawLine(Vec3 from, Vec3 to, float r, float g, float b) {
        // PURPOSE: define the two endpoints of the line as a flat float array (x,y,z, x,y,z)
        float[] vertices = {
                from.x, from.y, from.z,
                to.x, to.y, to.z
        };

        // PURPOSE: wrap the float array in a native buffer (OpenGL can't read Java arrays directly)
        FloatBuffer buffer = createFloatBuffer(vertices);

        // PURPOSE: tell the fragment shader what color to draw this line
        int colorHandle = GLES20.glGetUniformLocation(shaderProgram, "uColor");
        GLES20.glUniform4f(colorHandle, r, g, b, 1.0f);

        // PURPOSE: tell the vertex shader where to read position data from
        int posHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition");
        GLES20.glEnableVertexAttribArray(posHandle);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buffer);

        // PURPOSE: actually draw the line (GL_LINES = connect every 2 vertices as a line)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2);

        // PURPOSE: cleanup — disable the attribute so it doesn't interfere with next draw call
        GLES20.glDisableVertexAttribArray(posHandle);
    }

    /**
     * drawPoint — draws a single dot at a 3D position with the given RGB color.
     * PURPOSE: mark intersection points so you can visually see WHERE a ray hits a primitive.
     * The point size is set to 15 pixels in the vertex shader (gl_PointSize = 15.0).
     */
    private void drawPoint(Vec3 pos, float r, float g, float b) {
        // PURPOSE: single vertex = single point (x, y, z)
        float[] vertices = { pos.x, pos.y, pos.z };

        // PURPOSE: wrap in native buffer for OpenGL
        FloatBuffer buffer = createFloatBuffer(vertices);

        // PURPOSE: set the dot color
        int colorHandle = GLES20.glGetUniformLocation(shaderProgram, "uColor");
        GLES20.glUniform4f(colorHandle, r, g, b, 1.0f);

        // PURPOSE: feed position data to the vertex shader
        int posHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition");
        GLES20.glEnableVertexAttribArray(posHandle);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buffer);

        // PURPOSE: draw 1 point (GL_POINTS = render each vertex as a square dot)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, 1);

        // PURPOSE: cleanup
        GLES20.glDisableVertexAttribArray(posHandle);
    }

    /**
     * drawWireframeSphere — approximates a sphere using 3 circles (one per plane).
     * PURPOSE: visualize the sphere's position and size without needing a full mesh.
     * Draws circles in XY (front view), XZ (top view), YZ (side view) planes.
     * It's not a perfect sphere, but enough to see where it is and test intersections.
     */
    private void drawWireframeSphere(Vec3 center, float radius, float r, float g, float b) {
        int segments = 40; // PURPOSE: how many line segments per circle (more = smoother)

        drawCircle(center, radius, segments, 0, r, g, b); // PURPOSE: circle in XY plane (front view)
        drawCircle(center, radius, segments, 1, r, g, b); // PURPOSE: circle in XZ plane (top view)
        drawCircle(center, radius, segments, 2, r, g, b); // PURPOSE: circle in YZ plane (side view)
    }

    /**
     * drawCircle — draws a single circle (ring of line segments) around a center point.
     * PURPOSE: helper for drawWireframeSphere — generates points along a circle using
     * cos/sin (parametric circle equation) and connects them with GL_LINE_LOOP.
     * @param plane which plane to draw in: 0=XY, 1=XZ, 2=YZ
     */
    private void drawCircle(Vec3 center, float radius, int segments, int plane, float r, float g, float b) {
        // PURPOSE: allocate space for all the points on the circle (each point = 3 floats: x,y,z)
        float[] vertices = new float[(segments + 1) * 3];

        for (int i = 0; i <= segments; i++) {
            // PURPOSE: calculate angle for this point (evenly spaced around the circle, 0 to 2π)
            float angle = (float) (2.0 * Math.PI * i / segments);
            // PURPOSE: convert angle to x,y position on the circle using cos/sin
            float cos = (float) Math.cos(angle) * radius;
            float sin = (float) Math.sin(angle) * radius;

            int idx = i * 3;
            switch (plane) {
                case 0: // PURPOSE: XY plane — cos moves along X, sin moves along Y, Z stays fixed
                    vertices[idx] = center.x + cos;
                    vertices[idx + 1] = center.y + sin;
                    vertices[idx + 2] = center.z;
                    break;
                case 1: // PURPOSE: XZ plane — cos moves along X, sin moves along Z, Y stays fixed
                    vertices[idx] = center.x + cos;
                    vertices[idx + 1] = center.y;
                    vertices[idx + 2] = center.z + sin;
                    break;
                case 2: // PURPOSE: YZ plane — cos moves along Y, sin moves along Z, X stays fixed
                    vertices[idx] = center.x;
                    vertices[idx + 1] = center.y + cos;
                    vertices[idx + 2] = center.z + sin;
                    break;
            }
        }

        // PURPOSE: wrap vertices in native buffer for OpenGL
        FloatBuffer buffer = createFloatBuffer(vertices);

        // PURPOSE: set circle color
        int colorHandle = GLES20.glGetUniformLocation(shaderProgram, "uColor");
        GLES20.glUniform4f(colorHandle, r, g, b, 1.0f);

        // PURPOSE: feed vertex positions to the shader
        int posHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition");
        GLES20.glEnableVertexAttribArray(posHandle);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buffer);

        // PURPOSE: draw as LINE_LOOP — connects all points with lines AND connects last point back to first
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, segments + 1);

        // PURPOSE: cleanup
        GLES20.glDisableVertexAttribArray(posHandle);
    }

    /**
     * drawWireframeBox — draws all 12 edges of a cuboid (box).
     * PURPOSE: visualize the AABB's position and size.
     * A box has 8 corners and 12 edges. We define all 24 vertices (2 per edge)
     * and draw them with GL_LINES.
     */
    private void drawWireframeBox(Vec3 min, Vec3 max, float r, float g, float b) {
        // PURPOSE: extract min/max coordinates for readability
        float lx = min.x, ly = min.y, lz = min.z;
        float rx = max.x, ry = max.y, rz = max.z;

        // PURPOSE: define all 12 edges as pairs of vertices (2 endpoints per line × 12 edges = 24 vertices)
        float[] vertices = {
                // Bottom face (4 edges)
                lx, ly, lz,  rx, ly, lz,  // PURPOSE: bottom-near edge
                rx, ly, lz,  rx, ly, rz,  // PURPOSE: bottom-right edge
                rx, ly, rz,  lx, ly, rz,  // PURPOSE: bottom-far edge
                lx, ly, rz,  lx, ly, lz,  // PURPOSE: bottom-left edge

                // Top face (4 edges)
                lx, ry, lz,  rx, ry, lz,  // PURPOSE: top-near edge
                rx, ry, lz,  rx, ry, rz,  // PURPOSE: top-right edge
                rx, ry, rz,  lx, ry, rz,  // PURPOSE: top-far edge
                lx, ry, rz,  lx, ry, lz,  // PURPOSE: top-left edge

                // Vertical edges (4 pillars connecting top and bottom)
                lx, ly, lz,  lx, ry, lz,  // PURPOSE: front-left pillar
                rx, ly, lz,  rx, ry, lz,  // PURPOSE: front-right pillar
                rx, ly, rz,  rx, ry, rz,  // PURPOSE: back-right pillar
                lx, ly, rz,  lx, ry, rz   // PURPOSE: back-left pillar
        };

        // PURPOSE: wrap in native buffer
        FloatBuffer buffer = createFloatBuffer(vertices);

        // PURPOSE: set box wireframe color
        int colorHandle = GLES20.glGetUniformLocation(shaderProgram, "uColor");
        GLES20.glUniform4f(colorHandle, r, g, b, 1.0f);

        // PURPOSE: feed vertex positions to the shader
        int posHandle = GLES20.glGetAttribLocation(shaderProgram, "aPosition");
        GLES20.glEnableVertexAttribArray(posHandle);
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, buffer);

        // PURPOSE: draw 12 lines (24 vertices, every 2 vertices = 1 line)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, 24);

        // PURPOSE: cleanup
        GLES20.glDisableVertexAttribArray(posHandle);
    }

    // =============================================
    // UTILITY
    // =============================================

    /**
     * createFloatBuffer — converts a Java float[] into a native FloatBuffer.
     * PURPOSE: OpenGL ES can NOT read Java arrays directly. It needs data in
     * native memory (ByteBuffer) with the device's byte order (little/big endian).
     * Every draw call needs this conversion.
     */
    private FloatBuffer createFloatBuffer(float[] data) {
        // PURPOSE: allocate native memory (4 bytes per float)
        ByteBuffer bb = ByteBuffer.allocateDirect(data.length * 4);
        // PURPOSE: use device's native byte order so OpenGL can read it correctly
        bb.order(ByteOrder.nativeOrder());
        // PURPOSE: view the byte buffer as floats
        FloatBuffer buffer = bb.asFloatBuffer();
        // PURPOSE: copy our Java float data into the native buffer
        buffer.put(data);
        // PURPOSE: reset read position to the start (OpenGL reads from position 0)
        buffer.position(0);
        return buffer;
    }

    /**
     * compileShaders — creates and compiles the GPU shader program for Session 3.
     * PURPOSE: the GPU needs its own mini-programs (shaders) to know HOW to draw.
     *
     * Our shaders are MUCH simpler than Session 1-2 (no textures, no lighting):
     * - Vertex shader: just transforms 3D position → screen position using MVP matrix
     * - Fragment shader: just outputs a solid color (set via uniform, no per-pixel lighting)
     *
     * Returns the program ID which we store and use in every draw call.
     */
    private int compileShaders() {
        // PURPOSE: vertex shader — runs once per vertex, transforms 3D position to screen position
        String vertexCode =
                "attribute vec4 aPosition;\n" +       // PURPOSE: receives vertex position from our buffers
                "uniform mat4 uMVPMatrix;\n" +        // PURPOSE: receives the MVP matrix to do the 3D→2D transform
                "void main() {\n" +
                "  gl_Position = uMVPMatrix * aPosition;\n" + // PURPOSE: multiply position by MVP = final screen position
                "  gl_PointSize = 15.0;\n" +          // PURPOSE: set dot size to 15 pixels (for intersection points)
                "}\n";

        // PURPOSE: fragment shader — runs once per pixel, determines the pixel's color
        String fragmentCode =
                "precision mediump float;\n" +        // PURPOSE: use medium precision (good enough, saves GPU power)
                "uniform vec4 uColor;\n" +            // PURPOSE: receives color from our Java code (set per draw call)
                "void main() {\n" +
                "  gl_FragColor = uColor;\n" +        // PURPOSE: output the color — every pixel of this draw call gets this color
                "}\n";

        // PURPOSE: create an empty vertex shader slot on the GPU, upload code, compile it
        int vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER);
        GLES20.glShaderSource(vertexShader, vertexCode);
        GLES20.glCompileShader(vertexShader);

        // PURPOSE: create an empty fragment shader slot on the GPU, upload code, compile it
        int fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER);
        GLES20.glShaderSource(fragmentShader, fragmentCode);
        GLES20.glCompileShader(fragmentShader);

        // PURPOSE: link both shaders into one program (they work together: vertex feeds into fragment)
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);   // PURPOSE: attach vertex shader
        GLES20.glAttachShader(program, fragmentShader); // PURPOSE: attach fragment shader
        GLES20.glLinkProgram(program);                  // PURPOSE: link them together into a usable program

        return program;
    }
}
