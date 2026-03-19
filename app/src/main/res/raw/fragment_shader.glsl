// fragment shader - runs once for every pixel/fragment
// where lighting is implemented (colors)

precision mediump float;  // tells GPU to use medium precision for floats

void main() {
    // for now just color every pixel white
    // gl_FragColor = (R, G, B, Alpha)
    gl_FragColor = vec4(1.0, 1.0, 1.0, 1.0); //
}