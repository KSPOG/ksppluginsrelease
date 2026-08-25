package net.runelite.client.plugins.microbot.kspdirectfishing;

import net.runelite.api.coords.WorldPoint;

public enum KspDirectFishingLocation {
    DRAYNOR_VILLAGE("Draynor Village",new WorldPoint(3085,3230,0),18,false),
    LUMBRIDGE_SWAMP("Lumbridge Swamp",new WorldPoint(3244,3150,0),18,true);

    private final String displayName;
    private final WorldPoint anchor;
    private final int radius;
    private final boolean forceDrop;

    KspDirectFishingLocation(String displayName,WorldPoint anchor,int radius,boolean forceDrop){this.displayName=displayName;this.anchor=anchor;this.radius=radius;this.forceDrop=forceDrop;}
    public WorldPoint getAnchor(){return anchor;}
    public int getRadius(){return radius;}
    public boolean isForceDrop(){return forceDrop;}
    public boolean contains(WorldPoint point){return point!=null&&point.getPlane()==anchor.getPlane()&&point.distanceTo(anchor)<=radius;}
    @Override public String toString(){return displayName;}
}
