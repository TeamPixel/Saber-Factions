package com.massivecraft.factions.cmd.points;

import com.massivecraft.factions.Faction;
import com.massivecraft.factions.Factions;
import com.massivecraft.factions.cmd.FCommand;
import com.massivecraft.factions.struct.Permission;
import com.massivecraft.factions.zcore.util.TL;

public class CmdPointsSet extends FCommand {

    public CmdPointsSet() {
        super();
        this.aliases.add("set");

        this.requiredArgs.add("faction");
        this.requiredArgs.add("# of points");


        this.errorOnToManyArgs = false;
        //this.optionalArgs

        this.permission = Permission.SETPOINTS.node;

        this.disableOnLock = true;

        senderMustBePlayer = false;
        senderMustBeMember = false;
        senderMustBeModerator = false;
        senderMustBeColeader = false;
        senderMustBeAdmin = false;
    }


    @Override
    public void perform() {
        Faction faction = Factions.getInstance().getByTag(args.get(0));

        if (faction == null) {
            fme.msg(TL.COMMAND_POINTS_FAILURE.toString().replace("{faction}", args.get(0)));
            return;
        }

        if(argAsInt(1) < 0){
            fme.msg(TL.COMMAND_POINTS_INSUFFICIENT);
            return;
        }

        faction.setPoints(argAsInt(1));
        fme.msg(TL.COMMAND_SETPOINTS_SUCCESSFUL, argAsInt(1), faction.getTag(), faction.getPoints());
    }


    @Override
    public TL getUsageTranslation() {
        return TL.COMMAND_SETPOINTS_DESCRIPTION;
    }


}
