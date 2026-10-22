package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.*;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * Production canvas UI: fixed RuneScape-inspired chrome, replaceable skill scene
 * and metrics, independent hitboxes, real client XP, and non-blocking effects.
 * The images are procedurally/vector rendered so they remain sharp under scaling.
 *
 * UI events never invoke child scripts directly. Requests are handed to the
 * Account Builder scheduler, and Stop uses its existing configuration lifecycle.
 */
final class KspClassicCinematicHud
{
    private static final int EXPANDED_W = 620, EXPANDED_H = 306;
    private static final int COMPACT_W = 436, COMPACT_H = 172;
    private static final Color GOLD = new Color(224, 179, 89);
    private static final Color PALE = new Color(242, 222, 178);
    private static final Color MUTED = new Color(174, 166, 148);
    private static final Color BG = new Color(17, 20, 22);
    private static final Color TEXT = new Color(228, 224, 207);
    private static final Color RED = new Color(170, 53, 49);
    private static final Color GREEN = new Color(43, 123, 66);
    private final KspAccountBuilderScript script;
    private final KspAccountBuilderConfig config;
    private final ConfigManager configManager;
    private final EnumMap<Skill, Integer> xpBase = new EnumMap<>(Skill.class);
    private final EnumMap<Skill, Long> xpSince = new EnumMap<>(Skill.class);
    private volatile Map<String, Rectangle> targets = Collections.emptyMap();
    private volatile Dimension lastSize = new Dimension(COMPACT_W, COMPACT_H);
    private volatile Point pointer = new Point(-100, -100);
    private volatile boolean compact = false;
    private volatile boolean autoCompact = false;
    private long accountHash = Long.MIN_VALUE;
    private volatile boolean showSettings = false;
    private volatile String page = "Skills";
    private volatile String pressed;
    private volatile float scale = 1f;

    KspClassicCinematicHud(KspAccountBuilderScript script, KspAccountBuilderConfig config, ConfigManager configManager)
    {
        this.script = script;
        this.config = config;
        this.configManager = configManager;
    }

    Dimension render(Graphics2D base, int canvasWidth, Rectangle absoluteBounds)
    {
        autoCompact = canvasWidth < 885;
        compact = config.classicCompact() || autoCompact;
        int w = compact ? COMPACT_W : EXPANDED_W;
        int h = compact ? COMPACT_H : EXPANDED_H;
        float factor = Math.min(1.6f, Math.max(0.65f, (canvasWidth - 16f) / w));
        if (canvasWidth >= w + 16) factor = 1f;
        scale = factor;
        lastSize = new Dimension(Math.round(w * factor), Math.round(h * factor));
        Map<String, Rectangle> hitboxes = new LinkedHashMap<>();
        Graphics2D g = (Graphics2D) base.create();
        try
        {
            g.scale(factor, factor);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            String rawTask = script.getCurrentTaskName();
            Module module = module(rawTask);
            int px = absoluteBounds == null ? -999 : Math.round((pointer.x - absoluteBounds.x) / factor);
            int py = absoluteBounds == null ? -999 : Math.round((pointer.y - absoluteBounds.y) / factor);
            paintFrame(g, w, h);
            paintHeader(g, w, hitboxes);
            if (compact)
            {
                paintCompact(g, module, rawTask, hitboxes);
            }
            else
            {
                paintExpanded(g, module, rawTask, hitboxes);
            }
            targets = Collections.unmodifiableMap(hitboxes);
            paintHover(g, px, py, w, h, module, rawTask);
        }
        finally
        {
            g.dispose();
        }
        return new Dimension(lastSize);
    }

    void pointer(Point p) { pointer = new Point(p); }

    /** Returns true if the mouse event belongs to and is consumed by the HUD. */
    boolean input(MouseEvent e, Rectangle bounds, boolean active)
    {
        if (!active || bounds == null || bounds.isEmpty()) return false;
        if (!bounds.contains(e.getPoint())) return false;
        int x = Math.round((e.getX() - bounds.x) / scale);
        int y = Math.round((e.getY() - bounds.y) / scale);
        String target = targetAt(x, y);
        boolean intercept = config.classicBlockClicks() || target != null;
        if (!intercept) return false;
        if (e.getID() == MouseEvent.MOUSE_PRESSED)
        {
            pressed = e.getButton() == MouseEvent.BUTTON1 ? target : null;
        }
        else if (e.getID() == MouseEvent.MOUSE_RELEASED)
        {
            if (e.getButton() == MouseEvent.BUTTON1 && target != null && target.equals(pressed))
            {
                activate(target);
            }
            pressed = null;
        }
        e.consume();
        return true;
    }

