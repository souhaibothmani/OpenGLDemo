package com.example.opengldemo;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

public class MyGLSurfaceView extends GLSurfaceView{

    private IntersectionRenderer renderer;

    public MyGLSurfaceView(Context context) {
        super(context);
        init();
    }

    // This constructor is required when the view is inflated from XML layout
    public MyGLSurfaceView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setEGLContextClientVersion(2);
        renderer = new IntersectionRenderer();
        setRenderer(renderer);
    }

    /** Expose the renderer so MainActivity can update values from the UI */
    public IntersectionRenderer getIntersectionRenderer() {
        return renderer;
    }
}
