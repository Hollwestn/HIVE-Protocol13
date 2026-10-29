package ru.hive13.model;
import static ru.hive13.model.Model.*;
/** Explicit test checkpoints, never substituted for a new STORY campaign. */
public final class VisualFixtures {
    private VisualFixtures(){}
    public static Campaign referenceA(){var s=WorldFactory.create("VISUAL_REFERENCE_A");s.cycle=7;s.power=43;s.ventilation=61;s.flags.add("lina_decided");s.rooms.values().forEach(r->r.oxygen=78);
        double[] hp={72,58,91},stress={41,63,37},stamina={68,52,74},infection={12,28,5};
        Point[] p={new Point(155,202),new Point(317,251),new Point(410,337)};String[] rooms={"A1","A2","B7"};
        for(int i=0;i<3;i++){var a=s.actor(i);a.hp=hp[i];a.stress=stress[i];a.stamina=stamina[i];for(var part:a.body.values())part.infection=infection[i];a.x=p[i].x();a.y=p[i].y();a.roomId=rooms[i];}
        var b=s.actor(1);String[] known={"head","neck","chest","abdomen","rightArm","leftArm","rightLeg","leftLeg"};double[] values={0,8,12,27,38,3,5,0};for(int i=0;i<known.length;i++)b.body.get(known[i]).infection=values[i];
        for(String id:new String[]{"pelvis","rightShoulder","leftShoulder","rightHand","leftHand"})b.body.get(id).infection=54.2;
        s.supplies.put("ammo",127);s.supplies.put("meds",3);s.supplies.put("filters",6);s.supplies.put("parts",4);s.supplies.put("batteries",2);
        s.discoveredRegions.add("sigma");s.actor(0).privateEvidence.add("engineer_order");s.sharedEvidence.add("conservation");return s;
    }
}
