package com.example.opengldemo;
import android.content.Context;
import android.opengl.GLSurfaceView;

public class MyGLSurfaceView extends GLSurfaceView{

    public MyGLSurfaceView(Context context) {
        super(context);
        setEGLContextClientVersion(2); //tells OpenGL "use ES version 2.0". EGL is the layer between Android and OpenGL.
        setRenderer(new MyGLRenderer(context)); //this is where we hand the canvas to the painter.
                                                // We attach our renderer which will do all the actual drawing.
    }
}
