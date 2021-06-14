import com.apogames.kitchenchef.ai.Cooking;
import com.apogames.kitchenchef.ai.KitchenInformation;
import com.apogames.kitchenchef.ai.KitchenPlayerAI;
import com.apogames.kitchenchef.ai.Student;
import com.apogames.kitchenchef.ai.action.Action;
import com.apogames.kitchenchef.ai.actionPoints.ActionPoint;
import com.apogames.kitchenchef.ai.player.Player;
import com.apogames.kitchenchef.game.entity.Vector;
import com.apogames.kitchenchef.game.enums.CookingStatus;
import com.apogames.kitchenchef.game.pathfinding.PathResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Student(author = "Dirk Aporius", matrikelnummer = 815)
public class MyBot extends KitchenPlayerAI {
    private static final Vector IDLE = Vector.ZERO;
    HashMap<String, ActionPoint> actionPoints;
    List<ActionPoint> customers;
    List<ActionPoint> cuttingActionPoints;
    List<ActionPoint> cookingActionPoints;
    List<ActionPoint> ingredientTakingActionPoints;
    private KitchenInformation info;
    private Cooking currentCooking;
    private boolean cookingOnStove = false;

    private HashMap<Cooking, ActionPoint> customerOfCooking;

    private Cooking cooking_debug;

    @Override
    public void init() {
        this.actionPoints = new HashMap<>();
        this.customers = new ArrayList<>();
        this.cuttingActionPoints = new ArrayList<>();
        this.cookingActionPoints = new ArrayList<>();
        this.ingredientTakingActionPoints = new ArrayList<>();
        this.cookingOnStove = false;
        this.customerOfCooking = new HashMap<>();
    }

    @Override
    public String getName() {
        return "OwnBot";
    }

    @Override
    public void update(final KitchenInformation information, final List<Player> players) {
        this.info = information;
        this.refactorActionPoints();
        System.out.println("update method called");
        final Player player = players.get(0);
        System.out.println("initial currentCooking null: " + (this.currentCooking == null));
        if (this.currentCooking == null && !this.cookingOnStove) {
            System.out.println("getting new cooking from customer");
            final ActionPoint nextCustomer = this.getNextCustomer();
            this.moveToActionPointAndUse(player, nextCustomer);
//            if (player.getCooking() == null)
//                throw new IllegalStateException("player went to customer but doesnt have cooking afterwards");
            final Cooking cooking = player.getCooking();

            this.customerOfCooking.put(cooking, nextCustomer);
            this.currentCooking = cooking;
            this.cooking_debug = cooking;
        } else {
            System.out.println("id of current cooking: " + this.currentCooking.getId());
            if (player.getCooking() != null) this.currentCooking = player.getCooking();
            if (player.getCooking() != this.currentCooking) System.err.println("COOKINGS NOT SYNCHRONIZED");
            final CookingStatus status = this.currentCooking.getStatus();
            System.out.println("status is: " + this.currentCooking.getStatus());
            if (status == CookingStatus.NEEDED_DISH) {
                this.printCurrentDishStatus();
                this.moveToActionPointAndUse(player, this.actionPoints.get("DISH_TAKING"));
            } else if (status == CookingStatus.DISH)
                this.moveToActionPointAndUse(player, this.ingredientTakingActionPoints.get(0));
            else if (status == CookingStatus.RAW) {
                if (!MyBot.allCorrect(this.currentCooking.getIngredientsCorrect()))
                    this.moveToActionPointAndUse(player, this.ingredientTakingActionPoints.get(0));
                else if (!MyBot.allCorrect(this.currentCooking.getSpiceCorrect()))
                    this.moveToActionPointAndUse(player, this.actionPoints.get("SPICE_TAKE"));
            } else if (status == CookingStatus.READY_FOR_CUTTING)
                this.moveToActionPointAndUse(player, this.cuttingActionPoints.get(0));
            else if (status == CookingStatus.READY_FOR_COOKING) {
                this.moveToActionPointAndUse(player, this.cookingActionPoints.get(0));
                this.cookingOnStove = true;
            } else if (status == CookingStatus.COOKING) {
                player.setAction(Action.idle());
                assert (player.getAction().getMovement().equals(MyBot.IDLE));
            } else if (status == CookingStatus.SERVEABLE) {
                this.cookingOnStove = false;
                if (!this.cookingActionPoints.get(0).isPlayerIn(player))
                    this.moveToActionPointAndUse(player, this.cookingActionPoints.get(0));
                if (player.getCooking() == null) player.setAction(Action.takeCookingUp());
                this.moveToPositionAndUse(player, player.getCooking().getCustomerPosition());
                this.currentCooking = null;
                System.out.println("Served meal to customer");

  
            } else if (status == CookingStatus.ROTTEN)
                this.moveToActionPointAndUse(player, this.actionPoints.get("DISH_WASHING"));
            else assert (false) : "Player not doing anything! (else condition fired)";
        }
    }

