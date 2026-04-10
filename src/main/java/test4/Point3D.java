package test4;


import java.awt.Color;
import java.util.*;
import java.util.List;

// 3D Point class for visualization
class Point3D {
    public double x, y, z;
    
    public Point3D(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
    
    public Point3D rotate(double angleX, double angleY, double angleZ) {
        // Rotate around X axis
        double cosX = Math.cos(angleX), sinX = Math.sin(angleX);
        double y1 = y * cosX - z * sinX;
        double z1 = y * sinX + z * cosX;
        
        // Rotate around Y axis
        double cosY = Math.cos(angleY), sinY = Math.sin(angleY);
        double x2 = x * cosY + z1 * sinY;
        double z2 = -x * sinY + z1 * cosY;
        
        // Rotate around Z axis
        double cosZ = Math.cos(angleZ), sinZ = Math.sin(angleZ);
        double x3 = x2 * cosZ - y1 * sinZ;
        double y3 = x2 * sinZ + y1 * cosZ;
        
        return new Point3D(x3, y3, z2);
    }
    
    public ProjectedPoint project(int screenWidth, int screenHeight, double distance) {
        double factor = distance / (distance + z + 1); // Avoid division by zero
        double projX = x * factor + screenWidth / 2.0;
        double projY = y * factor + screenHeight / 2.0;
        return new ProjectedPoint(projX, projY, factor); // factor used for z-buffering
    }
    
    public double distanceTo(Point3D other) {
        return Math.sqrt(
            Math.pow(x - other.x, 2) + 
            Math.pow(y - other.y, 2) + 
            Math.pow(z - other.z, 2)
        );
    }
}

// Projected point for 2D rendering
class ProjectedPoint {
    public double x, y, z; // z is depth factor for sorting
    
    public ProjectedPoint(double x, double y, double z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }
}



