package com.ethlo.geodata.util;

import com.ethlo.geodata.model.Coordinates;

public class DistanceUtil
{
    private DistanceUtil(){}
    
    public static double distance(Coordinates a, Coordinates b)
    {
        return distance(a.getLat(), a.getLng(), b.getLat(), b.getLng());
    }
    
    private static double distance(double lat1, double lon1, double lat2, double lon2)
    {
        double theta = lon1 - lon2;
        double dist = Math.sin(deg2rad(lat1)) * Math.sin(deg2rad(lat2)) + Math.cos(deg2rad(lat1)) * Math.cos(deg2rad(lat2)) * Math.cos(deg2rad(theta));
        dist = Math.acos(dist);
        dist = rad2deg(dist);
        return dist * 60 * 1.1515 * 1.609344;
      }
      
    private static double deg2rad(double deg)
    {
        return (deg * Math.PI / 180.0);
    }
    
    private static double rad2deg(double rad)
    {
        return (rad * 180.0 / Math.PI);
    }
}
