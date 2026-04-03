package com.example.opengldemo;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.os.Bundle;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

/**
 * MainActivity - Sets up the OpenGL view and editor UI for Session 3.
 *
 * Layout: GL view on top, control panel at the bottom.
 * The sliders adjust ray and primitive parameters in real-time.
 * The intersection result is shown as text below the sliders.
 */
public class MainActivity extends AppCompatActivity {

    private MyGLSurfaceView glSurfaceView;
    private IntersectionRenderer renderer;
    private TextView txtResult;
    private TextView labelParam1;

    // Current mode: 0 = Sphere, 1 = AABB, 2 = Triangle
    private int currentMode = 0;

    // Slider values mapped to world coordinates
    // Sliders go 0-200, we map to -5.0 to 5.0 for positions
    // and 0.1 to 5.0 for radius/size
    private float rayOriginY = 0f;
    private float rayDirX = 1f;
    private float rayDirY = 0f;
    private float rayDirZ = 0f;
    private float param1 = 2f;   // radius / size
    private float posX = 0f;
    private float posY = 0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Check device supports OpenGL ES 2.0
        ActivityManager activityManager =
                (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ConfigurationInfo configInfo =
                activityManager.getDeviceConfigurationInfo();

        if (configInfo.reqGlEsVersion < 0x20000) {
            return; // device doesn't support ES 2.0
        }

        setContentView(R.layout.activity_main);

        // Get the GL surface view from layout
        glSurfaceView = findViewById(R.id.gl_surface_view);
        renderer = glSurfaceView.getIntersectionRenderer();

        txtResult = findViewById(R.id.txt_result);
        labelParam1 = findViewById(R.id.label_param1);

        setupModeSelector();
        setupSliders();
    }

