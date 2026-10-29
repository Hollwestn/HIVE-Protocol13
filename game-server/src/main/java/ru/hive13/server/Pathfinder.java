package ru.hive13.server;
import java.util.*;
import static ru.hive13.model.Model.*;

public final class Pathfinder {
    public record Route(List<Point> points,List<String> doors){}
    public static Route find(Campaign s,String from,Point start,String to,Point goal){
        if(!s.rooms.containsKey(from)||!s.rooms.containsKey(to)||!s.rooms.get(to).contains(goal.x(),goal.y()))return null;
        var pts=new ArrayList<Point>();Point cursor=start;
        if(!s.rooms.get(from).contains(start.x(),start.y())){var entry=s.doors.values().stream().filter(d->d.a.equals(from)||d.b.equals(from)).filter(d->onCorridor(d,start)).min(Comparator.comparingDouble(d->start.distance(d.a.equals(from)?d.pa:d.pb))).orElse(null);if(entry==null)return null;cursor=entry.a.equals(from)?entry.pa:entry.pb;pts.add(cursor);}
        if(from.equals(to)){var local=inside(s.rooms.get(from),cursor,goal);if(local==null)return null;pts.addAll(local);return new Route(pts,new ArrayList<>());}
        var cost=new HashMap<String,Double>();var prev=new HashMap<String,Door>();
        var queue=new PriorityQueue<String>(Comparator.comparingDouble(r->cost.getOrDefault(r,Double.MAX_VALUE)+s.rooms.get(r).center().distance(s.rooms.get(to).center())));
        cost.put(from,0d);queue.add(from);var closed=new HashSet<String>();
        while(!queue.isEmpty()){String r=queue.poll();if(!closed.add(r))continue;if(r.equals(to))break;
            for(var d:s.doors.values()){if(!d.passable()||(!d.a.equals(r)&&!d.b.equals(r)))continue;String next=d.other(r);if(s.rooms.get(next).biomass>=95)continue;
                double g=cost.get(r)+s.rooms.get(r).center().distance(s.rooms.get(next).center());
                if(g<cost.getOrDefault(next,Double.MAX_VALUE)){cost.put(next,g);prev.put(next,d);queue.remove(next);queue.add(next);}}
        }
        if(!prev.containsKey(to))return null;var chain=new LinkedList<Door>();String r=to;
        while(!r.equals(from)){var d=prev.get(r);chain.addFirst(d);r=d.other(r);}
        var doors=new ArrayList<String>();r=from;
        for(var d:chain){boolean forward=d.a.equals(r);Point exit=forward?d.pa:d.pb;var local=inside(s.rooms.get(r),cursor,exit);if(local==null)return null;pts.addAll(local);cursor=forward?d.pb:d.pa;pts.add(cursor);doors.add(d.id);r=d.other(r);}
        var local=inside(s.rooms.get(r),cursor,goal);if(local==null)return null;pts.addAll(local);return new Route(pts,doors);
    }
    private static boolean onCorridor(Door d,Point p){return p.distance(d.pa)+p.distance(d.pb)<=d.pa.distance(d.pb)+.001;}
    /** Visibility graph around concave wall vertices. Returned points exclude start and include goal. */
    static List<Point> inside(Room room,Point start,Point goal){if(!room.contains(start.x(),start.y())||!room.contains(goal.x(),goal.y()))return null;if(segmentInside(room,start,goal))return new ArrayList<>(List.of(goal));var nodes=new ArrayList<Point>();nodes.add(start);nodes.add(goal);nodes.addAll(room.outline);int n=nodes.size();double[] distance=new double[n];Arrays.fill(distance,Double.POSITIVE_INFINITY);int[] previous=new int[n];Arrays.fill(previous,-1);boolean[] visited=new boolean[n];distance[0]=0;
        for(int step=0;step<n;step++){int current=-1;for(int i=0;i<n;i++)if(!visited[i]&&(current<0||distance[i]<distance[current]))current=i;if(current<0||!Double.isFinite(distance[current]))break;if(current==1)break;visited[current]=true;for(int next=0;next<n;next++){if(visited[next]||next==current)continue;var a=nodes.get(current);var b=nodes.get(next);double candidate=distance[current]+a.distance(b);if(candidate<distance[next]&&segmentInside(room,a,b)){distance[next]=candidate;previous[next]=current;}}}
        if(!Double.isFinite(distance[1]))return null;var result=new LinkedList<Point>();for(int i=1;i!=0;i=previous[i])result.addFirst(nodes.get(i));return result;
    }
    static boolean segmentInside(Room room,Point a,Point b){if(!room.contains(a.x(),a.y())||!room.contains(b.x(),b.y()))return false;if(room.outline.isEmpty())return true;double dx=b.x()-a.x(),dy=b.y()-a.y();var cuts=new ArrayList<Double>(List.of(0d,1d));for(int i=0;i<room.outline.size();i++){var p=room.outline.get(i);var q=room.outline.get((i+1)%room.outline.size());double ex=q.x()-p.x(),ey=q.y()-p.y(),den=dx*ey-dy*ex;if(Math.abs(den)<1e-9)continue;double ox=p.x()-a.x(),oy=p.y()-a.y(),t=(ox*ey-oy*ex)/den,u=(ox*dy-oy*dx)/den;if(t>0&&t<1&&u>=0&&u<=1)cuts.add(t);}Collections.sort(cuts);for(int i=1;i<cuts.size();i++){double t=(cuts.get(i-1)+cuts.get(i))*.5;if(!room.contains(a.x()+dx*t,a.y()+dy*t))return false;}return true;}
    public static String roomAt(Campaign s,double x,double y){return s.rooms.values().stream().filter(r->r.contains(x,y)).map(r->r.id).findFirst().orElse("");}
    public static String ownerAt(Campaign s,double x,double y){String room=roomAt(s,x,y);if(!room.isEmpty())return room;var p=new Point(x,y);for(var d:s.doors.values()){double length=d.pa.distance(d.pb);if(p.distance(d.pa)+p.distance(d.pb)<=length+1)return p.distance(d.pa)<=d.pa.distance(d.center())?d.a:d.b;}return "";}
    public static boolean blocked(Campaign s,List<String> doors){return doors.stream().anyMatch(id->!s.doors.get(id).passable());}
    public static Point advance(Point p,List<Point> path,double distance){while(distance>0&&!path.isEmpty()){var next=path.getFirst();double d=p.distance(next);if(d<=distance){p=next;path.removeFirst();distance-=d;}else{p=p.lerp(next,distance/d);break;}}return p;}
}
