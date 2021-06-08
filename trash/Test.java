import com.apogames.kitchenchef.ai.Cooking;
import com.apogames.kitchenchef.ai.KitchenInformation;
import com.apogames.kitchenchef.ai.KitchenPlayerAI;
import com.apogames.kitchenchef.ai.Student;
import com.apogames.kitchenchef.ai.action.Action;
import com.apogames.kitchenchef.ai.actionPoints.ActionPoint;
import com.apogames.kitchenchef.ai.player.Player;
import com.apogames.kitchenchef.game.actionPoint.KitchenActionPoint;
import com.apogames.kitchenchef.game.enums.CookingStatus;
import com.apogames.kitchenchef.game.enums.KitchenActionPointEnum;
import com.apogames.kitchenchef.game.pathfinding.PathResult;

import java.util.Iterator;
import java.util.List;

@Student(author = "Philipp Reinig", matrikelnummer = 232934)
public class Test extends KitchenPlayerAI {

    public Test(){
        return;
    }

    private KitchenInformation info;
    private List<Player> players;

    @Override
    public String getName(){
        return "AI";
    }

    @Override
    public void update(KitchenInformation info, List<Player> players){
        System.out.println("update method called");
        this.info = info;
        this.players = players;
        Player player = players.get(0);
        Cooking cooking = player.getCooking();
        if(cooking == null){
            System.out.println("player isnt cooking -> moving to customer");
            move(player, KitchenActionPointEnum.CUSTOMER);
            System.out.println("moved player to customer");
            player.setAction(Action.use());
            System.out.println("took order");
        }else{
            System.out.println("player already cooking, status: " + cooking.getStatus());
            switch(cooking.getStatus()){
                case NEEDED_DISH:
                    move(player, KitchenActionPointEnum.DISH_TAKING);
                    return;
                case DISH:
                    //what the heck is dish?
                    move(player, KitchenActionPointEnum.DISH_WASHING);
                    return;
                case RAW:
                    //move(player, KitchenActionPointEnum.COOKING);
                    System.out.println("raw meal");
                    return;
                case READY_FOR_CUTTING:
                    move(player, KitchenActionPointEnum.CUTTING);
                    return;
                case CUTTING:
                    System.out.println(player + " is cutting (case: CUTTING)");
//                    move(player, KitchenActionPointEnum.DISH_TAKING);
                    return;
                case READY_FOR_COOKING:
                    move(player, KitchenActionPointEnum.COOKING);
                    return;
                case COOKING:
                    System.out.println(player + " is currently cooking");
                    move(player, KitchenActionPointEnum.DISH_TAKING);
                    return;
                case SERVEABLE:
                    move(player, KitchenActionPointEnum.CUSTOMER);
                    return;
                case ROTTEN:
                    move(player, KitchenActionPointEnum.DISH_WASHING);
                    return;
                default:
                    System.err.println("UNHANDLED CASE: " + cooking.getStatus());
            }
        }
    }

    private void move(Player player, KitchenActionPointEnum kape){
        for (ActionPoint point : info.getActionPoints()) {
            if (point.getContent() == kape) {
                if (point.isPlayerIn(player)) {
                    return;
//                    player.setAction(Action.use());
                } else {
                    PathResult wayFromTo = info.getWays().findWayFromTo(info, player, point.getPosition());
                    player.setAction(Action.move(wayFromTo.getMovement()));
                }
                return;
            }
        }
    }
}