    boolean captures(Rectangle bounds, Point at)
    {
        return bounds != null && bounds.contains(at) && config.classicBlockClicks();
    }

    private String targetAt(int x, int y)
    {
        for (Map.Entry<String, Rectangle> entry : targets.entrySet())
            if (entry.getValue().contains(x, y)) return entry.getKey();
        return null;
    }

    private void activate(String action)
    {
        switch (action)
        {
            case "pause":
                if (script.isOverlayPaused()) script.requestOverlayResume();
                else script.requestOverlayPause();
                break;
            case "stop":
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "runAccountBuilder", false);
                break;
            case "skip":
                if (script.mayOverlaySkip()) script.requestOverlaySkip();
                break;
            case "settings": showSettings = !showSettings; break;
            case "compact":
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "classicCompact", !config.classicCompact());
                break;
            case "input":
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "classicBlockClicks", !config.classicBlockClicks());
                break;
            case "effects":
                configManager.setConfiguration(KspAccountBuilderConfig.CONFIG_GROUP, "classicAnimations", !config.classicAnimations());
                break;
            case "Status": case "Skills": case "Tasks": page = action; showSettings = false; break;
            default: break;
        }
    }

    private void paintFrame(Graphics2D g, int w, int h)
    {
        g.setPaint(new GradientPaint(0,0,new Color(46,46,43,251),0,h,BG));
        g.fillRoundRect(0,0,w-1,h-1,11,11);
        g.setStroke(new BasicStroke(2.1f));
        g.setColor(new Color(78,52,24));g.drawRoundRect(1,1,w-3,h-3,11,11);
        g.setStroke(new BasicStroke(1.1f));
        g.setColor(GOLD);g.drawRoundRect(4,4,w-9,h-9,9,9);
        g.setPaint(new GradientPaint(0,7,new Color(63,56,42),0,34,new Color(19,21,23)));
        g.fillRoundRect(8,8,w-16,30,6,6);
        g.setColor(new Color(138,98,49));g.drawLine(9,39,w-10,39);
        for (int x=16;x<w;x+=32)
        {
            g.setColor(new Color(240,185,86,13));
            g.drawLine(x,9,x+13,36);
        }
        int[] xs={9,w-9,9,w-9};
        int[] ys={9,9,h-9,h-9};
        for(int i=0;i<4;i++)
        {
            g.setColor(GOLD);g.fillOval(xs[i]-2,ys[i]-2,5,5);
        }
    }

    private void paintHeader(Graphics2D g,int w,Map<String,Rectangle> buttons)
    {
        g.setColor(GOLD);g.fillRoundRect(16,16,7,17,2,2);
        g.fillRoundRect(25,12,7,21,2,2);
        g.fillRoundRect(34,8,7,25,2,2);
        write(g,"KSP Account Builder",49,29,16,true,PALE,w-188);
        write(g,"Runtime "+duration(script.getRuntimeSeconds()),w-142,27,10,false,MUTED,107);
        if (!autoCompact)
            button(g,buttons,"compact",w-35,13,23,22,compact?"+":"−",new Color(59,54,42),12);
        else
            write(g,"□",w-29,29,13,false,MUTED,17);
    }

    private void paintExpanded(Graphics2D g,Module m,String task,Map<String,Rectangle> buttons)
    {
        int sceneX=113,sceneY=47,sceneW=307,sceneH=193;
        panel(g,13,45,94,217);
        String[] nav={"Status","Skills","Tasks"};
        for(int i=0;i<nav.length;i++)
        {
            String label=nav[i];
            button(g,buttons,label,19,53+i*44,82,35,label,
                    label.equals(page)?new Color(93,68,35):new Color(29,32,34),11);
        }
        button(g,buttons,"settings",19,190,82,35,"Settings",new Color(37,39,39),10);
        if(showSettings)
        {
            paintSettings(g,sceneX,sceneY,sceneW,sceneH,buttons);
        }
        else if("Status".equals(page))
        {
            paintStatus(g,sceneX,sceneY,sceneW,sceneH);
        }
        else if("Tasks".equals(page))
        {
            paintTasks(g,sceneX,sceneY,sceneW,sceneH);
        }
        else
        {
            drawScene(g,sceneX,sceneY,sceneW,sceneH,m);
            g.setColor(new Color(7,10,9,206));g.fillRoundRect(sceneX+9,sceneY+9,sceneW-18,49,10,10);
            skillMark(g,m,sceneX+28,sceneY+34,19);
            write(g,m.name,sceneX+53,sceneY+31,18,true,PALE,sceneW-69);
            write(g,shortText(script.getOverlayTaskDetail()),sceneX+53,sceneY+48,11,false,TEXT,sceneW-69);
            g.setColor(new Color(10,14,16,218));
            g.fillRoundRect(sceneX+9,sceneY+150,sceneW-18,34,7,7);
            write(g,"CURRENT TASK",sceneX+19,sceneY+164,9,true,GOLD,100);
            write(g,task.replace('_',' '),sceneX+19,sceneY+178,10,false,TEXT,sceneW-34);
        }
        paintStats(g,426,47,181,193,m);
        paintStatusStrip(g,114,245,492,17,m);
        button(g,buttons,"pause",114,267,128,29,script.isOverlayPaused()?"▶ RESUME":"Ⅱ PAUSE",new Color(109,79,33),12);
        if (script.mayOverlaySkip())
            button(g,buttons,"skip",247,267,106,29,"SKIP TASK",new Color(65,61,48),11);
        else
            disabled(g,247,267,106,29,"SKIP LOCKED");
        button(g,buttons,"stop",358,267,105,29,"■ STOP",RED,11);
        button(g,buttons,"settings",468,267,138,29,showSettings?"CLOSE SETTINGS":"⚙ SETTINGS",new Color(47,50,50),11);
    }

    private void paintCompact(Graphics2D g,Module m,String task,Map<String,Rectangle> buttons)
    {
        drawScene(g,11,45,222,88,m);
        g.setColor(new Color(8,12,14,207));g.fillRoundRect(19,54,203,35,7,7);
        skillMark(g,m,37,72,16);
        write(g,m.name,57,71,15,true,PALE,158);
        write(g,shortText(script.getOverlayTaskDetail()),57,83,9,false,TEXT,160);
        SkillXp xp = read(m.skill);
        panel(g,237,45,187,88);
        write(g,xp.skill==null ? m.name : "Level "+xp.level,246,61,11,true,PALE,165);
        progress(g,246,68,168,13,xp.progress,m.tint);
        write(g,xp.skill==null?"Task progress unavailable":fmt(xp.exp)+" XP · "+xp.percent+"%",246,94,10,false,TEXT,164);
        write(g,script.isOverlayPaused()?"PAUSED":shortText(task),246,113,10,true,script.isOverlayPaused()?GOLD:MUTED,170);
        button(g,buttons,"pause",11,139,122,27,script.isOverlayPaused()?"▶ RESUME":"Ⅱ PAUSE",new Color(105,76,31),11);
        if (script.mayOverlaySkip())
            button(g,buttons,"skip",139,139,91,27,"SKIP",new Color(62,61,54),11);
        else
            disabled(g,139,139,91,27,"LOCKED");
        button(g,buttons,"stop",236,139,90,27,"■ STOP",RED,11);
        button(g,buttons,"settings",332,139,92,27,showSettings?"CLOSE":"⚙ OPTS",new Color(49,52,51),10);
        if(showSettings)
        {
            g.setColor(new Color(12,14,17,240));g.fillRoundRect(11,45,413,88,7,7);
            button(g,buttons,"input",23,59,187,28,config.classicBlockClicks()?"✓ BLOCK CLICKS":"ALLOW CLICKS",new Color(64,63,45),10);
            button(g,buttons,"effects",217,59,191,28,config.classicAnimations()?"✓ ANIMATIONS":"ANIMATIONS OFF",new Color(64,63,45),10);
            write(g,"Game area outside this HUD is never locked.",23,118,10,false,MUTED,380);
        }
    }

    private void paintSettings(Graphics2D g,int x,int y,int w,int h,Map<String,Rectangle> buttons)
    {
        panel(g,x,y,w,h);
        write(g,"HUD SETTINGS",x+18,y+27,17,true,PALE,270);
        write(g,"Mouse capture applies only inside the panel.",x+18,y+47,11,false,MUTED,270);
        button(g,buttons,"input",x+18,y+63,w-36,33,config.classicBlockClicks()?"✓ Block game clicks under HUD":"Allow game clicks under HUD",new Color(63,60,42),12);
        button(g,buttons,"effects",x+18,y+105,w-36,33,config.classicAnimations()?"✓ Cinematic skill effects":"Cinematic effects disabled",new Color(62,57,47),12);
        write(g,"Controls remain clickable, even when blocking is off.",x+18,y+163,10,false,MUTED,270);
    }

    private void paintStatus(Graphics2D g,int x,int y,int w,int h)
    {
        panel(g,x,y,w,h);
        write(g,"ACCOUNT STATUS",x+14,y+27,16,true,PALE,w-24);
        field(g,x+14,y+57,"Task",script.getCurrentTaskName().replace('_',' '),w-28);
        field(g,x+14,y+82,"Status",shortText(Microbot.status),w-28);
        field(g,x+14,y+107,"Account playtime",duration(script.getAccountPlayTimeSeconds()),w-28);
        field(g,x+14,y+132,"Next break",script.isAnyBreakActive()?duration(script.getBreakTimeRemainingSeconds()):duration(script.getTimeUntilBreakSeconds()),w-28);
        field(g,x+14,y+157,"Task switching",duration(script.getTimeUntilActivitySwitchSeconds()),w-28);
    }

    private void paintTasks(Graphics2D g,int x,int y,int w,int h)
    {
        panel(g,x,y,w,h);
        write(g,"ACTIVITY CONTROL",x+14,y+27,16,true,PALE,w-24);
        field(g,x+14,y+58,"Active",script.getCurrentTaskName().replace('_',' '),w-28);
        field(g,x+14,y+88,"Mode",config.trainSingleSkill()?"Single skill":config.runSingleQuest()?"Single quest":"Automatic",w-28);
        field(g,x+14,y+118,"Switch ETA",duration(script.getTimeUntilActivitySwitchSeconds()),w-28);
        write(g,"Task queue is managed by Account Builder.",x+14,y+151,10,false,MUTED,w-20);
        write(g,"No synthetic queued tasks are shown.",x+14,y+165,10,false,MUTED,w-20);
    }

    private void paintStats(Graphics2D g,int x,int y,int w,int h,Module m)
    {
        panel(g,x,y,w,h);
        SkillXp xp = read(m.skill);
        skillMark(g,m,x+24,y+25,18);
        write(g,xp.skill==null?m.name:"Level "+xp.level,x+46,y+26,13,true,PALE,w-56);
        progress(g,x+11,y+39,w-22,16,xp.progress,m.tint);
        write(g,xp.skill==null?"No skill XP metric":fmt(xp.exp)+" / "+fmt(xp.next)+" XP",x+12,y+68,10,false,TEXT,w-20);
        line(g,x+12,y+86,w-25);
        field(g,x+12,y+103,"XP gained",xp.skill==null?"--":fmt(xp.gained),w-24);
        field(g,x+12,y+125,"XP/hour",xp.rate < 0?"--":fmt(xp.rate),w-24);
        field(g,x+12,y+147,"To level",xp.skill==null||xp.level>=99?"--":fmt(Math.max(0,xp.next-xp.exp))+" XP",w-24);
        line(g,x+12,y+157,w-25);
        write(g,script.isOverlayPaused()?"● PAUSED":script.isAnyBreakActive()?"● ON BREAK":"● RUNNING",x+13,y+179,11,true,script.isOverlayPaused()?GOLD:new Color(114,211,132),w-26);
    }

    private void paintStatusStrip(Graphics2D g,int x,int y,int w,int h,Module m)
    {
        g.setColor(new Color(31,37,39));g.fillRoundRect(x,y,w,h,5,5);
        g.setColor(new Color(102,85,48));g.drawRoundRect(x,y,w,h,5,5);
        write(g,"CURRENT TASK: "+script.getCurrentTaskName().replace('_',' '),x+8,y+12,10,false,TEXT,258);
        String right=script.isAnyBreakActive()?"Break "+duration(script.getBreakTimeRemainingSeconds()):"Next break "+duration(script.getTimeUntilBreakSeconds());
        write(g,right,x+319,y+12,10,false,MUTED,164);
    }

    private void paintHover(Graphics2D g,int x,int y,int w,int h,Module m,String task)
    {
        if(x<0||y<0||x>=w||y>=h || showSettings) return;
        Rectangle bar = compact ? new Rectangle(246,68,168,17):new Rectangle(437,86,160,18);
        if(!bar.contains(x,y) && !(compact ? new Rectangle(11,45,222,88):new Rectangle(113,47,307,193)).contains(x,y))
        {
            return;
        }
        SkillXp xp=read(m.skill);
        List<String> tips=new ArrayList<>();
        if(bar.contains(x,y) && xp.skill!=null)
        {
            tips.add(m.name+" experience");
            tips.add("XP: "+fmt(xp.exp));
            tips.add("Next level: "+fmt(Math.max(0,xp.next-xp.exp))+" XP");
            tips.add("Progress: "+xp.percent+"%");
            tips.add("XP/hour: "+(xp.rate<0?"warming up":fmt(xp.rate)));
        }
        else
        {
            tips.add("Current: "+task.replace('_',' '));
            tips.add(shortText(script.getOverlayTaskDetail()));
            tips.add("Session runtime: "+duration(script.getRuntimeSeconds()));
        }
        int tw=compact?225:225, th=12+tips.size()*16;
        int tx=Math.max(8,Math.min(w-tw-8,x+13));
        int ty=y+th+16>h?Math.max(43,y-th-12):y+13;
        g.setColor(new Color(5,8,11,244));g.fillRoundRect(tx,ty,tw,th,6,6);
        g.setColor(GOLD);g.drawRoundRect(tx,ty,tw,th,6,6);
        for(int i=0;i<tips.size();i++) write(g,tips.get(i),tx+9,ty+18+i*16,i==0?11:10,i==0,i==0?PALE:TEXT,tw-20);
    }

    private void drawScene(Graphics2D g,int x,int y,int w,int h,Module m)
    {
        Shape prior=g.getClip();
        g.setClip(x,y,w,h);
        Color sky=m.tint;
        g.setPaint(new GradientPaint(x,y,new Color(Math.max(8,sky.getRed()/3),Math.max(17,sky.getGreen()/3),Math.max(27,sky.getBlue()/3)),x,y+h,new Color(22,32,27)));
        g.fillRect(x,y,w,h);
        // Stylised OSRS landscape; consistent composition but unique task geometry.
        for(int i=0;i<8;i++)
        {
            int px=x+(i*83+17)%w, py=y+60+(i*19)%Math.max(16,h-63);
            g.setColor(new Color(5,12,15,70));
            g.fillOval(px-52,py-15,110,64);
        }
        if(m.key.equals("WOODCUTTING")||m.key.equals("FIREMAKING")||m.key.equals("QUEST"))
        {
            for(int i=0;i<4;i++)
            {
                int xx=x+34+i*82;
                g.setColor(new Color(72,46,25));g.fillRoundRect(xx,y+73,14,102,5,5);
                g.setColor(new Color(36+i*4,83+i*6,36));
                g.fillOval(xx-33,y+29,85,81);
                g.setColor(new Color(66,115,49,170));g.fillOval(xx-15,y+21,55,49);
            }
        }
        if(m.key.equals("MINING")||m.key.equals("RUNE_ESSENCE")||m.key.equals("RUNECRAFTING"))
        {
            for(int i=0;i<6;i++)
            {
                int xx=x+12+i*56, yy=y+83+(i%3)*11;
                Path2D rock=new Path2D.Double();
                rock.moveTo(xx,yy+59);rock.lineTo(xx+10,yy+10);rock.lineTo(xx+29,yy-8);
                rock.lineTo(xx+48,yy+44);rock.lineTo(xx+39,yy+65);rock.closePath();
                g.setColor(new Color(74,85,91));g.fill(rock);
                g.setColor(new Color(152,167,171));g.draw(rock);
                g.setColor(new Color(39,52,63));g.drawLine(xx+29,yy-8,xx+30,yy+48);
            }
        }
        if(m.key.equals("FISHING"))
        {
            g.setColor(new Color(23,70,113));g.fillRect(x,y+109,w,h-90);
            g.setColor(new Color(76,141,183));
            for(int i=0;i<10;i++)g.drawArc(x+i*52,y+124+(i%3)*17,48,13,0,170);
            g.setStroke(new BasicStroke(3f));g.setColor(new Color(126,92,49));g.drawLine(x+79,y+138,x+188,y+31);
            g.setColor(new Color(210,229,240));g.drawArc(x+170,y+70,48,68,15,155);
        }
        if(m.key.equals("COOKING")||m.key.equals("SMELTING")||m.key.equals("SMITHING")||m.key.equals("FIREMAKING"))
        {
            g.setColor(new Color(65,52,43));g.fillRoundRect(x+85,y+68,150,117,16,16);
            g.setColor(new Color(37,32,31));g.fillOval(x+110,y+76,111,84);
            g.setColor(new Color(239,119,38,188));g.fillOval(x+129,y+99,69,53);
            g.setColor(new Color(249,193,60));g.fillOval(x+148,y+115,38,29);
            g.setColor(new Color(100,109,112));g.fillRoundRect(x+115,y+143,115,16,3,3);
        }
        if(m.key.equals("MELEE"))
        {
            g.setColor(new Color(93,116,57));g.fillRect(x,y+119,w,h-116);
            g.setColor(new Color(125,84,48));g.fillOval(x+122,y+93,105,75);
            g.setColor(new Color(69,48,31));g.fillOval(x+177,y+91,40,34);
            g.setColor(new Color(233,211,176));g.drawLine(x+120,y+147,x+183,y+104);
            g.setColor(RED);g.fillRoundRect(x+121,y+81,108,6,3,3);
        }
        if(m.key.equals("CRAFTING")||m.key.equals("GE")||m.key.equals("TUTORIAL"))
        {
            g.setColor(new Color(103,75,46));g.fillRect(x+45,y+137,230,17);
            g.setColor(new Color(78,54,34));g.fillRect(x+60,y+153,12,31);
            g.setColor(new Color(165,122,62));g.fillRoundRect(x+124,y+108,58,32,4,4);
            g.setColor(new Color(215,164,73));g.drawLine(x+130,y+116,x+172,y+116);
        }
        if(m.key.equals("RUNECRAFTING"))
        {
            g.setColor(new Color(112,91,217,200));
            for(int i=0;i<3;i++)
            {
                int xx=x+90+i*59;
                g.setStroke(new BasicStroke(2f));g.drawOval(xx,y+52,36,55);
                g.drawLine(xx+18,y+52,xx+18,y+107);
            }
        }
        if(config.classicAnimations())
        {
            long time=System.currentTimeMillis();
            for(int i=0;i<22;i++)
            {
                double phase=((time/26d+i*31)%220)/220.0;
                int px=x+(int)((i*53+phase*25)%w);
                int py=y+21+(int)((h-34)*(1-phase));
                int alpha=(int)(25+85*(1-phase));
                g.setColor(new Color(m.tint.getRed(),m.tint.getGreen(),m.tint.getBlue(),alpha));
                int r=i%4+1;
                g.fillOval(px,py,r*2,r*2);
            }
        }
        g.setClip(prior);
        g.setColor(new Color(164,116,54));g.drawRect(x,y,w,h);
    }

    private SkillXp read(Skill skill)
    {
        if(skill==null || Microbot.getClient()==null || !Microbot.isLoggedIn()) return new SkillXp();
        try
        {
            long activeAccountHash = Microbot.getClient().getAccountHash();
            if (accountHash != activeAccountHash)
            {
                xpBase.clear();
                xpSince.clear();
                accountHash = activeAccountHash;
            }
            int level=Microbot.getClient().getRealSkillLevel(skill);
            int xp=Microbot.getClient().getSkillExperience(skill);
            long now=System.currentTimeMillis();
            if(!xpBase.containsKey(skill)||xp<xpBase.get(skill))
            {
                xpBase.put(skill,xp);
                xpSince.put(skill,now);
            }
            int gained=Math.max(0,xp-xpBase.get(skill));
            long elapsed=now-xpSince.get(skill);
            long rate=elapsed<30000?-1:Math.round(gained*3600000.0/Math.max(1,elapsed));
            int low=Experience.getXpForLevel(Math.max(1,Math.min(99,level)));
            int high=level>=99?low:Experience.getXpForLevel(Math.min(99,level+1));
            double progress=level>=99?1.0:Math.max(0,Math.min(1,(xp-low)/(double)Math.max(1,high-low)));
            return new SkillXp(skill,level,xp,high,gained,rate,progress,String.format(Locale.US,"%.1f",progress*100));
        }
        catch(Exception ignored) { return new SkillXp(); }
    }

    private static Module module(String task)
    {
        String t=task==null?"":task.replace("SWITCHING_TO_","").toUpperCase(Locale.ROOT);
        if(t.contains("WOODCUTTING"))return new Module("WOODCUTTING","Woodcutting","WC",new Color(72,166,62),Skill.WOODCUTTING);
        if(t.contains("RUNE_ESSENCE"))return new Module("RUNE_ESSENCE","Rune essence","ES",new Color(97,134,161),Skill.MINING);
        if(t.contains("MINING"))return new Module("MINING","Mining","MN",new Color(78,144,206),Skill.MINING);
        if(t.contains("FISHING"))return new Module("FISHING","Fishing","FI",new Color(67,165,213),Skill.FISHING);
        if(t.contains("FIREMAKING"))return new Module("FIREMAKING","Firemaking","FM",new Color(226,128,50),Skill.FIREMAKING);
        if(t.contains("COOKING"))return new Module("COOKING","Cooking","CO",new Color(235,144,72),Skill.COOKING);
        if(t.contains("CRAFTING"))return new Module("CRAFTING","Crafting","CR",new Color(200,152,86),Skill.CRAFTING);
        if(t.contains("SMITHING"))return new Module("SMITHING","Smithing","SM",new Color(210,161,67),Skill.SMITHING);
        if(t.contains("SMELTING"))return new Module("SMELTING","Smelting","SL",new Color(222,138,57),Skill.SMITHING);
        if(t.contains("MELEE"))return new Module("MELEE","Combat","CB",new Color(212,83,66),Skill.ATTACK);
        if(t.contains("RUNECRAFT"))return new Module("RUNECRAFTING","Runecrafting","RC",new Color(146,109,230),Skill.RUNECRAFT);
        if(t.startsWith("GE_"))return new Module("GE","Grand Exchange","GE",new Color(183,141,75),null);
        if(t.contains("TUTORIAL"))return new Module("TUTORIAL","Tutorial Island","TU",new Color(95,175,157),null);
        if(t.equals("IDLE"))return new Module("IDLE","Account Builder","AB",new Color(214,176,97),null);
        return new Module("QUEST","Questing","Q",new Color(207,170,98),null);
    }

    private static void panel(Graphics2D g,int x,int y,int w,int h)
    {
        g.setPaint(new GradientPaint(x,y,new Color(26,30,32,236),x,y+h,new Color(9,14,19,245)));
        g.fillRoundRect(x,y,w,h,6,6);
        g.setStroke(new BasicStroke(1f));g.setColor(new Color(130,94,49));g.drawRoundRect(x,y,w,h,6,6);
    }

    private static void progress(Graphics2D g,int x,int y,int w,int h,double v,Color accent)
    {
        g.setColor(new Color(5,8,9));g.fillRoundRect(x,y,w,h,5,5);
        int width=(int)Math.round((w-4)*Math.max(0,Math.min(1,v)));
        if(width>0)
        {
            g.setPaint(new GradientPaint(x,y,accent.brighter(),x,y+h,accent.darker()));
            g.fillRoundRect(x+2,y+2,width,h-4,3,3);
            g.setColor(new Color(255,255,255,55));g.drawLine(x+3,y+3,x+Math.max(3,width-1),y+3);
        }
        g.setColor(new Color(134,117,78));g.drawRoundRect(x,y,w,h,5,5);
    }

    private static void button(Graphics2D g,Map<String,Rectangle> buttons,String id,int x,int y,int w,int h,String label,Color bg,int font)
    {
        buttons.put(id,new Rectangle(x,y,w,h));
        g.setPaint(new GradientPaint(x,y,bg.brighter(),x,y+h,bg.darker()));
        g.fillRoundRect(x,y,w,h,7,7);
        g.setColor(new Color(212,170,91));g.drawRoundRect(x,y,w,h,7,7);
        g.setColor(PALE);
        Font f=new Font("Dialog",Font.BOLD,font);
        g.setFont(f);FontMetrics fm=g.getFontMetrics();
        String s=ellipsize(fm,label,w-12);
        g.drawString(s,x+(w-fm.stringWidth(s))/2,y+(h-fm.getHeight())/2+fm.getAscent());
    }

    private static void disabled(Graphics2D g,int x,int y,int w,int h,String label)
    {
        g.setColor(new Color(36,37,37));g.fillRoundRect(x,y,w,h,7,7);
        g.setColor(new Color(85,79,68));g.drawRoundRect(x,y,w,h,7,7);
        write(g,label,x+9,y+h/2+4,10,false,MUTED,w-17);
    }

    private static void skillMark(Graphics2D g,Module module,int x,int y,int radius)
    {
        g.setColor(new Color(6,10,14,230));g.fillOval(x-radius,y-radius,radius*2,radius*2);
        g.setColor(GOLD);g.drawOval(x-radius,y-radius,radius*2,radius*2);
        g.setColor(module.tint);
        g.setFont(new Font("Dialog",Font.BOLD,radius<18?11:13));
        FontMetrics f=g.getFontMetrics();g.drawString(module.symbol,x-f.stringWidth(module.symbol)/2,y+5);
    }

    private static void field(Graphics2D g,int x,int y,String key,String value,int max)
    {
        write(g,key,x,y,10,false,MUTED,Math.min(91,max/2));
        write(g,value,x+Math.min(92,max/2),y,10,true,TEXT,Math.max(20,max-96));
    }

    private static void line(Graphics2D g,int x,int y,int w) {g.setColor(new Color(117,90,50));g.drawLine(x,y,x+w,y);}
    private static void write(Graphics2D g,String s,int x,int y,int size,boolean bold,Color color,int limit)
    {
        if(s==null)s="--";
        g.setFont(new Font("Dialog",bold?Font.BOLD:Font.PLAIN,size));
        FontMetrics fm=g.getFontMetrics();g.setColor(color);g.drawString(ellipsize(fm,s,Math.max(1,limit)),x,y);
    }
    private static String ellipsize(FontMetrics f,String s,int max)
    {
        if(f.stringWidth(s)<=max) return s;
        while(s.length()>1 && f.stringWidth(s+"…")>max) s=s.substring(0,s.length()-1);
        return s+"…";
    }
    private static String shortText(String s)
    {
        return s==null||s.trim().isEmpty()?"Idle":s.replace('_',' ').trim();
    }
    private static String fmt(long x){return NumberFormat.getIntegerInstance(Locale.US).format(x);}
    private static String duration(long sec)
    {
        if(sec<0)return "--";
        long h=TimeUnit.SECONDS.toHours(sec),m=TimeUnit.SECONDS.toMinutes(sec)%60,s=sec%60;
        return h>0?String.format(Locale.US,"%02d:%02d:%02d",h,m,s):String.format(Locale.US,"%02d:%02d",m,s);
    }
    private static final class Module
    {
        final String key,name,symbol;final Color tint;final Skill skill;
        Module(String key,String name,String symbol,Color tint,Skill skill)
        {this.key=key;this.name=name;this.symbol=symbol;this.tint=tint;this.skill=skill;}
    }
    private static final class SkillXp
    {
        final Skill skill;final int level,exp,next,gained;final long rate;final double progress;final String percent;
        SkillXp(){this(null,0,0,0,0,-1,0,"--");}
        SkillXp(Skill skill,int level,int exp,int next,int gained,long rate,double progress,String percent)
        {this.skill=skill;this.level=level;this.exp=exp;this.next=next;this.gained=gained;this.rate=rate;this.progress=progress;this.percent=percent;}
    }
}
