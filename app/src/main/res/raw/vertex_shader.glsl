// vertex shader runs once for every vertex
attribute vec4 aPosition;  // input: vertex position (x,y,z,w) from our positionBuffer
attribute vec3 aNormal;    // input: normal vector (nx,ny,nz) from our normalBuffer

uniform mat4 uMVPMatrix;   // input: our MVP matrix (same for ALL vertices)

void main() {
    // multiply the vertex position by the MVP matrix
    // this transforms: local space → world space → camera space → clip space
    gl_Position = uMVPMatrix * aPosition;
}