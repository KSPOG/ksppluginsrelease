package net.runelite.client.plugins.microbot.kspfleshcrawlers;

import net.runelite.api.coords.WorldPoint;
import java.util.List;

/** Ground-truth Stronghold polygons supplied from the live client. */
final class StrongholdZones {
    private StrongholdZones(){}
    static final PolygonZone FLOOR_1_START=zone("Floor 1 start",p(1857,5245),p(1865,5245),p(1865,5239),p(1864,5237),p(1863,5237),p(1862,5239),p(1859,5239),p(1857,5239),p(1856,5240),p(1856,5244),p(1857,5245));
    static final PolygonZone FLOOR_1_TREASURE=zone("Floor 1 treasure",p(1915,5227),p(1915,5217),p(1901,5217),p(1900,5218),p(1901,5228),p(1911,5228),p(1914,5228),p(1915,5227));
    static final PolygonZone FLOOR_2_START=zone("Floor 2 start",p(2041,5246),p(2046,5246),p(2046,5241),p(2041,5241),p(2041,5246));
    static final PolygonZone ROOM_1_ENTER=zone("Room 1 enter",p(2043,5239),p(2045,5239),p(2045,5237),p(2043,5237),p(2043,5238),p(2043,5239));
    static final PolygonZone ROOM_1_EXIT=zone("Room 1 exit / corridor",p(2043,5236),p(2046,5236),p(2046,5230),p(2046,5219),p(2045,5210),p(2038,5210),p(2038,5205),p(2037,5204),p(2035,5204),p(2036,5220),p(2043,5221),p(2043,5225),p(2038,5225),p(2038,5235),p(2042,5236),p(2043,5236));
    static final PolygonZone FLOOR_2_LADDER_ROOM=zone("Floor 2 ladder room",p(2035,5200),p(2036,5198),p(2046,5198),p(2046,5207),p(2042,5209),p(2039,5209),p(2039,5205),p(2039,5200),p(2035,5200));
    static final PolygonZone ROOM_2_ENTER=zone("Room 2 enter",p(2044,5197),p(2047,5197),p(2047,5195),p(2044,5195),p(2044,5196),p(2044,5197));
    static final PolygonZone FLESH_CRAWLER_ROOM=zone("Flesh Crawler room",p(2046,5194),p(2046,5184),p(2037,5184),p(2037,5187),p(2037,5191),p(2034,5191),p(2034,5195),p(2041,5195),p(2042,5194),p(2045,5194),p(2046,5194));
    static final List<PolygonZone> ALL=List.of(FLESH_CRAWLER_ROOM,ROOM_2_ENTER,FLOOR_2_LADDER_ROOM,ROOM_1_ENTER,FLOOR_2_START,ROOM_1_EXIT,FLOOR_1_START,FLOOR_1_TREASURE);
    static PolygonZone locate(WorldPoint p){if(p==null)return null;for(PolygonZone z:ALL)if(z.contains(p))return z;return null;}
    static boolean isOnFloor1(WorldPoint p){return p!=null&&p.getPlane()==0&&p.getX()>=1855&&p.getX()<=1920&&p.getY()>=5184&&p.getY()<=5248;}
    static boolean isOnFloor2(WorldPoint p){return p!=null&&p.getPlane()==0&&p.getX()>=1983&&p.getX()<=2048&&p.getY()>=5184&&p.getY()<=5248;}
    private static WorldPoint p(int x,int y){return new WorldPoint(x,y,0);} private static PolygonZone zone(String n,WorldPoint...p){return new PolygonZone(n,p);}

    static final class PolygonZone {
        private final String name; private final WorldPoint[] vertices;
        PolygonZone(String name,WorldPoint[] vertices){this.name=name;this.vertices=vertices.clone();} String getName(){return name;}
        boolean contains(WorldPoint p){if(p==null||p.getPlane()!=0||vertices.length<3)return false;for(int i=0,j=vertices.length-1;i<vertices.length;j=i++)if(onSegment(vertices[j],vertices[i],p))return true;boolean inside=false;double px=p.getX(),py=p.getY();for(int i=0,j=vertices.length-1;i<vertices.length;j=i++){double xi=vertices[i].getX(),yi=vertices[i].getY(),xj=vertices[j].getX(),yj=vertices[j].getY();if((yi>py)!=(yj>py)&&px<(xj-xi)*(py-yi)/(yj-yi)+xi)inside=!inside;}return inside;}
        private boolean onSegment(WorldPoint a,WorldPoint b,WorldPoint p){long cross=(long)(p.getY()-a.getY())*(b.getX()-a.getX())-(long)(p.getX()-a.getX())*(b.getY()-a.getY());return cross==0&&p.getX()>=Math.min(a.getX(),b.getX())&&p.getX()<=Math.max(a.getX(),b.getX())&&p.getY()>=Math.min(a.getY(),b.getY())&&p.getY()<=Math.max(a.getY(),b.getY());}
    }
}
