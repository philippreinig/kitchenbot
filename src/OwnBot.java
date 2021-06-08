
import com.apogames.kitchenchef.ai.Cooking;
import com.apogames.kitchenchef.ai.KitchenInformation;
import com.apogames.kitchenchef.ai.KitchenPlayerAI;
import com.apogames.kitchenchef.ai.Student;
import com.apogames.kitchenchef.ai.action.Action;
import com.apogames.kitchenchef.ai.actionPoints.ActionPoint;
import com.apogames.kitchenchef.ai.player.Player;
import com.apogames.kitchenchef.game.entity.Vector;
import com.apogames.kitchenchef.game.enums.CookingStatus;
import com.apogames.kitchenchef.game.enums.KitchenActionPointEnum;
import com.apogames.kitchenchef.game.pathfinding.PathResult;

import java.util.List;

@Student(author = "Dirk Aporius", matrikelnummer = 815)
public class OwnBot extends KitchenPlayerAI {
    private static final Vector IDLE = Vector.ZERO;

    private KitchenInformation info;
    
    private Cooking currentCooking;
    private boolean cookingOnStove = false;

    @Override
    public String getName() {
        return "OwnBot";
    }

    @Override
    public void update(KitchenInformation information, List<Player> players) {
        System.out.println("update method called");
        this.info = information;
        Player player = players.get(0);
        System.out.println("inital currentCooking null: " + (this.currentCooking == null));
        if(currentCooking == null && !cookingOnStove){
            System.out.println("getting new cooking from customer");
            this.move(player, KitchenActionPointEnum.CUSTOMER);
            this.currentCooking = player.getCooking();
            System.out.println(player.getCooking() != null ? "got cooking" : "dont have cooking yet");
        }
        else {
            System.out.println("entered first else branch with cooking null: " + (this.currentCooking == null));
            if (player.getCooking() != null) this.currentCooking = player.getCooking();
            if(player.getCooking() != currentCooking) System.err.println("COOKINGS NOT SYNCHRONIZED");
            CookingStatus status = this.currentCooking.getStatus();
            System.out.println("status is: " + this.currentCooking.getStatus());
            if (status == CookingStatus.NEEDED_DISH) move(player, KitchenActionPointEnum.DISH_TAKING);
            else if (status == CookingStatus.DISH) move(player, KitchenActionPointEnum.INGREDIENT_TAKE);
            else if (status == CookingStatus.RAW){
                if(!allCorrect(this.currentCooking.getIngredientsCorrect())) move(player, KitchenActionPointEnum.INGREDIENT_TAKE);
                else if (!allCorrect(this.currentCooking.getSpiceCorrect())) move(player, KitchenActionPointEnum.SPICE_TAKE);
            }
            else if (status == CookingStatus.READY_FOR_CUTTING) move(player, KitchenActionPointEnum.CUTTING);
            else if (status == CookingStatus.READY_FOR_COOKING){
                move(player, KitchenActionPointEnum.COOKING);
                System.out.println("out of move method");
                this.cookingOnStove = true;
                System.out.println("set cooking on stove to true");
            }
            else if (status == CookingStatus.COOKING){
                player.setAction(Action.idle());
                assert(player.getAction().getMovement().equals(IDLE));
            }
            else if (status == CookingStatus.SERVEABLE){
                this.cookingOnStove = false;
                System.err.println("ERROR: getActionPoint(KitchenActionPointEnum.COOKING) leads to null!");
                if(!getActionPoint(KitchenActionPointEnum.COOKING).isPlayerIn(player))
                {
                    move(player, KitchenActionPointEnum.COOKING);
                }
                if(player.getCooking() == null){
                    player.setAction(Action.takeCookingUp());
                }
                move(player, KitchenActionPointEnum.CUSTOMER);
                this.currentCooking = null;
                System.out.println("Served meal to customer");
            }
            else if (status == CookingStatus.ROTTEN) move(player, KitchenActionPointEnum.DISH_WASHING);
            else{
                assert(false) : "Player not doing anything! (else condition fired)";
            }
        }
    }

    private void move(Player player, KitchenActionPointEnum kape){
        for (ActionPoint point : info.getActionPoints()) {
            if (point.getContent() == kape) {
                if (point.isPlayerIn(player)) {
                    player.setAction(Action.use());
                } else {
                    PathResult wayFromTo = info.getWays().findWayFromTo(info, player, point.getPosition());
                    player.setAction(Action.move(wayFromTo.getMovement()));
                }
                return;
            }
        }
    }

        private boolean allCorrect(List<Boolean> list){
            for(Boolean b : list) if(!b) return false;
            return true;
    }

    private ActionPoint getActionPoint(KitchenActionPointEnum kape){
        for(ActionPoint ap : info.getActionPoints()) if (ap.getContent() == kape) return ap;
        return null;
    }
}