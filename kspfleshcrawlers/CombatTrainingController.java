package net.runelite.client.plugins.microbot.kspfleshcrawlers;

import net.runelite.api.*;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import java.util.*;

final class CombatTrainingController {
    private Skill currentTrainingSkill; private long lastStyleCheck;
    Skill getCurrentTrainingSkill(){return currentTrainingSkill;}
    boolean allEnabledGoalsReached(KspFleshCrawlerConfig c){boolean any=c.trainAttack()||c.trainStrength()||c.trainDefence();return any&&(!c.trainAttack()||level(Skill.ATTACK)>=c.attackTarget())&&(!c.trainStrength()||level(Skill.STRENGTH)>=c.strengthTarget())&&(!c.trainDefence()||level(Skill.DEFENCE)>=c.defenceTarget());}
    void update(KspFleshCrawlerConfig c){long now=System.currentTimeMillis();if(now-lastStyleCheck<2_000)return;lastStyleCheck=now;Skill desired=chooseSkill(c);currentTrainingSkill=desired;if(desired==null)return;WidgetInfo w=findStyleWidget(desired,c.avoidControlled());if(w==null){Microbot.log("KSP Flesh Crawlers: no compatible attack style found for "+desired.getName());return;}if(widgetIndex(w)==Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE))return;if(Rs2Tab.getCurrentTab()!=InterfaceTab.COMBAT)Rs2Tab.switchToCombatOptionsTab();Rs2Combat.setAttackStyle(w);}
    private Skill chooseSkill(KspFleshCrawlerConfig c){List<Skill> p=new ArrayList<>();if(c.trainAttack()&&level(Skill.ATTACK)<c.attackTarget())p.add(Skill.ATTACK);if(c.trainStrength()&&level(Skill.STRENGTH)<c.strengthTarget())p.add(Skill.STRENGTH);if(c.trainDefence()&&level(Skill.DEFENCE)<c.defenceTarget())p.add(Skill.DEFENCE);return p.isEmpty()?null:!c.balanceCombatLevels()?p.get(0):p.stream().min(Comparator.comparingInt(this::level)).orElse(null);}
    private WidgetInfo findStyleWidget(Skill desired,boolean avoidControlled){int styleEnum=Microbot.getEnum(EnumID.WEAPON_STYLES).getIntValue(Microbot.getVarbitValue(Varbits.EQUIPPED_WEAPON_TYPE));int[] structs=Microbot.getEnum(styleEnum).getIntVals();WidgetInfo fallback=null;for(int i=0;i<structs.length&&i<4;i++){String name=Microbot.getStructComposition(structs[i]).getStringValue(ParamID.ATTACK_STYLE_NAME);if(name==null)continue;if(matches(name,desired))return widget(i);if("Controlled".equalsIgnoreCase(name))fallback=widget(i);}return avoidControlled?null:fallback;}
    private boolean matches(String name,Skill s){return s==Skill.ATTACK?"Accurate".equalsIgnoreCase(name):s==Skill.STRENGTH?"Aggressive".equalsIgnoreCase(name):s==Skill.DEFENCE&&"Defensive".equalsIgnoreCase(name);}
    private WidgetInfo widget(int i){return i==0?WidgetInfo.COMBAT_STYLE_ONE:i==1?WidgetInfo.COMBAT_STYLE_TWO:i==2?WidgetInfo.COMBAT_STYLE_THREE:i==3?WidgetInfo.COMBAT_STYLE_FOUR:null;}
    private int widgetIndex(WidgetInfo w){return w==WidgetInfo.COMBAT_STYLE_ONE?0:w==WidgetInfo.COMBAT_STYLE_TWO?1:w==WidgetInfo.COMBAT_STYLE_THREE?2:w==WidgetInfo.COMBAT_STYLE_FOUR?3:-1;}
    private int level(Skill s){return Microbot.getClient().getRealSkillLevel(s);}
}