    /**
     * Sets up the radio buttons to switch between Sphere / AABB / Triangle modes.
     */
    private void setupModeSelector() {
        RadioGroup modeGroup = findViewById(R.id.mode_group);
        modeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.mode_sphere) {
                currentMode = 0;
                labelParam1.setText("Sphere Radius:");
            } else if (checkedId == R.id.mode_aabb) {
                currentMode = 1;
                labelParam1.setText("AABB Size:");
            } else if (checkedId == R.id.mode_triangle) {
                currentMode = 2;
                labelParam1.setText("Triangle Scale:");
            }
            renderer.setMode(currentMode);
            updateScene();
        });
    }

    /**
     * Sets up all the SeekBar sliders and their change listeners.
     * Each slider calls updateScene() whenever it changes.
     */
    private void setupSliders() {
        SeekBar.OnSeekBarChangeListener listener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Map slider (0-200) to different ranges depending on which slider
                int id = seekBar.getId();

                if (id == R.id.slider_ray_origin_y) {
                    // Map 0-200 to -5.0 to 5.0
                    rayOriginY = (progress - 100) / 20.0f;
                } else if (id == R.id.slider_ray_dir_y) {
                    // Map 0-200 to -1.0 to 1.0
                    rayDirY = (progress - 100) / 100.0f;
                } else if (id == R.id.slider_ray_dir_x) {
                    // Map 0-200 to -1.0 to 1.0
                    rayDirX = (progress - 100) / 100.0f;
                } else if (id == R.id.slider_ray_dir_z) {
                    // Map 0-200 to -1.0 to 1.0
                    rayDirZ = (progress - 100) / 100.0f;
                } else if (id == R.id.slider_param1) {
                    // Map 0-200 to 0.1 to 5.0
                    param1 = progress / 40.0f + 0.1f;
                } else if (id == R.id.slider_pos_x) {
                    // Map 0-200 to -5.0 to 5.0
                    posX = (progress - 100) / 20.0f;
                } else if (id == R.id.slider_pos_y) {
                    // Map 0-200 to -5.0 to 5.0
                    posY = (progress - 100) / 20.0f;
                }

                updateScene();
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        };

        // Attach the same listener to all sliders
        ((SeekBar) findViewById(R.id.slider_ray_origin_y)).setOnSeekBarChangeListener(listener);
        ((SeekBar) findViewById(R.id.slider_ray_dir_y)).setOnSeekBarChangeListener(listener);
        ((SeekBar) findViewById(R.id.slider_ray_dir_x)).setOnSeekBarChangeListener(listener);
        ((SeekBar) findViewById(R.id.slider_ray_dir_z)).setOnSeekBarChangeListener(listener);
        ((SeekBar) findViewById(R.id.slider_param1)).setOnSeekBarChangeListener(listener);
        ((SeekBar) findViewById(R.id.slider_pos_x)).setOnSeekBarChangeListener(listener);
        ((SeekBar) findViewById(R.id.slider_pos_y)).setOnSeekBarChangeListener(listener);
    }

    /**
     * Updates the renderer with current slider values and computes intersection.
     * Called every time any slider changes.
     */
    private void updateScene() {
        // Update ray (origin at x=-5, direction from sliders)
        renderer.setRayOrigin(-5, rayOriginY, 0);
        renderer.setRayDirection(rayDirX, rayDirY, rayDirZ);

        // Update the active primitive based on mode
        switch (currentMode) {
            case 0: // Sphere
                renderer.setSphere(posX, posY, 0, param1);
                break;
            case 1: // AABB - param1 controls the half-size
                renderer.setAABB(
                        posX - param1, posY - param1, -param1,
                        posX + param1, posY + param1, param1);
                break;
            case 2: // Triangle - param1 controls scale
                renderer.setTriangleVertices(
                        posX - param1, posY - param1, 2,    // A bottom-left
                        posX + param1, posY - param1, 2,    // B bottom-right
                        posX, posY + param1, 2);             // C top-center
                break;
        }

        // Run intersection test and display result
        Ray testRay = new Ray(new Vec3(-5, rayOriginY, 0), new Vec3(rayDirX, rayDirY, rayDirZ));
        String result;

        switch (currentMode) {
            case 0:
                Sphere s = new Sphere(new Vec3(posX, posY, 0), param1);
                float[] sHits = s.intersect(testRay);
                if (sHits != null) {
                    Vec3 entry = testRay.pointAt(sHits[0]);
                    result = String.format("HIT! entry t=%.2f (%.1f, %.1f, %.1f)  exit t=%.2f",
                            sHits[0], entry.x, entry.y, entry.z, sHits[1]);
                } else {
                    result = "MISS - no intersection";
                }
                break;
            case 1:
                AABB box = new AABB(
                        new Vec3(posX - param1, posY - param1, -param1),
                        new Vec3(posX + param1, posY + param1, param1));
                float[] bHits = box.intersect(testRay);
                if (bHits != null) {
                    Vec3 entry = testRay.pointAt(bHits[0]);
                    result = String.format("HIT! enter t=%.2f (%.1f, %.1f, %.1f)  exit t=%.2f",
                            bHits[0], entry.x, entry.y, entry.z, bHits[1]);
                } else {
                    result = "MISS - no intersection";
                }
                break;
            case 2:
                Triangle tri = new Triangle(
                        new Vec3(posX - param1, posY - param1, 2),
                        new Vec3(posX + param1, posY - param1, 2),
                        new Vec3(posX, posY + param1, 2),
                        new Vec3(0, 0, 1), new Vec3(0, 0, 1), new Vec3(0, 0, 1));
                float[] tHits = tri.intersect(testRay);
                if (tHits != null) {
                    Vec3 hitPt = testRay.pointAt(tHits[0]);
                    result = String.format("HIT! t=%.2f (%.1f, %.1f, %.1f) u=%.2f v=%.2f",
                            tHits[0], hitPt.x, hitPt.y, hitPt.z, tHits[1], tHits[2]);
                } else {
                    result = "MISS - no intersection";
                }
                break;
            default:
                result = "--";
        }

        txtResult.setText("Intersection: " + result);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (glSurfaceView != null) glSurfaceView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (glSurfaceView != null) glSurfaceView.onPause();
    }
}