    private void printCurrentDishStatus() {
        if (this.currentCooking != null)
            System.out.println("for current cooking need following dish: " + this.currentCooking.getDish());
        else System.out.println("current cooking null -> no dish");
    }

    private void refactorActionPoints() {
        this.actionPoints = new HashMap<>();
        this.cookingActionPoints = new ArrayList<>();
        this.cuttingActionPoints = new ArrayList<>();
        this.ingredientTakingActionPoints = new ArrayList<>();
        this.customers = new ArrayList<>();


        for (final ActionPoint actionpoint : this.info.getActionPoints()) {
            final String name = actionpoint.getContent().name();
            System.out.println("id: " + actionpoint.getId() + ", name: " + name);
            switch (name) {
                case "DISH_TAKING":
                case "DISH_WASHING":
                case "SPICE_TAKE":
                case "BUY":
                case "UPGRADE":
                    this.actionPoints.put(name, actionpoint);
                    break;
                case "CUSTOMER":
                    this.customers.add(actionpoint);
                    break;
                case "INGREDIENT_TAKE":
                    this.ingredientTakingActionPoints.add(actionpoint);
                    break;
                case "CUTTING":
                    this.cuttingActionPoints.add(actionpoint);
                    break;
                case "COOKING":
                    this.cookingActionPoints.add(actionpoint);
                    break;
                default:
                    throw new IllegalStateException(name + " not handled by refactorActionPoints() method!");
            }
        }
        /*
        single
            DISH_TAKING
            DISH_WASHING
            SPICE_TAKE
            BUY
            UPGRADE
        multiple
            CUSTOMER
            INGREDIENT_TAKE
            CUTTING
            COOKING
         */

        System.out.println(this.info == null);
        for (final ActionPoint kap : this.info.getActionPoints()) System.out.println(kap.getContent().name());
    }

    private void moveToActionPointAndUse(final Player player, final ActionPoint actionpoint) {
        if (actionpoint == null) throw new IllegalStateException("move method got actionpoint, which is null!");
        if (actionpoint.isPlayerIn(player)) player.setAction(Action.use());
        else {
            final PathResult wayFromTo = this.info.getWays().findWayFromTo(this.info, player, actionpoint.getPosition());
            player.setAction(Action.move(wayFromTo.getMovement()));
        }
    }

    private void moveToPositionAndUse(final Player player, final Vector vector) {
        System.out.println("player currently at: " + player.getPosition() + ", needs to go to: " + vector);
        if (this.playerInsideActionPointAtVector(player, vector) != null)
            this.moveToActionPointAndUse(player, this.playerInsideActionPointAtVector(player, vector));
        else
            System.err.println("player not yet at right position");
        player.setAction(Action.move(this.info.getWays().findWayFromTo(this.info, player, vector).getMovement()));
    }

    private ActionPoint playerInsideActionPointAtVector(final Player player, final Vector vector) {
        if (player.getPosition().distance(vector) < this.info.getActionPoints().get(0).getRadius())
            for (final ActionPoint ap : this.info.getActionPoints())
                if (ap.isPlayerIn(player)) return ap;
        return null;
    }

    private static boolean allCorrect(final List<Boolean> list) {
        for (final Boolean b : list) if (!b) return false;
        return true;
    }

    private ActionPoint getNextCustomer() {
        ActionPoint nextCustomer = null;
        for (final ActionPoint customer : this.customers) {
            System.out.println(customer.getWaitingTime());
            if (nextCustomer == null || customer.getWaitingTime() != -1 && customer.getWaitingTime() < nextCustomer.getWaitingTime())
                nextCustomer = customer;
        }
        return nextCustomer;
    }
}