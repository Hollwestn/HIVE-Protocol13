package ru.hive13.model;

import java.util.*;
import static ru.hive13.model.Model.*;

/** Chapter geometry and physical operations, shared by the HUD and authority. */
public final class CampaignPlan {
    public record Operation(String id,String title,String description,double seconds,Map<String,Integer> cost,String needs,String done,String permission,int owner){}
    private static final Map<Integer,List<WorldObjects.Fixture>> FIXTURES=new LinkedHashMap<>();
    private static final Map<String,Operation> OPS=new LinkedHashMap<>();
    public static final Set<String> PERMISSIONS=Set.of("MEMORY","CONTACT","INTEGRATION","STOP_CYCLES");
    public static final List<String> ROUTES=List.of("exodus","bastion","cleansing","symbiosis","protocol","awakening");
    private CampaignPlan(){}
    private static Map<String,Integer> cost(Object...kv){var m=new LinkedHashMap<String,Integer>();for(int i=0;i<kv.length;i+=2)m.put((String)kv[i],(Integer)kv[i+1]);return m;}
    private static void op(String id,String title,String text,int time,Map<String,Integer> cost,String needs,String done,String permission,int owner){OPS.put(id,new Operation(id,title,text,time,cost,needs,done,permission,owner));}
    private static void fixture(int act,String id,String name,int room,int dx,String icon,String...actions){var p=roomPoint(act,room);FIXTURES.computeIfAbsent(act,k->new ArrayList<>()).add(new WorldObjects.Fixture(id,name,"R"+room,p.x()+dx,p.y()+100,icon,List.of(actions)));}
    static {
        op("CH_CACHE","Вскрыть аварийный запас","Конечный запас: 8 деталей, 10 батарей, 4 фильтра, 6 медикаментов, 8 топлива, 8 пайков, 16 патронов. Шум привлекает тварей.",10,cost(),"","cache","",-1);
        op("CH_KIT","Собрать аптечку","Один общий медикамент превращается в личную аптечку; масса 0,7 кг.",5,cost("meds",1),"","","",-1);
        op("CH_REST","Перевести дух","Здоровье +8, выносливость +40, стресс −15 за паёк и воду. Укрытие не гарантирует безопасности.",10,cost("food",1),"","","",-1);
        op("STAIRS_UP","Подняться на верхний этаж","Физический переход занимает 8 секунд. Преследователь способен идти по лестнице следом.",8,cost(),"","","",-1);
        op("STAIRS_DOWN","Спуститься на нижний этаж","Возвращает к нижней лестничной площадке. Просмотр этажа сам по себе не перемещает героя.",8,cost(),"","","",-1);
        op("CH_LEAVE","Покинуть локацию","Каждый живой герой должен лично воспользоваться выходом. До этого глава не завершится.",5,cost(),"","","",-1);
        op("CH_DEPLOY","Открыть подготовительный гермоконтур","Открывает оба затвора транспорта. До открытия лечитесь и распределяйте запасы. Повторно загерметизировать контур нельзя; сюжетные сроки идут.",4,cost(),"","deploy","",-1);
        for(int act=3;act<=6;act++){
            fixture(act,"camp_armory","Шкаф сдерживания",0,50,"weapon","ARMORY","REARM");
            fixture(act,"camp_med","Полевой медпункт",0,125,"medical","CH_KIT","CH_REST");
            fixture(act,"chapter_exit","Групповой транспорт",0,215,"exit","CH_LEAVE");
            fixture(act,"camp_gate","Пульт гермоконтура",0,175,"door","CH_DEPLOY");
            fixture(act,"chapter_cache","Аварийный контейнер",3,180,"store","CH_CACHE");
            fixture(act,"stairs_up","Лестница / вверх",1,200,"exit","STAIRS_UP");
            fixture(act,"stairs_down","Лестница / вниз",5,200,"exit","STAIRS_DOWN");
            fixture(act,"chapter_hide","Ниша за коммуникациями",2,215,"shield","HIDE");
        }
        op("H_VALVE_A","Открыть питающий клапан","Удерживает линию 90 секунд. Второй клапан и насос должны быть включены до окончания окна.",8,cost(),"","","",-1);
        op("H_VALVE_B","Открыть сливной клапан","Вторая половина гидравлического контура, окно 90 секунд. Координируйтесь с напарником.",8,cost(),"","","",-1);
        op("H_PUMP","Запустить насос","Нужны оба открытых клапана. Останавливает приток и постепенно осушает нижний этаж.",12,cost("parts",2),"","hydro_pump","",-1);
        op("H_SAMPLE","Проанализировать воду","Выявляет очистку тяжёлых металлов живой колонией. Результат сохраняется как улика.",14,cost("filters",1),"hydro_pump","ecology_proven","",-1);
        op("H_ARCHIVE","Скопировать экоархив","Независимые записи подтверждают снабжение города и хронологию поставок.",10,cost(),"","eco_archive","",-1);
        op("H_CONTACT","Ответить Хору","Добровольный нейроконтакт: заражение головы +6. Открывает возможность симбиоза.",12,cost(),"ecology_proven","hor_contact","CONTACT",-1);
        fixture(3,"valve_a","Питающий клапан",1,70,"vent","H_VALVE_A");fixture(3,"valve_b","Сливной клапан",2,70,"vent","H_VALVE_B");
        fixture(3,"hydro_pump","Насосная установка",3,80,"power","H_PUMP");fixture(3,"hydro_sample","Лаборатория водного контура",6,90,"lab","H_SAMPLE");
        fixture(3,"eco_archive","Экоархив ГЭС",7,90,"terminal","H_ARCHIVE");fixture(3,"resonator","Резонатор Хора",4,125,"bio","H_CONTACT");
        op("E_REGISTER","Восстановить доступ Эдема","2 детали. Выживший Резник позволяет обойти повреждённые записи, но питание терминалов всё равно необходимо.",12,cost("parts",2),"","eden_access","",-1);
        op("E_ORIGINAL","Сверить исходные подписи","Показывает, кто запустил циклы, почему закрыли память и как Куратор присвоил право повторять процедуру.",12,cost("batteries",1),"eden_access","origin_signatures","",-1);
        op("E_EVA","Поговорить с другой Евой","Копия помнит предыдущий выход. Она просит вывезти её, сохранив самостоятельность.",10,cost(),"eden_access","eva_met","",-1);
        op("E_SAVE_EVA","Эвакуировать Еву из изолятора","2 медикамента. Ева уходит с проводником к транспорту, её показания сохраняются.",14,cost("meds",2),"eva_met","eva_saved","",-1);
        op("E_PROXY","Извлечь технический ключ спящего","Только для отсутствующего спящего героя. Две батареи; тело и память спящего не изменяются.",12,cost("batteries",2),"eden_access","dormant_key","",-1);
        fixture(4,"eden_register","Регистратура Эдема",1,70,"terminal","E_REGISTER");fixture(4,"eden_origin","Реестр оригиналов",6,80,"terminal","E_ORIGINAL");
        fixture(4,"eva_copy","Изолятор / Ева",7,170,"medical","E_EVA","E_SAVE_EVA");fixture(4,"dormant_archive","Архив спящей капсулы",4,170,"terminal","E_PROXY");
        for(int i=0;i<3;i++){
            String key="E_KEY_"+i,decline="E_DECLINE_"+i;
            op(key,"Восстановить личный ключ / "+(i+1),"Личная процедура владельца. Возвращает фрагмент памяти и полный ключ; заражение головы +3. Другой игрок не может согласиться за вас.",12,cost(),"eden_access","key_"+i,"MEMORY",i);
            op(decline,"Отказаться от памяти / "+(i+1),"Сохраняет ваше право на отказ. Выдаёт ограниченную подпись для обычных маршрутов; истинное пробуждение требует полного ключа.",6,cost(),"eden_access","declined_"+i,"",i);
            fixture(4,"personal_"+i,"Личный архив / "+(i+1),i==0?2:i==1?4:6,45,"terminal",key,decline);
        }
        op("C_CLINIC","Вывести пациентов клиники","Резервное питание ограничено: 300 секунд от прибытия. После стабилизации эвакуацию ведёт персонал.",16,cost("meds",2),"","clinic_saved","",-1);
        op("C_KOVCHEG","Заключить договор с Ковчегом","2 пайка и 2 фильтра защищают убежища. Открывает маршрут Оплота.",12,cost("food",2,"filters",2),"","pact_kovcheg","",-1);
        op("C_AION","Проверить сеть AION","3 детали и батарея дают независимый сетевой аудит. Открывает Новый протокол.",14,cost("parts",3,"batteries",1),"","network_audit","",-1);
        op("C_FOLLOWERS","Помочь саду Последователей","2 фильтра восстанавливают споровый контур. Доверие даёт возможность мирного симбиоза, но не личное согласие.",12,cost("filters",2),"ecology_proven","pact_followers","",-1);
        op("C_DATES","Сверить городской архив","Независимые даты, партии воды и подписи доказывают подмену времени без доверия к Куратору.",10,cost(),"","city_dates","",-1);
        op("C_D00","Установить личность Д-00","Аудит и подписи оригиналов раскрывают четвёртое сознание: интегрированного оператора Даниила. Он просит переносной якорь.",14,cost(),"network_audit,origin_signatures","d00_known","",-1);
        op("C_COUNCIL","Открыть совет города","Представляет все шесть маршрутов. Их доступность зависит от доказательств, договоров и личных разрешений P.",10,cost(),"city_dates","council_open","",-1);
        fixture(5,"clinic","Клиника подстанции",1,75,"medical","C_CLINIC");fixture(5,"kovcheg","Представитель Ковчега",2,75,"shield","C_KOVCHEG");
        fixture(5,"aion","Сетевой узел AION",3,75,"power","C_AION");fixture(5,"followers","Сад Последователей",4,75,"bio","C_FOLLOWERS");
        fixture(5,"city_archive","Независимый архив",5,75,"terminal","C_DATES");fixture(5,"d00","Повреждённый голос / Д-00",6,75,"radio","C_D00");fixture(5,"council","Совет Нового Рассвета",7,75,"terminal","C_COUNCIL");
        op("N_TUNNEL","Расчистить эвакуационный тоннель","Две детали, шум. Открывает физический маршрут транспорта.",16,cost("parts",2),"route_exodus","tunnel_ready","",-1);
        op("N_BEACON","Подключить маяк конвоя","Батарея позволяет транспорту пройти слепую зону.",12,cost("batteries",1),"route_exodus,tunnel_ready","beacon_ready","",-1);
        op("N_CONVOY","Снарядить конвой","3 пайка и 10 воды. После подготовки каждый герой лично уходит через транспорт.",18,cost("food",3),"beacon_ready","convoy_ready","",-1);
        op("N_PRESSURE","Восстановить затворы Оплота","3 детали восстанавливают давление жилых контуров.",18,cost("parts",3),"route_bastion","pressure_ready","",-1);
        op("N_VENT","Запитать фильтрацию Оплота","2 фильтра защищают жителей; затем два пульта должны подтвердить контур.",14,cost("filters",2),"pressure_ready","vent_ready","",-1);
        op("N_CUT","Отсечь питательную линию","2 топлива; колония теряет питание, но остаётся живой.",16,cost("fuel",2),"route_cleansing","nutrient_cut","",-1);
        op("N_RUNOFF","Отвести заражённый сток","2 детали, вода −25. Нарушение очистки имеет последствия для города.",16,cost("parts",2),"nutrient_cut","runoff_cut","",-1);
        op("N_HEAT","Запустить тепловую изоляцию","Топливо. Твари подавлены на 90 секунд; экологическая функция утрачена. Это временное сдерживание.",14,cost("fuel",1),"runoff_cut","thermal_ready","",-1);
        op("N_RESONANCE","Настроить общий резонатор","Две батареи задают безопасную частоту контакта.",14,cost("batteries",2),"route_symbiosis","resonance_ready","",-1);
        op("N_BLOOD","Собрать биологический контур","2 медикамента, 2 фильтра. Затем каждый участник лично выполняет настройку.",14,cost("meds",2,"filters",2),"resonance_ready","blood_ready","",-1);
        op("N_ATTUNE","Лично подключиться к Хору","Необратимо: заражение головы +10 и интеграция сознания. Хор распознаёт ваш сигнал и прекращает нападения на вас. Требуется ваше разрешение P.",10,cost(),"blood_ready","","INTEGRATION",-1);
        op("N_AUDIT","Проверить независимые модули","2 детали и 2 батареи. Сверяет управление с исходными согласиями.",18,cost("parts",2,"batteries",2),"route_protocol","modules_verified","",-1);
        op("N_OFFLINE","Физически отключить Куратора","Отключает старое управление и его ложные сообщения. Затем подпишите новые ограничения.",12,cost(),"modules_verified","curator_offline","",-1);
        op("N_PROTOCOL","Подписать новый протокол","Запрещает автоматическое восстановление без нового согласия живущих людей.",16,cost(),"curator_offline","protocol_signed","",-1);
        op("N_KEYS","Сверить три исходных ключа","Только полные личные ключи и документы оригиналов. Все разрешения на остановку циклов должны оставаться действующими.",18,cost(),"route_awakening","keys_verified","STOP_CYCLES",-1);
        op("N_ANCHOR","Собрать физический якорь Эхо","Две батареи и деталь. Даёт Эхо доступ к двум пультам; сохраняет Д-00 при пробуждении.",16,cost("batteries",2,"parts",1),"","echo_anchor","",-1);
        op("N_SYNC_A","Подтвердить пульт А","Второй, другой участник должен подтвердить пульт Б в течение 60 секунд симуляции. После паузы таймер продолжается.",6,cost(),"","","",-1);
        op("N_SYNC_B","Подтвердить пульт Б","Требуется другой участник, пульт А и окно 60 секунд. Оружие и бег прерывают операцию.",6,cost(),"","","",-1);
        op("N_STOP","Остановить циклы восстановления","Необратимо. Нужны ключи, якорь, синхронизация и личное разрешение каждого активного сознания.",15,cost(),"keys_verified,echo_anchor,node_synced","cycles_stopped","STOP_CYCLES",-1);
        fixture(6,"node_tunnel","Транспортный тоннель",1,60,"exit","N_TUNNEL","N_PRESSURE","N_CUT");fixture(6,"node_beacon","Внешняя станция",2,60,"radio","N_BEACON","N_VENT","N_RUNOFF");
        fixture(6,"node_control","Главный пульт",3,60,"terminal","N_CONVOY","N_HEAT","N_RESONANCE","N_AUDIT","N_KEYS");
        fixture(6,"node_bio","Нейробиологический контур",4,60,"bio","N_BLOOD","N_ATTUNE");fixture(6,"node_anchor","Мастерская якоря",5,65,"power","N_ANCHOR");
        fixture(6,"node_a","Пульт синхронизации А",2,175,"terminal","N_SYNC_A");fixture(6,"node_b","Пульт синхронизации Б",6,175,"terminal","N_SYNC_B");
        fixture(6,"node_final","Узел исходных подписей",7,75,"terminal","N_OFFLINE","N_PROTOCOL","N_STOP");
    }
    public static Operation operation(String action){return OPS.get(action);}
    public static Collection<WorldObjects.Fixture> objects(int act){return FIXTURES.getOrDefault(act,List.of());}
    public static WorldObjects.Fixture object(int act,String id){return objects(act).stream().filter(f->f.id().equals(id)).findFirst().orElse(null);}
    public static Point roomPoint(int index){return new Point((index>=4?1000:0)+(index%4<2?70:530),index%4==0||index%4==3?300:60);}
    public static Point roomPoint(int act,int index){double left=act==4?110:act==5?45:act==6?95:70,right=act==4?535:act==5?500:act==6?550:530;return new Point((index>=4?1000:0)+(index%4<2?left:right),index%4==0||index%4==3?(act==4?290:300):(act==5?80:act==6?45:60));}
    public static int floor(Campaign s,String room){var r=s.rooms.get(room);return r==null?0:r.floor;}
    public static String title(int act){return switch(act){case 1->"Объект-13";case 2->"Внешний мир";case 3->"Сердце колонии";case 4->"Эдем-09";case 5->"Раскол Нового Рассвета";case 6->"Первичный узел";default->"HIVE";};}
    public static String routeTitle(String route){return switch(route){case "exodus"->"Исход";case "bastion"->"Оплот";case "cleansing"->"Очищение";case "symbiosis"->"Симбиоз";case "protocol"->"Новый протокол";case "awakening"->"Истинное пробуждение";default->"Падение";};}
    public static void install(Campaign s,int act){
        EchoRules.reset(s);
        s.currentAct=act;s.layoutRevision=100+act;s.location="chapter_"+act;s.rooms.clear();s.doors.clear();s.creatures.clear();s.contacts.clear();s.noises.clear();s.drones.clear();
        s.progress.keySet().removeIf(k->k.contains(":OBJECT:")||k.startsWith("blackout:")||k.startsWith("uv:")||k.startsWith("sync_")||k.equals("hunter_stairs"));
        String[] names=switch(act){case 3->new String[]{"Берег / транспорт","Входной коллектор","Сливной коллектор","Насосный зал","Резонатор","Служебная лестница","Водная лаборатория","Экоархив"};case 4->new String[]{"Приёмный шлюз","Регистратура","Архив инженера","Служебный склад","Медицинский архив","Верхняя лестница","Исходные записи","Изолятор Евы"};case 5->new String[]{"Площадь / транспорт","Клиника","Укрытие Ковчега","Узел AION","Сад Последователей","Городской архив","Узел Д-00","Городской совет"};default->new String[]{"Эвакуационная площадка","Затворы","Внешний контур","Операторская","Биологический контур","Якорная мастерская","Пульт Б","Первичный узел"};};
        for(int i=0;i<8;i++){var p=roomPoint(act,i);var r=new Room("R"+i,names[i],p.x(),p.y(),act==5?300:260,act==4?150:140);r.floor=i/4;s.rooms.put(r.id,r);}
        for(int floor=0;floor<2;floor++){int base=floor*4;link(s,"G"+base,base,base+1);link(s,"G"+(base+1),base+1,base+2);link(s,"G"+(base+2),base+2,base+3);link(s,"G"+(base+3),base+3,base);}
        s.doors.get("G0").status=DoorStatus.SEALED;s.doors.get("G3").status=DoorStatus.SEALED;
        spawn(s,"CH"+act+"-HOUND","ГОНЧАЯ",act==4?3:7);spawn(s,"CH"+act+"-WHISPER","ШЕПТУН",act==3?2:6);
        if(act==3){s.rooms.get("R4").biomass=30;s.rooms.get("R6").biomass=18;s.progress.put("hydro_flood",25d);}
        s.progress.put("chapter_entered",s.gameTime);s.progress.put("director_next",s.gameTime+55);
        if(act==5)s.progress.put("clinic_deadline",s.gameTime+300);
        if(act==6)s.progress.put("node_deadline",s.gameTime+720);
    }
    private static void link(Campaign s,String id,int aa,int bb){var a=s.rooms.get("R"+aa);var b=s.rooms.get("R"+bb);var ac=a.center();var bc=b.center();Point pa,pb;if(ac.x()==bc.x()){boolean down=ac.y()<bc.y();pa=new Point(ac.x(),down?a.y+a.h:a.y);pb=new Point(bc.x(),down?b.y:b.y+b.h);}else{boolean right=ac.x()<bc.x();pa=new Point(right?a.x+a.w:a.x,ac.y());pb=new Point(right?b.x:b.x+b.w,bc.y());}s.doors.put(id,new Door(id,a.id,b.id,pa,pb,DoorStatus.OPEN));}
    private static void spawn(Campaign s,String id,String type,int room){var r=s.rooms.get("R"+room);var c=new Creature();c.id=id;c.type=type;c.roomId=c.home=r.id;c.x=r.x+220;c.y=r.y+35;s.creatures.add(c);}
    public static String doneFlag(Campaign s,Operation op){return op.done().equals("cache")?"chapter_cache_"+s.currentAct:op.done().equals("deploy")?"chapter_deployed_"+s.currentAct:op.done();}
    public static boolean has(Campaign s,String flags){return flags.isEmpty()||Arrays.stream(flags.split(",")).allMatch(s.flags::contains);}
    public static boolean allPermission(Campaign s,String permission){return s.actors.stream().filter(a->!a.life.equals("DORMANT")).allMatch(a->a.permissions.contains(permission));}
    public static int keys(Campaign s){int n=0;for(int i=0;i<3;i++)if(s.flags.contains("key_"+i)||s.flags.contains("declined_"+i)||s.actor(i).life.equals("DORMANT")&&s.flags.contains("dormant_key"))n++;return n;}
    public static String exitReason(Campaign s){return switch(s.currentAct){case 3->has(s,"hydro_pump,ecology_proven,eco_archive,hydro_policy")?"":"Нужны насос, анализ, экоархив и решение о водоснабжении";case 4->has(s,"origin_signatures,eva_met")&&keys(s)==3?"":"Нужны исходные подписи, встреча с Евой и ответы трёх личных архивов";case 5->s.finalRoute.isEmpty()?"Совет должен выбрать маршрут финала":"";case 6->finalReady(s)?"":"Закончите операции выбранного маршрута: "+routeTitle(s.finalRoute);default->"Неизвестная глава";};}
    public static boolean finalReady(Campaign s){return switch(s.finalRoute){case "exodus"->has(s,"convoy_ready");case "bastion"->has(s,"vent_ready,node_synced");case "cleansing"->has(s,"thermal_ready");case "symbiosis"->has(s,"blood_ready")&&allPermission(s,"INTEGRATION")&&s.actors.stream().filter(a->!a.life.equals("DORMANT")).allMatch(a->s.flags.contains("attuned_"+a.slot));case "protocol"->has(s,"protocol_signed");case "awakening"->has(s,"cycles_stopped");default->false;};}
    public static String routeReason(Campaign s,String route){return switch(route){case "exodus","cleansing"->"";case "bastion"->has(s,"pact_kovcheg")&&keys(s)>=2?"":"Нужны договор Ковчега и два личных ключа";case "symbiosis"->has(s,"hor_contact,ecology_proven,pact_followers")&&allPermission(s,"INTEGRATION")?"":"Нужны контакт, экология, помощь Последователям и личное разрешение каждого (P)";case "protocol"->has(s,"ai_lies_proved,network_audit")&&keys(s)>=2?"":"Нужны доказанная ложь, аудит AION и два ключа";case "awakening"->has(s,"key_0,key_1,key_2,origin_signatures,city_dates,eva_met,d00_known")&&allPermission(s,"STOP_CYCLES")?"":"Нужны три полных ключа, подписи, даты, Ева, Д-00 и личные разрешения (P)";default->"Нет такого маршрута";};}
    public static WorldObjects.Offer offer(Campaign s,Actor a,WorldObjects.Fixture f,String action){var op=operation(action);if(op==null)return new WorldObjects.Offer(action,action,"",0,Map.of(),"Нет операции");String reason="";
        if(!f.actions().contains(action))reason="Нет такой операции у объекта";else if(s.phase!=Phase.ACTION)reason="Нужна фаза действий";else if(!a.alive())reason="Нужно живое тело; Эхо использует терминальную команду";
        else if(op.owner()>=0&&op.owner()!=a.slot)reason="Это личный архив другого героя";else if(!op.permission().isEmpty()&&!a.permissions.contains(op.permission()))reason="Нет вашего личного разрешения: P / "+op.permission();
        else if(!has(s,op.needs()))reason="Сначала: "+op.needs();else if(!doneFlag(s,op).isEmpty()&&s.flags.contains(doneFlag(s,op)))reason="Операция уже завершена";
        if(reason.isEmpty()&&action.startsWith("E_KEY_")&&s.flags.contains("declined_"+a.slot))reason="Вы уже выбрали отказ от восстановления";
        if(reason.isEmpty()&&action.startsWith("E_DECLINE_")&&s.flags.contains("key_"+a.slot))reason="Ключ уже восстановлен; прошлое нельзя отменить";
        if(reason.isEmpty()&&action.equals("E_PROXY")&&s.actors.stream().noneMatch(x->x.life.equals("DORMANT")))reason="Спящего участника нет; чужой ключ защищён";
        if(reason.isEmpty()&&action.equals("H_PUMP")&&(s.progress.getOrDefault("valve_a_until",0d)<=s.gameTime||s.progress.getOrDefault("valve_b_until",0d)<=s.gameTime))reason="Нужно открыть оба клапана в одном окне 90 секунд";
        if(reason.isEmpty()&&action.equals("CH_LEAVE"))reason=exitReason(s);
        if(reason.isEmpty()&&action.equals("CH_KIT")&&a.carriedWeight()+.7>18)reason="Нужно 0,7 кг места";
        if(reason.isEmpty()&&action.equals("CH_REST")&&s.water<1)reason="Нет воды";
        if(reason.isEmpty()&&action.equals("N_CONVOY")&&s.water<10)reason="Нужно 10 воды";
        if(reason.isEmpty()&&action.equals("C_CLINIC")&&s.flags.contains("clinic_lost"))reason="Резерв исчерпан, пациенты погибли";
        if(reason.isEmpty()&&Set.of("N_KEYS","N_STOP").contains(action)&&!allPermission(s,"STOP_CYCLES"))reason="Нужно личное разрешение каждого сознания";
        if(reason.isEmpty()&&action.equals("N_ATTUNE")&&(!allPermission(s,"INTEGRATION")||s.flags.contains("attuned_"+a.slot)))reason="Нужны все разрешения; повторная интеграция недоступна";
        if(reason.isEmpty()&&action.startsWith("N_SYNC")&&!((s.finalRoute.equals("bastion")&&has(s,"vent_ready"))||(s.finalRoute.equals("awakening")&&has(s,"keys_verified,echo_anchor"))))reason="Сначала подготовьте систему выбранного маршрута";
        if(reason.isEmpty())for(var c:op.cost().entrySet())if(s.supplies.getOrDefault(c.getKey(),0)<c.getValue()){reason="Недостаточно: "+WorldObjects.resourceName(c.getKey());break;}
        return new WorldObjects.Offer(action,op.title(),op.description(),op.seconds(),op.cost(),reason);
    }
}
