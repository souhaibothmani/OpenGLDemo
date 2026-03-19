package com.example.opengldemo;

import android.content.Context;
import java.io.BufferedReader; //reads the OBH file line by line
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList; //dynamic listing since we don't know how many vertices we have
import java.util.List;

//OBH is just a text file describing a 3D model. -> ObjLoader is to read this text file
public class ObjLoader {
    public float[] positions;  // x, y, z for each vertex
    public float[] normals;    // nx, ny, nz for each vertex

    public ObjLoader(Context context, String filename) {

        // Temporary lists while we read the file
        List<Float> tempPositions = new ArrayList<>();
        List<Float> tempNormals   = new ArrayList<>();

        // What the OBJ file gives us
        List<float[]> vList  = new ArrayList<>();  // all v lines (so all the v x y z line from the OBJ file)
        List<float[]> vnList = new ArrayList<>();  // all vn lines (so all the normals line from the OBJ file)


        try { // things can go wrong when reading files, so we wrap in try/catch

            // open the file from the assets folder
            // InputStreamReader converts raw bytes → characters
            // BufferedReader wraps it so we can read line by line
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(context.getAssets().open(filename)));

            String line;
            while ((line = reader.readLine()) != null) { // read one line at a time until end of file

                if (line.startsWith("v ")) { // vertex position line: v x y z
                    String[] parts = line.split("\\s+"); // split by whitespace → ["v", "1.23", "0.5", "-0.8"]
                    vList.add(new float[]{ // convert strings to floats and store in vList
                            Float.parseFloat(parts[1]), // x
                            Float.parseFloat(parts[2]), // y
                            Float.parseFloat(parts[3])  // z
                    });

                } else if (line.startsWith("vn ")) { // vertex normal line: vn x y z
                    String[] parts = line.split("\\s+"); // same parsing as above
                    vnList.add(new float[]{
                            Float.parseFloat(parts[1]), // nx
                            Float.parseFloat(parts[2]), // ny
                            Float.parseFloat(parts[3])  // nz
                    });

                } else if (line.startsWith("f ")) { // face line: f v1//vn1 v2//vn2 v3//vn3
                    // a face = one triangle = 3 vertices
                    String[] parts = line.split("\\s+"); // → ["f", "12//3", "7//3", "24//3"]
                    for (int i = 1; i <= 3; i++) { // loop over the 3 vertices of this triangle
                        String[] indices = parts[i].split("//"); // split "12//3" → ["12", "3"]
                        int vIndex  = Integer.parseInt(indices[0]) - 1; // vertex index (-1 because OBJ starts at 1, Java at 0)
                        int vnIndex = Integer.parseInt(indices[1]) - 1; // normal index (same reason)

                        float[] pos    = vList.get(vIndex);  // look up the actual x,y,z position
                        float[] normal = vnList.get(vnIndex); // look up the actual nx,ny,nz normal

                        // add position to final list, one float at a time
                        tempPositions.add(pos[0]); // x
                        tempPositions.add(pos[1]); // y
                        tempPositions.add(pos[2]); // z

                        // add normal to final list, one float at a time
                        tempNormals.add(normal[0]); // nx
                        tempNormals.add(normal[1]); // ny
                        tempNormals.add(normal[2]); // nz
                    }
                }
            }
            reader.close(); // always close the file when done

        } catch (IOException e) {
            e.printStackTrace(); // if something went wrong, print the error to Logcat
        }

        // OpenGL needs plain float[] arrays, not Java Lists
        // ArrayList is a Java object - OpenGL (written in C) doesn't understand it
        // float[] is raw memory - exactly what OpenGL needs

        positions = new float[tempPositions.size()]; // create array of exact size
        for (int i = 0; i < tempPositions.size(); i++)
            positions[i] = tempPositions.get(i); // copy each value across

        normals = new float[tempNormals.size()]; // same for normals
        for (int i = 0; i < normals.length; i++)
            normals[i] = tempNormals.get(i);

    } // end of constructor
} // end of ObjLoader class

