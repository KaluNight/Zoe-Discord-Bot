package ch.kalunight.zoe.command.remove;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;

import net.dv8tion.jda.core.Permission;

public class RemoveCommand extends Command {

  public RemoveCommand() {
    this.name = "remove";
    Permission[] permissionRequired = {Permission.MANAGE_CHANNEL};
    this.userPermissions = permissionRequired;
    this.help = "Send info about remove commands";
    Command[] commandsChildren = {};
    this.children = commandsChildren;
  }
  
  
  @Override
  protected void execute(CommandEvent event) {
    // TODO Auto-generated method stub
    
  }
  
  
}
